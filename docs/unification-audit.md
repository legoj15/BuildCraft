# BuildCraft Unification Audit — Synthesis Report

**Scope:** `src/main/java/buildcraft/` (single mod, id `buildcraftunofficial`), MC 26.1.x active node, Stonecutter multi-version source tree.
**Input:** 68 individually-verified findings, deduped and clustered into 18 themes below.
**Bottom line:** One architectural split (block-entity base) drives most of the duplication. Almost nothing is a live bug — but the duplication *hides* two real ones. The right play is quick wins + the two bug fixes now, then a staged base-class consolidation, leaving the genuinely load-bearing divergence alone.

---

## How to read this report

Every finding was read against the source and adversarially checked; the cited file:line references are verified. Where a divergence is **load-bearing** (Stonecutter version directives, client/server safety, deliberate per-git, COMMON/CLIENT config scoping, the statement registry, VolumeBox's richer model) it is called out as **JUSTIFIED — do not "fix"**. Severities reflect the corrected post-verification values, not the raw finding labels (most "high"s were downgraded once confirmed latent rather than active).

---

## THE HEADLINE SPLIT — Block-entity base classes

> **Doc/reality gap:** CLAUDE.md:75 states *"All block entities extend `TileBC_Neptune`."* This is **false** for ~10 tiles: `TileTank`, `TileHeatExchange`, `TileDistiller_BC8`, `TileFloodGate`, `TileSpringOil`, `TileLaser`, `TilePipeHolder`, `TileMarker`, and `TileEngineBase_BC8` (+ all engine subclasses + `TileDynamoMJ`) extend vanilla `BlockEntity` directly. Each carries a self-documenting *"Platform bridge … extends BlockEntity directly (not TileBC_Neptune)"* comment — the authors knew, but the top-level doc was never updated.

### Why these tiles diverge (JUSTIFIED at the hierarchy level)
Engines store owner differently and have their own abstract hierarchy; pipes use a `CompoundTag`-based save cache; markers have their own cache load/unload lifecycle; the fluid tiles need bespoke column/balancing/client-sync logic. Forcing them onto `TileBC_Neptune` would drag in unused item-handler/owner/player-tracking machinery. **So skipping the heavyweight base is legitimate.**

### What is NOT justified (the actual finding)
Because there is *no intermediate base* between `BlockEntity` and these tiles, the same concerns are re-copied per file:

| Concern | Canonical home | Copies in raw tiles | Status |
|---|---|---|---|
| `//? if >=1.21.10` save/load directive → `writeData`/`readData` | `TileBC_Neptune.java:108-132` | **10** (byte-identical; each comment says "see TileBC_Neptune for the rationale") | Unjustified |
| Owner `GameProfile` NBT (`ownerUUID`/`ownerName`) | `TileBC_Neptune.java:81-156` | **4** (engine/floodgate/distiller + base) | Unjustified + has a bug |
| `getUpdateTag`/`getUpdatePacket` + `<1.21.10 onDataPacket` compat | `TileBC_Neptune.java:227-250` | 7 (the `onDataPacket` fix only on 2 of them) | Unjustified + latent bug |
| Non-player-removal drop hook (`dropsHandled`/`preRemoveSideEffects`) | `TileBC_Neptune.java:178-218` | 4 fluid tiles (field-name `contentsDropped` vs `dropsHandled`) | Justified-within-structure |
| Player-tracking GUI coupling (`ContainerBCTile<T extends TileBC_Neptune>`) | `ContainerBCTile.java:14` | raw GUIs re-roll `stillValid` 4x | Justified bound (latent) |

`TileSpringOil.java:61-118` is the worst offender — it inlines `BCValueInput`/`BCValueOutput` construction *inside* the directive in both load and save arms, with no `writeData`/`readData` hook at all.

### Two latent bugs hiding in this duplication — FIX NOW (severity: high)

1. **Engine owner-loss.** `TileEngineBase_BC8.onPlacedBy` (line 166-170) sets `owner` but omits the `setChanged()` call that `TileBC_Neptune.onPlacedBy` deliberately added (commit 9176336eb, with a chunk-dirty comment). An engine placed and never otherwise mutated before chunk unload can lose its owner. Narrow window (engines self-dirty during ticking) but real. The signature also gratuitously forked to `(LivingEntity)` vs the base's `(LivingEntity, ItemStack)`.
2. **RF kinesis invisible flow.** Covered under Transport below — same root cause (copied sync code that didn't get a later fix).

### Recommended unified approach
A **thin `AbstractBCBlockEntity` intermediary** in `lib.tile` carrying *only*:
- the save/load Stonecutter directive + empty `writeData`/`readData` hooks,
- the `getUpdateTag`/`getUpdatePacket` + `<1.21.10 onDataPacket` sync pair,
- an `OwnerData` helper (`writeTo`/`readFrom` + a static `onPlacedBy` that calls `setChanged()`).

Both `TileBC_Neptune` and the ~10 raw tiles extend/use it. This collapses 10 directive copies → 1, the owner block → 1, fixes the engine bug structurally, retroactively protects the 5 raw tiles currently missing the `onDataPacket` fix on the 1.21.1 node, and drags **no** item/owner/player machinery into engines or pipes. NBT keys are unchanged → byte-identical persistence, no migration.

**Risk:** Stonecutter node-switch churn — do the refactor on the active node, then `git checkout -- src/`, then verify. The Java-interface-default alternative (one finding suggested `IBCSerializableTile`) **will not compile** — interface defaults cannot `super`-call `BlockEntity.saveAdditional`; the abstract-intermediary path is the correct one.

---

## Block base classes & block-side boilerplate

**Severity: medium · Effort: large (base) / small (DynamoMJ)**

No shared BC block base exists (`lib.block.BlockBCBase_Neptune` is a 2-line empty stub). ~14 GUI-machine blocks repeat the same six-method skeleton (`codec`/`newBlockEntity`/`getTicker`/`setPlacedBy`/`useWithoutItem`/`playerWillDestroy`). `BlockArchitectTable` (`:33-130`) and `BlockElectronicLibrary` (`:33-125`) are line-for-line identical bar the tile type. Per-copy drift is demonstrable: `setPlacedBy` is client-guarded in some blocks, unguarded in others (latent, not active — `onPlacedBy` only acts for a `Player`).

- **`BlockDynamoMJ` re-implements `BlockEngineBase_BC8` verbatim** (`:43-75`) — ~40 lines of facing/shape/ticker/rotation copied, while its *tile* `TileDynamoMJ` correctly extends `TileEngineBase_BC8`. `BlockEngineFE` is the existence proof that extending the base + overriding `newBlockEntity`/`useItemOn` works. **Quick-ish win.**
- The `//? if <1.21.10 onRemove` drop catch-all is duplicated across **15** blocks (finding under-counted as ~10). 14 collapse to one base override; **`BlockPipeHolder` is the exception** — it calls `dropPipeCargo`, not `dropContentsOnRemoval`, so it cannot fold in.
- `getTicker` is spelled two ways (inline lambda + manual guard vs `BaseEntityBlock.createTickerHelper`); the split is **partly forced** by the `Block`-vs-`BaseEntityBlock` parent choice (`createTickerHelper` is `protected` on `BaseEntityBlock`).

**Constraint (JUSTIFIED):** blocks split across `HorizontalDirectionalBlock` and `BaseEntityBlock` — one base can't cover both, so a `BlockBCTile_Neptune<T>` plus a facing-flavoured variant is needed. Keep the Stonecutter directives *inside* the base so they live in one place.

---

## GUI / Container / Menu

**Severity: medium → low · Effort: small (helpers) / large (re-root)**

`ContainerBCTile<T extends TileBC_Neptune>` cannot bind to the raw tiles, so `ContainerTank`/`ContainerDistiller`/`ContainerHeatExchange`/`ContainerEngine*`/`ContainerDynamoMJ` extend `ContainerBC_Neptune` directly and each re-roll (a) a static `getTile(Inventory, FriendlyByteBuf)` and (b) a `distanceToSqr(...) <= 64.0` `stillValid`. **The guards have already drifted** — the factory three use a `getBlockEntity(pos) != tile` staleness check; the engine four use `isRemoved()`.

- **Quick win:** extract `withinReach(Player, BlockPos)` + a generic `resolveTile(Inventory, FriendlyByteBuf, Class<T>)` into `ContainerBC_Neptune`. Zero risk; unifying onto `canInteractWith`'s logic actually *strengthens* the engine variants. `TileBC_Neptune.usingPlayers` is **dead state** — never iterated — so the "raw tiles miss player tracking" concern is mostly inert; the real value is killing the 4x-duplicated distance check.
- **Two container→screen sync mechanisms** (vanilla `ContainerData` for ints vs BC `Widget_Neptune`/`sendMessage` for fluids/longs/variable payloads) **coexist for good reason — do NOT collapse.** `ContainerData` is int-only (the engines hi/lo-split power *because* of this); fluids/longs genuinely can't ride it. **Action: one-line javadoc convention** in `ContainerBC_Neptune` documenting the boundary.

---

## NBT serialization idioms

**Severity: low · Effort: medium · Partly JUSTIFIED**

The object↔NBT concept is spelled ≥5 ways: `writeData/readData` (tiles), `writeToNbt/readFromNbt` (transport/lib), `writeToNBT/readFromNBT` (robotics api), `serializeNBT/deserializeNBT` (schematics/MjBattery), vanilla `save/load` (SavedData). Read side has 3 shapes (instance / static factory / `CompoundTag` constructor). Clusters map cleanly to the old 1.12.2 submodules — history, not design.

- **Internal** (non-api) renames to one casing (`writeToNbt`/`readFromNbt`) and one read shape are mechanically IDE-safe **now**.
- **`buildcraft.api` uppercase-NBT + `serializeNBT` renames are a breaking change** if the API-jar redistribution ever ships — **DEFER** until that decision (per the demand-gated `project_api_redistribution` plan).
- Leave vanilla `SavedData.save/load` alone (MC-imposed).

---

## Energy / MJ power

**Severity: medium · Effort: medium**

- **MJ-consumer scaffolding copy-pasted across ~10 tiles** (`battery` + `mjReceiver` + accessors) and **registered 3 caps per machine** across BCFactory/BCSilicon. This duplication caused a **real functional gap**: `BCBuilders` registers only `CAP_RECEIVER + energyCap` for QUARRY/FILLER/BUILDER and **omits `CAP_CONNECTOR`** (unlike every factory/silicon machine) — so those machines may not advertise as MJ connectors to pipes. **Fix the registration now; extract an `MjBatteryComponent` + `registerMjConsumer` helper** (with override hooks for the quarry's gated receiver and AutoWorkbench's redstone receiver).
- **Engine chain-walk duplicated** between `TileEngineBase_BC8.getReceiverToPower` and `TileDynamoMJ.getFeReceiver` (byte-identical loop, different terminus). Extract a generic `walkChain(side, resolver)` helper.
- `TileDynamoMJ` extends `TileEngineBase_BC8` but **subverts the entire MJ-transfer contract** (no-op `sendPower`, sentinel `extractPower`, dummy receiver) — deliberate scaffolding reuse, works, gametested. **Recommend documenting the subversion loudly + sharing the chain-walk; do NOT split the engine base** (regression risk to tuned overheat/piston behaviour).
- Soft-start throttle (3 copies) and MJ↔RF arithmetic (4 copies) are lower-value: the formulas **intentionally differ** (the quarry's BigInteger guard is needed at 24000-MJ capacity; the two tiles deliberately discard the fraction as a per-tick rate). Extract only the trivial multiply/divide/zero-guard primitive; the claimed overflow bug does **not** exist at current tuning.
- **`CAP_PASSIVE_PROVIDER` is declared + consumed but never registered**, and `PipeFlowPower.tryExtractPower` is never called — wooden-pipe MJ pull is **dead/non-functional**, and lacks the FE-autoconvert fallback the engine path has. `CAP_READABLE` has the same shape. **Decide: wire it (register on sources + add the FE fallback) or delete it** — a "completeness audit" commit (791e578ef) claimed it done but never finished it.

---

## Fluid handling

**Severity: medium · Effort: medium**

- **Fluid-box render kit triplicated** (~250 lines) across `RenderTank`/`RenderDistiller`/`RenderHeatExchange` — identical `quad`/`quadHorizontal`/`posU`/`posV`, ABGR unpack, sprite/RenderType selection, 6-face emission (a 4th near-copy as `drawFlowBox`). The class meant to own this — `lib.client.render.fluid.FluidRenderer` — is a **dead 2-line stub**. Lift the primitives into it; **client→client, safe, but REQUIRES in-client visual verification** (compile can't catch UV/winding regressions). Face-visibility differs per machine → expose flags.
- **Restricted fill-only/drain-only wrappers** re-rolled inline (pump extract-only, distiller fill+drain, ×2 for the version branch). A `RestrictedFluidHandler.insertOnly/extractOnly` helper collapses the pump+distiller copies. (The engine's 3-tank multiplexer is a different shape — leave it or target `MultiTankResourceHandler` if touched.)
- **`MultiTankResourceHandler` is dead** (uninstantiated; wraps classic `FluidTank[]`, not the `BCFluidTank[]` the live tiles use). The inline fan-outs in `TileBuilder`/`TileEngineIron` are correct as-is (they delegate to `BCFluidTank`'s own transactions). **Delete it + the unused `BCEnergy.java:19` import.**

---

## Transport pipes — internal

**Severity: medium · Effort: large (flow base) / small (behaviours)**

- **`PipeFlowPower` and `PipeFlowRedstoneFlux` are near-clone distribution engines** (564/622 lines, line-for-line identical bar `long` MJ vs `int` FE + capability + RF's `queryEnergyDemand`). **They have already drifted into a bug:** `PipeFlowPower` persists `displayPower`/`displayFlow` and resyncs via `readFromNbt` (the fix for invisible straight-pipe flow, 11ad9fa1d), but **`PipeFlowRedstoneFlux.writeToNbt` persists only `isReceiver` and has no `readFromNbt`** — so RF flow is invisible on straight pipes exactly as the MJ bug was. **FIX THE RF NBT NOW (quick win); extract the generic flow base second** (preserve the Stonecutter seams + PipeFlowPower's intentionally-inert `powerLoss` scaffolding).
- **Speed pipes** (Stone/Cobble/Quartz/Gold/Sandstone) duplicate a `modifySpeed` handler + 2 constants — but split across two superclasses, so favour a composed helper/instance handler over a base. **Watch `PipeEventBus` static-vs-instance handler discovery** (it walks the superclass chain and binds instances, so an inherited instance handler is discovered — a static base handler would be wrong).
- **`PipeBehaviourLapis`/`PipeBehaviourDaizuli`** duplicate the whole colour-state mechanism (~50 lines: field, NBT, payload, wrench-cycle, paint actions). Extract a composed `ColourState` helper (not a base — different parents); keep the `@PipeEventHandler` methods on the concrete classes.
- **`WoodDiamond` uses legacy `FluidUtil.getFluidContained` unconditionally** while `DiamondFluid` uses the directive-gated Transfer API for the same "fluid in this item" lookup — accidental drift from a partial migration. Factor one shared directive-gated helper.
- **MJ-receiver cap exposure** repeated across Wood/Obsidian/Stripes — low payoff; a `default` method on `IMjRedstoneReceiver` (not a base — parent conflict) is the right vehicle if touched.

---

## Builders snapshot / blueprint — internal

**Severity: medium · Effort: medium**

- **`SchematicBlock*` vs `SchematicEntity*` is a verbatim parallel system** — managers, factory holders, and registries are copy-paste twins differing only in type + the registry matcher + **throw-vs-null on-miss policy** (the latter is load-bearing: block scan must produce a schematic; entity scan legitimately skips unknown entities). Genericize to `SchematicManager<C,S>`/`Factory`/`Registry` with **thin api-package facade subclasses** (preserve public signatures for addon compat). Start with the **non-api** manager NBT codec (zero addon risk; highest-confidence pure copy-paste).
- **Snapshot payloads** duplicate the compressed-NBT codec (`NbtIo.writeCompressed`/`readCompressed`) and the client request/reply cache. Real count is **2-way** (`ClientSnapshots` + `ClientArchitectPreviews`), not 3 — `ClientArchitectScans` is a fade timer, not request/reply. Extract a `writeCompressedSnapshot`/`readCompressedSnapshot` helper + a `ClientRequestCache<K,V>` centralising the version-gated send directive.
- **`TileBuilder`/`TileFiller` merge client-task NBT two ways** — `TileFiller` inlines what `SnapshotBuilder.loadClientNBT` already encapsulates (a missed extraction, per commit 4ef960553). **Quick win:** point `TileFiller` at `loadClientNBT` directly (its `builder` is non-wildcard, so no cast needed).
- **`ItemStackRef`/`FluidStackRef`** duplicate the JSON-NBT-ref resolve shape; extract a generic resolver. `ItemStackRef`'s `tagCompound` branch is an empty TODO that silently drops captured components — currently **dead in shipped data** (no rule supplies a tag), so flag as a tracked follow-up, don't change semantics silently.

---

## Registration boilerplate

**Severity: medium · Effort: large**

- **No block+item+BE triple helper** — each of ~19 machines is declared in 3 files with the id string hand-typed multiple times. **The desync trap is already realized:** `BCEnergyBlocks` and `BCEnergyBlockEntities` *independently* type the literal `"engine_rf"` for the field named `ENGINE_FE`. Add an **opt-in** `registerMachine(...)` bundle helper alongside `RegistrationUtilBC` (pilot factory/builders/energy; leave silicon's menu-factory/dev-gated objects hand-rolled).
- **~50 hand-written pipe-item registrations** in `BCTransportItems.java:70-215`, all the same 3-line incantation. The `WIRE_ITEMS` EnumMap loop a few lines above (`:55-64`) is the in-file precedent. **Constraint:** the named public `DeferredItem` fields are referenced widely (recipes/guide/creative-tabs/tests), so keep the fields and factor the lambda into one private `pipe(id, def)` helper — do **not** collapse to a map-only form.
- **`registerSimpleBlockItem` called inconsistently** — `BCCore`/`BCBuilders` pass a redundant id string; the rest use the holder-only form (which derives the id from the block). Standardize on holder-only (drops a latent typo footgun). Leave `BCEnergyItems.ENGINE_FE` alone (custom BlockItem subclass).

---

## Marker / volume / zone systems

**Severity: medium · Effort: large · Partly JUSTIFIED**

- **`PathSavedData`/`VolumeSavedData` are copy-paste twins** (CODEC, `syncFromSubCache`, the cross-node SavedData directive, `getOrCreate` — identical bar type name + id). Collapse into a generic `MarkerConnectionSavedData<C,S>` base; the empty `lib.marker.MarkerSavedData` stub was clearly meant for this. (Each subclass keeps its own `TYPE` field — the directive block — since Java can't host per-subclass statics on a generic base.)
- **Two parallel box-region systems:** `VolumeConnection` (marker-framework, incremental `MessageMarker` sync) vs `VolumeBox` (self-rolled `LevelSavedDataVolumeBoxes` + a wasteful **full-replace** `MessageVolumeBoxes` on every change). Both define an AABB region; both driven by the same item. **VolumeBox's editing/addon/lock model is genuinely richer — do NOT force it onto MarkerConnection.** Safe actions: fix `MessageVolumeBoxes` to send deltas, OR make a **product decision** (todos.md) on whether both systems should ship. Retiring `VolumeConnection` is player-facing + save-compat (`TileQuarry` still reads `TileMarkerVolume`) — not a silent refactor.
- **`MarkerRenderer` holds a `volumeBoxRenderCallback` + a "reflection-free … BCCore module calls this via the public API" comment** — justifying indirection by a **module boundary that does not exist** in this single mod. Both classes are client-only render code; lib already imports `buildcraft.core` elsewhere. **Drop the callback fields/setters and call `VolumeBoxRenderer` directly.** (This is exactly the "ModList/reflection guard between BC packages = a finding" pattern the audit was told to flag.)

---

## Inventory & item-movement

**Severity: medium · Effort: medium**

- **`TileBuilder` hand-rolls ~210 lines** (a full `IItemHandler` pipe adapter + an `IItemTransactor` re-implementing the two-pass insert / cross-slot extract already in `AbstractInvItemTransactor`) over a raw `NonNullList`, while its sibling **`TileFiller` gets the identical job from one `ItemHandlerSimple` + `addInvHandler` call.** A stale in-code comment falsely claims "the 26.1 port hasn't landed a replacement for `ItemHandlerSimple` yet" — it has, and the sibling uses it. **Back `invResources` with `ItemHandlerSimple`** (needs a one-time `invRes_N` NBT migration + rewiring the cache callback to `onResourcesChanged()`; `EnumAccess.INSERT` expresses the "pipes push in, can't pull out" rule).
- **"Shuffle 6 faces, insert into adjacent inventory" triplicated** (`InventoryUtil.addToRandomInventory` canonical; `TileChute` + `TileFiller` inline it with their own copied `//? if >=1.21.10` Transfer-API blocks). The behavioural params (skip-dir, IPipeHolder-skip, 1-item-vs-stack) are real — **generalize the canonical helper with those as parameters** so the version directive lives once.
- **`PipeFlowItems` TILE-insert uses bare `insert()`** (plain index order) while every other BC ejector — and its own `<1.21.10` branch — uses stacking insert. **Quick win:** switch to `ResourceHandlerUtil.insertStacking` for parity (vanilla chests accept either; only stack fragmentation differs).

---

## Statements / triggers / gates

**Severity: low · Effort: medium**

- **Inventory vs fluid "container content" triggers re-roll the same scan loop 4x** and declare `State{EMPTY,CONTAINS,SPACE,FULL}` + `TriggerType{BELOW25/50/75}` **twice, byte-identical**. Lift into a generic `AbstractContainerContentTrigger<R>` with per-type probe hooks (each keeping its own `//? if >=1.21.10` directive — do NOT fuse into one `if(isFluid)` method). The cited `size()==1` caveat is already centralised in `CoreTriggerProvider`, not duplicated.
- **`StatementParameterItemStackExact` is dead** — never registered, only its own gametest references it; `getParameterReader("buildcraft:stackExact")` returns null (a latent footgun if anything ever emitted one). It was left half-wired by the same "completeness audit" commit. **Delete-or-finish.**
- **Two fluid triggers share the `buildcraft:fluid.` prefix** (the inventory pair correctly uses distinct prefixes). Non-colliding today (disjoint enum names) but a future `FULL`-style level value would silently overwrite. Rename to `buildcraft:fluidlevel.` with a legacy alias (BCStatement varargs already supports this) — best bundled with the trigger-base unification.
- **Silicon redundantly re-registers 7 already-self-registered statements** (`BCStatement` ctor self-registers; silicon is the only module that *also* calls `registerStatement`). **Quick win: delete the 7 calls.**
- **JUSTIFIED — do not change:** the statement system is a hand-rolled static `StatementManager` registry, NOT `DeferredRegister`. That is correct (statements aren't registry content). Add a one-line CLAUDE.md carve-out.

---

## Config system

**Severity: low · Effort: small**

- **`DetailedConfigOption` is a fake config** — its `getAsFloat()` parses the default String literal and never reads any file. Its one live use makes `render.pipe.misc.inside.shade` *look* tunable while being a hard-coded `0.725f`. **Inline the constant at `PipeBaseModelGenStandard.java:377` and delete the class** (client-only render code — do NOT route through the COMMON spec).
- **Three empty stub classes** (`ConfigUtil`, `RegistryConfig`, `EnumRestartRequirement` — the last an enum with zero constants) — **delete.**
- **Orphan lang key `bptStoreExternalThreshold`** (en_us.json:666) whose option was removed in commit 5129e2ae0 — **delete the line.**
- **7 near-identical `list→Set<Identifier>` getters** in `BCEnergyConfig.java:262-288` — extract one `toIdSet` helper.
- **`BCEnergyConfig` straddles COMMON/WORLDGEN/CLIENT scopes** and the CLIENT display section aggregates two subsystem classes. This is **JUSTIFIED** (COMMON/CLIENT split is correct; worldgen-with-energy is sensible). **Doc fix only:** CLAUDE.md still says "Single BCUnifiedConfig wraps all config" — there are now two specs (`BCUnifiedConfig` COMMON + `BCUnifiedClientConfig` CLIENT).

---

## Dead / stale code sweep

**Severity: low · Effort: small · all zero-reference, safe to delete**

| Item | Location | Note |
|---|---|---|
| `SingleUseTank`, `TankProperties` | `lib/fluid/` | empty stubs; `TankProperties`' old interface no longer exists |
| `ItemTransactorHelper`, `SidedInventoryWrapper`, `TransactorEntityItem`, `TransactorEntityArrow` | `lib/inventory/` | functionality relocated to `InventoryUtil` |
| `IInvSlot` | `api/core/` | orphaned 1.12.2 interface, no implementors |
| `BlockBCBase_Neptune` | `lib/block/` | 2-line empty placeholder for the never-written block base |
| `TUBE_LASER` fields + unused imports | `RenderPump`, `RenderMiningWell` | `TubeRenderer` is the live owner |
| `MultiTankResourceHandler` + `BCEnergy.java:19` import | `lib/misc/`, `energy/` | uninstantiated |
| `lib.net.MessageManager` | `lib/net/` | empty stub; doc implies a framework that doesn't exist |
| Unused `import net.minecraft.resources.Identifier;` | **116 files** project-wide (26 in lib/misc) | residue of the global ResourceLocation→Identifier rename; run an organize-imports sweep on a quiet tree |
| `VecUtil.scale/dot/convertFloor/convertCenter`, `MathUtil.interp` | `lib/misc/` | 1:1 forwarders of vanilla 26.1 APIs (`Vec3.scale/dot`, `BlockPos.containing`, `Vec3.atCenterOf`, `Mth.lerp`) — inline opportunistically only; keep `clamp/min/max/getValue` |
| `RenderUtil.swapARGBforABGR` | `lib/misc/` | byte-identical to `ColourUtil.swapArgbToAbgr`; redirect 2 call sites |

**Needs a decision, not blind deletion:** `CAP_PASSIVE_PROVIDER` + `CAP_READABLE` (declared + consumed, never registered — see Energy/MJ).

---

## Consolidated DOC/REALITY gaps in CLAUDE.md

Three stale claims actively mislead future agents into mis-modeling the architecture or "fixing" load-bearing divergence:

1. **CLAUDE.md:75** — *"All block entities extend `TileBC_Neptune`."* False for ~10 tiles. → "most; a documented set extend vanilla `BlockEntity` and re-implement the BC idioms by hand (and note the absence of a shared block base)."
2. **CLAUDE.md (Config)** — *"Single `BCUnifiedConfig` wraps all per-subsystem configs into one `ModConfigSpec`."* There are now **two** specs (COMMON `BCUnifiedConfig` + CLIENT `BCUnifiedClientConfig`).
3. **CLAUDE.md (Statements)** — implies statements ride the "always `DeferredRegister`" convention. They use the static `StatementManager` registry; `BCStatement` self-registers in its constructor. Add a carve-out.

Also worth a line: the networking story omits the BC generic-tunnel payload style (`MessageContainerPayload`/`MessagePipePayload` carrying an `IPayloadWriter`/`IWriter` callback) alongside the dedicated payload records, and `lib.net.MessageManager` is a dead stub.

---

## Prioritized plan

### Do now (quick wins + the two real bugs)
The 16 items in the quick-wins list — most importantly the **engine `setChanged()` owner-loss fix**, the **RF kinesis invisible-flow NBT fix**, the **missing `CAP_CONNECTOR` for builders machines**, the **three CLAUDE.md doc fixes**, and the dead-code deletions. All small, safe, high signal-to-noise.

### Stage deliberately (large refactors, in order)
1. **Thin `AbstractBCBlockEntity` intermediary + `OwnerData` helper** — foundational; unblocks the container and networking dedups and fixes the engine bug structurally.
2. **`BlockBCTile_Neptune` base** + make `BlockDynamoMJ` extend `BlockEngineBase_BC8`.
3. **`MjBatteryComponent` + `registerMjConsumer`**, then the engine chain-walk extraction.
4. **Generic schematic system** (non-api manager NBT codec first).
5. **Generic kinesis flow base** (after the RF bug is already fixed).
6. **Registration bundle helper + table-driven pipe items.**
7. **Fluid-box `FluidRenderer`** (with mandatory in-client verification).
8. **`TileBuilder` → `ItemHandlerSimple`** (with NBT migration).

### Hold for a product/architecture decision
- VolumeConnection vs VolumeBox convergence (player-facing + save-compat).
- `CAP_PASSIVE_PROVIDER`/`CAP_READABLE`: wire or delete.
- `buildcraft.api` NBT-method renames: wait for the API-jar redistribution call.

### Do NOT touch (load-bearing — flagged so they aren't "fixed")
Stonecutter version directives anywhere; the COMMON/CLIENT config split; the `StatementManager` static registry; the two container→screen sync mechanisms; VolumeBox's editing/addon/lock model; `PipeFlowPower`'s intentionally-inert `powerLoss` scaffolding; `TileDynamoMJ`'s deliberate engine-base scaffolding reuse; the raw-tiles' choice to skip the heavyweight `TileBC_Neptune` (only the *re-copying* is the problem, not the choice).

> **Risk posture:** the mod is mid-major-version (robots revival in flight). None of the staged refactors are urgent; none should be done as churn-for-churn. Sequence them so each lands green and committed before the next (the memory note on a subagent wiping ~50 files via `git checkout` is a real hazard — commit green states immediately, never node-switch on uncommitted work).

---

## Duality Catalog (follow-up sweep)

This sweep re-read and count-verified 59 candidate forks across nine axes (tile-base, block-base, menu/container, screen/GUI, networking, capability exposure, item-movement, MJ/energy, render, recipes/JEI, config/registration, markers/schematic/flow) plus a dedicated completeness critic. It deliberately reverses the first audit's mistake of downweighting "justified" forks: a fork that is justified *only* because another fixable split forces it is the **highest**-value unification, because one root fix collapses several forks at once.

### Headline

- **~50 distinct forks** remain after de-duping cross-axis views of the same split (e.g. the menu-bind fork and the server-side open-path fork are two faces of one tile-base split).
- **300+ class memberships** live in two-or-more parallel systems.
- **~18 forks collapse from just 5 fixable roots.** The biggest single root — finishing the `AbstractBCBlockEntity` tile re-root and re-rooting `ContainerBCTile`'s generic bound onto it — collapses **8** forks by itself.
- The completeness sweep surfaced the catalog's **largest population** (33 wrapper-serialized tiles vs **62** raw-`CompoundTag` non-BE objects) and a **previously-unnamed** soft-start power-ramp duality (5 sites, 3 divergent formulas).

### Root-cause map (the key artifact)

| Root fix | Forks it collapses |
|---|---|
| **Complete the `AbstractBCBlockEntity` re-root** (migrate `TileSpringOil`) **+ re-root `ContainerBCTile`'s bound** onto it + have raw-tile machines adopt `IBCMenuProvider`/shared sync/`setPlacedBy` | Menu/container bind (7 of 13 collapse); spectator-safe open path (5); container-open-call view (5 + DynamoMJ double-write); standard sync pair (7 copies); 1.21.1 `onDataPacket` workaround; `setPlacedBy` forward (17); item-cap engine fuel (System C); render-state empty stubs (10→1); partially the non-BE NBT serialization tail |
| **Add a `markForRenderUpdate()` push helper to `AbstractBCBlockEntity`** (calls `sendUpdateToTrackingPlayers`, no re-mesh) | Tile client-state push fork (~11 `sendBlockUpdated` sites move off the re-mesh path) |
| **Route MJ-battery receivers through `MjBatteryReceiver` + migrate `TileAutoWorkbenchBase.powerStored` to a real `MjBattery`** | MJ receiver-over-battery (MiningWell + Stripes inline copies); non-tile pipe-layer receiver duplication; FE-exposure gap (Auto Workbench gains the Energy cap for free) |
| **Flesh out the empty `FluidRenderer` stub into the shared quad-kit** | Fluid-box quad emitter (3 private copies → 1) |
| **Extract one `lib.client.model` block-quad helper** (the `collectParts` directive ladder) | Baked-quad extraction (3 copies; two byte-identical) |
| **Make the `BCTransportPipes` `PipeDefinition` set iterable** | Pipe BlockItem registration (46 hand-written lines → 1 loop) |
| **Generic-flow base for the kinesis power clone** (`AbstractPipeFlowPower<E>` + renderer) | Power-flow distribution + renderer clone (also fixes the FE overload invisible-stem bug) |
| **Leaf-level standalone cleanups** (no shared root) | LedgerEngine/EngineFE 1-line drift; `TileSpringOil` directive; 5 redundant `getUpdateTag` overrides; 5 dead `lib.net` stubs + 5 empty `lib.gui.recipe` stubs; `DetailedConfigOption`; duplicate JEI plugin UID (bug); duplicate `NET_JEI_RECIPE_TRANSFER` constant; 7 redundant `registerStatement` calls; `mjPerRf` re-parse; 2 duplicate REI plugins; marker SavedData/cache boilerplate; schematic block/entity twins; **the newly-found soft-start ramp duality** |

### Ranked catalog (by systemicness: even split × large population × player-invisible = highest)

**High systemicness**
1. **BE base** — 16 `TileBC_Neptune` / 14 `AbstractBCBlockEntity`-direct / 1 raw vanilla. *Load-bearing* (A/B real semantics; C drift). The ROOT under most of the catalog.
2. **Menu/container bind** — 12 `ContainerBCTile` / 13 `ContainerBC_Neptune`-direct (7 tile-backed). *Collapses-when-root-fixed.*
3. **Spectator-safe menu open path** — 12 `IBCMenuProvider` / 5 lambda / 5 anonymous. *Collapses-when-root-fixed*, player-visible (spectator NPE).
4. **Non-BE NBT persistence** — 33 `writeData/readData` / **62** raw-`CompoundTag`. *Load-bearing* but the largest population in the catalog.
5. **Block-hosting base** — 11 `BaseEntityBlock` / 6 `HorizontalDirectionalBlock` / 5 plain `Block` / 7 BC-abstract. *Collapses-when-root-fixed.*
6. **getTicker** — 10 `createTickerHelper` / 10 hand-rolled lambda. *Collapses-when-root-fixed.*
7. **BE client-sync (sub-object addressing)** — 14 full-NBT / 19 id-delta `writePayload` (+ markers' own channel). *Load-bearing.*
8. **Tile client-state push** — 10 no-re-mesh / ~11 `sendBlockUpdated`. *Collapses-when-root-fixed*, player-visible (FPS near re-meshing tiles).
9. **`BlockEntityRenderState`** — 10 empty stubs / 1 correct (PipeHolder) / 2 PiP records. *Collapses-when-root-fixed.*
10. **Power-flow distribution** — `PipeFlowPower` / `PipeFlowRedstoneFlux`. *Load-bearing* (two energy type systems).
11. **Power-flow renderer** — `PipeFlowRendererPower` / `...FE`. *Collapses-when-root-fixed*; ACTIVE drift: FE overload shows invisible stems.
12. **Schematic block/entity pipeline** — 5 / 5 twins. *Pure-accidental-drift* (genericize).
13. **Tile inventory backing** — 11 `ItemHandlerSimple` / 1 hand-rolled (`TileBuilder`, ~205 lines). *Collapses-when-root-fixed.*
14. **Pipe BlockItem registration** — 46 hand-written / 1 wire loop. *Collapses-when-root-fixed.*

**Medium systemicness**
15. **`setPlacedBy` forward** — 17 hand-copies / 9 none (+ 3 unguarded vs 14 guarded). *Collapses-when-root-fixed.*
16. **Event-handler registration** — 2 `@EventBusSubscriber` / 14 `@SubscribeEvent` / 10 lambda. *Pure-accidental-drift* (1 BreakEventCompat lambda load-bearing).
17. **Item-cap exposure** — 11 framework / 1 hand-rolled / 1 engine / 2 Empty. *Collapses-when-root-fixed.*
18. **Standard sync pair** — 7 re-copies (+5 redundant in-subtree overrides). *Collapses-when-root-fixed* / those 5 are drift.
19. **Recipe-viewer integration** — 4 JEI / 4 REI (asymmetric; 2 REI plugins copy-paste). *Collapses-when-root-fixed* (intra-REI).
20. **BER geometry submit** — ~27 sites across 3 version gates. *Load-bearing* (two cliffs, not one).
21. **Capability registration body** — 6 hand-written listeners (preamble in 3 shapes). *Pure-accidental-drift.*
22. **Screen mouse-event handling** — 12 delegate / 13 override. *Collapses-when-root-fixed.*
23. **1.21.1 `onDataPacket` workaround** — 1 re-copy / 4 invariant-dependent. *Collapses-when-root-fixed* (1.21.1-node only).
24. **MJ receiver over battery** — 8 reuse / 3 hand-rolled. *Pure-accidental-drift.*
25. **Container open-call view** — 11 clean + 1 double-write / 5 SimpleMenuProvider / 5 anonymous + 1 addon. *Collapses-when-root-fixed.*
26. **Fluid-cap exposure** — 2 direct / 1 column / 2 inline restricted / 2 fan-out. *Pure-accidental-drift.*
27. **Soft-start power ramp** *(NEWLY FOUND)* — 2 identical + 1 variant + 2 divergent-curve. *Pure-accidental-drift* (likely a curve bug).
28. **Recipe-book menu surface** — 2 real / 23 dead-inherited. *Load-bearing* (but over-engineered base).
29. **Payload content carriage** — 13 schema / 2 byte[] tunnels. *Load-bearing.*
30. **Item-eject 6-face shuffle** — 5 canonical / 2 inline. *Load-bearing* params, triplicated directive.
31. **Marker cache family** — 4 Path / 4 Volume. *Load-bearing* at Connection layer; boilerplate collapsible.
32. **JEI '+' recipe transfer** — 3 bespoke / 1 generic. *Collapses-when-root-fixed* (the 2 bucket handlers).
33. **Volume region** — `VolumeConnection` / `VolumeBox`. *Load-bearing* (different features); broadcast drift.
34. **Fluid-box quad emitter** — 3 private kits. *Pure-accidental-drift* (empty `FluidRenderer`).
35. **BlockItem call form** — 17 holder / 20 id-string. *Pure-accidental-drift.*

**Low systemicness** (mostly standalone cleanups, several real bugs)
36. **Block-quad extraction** — 3 ladders (2 byte-identical). *Pure-accidental-drift.*
37. **Marker SavedData** — `PathSavedData`/`VolumeSavedData` byte-identical. *Pure-accidental-drift.*
38. **Status ledger** — LedgerEngine/EngineFE 1-line drift. *Pure-accidental-drift.*
39. **Engine chain-walk** — `getReceiverToPower`/`getFeReceiver`. *Load-bearing*; dedup candidate.
40. **Mode button** — 6 `BCButton` / 2 vanilla. *Pure-accidental-drift.*
41. **Recipe-book component** — 2 verbatim duplicates. *Pure-accidental-drift* (+5 dead stubs).
42. **Text input** — 4 `EditBox` / 0 BC widget. *Collapses-when-root-fixed.*
43. **Non-tile pipe-layer MJ receiver** — 2 inline copies. *Collapses-when-root-fixed.*
44. **Stateless CustomRecipe serializer** — 4 × 3 branches. *Load-bearing* cross-version; intra-line micro-fork.
45. **Tag→Ingredient** — 1 site, 2 branches, centralized. *Load-bearing.*
46. **Custom-subclass item registration** — `RegistrationUtilBC` shim / direct. *Load-bearing* (1.21.2 cliff).
47. **FE-exposure gap** — 8 `createIfRfEnabled` / Auto Workbench none. *Collapses-when-root-fixed*; player-visible (dead on FE).
48. **`mjPerRf` ratio read** — 4 direct / 7 via `MjAPI`. *Pure-accidental-drift.*
49. **Network-packet framework** — 15 live / 5 dead stubs. *Pure-accidental-drift.*
50. **Tunable-value read** — ~25 real / 1 fake `DetailedConfigOption`. *Pure-accidental-drift.*
51. **`IStatement` registration** — 3 ctor-only / 1 with 7 redundant calls. *Pure-accidental-drift* (7-line delete).
52. **JEI transfer-id constant** — 1 canonical / 1 duplicate. *Pure-accidental-drift.*
53. **JEI plugin UID** — 3 colliding / 1 distinct. *Pure-accidental-drift — **REAL BUG** (UID collision).*

### Newly found — the answer to "what else is hiding"

The per-dimension passes each saw only half of several forks; the completeness critic named the rest:

- **Soft-start power-ramp duality** — the first audit declared this "exactly one implementation." It is actually 5 copy-pasted sites in 3 formulas, and `SnapshotBuilder` (driving Builder+Filler) uses `capacity*2` where Distiller/Laser/Quarry use `capacity/2` — a near-certain latent bug.
- **`TileMarkerPath`** was missing from the BE-base System-B enumeration (`TileMarker` has two concrete subclasses).
- **`PlugBakerFacade.getQuadsFromModel`** — a third byte-identical copy of the block-quad extraction ladder (audit named only two).
- **`sendBlockUpdated` is ~11 tiles**, not the lone engine; `TileAutoWorkbenchBase`/`TileAdvancedCraftingTable` were mis-filed as passive no-sync.
- **The 33-vs-62 non-BE serialization split** — largest parallel-system population, invisible to per-dimension passes.
- **Systemic menu-open pos double-write** — `BlockDynamoMJ` (and likely all 11 "clean" `openMenu(tile,pos)` sites) write the BlockPos twice; deserves its own audit.
- **6th menu-open writer** (`openFillerPlannerGUI`, addon-backed) and **two duplicate byte[]-tunnel writer interfaces** (`IPayloadWriter` / `IPipeHolder.IWriter`).
- **REI re-sends `NET_JEI_RECIPE_TRANSFER` inline** instead of reusing `BlueprintTransferHandler`.
- **5 redundant in-subtree `getUpdateTag` overrides** deletable today regardless of any root.
- **A third tile-sync approach** (markers via `MarkerCache`/SavedData) using neither system.
- **5 empty `lib.gui.recipe` "phantom" stubs** the audit mistook for shared infra; the real base is vanilla `RecipeBookComponent`.

### What to leave alone (load-bearing, with reason)

Tagged so they are not "simplified" into regressions: BE-base A/B (real Neptune-machinery vs bespoke-state semantics); BE client-sync delta channel (pipe sub-objects have no BE identity); power-flow distribution (two incompatible energy type systems); all Stonecutter `//?` directives (BER submit two-cliff split, tag→Ingredient, stateless CustomRecipe serializer, custom-subclass item Properties cliff); byte[] payload tunnels (genuinely polymorphic content); JEI-vs-REI (two third-party APIs); `VolumeConnection` vs `VolumeBox` (different player-facing features); marker Connection geometry (ordered chain vs axis box).
