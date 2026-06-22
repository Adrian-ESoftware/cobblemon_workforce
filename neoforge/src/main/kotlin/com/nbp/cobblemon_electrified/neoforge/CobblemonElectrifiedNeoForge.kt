package com.nbp.cobblemon_electrified.neoforge

import com.nbp.cobblemon_electrified.CobblemonElectrified
import com.nbp.cobblemon_electrified.block.CobblemonElectrifiedBlocks
import com.nbp.cobblemon_electrified.energy.PokeGeneratorEnergyOutput
import net.neoforged.fml.ModLoadingContext
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent

@Mod(CobblemonElectrified.MOD_ID)
class CobblemonElectrifiedNeoForge {
    init {
        CobblemonElectrified.init()
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
            CobblemonElectrifiedBlocks.POKE_GENERATOR_BLOCK_ENTITY
        ) { blockEntity, _ ->
            PokeGeneratorEnergyStorage(blockEntity)
        }
    }
}
