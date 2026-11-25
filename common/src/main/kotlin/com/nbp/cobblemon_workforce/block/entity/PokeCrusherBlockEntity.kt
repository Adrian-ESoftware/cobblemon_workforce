package com.nbp.cobblemon_workforce.block.entity

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonEntities
import com.cobblemon.mod.common.api.pokemon.stats.Stat
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.api.types.ElementalTypes
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.net.messages.client.pasture.OpenPasturePacket
import com.cobblemon.mod.common.pokemon.Pokemon
import com.nbp.cobblemon_workforce.block.CobblemonWorkforceBlocks
import com.nbp.cobblemon_workforce.config.CobblemonWorkforceConfig
import com.nbp.cobblemon_workforce.pasture.WorkforceEntityHelper
import com.nbp.cobblemon_workforce.pasture.WorkforcePastureHost
import com.nbp.cobblemon_workforce.pokemon.PokemonWorkforceStatus
import com.nbp.cobblemon_workforce.pokemon.WorkforcePokemonEffects
import com.nbp.cobblemon_workforce.pokemon.WorkforcePokemonStats
import com.nbp.cobblemon_workforce.registry.ModRegistry
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
import net.minecraft.world.Container
import net.minecraft.world.WorldlyContainer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.Pose
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB

class PokeCrusherBlockEntity(pos: BlockPos, state: BlockState) :
    BlockEntity(CobblemonWorkforceBlocks.POKE_CRUSHER_BLOCK_ENTITY, pos, state),
    WorkforcePastureHost {

    override val pastureDisplayName: String = "Poke Crusher"
    override val wrongTypeMessage: String = "Only Steel-type and Normal-type Pokemon can help this crusher."
    override val slotUnavailableMessage: String = "This Poke Crusher does not have an available slot for that Pokemon type."
    override fun onPasturedMessage(pokemon: Pokemon): String = "${pokemon.getDisplayName().string} is now working at this Poke Crusher."

    var normalSlotUnlocked: Boolean = false
        private set

    private val storedPokemon = mutableListOf<StoredPokemon>()
    private val outputBuffer = mutableListOf<ItemStack>()
    private val pathAttempts = mutableMapOf<UUID, Int>()
    private var loadedAtTick: Long = -1L

    override fun isOwner(player: ServerPlayer): Boolean = storedPokemon.any { it.ownerId == player.uuid }

    override fun slotLimit(): Int = 1 + if (normalSlotUnlocked) 1 else 0

    override fun canAddPokemon(): Boolean = storedPokemon.none { it.role == SlotRole.STEEL } ||
        normalSlotUnlocked && storedPokemon.none { it.role == SlotRole.NORMAL }

    override fun accepts(pokemon: Pokemon): Boolean = pokemon.types.any { it == ElementalTypes.STEEL || it == ElementalTypes.NORMAL }

    override fun canAddPokemon(pokemon: Pokemon): Boolean = slotRoleFor(pokemon) != null

    fun canUpgradeNormalSlot(): Boolean = !normalSlotUnlocked

    fun upgradeNormalSlot(): Boolean {
        if (!canUpgradeNormalSlot()) return false
        normalSlotUnlocked = true
        setChanged()
        return true
    }

    fun saveUpgradeData(): CompoundTag = CompoundTag().also { it.putBoolean("NormalSlotUnlocked", normalSlotUnlocked) }

    fun loadUpgradeData(nbt: CompoundTag) {
        normalSlotUnlocked = nbt.getBoolean("NormalSlotUnlocked")
        setChanged()
    }

    private fun slotRoleFor(pokemon: Pokemon): SlotRole? {
        val hasSteel = pokemon.types.any { it == ElementalTypes.STEEL }
        val hasNormal = pokemon.types.any { it == ElementalTypes.NORMAL }
        return when {
            hasSteel && storedPokemon.none { it.role == SlotRole.STEEL } -> SlotRole.STEEL
            normalSlotUnlocked && hasNormal && storedPokemon.none { it.role == SlotRole.NORMAL } -> SlotRole.NORMAL
            else -> null
        }
    }

    override fun addPokemon(player: ServerPlayer, pokemon: Pokemon, pcId: UUID): Boolean {
        val role = slotRoleFor(pokemon) ?: return false
        if (storedPokemon.any { it.pokemonId == pokemon.uuid } || !accepts(pokemon)) {
            return false
        }
        val linkId = UUID.randomUUID()
        pokemon.tetheringId = linkId
        storedPokemon += StoredPokemon(
            ownerId = player.uuid,
            pcId = pcId,
            pokemonId = pokemon.uuid,
            linkId = linkId,
            ownerName = player.gameProfile.name,
            pokemonName = pokemon.getDisplayName().string,
            role = role,
            level = pokemon.level,
            power = crusherPowerFor(pokemon)
        )
        setChanged()
        ensureVisiblePokemon()
        return true
    }

    override fun removePokemon(pokemonId: UUID): Boolean {
        val stored = storedPokemon.firstOrNull { it.pokemonId == pokemonId } ?: return false
        releaseLinkedPokemon(stored)
        storedPokemon.remove(stored)
        setChanged()
        ensureVisiblePokemon()
        return true
    }

    override fun ownsPokemon(player: ServerPlayer, pokemonId: UUID): Boolean {
        return storedPokemon.any { it.ownerId == player.uuid && it.pokemonId == pokemonId }
    }

    override fun storedPokemonIdsFor(player: ServerPlayer): List<UUID> {
        return storedPokemon.filter { it.ownerId == player.uuid }.map { it.pokemonId }
    }

    fun releaseLinkedPokemon() {
        storedPokemon.toList().forEach(::releaseLinkedPokemon)
    }

    private fun releaseLinkedPokemon(entry: StoredPokemon) {
        pathAttempts.remove(entry.pokemonId)
        val registryAccess = level?.server?.registryAccess() ?: return
        val pokemon = Cobblemon.storage.getPC(entry.pcId, registryAccess)[entry.pokemonId] ?: return
        getPokemonEntity(pokemon)
            ?.takeIf { getTethering(it)?.tetheringId == entry.linkId }
            ?.discard()
        if (pokemon.tetheringId == entry.linkId) {
            pokemon.tetheringId = null
        }
    }

    override fun toPastureDTOs(viewer: ServerPlayer): List<OpenPasturePacket.PasturePokemonDataDTO> {
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

    fun dropStoredItems(world: ServerLevel, pos: BlockPos) {
        outputBuffer.filterNot { it.isEmpty }.forEach { Block.popResource(world, pos, it.copy()) }
        outputBuffer.clear()
        setChanged()
    }

    override fun loadAdditional(nbt: CompoundTag, registryLookup: HolderLookup.Provider) {
        super.loadAdditional(nbt, registryLookup)
        normalSlotUnlocked = nbt.getBoolean("NormalSlotUnlocked")
        storedPokemon.clear()
        val list = nbt.getList("Pokemon", CompoundTag.TAG_COMPOUND.toInt())
        for (tag in list) {
            tag as CompoundTag
            storedPokemon += StoredPokemon.fromNbt(tag)
        }
        if (storedPokemon.any { it.role == SlotRole.NORMAL }) normalSlotUnlocked = true
        outputBuffer.clear()
        val bufferList = nbt.getList("OutputBuffer", CompoundTag.TAG_COMPOUND.toInt())
        for (tag in bufferList) {
            tag as CompoundTag
            ItemStack.parseOptional(registryLookup, tag).takeUnless { it.isEmpty }?.let(outputBuffer::add)
        }
    }

    override fun saveAdditional(nbt: CompoundTag, registryLookup: HolderLookup.Provider) {
        super.saveAdditional(nbt, registryLookup)
        nbt.putBoolean("NormalSlotUnlocked", normalSlotUnlocked)
        val list = ListTag()
        storedPokemon.forEach { list.add(it.toNbt()) }
        nbt.put("Pokemon", list)
        val bufferList = ListTag()
        outputBuffer.filterNot { it.isEmpty }.forEach { bufferList.add(it.saveOptional(registryLookup)) }
        nbt.put("OutputBuffer", bufferList)
    }

    private fun tickCrusher(world: ServerLevel) {
        if (loadedAtTick < 0L) loadedAtTick = world.gameTime
        ensureVisiblePokemon()
        val pokemon = storedPokemon.firstOrNull {
            it.role == SlotRole.STEEL && PokemonWorkforceStatus.isAwake(world, it.pcId, it.pokemonId)
        } ?: return
        val normal = storedPokemon.firstOrNull {
            it.role == SlotRole.NORMAL && PokemonWorkforceStatus.isAwake(world, it.pcId, it.pokemonId)
        }
        if (normal != null) {
            val normalProfile = WorkforcePokemonStats.profileFor(world, normal.pcId, normal.pokemonId)
            val transferLimit = (itemLimitFor(normal.power) * normalProfile.yieldMultiplier).toInt().coerceAtLeast(1)
            flushOutputBuffer(world, transferLimit)
        }
        val steelProfile = WorkforcePokemonStats.profileFor(world, pokemon.pcId, pokemon.pokemonId)
        val interval = steelProfile.adjustedInterval(intervalFor(pokemon.level), minInterval = 12, maxInterval = 240)
        if (world.gameTime % interval.toLong() != 0L) {
            return
        }

        val nearbyItem = nearestCrushableItem(world, rangeFor(pokemon.power).toDouble())
        if (nearbyItem != null) {
            val entity = visiblePokemonEntity(world, pokemon)
            if (entity == null || !isNearItem(entity, nearbyItem)) {
                val attempts = (pathAttempts[pokemon.pokemonId] ?: 0) + 1
                pathAttempts[pokemon.pokemonId] = attempts
                if (movePokemonToItem(world, pokemon, entity, nearbyItem) && attempts < MAX_PATH_ATTEMPTS) {
                    return
                }
            }
            pathAttempts.remove(pokemon.pokemonId)
            (entity ?: visiblePokemonEntity(world, pokemon))?.let {
                WorkforcePokemonEffects.playSteelCrushWork(world, it, nearbyItem.blockPosition())
            }
            crushItemEntity(world, nearbyItem, normal != null, steelProfile)
            return
        }

        pathAttempts.remove(pokemon.pokemonId)
        crushFromAdjacentInventory(world, pokemon, normal != null, steelProfile)
    }

    private fun nearestCrushableItem(world: ServerLevel, range: Double): ItemEntity? {
        return world.getEntitiesOfClass(ItemEntity::class.java, AABB(blockPos).inflate(range)) {
            it.isAlive && !it.item.isEmpty && crushResult(it.item) != null
        }.minByOrNull { it.distanceToSqr(blockPos.x + 0.5, blockPos.y + 0.5, blockPos.z + 0.5) }
    }

    private fun crushItemEntity(
        world: ServerLevel,
        itemEntity: ItemEntity,
        canTransfer: Boolean,
        profile: WorkforcePokemonStats.PerformanceProfile
    ): Boolean {
        val result = crushResult(itemEntity.item, world, profile) ?: return false
        val input = itemEntity.item
        input.shrink(1)
        if (input.isEmpty) {
            itemEntity.discard()
        } else {
            itemEntity.item = input
        }
        routeOutput(world, itemEntity.blockPosition(), result, canTransfer)
        return true
    }

    private fun crushFromAdjacentInventory(
        world: ServerLevel,
        pokemon: StoredPokemon,
        canTransfer: Boolean,
        profile: WorkforcePokemonStats.PerformanceProfile
    ): Boolean {
        adjacentInventoryPositions().forEach { (pos, face) ->
            val container = world.getBlockEntity(pos) as? Container ?: return@forEach
            val slots = if (container is WorldlyContainer) container.getSlotsForFace(face).toList() else (0 until container.containerSize).toList()
            slots.forEach { slot ->
                val stack = container.getItem(slot)
                val result = crushResult(stack, world, profile) ?: return@forEach
                visiblePokemonEntity(world, pokemon)?.let {
                    WorkforcePokemonEffects.playSteelCrushWork(world, it, pos)
                }
                stack.shrink(1)
                container.setChanged()
                val dropPos = if (canTransfer) blockPos else blockPos.above()
                routeOutput(world, dropPos, result, canTransfer)
                return true
            }
        }
        return false
    }

    private fun crushResult(
        input: ItemStack,
        world: ServerLevel? = null,
        profile: WorkforcePokemonStats.PerformanceProfile? = null
    ): ItemStack? {
        if (input.isEmpty) {
            return null
        }
        val base = CRUSHING_RESULTS[input.item]?.copy() ?: return null
        if (world != null && profile != null) {
            val factor = profile.rollCrushFactor(world.random)
            base.count = (base.count * factor).toInt().coerceAtLeast(1)
        }
        return base
    }

    private fun routeOutput(world: ServerLevel, sourcePos: BlockPos, stack: ItemStack, canTransfer: Boolean) {
        if (!canTransfer) {
            Block.popResource(world, sourcePos, stack)
            return
        }
        val remainder = insertIntoAdjacentInventories(world, stack)
        if (!remainder.isEmpty) {
            addToOutputBuffer(remainder)
        }
    }

    private fun flushOutputBuffer(world: ServerLevel, itemLimit: Int) {
        var remaining = itemLimit
        val iterator = outputBuffer.listIterator()
        while (iterator.hasNext() && remaining > 0) {
            val buffered = iterator.next()
            val moving = buffered.copy()
            val amount = min(moving.count, remaining)
            moving.count = amount
            val remainder = insertIntoAdjacentInventories(world, moving)
            val moved = amount - remainder.count
            if (moved > 0) {
                buffered.shrink(moved)
                remaining -= moved
                setChanged()
            }
            if (buffered.isEmpty) {
                iterator.remove()
            }
        }
    }

    private fun addToOutputBuffer(stack: ItemStack): ItemStack {
        var remainder = stack.copy()
        outputBuffer.forEach { existing ->
            if (remainder.isEmpty) return@forEach
            if (ItemStack.isSameItemSameComponents(existing, remainder)) {
                val moved = min(remainder.count, existing.maxStackSize - existing.count)
                if (moved > 0) {
                    existing.grow(moved)
                    remainder.shrink(moved)
                }
            }
        }
        while (!remainder.isEmpty && outputBuffer.size < OUTPUT_BUFFER_SLOTS) {
            val buffered = remainder.copy()
            buffered.count = min(remainder.count, remainder.maxStackSize)
            outputBuffer += buffered
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
            if (remainder.isEmpty) return@forEach
            val container = world.getBlockEntity(pos) as? Container ?: return@forEach
            remainder = insertIntoContainer(container, remainder, face)
        }
        return remainder
    }

    private fun adjacentInventoryPositions(): List<Pair<BlockPos, Direction>> {
        val positions = linkedSetOf<Pair<BlockPos, Direction>>()
        listOf(blockPos, blockPos.above()).forEach { base ->
            Direction.entries.forEach { direction ->
                val inventoryPos = base.relative(direction)
                if (inventoryPos != blockPos && inventoryPos != blockPos.above()) {
                    positions += inventoryPos to direction.opposite
                }
            }
        }
        return positions.toList()
    }

    private fun insertIntoContainer(container: Container, stack: ItemStack, face: Direction): ItemStack {
        val remainder = stack.copy()
        val slots = if (container is WorldlyContainer) container.getSlotsForFace(face).toList() else (0 until container.containerSize).toList()
        slots.forEach { slot ->
            if (remainder.isEmpty) return@forEach
            if (!container.canPlaceItem(slot, remainder) || container is WorldlyContainer && !container.canPlaceItemThroughFace(slot, remainder, face)) {
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

    private fun ensureVisiblePokemon() {
        val world = level as? ServerLevel ?: return
        val steel = storedPokemon.firstOrNull { it.role == SlotRole.STEEL }
        val normal = storedPokemon.firstOrNull { it.role == SlotRole.NORMAL }
        listOfNotNull(steel, normal).forEach { ensureVisiblePokemon(world, it) }
    }

    private fun ensureVisiblePokemon(world: ServerLevel, entry: StoredPokemon) {
        WorkforceEntityHelper.ensureVisiblePokemon(
            world = world,
            blockPos = blockPos,
            loadedAtTick = loadedAtTick,
            pokemonId = entry.pokemonId,
            pcId = entry.pcId,
            linkId = entry.linkId,
            showPokemonOutside = CobblemonWorkforceConfig.showPokemonOutside,
            createTethering = { entityId -> createVisibleTethering(entry, entityId) },
            findSpawnPos = { entity -> findVisibleSpawnPos(world, entity) },
            loggerContext = "Poke Crusher"
        )
    }

    private fun visiblePokemonEntity(world: ServerLevel, entry: StoredPokemon): Entity? {
        val pokemon = Cobblemon.storage.getPC(entry.pcId, world.registryAccess())[entry.pokemonId] ?: return null
        return getPokemonEntity(pokemon)?.takeIf { it.isAlive && getTethering(it)?.tetheringId == entry.linkId }
    }

    private fun movePokemonToItem(world: ServerLevel, entry: StoredPokemon, entity: Entity?, item: ItemEntity): Boolean {
        val visible = entity ?: run {
            ensureVisiblePokemon(world, entry)
            visiblePokemonEntity(world, entry)
        } ?: return false
        keepTethered(visible, entry)
        return (visible as? Mob)?.navigation?.moveTo(item.x, item.y, item.z, WORK_SPEED) == true
    }

    private fun isNearItem(entity: Entity, item: ItemEntity): Boolean = entity.distanceToSqr(item.x, item.y, item.z) <= WORK_DISTANCE_SQR

    private fun keepTethered(entity: Entity, entry: StoredPokemon) {
        val tethering = getTethering(entity)
        if (tethering == null || tethering.pasturePos != blockPos || tethering.tetheringId != entry.linkId) {
            setTethering(entity, createVisibleTethering(entry, entity.id))
        }
    }

    private fun createVisibleTethering(entry: StoredPokemon, entityId: Int): PokemonPastureBlockEntity.Tethering {
        val range = rangeFor(entry.power) + 4
        return object : PokemonPastureBlockEntity.Tethering(
            minRoamPos = blockPos.offset(-range, -3, -range),
            maxRoamPos = blockPos.offset(range + 1, range + 5, range + 1),
            playerId = entry.ownerId,
            playerName = entry.ownerName,
            tetheringId = entry.linkId,
            pokemonId = entry.pokemonId,
            pcId = entry.pcId,
            entityId = entityId,
            pasturePos = blockPos
        ) {
            override fun canRoamTo(pos: BlockPos): Boolean {
                val dx = kotlin.math.abs(pos.x - blockPos.x)
                val dz = kotlin.math.abs(pos.z - blockPos.z)
                return super.canRoamTo(pos) && dx <= range && dz <= range
            }
        }
    }

    private fun findVisibleSpawnPos(world: ServerLevel, entity: Entity): BlockPos? {
        val width = ceil(entity.boundingBox.xsize).toInt().coerceAtLeast(1)
        val directions = listOf(Direction.NORTH, Direction.EAST, Direction.WEST, Direction.SOUTH)
        val wanderRange = CobblemonWorkforceConfig.pokemonWanderRange
        for (direction in directions) {
            val ideal = blockPos.relative(direction, width + 1)
            var box = entity.getDimensions(Pose.STANDING).makeBoundingBox(ideal.center.subtract(0.0, 0.5, 0.0))
            for (i in 0..wanderRange) {
                box = box.move(direction.stepX.toDouble(), 0.0, direction.stepZ.toDouble())
                val fixed = makeSuitableY(world, ideal.relative(direction, i + 1), entity, box)
                if (fixed != null) return fixed
            }
        }
        return null
    }

    private fun makeSuitableY(world: Level, pos: BlockPos, entity: Entity, box: AABB): BlockPos? {
        if (world.collidesWithSuffocatingBlock(entity, box)) {
            for (i in 1..15) {
                val newBox = box.move(0.5, i.toDouble(), 0.5)
                if (!world.collidesWithSuffocatingBlock(entity, newBox) && isSafeFloor(world, pos.offset(0, i - 1, 0), entity)) return pos.offset(0, i, 0)
            }
        } else {
            for (i in 1..15) {
                val newBox = box.move(0.5, -i.toDouble(), 0.5)
                if (world.collidesWithSuffocatingBlock(entity, newBox) && isSafeFloor(world, pos.offset(0, -i, 0), entity)) return pos.offset(0, -i + 1, 0)
            }
        }
        return null
    }

    private fun isSafeFloor(world: Level, pos: BlockPos, entity: Entity): Boolean {
        val state = world.getBlockState(pos)
        return !state.isAir && (state.entityCanStandOn(world, pos, entity) || state.entityCanStandOnFace(world, pos, entity, Direction.DOWN))
    }

    private fun createPokemonEntity(world: ServerLevel, pokemon: Pokemon): PokemonEntity? {
        return runCatching {
            val entity = PokemonEntity(world, pokemon, CobblemonEntities.POKEMON)
            entity.refreshDimensions()
            entity
        }.getOrElse {
            Cobblemon.LOGGER.warn("Could not create visible Poke Crusher Pokemon entity", it)
            null
        }
    }

    private fun getPokemonEntity(pokemon: Pokemon): PokemonEntity? = pokemon.entity

    private fun getTethering(entity: Entity): PokemonPastureBlockEntity.Tethering? {
        return (entity as? PokemonEntity)?.tethering
    }

    private fun setTethering(entity: Entity, tethering: PokemonPastureBlockEntity.Tethering) {
        (entity as? PokemonEntity)?.tethering = tethering
    }

    companion object {
        private const val OUTPUT_BUFFER_SLOTS = 27
        private const val MAX_PATH_ATTEMPTS = 4
        private const val WORK_DISTANCE_SQR = 6.25
        private const val WORK_SPEED = 0.55

        fun tick(level: Level, pos: BlockPos, state: BlockState, blockEntity: PokeCrusherBlockEntity) {
            if (level is ServerLevel) {
                blockEntity.tickCrusher(level)
                val hasPokemon = blockEntity.storedPokemon.any { com.nbp.cobblemon_workforce.pokemon.PokemonWorkforceStatus.isAwake(level, it.pcId, it.pokemonId) }
                if (state.getValue(com.nbp.cobblemon_workforce.block.PokeGeneratorBlock.ON) != hasPokemon) {
                    level.setBlock(pos, state.setValue(com.nbp.cobblemon_workforce.block.PokeGeneratorBlock.ON, hasPokemon), 3)
                }
            }
        }

        private fun crusherPowerFor(pokemon: Pokemon): Int {
            return calculatedPower(pokemon, Stats.ATTACK).coerceAtLeast(calculatedPower(pokemon, Stats.DEFENCE))
        }

        private fun calculatedPower(pokemon: Pokemon, stat: Stat): Int {
            val baseStat = pokemon.form.baseStats[stat] ?: 0
            val iv = pokemon.ivs.getOrDefault(stat)
            val ev = pokemon.evs.getOrDefault(stat)
            val ivBonus = iv * CobblemonWorkforceConfig.ivBonusPercentPerPoint / 100.0
            val evBonus = (ev / 4) * CobblemonWorkforceConfig.evBonusPercentPer4Points / 100.0
            val levelMultiplier = if (pokemon.level <= 50) pokemon.level.coerceAtLeast(1) / 50.0 else 1.0 + ((pokemon.level.coerceAtMost(100) - 50) / 50.0)
            return (baseStat * (1.0 + ivBonus + evBonus) * levelMultiplier).toInt().coerceAtLeast(1)
        }

        private fun rangeFor(power: Int): Int = (3 + power / 96).coerceIn(3, 8)

        private fun intervalFor(level: Int): Int {
            val clamped = level.coerceIn(1, 100)
            return if (clamped <= 50) 120 - ((clamped - 1) * 70) / 49 else 50 - ((clamped - 50) * 20) / 50
        }

        private fun itemLimitFor(power: Int): Int = (4 + power / 10).coerceIn(4, 64)

        private val CRUSHING_RESULTS: Map<Item, ItemStack> by lazy {
            mapOf(
                Items.RAW_IRON to ItemStack(ModRegistry.IRON_DUST.get(), 2),
                Items.IRON_ORE to ItemStack(ModRegistry.IRON_DUST.get(), 2),
                Items.DEEPSLATE_IRON_ORE to ItemStack(ModRegistry.IRON_DUST.get(), 2),
                Items.RAW_COPPER to ItemStack(ModRegistry.COPPER_DUST.get(), 2),
                Items.COPPER_ORE to ItemStack(ModRegistry.COPPER_DUST.get(), 2),
                Items.DEEPSLATE_COPPER_ORE to ItemStack(ModRegistry.COPPER_DUST.get(), 2),
                Items.RAW_GOLD to ItemStack(ModRegistry.GOLD_DUST.get(), 2),
                Items.GOLD_ORE to ItemStack(ModRegistry.GOLD_DUST.get(), 2),
                Items.DEEPSLATE_GOLD_ORE to ItemStack(ModRegistry.GOLD_DUST.get(), 2),
                Items.NETHER_GOLD_ORE to ItemStack(ModRegistry.GOLD_DUST.get(), 2),
                Items.NETHER_QUARTZ_ORE to ItemStack(Items.QUARTZ, 2),
                Items.ANCIENT_DEBRIS to ItemStack(Items.NETHERITE_SCRAP, 2),
                Items.COBBLESTONE to ItemStack(Items.GRAVEL, 1),
                Items.COBBLED_DEEPSLATE to ItemStack(Items.GRAVEL, 1),
                Items.GRAVEL to ItemStack(Items.SAND, 1),
                Items.SANDSTONE to ItemStack(Items.SAND, 4),
                Items.RED_SANDSTONE to ItemStack(Items.RED_SAND, 4)
            )
        }
    }

    data class StoredPokemon(
        val ownerId: UUID,
        val pcId: UUID,
        val pokemonId: UUID,
        val linkId: UUID,
        val ownerName: String,
        val pokemonName: String,
        val role: SlotRole,
        val level: Int,
        val power: Int
    ) {
        fun toNbt(): CompoundTag {
            return CompoundTag().also {
                it.putUUID("OwnerId", ownerId)
                it.putUUID("PcId", pcId)
                it.putUUID("PokemonId", pokemonId)
                it.putUUID("LinkId", linkId)
                it.putString("OwnerName", ownerName)
                it.putString("PokemonName", pokemonName)
                it.putString("Role", role.name)
                it.putInt("Level", level)
                it.putInt("Power", power)
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
                    role = runCatching { SlotRole.valueOf(nbt.getString("Role")) }.getOrDefault(SlotRole.STEEL),
                    level = nbt.getInt("Level").takeIf { it > 0 } ?: 1,
                    power = nbt.getInt("Power").takeIf { it > 0 } ?: 1
                )
            }
        }
    }

    enum class SlotRole {
        STEEL,
        NORMAL
    }
}
