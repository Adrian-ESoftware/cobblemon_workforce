package com.nbp.cobblemon_workforce.block

import com.mojang.serialization.MapCodec
import com.nbp.cobblemon_workforce.block.entity.FarmAssistantBlockEntity
import com.nbp.cobblemon_workforce.pasture.FarmAssistantPastureBridge
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
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult

class FarmAssistantBlock(properties: Properties) : PokeGeneratorBlock(properties) {
    companion object {
        val CODEC: MapCodec<FarmAssistantBlock> = simpleCodec(::FarmAssistantBlock)
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
        val isWaterUpgrade = stack.`is`(ModRegistry.FARM_ASSISTANT_WATER_SLOT_UPGRADE.get())
        val isNormalUpgrade = stack.`is`(ModRegistry.FARM_ASSISTANT_NORMAL_SLOT_UPGRADE.get())
        if (!isWaterUpgrade && !isNormalUpgrade) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
        }

        if (level.isClientSide || player !is ServerPlayer) {
            return ItemInteractionResult.SUCCESS
        }

        val basePos = if (state.getValue(PART) == GeneratorPart.TOP) pos.below() else pos
        val assistant = level.getBlockEntity(basePos) as? FarmAssistantBlockEntity ?: return ItemInteractionResult.SUCCESS
        val upgraded = if (isWaterUpgrade) assistant.upgradeWaterSlot() else assistant.upgradeNormalSlot()
        if (upgraded) {
            if (!player.isCreative) {
                stack.shrink(1)
            }
            val slotName = if (isWaterUpgrade) "Water" else "Normal"
            player.sendSystemMessage(Component.literal("Farm Assistant unlocked its $slotName Pokemon slot."))
        } else {
            val slotName = if (isWaterUpgrade) "Water" else "Normal"
            player.sendSystemMessage(Component.literal("This Farm Assistant already has its $slotName Pokemon slot unlocked."))
        }

        return ItemInteractionResult.SUCCESS
    }

    override fun setPlacedBy(level: Level, pos: BlockPos, state: BlockState, placer: LivingEntity?, stack: ItemStack) {
        super.setPlacedBy(level, pos, state, placer, stack)
        if (!level.isClientSide) {
            val assistant = level.getBlockEntity(pos) as? FarmAssistantBlockEntity ?: return
            stack.get(DataComponents.BLOCK_ENTITY_DATA)?.let { assistant.loadUpgradeData(it.copyTag()) }
        }
    }

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity? {
        return if (state.getValue(PART) == GeneratorPart.BOTTOM) FarmAssistantBlockEntity(pos, state) else null
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
                val stack = (blockEntity as? FarmAssistantBlockEntity)?.toDroppedStack() ?: ItemStack(this)
                popResource(level, pos, stack)
                state.spawnAfterBreak(level, pos, tool, true)
            }
        }
    }

    override fun <T : BlockEntity> getTicker(level: Level, state: BlockState, type: BlockEntityType<T>): BlockEntityTicker<T>? {
        return createTickerHelper(type, CobblemonWorkforceBlocks.FARM_ASSISTANT_BLOCK_ENTITY, FarmAssistantBlockEntity::tick)
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
        val assistant = level.getBlockEntity(basePos) as? FarmAssistantBlockEntity ?: return InteractionResult.SUCCESS
        FarmAssistantPastureBridge.open(player, basePos, assistant)
        return InteractionResult.SUCCESS
    }

    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, movedByPiston: Boolean) {
        if (!state.`is`(newState.block) && state.getValue(PART) == GeneratorPart.BOTTOM) {
            (level.getBlockEntity(pos) as? FarmAssistantBlockEntity)?.releaseLinkedPokemon()
        }
        super.onRemove(state, level, pos, newState, movedByPiston)
    }

    private fun FarmAssistantBlockEntity.toDroppedStack(): ItemStack {
        return ItemStack(this@FarmAssistantBlock).also {
            BlockItem.setBlockEntityData(it, CobblemonWorkforceBlocks.FARM_ASSISTANT_BLOCK_ENTITY, saveUpgradeData())
        }
    }
}
