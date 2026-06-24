package com.nbp.cobblemon_electrified.mixin;

import com.nbp.cobblemon_electrified.block.entity.FarmAssistantBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FarmBlock.class)
public class FarmBlockMixin {
    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void cobblemonElectrified$keepMoistNearFarmAssistant(
        BlockState state,
        ServerLevel level,
        BlockPos pos,
        RandomSource random,
        CallbackInfo ci
    ) {
        if (!FarmAssistantBlockEntity.isWateredByAssistant(level, pos)) {
            return;
        }

        if (state.getValue(FarmBlock.MOISTURE) < FarmBlock.MAX_MOISTURE) {
            level.setBlock(pos, state.setValue(FarmBlock.MOISTURE, FarmBlock.MAX_MOISTURE), Block.UPDATE_CLIENTS);
        }
        ci.cancel();
    }
}
