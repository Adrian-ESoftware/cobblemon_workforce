package com.nbp.cobblemon_workforce.block.entity

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.pokemon.stats.Stat
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.api.types.ElementalTypes
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import com.cobblemon.mod.common.net.messages.client.pasture.OpenPasturePacket
import com.cobblemon.mod.common.pokemon.Pokemon
import com.nbp.cobblemon_workforce.block.CobblemonWorkforceBlocks
import com.nbp.cobblemon_workforce.block.PokeGeneratorBlock
import com.nbp.cobblemon_workforce.config.CobblemonWorkforceConfig
import com.nbp.cobblemon_workforce.mixin.AbstractFurnaceBlockEntityAccessor
import com.nbp.cobblemon_workforce.pokemon.PokemonWorkforceStatus
import java.util.UUID
import kotlin.math.ceil
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.ContainerHelper
import net.minecraft.world.WorldlyContainer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.Pose
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.AbstractFurnaceBlock
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB

class PokeFurnaceBlockEntity(pos: BlockPos, state: BlockState) :
    BlockEntity(CobblemonWorkforceBlocks.POKE_FURNACE_BLOCK_ENTITY, pos, state),
    WorldlyContainer {

    private var storedPokemon: StoredPokemon? = null
    private val items = mutableListOf(ItemStack.EMPTY, ItemStack.EMPTY)
    private var cookProgress = 0
    private var boostProgress = 0.0

    fun isOwner(player: ServerPlayer): Boolean = storedPokemon?.ownerId == player.uuid

    fun canAddPokemon(): Boolean = storedPokemon == null

    fun accepts(pokemon: Pokemon): Boolean = pokemon.types.any { it == ElementalTypes.FIRE }

    fun addPokemon(player: ServerPlayer, pokemon: Pokemon, pcId: UUID): Boolean {
        if (!canAddPokemon() || !accepts(pokemon)) {
            return false
        }
        val linkId = UUID.randomUUID()
        pokemon.tetheringId = linkId
        storedPokemon = StoredPokemon(
            ownerId = player.uuid,
            pcId = pcId,
            pokemonId = pokemon.uuid,
            linkId = linkId,
            ownerName = player.gameProfile.name,
            pokemonName = pokemon.getDisplayName().string,
            level = pokemon.level,
            power = furnacePowerFor(pokemon)
        )
        setChanged()
        ensureVisiblePokemon()
        return true
    }

    fun removePokemon(pokemonId: UUID): Boolean {
        val stored = storedPokemon ?: return false
        if (stored.pokemonId != pokemonId) {
            return false
        }
        releaseLinkedPokemon(stored)
        storedPokemon = null
        cookProgress = 0
        setChanged()
        ensureVisiblePokemon()
        return true
    }

    fun ownsPokemon(player: ServerPlayer, pokemonId: UUID): Boolean {
        return storedPokemon?.let { it.ownerId == player.uuid && it.pokemonId == pokemonId } == true
    }

    fun storedPokemonIdsFor(player: ServerPlayer): List<UUID> {
        val stored = storedPokemon ?: return emptyList()
        return if (stored.ownerId == player.uuid) listOf(stored.pokemonId) else emptyList()
    }

    fun releaseLinkedPokemon() {
        storedPokemon?.let(::releaseLinkedPokemon)
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
        val entry = storedPokemon ?: return emptyList()
        val registryAccess = level?.server?.registryAccess() ?: viewer.registryAccess()
        val pokemon = Cobblemon.storage.getPC(entry.pcId, registryAccess)[entry.pokemonId] ?: return emptyList()
        val entity = getPokemonEntity(pokemon)?.takeIf { getTethering(it)?.tetheringId == entry.linkId }
        return listOf(
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
        )
    }

    fun insertInputFromPlayer(stack: ItemStack): Int {
        return 0
    }

    fun dropInventory(world: ServerLevel, pos: BlockPos) {
        items.filterNot { it.isEmpty }.forEach { Block.popResource(world, pos, it.copy()) }
        items[INPUT_SLOT] = ItemStack.EMPTY
        items[OUTPUT_SLOT] = ItemStack.EMPTY
        setChanged()
    }

    fun extractOutputToPlayer(player: Player): Boolean {
        val output = items[OUTPUT_SLOT]
        if (output.isEmpty) {
            return false
        }
        val moving = output.copy()
        if (!player.inventory.add(moving)) {
            return false
        }
        items[OUTPUT_SLOT] = ItemStack.EMPTY
        setChanged()
        return true
    }

    override fun loadAdditional(nbt: CompoundTag, registryLookup: HolderLookup.Provider) {
        super.loadAdditional(nbt, registryLookup)
        storedPokemon = nbt.getCompound("Pokemon").takeUnless { it.isEmpty }?.let(StoredPokemon::fromNbt)
        items[INPUT_SLOT] = ItemStack.parseOptional(registryLookup, nbt.getCompound("Input"))
        items[OUTPUT_SLOT] = ItemStack.parseOptional(registryLookup, nbt.getCompound("Output"))
        cookProgress = nbt.getInt("CookProgress")
        boostProgress = nbt.getDouble("BoostProgress")
    }

    override fun saveAdditional(nbt: CompoundTag, registryLookup: HolderLookup.Provider) {
        super.saveAdditional(nbt, registryLookup)
        storedPokemon?.let { nbt.put("Pokemon", it.toNbt()) }
        nbt.put("Input", items[INPUT_SLOT].saveOptional(registryLookup))
        nbt.put("Output", items[OUTPUT_SLOT].saveOptional(registryLookup))
        nbt.putInt("CookProgress", cookProgress)
        nbt.putDouble("BoostProgress", boostProgress)
    }

    private fun tickFurnace(world: ServerLevel) {
        val pokemon = storedPokemon ?: run {
            cookProgress = 0
            boostProgress = 0.0
            return
        }
        ensureVisiblePokemon()
        if (!PokemonWorkforceStatus.isAwake(world, pokemon.pcId, pokemon.pokemonId)) {
            cookProgress = 0
            boostProgress = 0.0
            return
        }

        val boosted = boostAdjacentFurnaces(world, pokemon)
        cookProgress = 0
        if (boosted) setChanged()
    }

    private fun boostAdjacentFurnaces(world: ServerLevel, pokemon: StoredPokemon): Boolean {
        val furnaces = adjacentFurnaces(world)
        if (furnaces.isEmpty()) {
            boostProgress = 0.0
            return false
        }

        val heatedFurnaces = furnaces.filter { heatFurnace(world, it) }
        if (heatedFurnaces.isEmpty()) {
            boostProgress = 0.0
            return false
        }

        boostProgress += speedMultiplierFor(pokemon) - 1.0
        val extraTicks = boostProgress.toInt()
        boostProgress -= extraTicks
        if (extraTicks <= 0) {
            return true
        }

        heatedFurnaces.filter(::canBoost).forEach { furnace ->
            val accessor = furnace as AbstractFurnaceBlockEntityAccessor
            val total = accessor.`cobblemon_workforce$getCookingTotalTime`()
            val current = accessor.`cobblemon_workforce$getCookingProgress`()
            accessor.`cobblemon_workforce$setCookingProgress`((current + extraTicks).coerceAtMost(total - 1))
            furnace.setChanged()
        }
        return true
    }

    private fun adjacentFurnaces(world: ServerLevel): List<AbstractFurnaceBlockEntity> {
        val positions = buildSet {
            for (origin in listOf(blockPos, blockPos.above())) {
                Direction.values().forEach { add(origin.relative(it)) }
            }
        }
        return positions.mapNotNull { world.getBlockEntity(it) as? AbstractFurnaceBlockEntity }
    }

    private fun heatFurnace(world: ServerLevel, furnace: AbstractFurnaceBlockEntity): Boolean {
        if (furnace.getItem(0).isEmpty) {
            return false
        }
        val accessor = furnace as AbstractFurnaceBlockEntityAccessor
        accessor.`cobblemon_workforce$setLitTime`(FUEL_FREE_HEAT_TICKS)
        accessor.`cobblemon_workforce$setLitDuration`(FUEL_FREE_HEAT_TICKS)
        val state = furnace.blockState
        if (state.hasProperty(AbstractFurnaceBlock.LIT) && !state.getValue(AbstractFurnaceBlock.LIT)) {
            world.setBlock(furnace.blockPos, state.setValue(AbstractFurnaceBlock.LIT, true), Block.UPDATE_CLIENTS)
        }
        furnace.setChanged()
        return true
    }

    private fun canBoost(furnace: AbstractFurnaceBlockEntity): Boolean {
        val accessor = furnace as AbstractFurnaceBlockEntityAccessor
        val total = accessor.`cobblemon_workforce$getCookingTotalTime`()
        val progress = accessor.`cobblemon_workforce$getCookingProgress`()
        return accessor.`cobblemon_workforce$getLitTime`() > 0 && total > 1 && progress in 1 until total
    }

    private fun speedMultiplierFor(pokemon: StoredPokemon): Double {
        val level = pokemon.level.coerceIn(1, 100)
        val levelMultiplier = if (level <= 50) {
            1.25 + ((level - 1) * 2.75 / 49.0)
        } else {
            4.0 + ((level - 50) * 4.0 / 50.0)
        }
        val statMultiplier = (pokemon.power / 100.0).coerceIn(0.85, 1.3)
        return (levelMultiplier * statMultiplier).coerceAtLeast(1.0)
    }

    override fun getContainerSize(): Int = 0

    override fun isEmpty(): Boolean = items.all { it.isEmpty }

    override fun getItem(slot: Int): ItemStack = items.getOrElse(slot) { ItemStack.EMPTY }

    override fun removeItem(slot: Int, amount: Int): ItemStack {
        val removed = ContainerHelper.removeItem(items, slot, amount)
        if (!removed.isEmpty) {
            setChanged()
        }
        return removed
    }

    override fun removeItemNoUpdate(slot: Int): ItemStack {
        return ContainerHelper.takeItem(items, slot)
    }

    override fun setItem(slot: Int, stack: ItemStack) {
        if (slot !in 0 until SLOT_COUNT) {
            return
        }
        items[slot] = stack
        if (stack.count > maxStackSize) {
            stack.count = maxStackSize
        }
        setChanged()
    }

    override fun stillValid(player: net.minecraft.world.entity.player.Player): Boolean {
        return level?.getBlockEntity(blockPos) === this && player.distanceToSqr(blockPos.x + 0.5, blockPos.y + 0.5, blockPos.z + 0.5) <= 64.0
    }

    override fun clearContent() {
        items[INPUT_SLOT] = ItemStack.EMPTY
        items[OUTPUT_SLOT] = ItemStack.EMPTY
        setChanged()
    }

    override fun getSlotsForFace(side: Direction): IntArray {
        return intArrayOf()
    }

    override fun canPlaceItem(slot: Int, stack: ItemStack): Boolean {
        return false
    }

    override fun canPlaceItemThroughFace(slot: Int, stack: ItemStack, direction: Direction?): Boolean {
        return false
    }

    override fun canTakeItemThroughFace(slot: Int, stack: ItemStack, direction: Direction): Boolean {
        return false
    }

    private fun ensureVisiblePokemon() {
        val world = level as? ServerLevel ?: return
        val entry = storedPokemon ?: return
        ensureVisiblePokemon(world, entry)
    }

    private fun ensureVisiblePokemon(world: ServerLevel, entry: StoredPokemon) {
        val pokemon = Cobblemon.storage.getPC(entry.pcId, world.registryAccess())[entry.pokemonId] ?: return
        var existing = getPokemonEntity(pokemon)?.takeIf { it.isAlive && getTethering(it)?.tetheringId == entry.linkId }

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
                runCatching { pokemon.javaClass.getMethod("setEntity", Entity::class.java).invoke(pokemon, existing) }
            }
        }

        if (!CobblemonWorkforceConfig.showPokemonOutside) {
            existing?.discard()
            if (pokemon.tetheringId == entry.linkId) {
                pokemon.tetheringId = null
            }
            return
        }

        if (existing != null) {
            keepTethered(existing, entry)
            keepNearFurnace(world, existing)
            return
        }

        pokemon.tetheringId = entry.linkId
        val entity = createPokemonEntity(world, pokemon) ?: return
        val spawnPos = findVisibleSpawnPos(world, entity) ?: return
        entity.setPos(spawnPos.center.subtract(0.0, 0.5, 0.0))
        if (world.addFreshEntity(entity)) {
            setTethering(entity, createVisibleTethering(entry, entity.id))
            keepTethered(entity, entry)
            keepNearFurnace(world, entity)
        }
    }

    private fun keepNearFurnace(world: ServerLevel, entity: Entity) {
        val mob = entity as? Mob ?: return
        if (world.gameTime % 20L == 0L && mob.blockPosition().distManhattan(blockPos) > CobblemonWorkforceConfig.pokemonWanderRange) {
            mob.navigation.moveTo(blockPos.x + 0.5, blockPos.y.toDouble(), blockPos.z + 0.5, RETURN_SPEED)
        }
    }

    private fun keepTethered(entity: Entity, entry: StoredPokemon) {
        val tethering = getTethering(entity)
        if (tethering == null || tethering.pasturePos != blockPos || tethering.tetheringId != entry.linkId) {
            setTethering(entity, createVisibleTethering(entry, entity.id))
        }
    }

    private fun createVisibleTethering(entry: StoredPokemon, entityId: Int): PokemonPastureBlockEntity.Tethering {
        val wanderRange = CobblemonWorkforceConfig.pokemonWanderRange
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
        val directions = Direction.Plane.HORIZONTAL.toList()
        val wanderRange = CobblemonWorkforceConfig.pokemonWanderRange
        for (direction in directions) {
            val idealPlace = blockPos.relative(direction, width + 1)
            var box = entity.getDimensions(Pose.STANDING).makeBoundingBox(idealPlace.center.subtract(0.0, 0.5, 0.0))
            for (i in 0..wanderRange) {
                box = box.move(direction.stepX.toDouble(), 0.0, direction.stepZ.toDouble())
                val fixed = makeSuitableY(world, idealPlace.relative(direction, i + 1), entity, box)
                if (fixed != null) {
                    return fixed
                }
            }
        }
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
            Cobblemon.LOGGER.warn("Could not create visible Poke Furnace Pokemon entity", it)
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
            entity.javaClass.getMethod("setTethering", PokemonPastureBlockEntity.Tethering::class.java).invoke(entity, tethering)
        }
    }

    companion object {
        private const val INPUT_SLOT = 0
        private const val OUTPUT_SLOT = 1
        private const val SLOT_COUNT = 2
        private const val FUEL_FREE_HEAT_TICKS = 20
        private const val RETURN_SPEED = 0.35

        fun tick(level: Level, pos: BlockPos, state: BlockState, blockEntity: PokeFurnaceBlockEntity) {
            if (level is ServerLevel) {
                blockEntity.tickFurnace(level)
                val hasPokemon = blockEntity.storedPokemon?.let { com.nbp.cobblemon_workforce.pokemon.PokemonWorkforceStatus.isAwake(level, it.pcId, it.pokemonId) } == true
                if (state.getValue(com.nbp.cobblemon_workforce.block.PokeGeneratorBlock.ON) != hasPokemon) {
                    level.setBlock(pos, state.setValue(com.nbp.cobblemon_workforce.block.PokeGeneratorBlock.ON, hasPokemon), 3)
                }
            }
        }

        private fun furnacePowerFor(pokemon: Pokemon): Int {
            val attack = calculatedPower(pokemon, Stats.ATTACK)
            val specialAttack = calculatedPower(pokemon, Stats.SPECIAL_ATTACK)
            return attack.coerceAtLeast(specialAttack)
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

    }

    data class StoredPokemon(
        val ownerId: UUID,
        val pcId: UUID,
        val pokemonId: UUID,
        val linkId: UUID,
        val ownerName: String,
        val pokemonName: String,
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
                    level = nbt.getInt("Level").takeIf { it > 0 } ?: 1,
                    power = nbt.getInt("Power").takeIf { it > 0 } ?: 1
                )
            }
        }
    }
}
