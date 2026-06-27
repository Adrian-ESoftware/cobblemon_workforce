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
import com.nbp.cobblemon_workforce.pokemon.PokemonWorkforceStatus
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.min
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.BlockTags
import net.minecraft.world.Container
import net.minecraft.world.WorldlyContainer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.Pose
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB

class PokeLumberBlockEntity(pos: BlockPos, state: BlockState) :
    BlockEntity(CobblemonWorkforceBlocks.POKE_LUMBER_BLOCK_ENTITY, pos, state) {

    var normalSlotUnlocked: Boolean = false
        private set

    var plantSlotUnlocked: Boolean = false
        private set

    private val storedPokemon = mutableListOf<StoredPokemon>()
    private val collectionBuffer = mutableListOf<ItemStack>()
    private val pathAttempts = mutableMapOf<UUID, Int>()

    fun isOwner(player: ServerPlayer): Boolean = storedPokemon.any { it.ownerId == player.uuid }

    fun slotLimit(): Int = 1 + (if (normalSlotUnlocked) 1 else 0) + (if (plantSlotUnlocked) 1 else 0)

    fun canAddPokemon(): Boolean = storedPokemon.none { it.role == SlotRole.FIGHTING } ||
        normalSlotUnlocked && storedPokemon.none { it.role == SlotRole.NORMAL } ||
        plantSlotUnlocked && storedPokemon.none { it.role == SlotRole.GRASS }

    fun canAddPokemon(pokemon: Pokemon): Boolean = slotRoleFor(pokemon) != null

    fun accepts(pokemon: Pokemon): Boolean {
        return pokemon.types.any { it == ElementalTypes.FIGHTING || it == ElementalTypes.NORMAL || it == ElementalTypes.GRASS }
    }

    fun canUpgradeNormalSlot(): Boolean = !normalSlotUnlocked

    fun canUpgradePlantSlot(): Boolean = !plantSlotUnlocked

    fun upgradeNormalSlot(): Boolean {
        if (!canUpgradeNormalSlot()) {
            return false
        }
        normalSlotUnlocked = true
        setChanged()
        return true
    }

    fun upgradePlantSlot(): Boolean {
        if (!canUpgradePlantSlot()) {
            return false
        }
        plantSlotUnlocked = true
        setChanged()
        return true
    }

    fun saveUpgradeData(): CompoundTag {
        return CompoundTag().also {
            it.putBoolean("NormalSlotUnlocked", normalSlotUnlocked)
            it.putBoolean("PlantSlotUnlocked", plantSlotUnlocked)
        }
    }

    fun loadUpgradeData(nbt: CompoundTag) {
        normalSlotUnlocked = nbt.getBoolean("NormalSlotUnlocked")
        plantSlotUnlocked = nbt.getBoolean("PlantSlotUnlocked")
        setChanged()
    }

    private fun slotRoleFor(pokemon: Pokemon): SlotRole? {
        val hasFighting = pokemon.types.any { it == ElementalTypes.FIGHTING }
        val hasNormal = pokemon.types.any { it == ElementalTypes.NORMAL }
        val hasGrass = pokemon.types.any { it == ElementalTypes.GRASS }
        return when {
            hasFighting && storedPokemon.none { it.role == SlotRole.FIGHTING } -> SlotRole.FIGHTING
            normalSlotUnlocked && hasNormal && storedPokemon.none { it.role == SlotRole.NORMAL } -> SlotRole.NORMAL
            plantSlotUnlocked && hasGrass && storedPokemon.none { it.role == SlotRole.GRASS } -> SlotRole.GRASS
            else -> null
        }
    }

    fun addPokemon(player: ServerPlayer, pokemon: Pokemon, pcId: UUID): Boolean {
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
            power = woodPowerFor(pokemon)
        )
        setChanged()
        ensureVisiblePokemon()
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

    fun dropStoredItems(world: ServerLevel, pos: BlockPos) {
        collectionBuffer.filterNot { it.isEmpty }.forEach { Block.popResource(world, pos, it.copy()) }
        collectionBuffer.clear()
        setChanged()
    }

    override fun loadAdditional(nbt: CompoundTag, registryLookup: HolderLookup.Provider) {
        super.loadAdditional(nbt, registryLookup)
        normalSlotUnlocked = nbt.getBoolean("NormalSlotUnlocked")
        plantSlotUnlocked = nbt.getBoolean("PlantSlotUnlocked")
        storedPokemon.clear()
        val list = nbt.getList("Pokemon", CompoundTag.TAG_COMPOUND.toInt())
        for (tag in list) {
            tag as CompoundTag
            storedPokemon += StoredPokemon.fromNbt(tag)
        }
        if (storedPokemon.any { it.role == SlotRole.NORMAL }) {
            normalSlotUnlocked = true
        }
        if (storedPokemon.any { it.role == SlotRole.GRASS }) {
            plantSlotUnlocked = true
        }
        collectionBuffer.clear()
        val bufferList = nbt.getList("CollectionBuffer", CompoundTag.TAG_COMPOUND.toInt())
        for (tag in bufferList) {
            tag as CompoundTag
            ItemStack.parseOptional(registryLookup, tag).takeUnless { it.isEmpty }?.let(collectionBuffer::add)
        }
    }

    override fun saveAdditional(nbt: CompoundTag, registryLookup: HolderLookup.Provider) {
        super.saveAdditional(nbt, registryLookup)
        nbt.putBoolean("NormalSlotUnlocked", normalSlotUnlocked)
        nbt.putBoolean("PlantSlotUnlocked", plantSlotUnlocked)
        val list = ListTag()
        storedPokemon.forEach { list.add(it.toNbt()) }
        nbt.put("Pokemon", list)
        val bufferList = ListTag()
        collectionBuffer.filterNot { it.isEmpty }.forEach { bufferList.add(it.saveOptional(registryLookup)) }
        nbt.put("CollectionBuffer", bufferList)
    }

    private fun tickWood(world: ServerLevel) {
        ensureVisiblePokemon()
        val awakePokemon = storedPokemon.filter { PokemonWorkforceStatus.isAwake(world, it.pcId, it.pokemonId) }
        val fighter = awakePokemon.firstOrNull { it.role == SlotRole.FIGHTING }
        val fightingPower = fighter?.power ?: 0
        val normalPower = awakePokemon.filter { it.role == SlotRole.NORMAL }.sumOf { it.power }
        val collectionBudget = normalPower.takeIf { it > 0 }?.let { CollectionBudget(collectionItemLimitFor(it)) }
        if (collectionBudget != null) {
            flushCollectionBuffer(world, collectionBudget)
        }
        if (fighter == null || fightingPower <= 0 || world.gameTime % harvestIntervalForLevel(fighter.level) != 0L) {
            return
        }

        val target = findNearestLog(world, rangeForLevel(fighter.level)) ?: return
        tryHarvestWood(world, fighter, target, collectionBudget)
    }

    private fun findNearestLog(world: ServerLevel, range: Int): BlockPos? {
        return BlockPos.betweenClosed(blockPos.offset(-range, -2, -range), blockPos.offset(range, range, range))
            .map { it.immutable() }
            .filter { world.getBlockState(it).`is`(BlockTags.LOGS) }
            .minByOrNull { it.distManhattan(blockPos) }
    }

    private fun tryHarvestWood(world: ServerLevel, fighter: StoredPokemon, pos: BlockPos, collectionBudget: CollectionBudget?): Boolean {
        val fighterEntity = visiblePokemonEntity(world, fighter)
        if (fighterEntity == null || !isNearLog(fighterEntity, pos)) {
            val attempts = (pathAttempts[fighter.pokemonId] ?: 0) + 1
            pathAttempts[fighter.pokemonId] = attempts
            if (movePokemonToLog(world, fighter, fighterEntity, pos) && attempts < MAX_PATH_ATTEMPTS) {
                return false
            }
        }

        pathAttempts.remove(fighter.pokemonId)
        val hasPlantHelper = storedPokemon.any {
            it.role == SlotRole.GRASS && PokemonWorkforceStatus.isAwake(world, it.pcId, it.pokemonId)
        }
        val logLimit = MAX_TREE_LOGS
        val leafLimit = if (hasPlantHelper) MAX_TREE_LEAVES else 0
        val blocks = connectedTreeBlocks(world, pos, logLimit, leafLimit)
        if (blocks.isEmpty()) {
            return false
        }

        val replantTargets = if (hasPlantHelper) replantTargetsFor(blocks) else emptyList()
        blocks.forEach { harvestTreeBlock(world, it.pos, collectionBudget) }
        replantTargets.forEach { (targetPos, saplingState) -> replantSapling(world, targetPos, saplingState) }
        return true
    }

    private fun harvestTreeBlock(world: ServerLevel, pos: BlockPos, collectionBudget: CollectionBudget?): Boolean {
        val state = world.getBlockState(pos)
        if (!state.`is`(BlockTags.LOGS) && !state.`is`(BlockTags.LEAVES)) {
            return false
        }
        if (collectionBudget != null && collectionBudget.remainingItems <= 0 && !collectionBufferHasSpace()) {
            return false
        }

        val drops = Block.getDrops(state, world, pos, world.getBlockEntity(pos))
        routeDrops(world, pos, drops, collectionBudget)
        world.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS)
        return true
    }

    private fun connectedTreeBlocks(world: ServerLevel, start: BlockPos, logLimit: Int, leafLimit: Int): List<TreeHarvestBlock> {
        val origin = start.immutable()
        val originBase = logColumnBase(world, origin)
        val visited = mutableSetOf<BlockPos>()
        val logs = mutableListOf<TreeHarvestBlock>()
        val leaves = mutableListOf<TreeHarvestBlock>()
        val queue = ArrayDeque<BlockPos>()
        queue += start
        while (queue.isNotEmpty() && visited.size < MAX_TREE_SCAN_BLOCKS && (logs.size < logLimit || leaves.size < leafLimit)) {
            val current = queue.removeFirst().immutable()
            if (!visited.add(current)) {
                continue
            }
            if (!isInsideSingleTreeBounds(current, origin)) {
                continue
            }
            val state = world.getBlockState(current)
            val isLog = state.`is`(BlockTags.LOGS)
            if (!isLog && !state.`is`(BlockTags.LEAVES)) {
                continue
            }
            if (isLog && isSeparateNeighborTrunk(world, current, originBase)) {
                continue
            }
            if (isLog && logs.size < logLimit) {
                logs += TreeHarvestBlock(current, state)
            } else if (!isLog && leaves.size < leafLimit) {
                leaves += TreeHarvestBlock(current, state)
            }
            TREE_NEIGHBOR_OFFSETS.forEach { offset ->
                val next = current.offset(offset)
                if (next !in visited) {
                    queue += next
                }
            }
        }
        return logs + leaves
    }

    private fun isInsideSingleTreeBounds(pos: BlockPos, origin: BlockPos): Boolean {
        val dx = kotlin.math.abs(pos.x - origin.x)
        val dz = kotlin.math.abs(pos.z - origin.z)
        val dy = pos.y - origin.y
        return dx <= SINGLE_TREE_HORIZONTAL_RADIUS &&
            dz <= SINGLE_TREE_HORIZONTAL_RADIUS &&
            dy >= -SINGLE_TREE_BELOW_RANGE &&
            dy <= SINGLE_TREE_ABOVE_RANGE
    }

    private fun isSeparateNeighborTrunk(world: ServerLevel, pos: BlockPos, originBase: BlockPos): Boolean {
        val base = logColumnBase(world, pos)
        return base != originBase && verticalLogColumnHeight(world, base) >= NEIGHBOR_TRUNK_MIN_HEIGHT
    }

    private fun logColumnBase(world: ServerLevel, pos: BlockPos): BlockPos {
        var base = pos.immutable()
        while (world.getBlockState(base.below()).`is`(BlockTags.LOGS)) {
            base = base.below()
        }
        return base
    }

    private fun verticalLogColumnHeight(world: ServerLevel, base: BlockPos): Int {
        var height = 0
        var cursor = base
        while (world.getBlockState(cursor).`is`(BlockTags.LOGS) && height < NEIGHBOR_TRUNK_MIN_HEIGHT) {
            height++
            cursor = cursor.above()
        }
        return height
    }

    private fun replantTargetsFor(blocks: List<TreeHarvestBlock>): List<Pair<BlockPos, BlockState>> {
        val harvestedLogs = blocks
            .filter { it.state.`is`(BlockTags.LOGS) }
            .associateBy { it.pos }

        return harvestedLogs.values.mapNotNull { harvested ->
            if (harvested.pos.below() in harvestedLogs) {
                return@mapNotNull null
            }
            saplingStateForLog(harvested.state)?.let { harvested.pos to it }
        }
    }

    private fun replantSapling(world: ServerLevel, pos: BlockPos, saplingState: BlockState): Boolean {
        if (!world.isEmptyBlock(pos) || !saplingState.canSurvive(world, pos)) {
            return false
        }
        return world.setBlock(pos, saplingState, Block.UPDATE_CLIENTS)
    }

    private fun saplingStateForLog(logState: BlockState): BlockState? {
        val vanillaSapling = when (logState.block) {
            Blocks.OAK_LOG, Blocks.OAK_WOOD, Blocks.STRIPPED_OAK_LOG, Blocks.STRIPPED_OAK_WOOD -> Blocks.OAK_SAPLING
            Blocks.SPRUCE_LOG, Blocks.SPRUCE_WOOD, Blocks.STRIPPED_SPRUCE_LOG, Blocks.STRIPPED_SPRUCE_WOOD -> Blocks.SPRUCE_SAPLING
            Blocks.BIRCH_LOG, Blocks.BIRCH_WOOD, Blocks.STRIPPED_BIRCH_LOG, Blocks.STRIPPED_BIRCH_WOOD -> Blocks.BIRCH_SAPLING
            Blocks.JUNGLE_LOG, Blocks.JUNGLE_WOOD, Blocks.STRIPPED_JUNGLE_LOG, Blocks.STRIPPED_JUNGLE_WOOD -> Blocks.JUNGLE_SAPLING
            Blocks.ACACIA_LOG, Blocks.ACACIA_WOOD, Blocks.STRIPPED_ACACIA_LOG, Blocks.STRIPPED_ACACIA_WOOD -> Blocks.ACACIA_SAPLING
            Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_WOOD, Blocks.STRIPPED_DARK_OAK_LOG, Blocks.STRIPPED_DARK_OAK_WOOD -> Blocks.DARK_OAK_SAPLING
            Blocks.MANGROVE_LOG, Blocks.MANGROVE_WOOD, Blocks.STRIPPED_MANGROVE_LOG, Blocks.STRIPPED_MANGROVE_WOOD -> Blocks.MANGROVE_PROPAGULE
            Blocks.CHERRY_LOG, Blocks.CHERRY_WOOD, Blocks.STRIPPED_CHERRY_LOG, Blocks.STRIPPED_CHERRY_WOOD -> Blocks.CHERRY_SAPLING
            else -> null
        }
        if (vanillaSapling != null) {
            return vanillaSapling.defaultBlockState()
        }

        val logId = BuiltInRegistries.BLOCK.getKey(logState.block)
        val saplingPath = when {
            logId.path.endsWith("_log") -> logId.path.removeSuffix("_log") + "_sapling"
            logId.path.endsWith("_wood") -> logId.path.removeSuffix("_wood") + "_sapling"
            logId.path.endsWith("_stem") -> logId.path.removeSuffix("_stem") + "_fungus"
            else -> null
        } ?: return null
        val sapling = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(logId.namespace, saplingPath))
        return sapling.defaultBlockState().takeIf { it.`is`(BlockTags.SAPLINGS) }
    }

    private fun routeDrops(world: ServerLevel, sourcePos: BlockPos, drops: Collection<ItemStack>, collectionBudget: CollectionBudget?) {
        drops.forEach { routeDrop(world, sourcePos, it.copy(), collectionBudget) }
    }

    private fun routeDrop(world: ServerLevel, sourcePos: BlockPos, stack: ItemStack, collectionBudget: CollectionBudget?) {
        if (collectionBudget == null) {
            Block.popResource(world, sourcePos, stack)
            return
        }
        if (collectionBudget.remainingItems <= 0) {
            addToCollectionBuffer(stack)
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
        val extra = stack.count - routedAmount
        if (extra > 0) {
            val buffered = stack.copy()
            buffered.count = extra
            addToCollectionBuffer(buffered)
        }
    }

    private fun flushCollectionBuffer(world: ServerLevel, collectionBudget: CollectionBudget) {
        if (collectionBuffer.isEmpty() || collectionBudget.remainingItems <= 0) {
            return
        }
        val iterator = collectionBuffer.listIterator()
        while (iterator.hasNext() && collectionBudget.remainingItems > 0) {
            val buffered = iterator.next()
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

    private fun collectionBufferHasSpace(): Boolean {
        return collectionBuffer.size < COLLECTION_BUFFER_SLOTS || collectionBuffer.any { it.count < it.maxStackSize }
    }

    private fun addToCollectionBuffer(stack: ItemStack): ItemStack {
        var remainder = stack.copy()
        collectionBuffer.forEach { existing ->
            if (remainder.isEmpty) return@forEach
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
            if (remainder.isEmpty) return@forEach
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
        val fighter = storedPokemon.firstOrNull { it.role == SlotRole.FIGHTING }
        val normal = storedPokemon.firstOrNull { it.role == SlotRole.NORMAL }
        val grass = storedPokemon.firstOrNull { it.role == SlotRole.GRASS }
        listOfNotNull(fighter, normal, grass).forEach { ensureVisiblePokemon(world, it) }
    }

    private fun ensureVisiblePokemon(world: ServerLevel, entry: StoredPokemon) {
        val pokemon = Cobblemon.storage.getPC(entry.pcId, world.registryAccess())[entry.pokemonId] ?: return
        var existing: Entity? = getPokemonEntity(pokemon)?.takeIf { it.isAlive && getTethering(it)?.tetheringId == entry.linkId }

        if (existing == null) {
            val searchBox = AABB(blockPos).inflate(CobblemonWorkforceConfig.pokemonWanderRange.toDouble() + 10.0)
            val entities = world.getEntitiesOfClass(Entity::class.java, searchBox) { ent ->
                val tether = getTethering(ent)
                tether != null && tether.pokemonId == entry.pokemonId
            }
            if (entities.isNotEmpty()) {
                existing = entities.first()
                for (i in 1 until entities.size) {
                    entities[i].discard()
                }
            }
        }

        if (!CobblemonWorkforceConfig.showPokemonOutside) {
            if (existing != null) {
                existing.discard()
                if (pokemon.tetheringId == entry.linkId) {
                    pokemon.tetheringId = null
                }
            }
            return
        }

        if (existing != null) {
            keepTethered(existing, entry)
            return
        }

        pokemon.tetheringId = entry.linkId
        val entity = createPokemonEntity(world, pokemon) ?: return
        val spawnPos = findVisibleSpawnPos(world, entity, entry.role) ?: return
        entity.setPos(spawnPos.center.subtract(0.0, 0.5, 0.0))
        if (world.addFreshEntity(entity)) {
            setTethering(entity, createVisibleTethering(entry, entity.id))
            keepTethered(entity, entry)
        }
    }

    private fun visiblePokemonEntity(world: ServerLevel, entry: StoredPokemon): Entity? {
        val pokemon = Cobblemon.storage.getPC(entry.pcId, world.registryAccess())[entry.pokemonId] ?: return null
        return getPokemonEntity(pokemon)?.takeIf { it.isAlive && getTethering(it)?.tetheringId == entry.linkId }
    }

    private fun movePokemonToLog(world: ServerLevel, entry: StoredPokemon, entity: Entity?, logPos: BlockPos): Boolean {
        val visible = entity ?: run {
            ensureVisiblePokemon(world, entry)
            visiblePokemonEntity(world, entry)
        } ?: return false
        keepTethered(visible, entry)
        return (visible as? Mob)?.navigation?.moveTo(logPos.x + 0.5, logPos.y.toDouble(), logPos.z + 0.5, WORK_SPEED) == true
    }

    private fun isNearLog(entity: Entity, logPos: BlockPos): Boolean {
        val dx = entity.x - (logPos.x + 0.5)
        val dy = entity.y - logPos.y
        val dz = entity.z - (logPos.z + 0.5)
        return dx * dx + dy * dy + dz * dz <= WORK_DISTANCE_SQR
    }

    private fun keepTethered(entity: Entity, entry: StoredPokemon) {
        val tethering = getTethering(entity)
        if (tethering == null || tethering.pasturePos != blockPos || tethering.tetheringId != entry.linkId) {
            setTethering(entity, createVisibleTethering(entry, entity.id))
        }
    }

    private fun createVisibleTethering(entry: StoredPokemon, entityId: Int): PokemonPastureBlockEntity.Tethering {
        val range = rangeForLevel(entry.level) + 4
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

    private fun findVisibleSpawnPos(world: ServerLevel, entity: Entity, role: SlotRole): BlockPos? {
        val width = ceil(entity.boundingBox.xsize).toInt().coerceAtLeast(1)
        val directions = preferredSpawnDirections(role)
        val wanderRange = CobblemonWorkforceConfig.pokemonWanderRange

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
        return null
    }

    private fun preferredSpawnDirections(role: SlotRole): List<Direction> {
        return when (role) {
            SlotRole.FIGHTING -> listOf(Direction.NORTH, Direction.EAST, Direction.WEST, Direction.SOUTH)
            SlotRole.NORMAL -> listOf(Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.NORTH)
            SlotRole.GRASS -> listOf(Direction.WEST, Direction.SOUTH, Direction.NORTH, Direction.EAST)
        }
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

    private fun createPokemonEntity(world: ServerLevel, pokemon: Pokemon): PokemonEntity? {
        return runCatching {
            val entity = PokemonEntity(world, pokemon, CobblemonEntities.POKEMON)
            entity.refreshDimensions()
            entity
        }.getOrElse {
            Cobblemon.LOGGER.warn("Could not create visible Wood Assistant Pokemon entity", it)
            null
        }
    }

    private fun getPokemonEntity(pokemon: Pokemon): PokemonEntity? {
        return pokemon.entity
    }

    private fun getTethering(entity: Entity): PokemonPastureBlockEntity.Tethering? {
        return (entity as? PokemonEntity)?.tethering
    }

    private fun setTethering(entity: Entity, tethering: PokemonPastureBlockEntity.Tethering) {
        (entity as? PokemonEntity)?.tethering = tethering
    }

    companion object {
        private const val COLLECTION_BUFFER_SLOTS = 27
        private const val MAX_TREE_LOGS = 192
        private const val MAX_TREE_LEAVES = 384
        private const val MAX_TREE_SCAN_BLOCKS = 1536
        private const val SINGLE_TREE_HORIZONTAL_RADIUS = 8
        private const val SINGLE_TREE_BELOW_RANGE = 6
        private const val SINGLE_TREE_ABOVE_RANGE = 32
        private const val NEIGHBOR_TRUNK_MIN_HEIGHT = 3
        private const val MAX_PATH_ATTEMPTS = 4
        private const val WORK_SPEED = 0.5
        private const val WORK_DISTANCE_SQR = 9.0
        private val TREE_NEIGHBOR_OFFSETS: List<BlockPos> = buildList {
            for (x in -1..1) {
                for (y in -1..1) {
                    for (z in -1..1) {
                        if (x != 0 || y != 0 || z != 0) {
                            add(BlockPos(x, y, z))
                        }
                    }
                }
            }
        }

        fun tick(level: Level, pos: BlockPos, state: BlockState, blockEntity: PokeLumberBlockEntity) {
            if (level is ServerLevel) {
                blockEntity.tickWood(level)
                val hasPokemon = blockEntity.storedPokemon.any { com.nbp.cobblemon_workforce.pokemon.PokemonWorkforceStatus.isAwake(level, it.pcId, it.pokemonId) }
                if (state.getValue(com.nbp.cobblemon_workforce.block.PokeGeneratorBlock.ON) != hasPokemon) {
                    level.setBlock(pos, state.setValue(com.nbp.cobblemon_workforce.block.PokeGeneratorBlock.ON, hasPokemon), 3)
                }
            }
        }

        private fun woodPowerFor(pokemon: Pokemon): Int {
            return calculatedPower(pokemon, Stats.ATTACK).coerceAtLeast(calculatedPower(pokemon, Stats.SPECIAL_ATTACK))
        }

        private fun calculatedPower(pokemon: Pokemon, stat: Stat): Int {
            val baseStat = pokemon.form.baseStats[stat] ?: 0
            val iv = pokemon.ivs.getOrDefault(stat)
            val ev = pokemon.evs.getOrDefault(stat)
            val ivBonus = iv * CobblemonWorkforceConfig.ivBonusPercentPerPoint / 100.0
            val evBonus = (ev / 4) * CobblemonWorkforceConfig.evBonusPercentPer4Points / 100.0
            val levelMultiplier = if (pokemon.level <= 50) {
                pokemon.level.coerceAtLeast(1) / 50.0
            } else {
                1.0 + ((pokemon.level.coerceAtMost(100) - 50) / 50.0)
            }
            return (baseStat * (1.0 + ivBonus + evBonus) * levelMultiplier).toInt().coerceAtLeast(1)
        }

        private fun rangeForLevel(level: Int): Int {
            val clamped = level.coerceIn(1, 100)
            return if (clamped <= 50) {
                8 + ((clamped - 1) * 16) / 49
            } else {
                24 + ((clamped - 50) * 24) / 50
            }
        }

        private fun harvestIntervalForLevel(level: Int): Long {
            val clamped = level.coerceIn(1, 100)
            return if (clamped <= 50) {
                (700 - ((clamped - 1) * 520) / 49).toLong()
            } else {
                (180 - ((clamped - 50) * 100) / 50).toLong()
            }
        }

        private fun collectionItemLimitFor(power: Int): Int = (4 + power / 12).coerceIn(4, 96)
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
                    role = runCatching { SlotRole.valueOf(nbt.getString("Role")) }.getOrDefault(SlotRole.FIGHTING),
                    level = nbt.getInt("Level").takeIf { it > 0 } ?: 1,
                    power = nbt.getInt("Power").takeIf { it > 0 } ?: 1
                )
            }
        }
    }

    enum class SlotRole {
        FIGHTING,
        NORMAL,
        GRASS
    }

    private data class CollectionBudget(var remainingItems: Int)

    private data class TreeHarvestBlock(val pos: BlockPos, val state: BlockState)
}
