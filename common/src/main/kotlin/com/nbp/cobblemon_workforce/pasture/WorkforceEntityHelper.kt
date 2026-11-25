package com.nbp.cobblemon_workforce.pasture

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonEntities
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import java.util.UUID

object WorkforceEntityHelper {
    const val WORKFORCE_ENTITY_TAG = "cobblemon_workforce_tethered"
    private const val LOAD_SPAWN_GRACE_TICKS = 40L

    fun ensureVisiblePokemon(
        world: ServerLevel,
        blockPos: BlockPos,
        loadedAtTick: Long,
        pokemonId: UUID,
        pcId: UUID,
        linkId: UUID,
        showPokemonOutside: Boolean,
        createTethering: (Int) -> PokemonPastureBlockEntity.Tethering,
        findSpawnPos: (PokemonEntity) -> BlockPos?,
        loggerContext: String
    ) {
        val pokemon = Cobblemon.storage.getPC(pcId, world.registryAccess())[pokemonId] ?: return
        val matches = world.allEntities
            .filterIsInstance<PokemonEntity>()
            .filter { ent ->
                ent.isAlive && (ent.tethering?.pokemonId == pokemonId || ent.pokemon.uuid == pokemonId)
            }
            .toList()

        val existing: PokemonEntity? = pokemon.entity?.takeIf {
            it.isAlive && (it.tethering == null || it.tethering?.tetheringId == linkId)
        } ?: matches.firstOrNull()

        matches.forEach { ent ->
            if (existing != null && ent != existing) {
                ent.discard()
            }
        }

        if (!showPokemonOutside) {
            existing?.discard()
            if (pokemon.tetheringId == linkId) {
                pokemon.tetheringId = null
            }
            return
        }

        if (existing != null) {
            existing.addTag(WORKFORCE_ENTITY_TAG)
            keepTethered(existing, blockPos, linkId, createTethering)
            return
        }

        // Wait briefly after world/chunk load so persisted entities finish loading before spawning.
        if (world.gameTime - loadedAtTick < LOAD_SPAWN_GRACE_TICKS) {
            return
        }

        pokemon.tetheringId = linkId
        val entity = createPokemonEntity(world, pokemon, loggerContext) ?: return
        val spawnPos = findSpawnPos(entity) ?: return
        entity.addTag(WORKFORCE_ENTITY_TAG)
        entity.setPos(spawnPos.center.subtract(0.0, 0.5, 0.0))
        if (world.addFreshEntity(entity)) {
            entity.tethering = createTethering(entity.id)
        }
    }

    fun keepTethered(
        entity: Entity,
        blockPos: BlockPos,
        linkId: UUID,
        createTethering: (Int) -> PokemonPastureBlockEntity.Tethering
    ) {
        val pokemonEntity = entity as? PokemonEntity ?: return
        pokemonEntity.addTag(WORKFORCE_ENTITY_TAG)
        val tethering = pokemonEntity.tethering
        if (tethering == null || tethering.pasturePos != blockPos || tethering.tetheringId != linkId) {
            pokemonEntity.tethering = createTethering(pokemonEntity.id)
        }
    }

    fun discardDuplicatesAndEntity(world: ServerLevel, pokemon: Pokemon?, pokemonId: UUID, linkId: UUID) {
        pokemon?.entity?.let { entity ->
            if (entity.tethering?.tetheringId == linkId || entity.tags.contains(WORKFORCE_ENTITY_TAG)) {
                entity.discard()
            }
        }
        world.allEntities
            .filterIsInstance<PokemonEntity>()
            .filter { ent ->
                ent.isAlive && (ent.tethering?.tetheringId == linkId || ent.pokemon.uuid == pokemonId)
            }
            .forEach { it.discard() }
    }

    private fun createPokemonEntity(world: ServerLevel, pokemon: Pokemon, loggerContext: String): PokemonEntity? {
        return runCatching {
            val entity = PokemonEntity(world, pokemon, CobblemonEntities.POKEMON)
            entity.refreshDimensions()
            entity
        }.getOrElse {
            Cobblemon.LOGGER.warn("Could not create visible $loggerContext Pokemon entity", it)
            null
        }
    }
}
