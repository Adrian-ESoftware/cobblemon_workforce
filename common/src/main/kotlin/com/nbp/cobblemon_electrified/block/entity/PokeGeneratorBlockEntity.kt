package com.nbp.cobblemon_electrified.block.entity

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import com.cobblemon.mod.common.net.messages.client.pasture.OpenPasturePacket
import com.cobblemon.mod.common.pokemon.Pokemon
import com.nbp.cobblemon_electrified.block.CobblemonElectrifiedBlocks
import com.nbp.cobblemon_electrified.config.CobblemonElectrifiedConfig
import com.nbp.cobblemon_electrified.energy.PokeGeneratorEnergyOutput
import java.util.UUID
import kotlin.math.ceil
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
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB

class PokeGeneratorBlockEntity(pos: BlockPos, state: BlockState) :
    BlockEntity(CobblemonElectrifiedBlocks.POKE_GENERATOR_BLOCK_ENTITY, pos, state) {

    var unlockedSlots: Int = 1
        private set

    var energyStored: Int = 0
        private set

    var generationRate: Int = 0
        private set

    var pokemonName: String = ""
        private set

    var powerStatName: String = ""
        private set

    var powerStatValue: Int = 0
        private set

    val pokemonId: UUID?
        get() = storedPokemon.firstOrNull()?.pokemonId

    private val storedPokemon = mutableListOf<StoredPokemon>()

    fun hasPokemon(): Boolean = storedPokemon.isNotEmpty()

    fun isOwner(player: ServerPlayer): Boolean = storedPokemon.any { it.ownerId == player.uuid }

    fun slotLimit(): Int = unlockedSlots.coerceIn(1, CobblemonElectrifiedConfig.generatorMaxPokemon)

    fun canAddPokemon(): Boolean = storedPokemon.size < slotLimit()

    fun canUpgradeSlots(): Boolean = unlockedSlots < CobblemonElectrifiedConfig.generatorMaxPokemon

    fun saveUpgradeData(): CompoundTag {
        return CompoundTag().also {
            it.putInt("UnlockedSlots", unlockedSlots.coerceIn(1, CobblemonElectrifiedConfig.generatorMaxPokemon))
        }
    }

    fun loadUpgradeData(nbt: CompoundTag) {
        unlockedSlots = nbt.getInt("UnlockedSlots")
            .takeIf { it > 0 }
            ?.coerceIn(1, CobblemonElectrifiedConfig.generatorMaxPokemon)
            ?: 1
        setChanged()
    }

    fun upgradeSlots(): Boolean {
        if (!canUpgradeSlots()) {
            return false
        }

        unlockedSlots++
        setChanged()
        return true
    }

    fun addPokemon(player: ServerPlayer, pokemon: Pokemon, pcId: UUID): Boolean {
        if (!canAddPokemon() || storedPokemon.any { it.pokemonId == pokemon.uuid }) {
            return false
        }

        val powerStat = powerStatFor(pokemon)
        val linkId = UUID.randomUUID()
        pokemon.tetheringId = linkId
        val wasEmpty = storedPokemon.isEmpty()
        storedPokemon.add(
            StoredPokemon(
                ownerId = player.uuid,
                pcId = pcId,
                pokemonId = pokemon.uuid,
                generatorLinkId = linkId,
                ownerName = player.gameProfile.name,
                pokemonName = pokemon.getDisplayName().string,
                powerStatName = powerStat.name,
                powerStatValue = powerStat.value
            )
        )
        recalculateGeneration()
        setChanged()
        PokeGeneratorEnergyOutput.invalidate(this)
        if (wasEmpty) {
            ensureVisiblePokemon()
        }
        return true
    }

    fun removePokemon(pokemonId: UUID): Boolean {
        val removed = storedPokemon.firstOrNull { it.pokemonId == pokemonId } ?: return false
        val wasVisible = storedPokemon.firstOrNull()?.pokemonId == pokemonId
        releaseLinkedPokemon(removed)
        storedPokemon.remove(removed)
        recalculateGeneration()
        setChanged()
        PokeGeneratorEnergyOutput.invalidate(this)
        if (wasVisible) {
            ensureVisiblePokemon()
        }
        return true
    }

    fun clearPokemon() {
        storedPokemon.toList().forEach(::releaseLinkedPokemon)
        storedPokemon.clear()
        recalculateGeneration()
        setChanged()
        PokeGeneratorEnergyOutput.invalidate(this)
    }

    fun ownsPokemon(player: ServerPlayer, pokemonId: UUID): Boolean {
        return storedPokemon.any { it.ownerId == player.uuid && it.pokemonId == pokemonId }
    }

    fun storedPokemonIdsFor(player: ServerPlayer): List<UUID> {
        return storedPokemon.filter { it.ownerId == player.uuid }.map { it.pokemonId }
    }

    fun extractEnergy(maxExtract: Int, simulate: Boolean): Int {
        if (maxExtract <= 0 || energyStored <= 0) {
            return 0
        }

        val extracted = energyStored.coerceAtMost(maxExtract)
        if (!simulate) {
            energyStored -= extracted
            setChanged()
        }
        return extracted
    }

    fun releaseLinkedPokemon() {
        storedPokemon.toList().forEach(::releaseLinkedPokemon)
    }

    private fun releaseLinkedPokemon(entry: StoredPokemon) {
        val registryAccess = level?.server?.registryAccess() ?: return
        val pokemon = Cobblemon.storage.getPC(entry.pcId, registryAccess)[entry.pokemonId] ?: return
        getPokemonEntity(pokemon)
            ?.takeIf { getTethering(it)?.tetheringId == entry.generatorLinkId }
            ?.discard()
        if (pokemon.tetheringId == entry.generatorLinkId) {
            pokemon.tetheringId = null
        }
    }

    fun toPastureDTOs(viewer: ServerPlayer): List<OpenPasturePacket.PasturePokemonDataDTO> {
        val registryAccess = level?.server?.registryAccess() ?: viewer.registryAccess()
        return storedPokemon.mapNotNull { entry ->
            val pokemon = Cobblemon.storage.getPC(entry.pcId, registryAccess)[entry.pokemonId] ?: return@mapNotNull null
            val entity = getPokemonEntity(pokemon)?.takeIf { getTethering(it)?.tetheringId == entry.generatorLinkId }
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
        unlockedSlots = nbt.getInt("UnlockedSlots").takeIf { it > 0 } ?: 1
        energyStored = nbt.getInt("Energy")
        storedPokemon.clear()

        val list = nbt.getList("Pokemon", CompoundTag.TAG_COMPOUND.toInt())
        for (tag in list) {
            tag as CompoundTag
            storedPokemon.add(StoredPokemon.fromNbt(tag))
        }

        if (storedPokemon.isEmpty() && nbt.hasUUID("PokemonId") && nbt.hasUUID("OwnerId") && nbt.hasUUID("PcId")) {
            storedPokemon.add(
                StoredPokemon(
                    ownerId = nbt.getUUID("OwnerId"),
                    pcId = nbt.getUUID("PcId"),
                    pokemonId = nbt.getUUID("PokemonId"),
                    generatorLinkId = if (nbt.hasUUID("GeneratorLinkId")) nbt.getUUID("GeneratorLinkId") else UUID.randomUUID(),
                    ownerName = "",
                    pokemonName = nbt.getString("PokemonName"),
                    powerStatName = nbt.getString("PowerStatName"),
                    powerStatValue = nbt.getInt("PowerStatValue")
                )
            )
        }

        if (storedPokemon.size > unlockedSlots) {
            unlockedSlots = storedPokemon.size
        }
        unlockedSlots = unlockedSlots.coerceIn(1, CobblemonElectrifiedConfig.generatorMaxPokemon)
        recalculateGeneration()
    }

    override fun saveAdditional(nbt: CompoundTag, registryLookup: HolderLookup.Provider) {
        super.saveAdditional(nbt, registryLookup)
        nbt.putInt("UnlockedSlots", unlockedSlots)
        nbt.putInt("Energy", energyStored)

        val list = ListTag()
        storedPokemon.forEach { list.add(it.toNbt()) }
        nbt.put("Pokemon", list)
    }

    private fun generateEnergy() {
        if (!hasPokemon() || generationRate <= 0 || energyStored >= MAX_ENERGY) {
            return
        }

        energyStored = (energyStored + generationRate).coerceAtMost(MAX_ENERGY)
        setChanged()
    }

    private fun ensureVisiblePokemon() {
        val world = level as? ServerLevel ?: return
        val first = storedPokemon.firstOrNull() ?: return
        val pokemon = Cobblemon.storage.getPC(first.pcId, world.registryAccess())[first.pokemonId] ?: return
        val existing = getPokemonEntity(pokemon)?.takeIf { it.isAlive && getTethering(it)?.tetheringId == first.generatorLinkId }

        if (!CobblemonElectrifiedConfig.showPokemonOutside) {
            if (existing != null) {
                existing.discard()
                if (pokemon.tetheringId == first.generatorLinkId) {
                    pokemon.tetheringId = null
                }
            }
            return
        }

        if (existing != null) {
            keepCloseToGenerator(world, existing)
            return
        }

        pokemon.tetheringId = first.generatorLinkId
        val entity = createPokemonEntity(world, pokemon) ?: return
        val spawnPos = findVisibleSpawnPos(world, entity) ?: return
        entity.setPos(spawnPos.center.subtract(0.0, 0.5, 0.0))
        if (world.addFreshEntity(entity)) {
            setTethering(entity, createVisibleTethering(first, entity.id))
            keepCloseToGenerator(world, entity)
        }
    }

    private fun keepCloseToGenerator(world: ServerLevel, entity: Entity) {
        val tethering = getTethering(entity)
        if (tethering == null || tethering.pasturePos != blockPos) {
            setTethering(entity, createVisibleTethering(storedPokemon.first(), entity.id))
        }

        val mob = entity as? Mob
        if (mob != null && world.gameTime % 20 == 0L) {
            val wanderRange = CobblemonElectrifiedConfig.pokemonWanderRange
            if (mob.blockPosition().distManhattan(blockPos) > wanderRange) {
                findVisibleSpawnPos(world, entity)?.let {
                    mob.navigation.moveTo(
                        it.x + 0.5,
                        it.y.toDouble(),
                        it.z + 0.5,
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
            tetheringId = entry.generatorLinkId,
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

        Cobblemon.LOGGER.warn("Could not find a safe visible spawn position for Poke Power Generator at $blockPos")
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
        val entity = runCatching {
            val entityClass = Class.forName("com.cobblemon.mod.common.entity.pokemon.PokemonEntity")
            val entityType = Class.forName("com.cobblemon.mod.common.CobblemonEntities")
                .getField("POKEMON")
                .get(null) as EntityType<*>
            val constructor = entityClass.getConstructor(Level::class.java, Pokemon::class.java, EntityType::class.java)
            (constructor.newInstance(world, pokemon, entityType) as Entity).also {
                it.javaClass.getMethod("refreshDimensions").invoke(it)
            }
        }.getOrElse {
            Cobblemon.LOGGER.warn("Could not create visible Poke Power Generator Pokemon entity", it)
            null
        }
        return entity
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

    private fun recalculateGeneration() {
        generationRate = storedPokemon.sumOf { generationRateFor(it.powerStatValue) }
        pokemonName = when (storedPokemon.size) {
            0 -> ""
            1 -> storedPokemon.first().pokemonName
            else -> "${storedPokemon.size} Pokemon"
        }
        powerStatName = when (storedPokemon.size) {
            0 -> ""
            1 -> storedPokemon.first().powerStatName
            else -> "Combined Attack/Sp. Attack"
        }
        powerStatValue = storedPokemon.sumOf { it.powerStatValue }
    }

    companion object {
        const val MAX_ENERGY = 100_000
        private const val RETURN_SPEED = 1.35

        fun tick(level: Level, pos: BlockPos, state: BlockState, blockEntity: PokeGeneratorBlockEntity) {
            if (!level.isClientSide) {
                blockEntity.ensureVisiblePokemon()
                blockEntity.generateEnergy()
                PokeGeneratorEnergyOutput.pushEnergy(blockEntity)
            }
        }

        private fun powerStatFor(pokemon: Pokemon): PowerStat {
            val attack = pokemon.getStat(Stats.ATTACK)
            val specialAttack = pokemon.getStat(Stats.SPECIAL_ATTACK)
            return if (specialAttack > attack) {
                PowerStat("Sp. Attack", specialAttack)
            } else {
                PowerStat("Attack", attack)
            }
        }

        private fun generationRateFor(statValue: Int): Int {
            return statValue.coerceIn(1, 512)
        }
    }

    data class PowerStat(val name: String, val value: Int)

    data class StoredPokemon(
        val ownerId: UUID,
        val pcId: UUID,
        val pokemonId: UUID,
        val generatorLinkId: UUID,
        val ownerName: String,
        val pokemonName: String,
        val powerStatName: String,
        val powerStatValue: Int
    ) {
        fun toNbt(): CompoundTag {
            return CompoundTag().also {
                it.putUUID("OwnerId", ownerId)
                it.putUUID("PcId", pcId)
                it.putUUID("PokemonId", pokemonId)
                it.putUUID("GeneratorLinkId", generatorLinkId)
                it.putString("OwnerName", ownerName)
                it.putString("PokemonName", pokemonName)
                it.putString("PowerStatName", powerStatName)
                it.putInt("PowerStatValue", powerStatValue)
            }
        }

        companion object {
            fun fromNbt(nbt: CompoundTag): StoredPokemon {
                return StoredPokemon(
                    ownerId = nbt.getUUID("OwnerId"),
                    pcId = nbt.getUUID("PcId"),
                    pokemonId = nbt.getUUID("PokemonId"),
                    generatorLinkId = nbt.getUUID("GeneratorLinkId"),
                    ownerName = nbt.getString("OwnerName"),
                    pokemonName = nbt.getString("PokemonName"),
                    powerStatName = nbt.getString("PowerStatName"),
                    powerStatValue = nbt.getInt("PowerStatValue")
                )
            }
        }
    }
}
