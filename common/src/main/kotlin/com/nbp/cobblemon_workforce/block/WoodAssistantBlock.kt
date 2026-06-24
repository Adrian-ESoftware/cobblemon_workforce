package com.nbp.cobblemon_workforce.block

import com.mojang.serialization.MapCodec
import com.nbp.cobblemon_workforce.block.entity.WoodAssistantBlockEntity
import com.nbp.cobblemon_workforce.pasture.WoodAssistantPastureBridge
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

class WoodAssistantBlock(properties: Properties) : PokeGeneratorBlock(properties) {
    companion object {
        val CODEC: MapCodec<WoodAssistantBlock> = simpleCodec(::WoodAssistantBlock)
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
        val isNormalUpgrade = stack.`is`(ModRegistry.FARM_ASSISTANT_NORMAL_SLOT_UPGRADE.get())
        val isPlantUpgrade = stack.`is`(ModRegistry.WOOD_ASSISTANT_PLANT_SLOT_UPGRADE.get())
        if (!isNormalUpgrade && !isPlantUpgrade) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
        }
        if (level.isClientSide || player !is ServerPlayer) {
            return ItemInteractionResult.SUCCESS
        }

        val basePos = if (state.getValue(PART) == GeneratorPart.TOP) pos.below() else pos
        val assistant = level.getBlockEntity(basePos) as? WoodAssistantBlockEntity ?: return ItemInteractionResult.SUCCESS
        val upgraded = if (isPlantUpgrade) assistant.upgradePlantSlot() else assistant.upgradeNormalSlot()
        val slotName = if (isPlantUpgrade) "Plant" else "Normal"
        if (upgraded) {
            if (!player.isCreative) {
                stack.shrink(1)
            }
            player.sendSystemMessage(Component.literal("Wood Assistant unlocked its $slotName Pokemon slot."))
        } else {
            player.sendSystemMessage(Component.literal("This Wood Assistant already has its $slotName Pokemon slot unlocked."))
        }
        return ItemInteractionResult.SUCCESS
    }

    override fun setPlacedBy(level: Level, pos: BlockPos, state: BlockState, placer: LivingEntity?, stack: ItemStack) {
        super.setPlacedBy(level, pos, state, placer, stack)
        if (!level.isClientSide) {
            val assistant = level.getBlockEntity(pos) as? WoodAssistantBlockEntity ?: return
            stack.get(DataComponents.BLOCK_ENTITY_DATA)?.let { assistant.loadUpgradeData(it.copyTag()) }
        }
    }

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity? {
        return if (state.getValue(PART) == GeneratorPart.BOTTOM) WoodAssistantBlockEntity(pos, state) else null
    }

    override fun playerDestroy(level: Level, player: Player, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?, tool: ItemStack) {
        player.awardStat(Stats.BLOCK_MINED.get(this))
        player.causeFoodExhaustion(0.005F)
        if (level is ServerLevel && !player.isCreative && level.gameRules.getBoolean(GameRules.RULE_DOBLOCKDROPS)) {
            if (state.getValue(PART) == GeneratorPart.BOTTOM) {
                (blockEntity as? WoodAssistantBlockEntity)?.dropStoredItems(level, pos)
                val stack = (blockEntity as? WoodAssistantBlockEntity)?.toDroppedStack() ?: ItemStack(this)
                popResource(level, pos, stack)
                state.spawnAfterBreak(level, pos, tool, true)
            }
        }
    }

    override fun <T : BlockEntity> getTicker(level: Level, state: BlockState, type: BlockEntityType<T>): BlockEntityTicker<T>? {
        return createTickerHelper(type, CobblemonWorkforceBlocks.WOOD_ASSISTANT_BLOCK_ENTITY, WoodAssistantBlockEntity::tick)
    }

    override fun useWithoutItem(state: BlockState, level: Level, pos: BlockPos, player: Player, hit: BlockHitResult): InteractionResult {
        if (level.isClientSide || player !is ServerPlayer) {
            return InteractionResult.SUCCESS
        }
        val basePos = if (state.getValue(PART) == GeneratorPart.TOP) pos.below() else pos
        val assistant = level.getBlockEntity(basePos) as? WoodAssistantBlockEntity ?: return InteractionResult.SUCCESS
        WoodAssistantPastureBridge.open(player, basePos, assistant)
        return InteractionResult.SUCCESS
    }

    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, movedByPiston: Boolean) {
        if (!state.`is`(newState.block) && state.getValue(PART) == GeneratorPart.BOTTOM) {
            (level.getBlockEntity(pos) as? WoodAssistantBlockEntity)?.releaseLinkedPokemon()
        }
        super.onRemove(state, level, pos, newState, movedByPiston)
    }

    private fun WoodAssistantBlockEntity.toDroppedStack(): ItemStack {
        return ItemStack(this@WoodAssistantBlock).also {
            BlockItem.setBlockEntityData(it, CobblemonWorkforceBlocks.WOOD_ASSISTANT_BLOCK_ENTITY, saveUpgradeData())
        }
    }
}
