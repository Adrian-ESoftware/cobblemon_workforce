package com.nbp.cobblemon_electrified.mixin;

import com.cobblemon.mod.common.net.messages.server.pasture.PasturePokemonPacket;
import com.cobblemon.mod.common.net.serverhandling.pasture.PasturePokemonHandler;
import com.nbp.cobblemon_electrified.pasture.FarmAssistantPastureBridge;
import com.nbp.cobblemon_electrified.pasture.PokeGeneratorPastureBridge;
import com.nbp.cobblemon_electrified.pasture.PokeVacuumPastureBridge;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PasturePokemonHandler.class)
public class PasturePokemonHandlerMixin {
    @Inject(method = "handle", at = @At("HEAD"), cancellable = true)
    private void cobblemonElectrified$handleGenerator(PasturePokemonPacket packet, MinecraftServer server, ServerPlayer player, CallbackInfo ci) {
        if (PokeGeneratorPastureBridge.handlePasturePokemon(packet, server, player)
                || FarmAssistantPastureBridge.handlePasturePokemon(packet, server, player)
                || PokeVacuumPastureBridge.handlePasturePokemon(packet, server, player)) {
            ci.cancel();
        }
    }
}
