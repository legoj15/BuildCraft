# Robotics resurrection — plan & context

Linked from [todos.md](../todos.md). This is the working plan for porting the robot system; update phase status here as work lands, and delete a phase's section only if the whole program is ever abandoned.

**Status: Ph0 (seams b, c), Ph1, Ph2, Ph3, Ph4, Ph5, and Ph6 are complete. Next up: Ph7 — Programming Table.**

**2026-09-03 gameplay audit** against the live 7.1.27 reference client (record: [robotics-gameplay-audit.md](robotics-gameplay-audit.md)): numeric parity complete; 16 deviations fixed tests-first, the remaining ones listed there as follow-ups/decisions.

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

Model is 1.7.10's two-box pedestal — an 8×8×1px plate hugging the pipe hull plus a ~2.2px-square post out to the block edge — baked state-invariant (`KeyPlugRobotStation`/`PlugBakerSimple`) in the `available` texture, which upstream used for both of its idle states; the reserved and linked pedestals are re-emitted whole, a hair proud, per frame by `PlugRobotStationRenderer`. Both paths build from one geometry source (`RobotStationModel`), so baked and dynamic cannot drift apart. Deliberately NOT a single 4-state baked model: `KeyPlugGate`'s docs record that a state-varying baked key forces a 27-section chunk re-mesh on every flip, and dock/undock/reserve happens far more often than a block edit. Idle stations — the overwhelming majority — draw zero dynamic quads.

_Tests:_ 8 GameTests (`RobotStationPluggableTester`) — placement auto-registers, player-break auto-deregisters, `RobotUtils.getStations` discovery through a real `IPipeHolder`, the take/release reservation lifecycle (standing in for "undock → release"), render-state transitions (available/reserved/linked), MJ-charge handoff lossless when docked, no charge when merely reserved, item-pipe output smoke. Plus the placement-preview AABB case in `PipePlacementHighlightTester`, so the highlight outline can't drift from the pedestal it traces. Plus JUnit: `RobotUtilsTest` (6 cases, mocked `IPipeHolder`/`IDockingStationProvider`), `DockingStationPipeTest` (5 cases: `RobotManager` "pipe" registration, no-arg-ctor NBT reload, pos/side round-trip, the missing-`robotId`-defaults-to-`0L` characterization, the explicit unlinked sentinel), `RobotStationModelTester` (pedestal geometry, UVs and the proud offset against 7.1.x's numbers) and `RobotStationItemModelTester` (the hand-authored item model, which nothing else in the build validates). **Physical dock → position-snap is deferred to Ph3** — that's `EntityRobot`'s movement logic, and no concrete robot exists yet (same call `RobotRegistryTest` made for its own entity-gated paths).

### Ph3 — EntityRobot + ItemRobot + renderer (COMPLETE 2026-08-01)

The full worked design — every decision, its evidence, the traps it avoided, and the three
post-implementation defect diagnoses — is [robotics-ph3-design.md](robotics-ph3-design.md);
**read it before any Ph4+ work**. Landed across `bbb51478c` → `90a797f37`, tests-first (red
baseline committed before implementation).

What exists now: concrete `EntityRobot` on **bare `Entity`** (registry lifecycle with leak-proof
kill/unload routing, docking snap, damage-drains-battery/death-drops, modern save format under
7.1.x key names, `SynchedEntityData` networking with the ItemStack shadow-copy dirtiness gate,
client yaw smoothing + energy particles); seam (a) **`IRobotAccess`** (AIs/boards type against the
interface; the whole AI framework is now pure-JUnit-testable via a mock); `MAX_POWER` re-pinned to
`10_000 * MjAPI.MJ` (canonical 10 RF/MJ, constants live on `IRobotAccess`); `isActive()` renamed
`isSleeping()`; `RobotChargeReceiver` (ticksCharging latch, simulate-inert, still `IMjReadable`
for gate triggers); `BoardRobotEmpty` registered so skins/placement resolve; `ItemRobot`
(CUSTOM_DATA board+charge blob, hand-placement onto a free station, charge tooltip — deliberately
recipe-less until Ph4 decides pricing incl. the Redstone Crystal question, Decision 8);
`RenderRobot` across BOTH renderer generations (1.21.10 cliff; 26.1.2≡26.2) with the 8.0.x 32×32
UV net, charge/bottom overlays, and the two hard-won render rules — decal passes need straight-up
normals (the diffuse term caps "fullbright") and `order(1)` (coplanar same-pipeline submissions
tie-break arbitrarily on 26.x); `robot_station` recipe (a Ph2 oversight) + the 4-arg
`PluggableDefinition` ctor that made the station item-placeable at all.

_Tests:_ 15 new game tests (suite 381→398, all five nodes green) + JUnit (energy pins,
`UnreachableEntityCache`, 18-case AI-framework suite) + the shared `EntityArenaUtil` harness;
in-client visual verification on 26.2 + 1.21.1 (53 + refix screenshot sets). Deferred by design:
wearable acceptance/skull/armour render + melee (Ph9), zones (Ph6), requester residue (Ph8).

## Remaining phases

### Ph4 — First boards + recharge AI [MVP completion] (COMPLETE 2026-08-06)

~18 AIs (goto/straight-move/goto-station/goto-block, search/fetch/load/unload, `AIRobotMain`/`Recharge`/`Sleep`/`Shutdown`), each registered by name with NBT round-trip; **Picker + Carrier** boards; `ItemRedstoneBoard`; recharge through the Ph2 MJ handoff. Design seams D1–D7 as planned in [Ph4-plan.md](Ph4-plan.md): station access policy lives on `DockingStation` with permissive defaults (D1 — the one deliberate divergence from 7.1.x, whose gateless default was *refuse*; see the Ph6 note below), synchronous `PathFinding.iterate(50)` on the server thread (D3), `Set<UUID> targettedItems` (D4), boards creative-tab-only (D5), supply-side discovery on `DockingStationPipe.getItemInput` (D6).

Two real bugs were caught only by **in-client verification** (McDevBridge on 26.2), both then pinned by game tests: the board item's missing client model definition (`items/redstone_board.json`, the 1.21.4+ item-model split), and the station item-output/input fidelity pair — `getItemInput` accepted any item pipe and read the station's own face instead of requiring a wooden pipe's wrench-set face (a Carrier drained chests through plain cobblestone pipes: a free unpowered hopper), and `getItemOutput` exposed the raw `PipeFlowItems`, whose `canInjectItems = isConnected(from)` refused robot unload at every dead-end dock (7.1.x used a synthetic station-side `IInjectable` that never consulted the network; the port now synthesizes one over `insertItemsForce`). The double adversarial review (7 dimensions × 2 refutation lenses) plus completeness critic confirmed no other MVP-gate gaps; its 19 confirmed findings were then fixed tests-first (a latent fetch dupe on a removed target, partial-fit fetch targeting, the load-refusal put-back copy, goto-block/search-station/shutdown characterization pins, and the dropped-robot-item board identity).

One intermittent placement-test flake surfaced across the fix runs and was diagnosed to two independent mechanisms, both fixed: success-path robots were never discarded (the framework's pass-time cleanup bounds only reach ~1 block past the 1×1 empty structure, so they leaked into the NEXT batch's reuse of the same arena coordinates), and proximity-count assertions caught robots of CONCURRENTLY-RUNNING same-batch neighbours (empty-structure grid rows are spaced 7 apart in Z, not 8 — a pipe at rel z=7 sits in the next row's cell). The discipline — discard every world-added entity before succeeding, assert by station identity, keep every position at x≤5/z≤6 — is written into `EntityRobotTester`'s javadoc.

_Tests:_ suite 398→411 game tests: board registry sweep, picker fetch E2E (fly-to + consume, no dupe), carrier load E2E (fly-to + dock + load), carrier unload-cycle E2E (pins the D1-emergent loop below), low-power recharge E2E (search → fly → dock → charge past `SAFETY_POWER` against a live kinesis rig), the wooden-pipe supply gate, the dead-end unload regression, the three fetch-targeting pins, and death/drops extended to assert the dropped item's board — plus the Ph3 smoke test converted from an `insertItemsForce` dodge to the real `injectItem` contract. JUnit: `AIRobotMain` preempt ladder, per-leaf cost table, board NBT round-trips, goto-block/search-station/load units. In-client: picker fetch, dock+recharge, skins, board item render, 20-robot idle perf — all on 26.2.

**Amendment (2026-08-28): the Tank (FluidCarrier) board landed here too**, pulled forward from Ph9 — it is the Carrier's fluid twin and every piece it needs (the fluid load/unload AI family, the D1 fluid policy, the gate actions) had already landed around it. Ported verbatim (`BoardRobotFluidCarrier` + NBT, green tier 8000 RF); the load half needed 7.1.x's `getFluidInput`, now ported as the fluid twin of the D6 rule: a station supplies fluid only through a **wooden fluid pipe's** wrench-set extraction face (`DockingStationPipe.getFluidInput`, resolved through the neighbour's block capability), and the D1 seam gained its fluid filter twin (`DockingStation.getRobotFluidFilter` → `ActionRobotFilter.getGateFluidFilter`, pass-through when the gate sets no filter). The E2E caught a live `EntityRobot` bug on the way: the 26.x tank fork committed simulate insert/extracts immediately, so every station-search dry-run really moved a bucket (the search filled the robot's tank at spawn; the pump board's unload search would have spilled its cargo station by station) — the fork now journals through `SnapshotJournal` like `ItemHandlerSimple`, and the 1.21.1 classic branch already passed `FluidAction.execute()` honestly. Game tests 415→417: the fluid supply discovery pin (wooden *fluid* pipe required, cobble refuses) + the fluid-carrier load E2E; JUnit round-trip extended.

### Ph5 — Board catalog (COMPLETE 2026-08-21)

The remaining 16 AIs (search/fetch/load/unload(+fluids), break/harvest/plant/use-tool/attack, work-in-area, goto-station-adjacent, straight-move-to) + 8 boards (Lumberjack, Harvester, Miner, Planter, Farmer, Pump, Knight, Butcher) + the 2 abstract generic-search/break bases, each registered by name with an NBT round-trip, ported from `upstream/7.1.x:common/`; 7 world properties (soft, wood, harvestable, ore@hardness=0–3, dirt, replaceable, fluidSource) against the live `BuildCraftAPI` registry, plus `CropManager.setDefaultHandler(CropHandlerPlantable)`. 13 of the 16 AIs keep 7.1.x's legacy `buildcraft.core.robots.*` names (Harvest/Plant/SearchAndGotoBlock are post-6.x); costs re-pinned at the µMJ bridge (blue boards 3_200_000_000, knight 12_800_000_000).

The dead per-board item identity was resolved by keeping the single chip texture and using the 1.21.4+ item-model system: `items/redstone_board.json` is a per-board `minecraft:condition` chain (the type id is `condition`, not `conditional`; `minecraft:select` was rejected — exact-equality only) over `custom_data.board.id` with a partial NBT match resolving each board's icon. The 1.21.1 node has no item-model system and keeps the single chip — line-inherent, not a gap.

Step-5 debugging surfaced three real defects, all pinned (`398114a17`): the ported 3-arg `AIRobotSearchAndGotoBlock` defaulted `maxDistanceToEnd` to 64 where 7.1.x used 0 (a positive radius ends the pathfind at the first soft cell in range → a two-cell stub path → the goto leg never flies); `AIRobotGoto`'s movement targets were float, and the 26.x world (±3e7) has float's ulp at 1.0 past |8.4M|, so the +0.5 cell-centre offset was dropped at game-test arena coordinates (now double; NBT stays float); and the search tests were not hermetic — the search scans 64 blocks, so a neighbouring test's leftover oak log (two cells away) was a legal, closer target. Private `test_environment`s separated the search tests from the default-environment tests but NOT from each other: the two search arenas land one grid cell (6 blocks) apart with identical oak logs at identical relative (5,2,3), so each is the other's closer target — the search-finds-log test failed deterministically at final HEAD (found the sibling's log at exactly −6 in x). The hermetic fix is position-pinned filters: each search matches only its own log's exact block (`pos.equals(logPos)`), framework-independent. The full-suite gate was first genuinely green after that pin (`415 required tests passed` on 26.2; spot-checked on 1.21.1). The world-properties test had a test-world bug of its own: vegetation blocks re-validate canSurvive whenever a neighbour changes, and the dark bedrock-floored arena turned the crops to air before the read (the test now lays farmland/soul sand/dirt under the plants plus a glowstone — wheat needs light ≥ 8). The `AIRobot` no-terminated-flag re-cycle (`cycle()` re-runs `update()` after `terminate()`) is faithful to 7.1.x and required by production (`EntityRobot.tick` cycles `mainAI` forever; boards never self-terminate) — deliberately not changed.

_Tests:_ 411→415 game tests: the live-server world-property sweep (FML-JUnit performs no resource load, so tag-backed properties can never resolve there — and the wheat pin pins the soil+light the arena needs to keep crops alive), search-finds-log, and the search-and-goto flight (parks one cell short of the log; the 1.5 gate is the picker-E2E radius); JUnit: `BoardPredicateSweepTest`, `BoardNbtRoundTripTest`, `MinerHarvestLevelTest`, `AIRobotLoadUnloadFluidMathTest`, `AIRobotCostTableTest` over all 16 AIs, `WorldPropertySweepTest`. In-client (McDevBridge): the 8 board icons + creative tab on 26.2; boards present with the single chip on 1.21.1.

### Ph6 — Statements (COMPLETE 2026-08-22)

All 22 robot/station triggers+actions and the 2 parameter widgets (board-picker, zone-selector) ported, lang-wired and registered (full design: [robotics-ph6-design.md](robotics-ph6-design.md)); `DockingStationPipe` overrides the four permissive Ph4 policy defaults with 7.1.x gate semantics (the D1 decision: the *gate side* owns every default). A station with no gate now refuses robot item/fluid transfers exactly as 7.1.x did — which kills the Ph4 carrier loop (load→unload-at-feet→sleep→reload); the old `robot_carrier_unloads_at_loaded_station` pin is replaced by the live-forbid `robot_station_forbid_robot` pin. `AIRobotSearchStation` skips gate-forbidden stations verbatim.

_Tests:_ 7 JUnit classes (filter truth tables, provide-items extract, forbid matching + invert, work-area math, blanket registration/serialization sweep, param widgets, provider discovery) + 3 gate GameTests (sleep+wakeup through a live `PluggableGate`, the forbid refusal through the live station, the goto-station redirect).

### Ph7 — Programming Table

Block/tile/menu (reuse `RenderLaser` beam) + `BoardProgrammingRecipe`/`RobotIntegrationRecipe` (mind the CustomRecipe cross-node cliff).

_Tests:_ board crafting-cost tiers (8k/32k/128k/512k — distinct from per-tick leaf cost); Programming/Integration recipe math as **pure JUnit via a bespoke recipe-manager** (sidesteps the vanilla `CustomRecipe` cross-node serializer cliff); board-sorter determinism; robot energy preserved through integration. McDevBridge: table GUI + energy-fill animation.

### Ph8 — Requester network

`StackRequest`, Requester block (`IRequestProvider`), Delivery robot. Largest from-scratch server-side dependency. The reservation backend is **already done** (`RobotRegistry.take/release/isTaken` + `ResourceIdRequest` landed in Ph0/Ph2) — what is still stubbed is the *discovery* half: `DockingStation.getRequestProvider()` returns null and `DockingStationPipe` does not override it, so 7.1.x's six-neighbour scan of the host pipe (which is what finds an adjacent Requester) does not exist. (`getFluidInput()` was the other unoverridden discovery — it landed 2026-08-28 with the Tank board's wooden-fluid-pipe supply rule; the requester-facing `IRequestProvider` half stays here.)

**Unblocked by Ph6 (2026-08-22):** 7.1.x's load/unload AIs require an *active* `Provide Items`/`Accept Items` gate action, and `DockingStationPipe.getActiveActions()` now returns the live gate's actions — a Ph8 built before Ph6 would have been untestable end to end.

Two 7.1.x behaviours to decide rather than inherit: `getRequestProvider()` never returning null (it falls back to the station itself, which makes the `Request Needed Items` gate action appear on every station and 7.1.x then never reads it), and the ghost-slot input method (hold exactly N items to request N) — main already has a first-class phantom-slot framework in `BCContainerSupport`, so a real quantity control is available if wanted.

_Tests:_ `StackRequest`/`TileRequester` matching + fulfilment arithmetic (12–16 cases); `StackRequest` NBT round-trip (GameTest — ItemStack) + `isItemValidForSlot`; `AIRobotSearchStackRequest`/`DeliverRequested`; fluid-tank math. McDevBridge: requester table GUI.

### Ph9 — Builder robot + advanced

Builder board **rewritten** against main's `SnapshotBuilder`/`BlueprintBuilder` (7.1.x targets the obsolete `TileConstructionMarker`/`BuildingSlot` API, absent on main); Bomber/Stripes/LeaveCutter/Shovelman (FluidCarrier pulled forward to Ph4, 2026-08-28); goggles/wearables polish; optional multi-chunk A* upgrade from `8.0.x-better-robot-pathfinding`.

_Tests:_ `AIRobotBreak` progress formula (speed/hardness); **one representative each** of Harvest/Plant/Pump/UseTool/Attack/SearchEntity (not all); Builder board GameTest + manual (markers, `MAX_RANGE_SQ`, requirements, energy gate). If threaded pathfinding ships, add concurrency coverage for the `synchronized` reservation map.
