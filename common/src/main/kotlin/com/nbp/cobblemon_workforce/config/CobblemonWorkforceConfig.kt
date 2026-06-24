package com.nbp.cobblemon_workforce.config

import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import org.slf4j.LoggerFactory

object CobblemonWorkforceConfig {
    private const val DEFAULT_MAX_OUTPUT_PER_SIDE = 1024
    private const val DEFAULT_GENERATOR_MAX_POKEMON = 6
    private const val ABSOLUTE_MAX_GENERATOR_POKEMON = 6
    private const val DEFAULT_SHOW_POKEMON_OUTSIDE = true
    private const val DEFAULT_POKEMON_WANDER_RANGE = 6
    private const val DEFAULT_IV_BONUS_PERCENT_PER_POINT = 1.0
    private const val DEFAULT_EV_BONUS_PERCENT_PER_4_POINTS = 1.0
    private const val DEFAULT_BREEDER_MAX_ANIMALS = 32

    private const val CONFIG_FILE_NAME = "cobblemon_workforce.properties"
    private const val MAX_OUTPUT_PER_SIDE_KEY = "max_output_per_side"
    private const val GENERATOR_MAX_POKEMON_KEY = "generator_max_pokemon"
    private const val SHOW_POKEMON_OUTSIDE_KEY = "show_pokemon_outside"
    private const val POKEMON_WANDER_RANGE_KEY = "pokemon_wander_range"
    private const val IV_BONUS_PERCENT_PER_POINT_KEY = "iv_bonus_percent_per_point"
    private const val EV_BONUS_PERCENT_PER_4_POINTS_KEY = "ev_bonus_percent_per_4_points"
    private const val BREEDER_MAX_ANIMALS_KEY = "breeder_max_animals"

    private val logger = LoggerFactory.getLogger("CobblemonWorkforceConfig")
    private val path: Path = Path.of("config", CONFIG_FILE_NAME)

    var maxOutputPerSide: Int = DEFAULT_MAX_OUTPUT_PER_SIDE
        private set

    var generatorMaxPokemon: Int = DEFAULT_GENERATOR_MAX_POKEMON
        private set

    var showPokemonOutside: Boolean = DEFAULT_SHOW_POKEMON_OUTSIDE
        private set

    var pokemonWanderRange: Int = DEFAULT_POKEMON_WANDER_RANGE
        private set

    var ivBonusPercentPerPoint: Double = DEFAULT_IV_BONUS_PERCENT_PER_POINT
        private set

    var evBonusPercentPer4Points: Double = DEFAULT_EV_BONUS_PERCENT_PER_4_POINTS
        private set

    var breederMaxAnimals: Int = DEFAULT_BREEDER_MAX_ANIMALS
        private set

    fun load() {
        ensureConfigExists()

        val properties = Properties()
        Files.newInputStream(path).use(properties::load)
        var changed = false

        maxOutputPerSide = properties.getProperty(MAX_OUTPUT_PER_SIDE_KEY)
            ?.toIntOrNull()
            ?.takeIf { it > 0 }
            ?: DEFAULT_MAX_OUTPUT_PER_SIDE.also {
                properties.setProperty(MAX_OUTPUT_PER_SIDE_KEY, it.toString())
                changed = true
                logger.warn("Invalid or missing $MAX_OUTPUT_PER_SIDE_KEY in $CONFIG_FILE_NAME; using $DEFAULT_MAX_OUTPUT_PER_SIDE.")
            }

        generatorMaxPokemon = properties.getProperty(GENERATOR_MAX_POKEMON_KEY)
            ?.toIntOrNull()
            ?.coerceIn(1, ABSOLUTE_MAX_GENERATOR_POKEMON)
            ?: DEFAULT_GENERATOR_MAX_POKEMON.also {
                properties.setProperty(GENERATOR_MAX_POKEMON_KEY, it.toString())
                changed = true
                logger.warn("Invalid or missing $GENERATOR_MAX_POKEMON_KEY in $CONFIG_FILE_NAME; using $DEFAULT_GENERATOR_MAX_POKEMON.")
            }

        showPokemonOutside = properties.getProperty(SHOW_POKEMON_OUTSIDE_KEY)
            ?.let { it.trim().lowercase() == "true" }
            ?: DEFAULT_SHOW_POKEMON_OUTSIDE.also {
                properties.setProperty(SHOW_POKEMON_OUTSIDE_KEY, it.toString())
                changed = true
            }

        pokemonWanderRange = properties.getProperty(POKEMON_WANDER_RANGE_KEY)
            ?.toIntOrNull()
            ?.coerceIn(1, 32)
            ?: DEFAULT_POKEMON_WANDER_RANGE.also {
                properties.setProperty(POKEMON_WANDER_RANGE_KEY, it.toString())
                changed = true
            }

        ivBonusPercentPerPoint = properties.getProperty(IV_BONUS_PERCENT_PER_POINT_KEY)
            ?.toDoubleOrNull()
            ?.takeIf { it >= 0.0 }
            ?: DEFAULT_IV_BONUS_PERCENT_PER_POINT.also {
                properties.setProperty(IV_BONUS_PERCENT_PER_POINT_KEY, it.toString())
                changed = true
            }

        evBonusPercentPer4Points = properties.getProperty(EV_BONUS_PERCENT_PER_4_POINTS_KEY)
            ?.toDoubleOrNull()
            ?.takeIf { it >= 0.0 }
            ?: DEFAULT_EV_BONUS_PERCENT_PER_4_POINTS.also {
                properties.setProperty(EV_BONUS_PERCENT_PER_4_POINTS_KEY, it.toString())
                changed = true
            }

        breederMaxAnimals = properties.getProperty(BREEDER_MAX_ANIMALS_KEY)
            ?.toIntOrNull()
            ?.coerceIn(2, 256)
            ?: DEFAULT_BREEDER_MAX_ANIMALS.also {
                properties.setProperty(BREEDER_MAX_ANIMALS_KEY, it.toString())
                changed = true
            }

        if (changed) {
            Files.newOutputStream(path).use {
                properties.store(it, "Cobblemon WorkForce config")
            }
        }
    }

    private fun ensureConfigExists() {
        Files.createDirectories(path.parent)
        if (Files.exists(path)) {
            return
        }

        Files.writeString(
            path,
            """
            # Cobblemon WorkForce config
            # Maximum FE/RF the Poke Power Generator can push to each side every tick.
            $MAX_OUTPUT_PER_SIDE_KEY=$DEFAULT_MAX_OUTPUT_PER_SIDE
            # Maximum Pokemon inside one Poke Power Generator. Valid range: 1-$ABSOLUTE_MAX_GENERATOR_POKEMON.
            $GENERATOR_MAX_POKEMON_KEY=$DEFAULT_GENERATOR_MAX_POKEMON
            # Whether to show the first Pokemon in the generator walking around outside the block.
            $SHOW_POKEMON_OUTSIDE_KEY=$DEFAULT_SHOW_POKEMON_OUTSIDE
            # How far the Pokemon can wander from the generator block (Manhattan distance).
            $POKEMON_WANDER_RANGE_KEY=$DEFAULT_POKEMON_WANDER_RANGE
            # Extra generation percent per IV point in the chosen Attack/Sp. Attack stat. 1.0 means 31 IV = +31%.
            $IV_BONUS_PERCENT_PER_POINT_KEY=$DEFAULT_IV_BONUS_PERCENT_PER_POINT
            # Extra generation percent per 4 EV points in the chosen Attack/Sp. Attack stat. 1.0 means 252 EV = +63%.
            $EV_BONUS_PERCENT_PER_4_POINTS_KEY=$DEFAULT_EV_BONUS_PERCENT_PER_4_POINTS
            # Maximum vanilla animals allowed in one Poke Breeder work area before it stops breeding more.
            $BREEDER_MAX_ANIMALS_KEY=$DEFAULT_BREEDER_MAX_ANIMALS
            
            """.trimIndent()
        )
    }
}
