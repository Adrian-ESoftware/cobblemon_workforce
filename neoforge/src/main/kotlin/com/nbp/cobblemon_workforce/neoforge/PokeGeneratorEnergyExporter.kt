package com.nbp.cobblemon_workforce.neoforge

import com.nbp.cobblemon_workforce.block.entity.PokeGeneratorBlockEntity
import com.nbp.cobblemon_workforce.config.CobblemonWorkforceConfig
import net.minecraft.core.Direction
import net.neoforged.neoforge.capabilities.Capabilities

object PokeGeneratorEnergyExporter {
    fun invalidate(generator: PokeGeneratorBlockEntity) {
        generator.level?.invalidateCapabilities(generator.blockPos)
    }

    fun output(generator: PokeGeneratorBlockEntity) {
        val level = generator.level ?: return
        if (level.isClientSide || generator.energyStored <= 0) {
            return
        }

        for (direction in Direction.entries) {
            if (generator.energyStored <= 0) {
                return
            }

            val targetPos = generator.blockPos.relative(direction)
            val target = level.getCapability(Capabilities.EnergyStorage.BLOCK, targetPos, direction.opposite) ?: continue
            if (!target.canReceive()) {
                continue
            }

            val offered = generator.energyStored.coerceAtMost(CobblemonWorkforceConfig.maxOutputPerSide)
            val accepted = target.receiveEnergy(offered, true)
            if (accepted <= 0) {
                continue
            }

            val extracted = generator.extractEnergy(accepted, false)
            if (extracted > 0) {
                target.receiveEnergy(extracted, false)
            }
        }
    }
}
