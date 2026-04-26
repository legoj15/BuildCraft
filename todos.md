# BuildCraft 1.12.2 → 26.1.x Port Status

Last audited: 2026-04-25.

## Module Status Overview

| Module | Active `.java` | `.java.disabled` | Status |
|:---|:---:|:---:|:---|
| **API** | 231 | 0 | ✅ Complete |
| **Lib** | 606 | 0 | ✅ Complete |
| **Core** | 88 | 0 | ✅ Complete |
| **Transport** | 122 | 0 | ✅ Complete |
| **Silicon** | 75 | 0 | ✅ Complete |
| **Factory** | 51 | 0 | ✅ Complete |
| **Energy** | 31 | 0 | ✅ Complete |
| **Builders** | 127 | 0 | ✅ Complete |
| **Robotics** | 12 | 0 | ⚠️ Only Zone Planner ported (low priority — robots were not actively maintained in 1.12.2) |

**Zero `.java.disabled` files remain anywhere in the project.** The audit deleted 240 total: 113 paired files (where an active version already existed) and 127 orphans (where modern NeoForge facilities or the active port covered the responsibility). 1.12.2 logic remains accessible via the `8.0.x-1.12.2` branch if reference is ever needed.

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
- **Core module fully cleared (15 orphan files)** — investigated each against active code, confirmed all replaced:
  - `BCCoreGuis` (enum-based GUI ID dispatch) → `BCCoreMenuTypes` (deferred `MenuType` registration).
  - `BCCoreProxy` (1.12.2 SidedProxy with IGuiHandler) → `BCCoreClient.init()` directly registers BERs, render-stage listeners, config screen factory, menu screens, tooltips on the mod and game event buses.
  - `BlockEngine_BC8` + `ItemEngine_BC8` (meta-variant engine block/item) → individual engine entries (e.g. `BCCoreBlockEntities.ENGINE_REDSTONE`, `ENGINE_CREATIVE`) per NeoForge convention.
  - `BlockPowerConsumerTester` + `TilePowerConsumerTester` (1.12.2 dev fixture) → no replacement; was a debug-only fixture, can be re-added cleanly with modern patterns if ever needed.
  - `BCConfigElement` + `ConfigGuiFactoryBC` (Forge `IModGuiFactory` config UI) → NeoForge's auto-generated `ConfigurationScreen` registered via `IConfigScreenFactory` (BCCoreClient lines 9–10).
  - `RenderEngineCreative` / `RenderEngineWood` (TESRs) → `BCCoreClient` lines 28–35 register `RenderEngine_BC8` BERs with the appropriate `BCCoreModels::getCreativeEngineQuads` / `getWoodEngineQuads` quad suppliers.
  - `RenderMarkerVolume` (TESR for marker signal lasers) → `MarkerRenderer.onRenderLevelStage` (lib/client/render) registered to `RenderLevelStageEvent.AfterTranslucentBlocks` via `BCCoreClient` line 14.
  - `RenderVolumeBoxes` (DetachedRenderer for volume box laser frames) → `VolumeBoxRenderer::renderAll` wired into `MarkerRenderer.setVolumeBoxRenderCallback` (BCCoreClient line 17).
  - `RenderTickListener` (debug overlay + held-item map-location/marker-connector lasers) → split into: `MarkerRenderer.setHoldingConnectorCheck` for the connector-preview lasers (BCCoreClient line 21–24), `DebugOverlayHelper.onClientTick` + `DebugOverlayRenderer::render` for the F3 debug overlay (BCCoreClient lines 38, 51–58).
  - `SpringPopulate` (Forge `PopulateChunkEvent.Post` handler) → data-driven world-gen: `SpringFeature.java` + `worldgen/configured_feature/water_spring.json` + `worldgen/placed_feature/water_spring.json` + `neoforge/biome_modifier/add_water_spring.json`.
  - `ItemBlockDecorated` (meta-variant ItemBlock for decorated stone) → individual decorated-block items per NeoForge convention.
  - **Core disabled count: 0**.
- **Builders module fully cleared (7 orphan files)** — investigated each against active code, confirmed all replaced:
  - `BCBuildersProxy` (1.12.2 SidedProxy: GUI dispatch, TESR registration, network handler binding, stencil setup) → replaced by `BCBuildersClient` + `BCBuildersMenuTypes` + `RegisterPayloadHandlersEvent` + `BCUnifiedConfig`.
  - `HudSingleSchematic` (empty 26-line stub even in 1.12.2) → replaced by `SchematicSingleTooltipOverlay` (3D PiP preview).
  - `RenderArchitectTable` (49-line TESR drawing laser-box outline) → replaced by `BCBuildersEventDist.renderAllArchitectTables()` (line 284) + `ClientArchitectScans` digitizing cubes.
  - `RenderBuilder` (82-line FastTESR) → replaced by `BCBuildersEventDist.renderAllBuilders()` (line 371).
  - `RenderSnapshotBuilder` (115-line helper rendering place-task throws, robot cube, break lasers) → replaced by `renderAllBuildersCustomGeometry()` + `renderPlaceTasks()` (lines 489, 521).
  - `MessageSnapshotRequest` / `MessageSnapshotResponse` (Forge `IMessage`) → replaced by `SnapshotRequestPayload` / `SnapshotResponsePayload` (NeoForge `CustomPacketPayload` + `StreamCodec`).
  - **Builders disabled count: 0**.
- **Lib module fully cleared (107 orphan files)** — done in two phases:
  - **Phase 1 (82 files, high-confidence delete)**: subsystems entirely subsumed by NeoForge or replaced by per-module deferred registration — `gui/` (27, the JSON-driven element framework; the active mod uses vanilla `Screen` + `GuiBC8` directly), `client/` (16: 5 reload, 6 render incl. 3 fonts, 1 resource, 4 sprite — all replaced by `ReloadableResourceManager` + modern font + `TextureAtlas` API), `path/` (9, robotics pathfinding — robots not ported), `inventory/` (8, wrappers + filters; replaced by `IItemHandler` + tags instead of `OreDictionary`), `net/` (5, old `IMessage` + 1.12.2 stack-cache trick → `CustomPacketPayload` + `StreamCodec` + `RegistryFriendlyByteBuf`), `config/` (4, old Forge `Configuration` API → `ModConfigSpec`), `cache/` (3, manual chunk cache → vanilla `LevelChunk`), `block/` (3, old base classes → composed `Block.Properties`), `recipe/` (1, hard-coded class → JSON `RecipeSerializer`), `compat/` (1, mod-loaded check → `ModList.get().isLoaded`), `cap/` (1, Forge capability framework → NeoForge `Capabilities` API), `item/` (2, marker interface → `Item.Properties`), top-level (2, init holders → per-module deferred registries).
  - **Phase 2 (25 files, verified-then-deleted)**: per-class grep confirmed zero active references for `misc/` (15: utilities + data classes; superseded by Java/NeoForge equivalents like `RenderSystem`, modern `Profiler`, deferred registry patterns), `particle/` (3, old particle system → vanilla `ParticleType`), `list/` (3, two `OreDictionary`-based handlers gone in 1.13+ plus `ListMatchHandlerFluid` whose role is covered by the active List GUI), `registry/` (2: `CreativeTabManager` → `CreativeModeTab` deferred registration; `PluggableRegistry` superseded by an active class of the same name in `transport/pipe/`), `fluid/` (2: `Tank` / `TankManager` → NeoForge `FluidTank` + `TankProperties`/`SingleUseTank`).
  - **The two `StringUtilBC` and `ModUtil` "references"** that turned up in the grep were comments noting the legacy methods were not ported and a simpler alternative is used; no actual call sites.
  - **Lib disabled count: 0**. Compile clean throughout.
