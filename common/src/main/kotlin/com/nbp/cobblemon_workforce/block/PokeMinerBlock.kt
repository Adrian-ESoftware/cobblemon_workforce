package com.nbp.cobblemon_workforce.block

import com.mojang.serialization.MapCodec
import com.nbp.cobblemon_workforce.block.entity.PokeMinerBlockEntity
import com.nbp.cobblemon_workforce.pasture.PokeMinerPastureBridge
import com.nbp.cobblemon_workforce.registry.ModRegistry
import net.minecraft.core.BlockPos
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
import net.minecraft.world.level.GameRules
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult

class PokeMinerBlock(properties: Properties) : PokeGeneratorBlock(properties) {
    companion object {
        val CODEC: MapCodec<PokeMinerBlock> = simpleCodec(::PokeMinerBlock)
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
        val miner = level.getBlockEntity(basePos) as? PokeMinerBlockEntity ?: return ItemInteractionResult.SUCCESS
        if (miner.upgradeNormalSlot()) {
            if (!player.isCreative) {
                stack.shrink(1)
            }
            player.sendSystemMessage(Component.literal("Poke Miner unlocked its Normal Pokemon slot."))
        } else {
            player.sendSystemMessage(Component.literal("This Poke Miner already has its Normal Pokemon slot unlocked."))
        }
        return ItemInteractionResult.SUCCESS
    }

    override fun setPlacedBy(level: Level, pos: BlockPos, state: BlockState, placer: LivingEntity?, stack: ItemStack) {
        super.setPlacedBy(level, pos, state, placer, stack)
        if (!level.isClientSide) {
            val miner = level.getBlockEntity(pos) as? PokeMinerBlockEntity ?: return
            stack.get(DataComponents.BLOCK_ENTITY_DATA)?.let { miner.loadUpgradeData(it.copyTag()) }
        }
    }

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity? {
        return if (state.getValue(PART) == GeneratorPart.BOTTOM) PokeMinerBlockEntity(pos, state) else null
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
            if (state.getValue(PART) == GeneratorPart.BOTTOM) {
                (blockEntity as? PokeMinerBlockEntity)?.dropStoredItems(level, pos)
                val stack = (blockEntity as? PokeMinerBlockEntity)?.toDroppedStack() ?: ItemStack(this)
                popResource(level, pos, stack)
                state.spawnAfterBreak(level, pos, tool, true)
            }
        }
    }

    override fun <T : BlockEntity> getTicker(level: Level, state: BlockState, type: BlockEntityType<T>): BlockEntityTicker<T>? {
        return createTickerHelper(type, CobblemonWorkforceBlocks.POKE_MINER_BLOCK_ENTITY, PokeMinerBlockEntity::tick)
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
        val miner = level.getBlockEntity(basePos) as? PokeMinerBlockEntity ?: return InteractionResult.SUCCESS
        PokeMinerPastureBridge.open(player, basePos, miner)
        return InteractionResult.SUCCESS
    }

    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, movedByPiston: Boolean) {
        if (!state.`is`(newState.block) && state.getValue(PART) == GeneratorPart.BOTTOM) {
            (level.getBlockEntity(pos) as? PokeMinerBlockEntity)?.releaseLinkedPokemon()
        }
        super.onRemove(state, level, pos, newState, movedByPiston)
    }

    private fun PokeMinerBlockEntity.toDroppedStack(): ItemStack {
        return ItemStack(this@PokeMinerBlock).also {
            BlockItem.setBlockEntityData(it, CobblemonWorkforceBlocks.POKE_MINER_BLOCK_ENTITY, saveUpgradeData())
        }
    }
}
