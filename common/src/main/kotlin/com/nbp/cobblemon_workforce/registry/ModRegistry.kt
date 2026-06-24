package com.nbp.cobblemon_workforce.registry

import com.nbp.cobblemon_workforce.CobblemonWorkforce
import com.nbp.cobblemon_workforce.block.PokeGeneratorBlock
import com.nbp.cobblemon_workforce.block.FarmAssistantBlock
import com.nbp.cobblemon_workforce.block.PokeBreederBlock
import com.nbp.cobblemon_workforce.block.PokeCrusherBlock
import com.nbp.cobblemon_workforce.block.PokeMinerBlock
import com.nbp.cobblemon_workforce.block.PokeVacuumBlock
import com.nbp.cobblemon_workforce.block.WoodAssistantBlock
import com.nbp.cobblemon_workforce.block.entity.FarmAssistantBlockEntity
import com.nbp.cobblemon_workforce.block.entity.PokeBreederBlockEntity
import com.nbp.cobblemon_workforce.block.entity.PokeCrusherBlockEntity
import com.nbp.cobblemon_workforce.block.entity.PokeGeneratorBlockEntity
import com.nbp.cobblemon_workforce.block.entity.PokeMinerBlockEntity
import com.nbp.cobblemon_workforce.block.entity.PokeVacuumBlockEntity
import com.nbp.cobblemon_workforce.block.entity.WoodAssistantBlockEntity
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
    val BLOCKS = DeferredRegister.create(CobblemonWorkforce.MOD_ID, Registries.BLOCK)
    val ITEMS = DeferredRegister.create(CobblemonWorkforce.MOD_ID, Registries.ITEM)
    val BLOCK_ENTITY_TYPES = DeferredRegister.create(CobblemonWorkforce.MOD_ID, Registries.BLOCK_ENTITY_TYPE)
    val TABS = DeferredRegister.create(CobblemonWorkforce.MOD_ID, Registries.CREATIVE_MODE_TAB)

    val POKE_GENERATOR: RegistrySupplier<Block> = BLOCKS.register("poke_generator") {
        PokeGeneratorBlock(
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

    val FARM_ASSISTANT: RegistrySupplier<Block> = BLOCKS.register("farm_assistant") {
        FarmAssistantBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(1.5F)
                .sound(SoundType.WOOD)
                .noOcclusion()
        )
    }

    val FARM_ASSISTANT_ITEM: RegistrySupplier<Item> = ITEMS.register("farm_assistant") {
        BlockItem(FARM_ASSISTANT.get(), Item.Properties())
    }

    val POKE_VACUUM: RegistrySupplier<Block> = BLOCKS.register("poke_vacuum") {
        PokeVacuumBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(1.5F)
                .sound(SoundType.WOOD)
                .noOcclusion()
        )
    }

    val POKE_VACUUM_ITEM: RegistrySupplier<Item> = ITEMS.register("poke_vacuum") {
        BlockItem(POKE_VACUUM.get(), Item.Properties())
    }

    val POKE_MINER: RegistrySupplier<Block> = BLOCKS.register("poke_miner") {
        PokeMinerBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(1.5F)
                .sound(SoundType.WOOD)
                .noOcclusion()
        )
    }

    val POKE_MINER_ITEM: RegistrySupplier<Item> = ITEMS.register("poke_miner") {
        BlockItem(POKE_MINER.get(), Item.Properties())
    }

    val POKE_CRUSHER: RegistrySupplier<Block> = BLOCKS.register("poke_crusher") {
        PokeCrusherBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(1.5F)
                .sound(SoundType.WOOD)
                .noOcclusion()
        )
    }

    val POKE_CRUSHER_ITEM: RegistrySupplier<Item> = ITEMS.register("poke_crusher") {
        BlockItem(POKE_CRUSHER.get(), Item.Properties())
    }

    val POKE_BREEDER: RegistrySupplier<Block> = BLOCKS.register("poke_breeder") {
        PokeBreederBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(1.5F)
                .sound(SoundType.WOOD)
                .noOcclusion()
        )
    }

    val POKE_BREEDER_ITEM: RegistrySupplier<Item> = ITEMS.register("poke_breeder") {
        BlockItem(POKE_BREEDER.get(), Item.Properties())
    }

    val WOOD_ASSISTANT: RegistrySupplier<Block> = BLOCKS.register("wood_assistant") {
        WoodAssistantBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(1.5F)
                .sound(SoundType.WOOD)
                .noOcclusion()
        )
    }

    val WOOD_ASSISTANT_ITEM: RegistrySupplier<Item> = ITEMS.register("wood_assistant") {
        BlockItem(WOOD_ASSISTANT.get(), Item.Properties())
    }

    val POKE_GENERATOR_SLOT_UPGRADE: RegistrySupplier<Item> = ITEMS.register("slot_upgrade") {
        Item(Item.Properties())
    }

    val FARM_ASSISTANT_WATER_SLOT_UPGRADE: RegistrySupplier<Item> = ITEMS.register("water_slot_upgrade") {
        Item(Item.Properties())
    }

    val FARM_ASSISTANT_NORMAL_SLOT_UPGRADE: RegistrySupplier<Item> = ITEMS.register("normal_slot_upgrade") {
        Item(Item.Properties())
    }

    val WOOD_ASSISTANT_PLANT_SLOT_UPGRADE: RegistrySupplier<Item> = ITEMS.register("plant_slot_upgrade") {
        Item(Item.Properties())
    }

    val IRON_DUST: RegistrySupplier<Item> = ITEMS.register("iron_dust") { Item(Item.Properties()) }
    val COPPER_DUST: RegistrySupplier<Item> = ITEMS.register("copper_dust") { Item(Item.Properties()) }
    val GOLD_DUST: RegistrySupplier<Item> = ITEMS.register("gold_dust") { Item(Item.Properties()) }
    val COAL_DUST: RegistrySupplier<Item> = ITEMS.register("coal_dust") { Item(Item.Properties()) }
    val LAPIS_DUST: RegistrySupplier<Item> = ITEMS.register("lapis_dust") { Item(Item.Properties()) }
    val DIAMOND_DUST: RegistrySupplier<Item> = ITEMS.register("diamond_dust") { Item(Item.Properties()) }
    val EMERALD_DUST: RegistrySupplier<Item> = ITEMS.register("emerald_dust") { Item(Item.Properties()) }

    val POKE_GENERATOR_BLOCK_ENTITY: RegistrySupplier<BlockEntityType<PokeGeneratorBlockEntity>> = 
        BLOCK_ENTITY_TYPES.register("poke_generator") {
            BlockEntityType.Builder.of(::PokeGeneratorBlockEntity, POKE_GENERATOR.get()).build(null)
        }

    val FARM_ASSISTANT_BLOCK_ENTITY: RegistrySupplier<BlockEntityType<FarmAssistantBlockEntity>> =
        BLOCK_ENTITY_TYPES.register("farm_assistant") {
            BlockEntityType.Builder.of(::FarmAssistantBlockEntity, FARM_ASSISTANT.get()).build(null)
        }

    val POKE_VACUUM_BLOCK_ENTITY: RegistrySupplier<BlockEntityType<PokeVacuumBlockEntity>> =
        BLOCK_ENTITY_TYPES.register("poke_vacuum") {
            BlockEntityType.Builder.of(::PokeVacuumBlockEntity, POKE_VACUUM.get()).build(null)
        }

    val POKE_MINER_BLOCK_ENTITY: RegistrySupplier<BlockEntityType<PokeMinerBlockEntity>> =
        BLOCK_ENTITY_TYPES.register("poke_miner") {
            BlockEntityType.Builder.of(::PokeMinerBlockEntity, POKE_MINER.get()).build(null)
        }

    val POKE_CRUSHER_BLOCK_ENTITY: RegistrySupplier<BlockEntityType<PokeCrusherBlockEntity>> =
        BLOCK_ENTITY_TYPES.register("poke_crusher") {
            BlockEntityType.Builder.of(::PokeCrusherBlockEntity, POKE_CRUSHER.get()).build(null)
        }

    val POKE_BREEDER_BLOCK_ENTITY: RegistrySupplier<BlockEntityType<PokeBreederBlockEntity>> =
        BLOCK_ENTITY_TYPES.register("poke_breeder") {
            BlockEntityType.Builder.of(::PokeBreederBlockEntity, POKE_BREEDER.get()).build(null)
        }

    val WOOD_ASSISTANT_BLOCK_ENTITY: RegistrySupplier<BlockEntityType<WoodAssistantBlockEntity>> =
        BLOCK_ENTITY_TYPES.register("wood_assistant") {
            BlockEntityType.Builder.of(::WoodAssistantBlockEntity, WOOD_ASSISTANT.get()).build(null)
        }

    val WORKFORCE_TAB: RegistrySupplier<CreativeModeTab> = TABS.register("workforce_tab") {
        CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
            .title(Component.translatable("itemGroup.cobblemon_workforce.workforce_tab"))
            .icon { ItemStack(POKE_GENERATOR.get()) }
            .displayItems { parameters, output ->
                output.accept(POKE_GENERATOR.get())
                output.accept(FARM_ASSISTANT.get())
                output.accept(POKE_VACUUM.get())
                output.accept(POKE_MINER.get())
                output.accept(POKE_CRUSHER.get())
                output.accept(POKE_BREEDER.get())
                output.accept(WOOD_ASSISTANT.get())
                output.accept(POKE_GENERATOR_SLOT_UPGRADE.get())
                output.accept(FARM_ASSISTANT_WATER_SLOT_UPGRADE.get())
                output.accept(FARM_ASSISTANT_NORMAL_SLOT_UPGRADE.get())
                output.accept(WOOD_ASSISTANT_PLANT_SLOT_UPGRADE.get())
                output.accept(IRON_DUST.get())
                output.accept(COPPER_DUST.get())
                output.accept(GOLD_DUST.get())
                output.accept(COAL_DUST.get())
                output.accept(LAPIS_DUST.get())
                output.accept(DIAMOND_DUST.get())
                output.accept(EMERALD_DUST.get())
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
