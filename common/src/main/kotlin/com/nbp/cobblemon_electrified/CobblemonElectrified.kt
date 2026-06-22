package com.nbp.cobblemon_electrified

import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.nbp.cobblemon_electrified.block.CobblemonElectrifiedBlocks
import com.nbp.cobblemon_electrified.config.CobblemonElectrifiedConfig
import com.nbp.cobblemon_electrified.net.CobblemonElectrifiedNetwork
import com.nbp.cobblemon_electrified.registry.ModRegistry
import net.minecraft.network.chat.Component
import org.slf4j.LoggerFactory

object CobblemonElectrified {
    const val MOD_ID = "cobblemon_electrified"

    private val logger = LoggerFactory.getLogger("CobblemonElectrified")

    fun init() {
        CobblemonElectrifiedConfig.load()
        CobblemonElectrifiedNetwork.register()
        ModRegistry.init()
        logger.info("Meu Addon Cobblemon carregado!")

        CobblemonElectrifiedBlocks.register()

        CobblemonEvents.POKEMON_CAPTURED.subscribe { event ->
            val player = event.player
            val pokemon = event.pokemon

            player.sendSystemMessage(
                Component.literal("You caught: ${pokemon.species.name}")
            )
        }
    }
}
