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
import com.nbp.cobblemon_workforce.block.entity.PokeFarmBlockEntity
import java.util.UUID
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object PokeFarmPastureBridge {
    fun open(player: ServerPlayer, pos: BlockPos, assistant: PokeFarmBlockEntity) {
        val pc = Cobblemon.storage.getPC(player)
        val linkId = UUID.randomUUID()
        val maxPokemon = assistant.slotLimit()
        val permissions = PasturePermissions(
            canUnpastureOthers = false,
            canPasture = assistant.canAddPokemon(),
            maxPokemon = maxPokemon
        )

        OpenPasturePacket(
            pcId = pc.uuid,
            pastureId = linkId,
            limit = maxPokemon,
            tetheredPokemon = assistant.toPastureDTOs(player),
            permissions = permissions
        ).sendToPlayer(player)

        val dimension = ResourceLocation.tryParse(player.level().dimensionTypeRegistration().registeredName) ?: return
        PastureLinkManager.createLink(player.uuid, PastureLink(linkId, pc.uuid, dimension, pos, permissions))
    }

    private fun syncOpen(player: ServerPlayer, link: PastureLink, assistant: PokeFarmBlockEntity) {
        val maxPokemon = assistant.slotLimit()
        val permissions = PasturePermissions(
            canUnpastureOthers = false,
            canPasture = assistant.canAddPokemon(),
            maxPokemon = maxPokemon
        )

        OpenPasturePacket(
            pcId = link.pcId,
            pastureId = link.linkId,
            limit = maxPokemon,
            tetheredPokemon = assistant.toPastureDTOs(player),
            permissions = permissions
        ).sendToPlayer(player)

        PastureLinkManager.createLink(
            player.uuid,
            PastureLink(link.linkId, link.pcId, link.dimension, link.pos, permissions)
        )
    }

    @JvmStatic
    fun handlePasturePokemon(packet: PasturePokemonPacket, server: MinecraftServer, player: ServerPlayer): Boolean {
        val link = PastureLinkManager.getLinkByPlayer(player) ?: return false
        if (link.linkId != packet.pastureId) {
            ClosePasturePacket().sendToPlayer(player)
            return true
        }

        val assistant = player.level().getBlockEntity(link.pos) as? PokeFarmBlockEntity ?: return false
        val pc = Cobblemon.storage.getPC(link.pcId, player.registryAccess())
        val pokemon = pc[packet.pokemonId] ?: return true

        if (!assistant.accepts(pokemon)) {
            player.sendSystemMessage(Component.literal("Only Water-type, Grass-type, and Normal-type Pokemon can help this farm assistant."))
            return true
        }
        if (pokemon.isFainted()) {
            player.sendSystemMessage(Component.literal("Fainted Pokemon cannot help this farm assistant."))
            return true
        }
        if (pokemon.tetheringId != null) {
            player.sendSystemMessage(Component.literal("That Pokemon is already assigned somewhere else."))
            return true
        }
        if (!assistant.canAddPokemon(pokemon)) {
            player.sendSystemMessage(Component.literal("This Farm Assistant does not have an available slot for that Pokemon type."))
            return true
        }
        if (!assistant.addPokemon(player, pokemon, pc.uuid)) {
            player.sendSystemMessage(Component.literal("This Farm Assistant is full."))
            return true
        }

        assistant.toPastureDTOs(player).firstOrNull { it.pokemonId == pokemon.uuid }?.let { PokemonPasturedPacket(it).sendToPlayer(player) }
        syncOpen(player, link, assistant)
        player.sendSystemMessage(Component.literal("${pokemon.getDisplayName().string} is now helping nearby crops."))
        return true
    }

    @JvmStatic
    fun handleUnpasturePokemon(packet: UnpasturePokemonPacket, server: MinecraftServer, player: ServerPlayer): Boolean {
        val link = PastureLinkManager.getLinkByPlayer(player) ?: return false
        val assistant = player.level().getBlockEntity(link.pos) as? PokeFarmBlockEntity ?: return false
        if (!assistant.ownsPokemon(player, packet.pokemonId)) {
            ClosePasturePacket().sendToPlayer(player)
            return true
        }

        assistant.removePokemon(packet.pokemonId)
        PokemonUnpasturedPacket(packet.pokemonId).sendToPlayer(player)
        syncOpen(player, link, assistant)
        return true
    }

    @JvmStatic
    fun handleUnpastureAllPokemon(packet: UnpastureAllPokemonPacket, server: MinecraftServer, player: ServerPlayer): Boolean {
        val link = PastureLinkManager.getLinkByPlayer(player) ?: return false
        val assistant = player.level().getBlockEntity(link.pos) as? PokeFarmBlockEntity ?: return false
        if (!assistant.isOwner(player)) {
            ClosePasturePacket().sendToPlayer(player)
            return true
        }

        assistant.storedPokemonIdsFor(player).forEach {
            assistant.removePokemon(it)
            PokemonUnpasturedPacket(it).sendToPlayer(player)
        }
        syncOpen(player, link, assistant)
        return true
    }

    @JvmStatic
    fun handleSetPastureConflict(packet: SetPastureConflictPacket, server: MinecraftServer, player: ServerPlayer): Boolean {
        val link = PastureLinkManager.getLinkByPlayer(player) ?: return false
        return player.level().getBlockEntity(link.pos) is PokeFarmBlockEntity
    }
}
