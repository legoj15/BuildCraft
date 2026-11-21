# Scripted in-client smoke runs

`Invoke-InClientSmoke.ps1` boots each Stonecutter node's dev client, drives a scenario through the
[McDevBridge](../../../McDevBridge) HTTP bridge, and writes screenshots plus a pass/fail report. It exists so a round
of visual changes can be checked on every node with one command and a screenshot review, instead of a manual
session per node.

```powershell
pwsh scripts/smoke/Invoke-InClientSmoke.ps1                          # all twelve nodes, scenarios/line8.json
pwsh scripts/smoke/Invoke-InClientSmoke.ps1 -Loader fabric           # the six Fabric nodes (or -Loader neoforge)
pwsh scripts/smoke/Invoke-InClientSmoke.ps1 -Nodes 26.2,1.21.1-fabric   # a subset
pwsh scripts/smoke/Invoke-InClientSmoke.ps1 -Scenario scripts/smoke/scenarios/<other>.json
pwsh scripts/smoke/Invoke-InClientSmoke.ps1 -Nodes 26.3 -Lang ru_ru -Scenario scripts/smoke/scenarios/lang-overflow.json
pwsh scripts/smoke/Invoke-SmokeBatch.ps1 -Node 26.3-fabric -Scenarios guide-robot,quarry-pit   # several scenarios, one node, one table
```

A node is a Stonecutter node name: `26.3` is the NeoForge node, `26.3-fabric` its Fabric twin, `26.3-forge` its
MinecraftForge twin (not NeoForge; #209). The runner reads a node's loader from the `loader=` key of
`versions/<node>/gradle.properties` (a NeoForge node carries none; the name's suffix is the fallback). The default node
list is the twelve NeoForge and Fabric nodes: a Forge node runs only when named (`-Nodes 26.3-forge`, or `-Loader forge`
with a list that holds one) while nothing ships there. All loaders run the same scenarios, except what a loader's dev
runtime lacks (below); each node needs the McDevBridge jar built for it (`gradlew :<node>:build` in McDevBridge, the same
node name there).

Output: `build/smoke/<timestamp>/summary.md` (one table per node, screenshot links) and `<node>/report.json`,
`<node>/NN-<name>.png`, `<node>/NN-<name>.widgets.json`. Exit code 1 when any step failed, any node hit a fatal
error, or BuildCraft logged an ERROR.

## What it does per node

1. **Own game folder.** `versions/<node>/run-smoke/` via `-PbcRunDir=run-smoke` (gitignored). The hand-used `run/`
   with its extra mods and saves is never read, and its mods, config, saves and options are never written. On Fabric one
   file is: the folder is passed as `--gameDir`, but Loom fixes the working directory, so every smoke run still writes
   (and rolls) `run/logs/latest.log`. The node's newest McDevBridge jar is copied into
   `run-smoke/mods` (searched in McDevBridge's `versions/<node>/build/libs` and its worktrees; `-BuildBridge` builds
   one if none exists). `options.txt` is written once: no pause on focus loss, no accessibility onboarding, sound
   off. The GUI scale is re-applied every run: the scenario's `guiScale`, else 2 (so screenshots line up across runs),
   and so is `particles:0` (All), so a scenario that lowers particles in Video Settings cannot thin the next run, and
   the language (`-Lang`, else `en_us`).
2. **World.** The first run on a node creates one through the menus (Singleplayer → Create New World) and renames the
   save to `bc-smoke` after the client closes; every later run quick-plays it (`-PbcQuickPlay=bc-smoke`). The rig is
   rebuilt by the scenario each run, so the save's state never matters. Delete `run-smoke/saves/` to start over. (A lone save of any name is adopted as `bc-smoke`, so an interrupted first run heals itself.)
3. **Scenario**, then every ERROR log line mentioning BuildCraft since the world loaded.
4. `save-all`, close the client (and its Gradle wrapper), next node.

**Keep the physical mouse off the game window** while it runs — a real mouse move overwrites the bridge's synthetic
cursor.

## One run per machine: the lock and the pin

McDevBridge's `~/.mcdevbridge/bridge.json` only ever points at the newest client, so a second client booting beside a run
(another session's runner, a game opened by hand) could hand the run the wrong world, or drive the run's own. Two guards,
both in `SmokeGuard.ps1` (`multiplayer/mp.py` speaks the same lock file):

- **Lock.** The runner takes `~/.mcdevbridge/smoke.lock` before its first boot and holds it until the last node is done
  (released in `finally`). Any other runner (`Invoke-InClientSmoke.ps1`, `mp.py server`) exits at once, booting nothing,
  naming the holder: owner, pid, since, nodes and scenario, repo. The PowerShell runner's exit code for that is 3 (1 stays
  "a step failed or the node was fatal"). A lock whose process is gone (a killed run) is stale and is taken over by the next
  runner, so it is never deleted by hand; the file records the holder's start time, so a pid number reused by another
  process is not mistaken for the holder.
- **Pin.** The bridge's url, token and pid are read once at boot and used from then on, and only a bridge.json that is the
  client THIS run spawned is accepted: the port that client logged to the run's own `<node>/gradle.out.log` must be the
  file's port, and the pid must be a live Java process that started after the spawn and, where Windows will say, listens on that
  port (McDevBridge reports no pid over HTTP, so the OS says who owns the port). Before every bridge call the runner checks the pinned client
  still runs and bridge.json still names it. If another client has taken the file over, or the pinned client is gone, that
  step fails with `bridge pin violated: ...`, the rest of the node's steps are skipped and the node ends FATAL; the pinned
  client is still the one that gets saved and closed, never whatever bridge.json names by then. A missing or half-written
  bridge.json is not a violation (a client exiting deletes it).

## Running on Server1 (nothing heavy runs on the workstation)

The workstation does not boot Minecraft or run Gradle for these checks. `E:/GitHub/McDevBridge/tools/remote/` (its README
has the full story) sends a commit to Server1, runs a command in Server1's logged-on desktop session (the client needs a
GL context, so a plain ssh session will not do) and copies results back. Server1-side paths are under `C:\Dev`.

```powershell
$r = 'E:/GitHub/McDevBridge/tools/remote'
# 1. This branch and McDevBridge, each under a name of its own (another session owns C:\Dev\McDevBridge; never sync over it).
pwsh $r/Sync-ToServer1.ps1 -Repo <this worktree> -Ref <branch> -Name BuildCraft-<tag>
pwsh $r/Sync-ToServer1.ps1 -Repo E:/GitHub/McDevBridge -Ref master -Name McDevBridge-bc
# 2. The node's bridge jar (23 s once the Gradle cache is warm) and a warm compile (6 min warm; allow an hour cold).
pwsh $r/Invoke-OnServer1.ps1 -NoDesktop -Name McDevBridge-bc -TimeoutMinutes 60 -Command './gradlew.bat :26.3-fabric:build --console=plain'
pwsh $r/Invoke-OnServer1.ps1 -NoDesktop -Name BuildCraft-<tag> -TimeoutMinutes 150 -Command './gradlew.bat :26.3-fabric:classes --console=plain'
# 3. The smoke itself: desktop mode (no -NoDesktop), the bridge checkout named by MCDEVBRIDGE_ROOT, and -NoDaemon.
pwsh $r/Invoke-OnServer1.ps1 -Name BuildCraft-<tag> -TimeoutMinutes 300 -PollSeconds 120 -EnvVars 'MCDEVBRIDGE_ROOT=C:/Dev/McDevBridge-bc' `
  -Command 'pwsh scripts/smoke/Invoke-SmokeBatch.ps1 -Node 26.3-fabric -OutDir build/smoke/<name> -BootMinutes 30 -NoDaemon'
# 4. What came back: batch-summary.md first, then the folders of the scenarios that failed.
pwsh $r/Receive-FromServer1.ps1 -Path BuildCraft-<tag>/build/smoke/<name> -Destination build/remote/fetched
```

Start long runs with `run_in_background` and redirect the console to a file; the poller only sleeps. `Invoke-SmokeBatch.ps1`
runs the scenarios of one node one after the other (`-Scenarios a,b`, `-Skip`, `-Lang ru_ru,zh_cn`) and writes
`batch-summary.md` and `batch-summary.json`; the default list is 42 scenarios (a pass over 34 of them on `26.3-fabric` took 47 minutes with no lock wait). A scenario the runner skips (a `loaders` mismatch, or `englishOnly` under `-Lang`) shows as SKIPPED, not as a failure. The default list leaves out `zone-planner-drag`, `library-scrollbar-drag` and `pipe-sounds`: they wait while a real-mouse driver or a listener does the work (the first two fail their `expectFile` step without the driver), so name them in `-Scenarios` when that company is there. Console files are `<scenario>.console.log`, or `<scenario>-<lang>.console.log` under a language other than `en_us`.

Traps (each one cost a run):
- **`-NoDaemon` is not optional there.** A client is a child of the Gradle daemon that serves the build. An idle daemon from
  an earlier `-NoDesktop` build (session 0, no desktop) is reused by the next `runClient`, the window opens in session 0 and
  Minecraft 26.3 aborts in `GlCommandEncoder` with "No context is current or a function that is not available in the
  current context was called". Same checkout, same node, only the daemon's session differs. Never `gradlew --stop` to cure
  it: that stops other sessions' daemons of the same Gradle version too.
- **The smoke lock is shared with McDevBridge's `Invoke-EndpointSweep`**, which takes it node after node with seconds in
  between. The batch driver looks at the lock every 3 s (`-LockWaitMinutes`, default 240) and boots a runner only when it is
  free; a runner retried once a minute can starve for hours.
- **Do not sync over a checkout a run is using** (the sync rewrites tracked files in place), and do not start two runs in one
  checkout: the real-mouse driver below is the one exception, a second desktop run that only moves the mouse.
- Server1 is Windows Server 2019 with an NVIDIA GTX TITAN X. Its console display is 1024x1280, but a client window larger
  than the screen is fine (`window` steps up to 2560x1440 pass). There is no audio device (the client logs "Failed to open
  OpenAL device" and goes silent), so sound-only checks (`pipe-sounds`) cannot be done there.
- `C:` is shared with other work: a node's first build and first boot take a few GB, so look at the free space first (keep
  15 GB) and delete `build/smoke/<name>` folders you have read.
- A whole run folder was 100 MB for 34 scenarios (the default list is 42 now); fetch single scenario folders when only a failure matters.
- The McDevBridge copy is yours to patch for a diagnosis (the bridge reports a screen handler's exception only as
  `ReportedException: mouseClicked event handler`; logging `t` in `BridgeServer.dispatch` puts the cause in the client log),
  but re-sync it with `-Force` and rebuild the jar afterwards.
- The real-mouse scenario `zone-planner-drag` can be played there: see "Real-mouse checks" below.

## Scenario format

```json
{ "name": "line8", "base": [0, 150, 0], "steps": [ { "do": "...", ... } ] }
```

Coordinates in commands use `{X}`, `{Y+2}`, `{Z-1}` (the `base` plus an offset); `at` arrays are offsets from `base`.
Every step may carry `"label"` (report text), `"nodes": ["26.2", ...]` (run only on those Minecraft lines: `"26.2"`
covers every loader's node of that line, the NeoForge node, its Fabric twin and its Forge twin; `"26.2-fabric"` or
`"26.2-forge"` only that twin) and `"loaders": ["neoforge"]` (run only on that loader: a step one loader cannot run says so
here, not by leaving the other twins out of `nodes`). A top-level `"loaders"` skips the whole scenario on the other loaders'
nodes (reported as skipped, not as a failure): use it when a scenario needs a mod only one loader's dev runtime has.
`"needs": ["recipeViewer"]` (top-level or on a step) says what the scenario or step asks of the dev runtime instead of naming
loaders: a loader that lacks it skips the scenario, or records the step as `skip`, with the reason. The capabilities: `recipeViewer`
(implied by the `"recipeViewer"` key; the Forge node has none), `devProperties` (implied by `gradleProperties`: `-PwithGuideMe`
and the like; the Forge node wires none) and `devMods` (implied by `mods` and `-Mods`: the `-PwithMods` aliases, which every
loader reads). A scenario whose own `"mods"` the node defines none of (no `withmod.<alias>` in its `gradle.properties`) is
skipped too, with that reason, instead of running bare; one defined alias is enough to run it (the other lanes of
`crossmod-energy` need aliases only some nodes have). `gen_port_26_3.py` marks its JEI steps with `needs`. A top-level `"englishOnly": true` skips
the whole scenario the same way under any `-Lang` other than `en_us` (see the `-Lang` notes below): it clicks by English
widget text.

| `do` | Fields | Effect |
|---|---|---|
| `cmd` | `run`: string or list; `allowFail`; `benign`; `echo` | server commands (`/command`, OWNER, no entity — target `@p`). A command the bridge reports as failed (its `success` flag, a language-neutral result code) fails the step, in every language, unless the command is optional (a leading `?`), the step has `allowFail` (any failure of the step's commands is fine), or `benign: true` (the commands may legitimately fail: nothing to kill, nothing changed on a re-run, a chunk already force-loaded). English runs also forgive, by the reply text, the "already so" replies (`did not change`, `could not set the block`, `no blocks were filled`, `is already set to`, `is already at time marker`), since the world persists between runs; a translated reply cannot be read, so a scenario meant for `-Lang` says it with `benign` or `?`. World-state setters that fail only when the state is already so (`difficulty`, `time set`, `weather`, `gamerule`, `gamemode`, `forceload add`, `tick freeze`, `tick unfreeze`) are forgiven by the command itself, in every language, unless the command never parsed (vanilla then echoes the input in its error context line, which marks a bad argument rather than an "already so"). `echo: true` copies the last command's reply into the report (state dumps) |
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
| `expectFile` | `path` (relative to the node's output folder, the one holding the screenshots), `timeoutSec` (default 10) | fails unless that file shows up, non-empty and written after the node's steps began (an earlier run's file does not count); the report's Detail holds its content. How a check the game cannot see learns that an outside driver acted: `Drive-RealMouse.ps1` writes `real-mouse-*.done` when its gesture is finished (see "Real-mouse checks"). The path may not leave the output folder |
| `pause` | — | opens the pause menu from the world. The bridge cannot press a key with no screen open, so this posts a real Esc key message to the game window (no focus needed on GLFW nodes). 26.3 runs SDL3, which drops key messages for an unfocused window: when the post does not open the menu within 4 s, the runner brings the game window to the front (an Alt tap lifts Windows' foreground lock) and presses a real Esc, and the step's Detail says so. Follow with `click` "Save and Quit to Title" to leave the world |
| `inWorld` | `timeoutSec` (default 300) | waits until the player is in a world with no screen open: after `click` "Play Selected World" |

`-Lang <code>` (e.g. `ru_ru`, `zh_cn`) runs the scenario in that language, for a text-overflow look: screenshots
and tooltips come out translated. Two things read English and so behave differently:
- `click`/`hover` by `widgetText` match the English text, so a scenario meant for `-Lang` targets widgets by position
  or slot (`scenarios/lang-overflow.json`). A scenario that does click by `widgetText` (without `allowMiss`) carries a
  top-level `"englishOnly": true`: under any `-Lang` other than `en_us` the runner reports it as skipped, the same way
  it reports a `loaders` mismatch, and the batch shows SKIPPED. Runs in English are unaffected.
- The game answers commands in the run's language, so the runner cannot read an "already so" reply to tell it from a
  failure. It does not need to: a failed command (the bridge's language-neutral `success: false`) fails its step under
  `-Lang` exactly as in English, unless the command is optional (`?`) or the step has `allowFail` or `benign: true`
  (the commands of that step may legitimately fail on a re-run), or it is one of the world-state setters listed under
  `cmd` above and parsed (the same rule in every language). The English reply-text forgiveness applies to English
  runs only, so a scenario meant for `-Lang` marks its own re-run failures (`generators/gen_lang_overflow.py` does).
  `waitCmd`'s `match` still reads the translated reply: give a scenario meant for `-Lang` a language-neutral one (NBT
  keys, numbers).
The node's `bc-smoke` world must already exist (the first run creates it through the English menus).

A top-level `"options": {"renderDistance": 4}` pins keys of the run's `options.txt` (`zone-planner-far-map` does); `renderDistance` goes
back to 6 on every run that names none, so a pinned value never reaches the next scenario.

A top-level `"recipeViewer": "rei"` boots the client with REI in place of JEI (`-Precipe_viewer=rei`); see `scenarios/rei-parity.json`.
A NeoForge dev client has JEI without being asked. A Fabric one loads a viewer only when told to, so on Fabric nodes the
runner always passes `-Precipe_viewer` (the scenario's, else `jei`).

A scenario can play a world of its own: top-level `"save": "<folder>"` quick-plays that save instead of `bc-smoke`, and `"saveSeed": "bc-smoke"` copies the seed to it when it is missing. Without a seed the save must already be there (a world carried over from another node); the runner refuses to boot otherwise. `"freshSave": true` (or `-FreshSave`) deletes the save first and copies it from the seed again on every run (the seed must be present and be another save; the runner refuses and keeps the save otherwise); `-Save`, `-SaveSeed` on the command line override the scenario's. See "Cross-mod pass" below.

A scenario that needs an optional dev-runtime mod names its Gradle switch in a top-level `"gradleProperties": ["withGuideMe"]`; each becomes a `-P` flag on the node's `runClient` (`guideme-guide` uses it to load GuideME and page through the generated guide).

A scenario can also name optional dev-runtime mods by alias in a top-level `"mods": ["alias", ...]`, and the runner takes `-Mods alias,alias` for the same thing on the command line. When either is present (the two lists are joined, each alias once) the runner adds `-PwithMods=alias,alias` to the node's `runClient` Gradle command, next to the recipe-viewer switch. The runner only passes the property: the aliases are resolved by the Gradle side, and a name it does not know is its error to report.

A scenario that visits a second world names it in a top-level `"extraSaves": ["bc-smoke-b"]`: before boot the runner copies the `bc-smoke` save to each missing name (so the first run on a node, which only creates `bc-smoke`, needs a second run). The world list is sorted by last played, so the world just left is entry one and `click` `x:213, y:100` picks entry two (see `scenarios/marker-relog.json`, which leaves a world, joins the other and comes back).

A failing step records its error, takes a `NN-FAILED.png`, closes any open screen and the run continues.

## Robot scenarios and their generators

`robots-live`, `robots-boards` and `robots-logistics` build working robot rigs (stations, gates with Work-in-Area
zones, tools in chests) from commands and read the outcome back with `echo` steps. The last two are generated:
edit `generators/gen_boards.py` / `generators/gen_logistics.py` (shared helpers in `generators/robotrig.py`) and
re-run them with `python`, never hand-edit their JSON. Each echo step's label says the expected reply (`expect pass` /
`expect fail` / a count); the report's Detail column carries the actual reply.

## Text overflow in other languages

`lang-overflow` (generated by `generators/gen_lang_overflow.py`) opens every BuildCraft screen, each ledger in turn and
every widget tooltip, clicking by position only, so it runs under `-Lang ru_ru` and `-Lang zh_cn`. Run it in English
too and compare same-named shots: a title wider than its window, a ledger title cut at the ledger's edge, a help entry
that runs off the bottom of the screen. Ledger tabs are not vanilla widgets; the generator computes their positions
from each screen's size at 854x480 and GUI scale 2 (its docstring has the rule): the scenario's first step is a `window`
step that sets that size, since nothing else pins it. The runner deletes `config/buildcraft/gui_state.json` before each
boot so every ledger starts closed. The commands that may fail on a re-run (chunks already loaded, settings already set,
nothing to clear) sit in steps flagged `benign`, so a failed command anywhere else fails the step in every language.

## Facade shading

`facade-shading` (generated by `generators/gen_facade_shading.py`) builds four L-shaped floor-and-wall rigs side by side: real concrete, concrete facades, a real floor under a facade wall, and hollow facades. The first three must show the same ambient-occlusion crease at the wall's foot; the hollow rig shows none. The camera stands on a barrier walkway kept a block clear of the rigs.

## 26.3 port verification

`port-26-3` (generated by `generators/gen_port_26_3.py`) is the 26.3 port's in-client check: translucency with and
without order-independent transparency, model shading, pipe and plug items, Stirling fuel and JEI, mouse buttons,
guide link and tag recipes, fluid shards, the oil feature, the fluid-tag and heightmap echoes. Run it on 26.3 and
26.2 and compare same-named shots; the generator's docstring says how to run the OIT pass.

`upgrade-26-3-author` and `upgrade-26-3-verify` (generated by `generators/gen_upgrade_26_3.py`) check the 26.2 to 26.3 world upgrade: blueprints, the library list and facades saved on 26.2 must read back on 26.3, and 26.3 must read the carried-over `-common.toml`. The generator's docstring has the order: author on 26.2, carry the save, the snapshot folders and the config over with `Copy-UpgradeWorld.ps1`, verify on 26.3 and then on 26.2.

## Release 2026.2.1

`release-2026-2-1` (generated by `generators/gen_release_2026_2_1.py`) carries the release's visual checks, shot names prefixed by item: `a` robot gliding (a 20 s WATCH LIVE wait, docked shot, a five-shot burst mid-flight), `b` stripes retraction facing and an extension sanity shot, `c` a guide link split over a page break clicked on both pages, `d` the Programming Table option grid, `e` the cheap leftovers from the 2026-09-26 smoke. The docstring records what could not be driven.

The same generator writes `release-2026-2-1-f`, the rest of the 2026-09-26 list: `f1` the guide's side chapter tabs on a
window wide enough for them, `f2` the Filler's Preview toggle (the eye button above the invert button and both its
tooltips, the translucent cubes in the box after Fill is picked, the cubes still drawing with the Filler block out of
view, the toggle off again with the cubes gone, and `data get block ... showPreview` echoes of the saved flag), `f3` a
digging Shovelman's exhaust on All and then on Minimal particles (switched in Video Settings).
Volume boxes live in saved data no command reaches, so `f2` sneak-clicks away the box an earlier run left (the opening
clear-and-floor fill has already taken the Filler that locked it) and drags a new one to 3x3x3 with the Volume Wrench;
the generator solves that drag (`solve_drag`) and fails if a layout change leaves no solution. A Filler finds its box when
a player places it, so `f2` right-clicks the floor with the Filler item rather than using `setblock`. Patterns are
dragged from the Filler's list, which the bridge cannot do, so `f2` takes the Fill that an in-place click on the pattern
slot picks.

## Builder stall outlines: `builder-stall`

`builder-stall` (generated by `generators/gen_builder_stall.py`) checks issue #39 on 26.3: a Builder whose build is stuck
outlines the cells it is skipping (red can't stand / can't be broken, yellow missing items, blue fluid) for a player
holding a Marker or Volume Wrench and not with an empty hand, and its GUI readout turns dark red with a "Stalled: ..."
tooltip. It shows the in-world blueprint flow a scenario can drive: an Architect Table is given its box and a blank
blueprint by NBT, the used blueprint goes architect output -> chest -> player inventory (`item replace ... from block`)
-> the Builder's snapshot slot through the real Builder GUI (`click` slot 52, then slot 0), and `waitCmd` gates on the
server's own `builderClientData.stalledCounts`. The generator's docstring lists which cell of the rig stalls for which
reason; its GUI hover coordinates assume the scenario's 1600x900 window at GUI scale 3.

## Filler patterns: `filler-patterns`

`filler-patterns` (hand-written) checks issues #80 and #85: five Fillers set up by NBT (box, pattern and parameters,
power, cobblestone), so no pattern drag is needed. Flatten over a dirt hill with holes in its floor (before and after
shots), Octagon hollow and filled in an 11x9 box, and a quarter-turned Hexagon Filled and Surrounded in a 5x9 box,
shot top-down from a barrier block (a creative player is not flying and falls during a wait). Each Filler's finish is
gated with `waitCmd` on its `finished` flag. Ends on the Flatten Filler's GUI and the pattern slot's tooltip.

## Robot Goggles: `goggles`

`goggles` (hand-written) checks issue #79 on every node: three robots with empty, half and full batteries stand behind a
stone wall and one stands in the open to the side; with a bare head the walled ones are invisible, with the goggles put
on the head (`item replace entity @p armor.head`) they are outlined through the wall, red to green by charge, and a close
shot of the open robot checks its body is not washed out (the outline pass must draw nothing but the outline). The outlines
are gone again once the goggles come off. It then right-clicks the goggles in survival and reads back, with
`execute if/unless items`, that they swapped onto the head (the `Equipable` path on 1.21.1, the equippable component
elsewhere). The outline is vanilla's glowing pass, so the node differences worth
comparing are whether it shows at all (the render-state nodes draw the cube's outline through a model part,
`RenderRobot#submitGogglesOutline`; 1.21.1 through the outline-aware buffer source) and the colours.

## Real-mouse checks: `zone-planner-drag`, `library-scrollbar-drag`

The bridge has no press-drag-release, and the Zone Planner also asks the window system whether a pan's or stroke's
button is still held, which no synthetic event can fake. These two scenarios set the screen up and wait for a REAL
drag: a person, or an agent with desktop control (computer use) driving the game window. `zone-planner-drag` gives 45 s
for an empty-hand pan and then 90 s for a brush stroke, echoing the red layer before and checking it after;
`library-scrollbar-drag` gives 45 s to drag the Electronic Library's scrollbar and needs 14 or more blueprints in the
node's `run-smoke/snapshots-client` first (copy some from its `run-gameTestServer/snapshots-server`).

Neither scenario can pass undriven. Each waits out its fixed time and then has an `expectFile` step for the marker file
`Drive-RealMouse.ps1` leaves in the node's output folder when a gesture is finished (`real-mouse-zp-pan.done`,
`real-mouse-zp-stroke.done`, `real-mouse-lib-scrollbar.done`; the scenario name and the UTC time inside), so a run that
nothing drove fails there. A person or an agent driving by hand does the same: when the drag is done, create the file
(any non-empty content). The Zone Planner has one more check that needs no marker: after the stroke it `waitCmd`s on
`data get block <planner> layer_14` until the reply holds a `chunkX` (the layer's NBT lists one entry per painted chunk;
an unpainted layer reads `{chunkMapping: []}`), the language-neutral proof that the stroke reached the server. The pan
has no server-side state, so the marker and the `zp-panned` shot are all there is; the scrollbar's result is judged from
`lib-after`.

On a machine nobody sits at (Server1) `Drive-RealMouse.ps1` plays both: a second desktop run beside the runner that waits for
the shot that opens each window, brings the game window to the front and sends the real input (SetCursorPos and
mouse_event, so the window system sees held buttons). `zone-planner-drag`: the pan, then the stroke with a right tap while
left is held (the log line says whether left stayed down); `-Scenario library-scrollbar-drag`: the scrollbar thumb to the
bottom. Its positions are framebuffer pixels of the scenario's 1600x900 window; judge the result from the runner's shots.
Start the stroke on flat ground, not on a hill: the stroke's corners are picked from the terrain under the cursor, and a
press on a slope paints a rectangle that reaches past it on screen (a first run on 26.3-fabric did, and looked like a bug;
`-StrokeFrom`/`-StrokeTo` and `-NoRightTap` take the same drag from flat ground and without the tap, and both gave the
rectangle from press to release).

An agent driving them by hand: a warm client reaches the first wait about 30 s after launch, so block on the shot that opens
each window (the `zp-start` shot for the pan, `zp-before` for the stroke, `lib-before` for the library; matched by name, whatever the step number) in the
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

## Zone Planner far map: `zone-planner-far-map`

The scenario pins render distance 4 itself (`"options": {"renderDistance": 4}`; the runner puts it back to 6 on every other scenario). It tours a
cross and a ring across the survey square so the server samples those chunks, returns to spawn and opens the planner:
at survey zoom the toured strips must show past the small loaded patch, joined to it without a seam.

## Two players: `multiplayer/`

What another player sees (arm swings, particles, entity sync that goes server to other clients) needs a dedicated
server and two clients, which the runner above cannot do. `multiplayer/mp.py` boots `:<node>:runServer` and two
`runClient`s (Watcher and Worker) that quick-play into it, all under `versions/<node>/run-smoke/mp-*`, drives world
commands over RCON and each client over its own bridge (it records each client's url and token as it starts), and
stops only what it started. `server` takes the machine-wide smoke lock described above (held by the server's Gradle
process), `client` joins it and `stop` releases it; `b` and the scene scripts fail loudly once a client's pinned process
is gone. `multiplayer/scene_swing_particles.py` is the 26.3 port's B6/B7 check built on it; its
docstring gives the phase order and the expected result, and `multiplayer/grids.py` tiles the shots per node for a
26.3 vs 26.2 comparison. `multiplayer/scene_machines.py` checks machines over the connection (a Tank poured by one player
and opened by both, a Stone Engine's GUI, a creative engine's power rig filmed by the other player) with recorded pass/fail
checks; `scene_lib.py` holds the helpers the two scenes share. Run it from Git Bash with `MSYS_NO_PATHCONV=1`. The server
log's `No key layers` ERROR is vanilla complaining about the flat world's empty generator settings; the default flat world
is used.

Loaders: NeoForge nodes take their run switches through `mp.init.gradle` (an init script); a MinecraftForge node (`26.3-forge`)
through `bc.forge`'s own `-PbcUser`, `-PbcQuickPlayServer` and `-PbcRunDir` (ForgeGradle's client run already carries
`--username Dev`, and a second one is an error, so the init-script route is not available there). A Fabric node has no arm yet:
`mp.py` and the scene scripts skip it with a reason and exit 0. `setup` also works on a node no single-player run has prepared
(it takes the newest bridge jar the way the runner does and writes the default `options.txt`).

**The server on another machine.** The two clients always run here (they need a GL context); the server half can run on
Server1, so the connection is a real network one. Set `MP_REMOTE_CHECKOUT=<name>` (a checkout under `C:\Dev` on Server1, made
with `E:/GitHub/McDevBridge/tools/remote/Sync-ToServer1.ps1 -Repo <this worktree> -Ref <branch> -Name <name>`; committed state
only, so commit first) and `server` writes the game directory there, runs `gradlew :<node>:runServer` through
`Invoke-OnServer1.ps1 -NoDesktop` (the long-lived local process is that poller, which the smoke lock names) and polls the
server's log over scp for `Done`; the clients quick-play into `MP_SERVER_HOST` (default `Server1`, resolved to IPv4) on the
scratch ports `MP_PORT`/`MP_RCON_PORT` (25661/25662; the real servers' 25565, 25566 and 25575 are refused), RCON uses a
password `setup` generates per node (`build/smoke-mp/<node>/rcon.pw`), and `stop` ends the server with RCON `stop`, fetches its
log to `build/smoke-mp/<node>/server-remote-latest.log` and, only if it will not stop, ends the java processes of that checkout
alone. A fresh checkout's first `server` includes a cold Gradle configure (minutes). `MP_LOCK_WAIT_MINUTES=<n>` makes `server`
wait for another run's smoke lock instead of refusing. Server1's firewall must already allow the JDK that Gradle forks (the
Microsoft JDK 25 has an any-port inbound rule there); the script changes no firewall or system setting.

## Resource reload: `sprite-reload`

`sprite-reload` (hand-written) checks that plugs, gates, the docking station and dyed pipes keep correct textures across a resource reload (issue #81): it shoots the rig, turns vanilla High Contrast on and off through Options → Accessibility (each toggle reloads the resource packs), and shoots it after each. All three shot pairs must match. A pipe's `col` NBT takes the enum name in capitals (`LIME`); lowercase is silently ignored.

## Plugs on a shelf: `plug-shelf`

`plug-shelf` (hand-written) checks issue #180 on 1.21.10 and later (shelves): three shelves hold the pulsar, timer, light sensor, blocker, power adaptor, an oak facade, and a gate, red lens and paper as the reference look; every plug must show its face, not its edge. Two mode-less `item_display`s (the facade and a gate) are then shot from the front and from behind: each must show its panel face-on from both sides. Issue #181 adds a third, a pulsar (its piston and LEDs on the north side), and a pulsar cargo run: a creative-engine wooden pipe pulls pulsars from a chest along a stone pipe at eye level. Pipe cargo turns to face its travel direction (as in 1.12.2), so from this side view a pulsar shows its slim side, exactly as the gate, lens, facade and flat vanilla items do; the shot checks it renders. The end-chest echo only reports how many arrived.

## Ledgers over the window: `ledger-narrow-gap`

`ledger-narrow-gap` (hand-written) checks #199 and #200. A ledger whose gap beside the window is narrower than `Ledger_Neptune.MIN_OPEN_WIDTH` (72 GUI px, the gap a 176-wide window leaves on a 320-wide screen) opens at that width (or its natural width when shorter) over the window, clamped to the screen, drawn above the slots, and what it covers is out of reach: no hover, no click, and the keys that act on the hovered slot (Q, the hotbar digits) do nothing. A GUI screen is never narrower than 320 px (vanilla's own minimum; a Windows client area is never narrower either, so a `window` step below 320 wide fails), which only the windows wider than 176 reach: the Builder (256) and the Library (244) here. The scenario runs a wide screen first as the regression reference (ledgers beside the window, the carried stack drawn over an open ledger) and the List's hover ledger scrolled by the wheel while the mouse stays on its slot (#200); then 320x240: the engine, whose 72 px gap must not move its ledgers, the Builder's help ledger overlapping the window (not retargeting while the mouse rests on its panel, the wheel scrolling it, a carried stack drawn above it, Q and `1` pressed over a covered slot after pressing them over an uncovered one, to show the keys arrive at all, and a click on the ledger that must close it without picking up the item beneath), and the Library's owner ledger.

## Cross-mod pass (#186): `crossmod-*`

Scenarios for BuildCraft next to other mods on the dev runtime. The mods come from `-PwithMods` aliases (`withmod.<alias>` in
`versions/<node>/gradle.properties`, resolved by `buildSrc/.../WithMods.kt`); a scenario names them in its top-level `"mods"` and the
runner takes `-Mods a,b` for more (the batch wrapper passes `-Mods` on). All of them are generated: edit
`generators/gen_crossmod_*.py` (shared step builders in `generators/xmod.py`) and re-run it, never hand-edit the JSON.

- **A mod world is its own world.** A mod's blocks and ores end up in the save, and a Fabric client that loads that save without the
  mod stops at "Missing content detected!" (Fabric registry sync), which blocks quick-play. So a scenario that needs mods names a
  `"save"` seeded from `bc-smoke` (`"saveSeed"`) and `"freshSave": true` (the save is deleted and copied from the seed on every run, so a
  claim, a battery or a rig from an earlier run never leaks in). Never explore with mods in `bc-smoke` itself.
- **Comparing runs.** `-Save bc-perf -SaveSeed bc-smoke -FreshSave` (runner and batch wrapper) plays a fresh copy of the seed for every
  scenario, so the same scenario bare and with `-Mods lithium,sodium,...` starts from the same world; without it a scenario builds on
  what the one before it left (item entities, loaded chunks) and the shots differ for that reason alone.
- **The dev user is `Dev` on every loader** (`bc.fabric` passes `--username Dev`), so `ownerUUID:"380df991-f603-344c-a090-369bad2a924a"`
  (Dev's offline UUID) names the player on Fabric as it does on NeoForge.
- **A mod set the loader refuses ends the node at once.** Fabric Loader shows its error window and the process stays alive, so the runner used to wait out the whole boot budget with the smoke lock held; it now reads the loader's refusal from the console log (`Incompatible mod set`, `FormattedException`) and fails the node with the message. A new alias is worth one boot before a long batch: `sodium,iris` on 26.3-fabric was such a case (Sodium 0.9.3-beta.5 refuses Iris 1.12.0 and older, so the `iris` alias carries Sodium 0.9.2: ask for `iris` alone).
- **A command that acts "at the player" acts where he is by then.** A creative player teleported up to a rig is in free fall until
  something is under him: about two seconds after a `tp` to y+4 the dev was 30 blocks down, 16 under the floor of a Flan claim (`flan add`
  floors it 10 blocks under the y it is given). `flan setAdminClaim` (the claim at the command's position) found nothing there,
  said "no claim" without failing, and rig B stayed the dev's own claim on NeoForge, so every machine of the dev's was rightly let
  through (#204, first read as an operator leak in the fake player). Give such a command an explicit position
  (`execute as @p positioned x y z run ...`) and gate the rig on the reply (`waitCmd`), as `gen_crossmod_claims.py` does for Flan.
- **A Quarry's boxes go in with the `setblock`** (`setblock ... quarry[facing=west]{frame_init:1b,...}`): it scans its frame from
  `onLoaded`, which a later `data merge` never runs, so a quarry given its boxes afterwards sits idle with an empty scan queue.

| Scenario | Mods | What it shows |
|---|---|---|
| `crossmod-claims-<mod>` | opac, flan (both loaders), ftbchunks (NeoForge 1.21.1, 1.21.11, 26.1.2) | three rigs of Quarry, Builder, Filler, stripes pipe and a Stripes robot beside a claim: none, one that is not the owner's, one that is. Each machine and each kind of act (place, break) is probed separately. `-stranger` variants (opac, flan) claim rig B like rig C (the dev's) and give its machines to a stranger instead; `flan-admin-stranger` does that inside an admin claim, where Flan refuses a machine whose owner is no known player through its separate fake-player permission. Flan's claims are asserted (`flan info` must name the owner) before any machine is placed. An admin claim stops everyone but operators: Flan lets a player of permission level 2 and up through (`Claim.isAdminIgnore`), a real operator's hands included, and on NeoForge a machine's fake player answers `permissions()` with its owner's level, so an operator's machines pass as the operator does (measured on 26.3 with the world's cheats forced on, #204: real hands, `canInteract` of the real player and of the fake player all allow, and `flan` rig B fails 7 of 7). The dev world has cheats off, so the dev is no operator and `flan` rig B is refused on both loaders |
| `crossmod-energy` | energizedpower (both loaders); tre, techreborn (Fabric) | BuildCraft's MJ dynamo into Energized Power and Tech Reborn batteries and cables, their creative sources into BuildCraft's FE engine; the other mod's stored energy rose, the Mining Well dug. The Tech Reborn lanes run only on the Fabric nodes with a techreborn alias |
| `crossmod-energy-notre` | none (Fabric) | the same BuildCraft blocks with no Team Reborn Energy: they place, open their GUIs, nothing is logged |
| `crossmod-storage` | tomsstorage, irontanks (both loaders); extendeddrawers, techreborn (Fabric) | item pipes in and out of Tom's Simple Storage and Extended Drawers; fluid pipes, a Pump and a Flood Gate against Tech Reborn tank units with the 81-droplets-per-millibucket rule (1000 mB, 1234 mB, a unit holding a part millibucket) and against Iron Tanks (26.1.2, 26.2). Each mod's lanes run only where it has an alias |

The Fabric dev run has no recipe viewer unless asked and Team Reborn Energy only through `tre` (or a mod that nests it: Tech Reborn,
Energized Power); 1.21.x Fabric nodes carry it always.
