package com.nbp.cobblemon_workforce

import com.nbp.cobblemon_workforce.block.CobblemonWorkforceBlocks
import com.nbp.cobblemon_workforce.config.CobblemonWorkforceConfig
import com.nbp.cobblemon_workforce.net.CobblemonWorkforceNetwork
import com.nbp.cobblemon_workforce.registry.ModRegistry
import org.slf4j.LoggerFactory

object CobblemonWorkforce {
    const val MOD_ID = "cobblemon_workforce"

    private val logger = LoggerFactory.getLogger("CobblemonWorkforce")

    fun init() {
        CobblemonWorkforceConfig.load()
        CobblemonWorkforceNetwork.register()
        ModRegistry.init()
        CobblemonWorkforceBlocks.register()
        logger.info("Cobblemon WorkForce initialized.")
    }
}
