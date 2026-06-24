package com.nbp.cobblemon_workforce

import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.nbp.cobblemon_workforce.block.CobblemonWorkforceBlocks
import com.nbp.cobblemon_workforce.config.CobblemonWorkforceConfig
import com.nbp.cobblemon_workforce.net.CobblemonWorkforceNetwork
import com.nbp.cobblemon_workforce.registry.ModRegistry
import net.minecraft.network.chat.Component
import org.slf4j.LoggerFactory

object CobblemonWorkforce {
    const val MOD_ID = "cobblemon_workforce"

    private val logger = LoggerFactory.getLogger("CobblemonWorkforce")

    fun init() {
        CobblemonWorkforceConfig.load()
        CobblemonWorkforceNetwork.register()
        ModRegistry.init()
        logger.info("Meu Addon Cobblemon carregado!")

        CobblemonWorkforceBlocks.register()

        CobblemonEvents.POKEMON_CAPTURED.subscribe { event ->
            val player = event.player
            val pokemon = event.pokemon

            player.sendSystemMessage(
                Component.literal("You caught: ${pokemon.species.name}")
            )
        }
    }
}
