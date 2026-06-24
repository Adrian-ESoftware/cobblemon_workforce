package com.nbp.cobblemon_workforce.energy

import com.nbp.cobblemon_workforce.block.entity.PokeGeneratorBlockEntity

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
