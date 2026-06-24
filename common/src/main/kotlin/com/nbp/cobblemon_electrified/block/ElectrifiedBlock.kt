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
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.block.state.properties.EnumProperty
import net.minecraft.util.StringRepresentable
import com.nbp.cobblemon_electrified.block.CobblemonElectrifiedBlocks

open class ElectrifiedBlock(properties: Properties) : BaseEntityBlock(properties) {
    companion object {
        val CODEC: MapCodec<ElectrifiedBlock> = simpleCodec(::ElectrifiedBlock)
        val ON: BooleanProperty = BooleanProperty.create("on")
        val PART: EnumProperty<GeneratorPart> = EnumProperty.create("part", GeneratorPart::class.java)

        private val SHAPES: Map<Pair<GeneratorPart, Direction>, VoxelShape> = run {
            val map = mutableMapOf<Pair<GeneratorPart, Direction>, VoxelShape>()
            val horizontals = arrayOf(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)
            for (part in GeneratorPart.values()) {
                for (direction in horizontals) {
                    map[part to direction] = buildCollider(part == GeneratorPart.TOP, direction)
                }
            }
            map
        }

        private fun voxelShape(
            x1: Double, y1: Double, z1: Double,
            x2: Double, y2: Double, z2: Double,
            direction: Direction
        ): VoxelShape {
            return when (direction) {
                Direction.SOUTH -> Shapes.box(1.0 - x2, y1, 1.0 - z2, 1.0 - x1, y2, 1.0 - z1)
                Direction.EAST -> Shapes.box(1.0 - z2, y1, x1, 1.0 - z1, y2, x2)
                Direction.WEST -> Shapes.box(z1, y1, 1.0 - x2, z2, y2, 1.0 - x1)
                else -> Shapes.box(x1, y1, z1, x2, y2, z2)
            }
        }

        private fun buildCollider(top: Boolean, direction: Direction): VoxelShape {
            if (top) {
                return Shapes.or(
                    voxelShape(0.1875, 0.0, 0.0625, 0.8125, 0.0625, 0.3125, direction),
                    voxelShape(0.125, 0.0, 0.375, 0.875, 0.1875, 0.9375, direction),
                    voxelShape(0.1875, 0.1875, 0.4375, 0.8125, 0.6875, 0.9375, direction),
                    voxelShape(0.8125, 0.1875, 0.375, 0.875, 0.6875, 0.9375, direction),
                    voxelShape(0.125, 0.1875, 0.375, 0.1875, 0.6875, 0.9375, direction),
                    voxelShape(0.125, 0.6875, 0.375, 0.875, 0.75, 0.9375, direction)
                )
            } else {
                return Shapes.or(
                    voxelShape(0.875, 0.0, 0.0, 1.0, 1.0, 0.125, direction),
                    voxelShape(0.125, 0.0, 0.0, 0.875, 0.125, 0.125, direction),
                    voxelShape(0.125, 0.875, 0.0, 0.875, 1.0, 0.125, direction),
                    voxelShape(0.125, 0.125, 0.0625, 0.875, 0.875, 0.125, direction),
                    voxelShape(0.0625, 0.125, 0.125, 0.125, 0.875, 0.875, direction),
                    voxelShape(0.0, 0.0, 0.0, 0.125, 1.0, 0.125, direction),
                    voxelShape(0.125, 0.0, 0.125, 0.875, 0.125, 1.0, direction),
                    voxelShape(0.875, 0.125, 0.125, 0.9375, 0.875, 0.875, direction),
                    voxelShape(0.125, 0.875, 0.125, 0.875, 1.0, 1.0, direction),
                    voxelShape(0.875, 0.0, 0.875, 1.0, 1.0, 1.0, direction),
                    voxelShape(0.875, 0.0, 0.125, 1.0, 0.125, 0.875, direction),
                    voxelShape(0.875, 0.875, 0.125, 1.0, 1.0, 0.875, direction),
                    voxelShape(0.0, 0.875, 0.125, 0.125, 1.0, 0.875, direction),
                    voxelShape(0.0, 0.0, 0.125, 0.125, 0.125, 0.875, direction),
                    voxelShape(0.0, 0.0, 0.875, 0.125, 1.0, 1.0, direction),
                    voxelShape(0.0, 0.125, 0.375, 0.0625, 0.875, 0.625, direction),
                    voxelShape(0.9375, 0.125, 0.375, 1.0, 0.875, 0.625, direction),
                    voxelShape(0.1875, 0.1875, 0.05625, 0.8125, 0.75, 0.05625, direction),
                    voxelShape(0.1875, 0.125, 0.3125, 0.8125, 0.3125, 0.875, direction),
                    voxelShape(0.1875, 0.125, 0.3125, 0.8125, 0.3125, 0.875, direction),
                    voxelShape(0.1875, 0.0625, 0.875, 0.8125, 0.25, 0.875, direction),
                    voxelShape(0.1875, 0.25, 0.25, 0.1875, 0.4375, 0.875, direction),
                    voxelShape(0.8125, 0.25, 0.25, 0.8125, 0.4375, 0.875, direction),
                    voxelShape(0.1875, 0.3125, 0.3125, 0.8125, 0.5, 0.3125, direction),
                    voxelShape(0.25, 0.75, 0.3125, 0.75, 1.0, 0.8125, direction),
                    voxelShape(0.0, 0.0, 0.0, 0.0625, 1.0, 1.0, direction),
                    voxelShape(0.9375, 0.0, 0.0, 1.0, 1.0, 1.0, direction)
                )
            }
        }
    }

    enum class GeneratorPart : StringRepresentable {
        TOP, BOTTOM;
        override fun getSerializedName(): String = name.lowercase()
    }

    override fun codec(): MapCodec<out BaseEntityBlock> = CODEC

    override fun getShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext
    ): VoxelShape {
        val part = state.getValue(PART)
        val facing = state.getValue(HorizontalDirectionalBlock.FACING)
        return SHAPES[part to facing] ?: Shapes.block()
    }

    init {
        registerDefaultState(
            stateDefinition.any()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH)
                .setValue(ON, false)
                .setValue(PART, GeneratorPart.BOTTOM)
        )
    }

    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState? {
        val abovePosition = context.clickedPos.above()
        val world = context.level
        if (world.getBlockState(abovePosition).canBeReplaced(context) && !world.isOutsideBuildHeight(abovePosition)) {
            return defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, context.horizontalDirection.opposite)
                .setValue(ON, false)
                .setValue(PART, GeneratorPart.BOTTOM)
        }
        return null
    }

    override fun setPlacedBy(level: Level, pos: BlockPos, state: BlockState, placer: LivingEntity?, stack: ItemStack) {
        super.setPlacedBy(level, pos, state, placer, stack)
        if (!level.isClientSide) {
            level.setBlock(
                pos.above(),
                state.setValue(PART, GeneratorPart.TOP),
                3
            )
            val generator = level.getBlockEntity(pos) as? PokeGeneratorBlockEntity ?: return
            stack.get(DataComponents.BLOCK_ENTITY_DATA)?.let { generator.loadUpgradeData(it.copyTag()) }
        }
    }

    override fun updateShape(
        state: BlockState,
        direction: Direction,
        neighborState: BlockState,
        world: LevelAccessor,
        pos: BlockPos,
        neighborPos: BlockPos
    ): BlockState {
        val part = state.getValue(PART)
        if (part == GeneratorPart.TOP && direction == Direction.DOWN && !neighborState.`is`(this)) {
            return Blocks.AIR.defaultBlockState()
        }
        if (part == GeneratorPart.BOTTOM && direction == Direction.UP && !neighborState.`is`(this)) {
            return Blocks.AIR.defaultBlockState()
        }
        return super.updateShape(state, direction, neighborState, world, pos, neighborPos)
    }

    override fun playerWillDestroy(level: Level, pos: BlockPos, state: BlockState, player: Player): BlockState {
        if (!level.isClientSide) {
            val part = state.getValue(PART)
            if (part == GeneratorPart.TOP) {
                val below = pos.below()
                val belowState = level.getBlockState(below)
                if (belowState.`is`(this) && belowState.getValue(PART) == GeneratorPart.BOTTOM) {
                    level.destroyBlock(below, !player.isCreative)
                }
            } else {
                val above = pos.above()
                val aboveState = level.getBlockState(above)
                if (aboveState.`is`(this) && aboveState.getValue(PART) == GeneratorPart.TOP) {
                    level.setBlock(above, Blocks.AIR.defaultBlockState(), 35)
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player)
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(HorizontalDirectionalBlock.FACING, ON, PART)
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
        val basePos = if (state.getValue(PART) == GeneratorPart.TOP) pos.below() else pos
        
        if (!stack.`is`(ModRegistry.POKE_GENERATOR_SLOT_UPGRADE.get())) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
        }

        if (level.isClientSide || player !is ServerPlayer) {
            return ItemInteractionResult.SUCCESS
        }

        val generator = level.getBlockEntity(basePos) as? PokeGeneratorBlockEntity ?: return ItemInteractionResult.SUCCESS
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

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity? {
        return if (state.getValue(PART) == GeneratorPart.BOTTOM) PokeGeneratorBlockEntity(pos, state) else null
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
                val stack = (blockEntity as? PokeGeneratorBlockEntity)?.toDroppedStack() ?: ItemStack(this)
                popResource(level, pos, stack)
                state.spawnAfterBreak(level, pos, tool, true)
            }
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

        val basePos = if (state.getValue(PART) == GeneratorPart.TOP) pos.below() else pos
        val generator = level.getBlockEntity(basePos) as? PokeGeneratorBlockEntity ?: return InteractionResult.SUCCESS

        PokeGeneratorPastureBridge.open(player, basePos, generator)

        return InteractionResult.SUCCESS
    }

    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, movedByPiston: Boolean) {
        if (!state.`is`(newState.block)) {
            if (state.getValue(PART) == GeneratorPart.BOTTOM) {
                (level.getBlockEntity(pos) as? PokeGeneratorBlockEntity)?.releaseLinkedPokemon()
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston)
    }

    private fun PokeGeneratorBlockEntity.toDroppedStack(): ItemStack {
        return ItemStack(this@ElectrifiedBlock).also {
            BlockItem.setBlockEntityData(it, CobblemonElectrifiedBlocks.POKE_GENERATOR_BLOCK_ENTITY, saveUpgradeData())
        }
    }
}
