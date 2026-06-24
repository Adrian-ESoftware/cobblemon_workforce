package com.nbp.cobblemon_workforce.mixin;

import com.cobblemon.mod.common.net.messages.server.pasture.UnpastureAllPokemonPacket;
import com.cobblemon.mod.common.net.serverhandling.pasture.UnpastureAllPokemonHandler;
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

@Mixin(UnpastureAllPokemonHandler.class)
public class UnpastureAllPokemonHandlerMixin {
    @Inject(method = "handle", at = @At("HEAD"), cancellable = true)
    private void CobblemonWorkforce$handleGenerator(UnpastureAllPokemonPacket packet, MinecraftServer server, ServerPlayer player, CallbackInfo ci) {
        if (PokeGeneratorPastureBridge.handleUnpastureAllPokemon(packet, server, player)
                || PokeFarmPastureBridge.handleUnpastureAllPokemon(packet, server, player)
                || PokeVacuumPastureBridge.handleUnpastureAllPokemon(packet, server, player)
                || PokeMinerPastureBridge.handleUnpastureAllPokemon(packet, server, player)
                || PokeCrusherPastureBridge.handleUnpastureAllPokemon(packet, server, player)
                || PokeBreederPastureBridge.handleUnpastureAllPokemon(packet, server, player)
                || PokeLumberPastureBridge.handleUnpastureAllPokemon(packet, server, player)) {
            ci.cancel();
        }
    }
}
