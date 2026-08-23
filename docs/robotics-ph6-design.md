# Ph6 — Robot/Station Gate Statements — Design

Port the robotics gate statements from 7.1.x (`common/buildcraft/robotics/statements/` + the
statement helpers it depends on), wire the orphan lang strings, and reconcile the D1 station
policy with real gate-driven semantics. Companion to [robotics-resurrection.md](robotics-resurrection.md).

## Scope

- **18 registered statement instances** (4 triggers + 14 actions — `ActionRobotWorkInArea` and
  `TriggerRobotLinked` each register two instances via their area-type/invert variant), plus the
  abstract base `ActionStationInputItems`.
- **2 parameter widgets**: `StatementParameterRobot` (board picker), `StatementParameterMapLocation`
  (zone selector). Both extend `StatementParameterItemStack` (main's, in `buildcraft.api.statements`).
- **Helpers**: `StatementParameterStackFilter` / `ArrayStackOrListFilter` (item-filter plumbing),
  `StatementParameterItemStackExact` (request-item count widget), `ActionState` (marker class).
- **2 providers**: `RobotsActionProvider`, `RobotsTriggerProvider`.
- **Lang wiring**: 15 orphaned `gate.action.robot.*` / `gate.action.station.*` /
  `gate.trigger.robot.*` keys in `en_us.json` (lines ~764–783, 820–823).
- **D1 reconciliation**: `DockingStationPipe` overrides the four permissive Ph4 policy defaults with
  7.1.x gate semantics; fluid policies gain an `IFluidFilter` argument.
- **Consumers**: `AIRobotSleep.preempt` (wake), `EntityRobot.getZoneToWork` / `getZoneToLoadUnload`
  (zone statements), `BoardRobotCarrier`/`BoardRobotPicker` (via `getRobotItemFilter()` — already
  wired at Ph4).

## The D1 decision — per-policy, gate-side owns every default

Ph4's `DockingStation` javadoc claimed "7.1.x had no fluid gate statements". **That is wrong** —
verified verbatim in `upstream/7.1.x`: `AIRobotLoadFluids`/`AIRobotUnloadFluids` gate on
`ActionStationProvideFluids`/`ActionStationAcceptFluids`, which refuse by default. The doc comment
on the fluid defaults is corrected in this phase.

`DockingStation` (the API seam) keeps its permissive defaults as the *gateless* contract; every
override lands on `DockingStationPipe` reading real gate actions via
`getActiveActions()` → iterate the 6 faces, `holder.getPluggable(dir) instanceof PluggableGate` →
`pg.logic.getActiveActions()` (silicon import — the mod is one package tree).

| Policy | DockingStationPipe override | Semantics |
|---|---|---|
| `canRobotExtractItem(stack)` | `ActionRobotFilter.canInteractWithItem(this, new ArrayStackOrListFilter(stack), ActionStationProvideItems.class) && ActionStationProvideItems.canExtractItem(this, stack)` | net refuse: the robot may pull only what a filtered Provide-Items action offers; the `canExtractItem` half is the permissive "no filter → anything" |
| `canRobotAcceptItem(stack)` | `ActionRobotFilter.canInteractWithItem(this, new ArrayStackOrListFilter(stack), ActionStationAcceptItems.class)` | refuse-by-default — kills the Ph4 carrier load/unload-at-feet loop |
| `isRobotForbidden(robot)` | `ActionStationForbidRobot.isForbidden(this, robot)` | any `forbid_robot` slot where `invert ^ anyParamMatches`; matches by board NBT id |
| `getRobotItemFilter()` | `ActionRobotFilter.getGateFilter(this)`, falling back to `ActionRobotFilterTool.getGateFilter(this)` when empty | merged work-filter + tool-filter for picker/carrier fetch |
| `canRobotExtractFluid(filter)` | `canInteractWithFluid(this, filter, ActionStationProvideFluids.class)` | **signature extended** to take `IFluidFilter` |
| `canRobotAcceptFluid(filter)` | `canInteractWithFluid(this, filter, ActionStationAcceptFluids.class)` | ditto |

The fluid-signature change is a breaking edit of the two 3-week-old API methods
(`canRobotExtractFluid()` → `canRobotExtractFluid(IFluidFilter)`, same for accept); the only call
sites are `AIRobotLoadFluids`/`AIRobotUnloadFluids`, which already hold the filter. Base-class
default stays permissive (ignores the filter).

**`ActionStationForbidRobot.isForbidden` robot matching** needs `robot.getBoard().getNBTHandler().getID()`
versus `ItemRobot.getBoardId(stack)`; wearables only via `robot instanceof EntityRobot` (IRobotAccess
has no wearables — a documented 7.1.x→modern fidelity gap).

## TakeAsMain flag on AIRobotGotoStation

7.1.x's `ActionRobotGotoStation.activate` ends by running `AIRobotGoAndLinkToDock`, whose final step
is `robot.dock(station)` → `station.takeAsMain(robot)`. Main's `AIRobotGotoStation.start()` uses plain
`station.take(...)` — fine for a search-resolved dock, wrong for an explicit action (plain take leaves
`getLinkedStation()` stale, so the robot flies home to the old station after the action). Add a
`takeAsMain` boolean ctor to `AIRobotGotoStation` (existing 2-arg ctor delegates with `false` — zero
impact on existing tests); the action constructs `new AIRobotGotoStation(robot, newStation, true)`.
`AIRobotGoAndLinkToDock` itself is **not ported** (it is unregistered internal composition; the flag
covers its only behavioural difference).

## Statement inventory and icon mapping

All statements extend `buildcraft.core.statements.BCStatement` (main's `BCStatement(String...)`
self-registers every tag into `StatementManager.statements`; first tag is save-canonical). Sprites
resolve via `SpriteHolderRegistry.getHolder("buildcraftunofficial:" + path)` through a new
`BCRoboticsSprites` class (`h(String)` helper, mirroring `BCCoreSprites`).

Icons are git-mv'd from `misc/unused_textures/triggers/` to
`src/main/resources/assets/buildcraftunofficial/textures/triggers/` (the 1.12.2-era texture names —
several differ from the 7.1.x logical names; mapping below is authoritative):

| Statement (instance) | 7.1.x tag(s) | Icon file |
|---|---|---|
| `ActionRobotFilter` | `buildcraft:robot.work_filter` | `action_robot_filter` |
| `ActionRobotFilterTool` | `buildcraft:robot.work_filter_tool` | `action_robot_filter_tool` |
| `ActionRobotGotoStation` | `buildcraft:robot.goto_station` | `action_robot_goto_station` |
| `ActionRobotWakeUp` | `buildcraft:robot.wakeup` | `action_robot_wakeup` |
| `ActionRobotWorkInArea` (WORK / LOAD_UNLOAD) | `buildcraft:robot.work_in_area` / `load_unload_area` | `action_robot_work_in_area` / `action_robot_load_unload_area` |
| `ActionStationAcceptFluids` | `buildcraft:station.accept_fluids` | `action_station_accept_fluids` |
| `ActionStationAcceptItems` | `buildcraft:station.accept_items` + alias `buildcraft:station.drop_in_pipe` | `action_station_drop_in_pipe` |
| `ActionStationForbidRobot` (normal / invert) | `buildcraft:station.forbid_robot` / `force_robot` | `action_station_robot_forbidden` / `action_station_robot_mandatory` |
| `ActionStationProvideFluids` | `buildcraft:station.provide_fluids` | `action_station_provide_fluids` |
| `ActionStationProvideItems` | `buildcraft:station.provide_items` | `action_station_provide_items` |
| `ActionStationRequestItems` | `buildcraft:station.request_items` | `action_station_request_items` |
| `ActionStationRequestItemsMachine` | `buildcraft:station.provide_machine_request` | `action_station_machine_request` |
| `TriggerRobotInStation` | `buildcraft:robot.in.station` (dotted) | `trigger_robot_in_station` |
| `TriggerRobotLinked` (plain / reserved) | `buildcraft:robot.linked` / `reserved` | `trigger_robot_linked` / `trigger_robot_reserved` |
| `TriggerRobotSleep` | `buildcraft:robot.sleep` | `trigger_robot_sleep` |

18 files moved, none left behind (the 21-file `misc/unused_textures/triggers/` set keeps
`action_robot_inventory`, `action_station_allow_craft` — not part of the 22).

**Parameter widgets** — `StatementParameterRobot` (tag `buildcraft:robot`; onClick cycles boards via
`RobotUtils.getNextBoard` + `ItemRobot.createRobotStack`, converted from 7.1.x's mutating style to
the modern functional `onClick` that **returns** the new param; the gate GUI's
`GuiElementStatementParam.onMouseClicked` calls `param.onClick(container, ref.get(), heldStack,
new StatementMouseClick(0, false))` and sets the result back — verified live on main) and
`StatementParameterMapLocation` (tag `buildcraft:maplocation`; accepts only `IMapLocation` stacks).
The request-count widget `StatementParameterItemStackExact` (tag `buildcraft:stackExact`) is robotics-
only, so it lives in `buildcraft.robotics.statements` (divergence from 7.1.x's core.statements —
noted). Its 7.1.x ±1/±16 mouse-button/shift wheel must be converted to functional style.

## Lang wiring

All 15 keys exist orphaned in `en_us.json`; `getDescription()` localizes them explicitly
(`LocaleUtil.localize("gate.action.robot.<...>")`), which is the modern convention. One 7.1.x typo
is fixed: `ActionStationProvideFluids` localizes `gate.action.station.povide_fluids` → the key in
`en_us.json` becomes `gate.action.station.provide_fluids` (orphan key, zero compat cost).

## Providers

`RobotsActionProvider` (enum INSTANCE) and `RobotsTriggerProvider` implement
`IActionProvider`/`ITriggerProvider` with `addInternalTriggers(Collection, container)` + no-op
sided/external. The trigger provider gates its 4 triggers on the container being a station-bearing
pipe (`RobotsTriggerProvider` 7.1.x semantics: only when stations exist on the pipe). Registration:
`BCRobotics.init` calls `StatementManager.registerTriggerProvider/registerActionProvider` +
`registerParameter(...)` for the two widgets (pattern: `BCCoreStatements` / `BCTransportStatements`
hold static final instances; a new `BCRoboticsStatements` does the same).

## API pins verified on main

- `GateLogic.statements[i]` is a `FullStatement<IStatement>` pair; `trigger.set(stmt)` / `set(j, param)`
  are public — game tests configure gates directly (no set-trigger API on GateLogic).
- `StackUtil.isMatchingItemOrList(filter, toTest)` **already exists** (IList-aware) — `ArrayStackOrListFilter`
  delegates to it; `buildcraft.api.items.IList.matches(stackList, item)` is live.
- `StatementMouseClick(int button, boolean shift)` matches 7.1.x semantics.
- `DockingStation` ctor `(BlockPos, Direction)` is Level-free and `getActiveActions()` abstract —
  JUnit predicate tests use a hand-rolled test station subclass (no Mockito dependency needed).
- `MockRobotAccess.getBoard()` returns null — gains a `setBoard(RedstoneBoardRobot)` setter for the
  forbid-robot tests.

## Tests (red baseline = skeletons + failing tests, per phase convention)

**JUnit** (new `buildcraft.robotics.statements` test package, `VanillaSetupBaseTester` where stacks
are constructed):

1. `ActionRobotFilterTest` — `canInteractWithItem` truth table: no actions → false; matching filtered
   action → true; matching action with empty params → true; non-matching class → false;
   `getGateFilter` merge (FilterTool fallback).
2. `ActionStationProvideItemsTest` — `canExtractItem`: no ProvideItems → true; ProvideItems with empty
   params → true; filtered ProvideItems → false (unless the item matches).
3. `ActionStationForbidRobotTest` — `isForbidden`: no forbid actions → false; matching param → true;
   invert flips; `force_robot` alias instance flips; matches by board id (via `setBoard`).
4. `ActionRobotWorkInAreaTest` — `getArea(StatementSlot)` math for each AreaType with a constructed
   map-location param (needs an `IMapLocation` stack; `ItemMapLocation` on main).
5. `RoboticsStatementSweepTest` — blanket: every statement instance registers all its tags in
   `StatementManager.statements` (including the `drop_in_pipe` alias); min/max parameters;
   `createParameter()` in-bounds; NBT round-trip of every statement + widget (including
   `StatementParameterItemStackExact` counts and `StatementParameterMapLocation` zones);
   `getDescription()` localizes a non-`gate.trigger.robot.<tag>`-looking key without throwing.
6. `RoboticsParameterTest` — `StatementParameterRobot.onClick` cycles boards and creates robot stacks
   (red until the widget is real); `StatementParameterMapLocation.onClick` accepts map stacks,
   rejects non-maps.
7. `RoboticsProvidersTest` — after mod init (FML-JUnit boots the mod), the two providers are
   registered in `StatementManager`; the action provider yields the 14 actions; the trigger provider
   yields the 4 triggers only for station pipes (needs a `TilePipeHolder`-bearing container — game
   test if not mockable).

**Game tests** (new, registered + manifested):

- `robot_gate_trigger_sleep` — real pipe holder + `PluggableGate`, `statements[0].trigger.set(TriggerRobotSleep)`,
  docked robot asleep → triggerOn; then a `wakeup` action preempts it (exercises `AIRobotSleep.preempt`
  with a null-guarded linked station).
- `robot_station_forbid_robot` — the D1 pin: carrier at a station whose gate forbids its board unloads
  **elsewhere** (replaces/extends the Ph4 `robot_carrier_unloads_at_loaded_station` pin — the permissive
  loop is killed by the refuse-default; the updated pin asserts the unload is refused at the forbidden
  station).
- `robot_goto_station_action` — action on a gate at station B; robot docked at A flies to B and takes
  it as main (takeAsMain flag).

Game-test placement pattern (from `GateRedstoneSyncTester`): `helper.setBlock(pos, PIPE_HOLDER)` →
`helper.getBlockEntity(pos, TilePipeHolder.class)` → `new PluggableGate(...)` →
`tile.replacePluggable(Direction.UP, gate)`.

## NOTICE.md impact

Ported files keep their upstream headers verbatim (MMPL); `NOTICE.md` gains them. New files
(`BCRoboticsStatements`, `BCRoboticsSprites`, tests) carry the 2026 BCU+MPL header. `ActionState`'s
upstream header decides its licence (7.1.x api/ is MIT territory — check the file's own notice at
port time, never assume from the directory). `CopyrightHeaderTester` re-validates both directions.

**Headerless ported files** — `StatementParameterItemStackExact` (7.1.x
`common/buildcraft/core/statements/`) and `StatementParameterMapLocation` (7.1.x
`common/buildcraft/robotics/statements/`) ship bare upstream and are ported bare, so they silently
inherit the root MPL-2.0 default. They CANNOT be recorded in NOTICE.md: its list is machine-validated
both ways, and `CopyrightHeaderTester.noticeFileListsEveryMmplFile` treats any listed path without an
MMPL notice as a stale legal claim (CLAUDE.md's "record headerless ports in NOTICE.md" predates that
reverse check and no longer applies to them). This paragraph is their provenance record.

## Out of scope

`StateStationProvideItems`/`StateStationRequestItems` (7.1.x gate-state display helper) — the modern
gate GUI doesn't render statement-held state; fold their list-holding role into the actions if a
consumer appears. `ArrayFluidFilter` and `ActionRobotFilter.getGateFluidFilter` are dead in 7.1.x
(no consumer) — not ported. `AIRobotGoAndLinkToDock` — covered by the takeAsMain flag.
