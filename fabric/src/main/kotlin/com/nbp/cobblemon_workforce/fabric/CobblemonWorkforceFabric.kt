package com.nbp.cobblemon_workforce.fabric

import com.nbp.cobblemon_workforce.CobblemonWorkforce
import com.nbp.cobblemon_workforce.block.CobblemonWorkforceBlocks
import com.nbp.cobblemon_workforce.energy.PokeGeneratorEnergyOutput
import net.fabricmc.api.ModInitializer
import team.reborn.energy.api.EnergyStorage

class CobblemonWorkforceFabric : ModInitializer {
    override fun onInitialize() {
        CobblemonWorkforce.init()
        PokeGeneratorEnergyOutput.output = PokeGeneratorEnergyExporter::output
        PokeGeneratorEnergyOutput.invalidate = PokeGeneratorEnergyExporter::invalidate

        EnergyStorage.SIDED.registerForBlockEntity(
            { blockEntity, direction -> PokeGeneratorEnergyStorage(blockEntity) },
            CobblemonWorkforceBlocks.POKE_GENERATOR_BLOCK_ENTITY
        )
    }
}
