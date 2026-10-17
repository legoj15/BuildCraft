# Todo details

Agent-facing background for the one-line bullets in [todos.md](../todos.md). Each section is linked from its bullet. **When a bullet ships, delete its section here in the same change.** Sections mirror the todos order.

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

## Guide book follow-ups

Left over from the 2026-10-15 Sort By System / Robots chapter work ([docs/guide-robots-and-toc.md](guide-robots-and-toc.md)):
- Board pages have no icon: they are `external` entries and `PageEntryExternal#createDrawable` returns null. Give external entries an optional `icon` stack field (a programmed board stack via `ItemRedstoneBoard.createStack`) so Robots > Boards reads like the rest of the tree.
- The Sort By System button reuses the old Sort By Mod glyph (`textures/gui/sprites/guide/sort_system*.png`, four states). Needs a 14x14 icon that says "system".
- Robot name tags: `RenderRobot` draws a custom name, but the robot is a bare `Entity`, so the vanilla name tag never applies and nothing else sets one. Either accept name tags in `EntityRobot#interactRobot` (and carry the name through the item on wrench pickup) or drop the render hook. The guide no longer mentions naming.
- `RoboticsJeiSubtypes` and the two board/robot program collectors live in `buildcraft.robotics.compat.jei` but the guide's recipe lookups use them too; move them to a neutral package.
- `GuideAssembly` still shows a raw "N MJ" cost tooltip with a stale "localizeMj not ported" comment; `LocaleUtil.localizeMj` exists and is what the new laser-table panel uses.
- In the system view the Robots chapter's first subchapter is also called "Robots" (subtype `robot`). A better subtype label ("Robot Basics"?) would read better in both views.
- Only 26.1.2 was looked at in-client for the new contents page and pages; the other four nodes compiled and passed unit tests only.

## Builder stalls beside unremovable fluid

Left from the 2026-10-17 Clear-mode reflood fix (`BlueprintBuilder.isFragileUnbuildableAt` / `isRefloodedByUnmoppableWater`, `BuilderRefloodTester`). The Builder can still sit "busy" for ever, with nothing visible happening, next to fluid it can never remove:
- **Clear mode, outside lava** beside a fragile cell (snow layer, torch): the cell is mopped once, then the fragile block can never be placed and nothing refills it (`builder_clear_fragile_outside_lava_still_mopped` pins the not-finished half). Water is tolerated only because vanilla's infinite-water rule makes it permanent; lava has no such rule, so a decision is needed: accept it like water, or report the cell.
- **Replace mode** holds back every fragile placement while any fluid is anywhere in the box (the gate was added for a user-reported race with moving water), so a Replace build in water with one torch never finishes. Needs a way to tell settled water from moving water.
- Air-cell tolerance is water-only: modded fluid flowing in from outside is mopped for ever in Clear.
- Cells accepted as unbuildable vanish from the build silently — tie into the "Builder highlights blocks it can't place yet" feature.
- Dead code from `453e756f2`: `isWaterlogClearOnly` plus `BlueprintBuilder.isWaterlogClearOnlyAt/isAllowedDuringFluidMop/hasEnoughToPlaceItems/getToPlaceItems` carve-outs can never fire (canPlace rejects every fluid cell under Clear). Remove with this work.

## Filler owner shadow

`TileFiller` declares `private GameProfile owner` (~line 118) over the inherited `AbstractBCBlockEntity.owner`, so the inherited `setOwner()` is invisible to its `getOwner()` — the same bug fixed for `TilePipeHolder` on 2026-10-17 (`pipe_set_owner_agrees_with_get_owner`). Its NBT shape differs (an `owner` child with name/uuid), so the fix must keep old saves loading.

## Stone engine fuel components

`TileEngineStone_BC8.writeData/readData` (~312-345) saves the burning fuel as `fuelId` + `fuelCount` only, so a fuel item carrying data components loses them on save/reload (and its crafting remainder can change). Same class as the Assembly Table / Builder place-task fix of 2026-10-17: save through `ItemStack.CODEC` with `NBTUtilBC.registryAwareOps()`, keep reading the old two-field shape.

## Marker client cache

`MarkerCache`'s client sub-cache (`cacheClient`) is never cleared, so it carries markers and connections across worlds and dimensions, and `MarkerCache.onPlayerJoinWorld` (the join-time resync upstream called from `BCLibEventDist`) is unwired. Only the server half was wired on 2026-10-17. Clearing the client cache alone would drop connections on a dimension hop, so wire the join resync with it and check in-client.

## JEI transfer decisions

Open questions from the 2026-10-17 Programming/Integration Table "+" work (`RoboticsTableTransfer`, `AbstractTableTransferHandler`): the "+" takes only BLANK boards/robots (a player holding only programmed boards sees "missing materials" though the table would reprogram one); the Integration Table refuses with "Empty the table's slots first" instead of JEI's usual clear-to-inventory. Also: JEI ghost-ingredient drops onto phantom slots send only the item id (`BCGhostIngredientHandler` ~95, `BCContainerSupport` NET_GHOST_SLOT_SET, `ReiCraftingTableSupport` ~173), so a data-carrying ingredient drops as its bare item; and the Assembly Table transfer matches ingredients by exact item+components rather than `Ingredient.test`.

## Small cleanups

Found in passing; none is a known player-facing bug today.
- Optional wording (user call): upstream 7.1.27 `b84395c85` renamed the emerald-pipe tips Whitelist/Blacklist to "Limit (Only Filtered)"/"Exclude (Except Filtered)"; the Diamond-Wood GUI still uses `tip.PipeItemsEmerald.*`.
- Optional API: upstream `d891e3b76` let addons forbid blocks from the default crop handler via IMC; the modern equivalent is a block tag read by `CropHandlerPlantable`. Only if an addon asks.
- Refusal memory exists twice plus micro-variants: `ProtectionRefusals` (stripes pipe, robot search, quarry frame; bounded, polled with `takeExpired`), `DrainRefusals` (pumps; unbounded, window grows with the pool, walks the whole pool past 300 entries), `TilePump.permittedPos`, `AIRobotPumpBlock.refused` and `BoardRobotPump`'s clear-on-owner-change. Unify into one `RefusalMemory` (retry policy + clock + optional owner key), and give the repeated "ask claim mods as the owner, then remember the refusal" lifecycle (pump, stripes, quarry, robot boards) one `MachinePermission` helper over `BlockUtil.canMachineBreak` (2026-10-17 GLM-5.3 cross-packet review).
- Two JEI transfer cores with different vocabularies: `RoboticsTableTransfer` (Result enum, simulate-then-commit) and `AssemblyTableTransfer` (Request record), plus the older `NET_JEI_TRANSFER_BUCKETS` path. Share one result type and one advisory/send/server-re-run skeleton. Likewise `AIRobotStripesHandler.takeBackFrom` and `PipeBehaviourStripes` decide "what is left of the used stack" separately; extract one helper so they can't drift.
- `TileMiner.updateLength` removes and re-places every tube on each length change (with water it now un-waterlogs and re-waterlogs every cell per mined block); make it delta-aware. Only water SOURCES are kept by the tube; the pump tube is also placed into claims without asking.
- `TilePump` rebuilds its whole flood fill every 30 ticks while its queue is empty (radius up to `pumpMaxDistance`, 512 max), costly for a huge refused or exhausted pool; and an abandoned bucket (drain null) keeps its progress, so the next cell gets one bucket free.
- `TilePump.isInfiniteSourceAt` counts a neighbouring well's waterlogged tube as a water source (vanilla's regen rule agrees, but a pre-waterlogging world had a dry bore there): decide and pin with a test. A miner-tube block tag would also replace `BoardRobotPump`'s direct `buildcraft.factory` import.
- `BlockUtil.drainBlock` goes through `SimpleWaterloggedBlock.pickupBlock`, which destroys a waterlogged block that can't survive dry (with drops).
- `BlockUtil.breakBlockAndGetDropsWithXp` takes an owner profile and ignores it; `AIRobotHarvest` runs `CropManager.harvestCrop` with no player at all; the stripes handler's actual destroy posts no BreakEvent (only the pre-swing probe does).
- `GlobalSavedDataSnapshots.readSnapshot` decompresses every file of a build on a content-only miss; `SnapshotIndex.contains` is a linear scan; `intactFiles`' Javadoc says files are trusted once read back, but reads never add to it.
- `ProgrammingRecipeRegistry` is a HashMap, so `findRecipe`'s pick is nondeterministic once two recipes accept one board; the GUI/collector/transfer cap at 24 cells, so an addon board past index 23 is unreachable. `AssemblyTableCategory` restates the Assembly Table geometry the way the two robot tables did (see `ProgrammingTableLayout`).
- Pipe behaviours with GUI-edited state other than Emzuli/Emerald/Diamond (Daizuli, Lapis, Gold, Iron, Stripes, Limiter) were not audited for dirty-marking; the per-tick `level.blockEntityChanged` in `TilePipeHolder.tick()` masks the bug class.
- Game tests: many nest `runAfterDelay` inside scheduled runnables while `GameTestInfo.tickInternal` iterates a fastutil map, so a mid-iteration insert can rehash and skip or double-run entries — audit for latent flakes. A test claimed `GameTestHelper.relativePos` negates coordinates on 26.1.2 (worked around in `MarkerArenaUtil`); verify. `StripesRegistry` has no removal API, so test handlers leak into the rest of the run.
- `ServerUnitBakingGuardTester` exempts every path containing `/gui/`, which also hides server-side menu classes in `buildcraft/lib/gui`. Item names built on the SERVER (a chat `[item]`, advancement text) always carry the FE key, since `useRfNaming` is client config — accepted limitation, documented in `BCEnergyItems`.
- `TileQuarry.retryRefusedFrameCells` re-queues only when `canMine` is true, so a parked frame cell that turned into bare fluid keeps its gap until chunk reload; `TaskBreakBlock.finish` doesn't park a cell whose `canMine` flipped mid-break.
- `DockingStation.unsafeRelease` only clears when the cached `robotTaking` is the same instance; `BoardRobotDelivery.releaseCurrentRequest` calls `DockingStation.release` without the `canRelease` guard `AIRobotGotoStation.end()` gained.

## In-client smoke 2026-09-26

Verified in a live 26.1.2 client on 2026-09-26 (McDevBridge): kinesis MJ flow, tank/distiller/heat-exchanger fluids, laser beam, Builder/Auto Workbench/Diamond-wood/Emzuli/Tank/List/Filler GUIs (placement, vanilla-face buttons, pressed-in modes, ledgers), the red exhaust particle and its size-bound command error; no BuildCraft render errors in the log. (JEI's quick-play "hasn't started yet / missing TagsUpdatedEvent" line also appears in 2026-08 logs and BC has no listener for that event, so it's JEI quick-play timing, not ours.) Still owed:
- Not yet in the scenario (add steps to `scripts/smoke/scenarios/line8.json`): pipe-preview pluggables in hand, filler-planner addon box + its invert button, gate plugs, stripes-pipe renderer, LED variable models, painted fluid-pipe item models, volume-marker addon icons (`AddonDefaultRenderer` now binds the sprite's own atlas page), the contents page's left chapter tabs beside the sort buttons (needs a window wide enough for side tabs; the runner's is small-screen), TNT beside an obsidian-faced pipe, a working robot's smoke rate on Decreased/Minimal.
- Cosmetic, confirmed on 26.1.2 and all three 1.21 lines by the smoke run: on the Filler GUI, JEI's page-left arrow sits over the owner ledger at the window's top-right (JEI avoids ledgers for its item grid, not its nav bar).

Scripted smoke run 2026-09-26 (`scripts/smoke/`, `line8` scenario, 95 steps) clean on **all five nodes** (26.1.2 and 26.2 first; the 1.21 lines once McDevBridge `71310b0` fixed `/worldclick` below 26.1): render rig (creative engine → kinesis pipes with visible flow → power tester, tank filled by bucket, distiller, heat exchanger, laser), Tank/Builder/Auto Workbench/Filler/Diamond-wood/Emzuli/List GUIs with every button's hover tooltip, Emzuli slot right/middle click, guide cover, page arrows with tooltips, contents page, small-screen chapter overlay, back. Its screenshots caught a real bug, fixed the same day: picking a chapter from the small-screen overlay opened the one above it (`GuideChapterTitleWrapTest`; the fix then verified in-client on the 1.21 lines). The three 1.21 lines' screenshots match each other and the 26.x ones — no node-specific render or GUI differences.

Moved from this list into tests (2026-09-26): water gel's per-stage break speed and sounds (`WaterGelBlockTester`, via the same state calls the client digs with); the client's no-swing prediction for a blank robot (`ItemRobotPlacementTester.clientPredictsNoSwingForBlankRobot`; the server-side refusal and message were already pinned); the facade/gate/lens/fragile-shard JEI keys, identity and exact legacy text (`JeiSubtypeKeysTest`). The 1.21.1 JEI bookmark worry is narrower than first written. Verified by `javap` on `jei-1.21.1-neoforge-19.27.0.340.jar` (not in a live client): `BookmarkJsonConfig`/`BookmarkCodec` serialize each bookmark through `ICodecHelper.getTypedIngredientCodec()`, i.e. the full stack, so bookmarks resolve by stack; only the pre-JSON `LegacyBookmarkConfig` import (`LegacyTypedIngredientSerializer`) goes through the legacy subtype text, which the key test now pins.

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

## JEI old transfer overload

JEI 30.30.0.203+ (26.2 node) marks the six-arg `IRecipeTransferHandler#transferRecipe(C,R,IRecipeSlotsView,Player,boolean,boolean)` for removal but keeps it abstract, so `AbstractBucketTransferHandler`, `BlueprintTransferHandler` and `AssemblyTableTransferHandler` must still declare it; their real entry point on 26.2 is the `//? if >=26.2` `transferRecipe(IRecipeTransferContext, boolean)` override, and both delegate to a private `transfer(...)`. Reopen trigger: a JEI 30.x (or later 26.2+ line) release where the six-arg method is gone. Then delete the old override behind `//? if <that line` (other nodes' JEI never had the context overload), and remove the `IRecipeTransferHandler#transferRecipe` entry from `ForRemovalApiGuardTester.PENDING`. Note that dropping it also drops support for JEI < 30.30 on 26.2, so add a `jei` version range to the mods.toml at that point.

## NeoForge 26.2.0.87 recompile break

Bumping the 26.2 node to NeoForge 26.2.0.87 fails in `:26.2:createMinecraftArtifacts` — NeoForm's recompile of the patched MC sources dies with `contents() in <anonymous net.minecraft.core.HolderSet$1> cannot override contents() in net.minecraft.core.HolderSet.Named; attempting to assign weaker access privileges; was public` (line 44 = the `emptyNamed` anonymous-class override in `net/minecraft/core/HolderSet.java`).

Diagnosed 2026-09-11: [PR #3451](https://github.com/neoforged/NeoForge/pull/3451) (registry-based-conditions fix, in .87 only) added two accesstransformer.cfg lines widening `HolderSet$Named contents()` and `HolderSet$1 contents()` to public. ModDevGradle's recompile classpath carries the AT-applied binary while the decompiled sources still declare both methods `protected`, so javac rejects the override. Verified the patched-sources zip for .87 is clean (`protected` everywhere) — it is purely the AT/binary-vs-source mismatch. Breaks `createMinecraftArtifacts` for every ModDevGradle user on .87; 26.1.2.109 does not carry the AT change and builds fine.

**Unblock:** bump the pin once NeoForge ships a fix (drop the two `HolderSet` AT lines or patch the sources to match). The SessionStart hook will re-flag the node; file/check an upstream issue at that point if still broken.
