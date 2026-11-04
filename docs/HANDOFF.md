# Handoff — 2026-11-04 (Fabric support, epic #98)

## Where the work is
- Branch `worktree-bridge-cse_0142NTdi4TY4SQdY9huBdmxu`, pushed to `origin`. It is about 100 commits ahead of
  `origin/main` and **not merged into `main`**. Merging and releasing are the owner's call.
- To continue elsewhere: `git fetch origin worktree-bridge-cse_0142NTdi4TY4SQdY9huBdmxu`, then check it out (or
  merge it into a fresh worktree branch). Re-run `bash scripts/neoforge-sources-sync.sh` there; `.neoforge-ref/` is
  gitignored.
- `wip/fabric-entity-item-fallback-test` (also pushed): one **unverified** game test for the living-entity
  item-storage fallback (`EntityItemStorageTester`). Its agent was stopped before it compiled or ran it. Finish or
  drop it; it is not on the main branch above.

## Done (all on the branch above)
- **Phase 1, the platform layer (docs/platform-layer.md):** slices S2–S16 (#101–#111) are merged.
  - The census is down from 785 live NeoForge references outside `platform/neoforge` to 4, all inside neoforge
    arms, plus 8 allowlisted `ModConfigSpec` uses (Forge Config API Port supplies those on Fabric). Leaks: 0.
  - Behaviour on NeoForge is unchanged. Golden save-format fixtures (S12) pass unmodified on all six nodes.
- **Phase 2, the 26.3-fabric node:**
  - It compiles all of BuildCraft and ships `BuildCraftRedux-<v>+mc26.3-fabric.jar`. Dependencies: Fabric API, Forge
    Config API Port (config route, open question 1 decided), Common Protection API (MIT, jar-in-jar), Team Reborn
    Energy (optional, compile-only).
  - Supporting pieces: the class tweaker, `fabric.mod.json`, the Fabric data transform (`DataFabric.kt`), mixins for
    every NeoForge patch BuildCraft relies on, and Gradle 9.8.2 with Loom 1.19.3 (#113).
- **Verified:**
  - Last full run, `fullTestSuite` plus `:26.3-fabric:runClientGameTest`, was green on all seven nodes:
    - NeoForge: 926 game tests on 1.21.1, 927 on each other node.
    - Fabric: 920 server game tests, plus the client game test, which runs the other 7 test bodies and takes 13
      in-world screenshots (reviewed: pipes, engines, tank oil, bucket fluid colour, GUIs, guide). That is the same
      927 as NeoForge 26.3.
  - The shipped Fabric jar reached "Done" on a stock Fabric 26.3 server with no BuildCraft, mixin or class-tweaker
    warnings.
- **Bugs fixed on the way:**
  - #117: entities were frozen in crude oil and residue on NeoForge 26.1.2+.
  - The Fabric chunk-ticket release unforced chunks that other mods had forced.
  - On Fabric, claim mods were never asked when a fake player clicked an entity.
  - The test harness: #114, #118, #119 and `oil_bobbing_physics`.
  - Machine models defined no `#top`, which produced "Unresolved texture" warnings.
- **Tooling:**
  - The Fabric node compiles with `-Xlint:deprecation,removal -Werror` (`checkDeprecations`, in `nodeVerification`).
  - The version-check hook now tracks the Fabric Loader and Fabric API pins.
  - README and AGENTS.md document the Fabric node.

## Next
- **#112 (owner's machine):** the release harness `release/modules/BcRelease.psm1` must skip non-NeoForge nodes, or
  the next release uploads the 26.3 NeoForge jar a second time. This must land before any release run.
- **#120:** report the Fabric API client game-test runner race upstream (outward-facing, needs a human). The draft
  text is in the issue. Remove `ClientGameTestThreadStartMixin` once it is fixed upstream.
- Decide on merging into `main` and pushing. The issues closed by these commits (#99–#119) close when they reach
  `main`.
- Finish or drop the WIP branch above.
- **An independent read-only review of `platform/fabric/**` was started and stopped before it reported.** Worth
  re-running. Its brief: client classes on dedicated-server paths, thread safety, static maps keyed by level and
  never cleared, conflict-prone mixins and fluid physics affecting other mods' fluids in the `minecraft:water` tag,
  Transaction misuse, and droplet rounding.
- Fabric gaps that remain (docs/platform-layer.md, the "As built" sections):
  - No accessory slot for the goggles: no 26.3 Fabric Curios, Trinkets or Accessories build exists.
  - Fluids move in whole millibuckets.
  - Spawn data arrives one packet after the spawn.
  - Creative tabs follow registration order.
  - Minor event narrowings, all documented.

## Open questions / leads (unverified)
- Placement on Fabric is put back after the block's own side effects run, unlike NeoForge's deferred capture.
  Documented in `FabricProtection`.
- `fabric.mod.json` dependency floors equal the pinned dev versions. They have not been bisected.

## Uncommitted changes
- None. Stray agent worktrees under `.claude/worktrees/agent-*` can be cleaned up; all their wanted work is merged
  apart from the WIP branch above.
