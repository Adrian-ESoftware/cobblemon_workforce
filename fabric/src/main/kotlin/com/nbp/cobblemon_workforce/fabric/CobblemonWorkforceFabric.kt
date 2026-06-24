package com.nbp.cobblemon_workforce.fabric

import com.nbp.cobblemon_workforce.CobblemonWorkforce
import net.fabricmc.api.ModInitializer

class CobblemonWorkforceFabric : ModInitializer {
    override fun onInitialize() {
        CobblemonWorkforce.init()
    }
}
