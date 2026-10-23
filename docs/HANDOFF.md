# Handoff — 2026-10-23 pre-2026.2.1 review follow-ups + visual checks

Goal (user, 2026-10-22): fix everything in todos.md's Outstanding section before releasing 2026.2.1. Visual checks finished 2026-10-23.

## Done (on main, committed, not pushed)
- The 2026-10-22 review fan-out was adjudicated; all real findings were fixed with red-first tests: claim asks before Quarry frames and Mining Well/Pump tubes are placed; engine removal drops moved into the engine base (FE engine, MJ dynamo, Stirling); Stripes break-only refusal tally, safe minecart breaking, copy-on-write registry; Filler finishes beside fluid; snapshot rescan survives a bad file; guide Builder/Filler text. Commits `b8f651ec8..0409bc00e`.
- Two arena-timing flakes fixed (`d92820619`); 26.3 ×3 and 1.21.1 green after.
- Last full `fullTestSuite` (before the flake fix): five nodes green, 26.3 hit one of those flakes.
- Third-party reviews: Qwen 27B + GLM-flash on the follow-ups, post-merge Qwen3.8-Flash + GLM; all adjudicated. Delegation log updated for the adjudication.
- New smoke scenario `scripts/smoke/scenarios/release-2026-2-1.json` (generator `gen_release_2026_2_1.py`), dry run green on 26.3 (`build/smoke/20261023-153158`).

## Visual checks: all passed (user-judged 2026-10-23)
A robots gliding (26.3, 1.21.1, 26.2), B stripes retraction (after the client fix `997eb737c`), C guide links across a page break, D Programming Table, E gate plugs / LEDs / Architect preview / painted fluid pipes. The magenta owner head is the dev "Dev" user, not a bug.

## Next
1. Flake-fix review follow-ups (not started; partial edit saved at the session scratchpad as `pollbudget-partial.patch`): widen the poll-budget guard past robot tests (BlockWaterloggingTester's doc claims coverage it doesn't have); convert `assertWaterloggedBetweenSources` to the tickUntil+cleanup shape; name the stalled lava side on timeout; fix the "(about 90 ms)" and inconsistent straddle-rate comments.
2. New flakes: `robot_name_kept_through_wrench_pickup` scans in the same tick as the drop (gate on entity ticking); `drops_stirling_hand` failed once (3 of 4 fragile_fluid_container) under a one-thread run.
3. Final `fullTestSuite`, then release work is user-gated.

## Uncommitted changes
- None.
