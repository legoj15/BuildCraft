---
name: add-game-test
description: Adding, changing, or debugging a NeoForge game test in BuildCraft (any *Tester.java under src/test plus its test_instance manifest). The three-part wiring (Java method, registration, manifest JSON), arena geometry limits, tick-latency pitfalls, player-state limits, and how to verify the test actually runs. Read this BEFORE writing the first line of a new game test.
---

# Adding a game test in BuildCraft

Adding a game test in MC 1.21.10+/26.x takes **three** things, not two. If you do only the first
two it silently skips — there is no error, no warning, and `runGameTestServer` keeps reporting
"N GAME TESTS COMPLETE" without your test included in N. This footgun had silently disabled 34
registered tests until a manifest sweep caught it, which is why it is now enforced:
[GameTestManifestTester](../../../src/test/java/buildcraft/GameTestManifestTester.java) fails the
unit-test run when a registration and its manifest disagree in either direction. That guard is
the safety net — this skill is the manual.

## The three parts

1. **Java method** — static, signature `void name(GameTestHelper helper)`, throws on failure,
   calls `helper.succeed()` (or one of the async `succeedWhen*` variants) on pass. Put it in a
   `*Tester.java` class under the right subsystem subpackage of `src/test/java/buildcraft/`.

2. **Registration in
   [BuildCraftGameTests.registerAll](../../../src/test/java/buildcraft/BuildCraftGameTests.java)** —
   one line like:
   ```java
   reg.accept("buildcraftunofficial:your_test_id", () -> buildcraft.transport.YourTester::yourMethod);
   ```
   `registerAll` is the SINGLE source of truth for every node line; each node only differs in the
   registrar lambda it passes, so never register anywhere else.

3. **Test-instance manifest JSON** at
   `src/test/resources/data/buildcraftunofficial/test_instance/<your_test_id>.json` — the test ID
   (no namespace) MUST match the file name and the `function` field. For most tests the body is
   just:

   ```json
   {
       "type": "minecraft:function",
       "function": "buildcraftunofficial:your_test_id",
       "environment": "minecraft:default",
       "structure": "minecraft:empty",
       "max_ticks": 100,
       "setup_ticks": 0,
       "required": true
   }
   ```

   `structure` can point at a saved arena structure if the test needs a pre-built world;
   `minecraft:empty` gives you a void arena to `helper.setBlock(...)` into. `max_ticks` is the
   watchdog timeout — async tests with `succeedWhen*` need enough headroom; synchronous tests
   that throw or succeed immediately can use `20`. **Any test's `max_ticks` must be strictly
   greater than the largest `tickUntil`/`tickUntilThen`/`ConfigScope.succeedWhen`/marker
   `await`/`awaitThen` budget in the test (a marker one counts `CELL_READY_TICKS` on top of its own), or than `DropArenaUtil.POLL_TICKS` for one that calls
   `awaitDrops`, or than `BuilderRefloodTester.DRIVE_POLL_TICKS` for one that calls `drive`** (see
   "Entity and robot tests" below) —
   `GameTestPollBudgetTester` fails the unit run otherwise, so give the poll a literal, a named
   `static final int`, the shared `EntityArenaUtil.CELL_READY_TICKS` (written qualified), or a `+` sum of
   those; a budget computed from production code is unreadable to it.

## The 1.21.1 node is different (but the manifest still matters)

1.21.1 predates the dynamic `Registries.TEST_FUNCTION` registry and has no native
`test_instance` support: `onRegisterGameTests` reflects each registration straight into
`GameTestRegistry.TEST_FUNCTIONS` against the `buildcraftunofficial:empty` arena
(`data/buildcraftunofficial/structure/empty.nbt` — a 16x16x16 air stand-in for
`minecraft:empty`, sized so `encaseStructure` has room for the oil-gen surface scan, with
`skyAccess=true` so no barrier ceiling blocks top-down scans). Its watchdog `max_ticks` is read
from the SAME JSON manifests the modern nodes consume — so a missing manifest degrades 1.21.1
too, and every manifest field you write is live on every node. You never write any of this
yourself; adding to `registerAll` is all three nodes at once.

## Never assert on a fixed tick after placing blocks/entities

Even force-loaded chunks take a variable 1–3+ ticks to become block/entity-ticking
(`setChunkForced` adds the ticket; promotion happens later), and the arena grid lands at a
random world position every run, so the same test passes or flakes run to run. Gate on observed
state (`EntityArenaUtil.tickUntil`/`tickUntilThen`, or poll for your own registration) instead
of `runAfterDelay(N)`.

The same goes for a poll's BUDGET: anything that needs a chunk to tick (a block entity, a robot, a Builder, a
fluid's scheduled tick after `BlockWaterloggingTester.placeSource`) must not start counting at the test's first
tick, because the framework forces only the structure's chunk and a 6x7 cell straddles a chunk border in about
57% of runs. Call `EntityArenaUtil.forceLoadCell(helper)` first, then poll with
`tickUntil(helper, READY + WORK, whenCellTicking(helper, READY, WORK, condition), ...)` (`READY` is the shared
`EntityArenaUtil.CELL_READY_TICKS` = 300, written qualified so the guard reads it, and `WORK` a named
`static final int` of the test's own file) and keep the manifest's `max_ticks` above `READY + WORK` (the repo's
convention is 10 above). A helper that polls through `helper.succeedWhen` or its own `runAfterDelay` loop is the
same trap: the phased Builder tests go through `BuilderRefloodTester.drive`, whose step argument counts from the cell
ticking and whose whole budget, `DRIVE_POLL_TICKS`, the guard reads for every test that calls it.

**Never schedule from inside a scheduled runnable** (a `runAfterDelay`/`tickUntil` call written
inside the lambda of another one): `GameTestInfo.tickInternal` runs those runnables while iterating
the fastutil map the new entry goes into, and an insert that rehashes it can skip or double-run
another entry, a flake that appears only once enough runnables are pending. A multi-phase test queues
every phase from the test body at an absolute delay (`runAfterDelay(1 + 20, ...)`, looking the tile
up inside each), or waits on observed state with a poll (`ElectronicLibraryTester.runSteps` is a
step runner built that way). `GameTestNestedSchedulingTester` fails on a lexical nesting; the
framework's own sequences (`helper.succeedWhen`, `startSequence`) are ticked outside the map loop and
are fine.

Also keep ALL relative positions inside the test's own arena grid cell — with the
`minecraft:empty` structure the framework spaces arenas 6 apart in X and 7 apart in Z, so x
beyond 5 or z beyond 6 writes into the NEXT test's arena. (z=7 is the next ROW, not the same
one.) Entities added during the test must be discarded before `succeed()` — pass-time cleanup
sweeps only ~1 block, and leaked entities fail unrelated tests; assert dropped items by
identity, not proximity. Full diagnosis: docs/robotics-ph3-design.md, amendment 4.

A test that genuinely needs a floor wider than its cell (the oil pool tests lay a 24x24 one) builds
it at `EntityArenaUtil.ABOVE_ARENAS_Y`, never at the arena's own level: the arenas of a batch run
at the same time, and a wide floor at y=1 overwrote the chest of `pipe_force_insert_entry_leg`
(stone one tick in, the item dropped, a 200-tick timeout) whenever it was laid after that test had
built its rig. `oilgen_surface_pool_floor_spares_arena_cells` pins it for the pool floor. A victim
that checks its own rig each tick (as the force-insert test does) turns this class of flake into a
named failure instead of a timeout.

## Entity and robot tests

Everything here lives in `EntityArenaUtil` (`src/test/java/buildcraft/lib/test/`) and
`ZonePinnedRobot` (`src/test/java/buildcraft/robotics/`).

- **Clean up in the poll, not at the end of the body.** `tickUntil`, `tickUntilThen` and
  `ConfigScope.succeedWhen` each have an overload taking a trailing `Runnable cleanup`, run exactly
  once on EVERY way out: after the success body (passing or throwing), when the condition throws,
  and on the poll's own timeout. Discard the test's entities there
  (`() -> EntityArenaUtil.discard(robot[0], item)`, null-safe, so it works for a robot spawned
  mid-poll). A `discard()` as the last line of the success body is skipped by the first failed
  assertion, and a robot left in the shared level keeps its station reservations and keeps flying
  into later tests. Pass the cleanup on every robot test that spawns something.
- **The framework's own timeout skips that cleanup.** If `max_ticks` fires before the poll's last
  tick, the framework fails the test and the cleanup never runs. So `max_ticks` must be strictly
  greater than the poll budget (`GameTestPollBudgetTester` pins this for every registered test: it reads
  the budget from the registered method and any same-file helper it calls, resolves a literal, a
  `static final int` or a `+` sum of those, and fails on a budget it cannot read).
- **A robot test that runs a board's `update()` must be zone-pinned.** A board with no zone
  searches the whole dimension: a picker takes a sibling arena's drops, a carrier or delivery robot
  flies to a sibling's station, a lumberjack chops a sibling's tree, and it is the OTHER test that
  fails. Spawn it with `ZonePinnedRobot.spawn(helper, relPos, boardNbt)` (or
  `new ZonePinnedRobot(helper, boardNbt)` when you place it yourself): both its work zone and its
  load/unload zone are the test's own 6x7 cell (`EntityArenaUtil.cellZone`). Variations are chained
  setters, never a private `EntityRobot` subclass: `new ZonePinnedRobot(level, board, zone)` for an
  explicit work zone, `.loadUnloadZone(zone-or-null)`, `.parkedAt(station)` (docked and linked),
  `.registry(registry)` (an isolated registry). `ZonePinnedRobotUseTester` fails on a bare
  `new EntityRobot(level, board)` and on a test-local subclass overriding the zone getters. The one
  exception is a test whose point is a zone supplied by the home station's gate actions (Work in
  Area / Load/Unload Area): it builds its robot the way `ItemRobot.useOn` does and is listed in
  that tester's allowlist. Recharge is zone-free by design (`AIRobotRecharge` searches every
  powered station), so the pin does not narrow a recharge search: such a test stays hermetic through
  its own `test_environment` and the nearest-station rule.
- **Gate a same-tick scan on `EntityArenaUtil.isEntityTicking(helper, relPos)`.** The framework
  waits only for the 1x1 structure's own chunk, so a position a couple of blocks in can sit in a
  neighbouring chunk whose entities are not loaded yet (measured on 26.2 and 1.21.11: 6 of 50
  samples were not entity-ticking at the first tick). An entity
  added there is invisible to `getEntitiesOfClass` in the same tick, so a test that adds something
  and scans for it at once polls `isEntityTicking` first (and `forceLoadEntityArena` around the
  position).
- **A test whose work spans the cell starts its budget when the whole cell ticks.** The 6x7 cell
  crosses a chunk border in about 57% of runs (5/16 in x, 6/16 in z, at a random block origin), and
  the half the robot is not in starts ticking
  later (measured on 26.3 with only the robot's chunk forced: far corners at tick 20-23, the robot's
  at tick 1), far later when several servers share the machine. A poll budget that starts at tick 1
  then measures the machine: `robot_break_canopy_tree_vines_fall_with_decay` and
  `robot_picker_picks_up_dropped_item` flaked that way. Call `EntityArenaUtil.forceLoadCell(helper)`
  (the 5x5 around every chunk the cell touches; with it every corner ticked on tick 1 in 48 of 48
  arenas) and wrap the condition: `tickUntil(helper, READY + WORK, whenCellTicking(helper, READY,
  WORK, cond), ...)`. The wrapper answers false until `isCellEntityTicking`, then gives `cond`
  `WORK` polls, and fails a cell that never ticks as the machine's fault, not the robot's. A fifth
  argument, a `Supplier<String>`, is asked only when `WORK` runs out and its text joins the failure:
  use it to name the side, source or item that never came. Write
  `READY + WORK` as `EntityArenaUtil.CELL_READY_TICKS + WORK` (WORK a literal or constant) so
  `GameTestPollBudgetTester` can read it, and keep `max_ticks` above it. `EntityArenaReadinessGateTest` pins the budget arithmetic. Put the spawn of the robot AND of
  everything it must see inside the gated condition: a drop added to a chunk that does not tick yet is invisible to
  the picker's first scan, which then sleeps for 60 seconds (`robot_station_unloaded_home_keeps_robot_working` failed
  about one solo run in eight that way, reading as "froze"). A fluid test needs the same: a scheduled fluid tick runs
  only in a ticking chunk (`placeSource`'s tick is kept if the fluid already scheduled its own: 5 ticks for water, 30
  for lava), `BlockWaterloggingTester.buildBasin` force-loads the cell, and `waterloggable_marker_holds_back_lava`
  waits with `whenCellTicking`.
- **Which polls need that gate.** Every poll whose condition waits on something that ticks (a robot's first tick, a
  station registering on its pipe's tick, an item crossing a pipe, a comparator following a block entity, a drop that
  must become visible to a scan) gets `forceLoadCell` and `whenCellTicking`, a one-block rig included: its chunk's
  promotion is the machine's clock, and a budget counted from tick 1 charges it to the code under test. Two kinds
  do not: a poll whose condition is itself `isEntityTicking`/`isCellEntityTicking` (its whole budget is the
  readiness wait and its body is synchronous), and a poll that drives the machine by hand on every poll tick (the
  Quarry and Filler tests call `tick()` themselves, so no chunk's ticking matters to them). A fixture that polls for
  every test of its file (`whenStationRegistered`) gates once, inside the fixture, and every manifest of those tests
  gets the larger `max_ticks`. A hand-rolled `runAfterDelay` loop is the same trap as `tickUntil` and is invisible
  to `GameTestPollBudgetTester`: convert it to `tickUntil` (`UpstreamTailRegressionTester` did, and so did
  `ElectronicLibraryTester.runSteps`, whose per-test budget is enforced by the gate while the poll's own, guard-read
  budget is `CELL_READY_TICKS + LONGEST_STEPS_TICKS`), so the guard reads it. The same goes for a fixed
  `runAfterDelay(N)` that waits N ticks of a machine's OWN running (an engine's heat bleed, a fluid's flow, a
  settle after a setup): its clock must start when the cell ticks, so make the setup the first phase of
  `tickUntilThen(..., whenCellTicking(...), setup, N, check, ...)` (`EngineTester`'s overheat and recovery tests,
  `BlockFrameTester`); a check that nothing "slid back" is otherwise vacuous on a machine that never ran. A delay that
  only waits for a tile to exist and then reads predicates or calls `serverTick` by hand is not the trap.
- **A "break a block, check its drops" test goes through `DropArenaUtil.awaitDrops(helper, act, drops)`.**
  Never `runAfterDelay(10, ...)` after the break: `filtered_buffer_drops` failed that way (the drop was
  invisible on tick 10 in a cell chunk that was not entity-ticking yet), and a fixed wait long enough for that is
  too long for a fragile stack, which drifts out of a one-block scan after about ten ticks. The harness forces the
  cell, waits for it to tick, runs `act` (place, fill, `breakBlock`/`removeBlock`) in one tick, polls until every
  `present`/`exactly` drop is there, settles 5 more ticks, and only then asserts (`absent` and exact totals
  included, so a late duplicate or a stray block is caught); it discards the cell's items on every exit. Put the
  whole place-and-break inside `act` (nothing ticks between filling a machine and breaking it), describe the
  result with `Drops.near(pos, radius).present(...).absent(...).alsoNear(...).exactly(item, n)`, and give the
  manifest `max_ticks` above `POLL_TICKS` (400 today). Counts are totals, not entities: stacks merge, and only
  items inside the test's own cell count. A drop popped into a block that stays (a plug or wire clicked off a
  pipe) ejects itself at up to 0.3 blocks a tick, so keep that block two blocks from every cell edge:
  `pipe_pluggable_break` and `pipe_wire_break` lost their drop across the edge from (1,2,1).
- **Sweep and scan inside the cell with `EntityArenaUtil.cellBounds(helper)`.** It is the 6x7 cell
  as an entity box (the block cells' outer faces); any wider sweep reaches into the neighbouring
  arenas and discards THEIR drops. Use `cellZone` for a robot zone and `cellBounds` for a sweep.

## Tests that place markers (or touch any other dimension-wide cache)

Marker connections live in `VolumeCache`/`PathCache`: singletons keyed only by dimension, scanned up to
`markerMaxDistance` (64) blocks in a straight line **across arenas**, counting unloaded markers too. The
runner never clears the grid between batches, so a marker any test leaves behind is a connection candidate
and a line-of-sight obstacle for every later marker test that shares a row, column or height with it (this
was the 2026-09-26 flake: orientation, volume_los and triangulation_3d failing together). A private
`test_environment` only stops marker tests running *concurrently*; it does nothing about leftovers.

- **A marker test** goes through `MarkerArenaUtil` (`src/test/java/buildcraft/lib/test/`): `open` evicts
  everything already in scan range (WARN-logged, so the polluter is findable), `Session.place` refuses
  coordinates outside the 6x7 cell, `await`/`awaitThen` gate on the marker joining the cache (it does on the
  first level tick, not on placement) and clean up on timeout, `guard`/`finish` clean up on every other exit. Their
  budget starts when the whole cell is entity-ticking (`open` force-loads it), so the poll runs for up to
  `CELL_READY_TICKS + budget` ticks: the guard adds that itself, so the test passes its own `REGISTER_TICKS`-style
  budget and the manifest's `max_ticks` sits above the sum (350 for a 40 tick budget). A wait that runs before the
  arena opens (strays planted to be evicted by `open`) goes through `MarkerArenaUtil.openWithoutEviction` and `adopt`,
  so it is gated and cleaned up the same way.
  Give it its own `test_environment` file (copy `marker_volume_los.json`) and set the manifest's `environment`
  to the test's own id; `MarkerTestIsolationTester` enforces both. Never schedule from inside a scheduled
  runnable (the framework iterates a fastutil map while running them): schedule every poll up front.
- **Any other test that places a marker** (waterlogging, pumps, schematics) calls
  `MarkerArenaUtil.clearMarkers(helper, rel...)` before it ends: in a `finally` for a synchronous test, as the
  last step of the assertion for a `succeedWhen` one. `MarkerTestIsolationTester` fails a test source that
  references `MARKER_VOLUME`/`MARKER_PATH` without it.
- `GameTestHelper.relativePos` inverts the rotation wrongly even for an unrotated arena (26.1.2 returns
  `(-1,2,-1)` for `(1,2,1)`); subtract `absolutePos(BlockPos.ZERO)` instead.

## Tests that need an absent chunk (or one that unloads)

A test of code that must never load a chunk (a machine that reads or changes a cell beside a chunk border) needs a
chunk whose neighbour is *known* to be absent. Build it on `FarChunk` (`src/test/java/buildcraft/lib/test/FarChunk.java`),
never on `setChunkForced`:

- **`setChunkForced` is not isolated.** Its ticket reaches two chunks out, so the ring around the chunk is queued to
  load on other threads, and "the chunk next door is absent" then passes or fails with what ran before it
  (`pump_fill_skips_unloaded_chunk` failed two full runs in six on its "must stay absent" precondition).
  `FarChunk.load` uses a plain `getChunk`, whose ticket is at the chunk alone and lasts for the rest of the tick:
  build, drive and tear down inside one tick, no polls. `FarChunk.hold` pins the chunk across ticks until `release`,
  for a test that waits for the chunk beside it to unload (`pump_drain_rechecks_reach_when_chunk_unloads`).
  `FarChunk.queue` makes a chunk that is ticketed but not in memory (`hasChunk`/`isLoaded` true, `getChunkNow`
  null): the case that proves a gate tests presence, not the ticket level (`robot_search_skips_queued_chunk`).
- **Every test gets its own offset**, at least 8 chunks from every other (the list is in `FarChunk`'s javadoc): the arenas
  of a run sit on one grid some fifty chunks across, so two tests on one offset can land on neighbouring chunks and the
  first one's chunk is still loaded when the second asserts that its neighbour is absent.
- **Keep the machine two blocks in from the border.** A block entity's own `setChanged` (the pump's runs every ten
  ticks, on a random tick of the ten, so in about one run in ten) lets vanilla tell the comparators around it, and past a
  solid neighbour that reads the cell beyond with no chunk check: a pump one block from the border loaded the chunk
  beside it.
- **Write every cell the test depends on** (`FarChunk.setRaw`, no block update): the chunk is real worldgen, so what stands
  in it is not controlled. Place a fluid the normal way and `LiquidBlock.onPlace` reads its neighbours.
- A test that merely runs code which now refuses to touch a chunk that is not in memory (the robot pump AI and its
  board, `BlockUtil.drainStaysInLoadedChunks`) must have its neighbours loaded: `EntityArenaUtil.forceLoadCell(helper)`
  first, because the framework loads only the one chunk under the arena.

## To verify your test is actually running (not silently skipped)

Note the "N GAME TESTS COMPLETE" count before and after. Each new test should bump N by 1. If it
doesn't, the manifest is missing or its `function` field doesn't match the registered ID (the
unit-test guard `GameTestManifestTester` should already have caught this — if it didn't, check
that your `reg.accept` line follows the exact call shape it scans for). Confirm by temporarily
making the test throw — if the failure shows up in the "required tests failed" list, it's wired
correctly; if it doesn't, fix the wiring first before debugging the test logic.

## Player-state testing limitation

`GameTestHelper.makeMockPlayer(GameType)` returns an anonymous `Player` (see
[GuiTester.java](../../../src/test/java/buildcraft/lib/test/gui/GuiTester.java)), NOT a
`ServerPlayer`. Anything guarded by `instanceof ServerPlayer` (including
`AdvancementUtil.unlockAdvancement(Player, …)`) short-circuits silently. Test the predicates
and the wiring around player-state calls; the final award/tracker write needs in-client
verification.

## Related traps (unit tests, not game tests)

- A unit test that constructs `ItemStack`s must extend `VanillaSetupBaseTester` — default item
  components bind only during server resource load, so a bare test dies with "Components not
  bound yet" on 26.x nodes while passing on 1.21.x.
- Shared TEST sources with Stonecutter directives: only ONE branch may be live, and it must be
  the active-node's (see the Testing section of AGENTS.md for the convention and the violation
  symptom).
