Last audited: 2026-09-26

*One line per item. Background, file pointers, and design notes live in [docs/todo-details.md](docs/todo-details.md) (or a dedicated doc, where linked). Finished items are removed, never checked off.*

## 🔧 Outstanding work

- Fix the last places English text leaks through translations — [list](docs/todo-details.md#translation-follow-ups)
- In-game check of the few visual changes the scripted check doesn't cover yet — [checklist](docs/todo-details.md#in-client-smoke-2026-09-26)
- Electronic Library keeps only one copy when the same build is saved under two names — [notes](docs/todo-details.md#snapshot-name-collision)
- Zone Planner may not save changes made in its slots — [notes](docs/todo-details.md#zone-planner-slot-saving)
- Builders in Clear mode loop forever on a block that outside water keeps re-flooding — [notes](docs/todo-details.md#builder-clear-mode-reflood-loop)
- Quarry frame stalls on a block it isn't allowed to mine — [notes](docs/todo-details.md#quarry-frame-stall)
- Mining Well tube permanently deletes the water it drills through — [notes](docs/todo-details.md#mining-well-tube-erases-water)
- Pumps and robot planting/tool use still skip claim protection — [notes](docs/todo-details.md#machine-protection-gaps)
- Stop pumps retrying a modded fluid block that refuses to be drained (low priority) — [notes](docs/todo-details.md#fake-player-cache-hardening)
- JEI category for the Integration Table — [notes](docs/todo-details.md#integration-table-jei)
- Three marker game tests flaked together once on 26.1.2 — [notes](docs/todo-details.md#flaky-marker-tests)
- Robots: make sure every reservation is released when a robot unloads or dies — [audit follow-ups](docs/robotics-gameplay-audit.md#follow-ups)
- Small code cleanups found in passing — [list](docs/todo-details.md#small-cleanups)

## 🆕 New Features (version 2026.2)

- Use modern Minecraft sounds where they fit (copper grate for pipes, etc.)
- Fuel heat changes engine output; the route to a Nether start — [design](docs/todo-details.md#heat-tiered-fuel-and-nether-oil)
- Oil spawns in the Nether — [design](docs/todo-details.md#heat-tiered-fuel-and-nether-oil)
- A Pump on top of a Mining Well or Quarry drains the fluid blocking the dig — [notes](docs/todo-details.md#pump-on-top-of-miners)
- Show a marker connection's length while looking at it
- Zone Planner survey view reaching beyond loaded chunks, via a server-sampled region map like 1.7.10 kept — [notes](docs/todo-details.md#zone-planner-far-map-data)
- Editable, reclaimable marker regions in survival — [notes](docs/todo-details.md#reclaimable-marker-region)
- Smooth shading on facades — [quick notes](docs/todo-details.md#quick-notes)
- Abandoned quarry frames over pre-dug holes (worldgen)
- Make the wrench a real tool: light damage, durability, enchantable, best against BuildCraft blocks, maybe a zombie drop
- Let resource packs reshape plugs via standard model files — [notes](docs/todo-details.md#plug-model-unification)
- Exploding and flammable fluids (gaseous fuel, oils)
- Cauldrons act as small tanks for pipes and pumps — [quick notes](docs/todo-details.md#quick-notes)
- Empty "to" slot on the Replacer flips its button to "Remove" (useful for clearing grass tufts)
- Builder highlights blocks it can't place yet, so a stalled build is legible — [quick notes](docs/todo-details.md#quick-notes)
- AE2 GuideME support
- Quarry item transport visualization
- Alternate recipe input/output pickers on the Advanced Crafting Table (different woods, stones, stairs, doors)
- Enchantable Quarry and Mining Well
- Give the heat exchanger a better in-world look — [notes](docs/todo-details.md#heat-exchanger-visual-overhaul)
- New Filler mode icons (fresh art — see [quick notes](docs/todo-details.md#quick-notes))
- Real goggles art — [quick notes](docs/todo-details.md#quick-notes)
- Decide what happens to the dev-only item files that ship in release jars — [question](docs/todo-details.md#dev-only-item-files-in-release-jars)

## 🚫 Blocked

- Re-verify the REI integration — no REI release for MC 26.1 exists yet — [notes](docs/todo-details.md#rei-recompile)
- Dedicated pipe-texture atlas — vanilla render internals forbid it today — [why + reopen trigger](docs/todo-details.md#pipe-atlas-split-blocked)
- Fluid viscosity — floating gases and swim effects need vanilla support — [quick notes](docs/todo-details.md#quick-notes)
- Bump the 26.2 node past NeoForge 26.2.0.84 — .87's access transformer breaks the dev recompile for everyone — [diagnosis](docs/todo-details.md#neoforge-262087-recompile-break)
