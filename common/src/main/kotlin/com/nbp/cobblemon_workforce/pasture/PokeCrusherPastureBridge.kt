package com.nbp.cobblemon_workforce.pasture

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.pasture.PastureLink
import com.cobblemon.mod.common.api.pasture.PastureLinkManager
import com.cobblemon.mod.common.api.pasture.PasturePermissions
import com.cobblemon.mod.common.net.messages.client.pasture.ClosePasturePacket
import com.cobblemon.mod.common.net.messages.client.pasture.OpenPasturePacket
import com.cobblemon.mod.common.net.messages.client.pasture.PokemonPasturedPacket
import com.cobblemon.mod.common.net.messages.client.pasture.PokemonUnpasturedPacket
import com.cobblemon.mod.common.net.messages.server.pasture.PasturePokemonPacket
import com.cobblemon.mod.common.net.messages.server.pasture.SetPastureConflictPacket
import com.cobblemon.mod.common.net.messages.server.pasture.UnpastureAllPokemonPacket
import com.cobblemon.mod.common.net.messages.server.pasture.UnpasturePokemonPacket
import com.nbp.cobblemon_workforce.block.entity.PokeCrusherBlockEntity
import java.util.UUID
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object PokeCrusherPastureBridge {
    fun open(player: ServerPlayer, pos: BlockPos, crusher: PokeCrusherBlockEntity) {
        val pc = Cobblemon.storage.getPC(player)
        val linkId = UUID.randomUUID()
        val permissions = permissions(crusher)
        OpenPasturePacket(
            pcId = pc.uuid,
            pastureId = linkId,
            limit = crusher.slotLimit(),
            tetheredPokemon = crusher.toPastureDTOs(player),
            permissions = permissions
        ).sendToPlayer(player)
        val dimension = ResourceLocation.tryParse(player.level().dimensionTypeRegistration().registeredName) ?: return
        PastureLinkManager.createLink(player.uuid, PastureLink(linkId, pc.uuid, dimension, pos, permissions))
    }

    private fun syncOpen(player: ServerPlayer, link: PastureLink, crusher: PokeCrusherBlockEntity) {
        val permissions = permissions(crusher)
        OpenPasturePacket(
            pcId = link.pcId,
            pastureId = link.linkId,
            limit = crusher.slotLimit(),
            tetheredPokemon = crusher.toPastureDTOs(player),
            permissions = permissions
        ).sendToPlayer(player)
        PastureLinkManager.createLink(player.uuid, PastureLink(link.linkId, link.pcId, link.dimension, link.pos, permissions))
    }

    private fun permissions(crusher: PokeCrusherBlockEntity): PasturePermissions {
        return PasturePermissions(canUnpastureOthers = false, canPasture = crusher.canAddPokemon(), maxPokemon = crusher.slotLimit())
    }

    @JvmStatic
    fun handlePasturePokemon(packet: PasturePokemonPacket, server: MinecraftServer, player: ServerPlayer): Boolean {
        val link = PastureLinkManager.getLinkByPlayer(player) ?: return false
        if (link.linkId != packet.pastureId) {
            ClosePasturePacket().sendToPlayer(player)
            return true
        }
        val crusher = player.level().getBlockEntity(link.pos) as? PokeCrusherBlockEntity ?: return false
        val pc = Cobblemon.storage.getPC(link.pcId, player.registryAccess())
        val pokemon = pc[packet.pokemonId] ?: return true
        if (!crusher.accepts(pokemon)) {
            player.sendSystemMessage(Component.literal("Only Steel-type and Normal-type Pokemon can help this crusher."))
            return true
        }
        if (pokemon.isFainted()) {
            player.sendSystemMessage(Component.literal("Fainted Pokemon cannot power this crusher."))
            return true
        }
        if (pokemon.tetheringId != null) {
            player.sendSystemMessage(Component.literal("That Pokemon is already assigned somewhere else."))
            return true
        }
        if (!crusher.canAddPokemon(pokemon)) {
            player.sendSystemMessage(Component.literal("This Poke Crusher does not have an available slot for that Pokemon type."))
            return true
        }
        if (!crusher.addPokemon(player, pokemon, pc.uuid)) {
            player.sendSystemMessage(Component.literal("This Poke Crusher is full."))
            return true
        }
        crusher.toPastureDTOs(player).firstOrNull { it.pokemonId == pokemon.uuid }?.let { PokemonPasturedPacket(it).sendToPlayer(player) }
        syncOpen(player, link, crusher)
        player.sendSystemMessage(Component.literal("${pokemon.getDisplayName().string} is now crushing nearby ores."))
        return true
    }

    @JvmStatic
    fun handleUnpasturePokemon(packet: UnpasturePokemonPacket, server: MinecraftServer, player: ServerPlayer): Boolean {
        val link = PastureLinkManager.getLinkByPlayer(player) ?: return false
        val crusher = player.level().getBlockEntity(link.pos) as? PokeCrusherBlockEntity ?: return false
        if (!crusher.ownsPokemon(player, packet.pokemonId)) {
            ClosePasturePacket().sendToPlayer(player)
            return true
        }
        crusher.removePokemon(packet.pokemonId)
        PokemonUnpasturedPacket(packet.pokemonId).sendToPlayer(player)
        syncOpen(player, link, crusher)
        return true
    }

    @JvmStatic
    fun handleUnpastureAllPokemon(packet: UnpastureAllPokemonPacket, server: MinecraftServer, player: ServerPlayer): Boolean {
        val link = PastureLinkManager.getLinkByPlayer(player) ?: return false
        val crusher = player.level().getBlockEntity(link.pos) as? PokeCrusherBlockEntity ?: return false
        if (!crusher.isOwner(player)) {
            ClosePasturePacket().sendToPlayer(player)
            return true
        }
        crusher.storedPokemonIdsFor(player).forEach {
            crusher.removePokemon(it)
            PokemonUnpasturedPacket(it).sendToPlayer(player)
        }
        syncOpen(player, link, crusher)
        return true
    }

    @JvmStatic
    fun handleSetPastureConflict(packet: SetPastureConflictPacket, server: MinecraftServer, player: ServerPlayer): Boolean {
        val link = PastureLinkManager.getLinkByPlayer(player) ?: return false
        return player.level().getBlockEntity(link.pos) is PokeCrusherBlockEntity
    }
}
