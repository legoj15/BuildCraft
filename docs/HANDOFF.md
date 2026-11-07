# Handoff — 2026-11-05 (Fabric support, epic #98: review round)

## Where the work is
- The platform layer (Phase 1), the `26.3-fabric` node (Phase 2) and their fixes up to `eb44a3ec3` are in `main` and
  `origin/main`. #98 and its slices #99–#119 are closed on GitHub (#112 included: its fix is in the gitignored release
  harness `release/modules/BcRelease.psm1`, so it exists on the owner's machine only).
- A read-only review of `platform/**` and the Fabric node followed. Twelve fix packets answered it (neo-fluids,
  fabric-protection, events-bus, oil-spread, fabric-lifecycle, neo-items-transfer, fabric-transfer,
  fabric-physics-client, build-scripts, platform-contracts, tests-misc, client-misc). They are merged on the
  integration branch `claude/review-98-fixes` (`0bc21ba64`), with the docs packet on top and two fixes from the
  third-party review of `0bc21ba64` (`a40cdd037` logs a refused hand-back, `c9b519984` refuses a mixed fill/drain
  on a simulate-only view; 26.3, 1.21.1 and Fabric game tests green on `c9b519984`). None of
  that is in `main` yet; merging it is the orchestrator's call.
- To continue elsewhere, check that branch out. Re-run `bash scripts/neoforge-sources-sync.sh` there; `.neoforge-ref/`
  is gitignored.

## Done
- **Phase 1, the platform layer (docs/platform-layer.md):** slices S2–S16 (#101–#111).
  - The census is down from 785 live NeoForge references outside `platform/neoforge` to 4 (all inside neoforge arms),
    plus 11 allowlisted ones: the `ModConfigSpec` uses (Forge Config API Port supplies those on Fabric) and the
    permanent `api-events-on-neoforge-bus` entry. Leaks: 0 (re-run on `0bc21ba64`).
  - Behaviour on NeoForge is unchanged except the #117 fix (below). Golden save-format fixtures (S12) pass unmodified
    on all six nodes.
- **Phase 2, the 26.3-fabric node:**
  - It compiles all of BuildCraft and ships `BuildCraftRedux-<v>+mc26.3-fabric.jar`. Dependencies: Fabric API, Forge
    Config API Port (config route, open question 1 decided), Common Protection API (MIT, jar-in-jar, listed in
    NOTICE.md), Team Reborn Energy (optional, compile-only).
  - Supporting pieces: the class tweaker, `fabric.mod.json`, the Fabric data transform (`DataFabric.kt`), mixins for
    every NeoForge patch BuildCraft relies on, and Gradle 9.8.2 with Loom 1.19.3 (#113).
- **Verified (before the review round):**
  - The last full run, `fullTestSuite` plus `:26.3-fabric:runClientGameTest`, was green on all seven nodes:
    - NeoForge: 926 game tests on 1.21.1, 927 on each other node.
    - Fabric: 920 server game tests, plus the client game test, which runs the other 7 test bodies and takes 13
      in-world screenshots (reviewed: pipes, engines, tank oil, bucket fluid colour, GUIs, guide).
  - The shipped Fabric jar reached "Done" on a stock Fabric 26.3 server with no BuildCraft, mixin or class-tweaker
    warnings.
- **Verified (after the review round, integrated sha `0bc21ba64`, 2026-11-05):** `fullTestSuite` green on all seven
  nodes: NeoForge 943 game tests on 1.21.1, 944 on each other node, unit tests clean; Fabric 937 server game tests.
  `:26.3-fabric:runClientGameTest` was not re-run.
- **Bugs fixed on the way:**
  - #117: entities were frozen in crude oil and residue on NeoForge 26.1.2+.
  - The Fabric chunk-ticket release unforced chunks that other mods had forced; its owner map was never cleared on
    level unload.
  - On Fabric, claim mods were never asked when a fake player clicked an entity.
  - The test harness: #114, #118, #119 and `oil_bobbing_physics`.
  - Machine models defined no `#top`, which produced "Unresolved texture" warnings.
- **Review round (2026-11-05), what it changed:**
  - NeoForge, restoring what the first port had changed against `main`: a light oil or fuel source resting on its own
    fluid spreads on every open side again; BuildCraft's API events are posted on the NeoForge bus again (open
    question 3 taken); BuildCraft's tab items follow other mods' default-priority additions again; 1.21.1 inventories
    are `IItemHandlerModifiable` again, with the live `getStackInSlot`; BuildCraft tanks on 1.21.10+ throw
    `IllegalArgumentException` for an empty or negative offer again; and a few smaller details in platform-layer.md.
  - Fabric: claim-mod asks now match NeoForge's order and ask providers alone for placements (no spawn protection or
    border), a refused two-block placement drops nothing, late-registered blocks reach the soft-block and wrench
    scans, an already-loaded config spec runs its load hook, the config screen is offered to Mod Menu, the
    cauldron moves whole buckets only, dropped items bob in oil and boats sink in it, the shipped dependency
    floors are separate from the dev pins, and the version-check hook survives a NeoForge outage.
  - Docs: platform-layer.md now records every accepted trade-off and gap named below; README's Fabric section is
    player-level; NOTICE.md lists the bundled Common Protection API and no longer carries a stale file count;
    `docs/stoncutter.md` has the loader axis.
  - Header judgement: `NeoForgeFluidContainers` and `NeoForgeFlowingFluids` keep the 2026 contributors header. Their
    text is NeoForge Transfer API and `FluidType`/`BaseFlowingFluid` code written for this fork; the 1.12.2 files they
    were split out of (`FluidUtilBC`, `WidgetFluidTank`, `BCEnergyFluids`) share no expression with it (1.12.2 used
    `BlockFluidClassic` and the Forge fluid handler API), so AGENTS.md's mixed-file rule does not apply.

## Next
- Planned work lives in GitHub Issues (`issues.sh ready`). The Fabric items open at the time of writing: #120 (report
  the client game-test race upstream; remove `ClientGameTestThreadStartMixin` once fixed), #121 and #122 (both
  `[unconfirmed]`), #123 and #126 (harden the
  entity-item game test). The deferred
  review-round items are filed by the orchestrator.
- Housekeeping, tracked nowhere: stray agent worktrees under `.claude/worktrees/agent-*` can be cleaned up once their
  work is merged.
- Fabric gaps that remain (docs/platform-layer.md, the "As built" sections and the Fabric gap lists):
  - No accessory slot for the goggles: no 26.3 Fabric Curios, Trinkets or Accessories build exists.
  - Fluids move in whole millibuckets.
  - Spawn data arrives one packet after the spawn.
  - Creative tabs follow registration order, with no late-addition hook.
  - No block-break particles on the tube.
  - Minor event narrowings, all documented.

## Open questions / leads (unverified)
- Placement on Fabric is put back after the block's own side effects run, unlike NeoForge's deferred capture.
  Documented in `FabricProtection`.
- The #117 override is inert on the NeoForge 26.1.0 beta (older `move` signature); 26.1.1 is unverified.

## Uncommitted changes
- None.
