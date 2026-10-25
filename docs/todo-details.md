# Todo details

Agent-facing background for the one-line bullets in [todos.md](../todos.md). Each section is linked from its bullet. **When a bullet ships, delete its section here in the same change.** Sections mirror the todos order.

## Pump on top of a Quarry

Seam to plug into: `factory.tile.IPumpHost` (the Mining Well implements it; a `TilePump` on any host drains from `getBlockingFluid()` on the host's battery). The Quarry does not fit it as-is: it never stops at fluid, it *skips* a fluid cell (`canMine` false) and every cell under it (`canMoveDownTo` false) and finishes, leaving lava pillars. Needs: the quarry recording the obstructing fluid cells its sweep skipped (cheaply: an idle pump re-asks every 30 ticks), a revisit of those columns after draining (resetting `boxIterator` re-walks the box), and a way back to work once finished (a finished quarry's receiver stops requesting power and its chunk tickets are released, see `hasPendingWork`/`isActive`). The pump sits on the quarry block at the frame's edge; `pumpMaxDistance` is measured from the seed, so the remote pool is fine. Open: whether lava that flowed into the dug pit counts as blocking.

## Reclaimable marker region

Survival UX, optional. Let connecting markers spawn an *editable* survival region: drag a corner with the marker connector, and on apply (exit edit mode) — if the box no longer matches the markers — the markers break (as if a machine adopted them) and the box floats as a one-shot, consumable-until-a-machine-takes-it region, vanishing with that machine. Stays distinct from the creative item box (reusable) via a `source`/`adopted` flag; both removable via the connector. NOTE: this is an *additive feature* that increases the volume-box lifecycle complexity — keep it out of any marker/volume plumbing consolidation and evaluate it on its own merits. Today's survival lifecycle (markers consumed on machine adoption) already works without it.

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

## JEI transfer decisions

Open questions from the 2026-10-17 Programming/Integration Table "+" work (`RoboticsTableTransfer`, `AbstractTableTransferHandler`): the "+" takes only BLANK boards/robots (a player holding only programmed boards sees "missing materials" though the table would reprogram one); the Integration Table refuses with "Empty the table's slots first" instead of JEI's usual clear-to-inventory. Also: JEI ghost-ingredient drops onto phantom slots send only the item id (`BCGhostIngredientHandler` ~95, `BCContainerSupport` NET_GHOST_SLOT_SET, `ReiCraftingTableSupport` ~173), so a data-carrying ingredient drops as its bare item; and the Assembly Table transfer matches ingredients by exact item+components rather than `Ingredient.test`.

## Forbid Robot on the docked station

`AIRobotSearchStation.start()` returns the station a robot is already docked at before it checks `isRobotForbidden` (7.1.x did the same), so a Forbid Robot gate on a robot's own dock does not stop it loading, unloading or recharging there. A fix was tried on 2026-10-17 (make the docked shortcut honour the forbid) and backed out: with no other accepting station in reach, a Delivery robot homed on a forbidding station then disposed of its whole cargo on the ground. Options: keep 7.1.x behaviour; or, when a forbid starts matching the docked robot, undock it so the search picks another station (cargo-safe), and decide whether a robot may still sleep at a home that forbids it.

## Zone Planner Ctrl+click erase on macOS

On macOS vanilla turns a Ctrl+left press into a simulated right click, so an erase stroke starts as a right press while the finger is on the left button. `ZonePaintStroke.press` then follows the left button (`ZonePaintStrokeTest` pins the rule); everything else about the stroke, pan and erase input was checked with a real mouse on 1.21.1, 26.2 and 26.3 on 2026-10-25 (`scripts/smoke/scenarios/zone-planner-drag.json`). Owed: one Ctrl+drag on the map with a brush on a Mac client, expected to erase a rectangle from the press to the release.

## Dev-only item files in release jars

The item-definition coverage guard compares against the DEV registry (`tasks.test` sets `-Dbuildcraft.dev=true` like every run env), so the 6 dev-gated items' definitions/models are permanently invisible to it — and those files ship in release jars where the items never register. (The Power Tester left the dev-gated set on 2026-09-20 and registers publicly.) Excluding dev assets from the jar (or re-deciding the allow-list route) is the open question, not a decided task.

## Quick notes

One-line technical breadcrumbs for bullets that don't need a full section:

- **Loop Filler packet spam** — in `TileFiller.tick()`, LOOP mode keeps `isFinished()` false, so once `b.tick()` reports done it runs `b.onNetworkSync()` + `markForRenderUpdate()` every tick (only the advancement is edge-gated on `!finished`). Gate the sync on the transition into done. Pre-existing; found in the 2026-10-25 Filler preview reviews.
- **Filler mode icons** — the old `filling/` selector-icon set archived at `misc/unused_textures/filling/` is stale erroneous leftover, not a basis for the new art.
- **Goggles texture** — `item/goggles.png` is a verbatim copy of the vanilla paper texture; the headpiece stays dev-gated until real art exists.
- **Cauldron as tank** — NeoForge already exposes `IFluidHandler` capabilities for cauldrons; wire that into BC's fluid pipe connection logic (drain water/lava/powder-snow, fill to the appropriate level).
- **Builder can't-place flags** — e.g. a red box overlay on the resource list or the build area for blocks that can't be placed yet (floating torches, flowers on stone).
- **Fluid viscosity (blocked)** — flow speed is already moddable, but negative density (floating gases) is not native and traversal/swimming modifications aren't possible.

## JEI old transfer overload

JEI 30.30.0.203+ (26.2 node) marks the six-arg `IRecipeTransferHandler#transferRecipe(C,R,IRecipeSlotsView,Player,boolean,boolean)` for removal but keeps it abstract, so `AbstractBucketTransferHandler`, `BlueprintTransferHandler` and `AssemblyTableTransferHandler` must still declare it; their real entry point on 26.2 is the `//? if >=26.2` `transferRecipe(IRecipeTransferContext, boolean)` override, and both delegate to a private `transfer(...)`. Reopen trigger: a JEI 30.x (or later 26.2+ line) release where the six-arg method is gone. Then delete the old override behind `//? if <that line` (other nodes' JEI never had the context overload), and remove the `IRecipeTransferHandler#transferRecipe` entry from `ForRemovalApiGuardTester.PENDING`. Note that dropping it also drops support for JEI < 30.30 on 26.2, so add a `jei` version range to the mods.toml at that point.
