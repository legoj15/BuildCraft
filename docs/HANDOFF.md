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
1. Release work is user-gated (version bump, changelog consolidation, publish). Nothing is pushed.

## Done since the visual checks
- 1.21.1 NeoForge 21.1.262 -> 21.1.264 (`112fed2ec`).
- Flake follow-ups (`127c81975`, `aeea0107e`, `5c2d593b4`): the poll-budget guard covers every game test; every drop test uses `DropArenaUtil` (waits for the cell to tick and the drops to settle, counts only inside its own cell); click-break pipe tests sit mid-cell.

## Uncommitted changes
- None.
