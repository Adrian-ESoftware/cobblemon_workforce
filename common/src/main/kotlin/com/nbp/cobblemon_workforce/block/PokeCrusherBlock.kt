package com.nbp.cobblemon_workforce.block

import com.mojang.serialization.MapCodec
import com.nbp.cobblemon_workforce.block.entity.PokeCrusherBlockEntity
import com.nbp.cobblemon_workforce.pasture.PokeCrusherPastureBridge
import com.nbp.cobblemon_workforce.registry.ModRegistry
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.stats.Stats
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.ItemInteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.GameRules
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.level.storage.loot.LootParams
import net.minecraft.world.level.storage.loot.parameters.LootContextParams

class PokeCrusherBlock(properties: Properties) : PokeGeneratorBlock(properties) {
    companion object {
        val CODEC: MapCodec<PokeCrusherBlock> = simpleCodec(::PokeCrusherBlock)
    }

    override fun codec(): MapCodec<out BaseEntityBlock> = CODEC



    override fun useItemOn(
        stack: ItemStack,
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        hit: BlockHitResult
    ): ItemInteractionResult {
        if (!stack.`is`(ModRegistry.POKE_FARM_NORMAL_SLOT_UPGRADE.get())) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
        }
        if (level.isClientSide || player !is ServerPlayer) {
            return ItemInteractionResult.SUCCESS
        }
        val basePos = if (state.getValue(PART) == GeneratorPart.TOP) pos.below() else pos
        val crusher = level.getBlockEntity(basePos) as? PokeCrusherBlockEntity ?: return ItemInteractionResult.SUCCESS
        if (crusher.upgradeNormalSlot()) {
            if (!player.isCreative) stack.shrink(1)
            player.sendSystemMessage(Component.literal("Poke Crusher unlocked its Normal Pokemon slot."))
        } else {
            player.sendSystemMessage(Component.literal("This Poke Crusher already has its Normal Pokemon slot unlocked."))
        }
        return ItemInteractionResult.SUCCESS
    }

    override fun setPlacedBy(level: Level, pos: BlockPos, state: BlockState, placer: LivingEntity?, stack: ItemStack) {
        super.setPlacedBy(level, pos, state, placer, stack)
        if (!level.isClientSide) {
            val crusher = level.getBlockEntity(pos) as? PokeCrusherBlockEntity ?: return
            stack.get(DataComponents.BLOCK_ENTITY_DATA)?.let { crusher.loadUpgradeData(it.copyTag()) }
        }
    }

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity? {
        return if (state.getValue(PART) == GeneratorPart.BOTTOM) PokeCrusherBlockEntity(pos, state) else null
    }

    override fun getDrops(state: BlockState, builder: LootParams.Builder): List<ItemStack> {
        val part = state.getValue(PART)
        if (part != GeneratorPart.BOTTOM) {
            return emptyList()
        }
        val blockEntity = builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY)
        val stack = if (blockEntity is PokeCrusherBlockEntity) {
            blockEntity.toDroppedStack()
        } else {
            ItemStack(this)
        }
        return listOf(stack)
    }

    override fun playerDestroy(
        level: Level,
        player: Player,
        pos: BlockPos,
        state: BlockState,
        blockEntity: BlockEntity?,
        tool: ItemStack
    ) {
        super.playerDestroy(level, player, pos, state, blockEntity, tool)
        if (level is ServerLevel && !player.isCreative && level.gameRules.getBoolean(GameRules.RULE_DOBLOCKDROPS)) {
            if (state.getValue(PART) == GeneratorPart.BOTTOM) {
                (blockEntity as? PokeCrusherBlockEntity)?.dropStoredItems(level, pos)
            }
        }
    }

    override fun <T : BlockEntity> getTicker(level: Level, state: BlockState, type: BlockEntityType<T>): BlockEntityTicker<T>? {
        return createTickerHelper(type, CobblemonWorkforceBlocks.POKE_CRUSHER_BLOCK_ENTITY, PokeCrusherBlockEntity::tick)
    }

    override fun useWithoutItem(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hit: BlockHitResult
    ): InteractionResult {
        if (level.isClientSide || player !is ServerPlayer) {
            return InteractionResult.SUCCESS
        }
        val basePos = if (state.getValue(PART) == GeneratorPart.TOP) pos.below() else pos
        val crusher = level.getBlockEntity(basePos) as? PokeCrusherBlockEntity ?: return InteractionResult.SUCCESS
        PokeCrusherPastureBridge.open(player, basePos, crusher)
        return InteractionResult.SUCCESS
    }

    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, movedByPiston: Boolean) {
        if (!state.`is`(newState.block) && state.getValue(PART) == GeneratorPart.BOTTOM) {
            (level.getBlockEntity(pos) as? PokeCrusherBlockEntity)?.releaseLinkedPokemon()
        }
        super.onRemove(state, level, pos, newState, movedByPiston)
    }

    private fun PokeCrusherBlockEntity.toDroppedStack(): ItemStack {
        return ItemStack(this@PokeCrusherBlock).also {
            BlockItem.setBlockEntityData(it, CobblemonWorkforceBlocks.POKE_CRUSHER_BLOCK_ENTITY, saveUpgradeData())
        }
    }
}
