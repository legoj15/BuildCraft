# Scripted in-client smoke runs

`Invoke-InClientSmoke.ps1` boots each Stonecutter node's dev client, drives a scenario through the
[McDevBridge](../../../McDevBridge) HTTP bridge, and writes screenshots plus a pass/fail report. It exists so a round
of visual changes can be checked on all six nodes with one command and a screenshot review, instead of a manual
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
   off. The GUI scale is re-applied every run: the scenario's `guiScale`, else 2 (so screenshots line up across runs),
   and so is `particles:0` (All), so a scenario that lowers particles in Video Settings cannot thin the next run.
2. **World.** The first run on a node creates one through the menus (Singleplayer → Create New World) and renames the
   save to `bc-smoke` after the client closes; every later run quick-plays it (`-PbcQuickPlay=bc-smoke`). The rig is
   rebuilt by the scenario each run, so the save's state never matters. Delete `run-smoke/saves/` to start over. (A lone save of any name is adopted as `bc-smoke`, so an interrupted first run heals itself.)
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
| `cmd` | `run`: string or list; `allowFail`; `echo` | server commands (`/command`, OWNER, no entity — target `@p`). `echo: true` copies the last command's reply into the report (state dumps). A leading `?` makes one command optional; "already so" replies (`did not change`, `could not set the block`, `no blocks were filled`, `is already set to`) count as success, since the world persists between runs |
| `tp` | `at`, `yaw`, `pitch` | move the player to the block's centre (run as the player; a bare server `tp` doesn't move it) and set the view |
| `aim` | `at`; `exact` | look at a block's centre from the current eye position (standing eye height); the report records what the crosshair hit. `exact: true` aims at the point `at` itself (a box corner, an addon slot) |
| `look` | `yaw`, `pitch` | absolute view (yaw 0 = south/+Z, 90 = west; pitch + is down) |
| `hold` | `item` | `item replace entity @p weapon.mainhand with <item>` (the selected slot, whatever it is) |
| `use` / `attack` | — | one right / left click at the crosshair |
| `click` | `widgetText` \| `slotIndex` \| `x`,`y` (GUI units) \| `fx`,`fy` (screen fractions); `button` 0/1/2; `modifiers`; `allowMiss` | `/screenclick`; fails when nothing was under the cursor |
| `hover` | same targets | `/screenhover`, then waits 600 ms so the tooltip draws — follow with `shot` |
| `hoverEach` | `name`, `max` (default 12) | hover every visible, active widget on the open screen and screenshot each tooltip (`NN-<name>-hoverKK.png`) |
| `scroll` | same targets as `hover`; `scrollY` (default 1, negative = wheel down / zoom out), `notches` (1..200), `scrollX` | `/screenscroll` (McDevBridge `/screenscroll`, 2026-10-18 or later); fails when the screen handled none of the notches |
| `key` | `key` | a named key into the open screen (`escape`, `tab`, `enter`, …) |
| `keybind` | `key` (a keybind's translation key, e.g. `key.sneak`), `action` (`press`/`release`/`tap`) | McDevBridge `/keybind`: `press` holds the binding until `release` (a sneak-click is press, `use`, release); `tap` clicks it once |
| `text` | `text` | type a string into the open screen's focused widget (click the search box first) |
| `close` | — | close the open screen (releases container menus server-side) |
| `wait` | `ms` | sleep |
| `window` | `width`, `height` | resize the game window so its client area (the framebuffer) is exactly that size — options.txt `overrideWidth` is ignored. Pair with a top-level `"guiScale"` (written to options.txt each run, default 2; the game clamps it to what the window fits, so a big scale only shows after the resize) |
| `waitCmd` | `run` (one command), `match` (regex on its reply), `timeoutSec` (default 60), `pollMs` (default 1000) | re-runs the command until its reply matches: gates on observed state (a machine finishing) instead of a fixed wait; the last reply goes in the report |
| `shot` | `name`, `look` | screenshot; `look` is what the reviewer should check in it |
| `widgets` | `name` | dump the open screen's widget list (text, GUI rect, hovered) — use it to author `click`/`hover` targets |
| `expectScreen` | `contains` (regex on the screen class; `""` = no screen), `timeoutSec` (default 5) | polls for the screen |
| `pause` | — | opens the pause menu from the world. The bridge cannot press a key with no screen open, so this posts a real Esc key message to the game window (no focus needed on GLFW nodes). 26.3 runs SDL3, which drops key messages for an unfocused window: when the post does not open the menu within 4 s, the runner brings the game window to the front (an Alt tap lifts Windows' foreground lock) and presses a real Esc, and the step's Detail says so. Follow with `click` "Save and Quit to Title" to leave the world |
| `inWorld` | `timeoutSec` (default 300) | waits until the player is in a world with no screen open: after `click` "Play Selected World" |

A scenario can play a world of its own: top-level `"save": "<folder>"` quick-plays that save instead of `bc-smoke`, and `"saveSeed": "bc-smoke"` copies the seed to it when it is missing. Without a seed the save must already be there (a world carried over from another node); the runner refuses to boot otherwise.

A scenario that visits a second world names it in a top-level `"extraSaves": ["bc-smoke-b"]`: before boot the runner copies the `bc-smoke` save to each missing name (so the first run on a node, which only creates `bc-smoke`, needs a second run). The world list is sorted by last played, so the world just left is entry one and `click` `x:213, y:100` picks entry two (see `scenarios/marker-relog.json`, which leaves a world, joins the other and comes back).

A failing step records its error, takes a `NN-FAILED.png`, closes any open screen and the run continues.

## Robot scenarios and their generators

`robots-live`, `robots-boards` and `robots-logistics` build working robot rigs (stations, gates with Work-in-Area
zones, tools in chests) from commands and read the outcome back with `echo` steps. The last two are generated:
edit `generators/gen_boards.py` / `generators/gen_logistics.py` (shared helpers in `generators/robotrig.py`) and
re-run them with `python`, never hand-edit their JSON. Each echo step's label says the expected reply (`expect pass` /
`expect fail` / a count); the report's Detail column carries the actual reply.

## 26.3 port verification

`port-26-3` (generated by `generators/gen_port_26_3.py`) is the 26.3 port's in-client check: translucency with and
without order-independent transparency, model shading, pipe and plug items, Stirling fuel and JEI, mouse buttons,
guide link and tag recipes, fluid shards, the oil feature, the fluid-tag and heightmap echoes. Run it on 26.3 and
26.2 and compare same-named shots; the generator's docstring says how to run the OIT pass.

`upgrade-26-3-author` and `upgrade-26-3-verify` (generated by `generators/gen_upgrade_26_3.py`) check the 26.2 to 26.3 world upgrade: blueprints, the library list and facades saved on 26.2 must read back on 26.3, and 26.3 must read the carried-over `-common.toml`. The generator's docstring has the order: author on 26.2, carry the save, the snapshot folders and the config over with `Copy-UpgradeWorld.ps1`, verify on 26.3 and then on 26.2.

## Release 2026.2.1

`release-2026-2-1` (generated by `generators/gen_release_2026_2_1.py`) carries the release's visual checks, shot names prefixed by item: `a` robot gliding (a 20 s WATCH LIVE wait, docked shot, a five-shot burst mid-flight), `b` stripes retraction facing and an extension sanity shot, `c` a guide link split over a page break clicked on both pages, `d` the Programming Table option grid, `e` the cheap leftovers from the 2026-09-26 smoke. The docstring records what could not be driven.

The same generator writes `release-2026-2-1-f`, the rest of the 2026-09-26 list: `f1` the guide's side chapter tabs on a
window wide enough for them, `f2` a Filler Planner on a volume box (its icon, the Fill preview, the invert button and the
inverted preview), `f3` a digging Shovelman's exhaust on All and then on Minimal particles (switched in Video Settings).
Volume boxes live in saved data no command reaches, so `f2` sneak-clicks away the box an earlier run left and drags a
new one to 3x3x3 with the Marker Connector; the generator solves that drag (`solve_drag`) and fails if a layout change
leaves no solution. Patterns are dragged from the planner's list, which the bridge cannot do, so `f2` takes the Fill that
an in-place click on the pattern slot picks.

## Real-mouse checks: `zone-planner-drag`, `library-scrollbar-drag`

The bridge has no press-drag-release, and the Zone Planner also asks the window system whether a pan's or stroke's
button is still held, which no synthetic event can fake. These two scenarios set the screen up and wait for a REAL
drag: a person, or an agent with desktop control (computer use) driving the game window. `zone-planner-drag` gives 45 s
for an empty-hand pan and then 90 s for a brush stroke, echoing the red layer before and after;
`library-scrollbar-drag` gives 45 s to drag the Electronic Library's scrollbar and needs 14 or more blueprints in the
node's `run-smoke/snapshots-client` first (copy some from its `run-gameTestServer/snapshots-server`).

An agent driving them: a warm client reaches the first wait about 30 s after launch, so block on the shot that opens
each window (`10-zp-start.png` for the pan, `17-zp-before.png` for the stroke, `lib-before` for the library) in the
run's folder rather than starting late. The game window may come up behind others: grant computer use the node's own
`java.exe` path (the 1.21.x nodes run a different JDK from the 26.x ones) and bring it to the front before the first
press. Hold left with the agent's own button-down and moves, but send right through `Send-MouseButton.ps1` (a tap
mid-stroke, or the press and release of a right-drag erase): the computer-use right click lets go of a held left
button, which ends the stroke where right was tapped, the very symptom the scenario hunts.

## Known harness gaps

- A `use` right after a `look` or `aim` can act on the old target: put a short `wait` between them (a forced re-pick in
  the bridge's `/look` would be cleaner).
- After a click that switches screens, the hovered slot and its tooltip can be stale until the cursor is sent again:
  `hover` once more before a tooltip `shot`.
- On 26.3 the `pause` step's real-Esc fallback, and any other step that brings the window to the front, can turn the
  camera (the focus grab recentres the mouse): `look` again before a shot that depends on the view.

## Two players: `multiplayer/`

What another player sees (arm swings, particles, entity sync that goes server to other clients) needs a dedicated
server and two clients, which the runner above cannot do. `multiplayer/mp.py` boots `:<node>:runServer` and two
`runClient`s (Watcher and Worker) that quick-play into it, all under `versions/<node>/run-smoke/mp-*`, drives world
commands over RCON and each client over its own bridge (it records each client's url and token as it starts), and
stops only what it started. `multiplayer/scene_swing_particles.py` is the 26.3 port's B6/B7 check built on it; its
docstring gives the phase order and the expected result, and `multiplayer/grids.py` tiles the shots per node for a
26.3 vs 26.2 comparison. Run it from Git Bash with `MSYS_NO_PATHCONV=1`. The server log's `No key layers` ERROR is
vanilla complaining about the flat world's empty generator settings; the default flat world is used.
