# BuildCraft 1.12.2 → 26.1.x Port Status

Last audited: 2026-05-07. The changelog block in `changelog.md` titled "Changes since 26.1.x Beta release 6" is authoritative for what has shipped since the previous audit; consult it before re-auditing this file.

## Module Status Overview

| Module | Active `.java` | `.java.disabled` | Status |
|:---|:---:|:---:|:---|
| **API** | 231 | 0 | ✅ Complete |
| **Lib** | 606 | 0 | ✅ Complete |
| **Core** | 88 | 0 | ✅ Complete |
| **Transport** | 122 | 0 | ✅ Complete |
| **Silicon** | 75 | 0 | ✅ Complete |
| **Factory** | 51 | 0 | ✅ Complete |
| **Energy** | 31 | 0 | ✅ Complete |
| **Builders** | 127 | 0 | ✅ Complete |
| **Robotics** | 12 | 0 | ⚠️ Only Zone Planner ported (low priority — robots were not actively maintained in 1.12.2) |

**Zero `.java.disabled` files remain anywhere in the project.** The audit deleted 240 total: 113 paired files (where an active version already existed) and 127 orphans (where modern NeoForge facilities or the active port covered the responsibility). 1.12.2 logic remains accessible via the `8.0.x-1.12.2` branch if reference is ever needed.

---

## 🔧 Outstanding work

### Advancements — 17 orphaned triggers
The `minecraft:impossible` trigger is intentional in this codebase: it's the "don't double-grant from JSON" placeholder for advancements granted via `AdvancementUtil.unlockAdvancement(...)` in Java. 24 of 41 impossible-trigger advancements ARE wired up that way (`diggy_diggy_hole` is granted at `TileQuarry.java:606` when a quarry's mining frame finishes; the 2026-04-25 audit had it incorrectly on the orphan list). The remaining 17 are truly orphaned — JSON exists, no Java grant:

- [ ] `all_plugged_up`
- [ ] `building_for_the_future`
- [ ] `colorful_electricial`
- [ ] `destroying_the_world`
- [ ] `flooding_the_world`
- [ ] `goggles`
- [ ] `heating_and_distilling`
- [ ] `lava_power`
- [ ] `list`
- [ ] `logic_transportation`
- [ ] `paper`
- [ ] `paving_the_way`
- [ ] `refine_and_redefine`
- [ ] `start_of_something_big`
- [ ] `sticky_dipping`
- [ ] `to_much_power`
- [ ] `too_many_pipe_filters`

### Block Sounds
- [ ] Most blocks use default `SoundType` — review and set appropriate sounds per block type (engines, pipes, facades, tanks, machines). Only 43 explicit `SoundType` declarations exist across the codebase (mostly `METAL`, with three `STONE` and three `SLIME_BLOCK` cases); a deliberate sound pass is still needed.

### Config System
- [ ] **Power unit display configuration** — MJ ↔ RF/FE wording is done (`useRfNaming`, post-Beta-6) and the abbreviated-vs-full unit naming toggle (`useFullEnergyNames`, `MJ` ↔ `Minecraft Joules` / `FE` ↔ `Forge Energy` / `RF` ↔ `Redstone Flux`) is done; large-number abbreviation (`1.2k MJ`) and per-tick/per-second toggle still missing.

### Guidebook
- [ ] Fill the last 2 stubs: `engine_basics.md` (1-line title only) and `registry_overview.md` (empty).

### Help Ledger — content coverage
- [ ] The `LedgerHelp` framework is active and interactive slot/tank highlighting works (confirmed in `ScreenEngineIron`, etc.). Per-block content is the gap: each block needs to register `ElementHelpInfo`s via `addHelpElements()`. Coverage is uneven; sweep needed.

### Robotics
- [ ] No robots, robot AI, robot stations, or robot items ported. **Low priority** — robots were not actively maintained in 1.12.2.

---

## 🆕 New Features (Post-Port)

- [ ] Quarry LEDs
- [ ] Facade smooth shading (AO infrastructure exists in `MutableQuad`; needs facade-specific wiring)
- [ ] Fix texture filtering bug and crash *(needs description / repro to be actionable)*

---

## 🧹 Finalization

### Other finalization
- [ ] Deprecation and warning fixes (both on compile and during runtime)
- [ ] Final code review across all modules