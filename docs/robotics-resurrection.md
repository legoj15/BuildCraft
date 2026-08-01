# Robotics resurrection — plan & context

Linked from [todos.md](../todos.md). This is the working plan for porting the robot system; update phase status here as work lands, and delete a phase's section only if the whole program is ever abandoned.

**Status: Ph0 (seams b, c), Ph1, and Ph2 are complete. Next up: Ph3 — EntityRobot + renderer.**

## Overview

Port the robot system from `upstream/7.1.x:common/buildcraft/robotics/` (1.7.10, 121 files / ~13.4k LOC) onto main's BC8 architecture, against the already-present orphaned `api/robots` + `api/boards` contract. No *working* modern port exists anywhere (robots were dropped at the 1.7.10→BC8 cliff; every BC8 port 1.11.2→1.20.1 carries the same Zone-Planner-only stub) — this is logic translation, not a lift.

**There is a closer starting point than raw 7.1.x for the mechanical half:** `8.0.x-1.12.2:src_old_license/buildcraft/robotics/` holds 123 files that were already mechanically migrated to 1.8+/1.12-era APIs — `EntityRobot` alone has 33 `BlockPos`/`EnumFacing`/`Vec3d` references and zero `ForgeDirection`, against 7.1.x's 0 and 13. It is dead (excluded from that branch's build via a commented-out `srcDir`, and never finished), so it is not a drop-in and its *logic* is no more modern than 7.1.x's — but for the coordinate/facing/`Vec3` churn that dominates a 1.7.10→26.1 port it is worth diffing against before hand-translating a file.

Feasibility GREEN (pluggables, statements, MJ, SavedData, networking, laser render, custom-entity registration all live on main). **MVP = Phases 0–4 (~6–8 wk); full restoration = +5–9 (~2–3 mo).**

### Gotchas up front

- Source energy is **RF/int**, contract is **MJ/long** — rewrite + re-tune, not a rename.
- `EntityRobotBase extends LivingEntity` — decide: keep, or rebase the API onto bare `Entity`. (`EntityQuarryRig` is a bare `Entity` + `NoopRenderer`, *not* a like-for-like precedent for a visible robot.)
- `api/mj/MjCapabilityHelper` is a dead `return null` stub — fix it, don't trust it.
- Model the charging station on `transport/plug/PluggablePowerAdaptor`.

## Test strategy

Characterization, not parity (no automated oracle — 1.7.10 can't run beside 26.1; the old 7.1.x tests give zero reusable oracles). The cheap-pure-JUnit majority rides 3 Ph0 *seams*, each budgeted **L w/ design risk** — if they slip, the per-phase test lines below demote to slow GameTests:

- **(a)** an `IRobotAccess` interface so `AIRobot.robot` isn't a concrete `LivingEntity` — **deferred to Ph3** (no concrete robot exists yet to type it against; its framework-delegation tests move to Ph3 with it).
- **(b)** an injectable `SoftBlockAccess` predicate decoupling A* from `World` — **landed in Ph0**.
- **(c)** a decoupled `RobotRegistry.writeToTag/readFromTag` — **landed in Ph0**.

Precedents to copy: `MjBatteryTest` (pure energy), `VanillaSetupBaseTester` (Bootstrap, for any `CompoundTag`), `EntityQuarryRigTester` (custom entity), `FluidPhysicsTest` (chunk-force-load), `StatementSerializationTester` (ItemStack ⇒ must be a GameTest), `MarkerTester` (+ private `test_environment` to serialize shared-singleton tests). ~6–9 wk test-writing, front-loaded; ~60% of value in the first 3 wk **iff the seams land**.

## Completed phases (kept as context for the remaining ones)

### Ph1 — Zone Planner: map viewport (COMPLETE 2026-06-28)

Interactive isometric 3D terrain map via the Picture-in-Picture pipeline (`ZoneMapPipRenderer`/`ZoneMapPipRenderState`, modelled on `BlueprintPipRenderer`; >=1.21.10), fed by a client-side per-chunk surface snapshot (`ZonePlannerMapChunk`/`ZonePlannerMapDataClient`, built from the WORLD_SURFACE heightmap — **no server streaming**, which retired BC8's `ZonePlannerMapData{,Server}`/`MessageZoneMap*` need). Top-down **perspective** camera (`ZoneMapCamera`, matching BC8 — not isometric) with terrain ray-pick hover/paint, hovered-block coordinate readout, and furnace-style input/output progress bars (`ContainerData`-synced). Paint-sync through `ContainerZonePlanner.broadcastChanges`/`readMessage`. Map Location un-gated + both survival recipes shipped.

The **in-world block-face minimap** (BC8's `RenderZonePlanner` TESR) is ported as `RenderZonePlanner` (a BER drawing vertex-coloured cells through `BCLibRenderTypes.led()`, sampling math in the pure `ZoneFacePreview`; in-client verified, north-up/east-right). This made `front_on.png` (a *baked* static map) obsolete: the live map covers exactly the screen recess where it differs from `front.png`, so it can never render — archived to `misc/unused_textures/zone_planner_front_on.png` (distinct from the older `zoneplanner_front_on.png`).

Remaining polish (low priority): **infinite pan into unloaded chunks** — the deferred server-streaming subsystem; only worth it for planning zones beyond render distance.

### Ph2 — Docking station pluggable (COMPLETE 2026-07-26)

`RobotStationPluggable` (`PipePluggable`, modeled on `PluggablePowerAdaptor`) registers/deregisters a `DockingStationPipe` in the `RobotRegistry` lazily on first server-tick (modern `PipePluggable` has no `validate()` hook to piggyback on) and on `onRemove()`; exposes `MjAPI.CAP_RECEIVER` as an `MjBatteryReceiver` wrapping the docked robot's battery, gated on actually-*docked* (`robot.getDockingStation() == station`), not merely reserved — reuses the pre-existing, unmodified `DockingStation.take/takeAsMain/release` reservation API. No bespoke `ItemRobotStation` — the pluggable's NBT is empty like `PluggablePowerAdaptor`'s, so the existing `ItemPluggableSimple` creator path covers it.

Model is a state-invariant baked plinth (`KeyPlugRobotStation`/`PlugBakerSimple`, ported 7.1.x `pipeRobotStationBase` texture) plus a per-frame BER indicator (`PlugRobotStationRenderer`) cycling the ported available/reserved/linked textures — deliberately NOT a single 4-state baked model: `KeyPlugGate`'s docs record that a state-varying baked key forces a 27-section chunk re-mesh on every flip, and dock/undock/reserve happens far more often than a block edit.

_Tests:_ 8 GameTests (`RobotStationPluggableTester`) — placement auto-registers, player-break auto-deregisters, `RobotUtils.getStations` discovery through a real `IPipeHolder`, the take/release reservation lifecycle (standing in for "undock → release"), render-state transitions (available/reserved/linked), MJ-charge handoff lossless when docked, no charge when merely reserved, item-pipe output smoke. Plus JUnit: `RobotUtilsTest` (6 cases, mocked `IPipeHolder`/`IDockingStationProvider`) and `DockingStationPipeTest` (5 cases: `RobotManager` "pipe" registration, no-arg-ctor NBT reload, pos/side round-trip, the missing-`robotId`-defaults-to-`0L` characterization, the explicit unlinked sentinel). **Physical dock → position-snap is deferred to Ph3** — that's `EntityRobot`'s movement logic, and no concrete robot exists yet (same call `RobotRegistryTest` made for its own entity-gated paths).

## Remaining phases

### Ph3 — EntityRobot + renderer [MVP, biggest single item]

The full worked design — every decision, its evidence and the traps it avoids — is [robotics-ph3-design.md](robotics-ph3-design.md); read that before touching Ph3 code. Summary: concrete `EntityRobot` (synched/spawn/save data, inventory, MJ battery via the RF→MJ conversion, AI host (seam (a): `IRobotAccess` lands here so `AIRobot.robot` types against an interface — its framework-delegation tests ride along), dock/undock) on bare `Entity`, not `LivingEntity`; register via new `BCRoboticsEntities`; one renderer class; `ItemRobot`.

**Renderer: TWO entity-renderer API generations, not three.** 1.21.1 is the classic `EntityRenderer<T>` (`render(...)` + `getTextureLocation`); everything from 1.21.10 up is the render-state/`SubmitNodeCollector` generation — **26.1.2 and 26.2 are identical to 1.21.10 for entity rendering**, so no third branch exists. (The laser call is the one site that forks at 26.1 rather than at 1.21.10.) Still the only true from-scratch piece, and still wants **in-client visual verification**.

**`ItemRobot` ships recipe-less in Ph3, on purpose.** Boards do not exist until Ph4, so a craftable robot would be an expensive do-nothing item; the pricing question — including whether 7.1.x's **Redstone Crystal** gate returns at all — is decided in Ph4, and the arguments for and against are recorded in Decision 8 of the design doc. The robot is creative-tab-only until then.

`robot_station`'s missing recipe (a Ph2 oversight) **landed with Ph3**: `data/…/recipe/robot_station.json`, 7.1.x's shape — 3 iron ingots + 1 Golden Chipset.

_Tests:_ **energy overflow tripwire** (`MAX_POWER = 10_000*MjAPI.MJ = 1e10 > Integer.MAX_VALUE` — the 7.1.x type was `int`; the capacity was re-pinned to the canonical 10 RF = 1 MJ bridge, see Decision 3) + MJ cost-ordering pins (FetchItem > Load > Goto > Main/Recharge=0; MJ constants are design decisions, comment "chosen, not derived"); battery-only NBT (JUnit+Boot) + full battery/inv/tank/wearable NBT (GameTest — serializes ItemStacks; this first entity GameTest brings the `forceLoadEntityArena`/`tickUntil` harness utils online); attack/charging conversions; spawn + persistence flags; framework delegation + **blanket AI NBT-round-trip + registration sweep** (now a real robot exists; covers single-arg-ctor AIs incl. the recursive-delegate reload); death-frees-all reservations end-to-end; SynchedEntityData 2-side sync. McDevBridge: renderer / energy particle FX / movement feel.

### Ph4 — First boards + recharge AI [MVP completion]

~12 AIs (goto/straight-move/goto-station/goto-block, search/fetch, load/unload, `AIRobotMain`/`Recharge`/`Sleep`/`Shutdown`), each registered by name in `RobotManager` with NBT round-trip; goto-family AIs must short-circuit "already there" (the `PathFinding` start==end 3-cell out-and-back quirk pinned in Ph0's tests); **Picker + Carrier** boards; `ItemRedstoneBoard`; spawn+dock+recharge game test. ← MVP ends: a placeable, dockable, self-recharging robot that picks up / carries items.

_Tests:_ **[MVP gate]** registry-completeness sweep (every board instantiable + self-resolves); board-blob `DataComponent` (`CUSTOM_DATA`) round-trip; `AIRobotMain` preempt ladder (handle 0-cost Main/Recharge) + `Recharge`/`Sleep` thresholds; per-leaf cost table + relative ordering; **Picker + Carrier end-to-end**; `AIRobotFetchItem` (private `test_environment`; watch `targettedItems` int-id→UUID migration); recharge-over-ticks (needs chunk-force-load) + death/drops; `ItemRobot` energy round-trip + `getDrops` composition; partial wrench-dismantle (`makeMockPlayer` caps the server path — document the skip).

### Ph5 — Board catalog

Remaining work/break/harvest AIs + boards (Lumberjack, Harvester, Miner, Planter, Farmer, Pump, Knight, Butcher) + the 2 abstract generic-search/break bases.

_Tests:_ flat per-board predicate sweep (`isExpectedTool`/`isExpectedBlock` etc.); board `writeSelfToNBT`/`loadSelfFromNBT` round-trips; `AIRobotLoad`/`Unload`(`+Fluids`) move-math (conservation + qty cap); `SearchBlock`/`SearchAndGoto*` orchestration; Miner harvest-level clamp + ore key.

### Ph6 — Statements

Port the 22 robot/station triggers+actions + the 2 parameter widgets (board-picker, zone-selector; atlas via `SpriteHolderRegistry`); wire the orphan `gate.action.robot.*` / `gate.trigger.robot.*` lang strings.

_Tests:_ static filter predicates (`canInteractWithItem`/`canExtractItem`/`getGateFilter`) as JUnit if mockable, else GameTest (verify `StatementSlot`/`DockingStation` are constructible without a `Level` first); blanket statement+param registration & serialization sweep; `ActionRobotWorkInArea.getArea`; trigger / robot-param GameTests once a docked entity exists.

### Ph7 — Programming Table

Block/tile/menu (reuse `RenderLaser` beam) + `BoardProgrammingRecipe`/`RobotIntegrationRecipe` (mind the CustomRecipe cross-node cliff).

_Tests:_ board crafting-cost tiers (8k/32k/128k/512k — distinct from per-tick leaf cost); Programming/Integration recipe math as **pure JUnit via a bespoke recipe-manager** (sidesteps the vanilla `CustomRecipe` cross-node serializer cliff); board-sorter determinism; robot energy preserved through integration. McDevBridge: table GUI + energy-fill animation.

### Ph8 — Requester network

`StackRequest`, Requester block (`IRequestProvider`), Delivery robot. Largest from-scratch server-side dependency. The reservation backend is **already done** (`RobotRegistry.take/release/isTaken` + `ResourceIdRequest` landed in Ph0/Ph2) — what is still stubbed is the *discovery* half: `DockingStation.getRequestProvider()` returns null and `DockingStationPipe` does not override it, so 7.1.x's six-neighbour scan of the host pipe (which is what finds an adjacent Requester) does not exist; `getItemInput()`/`getFluidInput()` are likewise unoverridden, so the wooden-pipe-facing-a-chest *supply* station has no modern equivalent.

**Hard-blocked on Ph6, not merely ordered after it:** 7.1.x's load/unload AIs require an *active* `Provide Items`/`Accept Items` gate action, and `DockingStationPipe.getActiveActions()` currently returns an empty list — a Ph8 built before Ph6 is untestable end to end.

Two 7.1.x behaviours to decide rather than inherit: `getRequestProvider()` never returning null (it falls back to the station itself, which makes the `Request Needed Items` gate action appear on every station and 7.1.x then never reads it), and the ghost-slot input method (hold exactly N items to request N) — main already has a first-class phantom-slot framework in `BCContainerSupport`, so a real quantity control is available if wanted.

_Tests:_ `StackRequest`/`TileRequester` matching + fulfilment arithmetic (12–16 cases); `StackRequest` NBT round-trip (GameTest — ItemStack) + `isItemValidForSlot`; `AIRobotSearchStackRequest`/`DeliverRequested`; fluid-tank math. McDevBridge: requester table GUI.

### Ph9 — Builder robot + advanced

Builder board **rewritten** against main's `SnapshotBuilder`/`BlueprintBuilder` (7.1.x targets the obsolete `TileConstructionMarker`/`BuildingSlot` API, absent on main); Bomber/Stripes/FluidCarrier/LeaveCutter/Shovelman; goggles/wearables polish; optional multi-chunk A* upgrade from `8.0.x-better-robot-pathfinding`.

_Tests:_ `AIRobotBreak` progress formula (speed/hardness); **one representative each** of Harvest/Plant/Pump/UseTool/Attack/SearchEntity (not all); Builder board GameTest + manual (markers, `MAX_RANGE_SQ`, requirements, energy gate). If threaded pathfinding ships, add concurrency coverage for the `synchronized` reservation map.
