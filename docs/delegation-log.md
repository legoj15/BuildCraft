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
