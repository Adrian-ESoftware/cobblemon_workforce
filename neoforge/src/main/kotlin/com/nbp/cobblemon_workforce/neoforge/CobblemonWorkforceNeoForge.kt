package com.nbp.cobblemon_workforce.neoforge

import com.nbp.cobblemon_workforce.CobblemonWorkforce
import com.nbp.cobblemon_workforce.block.CobblemonWorkforceBlocks
import com.nbp.cobblemon_workforce.energy.PokeGeneratorEnergyOutput
import net.neoforged.fml.ModLoadingContext
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent

@Mod(CobblemonWorkforce.MOD_ID)
class CobblemonWorkforceNeoForge {
    init {
        CobblemonWorkforce.init()
        PokeGeneratorEnergyOutput.output = PokeGeneratorEnergyExporter::output
        PokeGeneratorEnergyOutput.invalidate = PokeGeneratorEnergyExporter::invalidate
        ModLoadingContext.get().activeContainer.eventBus?.addListener(
            RegisterCapabilitiesEvent::class.java,
            ::registerCapabilities
        )
    }

    private fun registerCapabilities(event: RegisterCapabilitiesEvent) {
        event.registerBlockEntity(
            Capabilities.EnergyStorage.BLOCK,
            CobblemonWorkforceBlocks.POKE_GENERATOR_BLOCK_ENTITY
        ) { blockEntity, _ ->
            PokeGeneratorEnergyStorage(blockEntity)
        }
    }
}
