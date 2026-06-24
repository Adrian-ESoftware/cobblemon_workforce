package com.nbp.cobblemon_workforce.mixin;

import com.cobblemon.mod.common.net.messages.server.pasture.SetPastureConflictPacket;
import com.cobblemon.mod.common.net.serverhandling.pasture.SetPastureConflictHandler;
import com.nbp.cobblemon_workforce.pasture.FarmAssistantPastureBridge;
import com.nbp.cobblemon_workforce.pasture.PokeBreederPastureBridge;
import com.nbp.cobblemon_workforce.pasture.PokeGeneratorPastureBridge;
import com.nbp.cobblemon_workforce.pasture.PokeCrusherPastureBridge;
import com.nbp.cobblemon_workforce.pasture.PokeMinerPastureBridge;
import com.nbp.cobblemon_workforce.pasture.PokeVacuumPastureBridge;
import com.nbp.cobblemon_workforce.pasture.WoodAssistantPastureBridge;
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
        if (PokeGeneratorPastureBridge.handleSetPastureConflict(packet, server, player)
                || FarmAssistantPastureBridge.handleSetPastureConflict(packet, server, player)
                || PokeVacuumPastureBridge.handleSetPastureConflict(packet, server, player)
                || PokeMinerPastureBridge.handleSetPastureConflict(packet, server, player)
                || PokeCrusherPastureBridge.handleSetPastureConflict(packet, server, player)
                || PokeBreederPastureBridge.handleSetPastureConflict(packet, server, player)
                || WoodAssistantPastureBridge.handleSetPastureConflict(packet, server, player)) {
            ci.cancel();
        }
    }
}
