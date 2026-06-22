package com.nbp.cobblemon_electrified.net

import com.nbp.cobblemon_electrified.client.PokeGeneratorScreenOpener
import dev.architectury.networking.NetworkManager
import dev.architectury.networking.simple.BaseS2CMessage
import dev.architectury.networking.simple.MessageType
import java.util.UUID
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf

class OpenPokeGeneratorMessage(
    private val pos: BlockPos,
    private val pcId: UUID
) : BaseS2CMessage() {
    constructor(buf: RegistryFriendlyByteBuf) : this(buf.readBlockPos(), buf.readUUID())

    override fun getType(): MessageType = CobblemonElectrifiedNetwork.OPEN_POKE_GENERATOR

    override fun write(buf: RegistryFriendlyByteBuf) {
        buf.writeBlockPos(pos)
        buf.writeUUID(pcId)
    }

    override fun handle(context: NetworkManager.PacketContext) {
        PokeGeneratorScreenOpener.open(pos, pcId)
    }
}
