package com.nbp.cobblemon_workforce.mixin;

import com.cobblemon.mod.common.net.messages.server.pasture.PasturePokemonPacket;
import com.cobblemon.mod.common.net.serverhandling.pasture.PasturePokemonHandler;
import com.nbp.cobblemon_workforce.pasture.PokeFarmPastureBridge;
import com.nbp.cobblemon_workforce.pasture.PokeBreederPastureBridge;
import com.nbp.cobblemon_workforce.pasture.PokeFurnacePastureBridge;
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

@Mixin(PasturePokemonHandler.class)
public class PasturePokemonHandlerMixin {
    @Inject(method = "handle", at = @At("HEAD"), cancellable = true)
    private void CobblemonWorkforce$handleGenerator(PasturePokemonPacket packet, MinecraftServer server, ServerPlayer player, CallbackInfo ci) {
        if (PokeGeneratorPastureBridge.handlePasturePokemon(packet, server, player)
                || PokeFarmPastureBridge.handlePasturePokemon(packet, server, player)
                || PokeVacuumPastureBridge.handlePasturePokemon(packet, server, player)
                || PokeMinerPastureBridge.handlePasturePokemon(packet, server, player)
                || PokeCrusherPastureBridge.handlePasturePokemon(packet, server, player)
                || PokeBreederPastureBridge.handlePasturePokemon(packet, server, player)
                || PokeFurnacePastureBridge.handlePasturePokemon(packet, server, player)
                || PokeLumberPastureBridge.handlePasturePokemon(packet, server, player)) {
            ci.cancel();
        }
    }
}
