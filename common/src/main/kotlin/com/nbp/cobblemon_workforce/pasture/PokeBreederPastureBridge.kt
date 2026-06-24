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
import com.nbp.cobblemon_workforce.block.entity.PokeBreederBlockEntity
import java.util.UUID
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object PokeBreederPastureBridge {
    fun open(player: ServerPlayer, pos: BlockPos, breeder: PokeBreederBlockEntity) {
        val pc = Cobblemon.storage.getPC(player)
        val linkId = UUID.randomUUID()
        val permissions = permissions(breeder)
        OpenPasturePacket(
            pcId = pc.uuid,
            pastureId = linkId,
            limit = 1,
            tetheredPokemon = breeder.toPastureDTOs(player),
            permissions = permissions
        ).sendToPlayer(player)
        val dimension = ResourceLocation.tryParse(player.level().dimensionTypeRegistration().registeredName) ?: return
        PastureLinkManager.createLink(player.uuid, PastureLink(linkId, pc.uuid, dimension, pos, permissions))
    }

    private fun syncOpen(player: ServerPlayer, link: PastureLink, breeder: PokeBreederBlockEntity) {
        val permissions = permissions(breeder)
        OpenPasturePacket(
            pcId = link.pcId,
            pastureId = link.linkId,
            limit = 1,
            tetheredPokemon = breeder.toPastureDTOs(player),
            permissions = permissions
        ).sendToPlayer(player)
        PastureLinkManager.createLink(player.uuid, PastureLink(link.linkId, link.pcId, link.dimension, link.pos, permissions))
    }

    private fun permissions(breeder: PokeBreederBlockEntity): PasturePermissions {
        return PasturePermissions(canUnpastureOthers = false, canPasture = breeder.canAddPokemon(), maxPokemon = 1)
    }

    @JvmStatic
    fun handlePasturePokemon(packet: PasturePokemonPacket, server: MinecraftServer, player: ServerPlayer): Boolean {
        val link = PastureLinkManager.getLinkByPlayer(player) ?: return false
        if (link.linkId != packet.pastureId) {
            ClosePasturePacket().sendToPlayer(player)
            return true
        }
        val breeder = player.level().getBlockEntity(link.pos) as? PokeBreederBlockEntity ?: return false
        val pc = Cobblemon.storage.getPC(link.pcId, player.registryAccess())
        val pokemon = pc[packet.pokemonId] ?: return true
        if (!breeder.accepts(pokemon)) {
            player.sendSystemMessage(Component.literal("Only Fairy-type Pokemon can help this breeder."))
            return true
        }
        if (pokemon.isFainted()) {
            player.sendSystemMessage(Component.literal("Fainted Pokemon cannot help this breeder."))
            return true
        }
        if (pokemon.tetheringId != null) {
            player.sendSystemMessage(Component.literal("That Pokemon is already assigned somewhere else."))
            return true
        }
        if (!breeder.addPokemon(player, pokemon, pc.uuid)) {
            player.sendSystemMessage(Component.literal("This Poke Breeder is full."))
            return true
        }
        breeder.toPastureDTOs(player).firstOrNull { it.pokemonId == pokemon.uuid }?.let { PokemonPasturedPacket(it).sendToPlayer(player) }
        syncOpen(player, link, breeder)
        player.sendSystemMessage(Component.literal("${pokemon.getDisplayName().string} is now helping nearby animals breed."))
        return true
    }

    @JvmStatic
    fun handleUnpasturePokemon(packet: UnpasturePokemonPacket, server: MinecraftServer, player: ServerPlayer): Boolean {
        val link = PastureLinkManager.getLinkByPlayer(player) ?: return false
        val breeder = player.level().getBlockEntity(link.pos) as? PokeBreederBlockEntity ?: return false
        if (!breeder.ownsPokemon(player, packet.pokemonId)) {
            ClosePasturePacket().sendToPlayer(player)
            return true
        }
        breeder.removePokemon(packet.pokemonId)
        PokemonUnpasturedPacket(packet.pokemonId).sendToPlayer(player)
        syncOpen(player, link, breeder)
        return true
    }

    @JvmStatic
    fun handleUnpastureAllPokemon(packet: UnpastureAllPokemonPacket, server: MinecraftServer, player: ServerPlayer): Boolean {
        val link = PastureLinkManager.getLinkByPlayer(player) ?: return false
        val breeder = player.level().getBlockEntity(link.pos) as? PokeBreederBlockEntity ?: return false
        if (!breeder.isOwner(player)) {
            ClosePasturePacket().sendToPlayer(player)
            return true
        }
        breeder.storedPokemonIdsFor(player).forEach {
            breeder.removePokemon(it)
            PokemonUnpasturedPacket(it).sendToPlayer(player)
        }
        syncOpen(player, link, breeder)
        return true
    }

    @JvmStatic
    fun handleSetPastureConflict(packet: SetPastureConflictPacket, server: MinecraftServer, player: ServerPlayer): Boolean {
        val link = PastureLinkManager.getLinkByPlayer(player) ?: return false
        return player.level().getBlockEntity(link.pos) is PokeBreederBlockEntity
    }
}
