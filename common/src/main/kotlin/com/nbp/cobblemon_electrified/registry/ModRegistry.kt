package com.nbp.cobblemon_electrified.registry

import com.nbp.cobblemon_electrified.CobblemonElectrified
import com.nbp.cobblemon_electrified.block.ElectrifiedBlock
import com.nbp.cobblemon_electrified.block.entity.PokeGeneratorBlockEntity
import dev.architectury.registry.registries.DeferredRegister
import dev.architectury.registry.registries.RegistrySupplier
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.material.MapColor

object ModRegistry {
    val BLOCKS = DeferredRegister.create(CobblemonElectrified.MOD_ID, Registries.BLOCK)
    val ITEMS = DeferredRegister.create(CobblemonElectrified.MOD_ID, Registries.ITEM)
    val BLOCK_ENTITY_TYPES = DeferredRegister.create(CobblemonElectrified.MOD_ID, Registries.BLOCK_ENTITY_TYPE)
    val TABS = DeferredRegister.create(CobblemonElectrified.MOD_ID, Registries.CREATIVE_MODE_TAB)

    val POKE_GENERATOR: RegistrySupplier<Block> = BLOCKS.register("poke_generator") {
        ElectrifiedBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(1.5F)
                .sound(SoundType.WOOD)
                .noOcclusion()
        )
    }

    val POKE_GENERATOR_ITEM: RegistrySupplier<Item> = ITEMS.register("poke_generator") {
        BlockItem(POKE_GENERATOR.get(), Item.Properties())
    }

    val POKE_GENERATOR_SLOT_UPGRADE: RegistrySupplier<Item> = ITEMS.register("slot_upgrade") {
        Item(Item.Properties())
    }

    val POKE_GENERATOR_BLOCK_ENTITY: RegistrySupplier<BlockEntityType<PokeGeneratorBlockEntity>> = 
        BLOCK_ENTITY_TYPES.register("poke_generator") {
            BlockEntityType.Builder.of(::PokeGeneratorBlockEntity, POKE_GENERATOR.get()).build(null)
        }

    val ELECTRIFIED_TAB: RegistrySupplier<CreativeModeTab> = TABS.register("electrified_tab") {
        CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
            .title(Component.translatable("itemGroup.cobblemon_electrified.electrified_tab"))
            .icon { ItemStack(POKE_GENERATOR.get()) }
            .displayItems { parameters, output ->
                output.accept(POKE_GENERATOR.get())
                output.accept(POKE_GENERATOR_SLOT_UPGRADE.get())
            }
            .build()
    }

    fun init() {
        BLOCKS.register()
        ITEMS.register()
        BLOCK_ENTITY_TYPES.register()
        TABS.register()
    }
}
