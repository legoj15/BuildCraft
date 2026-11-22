---
name: in-client-smoke
description: Visually verifying BuildCraft in a real dev client on one or all Stonecutter nodes — render sites, machine GUIs, button hovers/tooltips, guide book clicks — with the scripted McDevBridge runner (scripts/smoke/Invoke-InClientSmoke.ps1). Use when a change needs an in-game look, when an open issue asks for an "in-client" or "in-game check", or when the user asks to smoke-test, screenshot, or click through the game.
---

# In-client smoke runs

The runner and its scenario format are documented in [scripts/smoke/README.md](../../../scripts/smoke/README.md) —
read it first. This skill is the procedure around it.

## Before running

- **Prefer a test over a screenshot.** Anything the game can report (a block's destroy speed, a sound type, which
  `InteractionResult` a click returns, a JEI key) belongs in a unit or game test (`add-game-test` skill). The smoke
  run is for what only eyes can judge: textures, models, layout, tooltips, animations.
- **McDevBridge jars**: the runner copies the newest `mcdevbridge-*+mc<node>.jar` from `E:/GitHub/McDevBridge`
  (`versions/<node>/build/libs`, or a McDevBridge worktree). None → `-BuildBridge`, or build it there. McDevBridge
  uses the same node names: `26.3` is the NeoForge jar, `26.3-fabric` the Fabric one, `26.3-forge` the MinecraftForge one.
  `/screenclick` + `/screenhover` need McDevBridge `4f0b536` or later.
- **Free disk**: each first boot of a node downloads assets and writes a world. A full C: drive fails the boot.
- **One run per machine.** The runner holds `~/.mcdevbridge/smoke.lock` for the whole run. When another session's run
  has it, the runner boots nothing and exits **3**, naming the holder (pid, nodes, repo): wait for that run, don't work
  around it. A lock left by a killed run is taken over automatically; delete it by hand only when its pid is gone and a
  runner still refuses. A client started by hand mid-run (the lock cannot stop that) breaks the bridge pin: the step reads
  `bridge pin violated: ...`, the node ends FATAL, and its results are not to be trusted; close the stray client and rerun.
- **Mouse off the game window** for the whole run (a real mouse move overrides the synthetic cursor). The window
  takes focus when it opens; warn the user if they're at the machine.

## Running

```powershell
pwsh scripts/smoke/Invoke-InClientSmoke.ps1 -Nodes 26.1.2          # one node while authoring
pwsh scripts/smoke/Invoke-InClientSmoke.ps1 -Nodes 26.3,26.3-fabric # a line on both loaders
pwsh scripts/smoke/Invoke-InClientSmoke.ps1                        # all twelve, sequentially (~3-5 min each once warm)
pwsh scripts/smoke/Invoke-InClientSmoke.ps1 -Loader fabric         # the six Fabric twins only
pwsh scripts/smoke/Invoke-InClientSmoke.ps1 -Nodes 26.3-forge      # MinecraftForge (#209): opt-in, never in the default list
```

A visual change is checked on both loaders: the Fabric nodes have their own render and screen glue, so a NeoForge-only
run does not cover them (the Forge node has its own too, `platform/forge/client`, and is run by name until it ships).
A scenario or step that needs what a loader's dev runtime lacks (`"needs"`: a recipe viewer, `gradleProperties` switches; Forge has
no switches, and a viewer, JEI, on `1.21.1-forge` only) is reported SKIPPED with the reason, not failed, and so is a `"mods"` scenario whose aliases the node does not define
(Forge nodes define `opac` only, so `crossmod-claims-opac*` run there and the flan ones skip). A step's `"nodes": ["26.3"]` names the
Minecraft line and covers every loader's node of it (NeoForge, Fabric and Forge twins), as it always did for the Fabric twin; a step
one loader cannot run is `"loaders"`-filtered. `-Lang ru_ru` / `-Lang zh_cn` re-run a scenario translated, to look for text that overflows
its box (`scenarios/lang-overflow.json` tours the GUIs without clicking by English text).

Run it with `run_in_background` — a node's first boot compiles and can take several minutes. It exits 1 on any
failed step, fatal error or BuildCraft ERROR log line, and 3 when another run holds the smoke lock.

## Two players, and a server on another machine

What another player sees, and anything that only crosses a real connection (menu opening data, recipe and fluid sync, entity
sync), needs a dedicated server and two clients: `scripts/smoke/multiplayer/mp.py` with the scenes beside it (`setup`, `server`,
`client A`, `client B`, a scene, `stop`; the README's "Two players" section has the commands). It drives NeoForge and Forge nodes
(a Fabric node is skipped with a reason). The server half can run on Server1 so the clients join over the LAN:
`MP_REMOTE_CHECKOUT=<checkout>` (committed state synced with `Sync-ToServer1.ps1 -Name <checkout>`), `MP_LOCK_WAIT_MINUTES` to
queue behind another run's smoke lock; scratch ports 25661/25662 only, never the real servers' 25565, 25566 or 25575.

## On Server1 (the default for anything long)

The workstation must not run Minecraft or Gradle for a pass. [scripts/smoke/README.md](../../../scripts/smoke/README.md)
("Running on Server1") has the commands: sync the branch and McDevBridge under names of their own with
`E:/GitHub/McDevBridge/tools/remote/Sync-ToServer1.ps1`, run `scripts/smoke/Invoke-SmokeBatch.ps1` through
`Invoke-OnServer1.ps1` in desktop mode with `-EnvVars 'MCDEVBRIDGE_ROOT=C:/Dev/McDevBridge-bc'`, fetch the folder back with
`Receive-FromServer1.ps1`. Three traps:
- Always `-NoDaemon`. A client is a child of the Gradle daemon; a reused idle session-0 daemon opens its window with no
  desktop and Minecraft 26.3 dies in `GlCommandEncoder` ("No context is current"). Never `gradlew --stop` (other sessions'
  daemons).
- The smoke lock is shared with McDevBridge's endpoint sweeps; the batch driver waits for it (it polls every 3 s).
- A screen handler's exception reaches the report only as `ReportedException: mouseClicked event handler`; the cause
  is in the client log only if the bridge logs it (patch `BridgeServer.dispatch` in YOUR McDevBridge copy, see the README).
`scripts/smoke/Drive-RealMouse.ps1` plays the two real-mouse scenarios there.

## Reviewing

1. Read `build/smoke/<stamp>/summary.md`: failed steps, BuildCraft log errors, fatal errors first.
2. Open every screenshot (`Read` the PNGs) and compare against the step's `look` text. Crop/zoom small geometry
   before judging (pipes are glass-framed by design; don't mistake that for missing textures).
3. The same shot across nodes is the useful comparison — a node that differs is the finding.
4. Record results as a comment on the issue that asked for the check (`bash ~/.claude/skills/issues/issues.sh note N`), `done N` when
   the check passes, and file anything broken as its own issue (`bash ~/.claude/skills/issues/issues.sh add … --type bug`).

## Authoring scenario steps

- Open the screen, then add a `widgets` step: its JSON dump gives each widget's text and GUI rect, which is what
  `click`/`hover` target. Widgets with no text (icon buttons) are targeted by `x`,`y` from that dump (rect centre).
- Build rigs with `cmd` (`setblock`, `data merge block`). Pipes: `setblock ...:pipe_holder` then
  `data merge block X Y Z {pipe:{def:"buildcraftunofficial:<pipe id>"}}`. Engines need a redstone signal.
- `aim` then `use` to open a block's GUI (empty hand: `hold minecraft:air`); `hold` + `look` up + `use` for item GUIs.
- Node-specific steps take `"nodes": [...]`; keep one scenario for all nodes where possible.
- Screenshots show the frame after the step: add a `wait` after anything animated or freshly placed.

## Known limits

- `/command` has OWNER rights but no entity: target `@p`, never `@s`.
- A failed step closes any open screen and the run continues, so later steps may fail as a consequence — read the
  first failure first.
- 26.2 dev clients may need Vulkan for some render paths (see memory `reference_dev_vulkan_26_2`); the runner uses
  the default renderer.
