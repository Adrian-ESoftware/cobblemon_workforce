package com.nbp.cobblemon_workforce.pokemon

import com.cobblemon.mod.common.CobblemonNetwork.sendPacket
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.net.messages.client.animation.PlayPosableAnimationPacket
import com.cobblemon.mod.common.net.messages.client.effect.SpawnSnowstormEntityParticlePacket
import com.cobblemon.mod.common.net.messages.client.effect.SpawnSnowstormParticlePacket
import com.nbp.cobblemon_workforce.config.CobblemonWorkforceConfig
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.BlockParticleOption
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.Mob
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

object WorkforcePokemonEffects {
    private data class AttackEffect(
        val actorEffect: ResourceLocation? = null,
        val targetEffect: ResourceLocation? = null,
        val targetYOffset: Double = 0.5
    )

    private fun cobblemonEffect(path: String) = ResourceLocation.fromNamespaceAndPath("cobblemon", path)

    private val ELECTRIC_EFFECTS = listOf(
        AttackEffect(cobblemonEffect("thunder_actor"), cobblemonEffect("thunder_target"), targetYOffset = 0.85),
        AttackEffect(cobblemonEffect("thunderbolt_actor"), cobblemonEffect("thunderbolt_target"), targetYOffset = 0.85)
    )

    private val WATER_EFFECTS = listOf(
        AttackEffect(cobblemonEffect("watergun_actor"), cobblemonEffect("watergun_target")),
        AttackEffect(cobblemonEffect("bubble_actor"), cobblemonEffect("bubble_targetpop"))
    )

    private val GRASS_EFFECTS = listOf(
        AttackEffect(cobblemonEffect("leafage_actor"), cobblemonEffect("leafage_target")),
        AttackEffect(cobblemonEffect("leafstorm_actor"), cobblemonEffect("leafstorm_target"))
    )

    private val ROCK_EFFECTS = listOf(
        AttackEffect(cobblemonEffect("bulldoze_actorquake"), cobblemonEffect("bulldoze_targetrumble")),
        AttackEffect(null, cobblemonEffect("stomp_target_impact1"))
    )

    private val STEEL_EFFECTS = listOf(
        AttackEffect(null, cobblemonEffect("bulletpunch_target")),
        AttackEffect(null, cobblemonEffect("bulletpunch_targetsparks"))
    )

    private val FIGHTING_PUNCH_EFFECT = AttackEffect(
        cobblemonEffect("closecombat_actor"),
        cobblemonEffect("closecombat_target")
    )

    private val FIRE_EFFECTS = listOf(
        AttackEffect(cobblemonEffect("flamethrower_actor"), cobblemonEffect("flamethrower_target")),
        AttackEffect(cobblemonEffect("ember_actor"), cobblemonEffect("ember_target"))
    )

    private val PSYCHIC_EFFECT = AttackEffect(
        cobblemonEffect("confusion_actor"),
        cobblemonEffect("confusion_target")
    )

    private val BREEDER_EFFECT = AttackEffect(
        cobblemonEffect("disarmingvoice_actorhearts"),
        cobblemonEffect("disarmingvoice_target")
    )

    private val PHYSICAL_ANIMS = setOf("physical", "attack", "cry")
    private val SPECIAL_ANIMS = setOf("special", "attack", "status", "cry")
    private val STATUS_ANIMS = setOf("status", "cry")

    fun playThunderStrike(world: ServerLevel, entity: Entity, generatorPos: BlockPos) {
        playAttack(world, entity, generatorPos, SPECIAL_ANIMS, ELECTRIC_EFFECTS.randomPick(world))
    }

    fun playWaterFarmWork(world: ServerLevel, entity: Entity, targetPos: BlockPos) {
        playAttack(world, entity, targetPos, SPECIAL_ANIMS, WATER_EFFECTS.randomPick(world))
    }

    fun playGrassFarmWork(world: ServerLevel, entity: Entity, targetPos: BlockPos) {
        playAttack(world, entity, targetPos, PHYSICAL_ANIMS, GRASS_EFFECTS.randomPick(world))
    }

    fun playRockMineWork(world: ServerLevel, entity: Entity, targetPos: BlockPos) {
        playAttack(world, entity, targetPos, PHYSICAL_ANIMS, ROCK_EFFECTS.randomPick(world))
    }

    fun playSteelCrushWork(world: ServerLevel, entity: Entity, targetPos: BlockPos) {
        playAttack(world, entity, targetPos, PHYSICAL_ANIMS, STEEL_EFFECTS.randomPick(world))
    }

    fun playFireFurnaceWork(world: ServerLevel, entity: Entity, targetPos: BlockPos) {
        playAttack(world, entity, targetPos, SPECIAL_ANIMS, FIRE_EFFECTS.randomPick(world))
    }

    fun playPsychicVacuumWork(world: ServerLevel, entity: Entity, targetPos: BlockPos) {
        playAttack(world, entity, targetPos, SPECIAL_ANIMS, PSYCHIC_EFFECT)
    }

    fun playBreederWork(world: ServerLevel, entity: Entity, targetPos: BlockPos) {
        playAttack(world, entity, targetPos, STATUS_ANIMS, BREEDER_EFFECT)
    }

    fun playPhysicalWork(world: ServerLevel, entity: Entity, targetPos: BlockPos) {
        lookAt(entity, targetPos)
        sendAnimation(world, entity, PHYSICAL_ANIMS)
    }

    fun playSpecialWork(world: ServerLevel, entity: Entity, targetPos: BlockPos) {
        lookAt(entity, targetPos)
        sendAnimation(world, entity, SPECIAL_ANIMS)
    }

    fun playStatusWork(world: ServerLevel, entity: Entity, targetPos: BlockPos) {
        lookAt(entity, targetPos)
        sendAnimation(world, entity, STATUS_ANIMS)
    }

    fun playTreePunchHit(world: ServerLevel, entity: Entity?, targetPos: BlockPos) {
        if (!CobblemonWorkforceConfig.enableWorkAnimations) {
            if (entity != null) lookAt(entity, targetPos)
            return
        }
        if (entity != null) {
            playAttack(world, entity, targetPos, PHYSICAL_ANIMS, FIGHTING_PUNCH_EFFECT)
        }
        val state = world.getBlockState(targetPos)
        if (!state.isAir) {
            world.sendParticles(
                BlockParticleOption(ParticleTypes.BLOCK, state),
                targetPos.x + 0.5,
                targetPos.y + 0.5,
                targetPos.z + 0.5,
                8,
                0.35,
                0.35,
                0.35,
                0.1
            )
            world.sendParticles(
                ParticleTypes.CRIT,
                targetPos.x + 0.5,
                targetPos.y + 0.5,
                targetPos.z + 0.5,
                4,
                0.3,
                0.3,
                0.3,
                0.05
            )
            world.playSound(
                null,
                targetPos,
                SoundEvents.WOOD_HIT,
                SoundSource.BLOCKS,
                0.8f,
                0.9f + world.random.nextFloat() * 0.25f
            )
        }
    }

    fun lookAt(entity: Entity, targetPos: BlockPos) {
        (entity as? Mob)?.lookControl?.setLookAt(
            targetPos.x + 0.5,
            targetPos.y + 0.5,
            targetPos.z + 0.5
        )
    }

    private fun playAttack(
        world: ServerLevel,
        entity: Entity,
        targetPos: BlockPos,
        animationCandidates: Set<String>,
        effect: AttackEffect
    ) {
        lookAt(entity, targetPos)
        if (!CobblemonWorkforceConfig.enableWorkAnimations) {
            return
        }
        sendAnimation(world, entity, animationCandidates)

        val actorPacket = effect.actorEffect?.let { effectId ->
            SpawnSnowstormEntityParticlePacket(
                effectId = effectId,
                sourceEntityId = entity.id,
                sourceLocators = listOf("root")
            )
        }
        val targetPacket = effect.targetEffect?.let { effectId ->
            SpawnSnowstormParticlePacket(
                effectId = effectId,
                position = Vec3(targetPos.x + 0.5, targetPos.y + effect.targetYOffset, targetPos.z + 0.5)
            )
        }

        if (actorPacket != null || targetPacket != null) {
            nearbyPlayers(world, targetPos).forEach { player ->
                if (actorPacket != null) player.sendPacket(actorPacket)
                if (targetPacket != null) player.sendPacket(targetPacket)
            }
        }
    }

    private fun List<AttackEffect>.randomPick(world: ServerLevel): AttackEffect {
        return this[world.random.nextInt(size)]
    }

    private fun sendAnimation(world: ServerLevel, entity: Entity, animationCandidates: Set<String>) {
        if (!CobblemonWorkforceConfig.enableWorkAnimations || entity !is PokemonEntity) return
        val packet = PlayPosableAnimationPacket(entity.id, animationCandidates, emptyList())
        nearbyPlayers(world, entity.blockPosition()).forEach { player ->
            player.sendPacket(packet)
        }
    }

    private fun nearbyPlayers(world: ServerLevel, center: BlockPos): List<ServerPlayer> {
        val area = AABB(center).inflate(48.0)
        return world.getEntitiesOfClass(ServerPlayer::class.java, area) { true }
    }
}
