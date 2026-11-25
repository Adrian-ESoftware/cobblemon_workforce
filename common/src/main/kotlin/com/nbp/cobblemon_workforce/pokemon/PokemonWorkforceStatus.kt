package com.nbp.cobblemon_workforce.pokemon

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonNetwork.sendPacket
import com.cobblemon.mod.common.api.pokemon.status.Statuses
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.net.messages.client.animation.PlayPosableAnimationPacket
import com.nbp.cobblemon_workforce.config.CobblemonWorkforceConfig
import java.util.UUID
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.AABB

object PokemonWorkforceStatus {
    private val SLEEP_ANIMS = setOf("sleep")

    fun isAwake(world: ServerLevel, pcId: UUID, pokemonId: UUID): Boolean {
        val pokemon = Cobblemon.storage.getPC(pcId, world.registryAccess())[pokemonId] ?: return false
        val sleeping = CobblemonWorkforceConfig.sleepStatusPausesMachines && pokemon.status?.status == Statuses.SLEEP
        syncSleepPose(world, pokemon.entity, sleeping)
        return !sleeping
    }

    private fun syncSleepPose(world: ServerLevel, entity: PokemonEntity?, sleeping: Boolean) {
        if (entity == null || !entity.isAlive) {
            return
        }
        if (sleeping) {
            if (entity.enablePoseTypeRecalculation) {
                entity.enablePoseTypeRecalculation = false
            }
            if (entity.getCurrentPoseType() != PoseType.SLEEP) {
                entity.entityData.set(PokemonEntity.POSE_TYPE, PoseType.SLEEP)
            }
            entity.navigation.stop()
            if (world.gameTime % 40L == 0L) {
                val packet = PlayPosableAnimationPacket(entity.id, SLEEP_ANIMS, emptyList())
                val area = AABB(entity.blockPosition()).inflate(48.0)
                world.getEntitiesOfClass(ServerPlayer::class.java, area) { true }.forEach {
                    it.sendPacket(packet)
                }
            }
        } else if (!entity.enablePoseTypeRecalculation) {
            entity.enablePoseTypeRecalculation = true
        }
    }
}
