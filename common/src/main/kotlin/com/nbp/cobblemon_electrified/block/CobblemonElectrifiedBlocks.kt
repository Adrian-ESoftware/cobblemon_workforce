package com.nbp.cobblemon_electrified.block

import com.nbp.cobblemon_electrified.block.entity.PokeGeneratorBlockEntity
import com.nbp.cobblemon_electrified.block.entity.FarmAssistantBlockEntity
import com.nbp.cobblemon_electrified.block.entity.PokeVacuumBlockEntity
import com.nbp.cobblemon_electrified.block.entity.WoodAssistantBlockEntity
import com.nbp.cobblemon_electrified.registry.ModRegistry
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntityType

object CobblemonElectrifiedBlocks {
    val POKE_GENERATOR: Block
        get() = ModRegistry.POKE_GENERATOR.get()

    val POKE_GENERATOR_BLOCK_ENTITY: BlockEntityType<PokeGeneratorBlockEntity>
        get() = ModRegistry.POKE_GENERATOR_BLOCK_ENTITY.get()

    val FARM_ASSISTANT: Block
        get() = ModRegistry.FARM_ASSISTANT.get()

    val FARM_ASSISTANT_BLOCK_ENTITY: BlockEntityType<FarmAssistantBlockEntity>
        get() = ModRegistry.FARM_ASSISTANT_BLOCK_ENTITY.get()

    val POKE_VACUUM: Block
        get() = ModRegistry.POKE_VACUUM.get()

    val POKE_VACUUM_BLOCK_ENTITY: BlockEntityType<PokeVacuumBlockEntity>
        get() = ModRegistry.POKE_VACUUM_BLOCK_ENTITY.get()

    val WOOD_ASSISTANT: Block
        get() = ModRegistry.WOOD_ASSISTANT.get()

    val WOOD_ASSISTANT_BLOCK_ENTITY: BlockEntityType<WoodAssistantBlockEntity>
        get() = ModRegistry.WOOD_ASSISTANT_BLOCK_ENTITY.get()

    fun register() {
        // As instâncias são inicializadas e registradas pelo ModRegistry.
    }
}
