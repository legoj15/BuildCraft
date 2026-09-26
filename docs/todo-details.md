# Todo details

Agent-facing background for the one-line bullets in [todos.md](../todos.md). Each section is linked from its bullet. **When a bullet ships, delete its section here in the same change.** Sections mirror the todos order.

## Translation follow-ups

Follow-ups from the 2026-07-21 lang sweep (which moved ~20 hardcoded player-facing strings onto lang keys). These were found in passing and left alone. All are "English leaks through no matter how complete a translation is":

- **Colour-before-noun concatenation.** `ItemPipeHolder.java:82-83` builds coloured pipe names as `literal("").append(colourName).append(" ").append(baseName)`, hardcoding English word order. Same pattern in `ItemPaintbrush_BC8.java:106`. `ItemWire.java:31` already does it correctly via the `item.buildcraftunofficial.wire` = "%s Pipe Wire" format key — copy that. (The identical bug in `ItemPluggableLens` was fixed via `item.buildcraftunofficial.plug_lens.name` = "%s %s".)
- **Three more hardcoded "Unknown" owner fallbacks**, upstream of the `gui.owner.unknown` fix in `LedgerOwnership`: `OwnerData.java:51`, `TilePipeHolder.java:162`, `TileSpringOil.java:64` bake it in at the NBT layer, so they reach the Owner ledger through `GameProfileUtil.getName` when a profile exists but its name never persisted.
- **`item.snapshot.date` renders an English date.** The value comes from `java.util.Date.toString()` ("Mon Jul 21 12:00:00 EDT 2026") regardless of locale, so a translated "日期: %s" is still followed by English weekday/month names. Needs a locale-aware `DateTimeFormatter` in `Snapshot.Header` or the tooltip.
- **`ScreenDynamoMJ.java:49-50`** builds its upgrade `ElementHelpInfo` from bare literals in exactly the shape that was broken on the FE engine. Not a live bug (the dynamo's upgrade text is unit-neutral, so no `.rf` sibling exists), but it will silently swallow one if a translator ever authors it. Apply the same `Language.has`-guarded `rfFeKey` helper.
- **`useRfNaming` is a CLIENT config resolved server-side.** `TileEngineFE.getDisplayName():253` and `BCEnergyItems:30` call `rfFeKey` on the server, where the ConfigValue is null and it silently returns the base key — so on a dedicated server the engine's block/item NAME stays "FE" even with the toggle on, while client-resolved GUI help and gate actions correctly say "RF". Fix means moving the toggle to COMMON+synced, or making the display name client-resolved. Design call.
- **Four legacy `tile.*` keys are still live and duplicate a modern key.** `tile.autoWorkbenchBlock.name`, `tile.engineStone.name`, `tile.engineIron.name` and `tile.buildcraftunofficial.library.name` are referenced in code as GUI titles while `block.buildcraftunofficial.{autoworkbench_item,engine_stone,engine_iron,library}` carry the identical English. Two keys, one string, so a translator can make them disagree. Point the GUI titles at the `block.*` keys and delete the legacy four.
- **Bucket-scale flow and tank readouts disagree on trailing zeros.** With abbreviation on, `formatFluidFlow(100, PER_SECOND, …)` renders `2.0 B/s` while `formatFluidTank(2000, 4000, …)` renders `2 / 4 B` — the tank path trims a trailing `.0`, the flow path keeps it. Surfaced 2026-07-21 by the new `LocaleUtilUnitKeyTester`, which is the first test to cover the abbreviated bucket-scale flow path at all. Possibly deliberate (a rate arguably wants the precision digit), so it is characterized, not "fixed": decide which way is right and make both agree.
- **`/bcsoundtest` output is hardcoded English** (`SoundTestCommand`). Deliberately skipped: it is hard-gated behind `BCLib.DEV` (`-Dbuildcraft.dev=true`) so no shipped client can reach it, and its ASCII menu relies on `padRight` over a fixed 32-char width that any non-Latin translation would break. Only worth doing if the command ever ships ungated.
- **`zh_cn.json` lacks the robotics names**: the robot item, docking station, robot entity and every `buildcraft.boardRobot*` name/description (found 2026-09-26).

## Heat-tiered fuel and Nether oil

Make a fuel's *heat tier* change its value in the Combustion Engine, on a deliberately **non-monotonic** curve: hot (`heat_1`) **better** than cool (`heat_0`), searing (`heat_2`) **worse** than cool. Paired with Nether oil spawns, this is the intended route to a **Nether start** for the mod. Design intent recovered 2026-07-21 from an orphaned legacy lang string (`gui.buildcraft.combustion_engine.help`, "Higher temperature fuels produce more power" — wording predates the final non-monotonic design and should NOT be reused verbatim); the string was deleted, so this document is now the only record.

- **Today heated fuel isn't weaker — it's rejected.** Every `addFuel`/`addDirtyFuel` call in [BCEnergyRecipes.java:75-87](../src/main/java/buildcraft/energy/BCEnergyRecipes.java) resolves through `findFluid(baseName)` → `findFluidByHeat(baseName, 0)`, so **only heat-0 variants are ever registered**, and `TileEngineIron_BC8` gates acceptance on `BuildcraftFuelRegistry.fuel.getFuel(fluid) != null`. So this is "register the heat_1/heat_2 variants with their own multipliers", not "re-tune existing numbers". Current heat-0 multipliers track *fraction weight*, not temperature (gaseous 8, light 6, dense 4).
- **Blocking constraint — the hot tier of the best fuels is a GAS.** `gaseous = density < 0` where `density = baseDensity × (heat >= boilPoint ? −1 : 1)` ([BCEnergyFluids.java:196-198](../src/main/java/buildcraft/energy/BCEnergyFluids.java)), and boil points from the table at BCEnergyFluids.java:86 are: crude/residue/heavy/dense oil = 3 (**never** gaseous), distilled oil + dense fuel + mixed-heavy = 2 (gaseous only at searing), light fuel + mixed-light = 1 (**gaseous already at `heat_1`**), gas fuel = 0 (**gaseous at every tier**). So "hot light fuel is the good stuff" means the reward tier floats upward in pipes and needs gas-capable handling, while "searing is worse" lands on fluids that are *also* gaseous — the penalty and the gas transition are confounded for exactly the fuels players will optimise toward. Decide up front whether the curve is keyed on heat tier or on phase, and whether the 4 always-liquid oils get a different curve from the fuels.
- Open questions: Nether oil generation (biome/feature wiring — reuse the existing `OilFeature`/`AddOilBiomeModifier` path); whether searing fuel is merely inefficient or actively harmful (residue? overheat?); whether the Heat Exchanger becomes mandatory infrastructure for the good tier, and if so whether that's too steep for a Nether start.

**Nether oil spawns** (the worldgen half): generate oil in the Nether (lakes/spouts) for Nether-start challenge runs. Nether oil always spawns *searing* (heat 2), never cool — the inverse of Overworld oil. The `heating_and_distilling` advancement already accounts for this: its Nether branch treats searing as the natural (non-qualifying) heat, so the advancement still demands Heat-Exchanger work there. See `TileDistiller_BC8.qualifiesForHeatingAdvancement`.

Design input: upstream 7.1.x `bfa69a2ef` (#3497) made flammable BuildCraft fluids explode in the Nether and skipped oil worldgen there; 8.0.x and the port dropped it. Decide explicitly whether any Nether flammability rule comes back.

## Pump on top of miners

Both miners stop when they hit blocking fluids today (Mining Well and Quarry both halt on lava/oil; both drill past low-viscosity water to the solids below). Adding a Pump on top of either should consume fluid from the same blocking column, draining it into a buffer / adjacent tank or pipe so the miner can resume. Power for the on-top Pump comes out of the host miner's internal MJ battery rather than requiring a separate engine hookup. Mining Well top texture should swap to the open Flood Gate sprite when a Pump is mounted. Open: shared-battery priority/throttling, where the drained fluid goes, whether the Quarry's larger column needs a different sweep pattern than the Well's single-block-below path.

## Reclaimable marker region

Survival UX, optional. Let connecting markers spawn an *editable* survival region: drag a corner with the marker connector, and on apply (exit edit mode) — if the box no longer matches the markers — the markers break (as if a machine adopted them) and the box floats as a one-shot, consumable-until-a-machine-takes-it region, vanishing with that machine. Stays distinct from the creative item box (reusable) via a `source`/`adopted` flag; both removable via the connector. NOTE: this is an *additive feature* that increases the volume-box lifecycle complexity — keep it out of any marker/volume plumbing consolidation and evaluate it on its own merits. Today's survival lifecycle (markers consumed on machine adoption) already works without it.

## Zone Planner far map data

The planner's survey zoom (0.125 px/block, ~1,700 blocks across) renders only client-loaded chunks — past the render distance the map is empty. 1.7.10's planner sampled its map server-side and kept a persistent 2048×2048-block region centred on the planner (`TileZonePlan.RESOLUTION`, filled by `buildcraft.robotics.map.MapWorld` from chunk events), so explored terrain stayed visible however far out you went. Port shape: a menu payload asking the server tile for a sampled height/colour grid of the current viewport (one sample per LOD pixel) from a server-side cache built the same way, rendered wherever the client's own chunks run out. Watch packet size (7.1.x chunked `receiveImage` at 30 KB) and never let a sample sync-load a chunk.

## Heat exchanger visual overhaul

The cap models (paper-thin 12×12 planes covering the unconnected sides of a middle segment, visible only on single-piece or end-of-chain assemblies) were consolidated from two mirror-image files into one shared `heat_exchange_cap` rotated via blockstate (2026-05-19). The current placed-block look is functional but visually thin compared to the inventory icon ([models/item/heat_exchange.json](../src/main/resources/assets/buildcraftunofficial/models/item/heat_exchange.json) — references `sprite_b`, which the consolidated cap still uses): open ends look hollow, segments don't read as a coherent industrial pipe assembly. Redesign the exchanger's in-world look: better end-cap geometry that's actually visible (the current paper-thin plane is the *only* thing using `sprite_b` in-world), and decide whether the item icon should keep its current shape or match the new in-world look. The `heat_exchanger_cap.bbmodel` master that was moved to `misc/model_masters/` may or may not be a useful starting point (its `r`-suffixed name doesn't match the active `heat_exchange_*` family, and nothing on disk suggests it was ever wired up).

## Plug model unification

Unify plug in-world geometry under vanilla `models/block/plug_*.json`. Today the plug rendering pipeline has four flavours: (a) blocker / power_adapter load BC-dialect JSON from `models/plugs/` via `ModelHolderStatic`, (b) timer / light_sensor / pulsar-base bake from hardcoded UVs in [PlugBakerSimpleItems.java:70-94](../src/main/java/buildcraft/silicon/client/model/plug/PlugBakerSimpleItems.java), (c) lens / facade / gate are genuinely parametric and stay in Java, and (d) the robot station builds its pedestal in [RobotStationModel](../src/main/java/buildcraft/robotics/client/model/RobotStationModel.java) — Java, but NOT parametric; it lives in code because the baked pedestal and the per-frame reserved/linked overlay must share one geometry source (one asymmetric texel would make any mismatch visible), so migrating it to JSON means feeding the dynamic renderer from the same parsed model, not just moving the numbers. Migrate (a) and (b) to standard vanilla block-model JSONs loaded through the normal resource manager, with one generic rotating baker that pulls BakedQuads off a face and rotates them per `KeyPlug*.side`; fold (d) in only with the shared-source constraint preserved. Wins: resourcepacks can reshape *and* retexture any static plug with stock Minecraft JSON, the `models/plugs/` directory and `ModelHolderStatic` go away (verify nothing else uses the latter first), and surviving Java bakers become clearly-exceptional parametric cases. Tradeoff: non-trivial refactor across transport + silicon; per-element `shade` / extended UV tricks in the BC dialect need a NeoForge model-extension equivalent or to be dropped.

## REI recompile

Re-compile-verify `compat/rei` when a 26.1-compatible REI ships. The whole REI tree is excluded from compilation (`build.gradle.kts` unconditional exclude; dependency commented out — "no compatible versions for MC 26.1 yet"), so the 2026-07 `ReiCraftingTableSupport` dedup is best-effort-unverified, and the old plugins had already rotted while frozen (called non-existent `getLeftPos`/`getTopPos`; the rewrite now reads the window rect via `GuiBC8#windowLeft()/windowTop()`, never either vanilla getter family). Expect `registerClickArea`/`DraggableStackVisitor`/`DraggingContext` API drift too.

## Pipe atlas split (blocked)

Split pipe textures onto a dedicated `buildcraftunofficial:pipes` atlas. Would let the 400 `dye_replace`-generated dyed fluid-pipe variants (plus the 25 base fluid pipe sprites and the rest of `textures/pipes/`) live on their own page instead of swelling the vanilla blocks atlas.

Runtime logs from a 2026-05-25 `runClient` show the blocks atlas stitched at **8192×8192** — bigger than the 4096×2048 snapshot the 2026-05-19 GUI-source removal had achieved; pinning down whether that growth is from vanilla 26.1 baseline additions, the BC fluid sprites registered through `BCEnergyFluidsClient` since the GUI fix, or the dyed-pipe variants alone wants a `--debug` atlas dump before claiming the cleanup is "pure" rather than a real low-spec-GPU concern again. 8192² is still within every modern GPU's MAX_TEXTURE_SIZE — vanilla itself ships 8192-sided atlases on 1.21+ — but the headroom is meaningfully smaller than the post-fix snapshot implied.

**Blocked by three independent vanilla-level constraints** (all empirically verified 2026-05-15 via a one-pipe-family spike):

- **`BakedQuad.MaterialInfo.of()` hardcodes a binary atlas check.** Item render type is derived via `if (sprite.atlasLocation().equals(LOCATION_BLOCKS)) Sheets.cutoutBlockItemSheet() else Sheets.cutoutItemSheet()`. A sprite on any third atlas falls into the `else` branch and binds the items atlas — there's no third-atlas case in the factory.
- **`ChunkSectionLayer` is a closed enum.** Three values (SOLID, CUTOUT, TRANSLUCENT), each hardwired to a vanilla `RenderPipeline` that binds the blocks atlas to Sampler0. No NeoForge `RegisterChunkSectionLayer` event; `AddSectionGeometryEvent` lets a mod append geometry but still constrains it to one of the three existing slots.
- **AtlasManager rejects cross-atlas duplicate sprites.** Declaring a sprite on both atlases produces a vanilla warning per duplicate ("Duplicate sprite … will be rejected in a future version") — the "register on both" workaround is itself on a Mojang-enforced deprecation timer.

Spike result: pipe rendered with panorama backdrop and GUI icons bleeding through quad faces — the chunk renderer ignored `sprite.atlasLocation()` and sampled whatever was bound to Sampler0.

**Reopen trigger:** Mojang exposes an extensibility hook for chunk render layers, per-quad atlas binding, or otherwise opens the third-atlas case.

**Cheap escape hatch if a low-spec-GPU compat report comes in:** ~1 hour revert of commit `5a6cdb5ac` — remove the 25 `dye_replace` entries from `assets/minecraft/atlases/blocks.json`, flip `PipeBaseModelGenStandard.ensureDyedSprites` to return null, restore the three fallback branches. Painted fluid pipes drop from 1-layer dyed-sprite rendering to 2-layer base+mask-overlay; atlas shrinks back to ~1024×1024.

## Machine protection gaps

Stripes pipes, robot breaking/harvesting and the probe-pings-lasers issue were fixed 2026-09-26 (`BreakEventCompat.isProbe`, `buildcraft.robotics.RobotProtection`). Still open:
- **Pumps** (`TilePump`, `AIRobotPumpBlock`) drain fluids inside claims without asking; 1.12.2 didn't ask either, so it's a design call — probably gate draining behind the same `canMachineBreak` probe as the owner / station owner.
- **Robot placing and tool use** (`AIRobotPlant`, `AIRobotUseToolOnBlock`) still run as the generic `[BuildCraft]` fake player. Switch to `FakePlayerUtil.lease(level, RobotProtection.ownerOf(robot), null)` so claim mods judge the station owner and advancements route to them.

## Fake player cache hardening

The reset gaps, the fetch/lease split and leaked-lease detection were done 2026-09-26. One item left: a modded `BucketPickup` that reports a source but refuses `pickupBlock` makes `TilePump` re-queue that cell on every 30-tick rebuild. No dupe and no free drain (the refusal resets progress); each pass just wastes one 10 MJ attempt and the tube extension. Cheap fix: in `TilePump`, keep a transient map of refused cell → `BlockState` and skip the cell in `buildQueue`/`buildQueue0` while its state is unchanged. Needs a test-only registered block (no vanilla `BucketPickup` refuses a source). Same for `AIRobotPumpBlock`.

## Modded fluid textures

`FluidUtilBC.getFluidTexture` (~line 71) guesses `<namespace>:block/<fluid-type-path>_still` for every fluid that isn't BuildCraft's, water or lava instead of asking the fluid. Below 26.1 the real answer is `IClientFluidTypeExtensions.of(fluid).getStillTexture(stack)`; on 26.x it's the `FluidModel` from `getModelManager().getFluidStateModelSet().get(state)`, which `getFluidColor` beside it already uses for tint. Any modded fluid with a differently-named texture (Create's, for example) likely draws as missing texture in tanks, `GuiElementFluidTank`, `GuiFluid`, Distiller, Heat Exchanger, Iron Engine screen, blueprint views and the fluid-shard tint. Unconfirmed — check in-client with a real mod fluid first.

## Snapshot name collision

`Snapshot.computeKey` hashes the content WITHOUT the header, and files are named `<hash>.bcnbt`. Saving the same structure twice under different names (or owners) silently keeps only the first file (`addSnapshot` skips existing files); a lookup for the second key then fails `readSnapshot`'s full-key equality check. Fix: header in the filename, or a header-agnostic content store plus separate header records (`GlobalSavedDataSnapshots.addSnapshot/readSnapshot`, `SnapshotIndex`, `Snapshot.Key`).

## Builder clear-mode reflood loop

In CLEAR fluid mode, a dry waterloggable placed next to water OUTSIDE the build area gets waterlogged by that neighbour; `isBuilt` is strict in CLEAR, so it re-queues a dry-it task forever (MJ only, no items lost; before 2026-09-26 it was deferred forever instead). Consider counting a cell that outside water re-floods as built, or placing it waterlogged. `SchematicBlockDefault.build/isBuilt`, `BlueprintBuilder`.

## Quarry frame stall

Pre-existing: if a cell in `TileQuarry.frameBreakBlockPoses` fails `canMine` (bedrock, a claim-protected block, a modded block logged with a thick fluid), `check()` re-adds it and the power loop spins on it every tick, so the frame never finishes. Skip or report such cells.

## Mining Well tube erases water

Pre-existing: `BlockTube` isn't waterloggable, so `TileMiner.updateLength`'s `setBlockAndUpdate(TUBE)` overwrites every water source in the bore, and retracting (`removeBlock`) leaves air — a well drilled through a pond permanently deletes its water column. Make the tube waterloggable, or restore the fluid on retract.

## Zone Planner slot saving

`TileZonePlanner` builds its seven `ItemHandlerSimple` slots (`invPaintbrushes`, `invInputPaintbrush`, `invInputMapLocation`, …) with a null callback outside the `itemManager`, so GUI changes may never call `setChanged()` — the bug class fixed for the Filtered Buffer on 2026-09-26 (`ItemHandlerManager` prebuilt-handler callback). Check whether the container/slot path marks the tile dirty; if not, wire the callback and add a save round-trip game test.

## Integration Table JEI

Robot + board → programmed robot, flat 5000 MJ (`RobotIntegrationRecipe`). No JEI presence at all. Pattern: a JEI-free collector like `robotics/compat/jei/ProgrammingRecipeCollector`; `RoboticsJeiSubtypes` already keys robots and boards. Optional extra for the Programming Table: a JEI '+' transfer — `TileProgrammingTable.findRecipe` runs only in `serverTick`, so options are null until the next tick and `selectOption` clamps to -1; the server handler must find recipes right after inserting.

## Guide overlay click-through

`GuiGuide.mouseClicked` with `showingContentsMenu`: `currentPage.mouseClicked` runs before the overlay swallows the click, so the contents page's search tab and sort buttons beneath the open small-screen chapter overlay still take clicks. Check the overlay before delegating to the page.

## JEI late start on 26.1.2

A 26.1.2 dev client quick-playing into a world logged `A Screen is opening but JEI hasn't started yet ... Missing events: [TagsUpdatedEvent]` and JEI started late on the first screen open. 26.2 and 1.21.1 logs start JEI normally. Unverified whether quick-play-only or a real join-path problem (a `TagsUpdatedEvent` not firing/swallowed) — do one normal-join check.

## Flaky marker tests

`marker_orientation`, `marker_volume_los` and `marker_volume_triangulation_3d` failed together once in a full 26.1.2 game-test run (2026-09-26) and passed on every later run. Likely arena/concurrency interference — see the game-test arena-geometry and batch-isolation memories.

## Small cleanups

Found in passing on 2026-09-26; none is a bug today.
- `TileEngineStone_BC8` (26.x branch) still calls the deprecated item-level `Item#getCraftingRemainder()` under a narrow suppression: NeoForge #3157 moved the stack-sensitive call mid-26.1.x. Switch to `consumed.getCraftingRemainder()` once the 26.1 floor passes that build (check 26.2's first build too).
- `StatementParameterItemStackExact.readFromNbt` defaults `availableSlots` to 0 when the key is missing; upstream kept -1 (unlimited up to 64). Only hand-edited/foreign NBT. Default to -1 + unit test.
- `BCEnergyFluids`' "layer cake" guard (`if (targetFluidState.getType().isSame(this)) continue;` in both `getSpread` overrides) looks redundant with `FlowingFluid.canBeReplacedWith`.
- Five tests keep private `repoRoot()` copies (ClientItemDefinitionCoverageTester, GameTestManifestTester, FakePlayerProfileTester, CopyrightHeaderTester, BlockStateVariantCoverageTester) — use `TestHelper.repoRoot()`.
- `GuiGate`'s connector hit-test is duplicated between the `>=1.21.10` and 1.21.1 `mouseClicked` bodies and repeats `drawBackgroundTexture`'s layout maths — one helper.
- Not re-audited: method-level `@SuppressWarnings("deprecation")` on Lens/Gate/FacadeItemModel, QuadItemBakedModel and the four FragileFluidShardModel getters.
- Optional wording (user call): upstream 7.1.27 `b84395c85` renamed the emerald-pipe tips Whitelist/Blacklist to "Limit (Only Filtered)"/"Exclude (Except Filtered)"; the Diamond-Wood GUI still uses `tip.PipeItemsEmerald.*`.
- Optional API: upstream `d891e3b76` let addons forbid blocks from the default crop handler via IMC; the modern equivalent is a block tag read by `CropHandlerPlantable`. Only if an addon asks.

## In-client smoke 2026-09-26

Headless tests can't see these; check on 26.1.2 (plus a look on 1.21.1 and 26.2):
- Blocks-atlas-id sweep: kinesis pipes (MJ + RF flow), tank/distiller/heat-exchanger fluid boxes and distiller power cubes, laser beams/boxes, blueprint PiP previews and the blueprint GUI renderer (1.21.1 especially), pipe-preview pluggables, filler-planner addon box, fluid GUI tanks (GuiElementFluidTank, GuiFluid, iron-engine screen), gate plugs, stripes-pipe renderer, LED variable models, painted fluid-pipe item models.
- GUI window rect (`GuiBC8#window*`): window placement in every BC GUI, ledgers, JEI ghost-drag; ideally the 26.1.2 jar once on a 26.1/26.1.1 runtime.
- Water gel break speed and sounds; TNT beside an obsidian-faced pipe (the facade shields that side).
- Buttons: guide cover arrows/back/tooltips/sort radio, Filler Planner invert, the migrated machine screens on 1.21.1 and 26.2.
- Robots: a working robot puffs red smoke at the right rate (fewer on Decreased/Minimal); `/particle buildcraftunofficial:robot_energy{size:100000}` errors; a blank robot on a station shows "Not programmed" with no arm swing (SP and dedicated server).
- McDevBridge lacks a `/screenclick` endpoint (mouse button + GUI coords) — that blocked the guide/Emzuli/tooltip checks headless.

## Dev-only item files in release jars

The item-definition coverage guard compares against the DEV registry (`tasks.test` sets `-Dbuildcraft.dev=true` like every run env), so the 6 dev-gated items' definitions/models are permanently invisible to it — and those files ship in release jars where the items never register. (The Power Tester left the dev-gated set on 2026-09-20 and registers publicly.) Excluding dev assets from the jar (or re-deciding the allow-list route) is the open question, not a decided task.

## Quick notes

One-line technical breadcrumbs for bullets that don't need a full section:

- **Facade smooth shading** — AO infrastructure already exists in `MutableQuad`; needs facade-specific wiring.
- **Filler mode icons** — the old `filling/` selector-icon set archived at `misc/unused_textures/filling/` is stale erroneous leftover, not a basis for the new art.
- **Goggles texture** — `item/goggles.png` is a verbatim copy of the vanilla paper texture; the headpiece stays dev-gated until real art exists.
- **Cauldron as tank** — NeoForge already exposes `IFluidHandler` capabilities for cauldrons; wire that into BC's fluid pipe connection logic (drain water/lava/powder-snow, fill to the appropriate level).
- **Builder can't-place flags** — e.g. a red box overlay on the resource list or the build area for blocks that can't be placed yet (floating torches, flowers on stone).
- **Fluid viscosity (blocked)** — flow speed is already moddable, but negative density (floating gases) is not native and traversal/swimming modifications aren't possible.

## NeoForge 26.2.0.87 recompile break

Bumping the 26.2 node to NeoForge 26.2.0.87 fails in `:26.2:createMinecraftArtifacts` — NeoForm's recompile of the patched MC sources dies with `contents() in <anonymous net.minecraft.core.HolderSet$1> cannot override contents() in net.minecraft.core.HolderSet.Named; attempting to assign weaker access privileges; was public` (line 44 = the `emptyNamed` anonymous-class override in `net/minecraft/core/HolderSet.java`).

Diagnosed 2026-09-11: [PR #3451](https://github.com/neoforged/NeoForge/pull/3451) (registry-based-conditions fix, in .87 only) added two accesstransformer.cfg lines widening `HolderSet$Named contents()` and `HolderSet$1 contents()` to public. ModDevGradle's recompile classpath carries the AT-applied binary while the decompiled sources still declare both methods `protected`, so javac rejects the override. Verified the patched-sources zip for .87 is clean (`protected` everywhere) — it is purely the AT/binary-vs-source mismatch. Breaks `createMinecraftArtifacts` for every ModDevGradle user on .87; 26.1.2.109 does not carry the AT change and builds fine.

**Unblock:** bump the pin once NeoForge ships a fix (drop the two `HolderSet` AT lines or patch the sources to match). The SessionStart hook will re-flag the node; file/check an upstream issue at that point if still broken.
