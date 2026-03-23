# BuildCraft 1.12.2 → 1.21.11 Port Status

## Module Status Overview

| Module | Active | Disabled | Status |
|:---|:---:|:---:|:---|
| **API** | 92 | 0 | ✅ Complete |
| **Lib** | 79 | 59 | ⚠️ Core ported, legacy stubs remain |
| **Core** | 82 | 54 | ⚠️ Core ported, old statements/sprites disabled |
| **Transport** | 75 | 0 | ✅ Complete |
| **Silicon** | 65 | 0 | ✅ Complete |
| **Factory** | 50 | 0 | ✅ Complete |
| **Energy** | 23 | 0 | ✅ Complete |
| **Builders** | 82 | 44 | ⚠️ Blocks/tiles ported, snapshot system partial |
| **Robotics** | 12 | 0 | ⚠️ Only Zone Planner ported |

---

## ✅ Completed (since original TODO)

### Silicon Module
- [x] Gates — `GateLogic`, `PluggableGate`, gate variants, gate copier
- [x] Wires — `ItemWire`, wire system ported
- [x] Lenses — `ItemPluggableLens`, `PluggableLens`
- [x] Facades — Full rendering pipeline (in-world + item), dedup, assembly recipes
- [x] Laser + Assembly/Integration Tables — Beam rendering, recipe system, power sync
- [x] Advanced Crafting Table — Recipe book, JEI/REI integration
- [x] Pulsar pluggable

### Transport Module
- [x] All pipe types (item, fluid, power, structure, void, stripes)
- [x] All pipe behaviours (wood, iron, diamond, diamond wood, emzuli, etc.)
- [x] All pipe flow renderers (items, fluids, power, RF)
- [x] Pipe GUIs (diamond, diamond wood, emzuli, filtered buffer)
- [x] Pipe pluggable system (blockers, gates, facades, lenses, pulsars)
- [x] Pipe wire system
- [x] Pipe connection logic and engine face exclusion

### Energy Module
- [x] Stirling engine (block, tile, GUI, container)
- [x] Combustion engine (block, tile, GUI, container, fluid extraction)
- [x] 30 energy fluid variants (10 types × 3 temperatures)
- [x] Oil world generation (`OilGenerator`, `OilGenStructure`)
- [x] Oil spring tile entity

### Factory Module
- [x] Auto Workbench — Power model, progress bar, recipe book, JEI/REI, material slot coloring
- [x] Tank — Block, tile, GUI, renderer
- [x] Pump — Block, tile, renderer
- [x] Distiller — Block, tile, GUI, renderer (with interpolation fixes)
- [x] Heat Exchanger — Block, tile, renderer
- [x] Mining Well — Block, tile, renderer
- [x] Chute — Block, tile, GUI
- [x] Flood Gate — Block, tile
- [x] Water Gel — Block, item

### Builders Module
- [x] Quarry — Full tile entity (1099 lines), frame building, mining, drill, chunk loading
- [x] Quarry renderer + debug overlay
- [x] Filler — Block, tile, GUI, container, statement context, filler patterns
- [x] Filler Planner — Item, addon, renderer, container, GUI
- [x] Architect Table — Block, tile, GUI, container
- [x] Builder — Block, tile, GUI, container
- [x] Electronic Library — Block, tile, GUI, container
- [x] Replacer — Block, tile, GUI, container
- [x] Frame block
- [x] Schematic Single item
- [x] Snapshot system (partial — Blueprint, SnapshotBuilder, Template basics)

### Core Module
- [x] All items (wrench, goggles, paintbrush, guide, debugger, map location, list, marker connector, volume box, filler planner)
- [x] Markers (path + volume) with caching and saved data
- [x] Volume box addon system
- [x] Engine blocks (creative, redstone)
- [x] Spring blocks + Spring world gen feature
- [x] Decorated blocks + paint handlers
- [x] Statement system (triggers/actions framework)
- [x] Laser manager
- [x] Debug overlay
- [x] Guide book system (full GUI, XML loader, rendering)
- [x] Advancements — 21 advancement JSONs ported
- [x] Ledger system (help, power, ownership) with animation, scissor clipping, wrapping
- [x] Interactive help system (slot/tank highlighting)
- [x] REI/JEI integration (exclusion zones, container handlers)
- [x] Creative tabs with ordering

---

## 🔧 Still Needs Work

### Config System
- [ ] Port `FileConfigManager`, `RoamingConfigManager`, `StreamConfigManager` from lib (all `.disabled`)
- [ ] Replace hardcoded `BCCoreConfig` defaults with actual NeoForge config file I/O
- [ ] Port `BCEnergyConfig`, `BCTransportConfig`, `BCBuildersConfig` to use real config
- [ ] Power unit configuration (MJ ↔ RF/FE, full text vs abbreviated, per tick vs per second)

### Advancements
- [ ] Port custom advancement triggers (currently only display advancements, no gameplay triggers)
- [ ] `AdvancementUtil.java` exists but triggers are not wired up

### Block Sounds
- [ ] Most blocks use default `SoundType` — review and set appropriate sounds per block type
- [ ] Only `BlockSpring` explicitly sets `SoundType.STONE`

### Builders — Remaining Disabled Files (44)
- [ ] `RenderArchitectTable` / `RenderArchitectTables` / `ClientArchitectTables` — architect table in-world rendering
- [ ] `RenderBuilder` / `RenderSnapshotBuilder` — builder in-world rendering (ghost blocks)
- [ ] `HudSingleSchematic` — schematic HUD overlay
- [ ] `BlueprintBuilder` — needs re-port (disabled copy exists alongside active copy)
- [ ] `FakeWorld` / `FakeChunkProvider` — blueprint building world simulation
- [ ] `GlobalSavedDataSnapshots` — needs re-port (disabled copy exists alongside active copy)
- [ ] `ClientSnapshots` — snapshot preview rendering
- [ ] `MessageSnapshotRequest` / `MessageSnapshotResponse` — snapshot networking
- [ ] `BCBuildersSchematics` / `BCBuildersRegistries` — schematic registration system
- [ ] `BCBuildersActionProvider` — gate action provider integration

### Core — Remaining Disabled Files (54)
- [ ] Statement implementations (12 files): `TriggerInventory`, `TriggerPower`, `TriggerRedstoneInput`, `TriggerMachine`, `TriggerFluidContainer`, `TriggerFluidContainerLevel`, `TriggerInventoryLevel`, `TriggerEnginePowerStage`, `TriggerTrue`, `ActionMachineControl`, `ActionRedstoneOutput`, `CoreTriggerProvider`, `CoreActionProvider`
- [ ] Statement parameters (4 files): `StatementParamGateSideOnly`, `StatementParameterDirection`, `StatementParameterItemStackExact`, `StatementParameterRedstoneLevel`
- [ ] List GUI: `ContainerList`, `GuiList`, `ListTooltipHandler`
- [ ] Engine render stubs: `RenderEngineCreative`, `RenderEngineWood` (may already have replacements)
- [ ] Volume box networking: `MessageVolumeBoxes`
- [ ] Misc old stubs: `BCCoreProxy`, `BCCoreGuis`, `BlockPowerConsumerTester`

### Lib — Remaining Disabled Files (59)
- [ ] Config system (4 files): `FileConfigManager`, `OverridableConfigOption`, `RoamingConfigManager`, `StreamConfigManager`
- [ ] Fluid system (4 files): `Tank`, `TankManager`, `FuelRegistry`, `CoolantRegistry`
- [ ] GUI framework (8 files): `ContainerBCTile`, `ContainerBC_Neptune`, `ContainerPipe`, `GuiScreenBuildCraft`, `GuiElementSimple`, `GuiElementToolTip`, `GuiSpriteScaled`, `Widget_Neptune`
- [ ] GUI buttons (3 files): `GuiAbstractButton`, `GuiButtonDrawable`, `GuiImageButton`
- [ ] Block utilities (3 files): `BlockBCTile_Neptune`, `IBlockWithFacing`, `VanillaRotationHandlers`
- [ ] Cache system (3 files): `CachedChunk`, `NeighbourTileCache`, `TileCacheType`
- [ ] Rendering (5 files): `CuboidRenderer`, `DetachedRenderer`, `RenderMachineWave`, font renderers
- [ ] Sprite system (4 files): `AtlasSpriteSwappable`, `AtlasSpriteVariants`, `DynamicTextureBC`, `SpriteAtlas`
- [ ] Reload system (5 files): `IReloadable`, `LibConfigChangeListener`, `ReloadManager`, `ReloadSource`, `ReloadUtil`
- [ ] Misc: `CapabilityHelper`, `CompatManager`, `DataMetadataSection`, `BCAdvDebugging`

### Wooden Diamond Pipe GUI
- [ ] Replace text-based filter mode buttons with 1.12.2-style icon buttons
- [ ] Currently uses `Button.builder(Component.literal("White"), ...)` instead of proper icons

### Combustion Engine
- [ ] Ice coolant support (not found in `TileEngineIron_BC8`)

### Guidebook
- [ ] Finish content: add help entries for all blocks/items
- [ ] Currently functional (GUI, XML loader, rendering) but content is incomplete

### Help Ledger
- [ ] `LedgerHelp` class exists but needs per-block content filled in
- [ ] Interactive slot/tank highlighting is ported

### Robotics Module
- [ ] Only Zone Planner is ported (block, tile, GUI, container, zone system)
- [ ] No robots, robot AI, robot stations, or robot items ported
- [ ] **(Low priority — robots were not actively maintained in 1.12.2)**

---

## 🆕 New Features (Post-Port)

- [ ] Quarry LEDs
- [ ] Facade smooth shading
- [ ] Power unit configuration (MJ → RF/FE, full text vs abbreviated, per tick vs per second)
- [ ] Distiller and heat exchanger recipes in JEI/REI
- [ ] Advanced facade recipe handling in JEI/REI
- [ ] Quarry rig collisions *(collision boxes exist but need verification)*

---

## 🧹 Finalization

- [ ] Delete disabled `.java.disabled` files that have been fully superseded by active ports
- [ ] Clean up temp files (`Box_1_12.java.tmp`, `CapDumper.java`, `cache_test.txt`, `test.py`, `test_output.txt`, `lib_errors.txt`, `deps.txt`)
- [ ] Deprecation and warning fixes
- [ ] Fix texture filtering bug and crash
- [ ] Final code review across all modules
