---
name: in-client-smoke
description: Visually verifying BuildCraft in a real dev client on one or all Stonecutter nodes — render sites, machine GUIs, button hovers/tooltips, guide book clicks — with the scripted McDevBridge runner (scripts/smoke/Invoke-InClientSmoke.ps1). Use when a change needs an in-game look, when todos/todo-details list "in-client" or "in-game check" items, or when the user asks to smoke-test, screenshot, or click through the game.
---

# In-client smoke runs

The runner and its scenario format are documented in [scripts/smoke/README.md](../../../scripts/smoke/README.md) —
read it first. This skill is the procedure around it.

## Before running

- **Prefer a test over a screenshot.** Anything the game can report (a block's destroy speed, a sound type, which
  `InteractionResult` a click returns, a JEI key) belongs in a unit or game test (`add-game-test` skill). The smoke
  run is for what only eyes can judge: textures, models, layout, tooltips, animations.
- **McDevBridge jars**: the runner copies the newest `mcdevbridge-*+mc<node>.jar` from `E:/GitHub/McDevBridge`
  (`versions/<node>/build/libs`, or a McDevBridge worktree). None → `-BuildBridge`, or build it there.
  `/screenclick` + `/screenhover` need McDevBridge `4f0b536` or later.
- **Free disk**: each first boot of a node downloads assets and writes a world. A full C: drive fails the boot.
- **Mouse off the game window** for the whole run (a real mouse move overrides the synthetic cursor). The window
  takes focus when it opens; warn the user if they're at the machine.

## Running

```powershell
pwsh scripts/smoke/Invoke-InClientSmoke.ps1 -Nodes 26.1.2          # one node while authoring
pwsh scripts/smoke/Invoke-InClientSmoke.ps1                        # all five, sequentially (~3-5 min each once warm)
```

Run it with `run_in_background` — a node's first boot compiles and can take several minutes. It exits 1 on any
failed step, fatal error or BuildCraft ERROR log line.

## Reviewing

1. Read `build/smoke/<stamp>/summary.md`: failed steps, BuildCraft log errors, fatal errors first.
2. Open every screenshot (`Read` the PNGs) and compare against the step's `look` text. Crop/zoom small geometry
   before judging (pipes are glass-framed by design; don't mistake that for missing textures).
3. The same shot across nodes is the useful comparison — a node that differs is the finding.
4. Record results where the checklist lives (usually `docs/todo-details.md`), trim the todo bullet, and file
   anything broken as its own bullet.

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
