package com.nbp.cobblemon_electrified.pasture

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.pasture.PastureLink
import com.cobblemon.mod.common.api.pasture.PastureLinkManager
import com.cobblemon.mod.common.api.pasture.PasturePermissions
import com.cobblemon.mod.common.api.types.ElementalTypes
import com.cobblemon.mod.common.net.messages.client.pasture.ClosePasturePacket
import com.cobblemon.mod.common.net.messages.client.pasture.OpenPasturePacket
import com.cobblemon.mod.common.net.messages.client.pasture.PokemonPasturedPacket
import com.cobblemon.mod.common.net.messages.client.pasture.PokemonUnpasturedPacket
import com.cobblemon.mod.common.net.messages.server.pasture.PasturePokemonPacket
import com.cobblemon.mod.common.net.messages.server.pasture.SetPastureConflictPacket
import com.cobblemon.mod.common.net.messages.server.pasture.UnpastureAllPokemonPacket
import com.cobblemon.mod.common.net.messages.server.pasture.UnpasturePokemonPacket
import com.nbp.cobblemon_electrified.block.entity.PokeGeneratorBlockEntity
import com.nbp.cobblemon_electrified.config.CobblemonElectrifiedConfig
import java.util.UUID
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object PokeGeneratorPastureBridge {
    fun open(player: ServerPlayer, pos: BlockPos, generator: PokeGeneratorBlockEntity) {
        val pc = Cobblemon.storage.getPC(player)
        val linkId = UUID.randomUUID()
        val maxPokemon = generator.slotLimit()
        val permissions = PasturePermissions(
            canUnpastureOthers = false,
            canPasture = generator.canAddPokemon(),
            maxPokemon = maxPokemon
        )

        OpenPasturePacket(
            pcId = pc.uuid,
            pastureId = linkId,
            limit = maxPokemon,
            tetheredPokemon = generator.toPastureDTOs(player),
            permissions = permissions
        ).sendToPlayer(player)

        val dimension = ResourceLocation.tryParse(player.level().dimensionTypeRegistration().registeredName) ?: return
        PastureLinkManager.createLink(player.uuid, PastureLink(linkId, pc.uuid, dimension, pos, permissions))
    }

    @JvmStatic
    fun handlePasturePokemon(packet: PasturePokemonPacket, server: MinecraftServer, player: ServerPlayer): Boolean {
        val link = PastureLinkManager.getLinkByPlayer(player) ?: return false
        if (link.linkId != packet.pastureId) {
            ClosePasturePacket().sendToPlayer(player)
            return true
        }

        val generator = player.level().getBlockEntity(link.pos) as? PokeGeneratorBlockEntity ?: return false
        if (!generator.canAddPokemon()) {
            player.sendSystemMessage(Component.literal("This Poke Power Generator is full."))
            return true
        }

        val pc = Cobblemon.storage.getPC(link.pcId, player.registryAccess())
        val pokemon = pc[packet.pokemonId] ?: return true
        if (pokemon.types.none { it == ElementalTypes.ELECTRIC }) {
            player.sendSystemMessage(Component.literal("Only Electric-type Pokemon can power this generator."))
            return true
        }
        if (pokemon.isFainted()) {
            player.sendSystemMessage(Component.literal("Fainted Pokemon cannot power this generator."))
            return true
        }
        if (pokemon.tetheringId != null) {
            player.sendSystemMessage(Component.literal("That Pokemon is already assigned somewhere else."))
            return true
        }

        if (!generator.addPokemon(player, pokemon, pc.uuid)) {
            player.sendSystemMessage(Component.literal("This Poke Power Generator is full."))
            return true
        }
        generator.toPastureDTOs(player).firstOrNull { it.pokemonId == pokemon.uuid }?.let { PokemonPasturedPacket(it).sendToPlayer(player) }
        player.sendSystemMessage(
            Component.literal("${pokemon.getDisplayName().string} is now producing ${generator.generationRate} RF/t from ${generator.powerStatName}.")
        )
        return true
    }

    @JvmStatic
    fun handleUnpasturePokemon(packet: UnpasturePokemonPacket, server: MinecraftServer, player: ServerPlayer): Boolean {
        val link = PastureLinkManager.getLinkByPlayer(player) ?: return false
        val generator = player.level().getBlockEntity(link.pos) as? PokeGeneratorBlockEntity ?: return false
        if (!generator.ownsPokemon(player, packet.pokemonId)) {
            ClosePasturePacket().sendToPlayer(player)
            return true
        }

        generator.removePokemon(packet.pokemonId)
        PokemonUnpasturedPacket(packet.pokemonId).sendToPlayer(player)
        return true
    }

    @JvmStatic
    fun handleUnpastureAllPokemon(packet: UnpastureAllPokemonPacket, server: MinecraftServer, player: ServerPlayer): Boolean {
        val link = PastureLinkManager.getLinkByPlayer(player) ?: return false
        val generator = player.level().getBlockEntity(link.pos) as? PokeGeneratorBlockEntity ?: return false
        if (!generator.isOwner(player)) {
            ClosePasturePacket().sendToPlayer(player)
            return true
        }

        val pokemonIds = generator.storedPokemonIdsFor(player)
        pokemonIds.forEach {
            generator.removePokemon(it)
            PokemonUnpasturedPacket(it).sendToPlayer(player)
        }
        return true
    }

    @JvmStatic
    fun handleSetPastureConflict(packet: SetPastureConflictPacket, server: MinecraftServer, player: ServerPlayer): Boolean {
        val link = PastureLinkManager.getLinkByPlayer(player) ?: return false
        return player.level().getBlockEntity(link.pos) is PokeGeneratorBlockEntity
    }
}
