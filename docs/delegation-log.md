# Delegation log

One line per delegation: date, agent, task, outcome, escalations. Used to tune the model ladder (see the user's CLAUDE.md).

## 2026-10-17 — todos.md Outstanding sweep (workflows wf_ccd5b302-521, wf_07315122-ebe)

Round 1: per packet a `scout` (Haiku) brief, an `impl` (Sonnet xhigh) implementation in its own worktree, a `reviewer` (Haiku) gate, and `impl-opus` fix rounds on blocking/major findings (max 2).

- translations-a — scout ok; impl done; reviewer found 12 zh_cn keys missed by a name-only match → impl-opus fix added them plus a source-scanning coverage test; reviewer approve.
- translations-b — scout ok; impl done; reviewer approve first pass.
- snapshots — scout ok; impl done; reviewer approve first pass.
- pipe-filters — scout ok; impl done (bug confirmed masked by per-tick blockEntityChanged; pinned with tests); reviewer approve.
- builder-reflood — scout ok; impl done; reviewer blocking ×2 rounds → impl-opus ×2; final Haiku re-review still 2 majors → round 2 (impl-opus).
- miners — scout ok; impl done; reviewer approve first pass.
- pumps — scout ok; impl done; reviewer major ×2 rounds → impl-opus ×2; final re-review 1 major → round 2 (impl-opus).
- robot-protection — scout ok; impl done; reviewer approve first pass.
- jei-tables — scout ok; impl done; reviewer approve first pass.
- flaky-markers — scout ok; impl done; reviewer approve first pass.
- robot-reservations — scout ok; impl done; reviewer major ×2 → impl-opus ×2; final re-review 1 major (Delivery recharge double-claim) → round 2 (impl-opus).
- small-cleanups — scout ok; impl done; reviewer approve first pass.
- Sonnet `impl` first-pass approval rate: 8/12; the 4 that needed Opus were the three world-simulation-heavy items (builder fluids, pump claims, robot reservations) plus a translation coverage miss.
- Third-party: opencode triad (shop26 Qwen3.8-27B + glm-flash) on the nine merged packets.
- Re-review (`reviewer` Haiku, ×3) of the three packets whose last Opus fix was unreviewed: all three found a further major (builder lava/fragile, pump permit not re-probed at drain, Delivery recharge double-claim).

Round 2 (wf_07315122-ebe): `impl-opus` fix per packet, then `reviewer`.
- builder-reflood, pumps, robot-reservations — impl-opus fix done; reviewer approve first pass (minors only).
- Third-party round 1 (shop26 + glm-flash triad on the 9 merged packets): 8 findings; 1 real high (zh_cn key missing across two merges — fixed by the orchestrator), 2 cheap lows fed into round 3, rest not-a-bug or unverifiable-without-jars.

Round 3 (wf_34f60214-e96): `impl` per packet, `reviewer`, `impl-opus` on blocking/major.
- r3-persistence — impl done; reviewer approve first pass.
- r3-translations — impl done; reviewer major (docs said COMMON unit settings were CLIENT) → impl-opus fix; approve.
- r3-jei-components — impl done; reviewer major (Builder place-task saves still dropped components) → impl-opus fix; approve.
- r3-robot-ai — impl done; reviewer approve first pass.
- Merge conflicts (ProtectionMod move; search AI owner view + approval filter) resolved by the orchestrator.
- Third-party round 2: shop26 + glm-flash on rounds 2–3; post-merge dgx (Flash-Next) + glm-5.3 cross-packet review of the whole session diff.
- Pump-own-tube fix (GLM-5.3 cross-packet finding): `impl` (Sonnet) done first pass, both tests red→green; `reviewer` approve.
- Third-party round 2 (shop26 + glm-flash on rounds 2–3): 4 lows, all real → `impl` (Sonnet) fixed first pass (6d15f7344), `reviewer` approve and traced one more (abandoned-bucket charge) → `impl` fixed (c2a255503). dgx Flash-Next post-merge review deferred past the session's waiter (results arrive via the notify hook).

## 2026-10-17 (afternoon) — robots release (workflows wf_9bdb2b6c-52d, wf_1c57ff33-9c3, wf_1d4b04bc-6db, wf_4ce2ca57-072)

- NeoForge bump (four nodes): orchestrator; classified neutral from the changelogs plus a read of the new network limits; full suite green.
- Round 1 (9 packets + hunt): `impl` ×7, `impl-opus` ×2 (robot-claims, refusal-identity). First-pass approval 7/9; builder-scan-perf needed 1 Opus fix, snapshot-hardening 2 Opus fixes and still ended with 1 blocking + 1 major (Template bound, cap size) → carried to round 2. Hunt: 9 `reviewer` (Haiku) finders, 40 unique findings, 2 Haiku refuters each → 34 confirmed, 3 contested, 3 dropped.
- Round 2 (9 packets from the hunt): `impl` ×6, `impl-opus` ×3. 6/9 approved; forbid-robot's Opus fixer added an unrequested 7.1.x divergence (Forbid Robot on the docked station) that the reviewer flagged as a cargo-loss path — orchestrator merged only the test-side commit. tables and registry-upgrade surfaced pre-existing bugs → round 3.
- Third-party: shop26 + glm-flash on round 1 (5 lows, 1 save-compat regression both families agreed on: tag-less DockingStation loads taken) and round 2 (2 GLM lows) → all queued into rounds 3–4.
- Round 3 (4 packets + hunt 2): `impl` ×4, 1 Opus fix (saved-data-recovery); all approved. Hunt 2: 7 finders, 26 unique → 21 confirmed (several regressions from rounds 1–2: Builder board reach, 1.21.1 fluid load mirror) → round 4.
- Merge conflicts (board claim latch vs refusal memory; stripes give-up vs JobClaim; flight reach vs loaded-only access) resolved by the orchestrator, keeping both sides.
- Round 4 (5 packets + hunt 3): `impl` ×4, `impl-opus` ×1 (search-flight); 4/5 first-pass approved, builder-board-misc needed 2 Opus fixes. Hunt 3: 5 regression-focused finders, 8 unique → 7 confirmed (mostly minor; one medium: Goto Station re-homes before the flight succeeds).
- Round 5 (3 packets + hunt 4): `impl-opus` ×2 (goto promotion, test flakes), `impl` ×1; all approved (flakes needed 1 Opus fix). Hunt 4: 4 finders, 12 unique → 8 confirmed, 7 of them low Zone Planner render details; one medium regression (round-4 culling dropped lowland at the map edges). Hunts converged 40 → 26 → 8 → 12 raw (34 → 21 → 7 → 8 confirmed); no fifth hunt.
- Round 6 (3 packets): `impl` ×3, all approved first pass. Round 7 (2 packets): `impl` ×2; zone-final needed 1 Opus fix (pan must follow mouse moves — vanilla clears its active button on any release).
- Third-party per round: shop26 + glm-flash on rounds 3–6 (shop26 timed out twice on 3–7k-line diffs with no findings in its partials; glm-flash found 1–2 lows each, all folded in). Whole-release post-merge: glm-5.3 (1 "high" REFUTED — it misdated the 1.21.10 horse package; 1 low FE-commit leftover; design debt) and dgx Flash-Next (2 mediums: side bytes indexed blind in StackRequest/DockingStation; 1 low half-loaded AI on load failure) → round 8.
- Orchestrator decisions taken without asking (all recorded in docs or the changelog): RF stations only charge with MJ→RF autoconversion (config gate); Butcher spares tamed/owned/name-tagged animals; Goto Station promotes home only on docking; armour floor not cap; unreachable-block memory 5 min; Forbid-on-docked left as 7.1.x and recorded as a decision for the user.
- Round 8 (2 packets, post-merge review fixes): `impl` ×2; load-robustness approved first pass, caches-energy needed 2 Opus fixes (FE carry design, macOS Ctrl+left). Final full suite green on all five nodes (723 game tests; 722 on 1.21.1).

## 2026-10-18 — robots in-game test sweep (unattended)

- Baseline: fullTestSuite green, then a 3-round game-test soak on all five nodes (15/15 green, no flakes).
- `scout` (rig facts for command-built robot rigs): accurate; its gate NBT and `currentDir` guesses held in-game.
- `impl` (three known small defects from todo-details: corrupt side bytes, robot-id counter, Delivery reservation): done first pass; triad review (shop26 + dgx + glm-flash) found only lows → second `impl` round fixed them (dropped the dead reservation field, made `rotateLeft` real, `@Nullable`, tests).
- `impl-opus` (idle robots in the smoke rig): found a real bug (a station orphaned by `/fill` read its removed pipe forever); review round (shop26 + glm-flash) found a HIGH regression in its fix (robot AI froze while its home chunk was unloaded) plus ghost stations and chunk loads → same agent fixed; round-2 review found unloaded stations picked as destinations → same agent again.
- `impl-opus` (Planter "planting once"): no bug, 7.1.x pace; the rig's count was wrong. Added a regression game test.
- `impl-opus` (Pump never unloading): no bug, home station outside its work zone (7.1.x); added an end-to-end pump game test and a guide sentence; design question recorded in todos.
- `impl` (robots dragged by falling water): real bug (7.1.x robots ignored water), fixed first pass; a GLM bubble-column follow-up was disproved by the same agent from the sources (noPhysics) and pinned with a guard test.
- The station-fix agent (`impl-opus`) ran five more review rounds (shop26 / glm-flash / glm-5.3 each round): unloaded stations as destinations, Goto Station to unloaded targets, pipe neighbour/capability lookups loading chunks, Delivery cargo dropped for an unloaded requester, destroyed/forbidding requesters during the wait. One GLM "high" (pipe connections dropped at chunk load-in) was raised twice and disproved from vanilla sources (block-entity ticking needs radius-1 neighbours FULL). Findings shrank each round; the last were lows.
- Post-merge whole-day review: glm-5.3 (the repeat "high", refuted; one medium, fixed); dgx Flash-Next deferred past the session's cache window (delivered later by the notify hook).
- Final: fullTestSuite green on all five nodes (739 game tests; 738 on 1.21.1), release gate PASS, 2-round soak clean; three robot smoke scenarios clean on all five nodes.
- `impl-opus` (fresh, after the station agent's worktree was cleaned up): Flash-Next's two mediums (plugless pipe adopted as a ghost station; low robot with an unloaded home drained to shutdown → zero-cost AIRobotWaitForHome) plus test/comment lows; final review: Qwen none, GLM two comment lows (fixed by the orchestrator).
- `scout` (rig facts for the remaining boards) found the Requester never saved its slots; `impl` fixed it first pass (7.1.x saved them — a port regression); review approve (one pre-existing low → todo-details).
- Smoke: `robots-misc` (fluid carrier, bomber, stripes, delivery + requester, laser tables) clean on all five nodes.
- 2026-10-18 home-station unload (user decisions: unload at home outside the zone = allow; low robot with unloaded home and no charger = keep holding): orchestrator implemented. Qwen 27B review approved (one low: wiring untested → pump game test's zone now excludes home). `reviewer` (Haiku) found the real gap: an unreachable home would hide a reachable in-zone station → unload searches try every station; also a test-placement hole and "always" overclaims; fixed. No escalations.
- 2026-10-20 guide text over page number: orchestrator fixed (per-segment guaranteeSpace in GuidePart/GuideInlineLinkText) + smoke runner guiScale/window step; Qwen 27B review approve — took the window-move, size-assert and README nits, skipped the GuideChapterWithin overshoot (headings get 36 px reserved; trailing-advance correction still fires), filed the pre-existing link-click quirks as a todo. No escalations.
