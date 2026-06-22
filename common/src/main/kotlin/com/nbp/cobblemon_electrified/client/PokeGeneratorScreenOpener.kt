package com.nbp.cobblemon_electrified.client

import com.cobblemon.mod.common.api.storage.StorePosition
import com.cobblemon.mod.common.api.types.ElementalTypes
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.gui.pc.PCGUI
import com.cobblemon.mod.common.client.gui.pc.PCGUIConfiguration
import com.cobblemon.mod.common.pokemon.Pokemon
import com.nbp.cobblemon_electrified.net.SelectPokeGeneratorPokemonMessage
import java.util.UUID
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos

object PokeGeneratorScreenOpener {
    fun open(pos: BlockPos, pcId: UUID) {
        val client = Minecraft.getInstance()
        val pc = CobblemonClient.storage.pcStores[pcId] ?: return

        val configuration = PCGUIConfiguration(
            exitFunction = { it.onClose() },
            selectOverride = { pcGui: PCGUI, _: StorePosition, pokemon: Pokemon? ->
                if (pokemon != null && pokemon.types.any { it == ElementalTypes.ELECTRIC } && !pokemon.isFainted() && pokemon.tetheringId == null) {
                    SelectPokeGeneratorPokemonMessage(pos, pokemon.uuid).sendToServer()
                    pcGui.onClose()
                }
            },
            showParty = false,
            canSelect = { pokemon -> pokemon.types.any { it == ElementalTypes.ELECTRIC } && !pokemon.isFainted() && pokemon.tetheringId == null }
        )

        client.setScreen(PCGUI(pc = pc, party = CobblemonClient.storage.party, configuration = configuration))
    }
}
