package com.nbp.cobblemon_electrified.net

import com.nbp.cobblemon_electrified.CobblemonElectrified
import dev.architectury.networking.simple.MessageType
import dev.architectury.networking.simple.SimpleNetworkManager

object CobblemonElectrifiedNetwork {
    private val NETWORK = SimpleNetworkManager.create(CobblemonElectrified.MOD_ID)

    lateinit var OPEN_POKE_GENERATOR: MessageType
        private set

    lateinit var SELECT_POKE_GENERATOR_POKEMON: MessageType
        private set

    fun register() {
        OPEN_POKE_GENERATOR = NETWORK.registerS2C("open_poke_generator", ::OpenPokeGeneratorMessage)
        SELECT_POKE_GENERATOR_POKEMON = NETWORK.registerC2S("select_poke_generator_pokemon", ::SelectPokeGeneratorPokemonMessage)
    }
}
