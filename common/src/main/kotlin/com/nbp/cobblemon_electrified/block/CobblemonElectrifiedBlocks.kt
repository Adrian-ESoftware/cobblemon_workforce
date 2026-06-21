package com.nbp.cobblemon_electrified.block

import com.nbp.cobblemon_electrified.CobblemonElectrified
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.material.MapColor

object CobblemonElectrifiedBlocks {
    val POKE_GENERATOR: Block = registerBlock(
        "poke_generator",
        Block(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(1.5F)
                .sound(SoundType.WOOD)
        )
    )

    fun register() {
        // Accessing the object initializes the block and item registries.
    }

    private fun registerBlock(name: String, block: Block): Block {
        val id = ResourceLocation.fromNamespaceAndPath(CobblemonElectrified.MOD_ID, name)

        Registry.register(BuiltInRegistries.BLOCK, id, block)
        Registry.register(BuiltInRegistries.ITEM, id, BlockItem(block, Item.Properties()))

        return block
    }
}
