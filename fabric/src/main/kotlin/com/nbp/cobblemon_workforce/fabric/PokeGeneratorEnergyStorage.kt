package com.nbp.cobblemon_workforce.fabric

import com.nbp.cobblemon_workforce.block.entity.PokeGeneratorBlockEntity
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant
import team.reborn.energy.api.EnergyStorage

class PokeGeneratorEnergyStorage(private val generator: PokeGeneratorBlockEntity) : EnergyStorage, SnapshotParticipant<Int>() {
    override fun supportsInsertion(): Boolean = false
    override fun insert(amount: Long, transaction: TransactionContext?): Long = 0L

    override fun supportsExtraction(): Boolean = true
    override fun extract(amount: Long, transaction: TransactionContext): Long {
        if (amount <= 0) return 0L
        val maxExtract = amount.coerceAtMost(generator.energyStored.toLong()).toInt()
        if (maxExtract <= 0) return 0L

        updateSnapshots(transaction)
        generator.energyStored -= maxExtract
        generator.setChanged()
        return maxExtract.toLong()
    }

    override fun getAmount(): Long = generator.energyStored.toLong()
    override fun getCapacity(): Long = PokeGeneratorBlockEntity.MAX_ENERGY.toLong()

    override fun createSnapshot(): Int {
        return generator.energyStored
    }

    override fun readSnapshot(snapshot: Int) {
        generator.energyStored = snapshot
    }

    override fun onFinalCommit() {
        generator.setChanged()
    }
}
