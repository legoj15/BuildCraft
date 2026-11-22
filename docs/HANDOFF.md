# Handoff — 2026-11-22 (MinecraftForge port merged, nothing released)

`main` now carries MinecraftForge as a third loader: `26.3-forge`, `26.2-forge`, `26.1.2-forge`, `1.21.11-forge` and `1.21.1-forge`, each with the whole mod, server and client. There is no Forge build for 1.21.10 and there will not be one. `mod_version` is still 2026.5.0. Nothing was pushed, tagged or published; the release tool holds Forge back (`heldBack` in the gitignored `release/config/release.json`) until the user lifts it.

The standing record is [forge-port.md](forge-port.md): toolchain, what each Forge line lacks and how it is served, every measured result. Issues #208, #209, #211, #212 and #213 close with the merge commit when it is pushed.

## What was verified

- `./gradlew fullTestSuite` on Server1: all 17 `nodeVerification` tasks green (at `d8400444c`; later commits touch only scripts and docs).
- Client smoke, full default batch on every Forge line: see "Client batches after the last line merged" in forge-port.md. Every scenario passed, five of them only on a rerun (bridge timeouts on a busy Server1).
- Networked multiplayer with the server on Server1 and two local clients: full cycles on 26.2, 26.1.2, 1.21.11 and 1.21.1 Forge (`scripts/smoke/multiplayer/mp.py`).
- `review_final` on the whole branch: one confirmed finding (the multiplayer rig's `stop`), fixed and re-reviewed.

## Decisions waiting for the user

- Whether and when Forge ships, and whether Forge shares NeoForge's update manifest.
- Server1's firewall has no rule for the Java 21 runtime, so the 1.21.x multiplayer servers are reached through an ssh tunnel (#230).
- `versions/1.21.1-forge/run` has no accepted EULA, so `:1.21.1-forge:runServer` was never run by hand (the game tests and the multiplayer rig cover the server).
- Two Forge defects worth reporting upstream are the user's to file: the 1.21.1 quick-play crash (#226) and the shaped-recipe network encoding on 26.3 (forge-port.md, Open).

## Open follow-ups

#215 to #231 hold everything found along the way. The ones that limit testing: #228 (Open Parties and Claims cannot boot on 26.2, 26.1.2 and 1.21.11 Forge, so the claims scenarios are skipped there), #225 (no config screen on Forge 1.21.1), #227 (Server1 cannot focus the 26.3 Forge window), #224 (three NeoForge 1.21.1 smoke failures that predate this work).

The gitignored release test matrix (`testing/config/runtimes.json`) now has all five Forge rows enabled with clients (edited on this machine after the merge; the release rig was not run with them). Server1 has no McDevBridge jar for `1.21.11-forge`; build it there before running that line's client batch remotely.

Nothing is in progress.
