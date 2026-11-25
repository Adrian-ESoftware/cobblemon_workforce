package com.nbp.cobblemon_workforce.pokemon

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.RandomSource
import java.util.UUID
import kotlin.math.roundToInt

object WorkforcePokemonStats {
    private val ALL_STATS = listOf(
        Stats.HP,
        Stats.ATTACK,
        Stats.DEFENCE,
        Stats.SPECIAL_ATTACK,
        Stats.SPECIAL_DEFENCE,
        Stats.SPEED
    )

    private const val MAX_TOTAL_IV = 186.0
    private const val MAX_TOTAL_EV = 510.0
    private const val MAX_FRIENDSHIP = 255.0

    data class PerformanceProfile(
        val ivRatio: Double,
        val evRatio: Double,
        val friendshipRatio: Double,
        val overallScore: Double,
        val speedMultiplier: Double,
        val yieldMultiplier: Double,
        val isUnhappy: Boolean,
        val isMaxed: Boolean
    ) {
        fun adjustedInterval(baseInterval: Int, minInterval: Int = 4, maxInterval: Int = 400): Int {
            return (baseInterval / speedMultiplier).roundToInt().coerceIn(minInterval, maxInterval)
        }

        fun rollBonusDropCount(random: RandomSource): Int {
            if (isUnhappy && random.nextDouble() < 0.25) {
                return -1
            }
            var bonus = 0
            if (overallScore >= 0.45) {
                val firstChance = ((overallScore - 0.45) / 0.55) * 0.45
                if (random.nextDouble() < firstChance) {
                    bonus += 1
                }
            }
            if (overallScore >= 0.85) {
                val secondChance = ((overallScore - 0.85) / 0.15) * 0.25
                if (random.nextDouble() < secondChance) {
                    bonus += 1
                }
            }
            return bonus
        }

        fun rollCrushFactor(random: RandomSource): Double {
            if ((isUnhappy || overallScore < 0.20) && random.nextDouble() < 0.25) {
                return 0.5
            }
            if (overallScore >= 0.75) {
                val quadChance = ((overallScore - 0.75) / 0.25).coerceIn(0.0, 1.0) * 0.50
                if (random.nextDouble() < quadChance) {
                    return 2.0 // 2x base -> 4x ore output!
                }
            }
            if (overallScore >= 0.45) {
                val tripleChance = ((overallScore - 0.45) / 0.55).coerceIn(0.0, 1.0) * 0.35
                if (random.nextDouble() < tripleChance) {
                    return 1.5 // 2x base -> 3x ore output!
                }
            }
            return 1.0
        }
    }

    val NEUTRAL_PROFILE = PerformanceProfile(
        ivRatio = 0.5,
        evRatio = 0.0,
        friendshipRatio = 70.0 / MAX_FRIENDSHIP,
        overallScore = 0.27,
        speedMultiplier = 1.0,
        yieldMultiplier = 1.0,
        isUnhappy = false,
        isMaxed = false
    )

    fun profileFor(world: ServerLevel, pcId: UUID, pokemonId: UUID): PerformanceProfile {
        val pokemon = Cobblemon.storage.getPC(pcId, world.registryAccess())[pokemonId] ?: return NEUTRAL_PROFILE
        return profileFor(pokemon)
    }

    fun profileFor(pokemon: Pokemon): PerformanceProfile {
        val totalIv = ALL_STATS.sumOf { pokemon.ivs.getOrDefault(it) }.toDouble()
        val totalEv = ALL_STATS.sumOf { pokemon.evs.getOrDefault(it) }.toDouble()
        val friendship = pokemon.friendship.toDouble().coerceIn(0.0, MAX_FRIENDSHIP)

        val ivRatio = (totalIv / MAX_TOTAL_IV).coerceIn(0.0, 1.0)
        val evRatio = (totalEv / MAX_TOTAL_EV).coerceIn(0.0, 1.0)
        val friendshipRatio = (friendship / MAX_FRIENDSHIP).coerceIn(0.0, 1.0)
        val overallScore = (0.40 * ivRatio + 0.35 * evRatio + 0.25 * friendshipRatio).coerceIn(0.0, 1.0)

        val isUnhappy = friendship < 40.0
        val happinessFactor = when {
            friendship < 40.0 -> -0.25
            friendship < 80.0 -> -0.10
            else -> ((friendship - 80.0) / (MAX_FRIENDSHIP - 80.0)) * 0.35
        }
        val combinedStats = 0.55 * ivRatio + 0.45 * evRatio
        val statFactor = (combinedStats - 0.25) * 0.65 // -0.1625 at 0, +0.4875 at max

        val speedMultiplier = (1.0 + happinessFactor + statFactor).coerceIn(0.60, 1.85)
        val yieldMultiplier = (1.0 + happinessFactor * 0.8 + statFactor * 0.9).coerceIn(0.65, 1.80)
        val isMaxed = ivRatio >= 0.98 && evRatio >= 0.98 && friendship >= 250.0

        return PerformanceProfile(
            ivRatio = ivRatio,
            evRatio = evRatio,
            friendshipRatio = friendshipRatio,
            overallScore = overallScore,
            speedMultiplier = speedMultiplier,
            yieldMultiplier = yieldMultiplier,
            isUnhappy = isUnhappy,
            isMaxed = isMaxed
        )
    }
}
