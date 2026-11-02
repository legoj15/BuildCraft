# Handoff - 2026-11-01 heat exchanger uniformity (worktree branch claude/heat-exchanger-uniformity-130a0d)

Goal (user, 2026-11-01): the redesigned Heat Exchanger stood out next to the other BC machines, mainly its iron-block white. Make it match.

## Done (committed on the worktree branch; not merged to main, not pushed)
- `232f75b90` shell palette moved from vanilla iron_block's ramp (#b1..#f2) to the ramp every BC casing shares (measured across filler, quarry, builder, architect, pump, library, chute, requester, replacer, zone planner): plate #cbcbcb, mottles #cdcdcd/#c6c6c6, #bdbdbd, frame #a7a7a7. Steel S_HI/S_MD snapped to #d9d9d9/#a7a7a7. Changelog wording ("iron" dropped).
- `ba01a90c3` frame + recess variant, approved by the user from a preview: rows 2 and 13 of shell_side/shell_top are the 1px #a7a7a7 casing frame (no more highlight/shadow ramp); interior I_BK/I_DK lifted from near-black to #2b2b2b/#363636 (the quarry/builder display grey). G_HI/G_LT removed.
- review_fast (shop26 + glm-flash) on `232f75b90..ba01a90c3`: committed PNGs pixel-identical to the generator; both reviewers raise the same single finding (Next 1).

## Next
1. Fix the shared review finding: in `tex_shell_side`, row 9 (I_DK #363636, luma ~54) sits directly on the seal row 10 (#24431b, luma ~53-57), so the bottom edge of every front/back sight slot is a hue-only boundary. Paint row 9 with I_BK (#2b2b2b), as `tex_shell_top` already does; do not darken I_DK itself (it is also the flange, cover and nozzle-bore recess). Regenerate with `python scripts/generate-heat-exchanger-assets.py`, then `git checkout --` the blockstate and the 9 model JSONs (the generator writes LF over the CRLF checkout: line-ending-only diffs). Re-run review_fast on the fix sha.
2. In-game look by the user (`./gradlew :26.3:runClient` from this worktree, or the `heat-exchanger-look` smoke scenario). Nothing was checked in a client this session.
3. Merge the branch into main locally on the user's go-ahead (expect a changelog.md conflict).

## Open questions for the user
- Keep the dark-green seal as the exchanger's accent? Recommended yes (it is the 1.12.2 exchanger's colour, like the Filler's hazard checker); the BC-native alternative is an #a7a7a7 bezel around the window with green only on the slot walls.
- Debt: the generator rewrites every JSON on each run (LF vs the CRLF checkout), dirtying 9 files. Make it skip files whose content is unchanged?
- NeoForge: the SessionStart check reported 26.1.2 (26.1.2.125 -> .126) and 26.3 (26.3.0.100 -> .101) one build behind; not assessed.

## Uncommitted changes
None.

---

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
- 2026-10-25 NeoForge bump, all neutral per changelogs: 1.21.1 21.1.265, 26.1.2 26.1.2.124, 26.3 26.3.0.99. Unit + game tests green on those three nodes.
- Flake follow-ups (`127c81975`, `aeea0107e`, `5c2d593b4`): the poll-budget guard covers every game test; every drop test uses `DropArenaUtil` (waits for the cell to tick and the drops to settle, counts only inside its own cell); click-break pipe tests sit mid-cell.

## Uncommitted changes
- None.
