# Changelog

## [1.1] - Cobblemon 1.8.2+1.21.1

### Compatibility & Core Architecture
- Updated mod version to `1.1` for **Minecraft 1.21.1** (Fabric & NeoForge) and **Cobblemon 1.8.2+1.21.1**.
- Unified the 8 separate pasture bridge classes into `WorkforcePastureBridge` and `WorkforcePastureHost`, removing redundant per-machine bridge code and updating pasture mixins accordingly.
- Cleaned up `CobblemonWorkforce.kt`.

### New Features
- **Fire Slot Upgrade (`cobblemon_workforce:fire_slot_upgrade`)**:
  - Unlocks the Fire-type helper slot on the **Poke Miner**.
- **IV, EV & Happiness Performance Rebalance (`WorkforcePokemonStats`)**:
  - All 8 machines now scale dynamically with each Pokémon's live IVs (`0–186`), EVs (`0–510`), and Happiness/Friendship (`0–255`).
  - Unhappy (`< 40` friendship) or low-stat Pokémon receive speed/yield penalties (down to `0.60x` speed / `0.65x` yield).
  - High/maxed IVs, EVs, and 255 Happiness grant up to `1.85x` speed, `1.80x` yield, bonus drops, and up to a `50%` chance to quadruple (`4x`) ore output (plus `35%` chance to `3x`) in the **Poke Crusher**.
- **Cobblemon Attack Animations & Effects (`WorkforcePokemonEffects`)**:
  - Assigned Pokémon now look at their target and play type-appropriate Cobblemon animations and Snowstorm particle effects (Electric, Water Gun/Bubble, Grass Leafage/Leaf Storm, Rock Bulldoze/Stomp, Steel Bullet Punch, Fighting Close Combat punch combos, Fire Flamethrower/Ember, Psychic/Fairy).
  - Added `enable_work_animations=true` in `config/cobblemon_workforce.properties` so work animations and particles can be toggled off.

### Machine Improvements
- **Poke Miner**:
  - Now mines deepslate and netherrack/obsidian (Lv. 80+ for netherrack/obsidian, dropping gold, quartz, glowstone, and obsidian).
  - Protects the 3x3 base support blocks directly beneath the miner from being broken.
  - Only mines blocks with at least one exposed face (`hasExposedFace`), preventing underground/inaccessible mining.
  - Water + Fire helpers regenerate mined blocks immediately after breaking (level-gated: Stone Lv. 1+, Deepslate Lv. 30+, Netherrack/Basalt Lv. 60+, Obsidian Lv. 80+) and stay positioned near/looking at the block being regenerated (`keepHelperNearTarget`).
- **Poke Farm**:
  - Excludes wild grass and flowers so decorative/yard plants are left intact.
  - Water-type helpers use Water Gun / Bubble animations when hydrating and boosting crop growth.
- **Poke Lumber**:
  - Fighting-type helpers fell trees using a multi-punch flurry animation.
- **Poke Crusher**:
  - Now also crushes ores/items stored inside adjacent chests, in addition to dropped items.
- **Poke Furnace**:
  - Fire-type helpers walk ~3 blocks in front of active adjacent furnaces and face them while smelting.
- **Tooltips & Localization**:
  - Added numbered English tooltips (`workTooltipLines()`) on all 8 machine blocks explaining what each Pokémon type does.

### Bug Fixes
- **Fixed Pokémon Duplication on Save Reload**:
  - Added `WorkforceEntitySaveMixin` (`shouldBeSaved() = false` for entities tagged `"cobblemon_workforce_tethered"`) so tethered workforce Pokémon are not persisted into chunk entity files.
  - Centralized entity spawning and deduplication in `WorkforceEntityHelper`, scanning `world.allEntities` by `pokemonId`/`tethering` to discard any existing duplicates and waiting `40` ticks after block entity load before spawning a new entity.
- **Sleep Status Handling**:
  - Improved sleep status feedback and machine pausing in `PokemonWorkforceStatus`.
