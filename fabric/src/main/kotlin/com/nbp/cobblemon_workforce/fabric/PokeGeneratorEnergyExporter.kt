package com.nbp.cobblemon_workforce.fabric

import com.nbp.cobblemon_workforce.block.entity.PokeGeneratorBlockEntity
import com.nbp.cobblemon_workforce.config.CobblemonWorkforceConfig
import net.minecraft.core.Direction
import team.reborn.energy.api.EnergyStorage
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction

object PokeGeneratorEnergyExporter {
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
            val target = EnergyStorage.SIDED.find(level, targetPos, direction.opposite) ?: continue
            if (!target.supportsInsertion()) {
                continue
            }

            val offered = generator.energyStored.coerceAtMost(CobblemonWorkforceConfig.maxOutputPerSide).toLong()

            Transaction.openOuter().use { transaction ->
                val accepted = target.insert(offered, transaction)
                if (accepted > 0) {
                    generator.energyStored -= accepted.toInt()
                    generator.setChanged()
                    transaction.commit()
                }
            }
        }
    }

    fun invalidate(generator: PokeGeneratorBlockEntity) {
        // No-op for Fabric
    }
}
