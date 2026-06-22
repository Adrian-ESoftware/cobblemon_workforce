package com.nbp.cobblemon_electrified.neoforge

import com.nbp.cobblemon_electrified.block.entity.PokeGeneratorBlockEntity
import net.neoforged.neoforge.energy.IEnergyStorage

class PokeGeneratorEnergyStorage(private val generator: PokeGeneratorBlockEntity) : IEnergyStorage {
    override fun receiveEnergy(toReceive: Int, simulate: Boolean): Int = 0

    override fun extractEnergy(toExtract: Int, simulate: Boolean): Int {
        return generator.extractEnergy(toExtract, simulate)
    }

    override fun getEnergyStored(): Int = generator.energyStored

    override fun getMaxEnergyStored(): Int = PokeGeneratorBlockEntity.MAX_ENERGY

    override fun canExtract(): Boolean = true

    override fun canReceive(): Boolean = false
}
