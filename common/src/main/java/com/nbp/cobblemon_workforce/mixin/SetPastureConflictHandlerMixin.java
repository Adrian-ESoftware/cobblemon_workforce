package com.nbp.cobblemon_workforce.mixin;

import com.cobblemon.mod.common.net.messages.server.pasture.SetPastureConflictPacket;
import com.cobblemon.mod.common.net.serverhandling.pasture.SetPastureConflictHandler;
import com.nbp.cobblemon_workforce.pasture.WorkforcePastureBridge;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SetPastureConflictHandler.class)
public class SetPastureConflictHandlerMixin {
    @Inject(method = "handle", at = @At("HEAD"), cancellable = true)
    private void CobblemonWorkforce$handleGenerator(SetPastureConflictPacket packet, MinecraftServer server, ServerPlayer player, CallbackInfo ci) {
        if (WorkforcePastureBridge.handleSetPastureConflict(packet, server, player)) {
            ci.cancel();
        }
    }
}
