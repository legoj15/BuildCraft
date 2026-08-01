Last audited: 2026-08-01

*One line per item. Background, file pointers, and design notes live in [docs/todo-details.md](docs/todo-details.md) (or a dedicated doc, where linked). Finished items are removed, never checked off.*

## 🔧 Outstanding work

- Find the 25 game tests that silently skip on Minecraft 1.21.1 — [notes](docs/todo-details.md#skipped-game-tests-on-1211)
- Fix the last places English text leaks through translations — [list](docs/todo-details.md#translation-follow-ups)
- Give the heat exchanger a better in-world look — [notes](docs/todo-details.md#heat-exchanger-visual-overhaul)
- New Filler mode icons (fresh art — see [quick notes](docs/todo-details.md#quick-notes))
- Let facades survive flowing water, like pipes now do — [notes](docs/todo-details.md#waterlogging-facades)
- Make the Electronic Library's snapshot list scrollable — [notes](docs/todo-details.md#electronic-library-scrolling)
- Real goggles art — [quick notes](docs/todo-details.md#quick-notes)
- Use modern Minecraft sounds where they fit (copper grate for pipes, etc.)
- Unify button implementation (native buttons over custom background images)
- Collapse duplicate fluid textures on the atlas (low value) — [notes](docs/todo-details.md#fluid-atlas-de-duplication)
- Stop creating a fresh fake player for every machine permission check — [notes](docs/todo-details.md#fake-player-churn)
- Delete dead render-event leftovers — [notes](docs/todo-details.md#dead-addsectiongeometryevent-remnants)
- Move off a fluid helper NeoForge is about to remove — [notes](docs/todo-details.md#deprecated-fluidutil-helper)
- Turn on Gradle's configuration cache for faster builds — [notes](docs/todo-details.md#gradle-configuration-cache)

## 🆕 New Features (version 2026.2)

- Bring robots back — next: first boards + recharge AI — [plan](docs/robotics-resurrection.md)
- Fuel heat changes engine output; the route to a Nether start — [design](docs/todo-details.md#heat-tiered-fuel-and-nether-oil)
- Oil spawns in the Nether — [design](docs/todo-details.md#heat-tiered-fuel-and-nether-oil)
- A Pump on top of a Mining Well or Quarry drains the fluid blocking the dig — [notes](docs/todo-details.md#pump-on-top-of-miners)
- Show a marker connection's length while looking at it
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

## 🚫 Blocked

- Re-verify the REI integration — no REI release for MC 26.1 exists yet — [notes](docs/todo-details.md#rei-recompile)
- Dedicated pipe-texture atlas — vanilla render internals forbid it today — [why + reopen trigger](docs/todo-details.md#pipe-atlas-split-blocked)
- Fluid viscosity — floating gases and swim effects need vanilla support — [quick notes](docs/todo-details.md#quick-notes)
