# Handoff — 2026-11-02 (#81 + heat exchanger uniformity, both on main)

## Done
- #81 (plug/pipe baker caches reset before the atlas swap; dead PlugGateBaker cache) fixed in `0a654d481`, merged as `5c98b16ae`. It is on `origin/main` and GitHub closed #81.
- Every model-derived sprite cache now resets on `ModelEvent.BakingCompleted` (after the atlas upload). The pipe and sprite-holder caches are generation-stamped against worker-thread races.
- Smoke scenario `scripts/smoke/scenarios/sprite-reload.json` (High Contrast toggled twice) passed on 26.3 and 1.21.1: 37/37 steps, shots identical.
- Heat exchanger matches the other BC machines: shell in the shared casing ramp (plate #cbcbcb, frame #a7a7a7), 1px casing frame on the shell edges, interior in the recess greys #2b2b2b/#363636, sight-slot bottom row I_BK so it no longer sits luma-equal on the seal. Commits `232f75b90`, `ba01a90c3`, `f95287a41`; review_fast approved the final sha; user approved the in-game look on 26.3 (2026-11-02). Merged from `claude/heat-exchanger-uniformity-130a0d`.
- The 2026-10-23 release follow-ups this file used to track are finished. Release work stays user-gated (see memory `project_next_release_plan`).

- Volume-marker distance readout (wrench right-click a marker; branch `claude/marker-distance-expansion-119343`). Delegations: impl built it first try, all six nodes compiled; reviewer (Haiku) pass with nits; review_fast glm-flash approve, deepseek-flash (standing in for busy shop26) comment, both low. The low findings were fixed or narrowed in the docs. CME lead filed as #93. User approved it in-client on 26.3 (2026-11-02).
- Marker-area lift (#32) is Volume Wrench only now, main hand decides (`087770eb1`). Done by the orchestrator. Delegations: reviewer (Haiku) FAIL on an offhand bypass, which was fixed with a game-test assertion that failed first; review_fast round 1 (shop26, glm-flash) found docs only, which were fixed; final-sha review_fast approved (one low: an offhand-only lift can only be confirmed by an air click, pre-existing, left). User approved it in-client.

## Next
- #89: two unit tests were already red on 26.3: the GuideMeExportStalenessTest export is stale, and the zh_cn `generateQuarryPits` lang key is missing.
- The NeoForge version check shows 26.1.2 (-> 26.1.2.126) and 26.3 (-> 26.3.0.101) one build behind; use the `neoforge-bump` skill.

## Open questions / leads (unverified)
- Keep the heat exchanger's dark-green seal as its accent? Recommended yes (the 1.12.2 exchanger's colour); alternative is an #a7a7a7 bezel with green only on the slot walls.
- Debt: `scripts/generate-heat-exchanger-assets.py` rewrites every model JSON on each run (LF over the CRLF checkout), dirtying 9 files; make it skip unchanged content.
- `EnumColor.brushSprites` looks like dead code.
- The Guava `ModelCache` may have a similar reload window to the one #81 closed. Not checked.
- Stale worktrees under `.claude/worktrees/` (merged `heat-exchanger-redesign-cc2297`, `heat-exchanger-uniformity-130a0d`, three `agent-*`, sixteen `wf_*`) can be cleaned up.

## Uncommitted changes
- None.
