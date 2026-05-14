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
- [x] **Power unit display configuration** — umbrella of cosmetic knobs that shape how `LocaleUtil` renders energy values in machine readouts, ledgers, JEI labels, and chat. All four sub-features shipped:
  - ✅ **Unit naming.** `display.useRfNaming` (post-Beta-6) flips `FE`/`RF` in every callsite of `LocaleUtil.energyUnit()` and the `BCEnergyConfig.rfFeKey(...)` translation-key swap. `display.useFullEnergyNames` (post-`useRfNaming`, default `true` after in-game verification) layers on top to expand abbreviations to full names — `MJ` ↔ `Minecraft Joules`, `FE` ↔ `Forge Energy`, `RF` ↔ `Redstone Flux`. Combines as a 2×2: `Forge Energy` (default), `Redstone Flux`, `FE`, `RF`.
  - ✅ **Number formatting.** `display.thousandsSeparator` cycles `COMMA` (default) / `DOT` / `SPACE` / `NONE` for the integer-portion grouping, and `display.decimalSeparator` cycles `DOT` (default) / `COMMA` for the fractional separator. Plumbed through new `LocaleUtil.formatLong(long)` and `LocaleUtil.formatDouble(double, int)` helpers; the existing formatters (`localizeMj`, `localizeMjFlow`, `localizeHeat`, `localizeRf`, `localizeRfFlow`) and the two JEI category power callsites all route through them. Covered by `LocaleUtilNumberFormatTester` (every 4×2 combination plus rounding and null-config fallback).
  - ✅ **Large-number abbreviation** (`1.2k FE`). `display.abbreviateLargeNumbers` (default off) collapses `LocaleUtil.formatLong` outputs at or above 1,000 into k/M/G/T suffixed form at one fractional digit, honouring the configured `decimalSeparator` and handling the tier-crossing rounding edge (`999,950` → `1.0M`, not `1000.0k`). Applied only to `formatLong`, so FE/RF readouts compact while MJ readouts and JEI category recipe-cost labels — both routed through `formatDouble` — keep their full precision.
  - ✅ **Per-tick vs per-second flow display.** `display.flowDisplay` cycles `PER_SECOND` (default — `100.00 MJ/s`, matching 1.12.2's display) / `PER_TICK` (`5.00 MJ/t` — the underlying API figure) / `BOTH` (`100.00 MJ/s (5.00 MJ/t)`). Plumbed via two new package-visible helpers `formatMjFlow(double mjPerTick, FlowDisplay, String unit)` and `formatRfFlow(int rfPerTick, FlowDisplay, String unit)` taking the unit string explicitly so unit tests don't depend on the `mjUnit()`/`energyUnit()` defaults. The diverging `localizeMjFlow(double mjPerTick)` overload (which had zero call sites — confirmed by grep) was deleted while we were in there. Pipe-gate "Switch to N MJ/t limit" chat is intentionally NOT routed through this — pipe transfer limits are inherently a per-tick API concept and the chat is correct documentation, not display drift.

### Guidebook
- [ ] Fill the last 2 stubs: `engine_basics.md` (1-line title only) and `registry_overview.md` (empty).

### Help Ledger — content coverage
- [ ] The `LedgerHelp` framework is active and interactive slot/tank highlighting works (confirmed in `ScreenEngineIron`, etc.). Per-block content is the gap: each block needs to register `ElementHelpInfo`s via `addHelpElements()`. Coverage is uneven; sweep needed.

### Robotics
- [ ] No robots, robot AI, robot stations, or robot items ported. **Low priority** — robots were not actively maintained in 1.12.2.

---

## 🆕 New Features (Post-Port)

- [ ] **Pump-on-top fluid extraction for Mining Well & Quarry.** Today both miners stop or skip when they hit blocking fluids — the Mining Well halts on lava / oil, the Quarry skips fluid columns. Adding a Pump on top of either machine should consume fluid from the same blocking column, draining it (capturing the fluid into a buffer / adjacent tank or pipe) so the miner can resume into the now-empty position. Power for the on-top Pump should come out of the host miner's internal MJ battery rather than requiring a separate engine hookup — the miner is effectively "wearing" the pump and shares its energy budget. The Mining Well's top texture should swap to the open Flood Gate sprite when a Pump is mounted, so the on/off state is visible at a glance. Open questions: what happens when both are running on the same battery (priority / throttling), where the drained fluid actually goes (does the pump's standard adjacent-tank output handle it, or does the host miner need an internal fluid buffer), and whether the Quarry's larger column needs a different sweep pattern than the Well's single-block-below path.
- [x] Quarry LEDs — two LEDs (green + red) on the front and two side faces (rear face omitted because it's the typical cabling/wall side). States: **Green only** = actively mining (`hasPower` && `currentTask != null`); **Red only** = no power but has work; **Both** = no current task (done, or not yet surveying). Pump's `RenderPartCube` positions reused unchanged (`Y=13.5/16`, side offsets `1.5/16` and `3.5/16`). New BER `RenderQuarry` reads `TileQuarry.hasPower()` / `isMining()` and skips the iteration where `dir == FACING.getOpposite()`; existing event-based laser/rig rendering in `BCBuildersEventDist` is untouched.
- [ ] Facade smooth shading (AO infrastructure exists in `MutableQuad`; needs facade-specific wiring)
- [ ] Fix texture filtering bug and crash *(needs description / repro to be actionable)*

---

## 🧹 Finalization

### Other finalization
- [ ] Deprecation and warning fixes (both on compile and during runtime)
- [ ] Final code review across all modules