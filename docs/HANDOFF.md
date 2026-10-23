# Handoff — 2026-10-23 pre-2026.2.1 review follow-ups + visual checks

Goal (user, 2026-10-22): fix everything in todos.md's Outstanding section before releasing 2026.2.1. Session paused at the user's request (machine shutdown), mid visual checks.

## Done (on main, committed, not pushed)
- The 2026-10-22 review fan-out was adjudicated; all real findings were fixed with red-first tests: claim asks before Quarry frames and Mining Well/Pump tubes are placed; engine removal drops moved into the engine base (FE engine, MJ dynamo, Stirling); Stripes break-only refusal tally, safe minecart breaking, copy-on-write registry; Filler finishes beside fluid; snapshot rescan survives a bad file; guide Builder/Filler text. Commits `b8f651ec8..0409bc00e`.
- Two arena-timing flakes fixed (`d92820619`); 26.3 ×3 and 1.21.1 green after.
- Last full `fullTestSuite` (before the flake fix): five nodes green, 26.3 hit one of those flakes.
- Third-party reviews: Qwen 27B + GLM-flash on the follow-ups, post-merge Qwen3.8-Flash + GLM; all adjudicated. Delegation log updated for the adjudication.
- New smoke scenario `scripts/smoke/scenarios/release-2026-2-1.json` (generator `gen_release_2026_2_1.py`), dry run green on 26.3 (`build/smoke/20261023-153158`).

## Visual checks (user judges each item from screenshots)
- A robots gliding: PASSED on 26.3 (user watched live). Still owed: one of 1.21.10–26.2, and 1.21.1.
- B stripes retraction: FAILED (user confirmed): see todos.md and todo-details "Stripes sideways retraction breaks". Fix needed before release; write a game test for the L-shaped (perpendicular) retraction first.
- C guide links across a page break (`c1..c6`), D Programming Table (`d1, d2`; no board has >24 programs so no pager in normal play), E gate plugs / LEDs / Architect preview / painted fluid pipes (`e1..e6`): shots exist in the 26.3 dry-run folder, NOT yet shown to the user. Resume by showing item C.
- Possible bug from the dry run, not yet shown: the Architect Table's owner-ledger head is a solid magenta square when a player placed it.

## Next
1. Show items C, D, E from `build/smoke/20261023-153158/26.3/` one at a time.
2. Fix the stripes retraction bug (test first), re-run item B.
3. Run the scenario on 1.21.1 and one of 1.21.10–26.2 for robot gliding (user watches) and same-shot comparison.
4. Flake-fix review follow-ups (not started; an agent was stopped, its partial edit to `GameTestPollBudgetTester` reverted and saved at the session scratchpad as `pollbudget-partial.patch`): widen the poll-budget guard past robot tests (BlockWaterloggingTester's doc claims coverage it doesn't have); convert `assertWaterloggedBetweenSources` to the tickUntil+cleanup shape so a failing marker test doesn't leak its marker; name the stalled lava side on timeout; fix the "(about 90 ms)" and inconsistent straddle-rate comments.
5. `drops_stirling_hand` failed once (3 of 4 fragile_fluid_container) under a one-thread run; watch it.
6. Offer the 1.21.1 NeoForge bump 21.1.262 → 21.1.264 (neutral). Use the `neoforge-bump` skill.
7. Final `fullTestSuite`, then release work is user-gated.

## Uncommitted changes
- None.
