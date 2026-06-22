package com.nbp.cobblemon_electrified.energy

import com.nbp.cobblemon_electrified.block.entity.PokeGeneratorBlockEntity

object PokeGeneratorEnergyOutput {
    var output: ((PokeGeneratorBlockEntity) -> Unit)? = null
    var invalidate: ((PokeGeneratorBlockEntity) -> Unit)? = null

    fun pushEnergy(generator: PokeGeneratorBlockEntity) {
        output?.invoke(generator)
    }

    fun invalidate(generator: PokeGeneratorBlockEntity) {
        invalidate?.invoke(generator)
    }
}
