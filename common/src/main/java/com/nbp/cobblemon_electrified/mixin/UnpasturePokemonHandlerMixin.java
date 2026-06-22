package com.nbp.cobblemon_electrified.mixin;

import com.cobblemon.mod.common.net.messages.server.pasture.UnpasturePokemonPacket;
import com.cobblemon.mod.common.net.serverhandling.pasture.UnpasturePokemonHandler;
import com.nbp.cobblemon_electrified.pasture.PokeGeneratorPastureBridge;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(UnpasturePokemonHandler.class)
public class UnpasturePokemonHandlerMixin {
    @Inject(method = "handle", at = @At("HEAD"), cancellable = true)
    private void cobblemonElectrified$handleGenerator(UnpasturePokemonPacket packet, MinecraftServer server, ServerPlayer player, CallbackInfo ci) {
        if (PokeGeneratorPastureBridge.handleUnpasturePokemon(packet, server, player)) {
            ci.cancel();
        }
    }
}
