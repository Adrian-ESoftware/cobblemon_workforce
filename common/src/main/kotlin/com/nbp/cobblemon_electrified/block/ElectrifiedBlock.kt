package com.nbp.cobblemon_electrified.block

import com.mojang.serialization.MapCodec
import com.nbp.cobblemon_electrified.block.entity.PokeGeneratorBlockEntity
import com.nbp.cobblemon_electrified.pasture.PokeGeneratorPastureBridge
import com.nbp.cobblemon_electrified.registry.ModRegistry
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.level.ServerLevel
import net.minecraft.stats.Stats
import net.minecraft.world.InteractionResult
import net.minecraft.world.ItemInteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.GameRules
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.phys.BlockHitResult

class ElectrifiedBlock(properties: Properties) : BaseEntityBlock(properties) {
    companion object {
        val CODEC: MapCodec<ElectrifiedBlock> = simpleCodec(::ElectrifiedBlock)
    }

    override fun codec(): MapCodec<out BaseEntityBlock> = CODEC

    init {
        registerDefaultState(stateDefinition.any().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH))
    }

    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState? {
        return defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, context.horizontalDirection.opposite)
    }

    override fun setPlacedBy(level: Level, pos: BlockPos, state: BlockState, placer: LivingEntity?, stack: ItemStack) {
        super.setPlacedBy(level, pos, state, placer, stack)

        val generator = level.getBlockEntity(pos) as? PokeGeneratorBlockEntity ?: return
        stack.get(DataComponents.BLOCK_ENTITY_DATA)?.let { generator.loadUpgradeData(it.copyTag()) }
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(HorizontalDirectionalBlock.FACING)
    }

    override fun useItemOn(
        stack: ItemStack,
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        hit: BlockHitResult
    ): ItemInteractionResult {
        if (!stack.`is`(ModRegistry.POKE_GENERATOR_SLOT_UPGRADE.get())) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
        }

        if (level.isClientSide || player !is ServerPlayer) {
            return ItemInteractionResult.SUCCESS
        }

        val generator = level.getBlockEntity(pos) as? PokeGeneratorBlockEntity ?: return ItemInteractionResult.SUCCESS
        if (generator.upgradeSlots()) {
            if (!player.isCreative) {
                stack.shrink(1)
            }
            player.sendSystemMessage(Component.literal("Poke Power Generator upgraded to ${generator.unlockedSlots} Pokemon slots."))
        } else {
            player.sendSystemMessage(Component.literal("This Poke Power Generator already has the maximum number of Pokemon slots."))
        }

        return ItemInteractionResult.SUCCESS
    }

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity {
        return PokeGeneratorBlockEntity(pos, state)
    }

    override fun playerDestroy(
        level: Level,
        player: Player,
        pos: BlockPos,
        state: BlockState,
        blockEntity: BlockEntity?,
        tool: ItemStack
    ) {
        player.awardStat(Stats.BLOCK_MINED.get(this))
        player.causeFoodExhaustion(0.005F)

        if (level is ServerLevel && !player.isCreative && level.gameRules.getBoolean(GameRules.RULE_DOBLOCKDROPS)) {
            val stack = (blockEntity as? PokeGeneratorBlockEntity)?.toDroppedStack() ?: ItemStack(this)
            popResource(level, pos, stack)
            state.spawnAfterBreak(level, pos, tool, true)
        }
    }

    override fun <T : BlockEntity> getTicker(level: Level, state: BlockState, type: BlockEntityType<T>): BlockEntityTicker<T>? {
        return createTickerHelper(type, CobblemonElectrifiedBlocks.POKE_GENERATOR_BLOCK_ENTITY, PokeGeneratorBlockEntity::tick)
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

        val generator = level.getBlockEntity(pos) as? PokeGeneratorBlockEntity ?: return InteractionResult.SUCCESS

        PokeGeneratorPastureBridge.open(player, pos, generator)

        return InteractionResult.SUCCESS
    }

    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, movedByPiston: Boolean) {
        if (!state.`is`(newState.block)) {
            (level.getBlockEntity(pos) as? PokeGeneratorBlockEntity)?.releaseLinkedPokemon()
        }
        super.onRemove(state, level, pos, newState, movedByPiston)
    }

    private fun PokeGeneratorBlockEntity.toDroppedStack(): ItemStack {
        return ItemStack(this@ElectrifiedBlock).also {
            BlockItem.setBlockEntityData(it, CobblemonElectrifiedBlocks.POKE_GENERATOR_BLOCK_ENTITY, saveUpgradeData())
        }
    }
}
