package com.nbp.cobblemon_workforce.mixin;

import com.cobblemon.mod.common.net.messages.server.pasture.UnpasturePokemonPacket;
import com.cobblemon.mod.common.net.serverhandling.pasture.UnpasturePokemonHandler;
import com.nbp.cobblemon_workforce.pasture.PokeFarmPastureBridge;
import com.nbp.cobblemon_workforce.pasture.PokeBreederPastureBridge;
import com.nbp.cobblemon_workforce.pasture.PokeGeneratorPastureBridge;
import com.nbp.cobblemon_workforce.pasture.PokeCrusherPastureBridge;
import com.nbp.cobblemon_workforce.pasture.PokeMinerPastureBridge;
import com.nbp.cobblemon_workforce.pasture.PokeVacuumPastureBridge;
import com.nbp.cobblemon_workforce.pasture.PokeLumberPastureBridge;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(UnpasturePokemonHandler.class)
public class UnpasturePokemonHandlerMixin {
    @Inject(method = "handle", at = @At("HEAD"), cancellable = true)
    private void CobblemonWorkforce$handleGenerator(UnpasturePokemonPacket packet, MinecraftServer server, ServerPlayer player, CallbackInfo ci) {
        if (PokeGeneratorPastureBridge.handleUnpasturePokemon(packet, server, player)
                || PokeFarmPastureBridge.handleUnpasturePokemon(packet, server, player)
                || PokeVacuumPastureBridge.handleUnpasturePokemon(packet, server, player)
                || PokeMinerPastureBridge.handleUnpasturePokemon(packet, server, player)
                || PokeCrusherPastureBridge.handleUnpasturePokemon(packet, server, player)
                || PokeBreederPastureBridge.handleUnpasturePokemon(packet, server, player)
                || PokeLumberPastureBridge.handleUnpasturePokemon(packet, server, player)) {
            ci.cancel();
        }
    }
}
