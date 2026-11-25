# Changelog

## [1.1] - Cobblemon 1.8.2 (Minecraft 1.21.1 - Fabric & NeoForge)

### New Features
- **Fire Slot Upgrade**: New upgrade item that unlocks a Fire-type helper slot on the **Poke Miner**.
- **IV, EV & Happiness Performance Scaling**:
  - Every machine now scales with your Pokémon's **IVs, EVs, and Happiness**.
  - Unhappy Pokémon or Pokémon with very low stats work slower and may produce fewer drops.
  - Well-trained, happy Pokémon work much faster, yield bonus drops, and in the **Poke Crusher** have a chance to **triple (3x) or even quadruple (4x)** ore outputs!
- **Pokémon Work Animations & Effects**:
  - Working Pokémon now look at what they are doing and play type-themed attack animations and effects (Electric sparks, Water Gun/Bubble, Grass attacks, Rock impacts, Steel punches, Fighting combos, Fire flames, and Psychic/Fairy effects).
  - Added an option in `config/cobblemon_workforce.properties` (`enable_work_animations`) to turn work animations on or off.

### Machine Improvements
- **Poke Miner**:
  - Can now mine **Deepslate** and **Netherrack / Obsidian** (requires a Level 80+ Pokémon for Netherrack/Obsidian, with bonus drops of gold, quartz, glowstone, and obsidian).
  - Protects the 3x3 blocks directly beneath the miner so it never breaks its own support base.
  - Only mines exposed blocks with open access, avoiding buried underground blocks.
  - Assigning **Water + Fire** helpers together immediately regenerates mined blocks (Stone at Lv. 1+, Deepslate at Lv. 30+, Netherrack/Basalt at Lv. 60+, Obsidian at Lv. 80+), and the Water and Fire Pokémon stay near the block they are regenerating.
- **Poke Farm**:
  - No longer harvests or clears wild grass and flowers.
  - Water-type helpers now use Water Gun and Bubble animations while watering and boosting crops.
- **Poke Lumber**:
  - Fighting-type helpers chop trees with a multi-punch combo.
- **Poke Crusher**:
  - Can now crush ores directly from **adjacent chests**, in addition to items dropped on the ground.
- **Poke Furnace**:
  - Fire-type helpers stand in front of active furnaces and face them while speeding up smelting.
- **Block Descriptions**:
  - Added numbered English tooltips to all 8 machines explaining what each Pokémon type does.

### Bug Fixes
- **Fixed Pokémon Duplication**: Fixed an issue where closing and reopening a world save could duplicate Pokémon assigned to machines.
- **Sleeping Pokémon**: Improved sleep status feedback and machine pausing when a working Pokémon is asleep.
