package com.nbp.cobblemon_electrified.block.entity

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.pokemon.stats.Stat
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.api.types.ElementalTypes
import com.cobblemon.mod.common.net.messages.client.pasture.OpenPasturePacket
import com.cobblemon.mod.common.pokemon.Pokemon
import com.nbp.cobblemon_electrified.block.CobblemonElectrifiedBlocks
import com.nbp.cobblemon_electrified.config.CobblemonElectrifiedConfig
import java.util.UUID
import kotlin.math.min
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Container
import net.minecraft.world.WorldlyContainer
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB

class PokeVacuumBlockEntity(pos: BlockPos, state: BlockState) :
    BlockEntity(CobblemonElectrifiedBlocks.POKE_VACUUM_BLOCK_ENTITY, pos, state) {

    private var storedPokemon: StoredPokemon? = null
    private val collectionBuffer = mutableListOf<ItemStack>()

    fun hasPokemon(): Boolean = storedPokemon != null

    fun isOwner(player: ServerPlayer): Boolean = storedPokemon?.ownerId == player.uuid

    fun canAddPokemon(): Boolean = storedPokemon == null

    fun accepts(pokemon: Pokemon): Boolean = pokemon.types.any { it == ElementalTypes.NORMAL }

    fun addPokemon(player: ServerPlayer, pokemon: Pokemon, pcId: UUID): Boolean {
        if (!canAddPokemon() || !accepts(pokemon)) {
            return false
        }

        val linkId = UUID.randomUUID()
        pokemon.tetheringId = linkId
        storedPokemon = StoredPokemon(
            ownerId = player.uuid,
            pcId = pcId,
            pokemonId = pokemon.uuid,
            linkId = linkId,
            pokemonName = pokemon.getDisplayName().string,
            vacuumPower = vacuumPowerFor(pokemon)
        )
        setChanged()
        return true
    }

    fun removePokemon(pokemonId: UUID): Boolean {
        val stored = storedPokemon ?: return false
        if (stored.pokemonId != pokemonId) {
            return false
        }

        releaseLinkedPokemon(stored)
        storedPokemon = null
        setChanged()
        return true
    }

    fun ownsPokemon(player: ServerPlayer, pokemonId: UUID): Boolean {
        return storedPokemon?.let { it.ownerId == player.uuid && it.pokemonId == pokemonId } == true
    }

    fun storedPokemonIdsFor(player: ServerPlayer): List<UUID> {
        val stored = storedPokemon ?: return emptyList()
        return if (stored.ownerId == player.uuid) listOf(stored.pokemonId) else emptyList()
    }

    fun releaseLinkedPokemon() {
        storedPokemon?.let(::releaseLinkedPokemon)
    }

    private fun releaseLinkedPokemon(entry: StoredPokemon) {
        val registryAccess = level?.server?.registryAccess() ?: return
        val pokemon = Cobblemon.storage.getPC(entry.pcId, registryAccess)[entry.pokemonId] ?: return
        if (pokemon.tetheringId == entry.linkId) {
            pokemon.tetheringId = null
        }
    }

    fun toPastureDTOs(viewer: ServerPlayer): List<OpenPasturePacket.PasturePokemonDataDTO> {
        val entry = storedPokemon ?: return emptyList()
        val registryAccess = level?.server?.registryAccess() ?: viewer.registryAccess()
        val pokemon = Cobblemon.storage.getPC(entry.pcId, registryAccess)[entry.pokemonId] ?: return emptyList()
        return listOf(
            OpenPasturePacket.PasturePokemonDataDTO(
                pokemonId = pokemon.uuid,
                playerId = entry.ownerId,
                displayName = pokemon.getDisplayName(),
                ownerName = null,
                species = pokemon.species.resourceIdentifier,
                aspects = pokemon.aspects,
                heldItem = pokemon.heldItem(),
                level = pokemon.level,
                entityKnown = false,
                behaviourFlags = emptySet()
            )
        )
    }

    fun dropStoredItems(world: ServerLevel, pos: BlockPos) {
        collectionBuffer.filterNot { it.isEmpty }.forEach { Block.popResource(world, pos, it.copy()) }
        collectionBuffer.clear()
        setChanged()
    }

    override fun loadAdditional(nbt: CompoundTag, registryLookup: HolderLookup.Provider) {
        super.loadAdditional(nbt, registryLookup)
        storedPokemon = nbt.getCompound("Pokemon").takeUnless { it.isEmpty }?.let(StoredPokemon::fromNbt)
        collectionBuffer.clear()
        val bufferList = nbt.getList("CollectionBuffer", CompoundTag.TAG_COMPOUND.toInt())
        for (tag in bufferList) {
            tag as CompoundTag
            ItemStack.parseOptional(registryLookup, tag).takeUnless { it.isEmpty }?.let(collectionBuffer::add)
        }
    }

    override fun saveAdditional(nbt: CompoundTag, registryLookup: HolderLookup.Provider) {
        super.saveAdditional(nbt, registryLookup)
        storedPokemon?.let { nbt.put("Pokemon", it.toNbt()) }
        val bufferList = ListTag()
        collectionBuffer.filterNot { it.isEmpty }.forEach { bufferList.add(it.saveOptional(registryLookup)) }
        nbt.put("CollectionBuffer", bufferList)
    }

    private fun tickVacuum(world: ServerLevel) {
        val pokemon = storedPokemon ?: return
        flushCollectionBuffer(world, itemLimitFor(pokemon.vacuumPower))
        if (world.gameTime % intervalFor(pokemon.vacuumPower).toLong() != 0L) {
            return
        }

        val range = rangeFor(pokemon.vacuumPower).toDouble()
        val items = world.getEntitiesOfClass(ItemEntity::class.java, AABB(blockPos).inflate(range)) {
            it.isAlive && !it.item.isEmpty
        }.sortedBy { it.distanceToSqr(blockPos.x + 0.5, blockPos.y + 0.5, blockPos.z + 0.5) }

        var remainingItems = itemLimitFor(pokemon.vacuumPower)
        for (itemEntity in items) {
            if (remainingItems <= 0) {
                break
            }
            val stack = itemEntity.item.copy()
            val movedAmount = min(stack.count, remainingItems)
            val moving = stack.copy()
            moving.count = movedAmount
            val remainder = routeStack(world, moving)
            val moved = movedAmount - remainder.count
            if (moved <= 0) {
                continue
            }

            remainingItems -= moved
            stack.shrink(moved)
            if (stack.isEmpty) {
                itemEntity.discard()
            } else {
                itemEntity.item = stack
            }
        }
    }

    private fun routeStack(world: ServerLevel, stack: ItemStack): ItemStack {
        val remainder = insertIntoAdjacentInventories(world, stack)
        return if (remainder.isEmpty) ItemStack.EMPTY else addToCollectionBuffer(remainder)
    }

    private fun flushCollectionBuffer(world: ServerLevel, itemLimit: Int) {
        if (collectionBuffer.isEmpty()) {
            return
        }

        var remainingItems = itemLimit
        val iterator = collectionBuffer.listIterator()
        while (iterator.hasNext() && remainingItems > 0) {
            val buffered = iterator.next()
            if (buffered.isEmpty) {
                iterator.remove()
                continue
            }

            val moving = buffered.copy()
            val movedAmount = min(moving.count, remainingItems)
            moving.count = movedAmount
            val remainder = insertIntoAdjacentInventories(world, moving)
            val moved = movedAmount - remainder.count
            if (moved > 0) {
                buffered.shrink(moved)
                remainingItems -= moved
                setChanged()
            }
            if (buffered.isEmpty) {
                iterator.remove()
            }
        }
    }

    private fun addToCollectionBuffer(stack: ItemStack): ItemStack {
        var remainder = stack.copy()
        collectionBuffer.forEach { existing ->
            if (remainder.isEmpty) {
                return@forEach
            }
            if (ItemStack.isSameItemSameComponents(existing, remainder)) {
                val moved = min(remainder.count, existing.maxStackSize - existing.count)
                if (moved > 0) {
                    existing.grow(moved)
                    remainder.shrink(moved)
                }
            }
        }

        while (!remainder.isEmpty && collectionBuffer.size < COLLECTION_BUFFER_SLOTS) {
            val buffered = remainder.copy()
            buffered.count = min(remainder.count, remainder.maxStackSize)
            collectionBuffer += buffered
            remainder.shrink(buffered.count)
        }

        if (remainder.count != stack.count) {
            setChanged()
        }
        return remainder
    }

    private fun insertIntoAdjacentInventories(world: ServerLevel, stack: ItemStack): ItemStack {
        var remainder = stack.copy()
        adjacentInventoryPositions().forEach { (pos, face) ->
            if (remainder.isEmpty) {
                return@forEach
            }
            val container = world.getBlockEntity(pos) as? Container ?: return@forEach
            remainder = insertIntoContainer(container, remainder, face)
        }
        return remainder
    }

    private fun adjacentInventoryPositions(): List<Pair<BlockPos, Direction>> {
        val positions = linkedSetOf<Pair<BlockPos, Direction>>()
        listOf(blockPos, blockPos.above()).forEach { basePos ->
            Direction.entries.forEach { direction ->
                val inventoryPos = basePos.relative(direction)
                if (inventoryPos != blockPos && inventoryPos != blockPos.above()) {
                    positions += inventoryPos to direction.opposite
                }
            }
        }
        return positions.toList()
    }

    private fun insertIntoContainer(container: Container, stack: ItemStack, face: Direction): ItemStack {
        val remainder = stack.copy()
        val slots = if (container is WorldlyContainer) {
            container.getSlotsForFace(face).toList()
        } else {
            (0 until container.containerSize).toList()
        }

        slots.forEach { slot ->
            if (remainder.isEmpty) {
                return@forEach
            }
            if (!container.canPlaceItem(slot, remainder) || container is WorldlyContainer && !container.canPlaceItemThroughFace(slot, remainder, face)) {
                return@forEach
            }

            val existing = container.getItem(slot)
            if (existing.isEmpty) {
                val moved = min(remainder.count, min(remainder.maxStackSize, container.maxStackSize))
                val inserted = remainder.copy()
                inserted.count = moved
                container.setItem(slot, inserted)
                remainder.shrink(moved)
                container.setChanged()
            } else if (ItemStack.isSameItemSameComponents(existing, remainder)) {
                val maxStackSize = min(existing.maxStackSize, container.maxStackSize)
                val moved = min(remainder.count, maxStackSize - existing.count)
                if (moved > 0) {
                    existing.grow(moved)
                    remainder.shrink(moved)
                    container.setChanged()
                }
            }
        }

        return remainder
    }

    companion object {
        private const val COLLECTION_BUFFER_SLOTS = 27

        fun tick(level: Level, pos: BlockPos, state: BlockState, blockEntity: PokeVacuumBlockEntity) {
            if (level is ServerLevel) {
                blockEntity.tickVacuum(level)
            }
        }

        private fun vacuumPowerFor(pokemon: Pokemon): Int {
            val attack = calculatedPower(pokemon, Stats.ATTACK)
            val specialAttack = calculatedPower(pokemon, Stats.SPECIAL_ATTACK)
            return attack.coerceAtLeast(specialAttack)
        }

        private fun calculatedPower(pokemon: Pokemon, stat: Stat): Int {
            val baseStat = pokemon.form.baseStats[stat] ?: 0
            val iv = pokemon.ivs.getOrDefault(stat)
            val ev = pokemon.evs.getOrDefault(stat)
            val ivBonus = iv * CobblemonElectrifiedConfig.ivBonusPercentPerPoint / 100.0
            val evBonus = (ev / 4) * CobblemonElectrifiedConfig.evBonusPercentPer4Points / 100.0
            val levelMultiplier = if (pokemon.level <= 50) {
                pokemon.level.coerceAtLeast(1) / 50.0
            } else {
                1.0 + ((pokemon.level.coerceAtMost(100) - 50) / 50.0)
            }
            return (baseStat * (1.0 + ivBonus + evBonus) * levelMultiplier).toInt().coerceAtLeast(1)
        }

        private fun rangeFor(power: Int): Int = (3 + power / 64).coerceIn(3, 12)

        private fun intervalFor(power: Int): Int = (40 - power / 8).coerceIn(5, 40)

        private fun itemLimitFor(power: Int): Int = (8 + power / 4).coerceIn(8, 256)
    }

    data class StoredPokemon(
        val ownerId: UUID,
        val pcId: UUID,
        val pokemonId: UUID,
        val linkId: UUID,
        val pokemonName: String,
        val vacuumPower: Int
    ) {
        fun toNbt(): CompoundTag {
            return CompoundTag().also {
                it.putUUID("OwnerId", ownerId)
                it.putUUID("PcId", pcId)
                it.putUUID("PokemonId", pokemonId)
                it.putUUID("LinkId", linkId)
                it.putString("PokemonName", pokemonName)
                it.putInt("VacuumPower", vacuumPower)
            }
        }

        companion object {
            fun fromNbt(nbt: CompoundTag): StoredPokemon {
                return StoredPokemon(
                    ownerId = nbt.getUUID("OwnerId"),
                    pcId = nbt.getUUID("PcId"),
                    pokemonId = nbt.getUUID("PokemonId"),
                    linkId = nbt.getUUID("LinkId"),
                    pokemonName = nbt.getString("PokemonName"),
                    vacuumPower = nbt.getInt("VacuumPower").takeIf { it > 0 } ?: 1
                )
            }
        }
    }
}
