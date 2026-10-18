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
   that throw or succeed immediately can use `20`. **A robot test's `max_ticks` must be strictly
   greater than the largest `tickUntil`/`tickUntilThen`/`ConfigScope.succeedWhen` budget in the
   test** (see "Entity and robot tests" below) — `GameTestPollBudgetTester` fails the unit run
   otherwise, so give the poll a literal or a named `static final int` budget it can read.

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

Also keep ALL relative positions inside the test's own arena grid cell — with the
`minecraft:empty` structure the framework spaces arenas 6 apart in X and 7 apart in Z, so x
beyond 5 or z beyond 6 writes into the NEXT test's arena. (z=7 is the next ROW, not the same
one.) Entities added during the test must be discarded before `succeed()` — pass-time cleanup
sweeps only ~1 block, and leaked entities fail unrelated tests; assert dropped items by
identity, not proximity. Full diagnosis: docs/robotics-ph3-design.md, amendment 4.

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
  greater than the poll budget (`GameTestPollBudgetTester` pins this for every robot test: it reads
  the budget from the registered method and any same-file helper it calls, resolves a literal or a
  `static final int`, and fails on a budget it cannot read).
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
  neighbouring chunk whose entities are not loaded yet (measured on 26.2 and 1.21.11: 20 of 50
  samples crossed a chunk border, 6 of 50 were not entity-ticking at the first tick). An entity
  added there is invisible to `getEntitiesOfClass` in the same tick, so a test that adds something
  and scans for it at once polls `isEntityTicking` first (and `forceLoadEntityArena` around the
  position).
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
  first level tick, not on placement) and clean up on timeout, `guard`/`finish` clean up on every other exit.
  Give it its own `test_environment` file (copy `marker_volume_los.json`) and set the manifest's `environment`
  to the test's own id; `MarkerTestIsolationTester` enforces both. Never schedule from inside a scheduled
  runnable (the framework iterates a fastutil map while running them): schedule every poll up front.
- **Any other test that places a marker** (waterlogging, pumps, schematics) calls
  `MarkerArenaUtil.clearMarkers(helper, rel...)` before it ends: in a `finally` for a synchronous test, as the
  last step of the assertion for a `succeedWhen` one. `MarkerTestIsolationTester` fails a test source that
  references `MARKER_VOLUME`/`MARKER_PATH` without it.
- `GameTestHelper.relativePos` inverts the rotation wrongly even for an unrotated arena (26.1.2 returns
  `(-1,2,-1)` for `(1,2,1)`); subtract `absolutePos(BlockPos.ZERO)` instead.

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
