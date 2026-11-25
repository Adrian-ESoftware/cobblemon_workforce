package com.nbp.cobblemon_workforce.mixin;

import com.cobblemon.mod.common.net.messages.server.pasture.UnpastureAllPokemonPacket;
import com.cobblemon.mod.common.net.serverhandling.pasture.UnpastureAllPokemonHandler;
import com.nbp.cobblemon_workforce.pasture.WorkforcePastureBridge;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(UnpastureAllPokemonHandler.class)
public class UnpastureAllPokemonHandlerMixin {
    @Inject(method = "handle", at = @At("HEAD"), cancellable = true)
    private void CobblemonWorkforce$handleGenerator(UnpastureAllPokemonPacket packet, MinecraftServer server, ServerPlayer player, CallbackInfo ci) {
        if (WorkforcePastureBridge.handleUnpastureAllPokemon(packet, server, player)) {
            ci.cancel();
        }
    }
}
