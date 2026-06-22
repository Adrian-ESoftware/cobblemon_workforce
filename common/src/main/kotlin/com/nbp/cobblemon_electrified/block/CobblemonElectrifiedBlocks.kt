package com.nbp.cobblemon_electrified.block

import com.nbp.cobblemon_electrified.block.entity.PokeGeneratorBlockEntity
import com.nbp.cobblemon_electrified.registry.ModRegistry
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntityType

object CobblemonElectrifiedBlocks {
    val POKE_GENERATOR: Block
        get() = ModRegistry.POKE_GENERATOR.get()

    val POKE_GENERATOR_BLOCK_ENTITY: BlockEntityType<PokeGeneratorBlockEntity>
        get() = ModRegistry.POKE_GENERATOR_BLOCK_ENTITY.get()

    fun register() {
        // As instâncias são inicializadas e registradas pelo ModRegistry.
    }
}
