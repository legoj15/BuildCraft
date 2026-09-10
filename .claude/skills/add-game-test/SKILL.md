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
   that throw or succeed immediately can use `20`.

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
  the active-node's (see the Testing section of CLAUDE.md for the convention and the violation
  symptom).
