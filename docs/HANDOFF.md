# Handoff — 2026-10-22 pre-2026.2.1 todos sweep

Goal (user, 2026-10-22): before releasing 2026.2.1, re-investigate and fix everything in todos.md's Outstanding section.

## Done (on main, committed, not pushed)
- Re-investigated every Outstanding item. Nearly all were still open; only a few sub-points were already done or obsolete.
- 13 fix packets merged (`a24f428b8..e28dacdfd`): Builder fluid stalls; land-claim asks for Builder and Filler; 1.21.1 command-replace wipe; Distiller slot drops; guide links across page breaks; Stripes aim plus restored minecart-breaking and pipe extension (user decision); robot client interpolation on every node; robots design-debt refactor; Electronic Library re-key on load (user decision); Allowlist/Blocklist wording (user decision); Programming Table paging; JEI transfer skeleton; miner/pump/quarry fixes; Zone Planner and snapshot-store perf; game-test scheduling guard and flake fixes; shard tint fix.
- Two more flakes fixed (`e28dacdfd`): delivery give-up and butcher tests now wait for the arena cell to entity-tick.
- `./gradlew fullTestSuite`: green on all six nodes (second run, 2026-10-22 20:56).
- changelog.md, todos.md, docs/todo-details.md and docs/delegation-log.md updated.

## In progress
- Third-party review fan-out `todos-2026-2-1-20261022-205101-ad8d`: 8 angles; stripes and builder on shop26, the rest on glm-flash. It is reviewing `a739c4c02..HEAD`. Collect it with `opencode_wait_group`, verify each finding, fix the real ones as follow-up commits, then run the post-merge pass (Qwen3.8-Flash + GLM-5.3).
- No packet's final commit had a passing Haiku review: the loop ended on a fix. The fan-out above is the first review of those final commits.

## Next
1. Adjudicate the fan-out findings (above).
2. In-client smoke for the owed visual checks (todo-details "In-client smoke"), now including robot gliding (26.3, a 1.21.10–26.2 node, 1.21.1, docking), stripes sideways retraction, wrapped guide-link clicks, and Programming Table paging. Use the `in-client-smoke` skill.
3. Offer the 1.21.1 NeoForge bump 21.1.262 → 21.1.264 (neutral: attachment initial-sync fix, enchanted loot events). Use the `neoforge-bump` skill.
4. Release work is user-gated (docs/port-26.3.md V3).

## Open questions
- None blocking. Small follow-ups are in todo-details "Small cleanups" (stacks above 99 in pipes etc., Replace mode with flowing fluid, `setPlaceBlock` ordering, the pump loading unloaded chunks — a gameplay decision — and the `power_display_survives_client_resync` flake).
- My merge resolution in `GlobalSavedDataSnapshots`: blueprint keys heal only on directory scans, not lookups. A healed file can briefly appear twice in the list until the next rescan. The snapshots-library reviewer is checking this.

## Uncommitted changes
- None.
