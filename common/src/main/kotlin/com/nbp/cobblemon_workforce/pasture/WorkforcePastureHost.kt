package com.nbp.cobblemon_workforce.pasture

import com.cobblemon.mod.common.net.messages.client.pasture.OpenPasturePacket
import com.cobblemon.mod.common.pokemon.Pokemon
import java.util.UUID
import net.minecraft.server.level.ServerPlayer

interface WorkforcePastureHost {
    val pastureDisplayName: String
    val wrongTypeMessage: String
    val slotUnavailableMessage: String
        get() = "This $pastureDisplayName is full."

    fun slotLimit(): Int
    fun canAddPokemon(): Boolean
    fun canAddPokemon(pokemon: Pokemon): Boolean = canAddPokemon()
    fun accepts(pokemon: Pokemon): Boolean
    fun addPokemon(player: ServerPlayer, pokemon: Pokemon, pcId: UUID): Boolean
    fun removePokemon(pokemonId: UUID): Boolean
    fun ownsPokemon(player: ServerPlayer, pokemonId: UUID): Boolean
    fun isOwner(player: ServerPlayer): Boolean
    fun storedPokemonIdsFor(player: ServerPlayer): List<UUID>
    fun toPastureDTOs(viewer: ServerPlayer): List<OpenPasturePacket.PasturePokemonDataDTO>
    fun onPasturedMessage(pokemon: Pokemon): String
}
