package com.nbp.cobblemon_electrified.net

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.types.ElementalTypes
import com.nbp.cobblemon_electrified.block.entity.PokeGeneratorBlockEntity
import dev.architectury.networking.NetworkManager
import dev.architectury.networking.simple.BaseC2SMessage
import dev.architectury.networking.simple.MessageType
import java.util.UUID
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

class SelectPokeGeneratorPokemonMessage(
    private val pos: BlockPos,
    private val pokemonId: UUID
) : BaseC2SMessage() {
    constructor(buf: RegistryFriendlyByteBuf) : this(buf.readBlockPos(), buf.readUUID())

    override fun getType(): MessageType = CobblemonElectrifiedNetwork.SELECT_POKE_GENERATOR_POKEMON

    override fun write(buf: RegistryFriendlyByteBuf) {
        buf.writeBlockPos(pos)
        buf.writeUUID(pokemonId)
    }

    override fun handle(context: NetworkManager.PacketContext) {
        val player = context.player as? ServerPlayer ?: return
        if (!pos.closerToCenterThan(player.position(), 10.0)) {
            return
        }

        val generator = player.level().getBlockEntity(pos) as? PokeGeneratorBlockEntity ?: return
        if (!generator.canAddPokemon()) {
            player.sendSystemMessage(Component.literal("This Poke Power Generator is full."))
            return
        }

        val pc = Cobblemon.storage.getPC(player)
        val pokemon = pc[pokemonId] ?: return
        if (pokemon.types.none { it == ElementalTypes.ELECTRIC }) {
            player.sendSystemMessage(Component.literal("Only Electric-type Pokemon can power this generator."))
            return
        }
        if (pokemon.isFainted()) {
            player.sendSystemMessage(Component.literal("Fainted Pokemon cannot power this generator."))
            return
        }
        if (pokemon.tetheringId != null) {
            player.sendSystemMessage(Component.literal("That Pokemon is already assigned somewhere else."))
            return
        }

        generator.addPokemon(player, pokemon, pc.uuid)
        player.sendSystemMessage(
            Component.literal("${pokemon.getDisplayName().string} is now producing ${generator.generationRate} RF/t from ${generator.powerStatName}.")
        )
    }
}
