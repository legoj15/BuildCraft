# Todo details

Agent-facing background for the one-line bullets in [todos.md](../todos.md). Each section is linked from its bullet. **When a bullet ships, delete its section here in the same change.** Sections mirror the todos order.

## 1.21.1 gated-test port

All 24 `//? if >=1.21.10`-gated game tests are faithfully implementable on 1.21.1 (1 PORTABLE_VERBATIM, 23 REWRITABLE, 0 IMPOSSIBLE). The old "25 silently-skipping tests" premise was wrong — the gap is exactly 24 gates + 1 vanilla builtin (`always_pass`, modern-registry-only), and the counts close exactly at doc time (396 regs − 24 gated = 372 reported) and today (414 − 24 = 390/390 pass; modern 415/415). The reflective `addReflectively` path registers every test faithfully; nothing was ever silently dropped.

Cross-cutting moves (serve many rewrites):
- **`BCFluidTank` facade** (`fill`/`drain`/`setFluidStack`/`getAmountMb`/`isTankEmpty` + simulate/`FluidAction`) is the mapping behind 15 of 23 rewrites — heat_exchanger (6), distiller tank (4), tank_manager (5). Modern `insertInternal(int, FluidResource, int, tx)` overloads exist in production only to exercise the modern path; the neutral facade is the 1.21.1 target.
- **Class-level un-gates free whole groups**: heat_exchanger (1 class → 7), distiller (1 → 7), flood_gate (1 → 4), tank_manager (1 file → 5). No per-test splitting anywhere.
- **Recurring directive pairs**: `getBlockEntity` 1-arg/2-arg (distiller 4 + flood_gate 4), `InteractionResult`↔`ItemInteractionResult` (distiller 1 + flood_gate 3), `Identifier.parse`↔`ResourceLocation.parse` — same idiom as the ungated `WrenchTagTester`.
- **One production fix**: `heat_exchanger_slot_caps_at_max_stack_size` needs `ItemHandlerSimple.getSlotLimit`→`slotCapacity` on 1.21.1 (one line; modern unaffected) — also closes a real 1.21.1 UX gap (64-bucket stack the auto-fill loop can't place).

Weaker-guarantee caveats (document, don't silently weaken):
- **tank_manager**: modern no-slot `ResourceHandler.insert`/`extract` accumulate across slots in one call; the 1.21.1 classic composite returns at the first matching slot — pin the weaker cross-call property (ordered routing/drain, conservation, SIMULATE non-mutation), which still catches a LIFO reorder.
- **gate_display**: partially redundant — the underlying regression is covered by ungated `guide_modifier_recipe_renders_correct_input_variant` + `basic_gate_appears_as_input_in_modifier_recipes`; the default-components half is vacuous on 1.21.1 (no `PatchedDataComponentMap`). Unique value = input-side GOLD stack identity.
- **distiller_output_reports_capacity_at_rest**: pins the readout contract, not the original regression (the capacity≤0 bug lived only in the Transfer-API branch).
- Fix the stale comment at `BuildCraftGameTests.java:454-460` ("written entirely against the NeoForge Transfer API") — FloodGateTester has no fluid API at all.

Order: flood_gate + distiller first (cheapest, highest verbatim ratio), then heat_exchanger (ships the production fix), tank_manager + gate_display last (weaker/partial).

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

## Heat-tiered fuel and Nether oil

Make a fuel's *heat tier* change its value in the Combustion Engine, on a deliberately **non-monotonic** curve: hot (`heat_1`) **better** than cool (`heat_0`), searing (`heat_2`) **worse** than cool. Paired with Nether oil spawns, this is the intended route to a **Nether start** for the mod. Design intent recovered 2026-07-21 from an orphaned legacy lang string (`gui.buildcraft.combustion_engine.help`, "Higher temperature fuels produce more power" — wording predates the final non-monotonic design and should NOT be reused verbatim); the string was deleted, so this document is now the only record.

- **Today heated fuel isn't weaker — it's rejected.** Every `addFuel`/`addDirtyFuel` call in [BCEnergyRecipes.java:75-87](../src/main/java/buildcraft/energy/BCEnergyRecipes.java) resolves through `findFluid(baseName)` → `findFluidByHeat(baseName, 0)`, so **only heat-0 variants are ever registered**, and `TileEngineIron_BC8` gates acceptance on `BuildcraftFuelRegistry.fuel.getFuel(fluid) != null`. So this is "register the heat_1/heat_2 variants with their own multipliers", not "re-tune existing numbers". Current heat-0 multipliers track *fraction weight*, not temperature (gaseous 8, light 6, dense 4).
- **Blocking constraint — the hot tier of the best fuels is a GAS.** `gaseous = density < 0` where `density = baseDensity × (heat >= boilPoint ? −1 : 1)` ([BCEnergyFluids.java:196-198](../src/main/java/buildcraft/energy/BCEnergyFluids.java)), and boil points from the table at BCEnergyFluids.java:86 are: crude/residue/heavy/dense oil = 3 (**never** gaseous), distilled oil + dense fuel + mixed-heavy = 2 (gaseous only at searing), light fuel + mixed-light = 1 (**gaseous already at `heat_1`**), gas fuel = 0 (**gaseous at every tier**). So "hot light fuel is the good stuff" means the reward tier floats upward in pipes and needs gas-capable handling, while "searing is worse" lands on fluids that are *also* gaseous — the penalty and the gas transition are confounded for exactly the fuels players will optimise toward. Decide up front whether the curve is keyed on heat tier or on phase, and whether the 4 always-liquid oils get a different curve from the fuels.
- Open questions: Nether oil generation (biome/feature wiring — reuse the existing `OilFeature`/`AddOilBiomeModifier` path); whether searing fuel is merely inefficient or actively harmful (residue? overheat?); whether the Heat Exchanger becomes mandatory infrastructure for the good tier, and if so whether that's too steep for a Nether start.

**Nether oil spawns** (the worldgen half): generate oil in the Nether (lakes/spouts) for Nether-start challenge runs. Nether oil always spawns *searing* (heat 2), never cool — the inverse of Overworld oil. The `heating_and_distilling` advancement already accounts for this: its Nether branch treats searing as the natural (non-qualifying) heat, so the advancement still demands Heat-Exchanger work there. See `TileDistiller_BC8.qualifiesForHeatingAdvancement`.

## Pump on top of miners

Both miners stop when they hit blocking fluids today (Mining Well and Quarry both halt on lava/oil; both drill past low-viscosity water to the solids below). Adding a Pump on top of either should consume fluid from the same blocking column, draining it into a buffer / adjacent tank or pipe so the miner can resume. Power for the on-top Pump comes out of the host miner's internal MJ battery rather than requiring a separate engine hookup. Mining Well top texture should swap to the open Flood Gate sprite when a Pump is mounted. Open: shared-battery priority/throttling, where the drained fluid goes, whether the Quarry's larger column needs a different sweep pattern than the Well's single-block-below path.

## Reclaimable marker region

Survival UX, optional. Let connecting markers spawn an *editable* survival region: drag a corner with the marker connector, and on apply (exit edit mode) — if the box no longer matches the markers — the markers break (as if a machine adopted them) and the box floats as a one-shot, consumable-until-a-machine-takes-it region, vanishing with that machine. Stays distinct from the creative item box (reusable) via a `source`/`adopted` flag; both removable via the connector. NOTE: this is an *additive feature* that increases the volume-box lifecycle complexity — keep it out of any marker/volume plumbing consolidation and evaluate it on its own merits. Today's survival lifecycle (markers consumed on machine adoption) already works without it.

## Heat exchanger visual overhaul

The cap models (paper-thin 12×12 planes covering the unconnected sides of a middle segment, visible only on single-piece or end-of-chain assemblies) were consolidated from two mirror-image files into one shared `heat_exchange_cap` rotated via blockstate (2026-05-19). The current placed-block look is functional but visually thin compared to the inventory icon ([models/item/heat_exchange.json](../src/main/resources/assets/buildcraftunofficial/models/item/heat_exchange.json) — references `sprite_b`, which the consolidated cap still uses): open ends look hollow, segments don't read as a coherent industrial pipe assembly. Redesign the exchanger's in-world look: better end-cap geometry that's actually visible (the current paper-thin plane is the *only* thing using `sprite_b` in-world), and decide whether the item icon should keep its current shape or match the new in-world look. The `heat_exchanger_cap.bbmodel` master that was moved to `misc/model_masters/` may or may not be a useful starting point (its `r`-suffixed name doesn't match the active `heat_exchange_*` family, and nothing on disk suggests it was ever wired up).

## Plug model unification

Unify plug in-world geometry under vanilla `models/block/plug_*.json`. Today the plug rendering pipeline has four flavours: (a) blocker / power_adapter load BC-dialect JSON from `models/plugs/` via `ModelHolderStatic`, (b) timer / light_sensor / pulsar-base bake from hardcoded UVs in [PlugBakerSimpleItems.java:70-94](../src/main/java/buildcraft/silicon/client/model/plug/PlugBakerSimpleItems.java), (c) lens / facade / gate are genuinely parametric and stay in Java, and (d) the robot station builds its pedestal in [RobotStationModel](../src/main/java/buildcraft/robotics/client/model/RobotStationModel.java) — Java, but NOT parametric; it lives in code because the baked pedestal and the per-frame reserved/linked overlay must share one geometry source (one asymmetric texel would make any mismatch visible), so migrating it to JSON means feeding the dynamic renderer from the same parsed model, not just moving the numbers. Migrate (a) and (b) to standard vanilla block-model JSONs loaded through the normal resource manager, with one generic rotating baker that pulls BakedQuads off a face and rotates them per `KeyPlug*.side`; fold (d) in only with the shared-source constraint preserved. Wins: resourcepacks can reshape *and* retexture any static plug with stock Minecraft JSON, the `models/plugs/` directory and `ModelHolderStatic` go away (verify nothing else uses the latter first), and surviving Java bakers become clearly-exceptional parametric cases. Tradeoff: non-trivial refactor across transport + silicon; per-element `shade` / extended UV tricks in the BC dialect need a NeoForge model-extension equivalent or to be dropped.

## Fluid atlas de-duplication

The on-disk fluid textures are de-duped (one `heat_still`/`heat_flow` base, recolored at stitch time by [FluidLerpSpriteSource](../src/main/java/buildcraft/lib/client/sprite/FluidLerpSpriteSource.java)), but the stitched atlas still holds 60 fluid sprites in 20 pixel-identical triplets — the 3 heat tiers of each fluid+frame are separate sprites only because MC ties animation `frametime` (3/2/1, the hot-vs-cool speed cue) to the `SpriteContents` itself, so identical pixels at different speeds must be different sprites. True dedup needs a custom fluid renderer that frame-steps a single shared sprite per fluid+frame at a heat-dependent rate, replacing the vanilla `FluidModel`/sprite-animation path; collapsing to one shared sprite without that would lose the per-heat speed difference. Low value — the redundancy is ~0.08% of the blocks atlas and ~3 MB RAM — so this is cleanup, not a fix.

## Waterlogging facades

Pipe holders are now waterloggable (`SimpleWaterloggedBlock` on `BlockPipeHolder`); facades and any other BC blocks with non-full collision shapes are still washed away by flowing water because they don't implement `LiquidBlockContainer`. Extend the same pattern (waterlogged block state + `SimpleWaterloggedBlock` + fluid-tick scheduling) to facades. Note this only protects against *water* — lava and oil still destroy these blocks (but a lava/oil-destroyed pipe now at least drops, via the `pipe_holder` loot table + `BlockPipeHolder#getDrops`).

## Electronic Library scrolling

Neither 1.12.2 nor the modern port wires up scrolling on the [GuiElectronicLibrary](../src/main/java/buildcraft/builders/gui/GuiElectronicLibrary.java) list panel. 1.12.2 lets the list overflow past the panel (drawing over the slots and player inventory); the port silently truncates at `LIST_MAX_ROWS = 13` and the texture has the arrows already in it. Add up/down arrow buttons (or scroll-wheel handling on the list rect) that page or pixel-scroll the visible window into the full snapshot list, persist the scroll offset across `containerTick` while the GUI stays open, and disable each arrow when there's nothing to scroll to in that direction. Worth sourcing the arrow sprites from the existing GUI texture sheet rather than spawning a second resource.

## Fake player churn

Use `FakePlayerFactory.get` instead of `new FakePlayer(...)`. [BCCore.java:224/230/237](../src/main/java/buildcraft/core/BCCore.java) allocates a full `ServerPlayer` on *every* fake-player request, including the per-block `BlockUtil.canMachineBreak` check in quarry/mining-well/builder scan loops (default `minePlayerProtected=false` means that path is live). Pure GC churn; all five nodes. While there: the unowned fake-player `GameProfile` is defined twice with identical values ([BCCore.java:215-219](../src/main/java/buildcraft/core/BCCore.java) and [BlockUtil.java:142-145](../src/main/java/buildcraft/lib/misc/BlockUtil.java)) — collapse to one constant.

## Dead AddSectionGeometryEvent remnants

`PipeModelCacheAll.getTranslucentMutableModel` has no callers and its javadoc claims BC renders through `AddSectionGeometryEvent`, which is false and actively misleading (BC never used the event; pipe translucent overlays go through the ordinary block-model pipeline). Check whether `IPipeBaseModelGen.generateTranslucentMutable` can go with it — its override is still reached from `PipeBaseModelGenStandard`, so only the cache-level entry point is cleanly dead.

## Deprecated FluidUtil helper

Migrate off deprecated `FluidUtil.getFluidContained`. [PipeBehaviourWoodDiamond.java:230](../src/main/java/buildcraft/transport/pipe/behaviour/PipeBehaviourWoodDiamond.java) calls `net.neoforged.neoforge.fluids.FluidUtil.getFluidContained(ItemStack)`, which NeoForge has marked deprecated-for-removal (2 build warnings surfaced on the 26.2 node). Swap to the fluid-handler item capability (`Capabilities.FluidHandler.ITEM` → `getFluidInTank(0)`) before NeoForge drops the helper.

## Gradle configuration cache

Enable it project-wide. Already clean on the active/primary/1.21.10/1.21.11 nodes (verified via `--configuration-cache`, 0 problems on compileJava/test/build/runClient-config); the sole blocker is the 1.21.1 node's `processResources`/`processTestResources` `doLast` blocks at [build.gradle.kts:319](../build.gradle.kts) — they call build-script methods (`downportRecipeDir1211`/`downportAdvancementBackground1211`/`generateOldItemModels1211` + callees `convertRecipe1211`/`convIngredient1211`/`stripCustomModelData`), which Gradle can't serialize ("cannot serialize Gradle script object references" → `this$0` NPE). Fix = move those ~6 helpers into `buildSrc/` (no behavior change), then add `org.gradle.configuration-cache=true`. Worth a further ~30% on warm no-op builds (~1.7s → ~1.3s) on top of the daemon re-enable.

## REI recompile

Re-compile-verify `compat/rei` when a 26.1-compatible REI ships. The whole REI tree is excluded from compilation (`build.gradle.kts` unconditional exclude; dependency commented out — "no compatible versions for MC 26.1 yet"), so the 2026-07 `ReiCraftingTableSupport` dedup is best-effort-unverified, and the old plugins had already rotted while frozen (called non-existent `getLeftPos`/`getTopPos` — fixed to `getGuiLeft`/`getGuiTop` in the rewrite). Expect `registerClickArea`/`DraggableStackVisitor`/`DraggingContext` API drift too.

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

## Quick notes

One-line technical breadcrumbs for bullets that don't need a full section:

- **Facade smooth shading** — AO infrastructure already exists in `MutableQuad`; needs facade-specific wiring.
- **Filler mode icons** — the old `filling/` selector-icon set archived at `misc/unused_textures/filling/` is stale erroneous leftover, not a basis for the new art.
- **Goggles texture** — `item/goggles.png` is a verbatim copy of the vanilla paper texture; the headpiece stays dev-gated until real art exists.
- **Cauldron as tank** — NeoForge already exposes `IFluidHandler` capabilities for cauldrons; wire that into BC's fluid pipe connection logic (drain water/lava/powder-snow, fill to the appropriate level).
- **Builder can't-place flags** — e.g. a red box overlay on the resource list or the build area for blocks that can't be placed yet (floating torches, flowers on stone).
- **Fluid viscosity (blocked)** — flow speed is already moddable, but negative density (floating gases) is not native and traversal/swimming modifications aren't possible.
