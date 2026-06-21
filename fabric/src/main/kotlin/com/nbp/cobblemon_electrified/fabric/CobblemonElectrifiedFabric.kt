package com.nbp.cobblemon_electrified.fabric

import com.nbp.cobblemon_electrified.CobblemonElectrified
import net.fabricmc.api.ModInitializer

class CobblemonElectrifiedFabric : ModInitializer {
    override fun onInitialize() {
        CobblemonElectrified.init()
    }
}
