package com.nbp.cobblemon_electrified.block.entity

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.pokemon.stats.Stat
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.api.types.ElementalTypes
import com.cobblemon.mod.common.block.BerryBlock
import com.cobblemon.mod.common.block.entity.BerryBlockEntity
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import com.cobblemon.mod.common.net.messages.client.pasture.OpenPasturePacket
import com.cobblemon.mod.common.pokemon.Pokemon
import com.nbp.cobblemon_electrified.block.CobblemonElectrifiedBlocks
import com.nbp.cobblemon_electrified.config.CobblemonElectrifiedConfig
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.min
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.Pose
import net.minecraft.world.Container
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.BonemealableBlock
import net.minecraft.world.level.block.CropBlock
import net.minecraft.world.level.block.FarmBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.WorldlyContainer
import net.minecraft.world.phys.AABB

class FarmAssistantBlockEntity(pos: BlockPos, state: BlockState) :
    BlockEntity(CobblemonElectrifiedBlocks.FARM_ASSISTANT_BLOCK_ENTITY, pos, state) {

    var waterSlotUnlocked: Boolean = false
        private set

    var normalSlotUnlocked: Boolean = false
        private set

    private val storedPokemon = mutableListOf<StoredPokemon>()
    private val collectionBuffer = mutableListOf<ItemStack>()

    fun hasPokemon(): Boolean = storedPokemon.isNotEmpty()

    fun hasWaterPokemon(): Boolean = storedPokemon.any { it.slotRole == SlotRole.WATER }

    fun isOwner(player: ServerPlayer): Boolean = storedPokemon.any { it.ownerId == player.uuid }

    fun slotLimit(): Int = 1 + (if (waterSlotUnlocked) 1 else 0) + (if (normalSlotUnlocked) 1 else 0)

    fun canAddPokemon(): Boolean = storedPokemon.any { it.slotRole == SlotRole.GRASS }.not() ||
        waterSlotUnlocked && storedPokemon.any { it.slotRole == SlotRole.WATER }.not() ||
        normalSlotUnlocked && storedPokemon.any { it.slotRole == SlotRole.NORMAL }.not()

    fun canAddPokemon(pokemon: Pokemon): Boolean = slotRoleFor(pokemon) != null

    fun canUpgradeWaterSlot(): Boolean = !waterSlotUnlocked

    fun canUpgradeNormalSlot(): Boolean = !normalSlotUnlocked

    fun saveUpgradeData(): CompoundTag {
        return CompoundTag().also {
            it.putBoolean("WaterSlotUnlocked", waterSlotUnlocked)
            it.putBoolean("NormalSlotUnlocked", normalSlotUnlocked)
        }
    }

    fun loadUpgradeData(nbt: CompoundTag) {
        waterSlotUnlocked = nbt.getBoolean("WaterSlotUnlocked") || nbt.getInt("UnlockedSlots") >= 2
        normalSlotUnlocked = nbt.getBoolean("NormalSlotUnlocked") || nbt.getInt("UnlockedSlots") >= 3
        setChanged()
    }

    fun upgradeWaterSlot(): Boolean {
        if (!canUpgradeWaterSlot()) {
            return false
        }

        waterSlotUnlocked = true
        setChanged()
        return true
    }

    fun upgradeNormalSlot(): Boolean {
        if (!canUpgradeNormalSlot()) {
            return false
        }

        normalSlotUnlocked = true
        setChanged()
        return true
    }

    fun accepts(pokemon: Pokemon): Boolean {
        return pokemon.types.any { it == ElementalTypes.WATER || it == ElementalTypes.GRASS || it == ElementalTypes.NORMAL }
    }

    private fun slotRoleFor(pokemon: Pokemon): SlotRole? {
        val hasGrass = pokemon.types.any { it == ElementalTypes.GRASS }
        val hasWater = pokemon.types.any { it == ElementalTypes.WATER }
        val hasNormal = pokemon.types.any { it == ElementalTypes.NORMAL }

        return when {
            hasGrass && storedPokemon.none { it.slotRole == SlotRole.GRASS } -> SlotRole.GRASS
            waterSlotUnlocked && hasWater && storedPokemon.none { it.slotRole == SlotRole.WATER } -> SlotRole.WATER
            normalSlotUnlocked && hasNormal && storedPokemon.none { it.slotRole == SlotRole.NORMAL } -> SlotRole.NORMAL
            else -> null
        }
    }

    fun addPokemon(player: ServerPlayer, pokemon: Pokemon, pcId: UUID): Boolean {
        val slotRole = slotRoleFor(pokemon)
        if (slotRole == null || storedPokemon.any { it.pokemonId == pokemon.uuid } || !accepts(pokemon)) {
            return false
        }

        val linkId = UUID.randomUUID()
        pokemon.tetheringId = linkId
        storedPokemon.add(
            StoredPokemon(
                ownerId = player.uuid,
                pcId = pcId,
                pokemonId = pokemon.uuid,
                linkId = linkId,
                ownerName = player.gameProfile.name,
                pokemonName = pokemon.getDisplayName().string,
                water = pokemon.types.any { it == ElementalTypes.WATER },
                grass = pokemon.types.any { it == ElementalTypes.GRASS },
                normal = pokemon.types.any { it == ElementalTypes.NORMAL },
                slotRole = slotRole,
                farmPower = farmPowerFor(pokemon)
            )
        )
        setChanged()
        ensureVisiblePokemon()
        if (slotRole == SlotRole.WATER) {
            (level as? ServerLevel)?.let(::hydrateFarmland)
        }
        return true
    }

    fun removePokemon(pokemonId: UUID): Boolean {
        val removed = storedPokemon.firstOrNull { it.pokemonId == pokemonId } ?: return false
        releaseLinkedPokemon(removed)
        storedPokemon.remove(removed)
        setChanged()
        ensureVisiblePokemon()
        return true
    }

    fun ownsPokemon(player: ServerPlayer, pokemonId: UUID): Boolean {
        return storedPokemon.any { it.ownerId == player.uuid && it.pokemonId == pokemonId }
    }

    fun storedPokemonIdsFor(player: ServerPlayer): List<UUID> {
        return storedPokemon.filter { it.ownerId == player.uuid }.map { it.pokemonId }
    }

    fun releaseLinkedPokemon() {
        storedPokemon.toList().forEach(::releaseLinkedPokemon)
    }

    private fun releaseLinkedPokemon(entry: StoredPokemon) {
        val registryAccess = level?.server?.registryAccess() ?: return
        val pokemon = Cobblemon.storage.getPC(entry.pcId, registryAccess)[entry.pokemonId] ?: return
        getPokemonEntity(pokemon)
            ?.takeIf { getTethering(it)?.tetheringId == entry.linkId }
            ?.discard()
        if (pokemon.tetheringId == entry.linkId) {
            pokemon.tetheringId = null
        }
    }

    fun toPastureDTOs(viewer: ServerPlayer): List<OpenPasturePacket.PasturePokemonDataDTO> {
        val registryAccess = level?.server?.registryAccess() ?: viewer.registryAccess()
        return storedPokemon.mapNotNull { entry ->
            val pokemon = Cobblemon.storage.getPC(entry.pcId, registryAccess)[entry.pokemonId] ?: return@mapNotNull null
            val entity = getPokemonEntity(pokemon)?.takeIf { getTethering(it)?.tetheringId == entry.linkId }
            OpenPasturePacket.PasturePokemonDataDTO(
                pokemonId = pokemon.uuid,
                playerId = entry.ownerId,
                displayName = pokemon.getDisplayName(),
                ownerName = null,
                species = pokemon.species.resourceIdentifier,
                aspects = pokemon.aspects,
                heldItem = pokemon.heldItem(),
                level = pokemon.level,
                entityKnown = entity != null,
                behaviourFlags = emptySet()
            )
        }
    }

    override fun loadAdditional(nbt: CompoundTag, registryLookup: HolderLookup.Provider) {
        super.loadAdditional(nbt, registryLookup)
        waterSlotUnlocked = nbt.getBoolean("WaterSlotUnlocked") || nbt.getInt("UnlockedSlots") >= 2
        normalSlotUnlocked = nbt.getBoolean("NormalSlotUnlocked") || nbt.getInt("UnlockedSlots") >= 3
        storedPokemon.clear()
        val list = nbt.getList("Pokemon", CompoundTag.TAG_COMPOUND.toInt())
        for (tag in list) {
            tag as CompoundTag
            storedPokemon.add(StoredPokemon.fromNbt(tag))
        }
        collectionBuffer.clear()
        val bufferList = nbt.getList("CollectionBuffer", CompoundTag.TAG_COMPOUND.toInt())
        for (tag in bufferList) {
            tag as CompoundTag
            ItemStack.parseOptional(registryLookup, tag).takeUnless { it.isEmpty }?.let(collectionBuffer::add)
        }
        if (storedPokemon.any { it.slotRole == SlotRole.WATER }) {
            waterSlotUnlocked = true
        }
        if (storedPokemon.any { it.slotRole == SlotRole.NORMAL }) {
            normalSlotUnlocked = true
        }
    }

    override fun saveAdditional(nbt: CompoundTag, registryLookup: HolderLookup.Provider) {
        super.saveAdditional(nbt, registryLookup)
        nbt.putBoolean("WaterSlotUnlocked", waterSlotUnlocked)
        nbt.putBoolean("NormalSlotUnlocked", normalSlotUnlocked)
        val list = ListTag()
        storedPokemon.forEach { list.add(it.toNbt()) }
        nbt.put("Pokemon", list)
        val bufferList = ListTag()
        collectionBuffer.filterNot { it.isEmpty }.forEach { bufferList.add(it.saveOptional(registryLookup)) }
        nbt.put("CollectionBuffer", bufferList)
    }

    private fun tickFarm(world: ServerLevel) {
        val waterPower = storedPokemon.filter { it.slotRole == SlotRole.WATER }.sumOf { it.farmPower }
        val grassPower = storedPokemon.filter { it.slotRole == SlotRole.GRASS }.sumOf { it.farmPower }
        val normalPower = storedPokemon.filter { it.slotRole == SlotRole.NORMAL }.sumOf { it.farmPower }
        val collectionBudget = normalPower.takeIf { it > 0 }?.let { CollectionBudget(collectionItemLimitFor(it)) }
        if (collectionBudget != null) {
            flushCollectionBuffer(world, collectionBudget)
        }
        if (waterPower <= 0 && grassPower <= 0) {
            return
        }

        val positions = BlockPos.betweenClosed(blockPos.offset(-RANGE, -1, -RANGE), blockPos.offset(RANGE, 1, RANGE))
            .map { it.immutable() }
            .shuffled()

        if (waterPower > 0) {
            hydrateFarmland(world)
            positions.asSequence()
                .filter { tryGrow(world, it) }
                .take(actionCountFor(waterPower))
                .count()
        }

        if (grassPower > 0) {
            positions.asSequence()
                .filter { tryHarvest(world, it, harvestMultiplierFor(grassPower), collectionBudget) }
                .take(actionCountFor(grassPower))
                .count()
        }
    }

    private fun tryGrow(world: ServerLevel, pos: BlockPos): Boolean {
        val state = world.getBlockState(pos)
        val block = state.block as? BonemealableBlock ?: return false
        if (!block.isValidBonemealTarget(world, pos, state) || !block.isBonemealSuccess(world, world.random, pos, state)) {
            return false
        }

        block.performBonemeal(world, world.random, pos, state)
        return true
    }

    private fun hydrateFarmland(world: ServerLevel) {
        val range = wateringRange()
        if (range <= 0) {
            return
        }

        BlockPos.betweenClosed(blockPos.offset(-range, -2, -range), blockPos.offset(range, 2, range)).forEach { pos ->
            val farmPos = pos.immutable()
            val state = world.getBlockState(farmPos)
            if (state.block is FarmBlock && state.getValue(FarmBlock.MOISTURE) < FarmBlock.MAX_MOISTURE) {
                world.setBlock(farmPos, state.setValue(FarmBlock.MOISTURE, FarmBlock.MAX_MOISTURE), Block.UPDATE_CLIENTS)
            }
        }
    }

    private fun tryHarvest(world: ServerLevel, pos: BlockPos, harvestMultiplier: Int, collectionBudget: CollectionBudget?): Boolean {
        if (collectionBudget != null) {
            if (collectionBudget.remainingItems <= 0 && !collectionBufferHasSpace()) {
                return false
            }
            if (!hasCollectionDestination(world)) {
                return false
            }
        }

        val state = world.getBlockState(pos)
        val crop = state.block as? CropBlock
        if (crop != null) {
            if (!crop.isMaxAge(state)) {
                return false
            }

            val drops = Block.getDrops(state, world, pos, world.getBlockEntity(pos))
            routeHarvestDrops(world, pos, drops, harvestMultiplier, collectionBudget)
            world.setBlock(pos, crop.getStateForAge(0), Block.UPDATE_CLIENTS)
            return true
        }

        val berryBlock = state.block as? BerryBlock ?: return false
        if (state.getValue(BerryBlock.AGE) != BerryBlock.FRUIT_AGE) {
            return false
        }
        val berryEntity = world.getBlockEntity(pos) as? BerryBlockEntity ?: return false
        routeHarvestDrops(world, pos, berryEntity.harvest(world, state, pos), harvestMultiplier, collectionBudget)
        return true
    }

    private fun routeHarvestDrops(
        world: ServerLevel,
        cropPos: BlockPos,
        drops: Collection<ItemStack>,
        harvestMultiplier: Int,
        collectionBudget: CollectionBudget?
    ) {
        drops.forEach { stack ->
            val boosted = stack.copy()
            boosted.count = (boosted.count * harvestMultiplier).coerceAtMost(boosted.maxStackSize)
            routeHarvestDrop(world, cropPos, boosted, collectionBudget)
        }
    }

    private fun routeHarvestDrop(world: ServerLevel, cropPos: BlockPos, stack: ItemStack, collectionBudget: CollectionBudget?) {
        if (collectionBudget == null || collectionBudget.remainingItems <= 0) {
            if (collectionBudget == null) {
                Block.popResource(world, cropPos, stack)
            } else {
                addToCollectionBuffer(stack)
            }
            return
        }

        val routed = stack.copy()
        val routedAmount = min(routed.count, collectionBudget.remainingItems)
        routed.count = routedAmount

        val remainder = insertIntoAdjacentInventories(world, routed)
        val inserted = routedAmount - remainder.count
        collectionBudget.remainingItems -= inserted
        if (!remainder.isEmpty) {
            addToCollectionBuffer(remainder)
        }

        val unroutedAmount = stack.count - routedAmount
        if (unroutedAmount > 0) {
            val unrouted = stack.copy()
            unrouted.count = unroutedAmount
            addToCollectionBuffer(unrouted)
        }
    }

    private fun flushCollectionBuffer(world: ServerLevel, collectionBudget: CollectionBudget) {
        if (collectionBuffer.isEmpty() || collectionBudget.remainingItems <= 0) {
            return
        }

        val iterator = collectionBuffer.listIterator()
        while (iterator.hasNext() && collectionBudget.remainingItems > 0) {
            val buffered = iterator.next()
            if (buffered.isEmpty) {
                iterator.remove()
                continue
            }

            val routed = buffered.copy()
            val routedAmount = min(routed.count, collectionBudget.remainingItems)
            routed.count = routedAmount
            val remainder = insertIntoAdjacentInventories(world, routed)
            val inserted = routedAmount - remainder.count
            if (inserted > 0) {
                buffered.shrink(inserted)
                collectionBudget.remainingItems -= inserted
                setChanged()
            }
            if (buffered.isEmpty) {
                iterator.remove()
            }
        }
    }

    private fun hasCollectionDestination(world: ServerLevel): Boolean {
        return collectionBufferHasSpace() || adjacentInventoryPositions().any { (pos, face) ->
            val container = world.getBlockEntity(pos) as? Container ?: return@any false
            hasContainerSpace(container, face)
        }
    }

    private fun collectionBufferHasSpace(): Boolean {
        return collectionBuffer.size < COLLECTION_BUFFER_SLOTS || collectionBuffer.any { it.count < it.maxStackSize }
    }

    private fun addToCollectionBuffer(stack: ItemStack): ItemStack {
        var remainder = stack.copy()
        collectionBuffer.forEach { existing ->
            if (remainder.isEmpty) {
                return@forEach
            }
            if (ItemStack.isSameItemSameComponents(existing, remainder)) {
                val moved = min(remainder.count, existing.maxStackSize - existing.count)
                if (moved > 0) {
                    existing.grow(moved)
                    remainder.shrink(moved)
                }
            }
        }

        while (!remainder.isEmpty && collectionBuffer.size < COLLECTION_BUFFER_SLOTS) {
            val buffered = remainder.copy()
            buffered.count = min(remainder.count, remainder.maxStackSize)
            collectionBuffer += buffered
            remainder.shrink(buffered.count)
        }

        if (remainder.count != stack.count) {
            setChanged()
        }
        return remainder
    }

    private fun insertIntoAdjacentInventories(world: ServerLevel, stack: ItemStack): ItemStack {
        var remainder = stack.copy()
        adjacentInventoryPositions().forEach { (pos, face) ->
            if (remainder.isEmpty) {
                return@forEach
            }
            val container = world.getBlockEntity(pos) as? Container ?: return@forEach
            remainder = insertIntoContainer(container, remainder, face)
        }
        return remainder
    }

    private fun adjacentInventoryPositions(): List<Pair<BlockPos, Direction>> {
        val positions = linkedSetOf<Pair<BlockPos, Direction>>()
        listOf(blockPos, blockPos.above()).forEach { basePos ->
            Direction.entries.forEach { direction ->
                val inventoryPos = basePos.relative(direction)
                if (inventoryPos != blockPos && inventoryPos != blockPos.above()) {
                    positions += inventoryPos to direction.opposite
                }
            }
        }
        return positions.toList()
    }

    private fun insertIntoContainer(container: Container, stack: ItemStack, face: Direction): ItemStack {
        val remainder = stack.copy()
        val slots = if (container is WorldlyContainer) {
            container.getSlotsForFace(face).toList()
        } else {
            (0 until container.containerSize).toList()
        }

        slots.forEach { slot ->
            if (remainder.isEmpty) {
                return@forEach
            }
            if (!canInsertIntoSlot(container, slot, remainder, face)) {
                return@forEach
            }

            val existing = container.getItem(slot)
            if (existing.isEmpty) {
                val moved = min(remainder.count, min(remainder.maxStackSize, container.maxStackSize))
                val inserted = remainder.copy()
                inserted.count = moved
                container.setItem(slot, inserted)
                remainder.shrink(moved)
                container.setChanged()
            } else if (ItemStack.isSameItemSameComponents(existing, remainder)) {
                val maxStackSize = min(existing.maxStackSize, container.maxStackSize)
                val moved = min(remainder.count, maxStackSize - existing.count)
                if (moved > 0) {
                    existing.grow(moved)
                    remainder.shrink(moved)
                    container.setChanged()
                }
            }
        }

        return remainder
    }

    private fun canInsertIntoSlot(container: Container, slot: Int, stack: ItemStack, face: Direction): Boolean {
        if (!container.canPlaceItem(slot, stack)) {
            return false
        }
        return container !is WorldlyContainer || container.canPlaceItemThroughFace(slot, stack, face)
    }

    private fun hasContainerSpace(container: Container, face: Direction): Boolean {
        val slots = if (container is WorldlyContainer) {
            container.getSlotsForFace(face).toList()
        } else {
            (0 until container.containerSize).toList()
        }

        return slots.any { slot ->
            val probe = ItemStack.EMPTY
            val existing = container.getItem(slot)
            existing.isEmpty && (container !is WorldlyContainer || container.canPlaceItemThroughFace(slot, probe, face))
                || !existing.isEmpty && existing.count < min(existing.maxStackSize, container.maxStackSize)
        }
    }

    private fun ensureVisiblePokemon() {
        val world = level as? ServerLevel ?: return
        val waterEntry = storedPokemon.firstOrNull { it.slotRole == SlotRole.WATER }
        val grassEntry = storedPokemon.firstOrNull { it.slotRole == SlotRole.GRASS }
        val normalEntry = storedPokemon.firstOrNull { it.slotRole == SlotRole.NORMAL }
        listOfNotNull(waterEntry, grassEntry, normalEntry)
            .distinctBy { it.pokemonId }
            .forEach { ensureVisiblePokemon(world, it) }
    }

    private fun ensureVisiblePokemon(world: ServerLevel, entry: StoredPokemon) {
        val pokemon = Cobblemon.storage.getPC(entry.pcId, world.registryAccess())[entry.pokemonId] ?: return
        var existing = getPokemonEntity(pokemon)?.takeIf { it.isAlive && getTethering(it)?.tetheringId == entry.linkId }

        if (existing == null) {
            val searchBox = AABB(blockPos).inflate(CobblemonElectrifiedConfig.pokemonWanderRange.toDouble() + 10.0)
            val entities = world.getEntitiesOfClass(Entity::class.java, searchBox) { ent ->
                val tether = getTethering(ent)
                tether != null && tether.pokemonId == entry.pokemonId
            }
            if (entities.isNotEmpty()) {
                existing = entities.first()
                for (i in 1 until entities.size) {
                    entities[i].discard()
                }
                runCatching {
                    pokemon.javaClass.getMethod("setEntity", Entity::class.java).invoke(pokemon, existing)
                }
            }
        }

        if (!CobblemonElectrifiedConfig.showPokemonOutside) {
            if (existing != null) {
                existing.discard()
                if (pokemon.tetheringId == entry.linkId) {
                    pokemon.tetheringId = null
                }
            }
            return
        }

        if (existing != null) {
            keepCloseToFarm(world, existing, entry)
            return
        }

        pokemon.tetheringId = entry.linkId
        val entity = createPokemonEntity(world, pokemon) ?: return
        val spawnPos = findVisibleSpawnPos(world, entity) ?: return
        entity.setPos(spawnPos.center.subtract(0.0, 0.5, 0.0))
        if (world.addFreshEntity(entity)) {
            setTethering(entity, createVisibleTethering(entry, entity.id))
            keepCloseToFarm(world, entity, entry)
        }
    }

    private fun keepCloseToFarm(world: ServerLevel, entity: Entity, entry: StoredPokemon) {
        val tethering = getTethering(entity)
        if (tethering == null || tethering.pasturePos != blockPos || tethering.tetheringId != entry.linkId) {
            setTethering(entity, createVisibleTethering(entry, entity.id))
        }

        val mob = entity as? Mob
        if (mob != null && world.gameTime % 20 == 0L) {
            val wanderRange = CobblemonElectrifiedConfig.pokemonWanderRange
            if (mob.blockPosition().distManhattan(blockPos) > wanderRange) {
                findVisibleSpawnPos(world, entity)?.let { target ->
                    mob.navigation.moveTo(
                        target.x + 0.5,
                        target.y.toDouble(),
                        target.z + 0.5,
                        RETURN_SPEED
                    )
                }
            }
        }
    }

    private fun createVisibleTethering(entry: StoredPokemon, entityId: Int): PokemonPastureBlockEntity.Tethering {
        val wanderRange = CobblemonElectrifiedConfig.pokemonWanderRange
        val tetherRange = wanderRange + 8
        return object : PokemonPastureBlockEntity.Tethering(
            minRoamPos = blockPos.offset(-tetherRange, -3, -tetherRange),
            maxRoamPos = blockPos.offset(tetherRange + 1, 5, tetherRange + 1),
            playerId = entry.ownerId,
            playerName = entry.ownerName,
            tetheringId = entry.linkId,
            pokemonId = entry.pokemonId,
            pcId = entry.pcId,
            entityId = entityId,
            pasturePos = blockPos
        ) {
            override fun canRoamTo(pos: BlockPos): Boolean {
                val dist = pos.distManhattan(blockPos)
                return super.canRoamTo(pos) && dist in 2..wanderRange
            }
        }
    }

    private fun findVisibleSpawnPos(world: ServerLevel, entity: Entity): BlockPos? {
        val width = ceil(entity.boundingBox.xsize).toInt().coerceAtLeast(1)
        val directions = Direction.Plane.HORIZONTAL.toList().shuffled()
        val wanderRange = CobblemonElectrifiedConfig.pokemonWanderRange

        for (direction in directions) {
            val idealPlace = blockPos.relative(direction, width + 1)
            var box = entity.getDimensions(Pose.STANDING).makeBoundingBox(idealPlace.center.subtract(0.0, 0.5, 0.0))

            for (i in 0..wanderRange) {
                box = box.move(direction.stepX.toDouble(), 0.0, direction.stepZ.toDouble())
                val fixedPosition = makeSuitableY(world, idealPlace.relative(direction, i + 1), entity, box)
                if (fixedPosition != null) {
                    return fixedPosition
                }
            }
        }

        Cobblemon.LOGGER.warn("Could not find a safe visible spawn position for Farm Assistant at $blockPos")
        return null
    }

    private fun makeSuitableY(world: Level, pos: BlockPos, entity: Entity, box: AABB): BlockPos? {
        if (world.collidesWithSuffocatingBlock(entity, box)) {
            for (i in 1..15) {
                val newBox = box.move(0.5, i.toDouble(), 0.5)
                if (!world.collidesWithSuffocatingBlock(entity, newBox) && isSafeFloor(world, pos.offset(0, i - 1, 0), entity)) {
                    return pos.offset(0, i, 0)
                }
            }
        } else {
            for (i in 1..15) {
                val newBox = box.move(0.5, -i.toDouble(), 0.5)
                if (world.collidesWithSuffocatingBlock(entity, newBox) && isSafeFloor(world, pos.offset(0, -i, 0), entity)) {
                    return pos.offset(0, -i + 1, 0)
                }
            }
        }

        return null
    }

    private fun isSafeFloor(world: Level, pos: BlockPos, entity: Entity): Boolean {
        val state = world.getBlockState(pos)
        return !state.isAir && (
            state.entityCanStandOn(world, pos, entity) ||
                state.entityCanStandOnFace(world, pos, entity, Direction.DOWN)
            )
    }

    private fun createPokemonEntity(world: ServerLevel, pokemon: Pokemon): Entity? {
        return runCatching {
            val entityClass = Class.forName("com.cobblemon.mod.common.entity.pokemon.PokemonEntity")
            val entityType = Class.forName("com.cobblemon.mod.common.CobblemonEntities")
                .getField("POKEMON")
                .get(null) as EntityType<*>
            val constructor = entityClass.getConstructor(Level::class.java, Pokemon::class.java, EntityType::class.java)
            (constructor.newInstance(world, pokemon, entityType) as Entity).also {
                it.javaClass.getMethod("refreshDimensions").invoke(it)
            }
        }.getOrElse {
            Cobblemon.LOGGER.warn("Could not create visible Farm Assistant Pokemon entity", it)
            null
        }
    }

    private fun getPokemonEntity(pokemon: Pokemon): Entity? {
        return runCatching { pokemon.javaClass.getMethod("getEntity").invoke(pokemon) as? Entity }.getOrNull()
    }

    private fun getTethering(entity: Entity): PokemonPastureBlockEntity.Tethering? {
        return runCatching {
            entity.javaClass.getMethod("getTethering").invoke(entity) as? PokemonPastureBlockEntity.Tethering
        }.getOrNull()
    }

    private fun setTethering(entity: Entity, tethering: PokemonPastureBlockEntity.Tethering) {
        runCatching {
            entity.javaClass.getMethod("setTethering", PokemonPastureBlockEntity.Tethering::class.java)
                .invoke(entity, tethering)
        }
    }

    private fun wateringRange(): Int {
        val waterPower = storedPokemon.filter { it.slotRole == SlotRole.WATER }.sumOf { it.farmPower }
        if (waterPower <= 0) {
            return 0
        }

        return (BASE_WATERING_RANGE + waterPower / 128).coerceIn(BASE_WATERING_RANGE, MAX_WATERING_RANGE)
    }

    private fun isWatering(farmlandPos: BlockPos): Boolean {
        val range = wateringRange()
        val dx = kotlin.math.abs(farmlandPos.x - blockPos.x)
        val dy = kotlin.math.abs(farmlandPos.y - blockPos.y)
        val dz = kotlin.math.abs(farmlandPos.z - blockPos.z)
        return range > 0 && dy <= 2 && dx <= range && dz <= range
    }

    companion object {
        private const val RANGE = 5
        private const val RETURN_SPEED = 0.35
        private const val BASE_WATERING_RANGE = RANGE
        private const val MAX_WATERING_RANGE = 7
        private const val MAX_ACTIONS_PER_TICK = 16
        private const val MAX_HARVEST_MULTIPLIER = 8
        private const val MAX_COLLECTION_ITEMS_PER_TICK = 256
        private const val COLLECTION_BUFFER_SLOTS = 27

        @JvmStatic
        fun isWateredByAssistant(level: Level, farmlandPos: BlockPos): Boolean {
            return BlockPos.betweenClosed(
                farmlandPos.offset(-MAX_WATERING_RANGE, -2, -MAX_WATERING_RANGE),
                farmlandPos.offset(MAX_WATERING_RANGE, 2, MAX_WATERING_RANGE)
            ).any { pos ->
                val assistant = level.getBlockEntity(pos) as? FarmAssistantBlockEntity
                assistant?.isWatering(farmlandPos) == true
            }
        }

        private fun farmPowerFor(pokemon: Pokemon): Int {
            val attack = calculatedFarmPower(pokemon, Stats.ATTACK)
            val specialAttack = calculatedFarmPower(pokemon, Stats.SPECIAL_ATTACK)
            return attack.coerceAtLeast(specialAttack)
        }

        private fun calculatedFarmPower(pokemon: Pokemon, stat: Stat): Int {
            val baseStat = pokemon.form.baseStats[stat] ?: 0
            val iv = pokemon.ivs.getOrDefault(stat)
            val ev = pokemon.evs.getOrDefault(stat)
            val ivBonus = iv * CobblemonElectrifiedConfig.ivBonusPercentPerPoint / 100.0
            val evBonus = (ev / 4) * CobblemonElectrifiedConfig.evBonusPercentPer4Points / 100.0
            val levelMultiplier = levelMultiplier(pokemon.level)
            return (baseStat * (1.0 + ivBonus + evBonus) * levelMultiplier).toInt().coerceAtLeast(1)
        }

        private fun levelMultiplier(level: Int): Double {
            return if (level <= 50) {
                level.coerceAtLeast(1) / 50.0
            } else {
                1.0 + ((level.coerceAtMost(100) - 50) / 50.0)
            }
        }

        private fun actionCountFor(power: Int): Int {
            return (power / 50).coerceIn(1, MAX_ACTIONS_PER_TICK)
        }

        private fun harvestMultiplierFor(power: Int): Int {
            return (1 + power / 100).coerceIn(1, MAX_HARVEST_MULTIPLIER)
        }

        private fun collectionItemLimitFor(power: Int): Int {
            return (power / 8).coerceIn(1, MAX_COLLECTION_ITEMS_PER_TICK)
        }

        fun tick(level: Level, pos: BlockPos, state: BlockState, blockEntity: FarmAssistantBlockEntity) {
            if (level is ServerLevel) {
                blockEntity.ensureVisiblePokemon()
                if (level.gameTime % 40L == 0L) {
                    blockEntity.tickFarm(level)
                }
            }
        }
    }

    data class StoredPokemon(
        val ownerId: UUID,
        val pcId: UUID,
        val pokemonId: UUID,
        val linkId: UUID,
        val ownerName: String,
        val pokemonName: String,
        val water: Boolean,
        val grass: Boolean,
        val normal: Boolean,
        val slotRole: SlotRole,
        val farmPower: Int
    ) {
        fun toNbt(): CompoundTag {
            return CompoundTag().also {
                it.putUUID("OwnerId", ownerId)
                it.putUUID("PcId", pcId)
                it.putUUID("PokemonId", pokemonId)
                it.putUUID("LinkId", linkId)
                it.putString("OwnerName", ownerName)
                it.putString("PokemonName", pokemonName)
                it.putBoolean("Water", water)
                it.putBoolean("Grass", grass)
                it.putBoolean("Normal", normal)
                it.putString("SlotRole", slotRole.name)
                it.putInt("FarmPower", farmPower)
            }
        }

        companion object {
            fun fromNbt(nbt: CompoundTag): StoredPokemon {
                return StoredPokemon(
                    ownerId = nbt.getUUID("OwnerId"),
                    pcId = nbt.getUUID("PcId"),
                    pokemonId = nbt.getUUID("PokemonId"),
                    linkId = nbt.getUUID("LinkId"),
                    ownerName = nbt.getString("OwnerName"),
                    pokemonName = nbt.getString("PokemonName"),
                    water = nbt.getBoolean("Water"),
                    grass = nbt.getBoolean("Grass"),
                    normal = nbt.getBoolean("Normal"),
                    slotRole = SlotRole.fromNbt(nbt),
                    farmPower = nbt.getInt("FarmPower").takeIf { it > 0 } ?: 1
                )
            }
        }
    }

    enum class SlotRole {
        GRASS,
        WATER,
        NORMAL;

        companion object {
            fun fromNbt(nbt: CompoundTag): SlotRole {
                return runCatching { valueOf(nbt.getString("SlotRole")) }.getOrNull()
                    ?: when {
                        nbt.getBoolean("Grass") -> GRASS
                        nbt.getBoolean("Water") -> WATER
                        nbt.getBoolean("Normal") -> NORMAL
                        else -> GRASS
                    }
            }
        }
    }

    private data class CollectionBudget(var remainingItems: Int)
}
