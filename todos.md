Last audited: 2026-10-17

*One line per item. Background, file pointers, and design notes live in [docs/todo-details.md](docs/todo-details.md) (or a dedicated doc, where linked). Finished items are removed, never checked off.*

## 🔧 Outstanding work

- In-game check of the few visual changes the scripted check doesn't cover yet — [checklist](docs/todo-details.md#in-client-smoke-2026-09-26)
- Builders can sit busy for ever beside fluid they can't remove (outside lava in Clear mode, any water in Replace mode) — [notes](docs/todo-details.md#builder-stalls-beside-unremovable-fluid)
- On Minecraft 1.21.1, /setblock over a Tank, Heat Exchanger, Flood Gate, Combustion Engine or item machine still spills its contents — [notes](docs/todo-details.md#setblock-spills-on-1211)
- Guide book: a link that wraps onto the next page only clicks on one of its two pages — [notes](docs/todo-details.md#guide-page-spanning-link-clicks)
- Small code cleanups found in passing — [list](docs/todo-details.md#small-cleanups)

## 🆕 New Features (version 2026.2)

- Guide book follow-ups from the Robots chapter: board icons, sort-button art, robot name tags — [list](docs/todo-details.md#guide-book-follow-ups)
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
- Decide whether Forbid Robot should also stop a robot already docked at that station (7.1.x: no) — [notes](docs/todo-details.md#forbid-robot-on-the-docked-station)
- Decide how JEI's "+" and drag-and-drop treat programmed boards, robots and items with extra data — [questions](docs/todo-details.md#jei-transfer-decisions)
- Decide what happens to the dev-only item files that ship in release jars — [question](docs/todo-details.md#dev-only-item-files-in-release-jars)

## 🚫 Blocked

- Re-verify the REI integration — no REI release for MC 26.1 exists yet — [notes](docs/todo-details.md#rei-recompile)
- Dedicated pipe-texture atlas — vanilla render internals forbid it today — [why + reopen trigger](docs/todo-details.md#pipe-atlas-split-blocked)
- Fluid viscosity — floating gases and swim effects need vanilla support — [quick notes](docs/todo-details.md#quick-notes)
- Drop the old JEI recipe-transfer method on 26.2 once JEI deletes it — [notes](docs/todo-details.md#jei-old-transfer-overload)
