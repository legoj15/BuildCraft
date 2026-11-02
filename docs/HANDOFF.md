# Handoff — 2026-11-01 (issue #81 session)

## Done
- #81 (plug/pipe baker caches reset before the atlas swap; dead PlugGateBaker cache) fixed in `0a654d481`, merged as `5c98b16ae`. It is on `origin/main` and GitHub closed #81.
- Every model-derived sprite cache now resets on `ModelEvent.BakingCompleted` (after the atlas upload). The pipe and sprite-holder caches are generation-stamped against worker-thread races.
- Smoke scenario `scripts/smoke/scenarios/sprite-reload.json` (High Contrast toggled twice) passed on 26.3 and 1.21.1: 37/37 steps, shots identical.
- The 2026-10-23 release follow-ups this file used to track are finished. Release work stays user-gated (see memory `project_next_release_plan`).

## In progress
- Nothing. The issue-81 worktree has been removed.

## Next
- #89: two unit tests were already red on 26.3 before this session: the GuideMeExportStalenessTest export is stale, and the zh_cn `generateQuarryPits` lang key is missing.
- The NeoForge version check shows 26.1.2 (-> 26.1.2.126) and 26.3 (-> 26.3.0.101) one build behind; use the `neoforge-bump` skill.

## Open questions / leads (unverified)
- `EnumColor.brushSprites` looks like dead code.
- The Guava `ModelCache` may have a similar reload window to the one #81 closed. This was not checked.

## Uncommitted changes
- None.
