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
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.Pose
import net.minecraft.world.entity.animal.Animal
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB

class PokeBreederBlockEntity(pos: BlockPos, state: BlockState) :
    BlockEntity(CobblemonWorkforceBlocks.POKE_BREEDER_BLOCK_ENTITY, pos, state) {

    private var storedPokemon: StoredPokemon? = null
    private var pathAttempts = 0

    fun isOwner(player: ServerPlayer): Boolean = storedPokemon?.ownerId == player.uuid

    fun canAddPokemon(): Boolean = storedPokemon == null

    fun accepts(pokemon: Pokemon): Boolean = pokemon.types.any { it == ElementalTypes.FAIRY }

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
            breederPower = breederPowerFor(pokemon)
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
        pathAttempts = 0
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

    override fun loadAdditional(nbt: CompoundTag, registryLookup: HolderLookup.Provider) {
        super.loadAdditional(nbt, registryLookup)
        storedPokemon = nbt.getCompound("Pokemon").takeUnless { it.isEmpty }?.let(StoredPokemon::fromNbt)
    }

    override fun saveAdditional(nbt: CompoundTag, registryLookup: HolderLookup.Provider) {
        super.saveAdditional(nbt, registryLookup)
        storedPokemon?.let { nbt.put("Pokemon", it.toNbt()) }
    }

    private fun tickBreeder(world: ServerLevel) {
        val pokemon = storedPokemon ?: return
        ensureVisiblePokemon()
        if (!PokemonWorkforceStatus.isAwake(world, pokemon.pcId, pokemon.pokemonId)) {
            return
        }
        if (world.gameTime % intervalFor(pokemon.breederPower).toLong() != 0L) {
            return
        }
        val range = rangeFor(pokemon.breederPower).toDouble()
        if (animalCount(world, range) >= CobblemonWorkforceConfig.breederMaxAnimals) {
            return
        }
        val pair = findBreedPair(world, range) ?: return
        val entity = visiblePokemonEntity(world, pokemon)
        val target = pair.first()
        if (entity == null || !isNearAnimal(entity, target)) {
            pathAttempts++
            if (movePokemonToAnimal(world, pokemon, entity, target) && pathAttempts < MAX_PATH_ATTEMPTS) {
                return
            }
        }

        pathAttempts = 0
        pair.forEach(::setInLove)
    }

    private fun findBreedPair(world: ServerLevel, range: Double): List<Animal>? {
        val animals = world.getEntitiesOfClass(Animal::class.java, AABB(blockPos).inflate(range)) {
            it.isAlive && canFallInLove(it)
        }.sortedBy { it.distanceToSqr(blockPos.x + 0.5, blockPos.y + 0.5, blockPos.z + 0.5) }
        return animals.groupBy { it.type }.values.firstOrNull { it.size >= 2 }?.take(2)
    }

    private fun animalCount(world: ServerLevel, range: Double): Int {
        return world.getEntitiesOfClass(Animal::class.java, AABB(blockPos).inflate(range)) { it.isAlive }.size
    }

    private fun canFallInLove(animal: Animal): Boolean {
        return animal.canFallInLove()
    }

    private fun setInLove(animal: Animal) {
        animal.setInLoveTime(600)
    }

    private fun ensureVisiblePokemon() {
        val world = level as? ServerLevel ?: return
        val entry = storedPokemon ?: return
        ensureVisiblePokemon(world, entry)
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
            existing?.discard()
            if (pokemon.tetheringId == entry.linkId) {
                pokemon.tetheringId = null
            }
            return
        }

        if (existing != null) {
            keepTethered(existing, entry)
            return
        }

        pokemon.tetheringId = entry.linkId
        val entity = createPokemonEntity(world, pokemon) ?: return
        val spawnPos = findVisibleSpawnPos(world, entity) ?: return
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

    private fun movePokemonToAnimal(world: ServerLevel, entry: StoredPokemon, entity: Entity?, animal: Animal): Boolean {
        val visible = entity ?: run {
            ensureVisiblePokemon(world, entry)
            visiblePokemonEntity(world, entry)
        } ?: return false
        keepTethered(visible, entry)
        return (visible as? Mob)?.navigation?.moveTo(animal.x, animal.y, animal.z, WORK_SPEED) == true
    }

    private fun isNearAnimal(entity: Entity, animal: Animal): Boolean {
        return entity.distanceToSqr(animal.x, animal.y, animal.z) <= WORK_DISTANCE_SQR
    }

    private fun keepTethered(entity: Entity, entry: StoredPokemon) {
        val tethering = getTethering(entity)
        if (tethering == null || tethering.pasturePos != blockPos || tethering.tetheringId != entry.linkId) {
            setTethering(entity, createVisibleTethering(entry, entity.id))
        }
    }

    private fun createVisibleTethering(entry: StoredPokemon, entityId: Int): PokemonPastureBlockEntity.Tethering {
        val range = rangeFor(entry.breederPower) + 4
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
        val directions = listOf(Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.NORTH)
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

    private fun createPokemonEntity(world: ServerLevel, pokemon: Pokemon): PokemonEntity? {
        return runCatching {
            val entity = PokemonEntity(world, pokemon, CobblemonEntities.POKEMON)
            entity.refreshDimensions()
            entity
        }.getOrElse {
            Cobblemon.LOGGER.warn("Could not create visible Poke Breeder Pokemon entity", it)
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
        private const val MAX_PATH_ATTEMPTS = 4
        private const val WORK_DISTANCE_SQR = 9.0
        private const val WORK_SPEED = 0.65

        fun tick(level: Level, pos: BlockPos, state: BlockState, blockEntity: PokeBreederBlockEntity) {
            if (level is ServerLevel) {
                blockEntity.tickBreeder(level)
                val hasPokemon = blockEntity.storedPokemon?.let { com.nbp.cobblemon_workforce.pokemon.PokemonWorkforceStatus.isAwake(level, it.pcId, it.pokemonId) } == true
                if (state.getValue(com.nbp.cobblemon_workforce.block.PokeGeneratorBlock.ON) != hasPokemon) {
                    level.setBlock(pos, state.setValue(com.nbp.cobblemon_workforce.block.PokeGeneratorBlock.ON, hasPokemon), 3)
                }
            }
        }

        private fun breederPowerFor(pokemon: Pokemon): Int {
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

        private fun rangeFor(power: Int): Int = (4 + power / 80).coerceIn(4, 12)

        private fun intervalFor(power: Int): Int = (360 - power / 2).coerceIn(100, 360)
    }

    data class StoredPokemon(
        val ownerId: UUID,
        val pcId: UUID,
        val pokemonId: UUID,
        val linkId: UUID,
        val ownerName: String,
        val pokemonName: String,
        val level: Int,
        val breederPower: Int
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
                it.putInt("BreederPower", breederPower)
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
                    breederPower = nbt.getInt("BreederPower").takeIf { it > 0 } ?: 1
                )
            }
        }
    }
}
