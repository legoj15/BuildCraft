# BuildCraft 1.12.2 → 26.1.x Port Status

Last audited: 2026-04-25.

## Module Status Overview

| Module | Active `.java` | `.java.disabled` | Status |
|:---|:---:|:---:|:---|
| **API** | 231 | 0 | ✅ Complete |
| **Lib** | 606 | 107 | ⚠️ Core ported; remaining disabled files are orphans (no active sibling) — review for delete-vs-reactivate per subfolder |
| **Core** | 88 | 15 | ✅ Mostly complete; 15 orphan disabled files remain (proxies, legacy renderers, debug tester) |
| **Transport** | 122 | 0 | ✅ Complete |
| **Silicon** | 75 | 0 | ✅ Complete |
| **Factory** | 51 | 0 | ✅ Complete |
| **Energy** | 31 | 0 | ✅ Complete |
| **Builders** | 127 | 7 | ✅ Rendering / HUD / snapshot networking all covered by `BCBuildersEventDist` + modern payloads; 7 orphan disabled files remain |
| **Robotics** | 12 | 0 | ⚠️ Only Zone Planner ported (low priority — robots were not actively maintained in 1.12.2) |

After the audit's two-phase cleanup (22 paired files in round 1, 89 paired files in round 2, 2 orphan FakeWorld files), all remaining `.java.disabled` files are true orphans — no active sibling exists for any of them. They represent functionality either replaced by NeoForge facilities or covered elsewhere in the active port.

Lib disabled-file breakdown (107 total): 27 gui, 16 client, 15 misc, 9 path, 8 inventory, 5 net, 4 config, 3 each {block, cache, list, particle}, 2 each {item, fluid, registry}, 1 each {cap, compat, recipe} + 2 top-level (`BCLibRegistries`, `BCLibSprites`).

---

## 🔧 Outstanding work

### Advancements — 18 orphaned triggers
The `minecraft:impossible` trigger is intentional in this codebase: it's the "don't double-grant from JSON" placeholder for advancements granted via `AdvancementUtil.unlockAdvancement(...)` in Java. 23 of 41 impossible-trigger advancements ARE wired up that way. The remaining 18 are truly orphaned — JSON exists, no Java grant:

- [ ] `all_plugged_up`
- [ ] `building_for_the_future`
- [ ] `colorful_electricial`
- [ ] `destroying_the_world`
- [ ] `diggy_diggy_hole`
- [ ] `flooding_the_world`
- [ ] `goggles`
- [ ] `heating_and_distilling`
- [ ] `lava_power`
- [ ] `list`
- [ ] `logic_transportation`
- [ ] `paper`
- [ ] `paving_the_way`
- [ ] `refine_and_redefine`
- [ ] `start_of_something_big`
- [ ] `sticky_dipping`
- [ ] `to_much_power`
- [ ] `too_many_pipe_filters`

### Block Sounds
- [ ] Most blocks use default `SoundType` — review and set appropriate sounds per block type (engines, pipes, facades, tanks, machines). Only `BlockSpring` (`SoundType.STONE`) and ~42 other explicit declarations exist; a deliberate sound pass is still needed.

### Config System
- [ ] Replace hardcoded `BCCoreConfig` defaults with NeoForge `ModConfigSpec` file I/O for power, energy, transport, builders sub-modules.
- [ ] **Power unit display configuration** — MJ ↔ RF/FE, full text vs abbreviated, per tick vs per second.
- [ ] **Fullbright fluid rendering in tanks** — config option to recreate the 1.12.2 "neon glow" look.
- [ ] **Heat-level color shifting** — config option to subtly brighten/warm fluid colors at higher heat levels.
- [ ] Decide fate of legacy config classes: `FileConfigManager`, `RoamingConfigManager`, `StreamConfigManager`, `OverridableConfigOption`. Either re-port or delete in favor of NeoForge's spec-based config — no dual system.

### Lib disabled-file triage
The 140 lib disabled files break down into roughly three categories. None of them are blocking the port; this is hygiene work:

- [ ] **Confirm-and-delete (likely fully replaced by NeoForge)**: cache (`CachedChunk`, `NeighbourTileCache`, `TileCacheType`), sprite (`AtlasSpriteSwappable`, `AtlasSpriteVariants`, `DynamicTextureBC`, `SpriteAtlas`), reload (`IReloadable`, `LibConfigChangeListener`, `ReloadManager`, `ReloadSource`, `ReloadUtil`), block utilities (`BlockBCTile_Neptune`, `IBlockWithFacing`, `VanillaRotationHandlers`).
- [ ] **Re-port candidates** — only if the active code lacks the functionality:
  - [ ] `Tank` / `TankManager` — verify whether NeoForge `FluidTank` + `IFluidHandler` + active `TankProperties`/`SingleUseTank` cover everything, then either delete or re-port.
- [ ] **Misc**: `CapabilityHelper`, `CompatManager`, `DataMetadataSection`, `BCAdvDebugging` (active version exists; `.disabled` is a duplicate).

### Core remaining disabled files (15 total — all orphans, no active siblings)
- [ ] `BCCoreProxy` — likely 🗑️ delete (proxy pattern obsolete in modern NeoForge `Dist.CLIENT` event handling).
- [ ] `BCCoreGuis` — likely 🗑️ delete (GUI registration moved to `MenuType` deferred registration).
- [ ] `BlockPowerConsumerTester` + `TilePowerConsumerTester` — decide: is this still useful as a debug fixture, or 🗑️ delete?
- [ ] `BlockEngine_BC8` + `ItemEngine_BC8` — verify whether modern engine blocks/items cover these. Likely 🗑️ delete.
- [ ] `BCConfigElement`, `ConfigGuiFactoryBC` — Forge in-game config UI shims. NeoForge handles this differently → likely 🗑️ delete.
- [ ] `RenderTickListener` — verify whether the active code uses `ClientTickEvent` or similar instead.
- [ ] `RenderEngineCreative`, `RenderEngineWood`, `RenderMarkerVolume`, `RenderVolumeBoxes` — check whether `BCBuildersEventDist`-style global renderers cover these in core, or whether they're genuinely missing.
- [ ] `SpringPopulate` — world-gen feature; check against modern feature/biome modifier wiring.
- [ ] `ItemBlockDecorated` — `BlockItem` subclass; verify against the active item registration.

### Builders remaining disabled files (7 total — all orphans)
- [ ] `BCBuildersProxy` — likely 🗑️ delete (proxy pattern obsolete).
- [ ] `RenderArchitectTable`, `RenderBuilder`, `RenderSnapshotBuilder`, `HudSingleSchematic` — replaced by `BCBuildersEventDist` and `SchematicSingleTooltipOverlay` (per audit). Likely 🗑️ delete.
- [ ] `MessageSnapshotRequest`, `MessageSnapshotResponse` — replaced by `SnapshotRequestPayload`/`SnapshotResponsePayload`. Likely 🗑️ delete.

### Guidebook
- [ ] Fill the last 2 stubs: `engine_basics.md` (1-line title only) and `registry_overview.md` (empty).

### Help Ledger — content coverage
- [ ] The `LedgerHelp` framework is active and interactive slot/tank highlighting works (confirmed in `ScreenEngineIron`, etc.). Per-block content is the gap: each block needs to register `ElementHelpInfo`s via `addHelpElements()`. Coverage is uneven; sweep needed.

### Robotics
- [ ] No robots, robot AI, robot stations, or robot items ported. **Low priority** — robots were not actively maintained in 1.12.2.

---

## 🆕 New Features (Post-Port)

- [ ] Quarry LEDs
- [ ] Facade smooth shading (AO infrastructure exists in `MutableQuad`; needs facade-specific wiring)
- [ ] Distiller and heat exchanger recipes in JEI/REI (currently only `BCFactoryJeiPlugin` covers Auto Workbench)
- [ ] Advanced facade recipe handling in JEI/REI (no `transport/compat/jei/` exists)
- [ ] Fix texture filtering bug and crash *(needs description / repro to be actionable)*

---

## 🧹 Finalization

### Other finalization
- [ ] Deprecation and warning fixes
- [ ] Final code review across all modules

---

## ✅ Done since last update — confirmed by audit, can be removed from this file

These were marked outstanding in earlier revisions of `todos.md` but have actually been completed and verified:

- **Core statement implementations (13)**: `TriggerInventory`, `TriggerPower`, `TriggerRedstoneInput`, `TriggerMachine`, `TriggerFluidContainer`, `TriggerFluidContainerLevel`, `TriggerInventoryLevel`, `TriggerEnginePowerStage`, `TriggerTrue`, `ActionMachineControl`, `ActionRedstoneOutput`, `CoreTriggerProvider`, `CoreActionProvider` — all active.
- **Statement parameters (4)**: `StatementParamGateSideOnly`, `StatementParameterDirection`, `StatementParameterItemStackExact`, `StatementParameterRedstoneLevel` — all active.
- **List GUI (3)**: `ContainerList`, `GuiList`, `ListTooltipHandler` — active.
- **Volume box networking**: `MessageVolumeBoxes` — active.
- **Lib fluid registries**: `FuelRegistry`, `CoolantRegistry` — active (`.disabled` siblings are in cleanup queue above).
- **Builders rendering / HUD / networking** — all covered by the active port. `BCBuildersEventDist.renderAllArchitectTables()` + `ClientArchitectScans` replace the architect renderers; `renderAllBuilders()` and `renderAllBuildersCustomGeometry()` replace `RenderBuilder`/`RenderSnapshotBuilder`; `SchematicSingleTooltipOverlay` replaces (and enhances) `HudSingleSchematic` with a 3D PiP preview; `SnapshotRequestPayload`/`SnapshotResponsePayload` modernize `MessageSnapshotRequest/Response`; `BCBuildersSchematics` and `BCBuildersActionProvider` are active.
- **Quarry rendering** — fully working in `BCBuildersEventDist.renderAllQuarries()` (frame beams, drill column, head interpolation, power-based break scaling, uninitialized box).
- **Quarry rig collisions** — `EntityQuarryRig` has full collision logic (AABB, makeBoundingBox, setRiggingBox, physics gating).
- **Silicon — Gate world models / BER**: NOT NEEDED. Gates render via baked models (`PlugGateBaker` + `KeyPlugGate` + `GateItemModel`). The active LED state is a baked model variant with `setLightEmission(15)`, not animated. This matches 1.12.2's pluggable-baked approach. No BER required.
- **Guidebook bulk content** — 160 pages across `block/`, `action/`, `pipe/`, `item/`, `trigger/`, `config/`, `placeholder/`. Only 2 stubs remaining (listed above).
- **Temp file cleanup** — `Box_1_12.java.tmp`, `CapDumper.java`, `cache_test.txt`, `test_output.txt`, `lib_errors.txt`, `deps.txt` are all gone. *(`tools/test.py` is a real classpath-builder utility, not a temp file.)*
- **Stale entry**: `BCBuildersRegistries` — file does not exist anywhere; was a phantom todo.
- **Bulk `.java.disabled` cleanup (20 files)** — removed during this audit. 12 had active siblings (`BlueprintBuilder`, `GlobalSavedDataSnapshots`, `ClientSnapshots`, `FuelRegistry`, `CoolantRegistry`, `ContainerBC_Neptune`, `ContainerBCTile`, `Widget_Neptune`, `Ledger_Neptune`, `LedgerHelp`, `WidgetFluidTank`, `BCAdvDebugging`); 8 were truly orphan with no active references (`GuiScreenBuildCraft`, `ContainerPipe`, `GuiElementToolTip`, `GuiSpriteScaled`, `GuiAbstractButton`, `GuiButtonDrawable`, `GuiImageButton`, `DummyHelpElement`). 1.12.2 logic remains accessible via the `8.0.x-1.12.2` branch.
- **`FakeWorld` / `FakeChunkProvider`** — deleted. They were a client-side rendering harness from 1.12.2 (a stub `World` to feed vanilla renderers when previewing snapshots), not a server-side validation layer. Modern preview rendering uses NeoForge's PiP pipeline (`BlueprintRenderer` → `BlueprintPipRenderState` → `GuiGraphicsExtractor.submitPictureInPictureRenderState`), which iterates the snapshot palette directly via the block render dispatcher with no `Level` instance needed. The active port also has zero references to them anywhere — they were already orphan code. They never overlapped with `SnapshotBuilder.checkResults[]` (which is server-side build-task generation, identical pattern in both eras).
- **Phase 2 paired-`.disabled` sweep (89 files)** — every other `.java.disabled` file with an active sibling was deleted (blocks, tiles, containers, GUIs, statements, markers, item classes, lib utilities, etc.). The active versions are the source of truth; the `.disabled` siblings were dead-code carryover from porting. Build still clean. Counts dropped: builders 43→7, core 52→15, lib 123→107.
