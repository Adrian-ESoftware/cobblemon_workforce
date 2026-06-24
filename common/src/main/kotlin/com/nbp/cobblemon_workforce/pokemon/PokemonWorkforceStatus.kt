package com.nbp.cobblemon_workforce.pokemon

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.pokemon.status.Statuses
import com.nbp.cobblemon_workforce.config.CobblemonWorkforceConfig
import java.util.UUID
import net.minecraft.server.level.ServerLevel

object PokemonWorkforceStatus {
    fun isAwake(world: ServerLevel, pcId: UUID, pokemonId: UUID): Boolean {
        if (!CobblemonWorkforceConfig.sleepStatusPausesMachines) {
            return true
        }
        val pokemon = Cobblemon.storage.getPC(pcId, world.registryAccess())[pokemonId] ?: return false
        return pokemon.status?.status != Statuses.SLEEP
    }
}
