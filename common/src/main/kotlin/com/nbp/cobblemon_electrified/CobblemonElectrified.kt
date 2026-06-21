package com.nbp.cobblemon_electrified

import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.nbp.cobblemon_electrified.block.CobblemonElectrifiedBlocks
import net.minecraft.network.chat.Component
import org.slf4j.LoggerFactory

object CobblemonElectrified {
    const val MOD_ID = "cobblemon_electrified"

    private val logger = LoggerFactory.getLogger("CobblemonElectrified")

    fun init() {
        logger.info("Meu Addon Cobblemon carregado!")

        CobblemonElectrifiedBlocks.register()

        CobblemonEvents.POKEMON_CAPTURED.subscribe { event ->
            val player = event.player
            val pokemon = event.pokemon

            player.sendSystemMessage(
                Component.literal("Voce capturou: ${pokemon.species.name}")
            )
        }
    }
}
