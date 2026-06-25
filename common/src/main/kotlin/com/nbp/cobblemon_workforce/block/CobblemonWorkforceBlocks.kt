package com.nbp.cobblemon_workforce.block

import com.nbp.cobblemon_workforce.block.entity.PokeGeneratorBlockEntity
import com.nbp.cobblemon_workforce.block.entity.PokeFarmBlockEntity
import com.nbp.cobblemon_workforce.block.entity.PokeBreederBlockEntity
import com.nbp.cobblemon_workforce.block.entity.PokeCrusherBlockEntity
import com.nbp.cobblemon_workforce.block.entity.PokeFurnaceBlockEntity
import com.nbp.cobblemon_workforce.block.entity.PokeMinerBlockEntity
import com.nbp.cobblemon_workforce.block.entity.PokeVacuumBlockEntity
import com.nbp.cobblemon_workforce.block.entity.PokeLumberBlockEntity
import com.nbp.cobblemon_workforce.registry.ModRegistry
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntityType

object CobblemonWorkforceBlocks {
    val POKE_GENERATOR: Block
        get() = ModRegistry.POKE_GENERATOR.get()

    val POKE_GENERATOR_BLOCK_ENTITY: BlockEntityType<PokeGeneratorBlockEntity>
        get() = ModRegistry.POKE_GENERATOR_BLOCK_ENTITY.get()

    val POKE_FARM: Block
        get() = ModRegistry.POKE_FARM.get()

    val POKE_FARM_BLOCK_ENTITY: BlockEntityType<PokeFarmBlockEntity>
        get() = ModRegistry.POKE_FARM_BLOCK_ENTITY.get()

    val POKE_VACUUM: Block
        get() = ModRegistry.POKE_VACUUM.get()

    val POKE_VACUUM_BLOCK_ENTITY: BlockEntityType<PokeVacuumBlockEntity>
        get() = ModRegistry.POKE_VACUUM_BLOCK_ENTITY.get()

    val POKE_MINER: Block
        get() = ModRegistry.POKE_MINER.get()

    val POKE_MINER_BLOCK_ENTITY: BlockEntityType<PokeMinerBlockEntity>
        get() = ModRegistry.POKE_MINER_BLOCK_ENTITY.get()

    val POKE_CRUSHER: Block
        get() = ModRegistry.POKE_CRUSHER.get()

    val POKE_CRUSHER_BLOCK_ENTITY: BlockEntityType<PokeCrusherBlockEntity>
        get() = ModRegistry.POKE_CRUSHER_BLOCK_ENTITY.get()

    val POKE_BREEDER: Block
        get() = ModRegistry.POKE_BREEDER.get()

    val POKE_BREEDER_BLOCK_ENTITY: BlockEntityType<PokeBreederBlockEntity>
        get() = ModRegistry.POKE_BREEDER_BLOCK_ENTITY.get()

    val POKE_FURNACE: Block
        get() = ModRegistry.POKE_FURNACE.get()

    val POKE_FURNACE_BLOCK_ENTITY: BlockEntityType<PokeFurnaceBlockEntity>
        get() = ModRegistry.POKE_FURNACE_BLOCK_ENTITY.get()

    val POKE_LUMBER: Block
        get() = ModRegistry.POKE_LUMBER.get()

    val POKE_LUMBER_BLOCK_ENTITY: BlockEntityType<PokeLumberBlockEntity>
        get() = ModRegistry.POKE_LUMBER_BLOCK_ENTITY.get()

    fun register() {
        // As instâncias são inicializadas e registradas pelo ModRegistry.
    }
}
