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
import com.nbp.cobblemon_workforce.block.entity.PokeVacuumBlockEntity
import java.util.UUID
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object PokeVacuumPastureBridge {
    fun open(player: ServerPlayer, pos: BlockPos, vacuum: PokeVacuumBlockEntity) {
        val pc = Cobblemon.storage.getPC(player)
        val linkId = UUID.randomUUID()
        val permissions = permissions(vacuum)

        OpenPasturePacket(
            pcId = pc.uuid,
            pastureId = linkId,
            limit = 1,
            tetheredPokemon = vacuum.toPastureDTOs(player),
            permissions = permissions
        ).sendToPlayer(player)

        val dimension = ResourceLocation.tryParse(player.level().dimensionTypeRegistration().registeredName) ?: return
        PastureLinkManager.createLink(player.uuid, PastureLink(linkId, pc.uuid, dimension, pos, permissions))
    }

    private fun syncOpen(player: ServerPlayer, link: PastureLink, vacuum: PokeVacuumBlockEntity) {
        val permissions = permissions(vacuum)
        OpenPasturePacket(
            pcId = link.pcId,
            pastureId = link.linkId,
            limit = 1,
            tetheredPokemon = vacuum.toPastureDTOs(player),
            permissions = permissions
        ).sendToPlayer(player)
        PastureLinkManager.createLink(player.uuid, PastureLink(link.linkId, link.pcId, link.dimension, link.pos, permissions))
    }

    private fun permissions(vacuum: PokeVacuumBlockEntity): PasturePermissions {
        return PasturePermissions(
            canUnpastureOthers = false,
            canPasture = vacuum.canAddPokemon(),
            maxPokemon = 1
        )
    }

    @JvmStatic
    fun handlePasturePokemon(packet: PasturePokemonPacket, server: MinecraftServer, player: ServerPlayer): Boolean {
        val link = PastureLinkManager.getLinkByPlayer(player) ?: return false
        if (link.linkId != packet.pastureId) {
            ClosePasturePacket().sendToPlayer(player)
            return true
        }

        val vacuum = player.level().getBlockEntity(link.pos) as? PokeVacuumBlockEntity ?: return false
        val pc = Cobblemon.storage.getPC(link.pcId, player.registryAccess())
        val pokemon = pc[packet.pokemonId] ?: return true

        if (!vacuum.accepts(pokemon)) {
            player.sendSystemMessage(Component.literal("Only Normal-type Pokemon can power this vacuum."))
            return true
        }
        if (pokemon.isFainted()) {
            player.sendSystemMessage(Component.literal("Fainted Pokemon cannot power this vacuum."))
            return true
        }
        if (pokemon.tetheringId != null) {
            player.sendSystemMessage(Component.literal("That Pokemon is already assigned somewhere else."))
            return true
        }
        if (!vacuum.addPokemon(player, pokemon, pc.uuid)) {
            player.sendSystemMessage(Component.literal("This Poke Vacuum is full."))
            return true
        }

        vacuum.toPastureDTOs(player).firstOrNull { it.pokemonId == pokemon.uuid }?.let { PokemonPasturedPacket(it).sendToPlayer(player) }
        syncOpen(player, link, vacuum)
        player.sendSystemMessage(Component.literal("${pokemon.getDisplayName().string} is now collecting nearby items."))
        return true
    }

    @JvmStatic
    fun handleUnpasturePokemon(packet: UnpasturePokemonPacket, server: MinecraftServer, player: ServerPlayer): Boolean {
        val link = PastureLinkManager.getLinkByPlayer(player) ?: return false
        val vacuum = player.level().getBlockEntity(link.pos) as? PokeVacuumBlockEntity ?: return false
        if (!vacuum.ownsPokemon(player, packet.pokemonId)) {
            ClosePasturePacket().sendToPlayer(player)
            return true
        }

        vacuum.removePokemon(packet.pokemonId)
        PokemonUnpasturedPacket(packet.pokemonId).sendToPlayer(player)
        syncOpen(player, link, vacuum)
        return true
    }

    @JvmStatic
    fun handleUnpastureAllPokemon(packet: UnpastureAllPokemonPacket, server: MinecraftServer, player: ServerPlayer): Boolean {
        val link = PastureLinkManager.getLinkByPlayer(player) ?: return false
        val vacuum = player.level().getBlockEntity(link.pos) as? PokeVacuumBlockEntity ?: return false
        if (!vacuum.isOwner(player)) {
            ClosePasturePacket().sendToPlayer(player)
            return true
        }

        vacuum.storedPokemonIdsFor(player).forEach {
            vacuum.removePokemon(it)
            PokemonUnpasturedPacket(it).sendToPlayer(player)
        }
        syncOpen(player, link, vacuum)
        return true
    }

    @JvmStatic
    fun handleSetPastureConflict(packet: SetPastureConflictPacket, server: MinecraftServer, player: ServerPlayer): Boolean {
        val link = PastureLinkManager.getLinkByPlayer(player) ?: return false
        return player.level().getBlockEntity(link.pos) is PokeVacuumBlockEntity
    }
}
