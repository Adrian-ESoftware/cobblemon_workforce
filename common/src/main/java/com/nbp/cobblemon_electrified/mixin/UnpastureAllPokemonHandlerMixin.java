package com.nbp.cobblemon_electrified.mixin;

import com.cobblemon.mod.common.net.messages.server.pasture.UnpastureAllPokemonPacket;
import com.cobblemon.mod.common.net.serverhandling.pasture.UnpastureAllPokemonHandler;
import com.nbp.cobblemon_electrified.pasture.PokeGeneratorPastureBridge;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(UnpastureAllPokemonHandler.class)
public class UnpastureAllPokemonHandlerMixin {
    @Inject(method = "handle", at = @At("HEAD"), cancellable = true)
    private void cobblemonElectrified$handleGenerator(UnpastureAllPokemonPacket packet, MinecraftServer server, ServerPlayer player, CallbackInfo ci) {
        if (PokeGeneratorPastureBridge.handleUnpastureAllPokemon(packet, server, player)) {
            ci.cancel();
        }
    }
}
