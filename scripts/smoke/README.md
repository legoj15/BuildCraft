# Scripted in-client smoke runs

`Invoke-InClientSmoke.ps1` boots each Stonecutter node's dev client, drives a scenario through the
[McDevBridge](../../../McDevBridge) HTTP bridge, and writes screenshots plus a pass/fail report. It exists so a round
of visual changes can be checked on all five nodes with one command and a screenshot review, instead of a manual
session per node.

```powershell
pwsh scripts/smoke/Invoke-InClientSmoke.ps1                          # all nodes, scenarios/line8.json
pwsh scripts/smoke/Invoke-InClientSmoke.ps1 -Nodes 26.2,1.21.1       # a subset
pwsh scripts/smoke/Invoke-InClientSmoke.ps1 -Scenario scripts/smoke/scenarios/<other>.json
```

Output: `build/smoke/<timestamp>/summary.md` (one table per node, screenshot links) and `<node>/report.json`,
`<node>/NN-<name>.png`, `<node>/NN-<name>.widgets.json`. Exit code 1 when any step failed, any node hit a fatal
error, or BuildCraft logged an ERROR.

## What it does per node

1. **Own game folder.** `versions/<node>/run-smoke/` via `-PbcRunDir=run-smoke` (gitignored). The hand-used `run/`
   with its extra mods and saves is never read or written. The node's newest McDevBridge jar is copied into
   `run-smoke/mods` (searched in McDevBridge's `versions/<node>/build/libs` and its worktrees; `-BuildBridge` builds
   one if none exists). `options.txt` is written once: no pause on focus loss, no accessibility onboarding, sound
   off, GUI scale 2 (so screenshots line up across runs).
2. **World.** The first run on a node creates one through the menus (Singleplayer → Create New World) and renames the
   save to `bc-smoke` after the client closes; every later run quick-plays it (`-PbcQuickPlay=bc-smoke`). The rig is
   rebuilt by the scenario each run, so the save's state never matters. Delete `run-smoke/saves/bc-smoke` to start over.
3. **Scenario**, then every ERROR log line mentioning BuildCraft since the world loaded.
4. `save-all`, close the client (and its Gradle wrapper), next node.

**Keep the physical mouse off the game window** while it runs — a real mouse move overwrites the bridge's synthetic
cursor. One node at a time: McDevBridge's `~/.mcdevbridge/bridge.json` only points at the newest client.

## Scenario format

```json
{ "name": "line8", "base": [0, 150, 0], "steps": [ { "do": "...", ... } ] }
```

Coordinates in commands use `{X}`, `{Y+2}`, `{Z-1}` (the `base` plus an offset); `at` arrays are offsets from `base`.
Every step may carry `"label"` (report text) and `"nodes": ["26.2", ...]` (run only there).

| `do` | Fields | Effect |
|---|---|---|
| `cmd` | `run`: string or list; `allowFail` | server commands (`/command`, OWNER, no entity — target `@p`) |
| `tp` | `at`, `yaw`, `pitch` | move the player to the block's centre (run as the player; a bare server `tp` doesn't move it) and set the view |
| `aim` | `at` | look at a block's centre from the current eye position; the report records what the crosshair hit |
| `look` | `yaw`, `pitch` | absolute view (yaw 0 = south/+Z, 90 = west; pitch + is down) |
| `hold` | `item` | `item replace entity @p weapon.mainhand with <item>` (the selected slot, whatever it is) |
| `use` / `attack` | — | one right / left click at the crosshair |
| `click` | `widgetText` \| `slotIndex` \| `x`,`y` (GUI units) \| `fx`,`fy` (screen fractions); `button` 0/1/2; `modifiers`; `allowMiss` | `/screenclick`; fails when nothing was under the cursor |
| `hover` | same targets | `/screenhover`, then waits 600 ms so the tooltip draws — follow with `shot` |
| `key` | `key` | a named key into the open screen (`escape`, `tab`, `enter`, …) |
| `close` | — | close the open screen (releases container menus server-side) |
| `wait` | `ms` | sleep |
| `shot` | `name`, `look` | screenshot; `look` is what the reviewer should check in it |
| `widgets` | `name` | dump the open screen's widget list (text, GUI rect, hovered) — use it to author `click`/`hover` targets |
| `expectScreen` | `contains` (regex on the screen class; `""` = no screen) | polls up to 5 s |

A failing step records its error, takes a `NN-FAILED.png`, closes any open screen and the run continues.
