# Robotics gameplay audit — port (Ph0–Ph6) vs BuildCraft 7.1.27

Started 2026-09-03. Companion to [robotics-resurrection.md](robotics-resurrection.md) and
[gameplay-differences-1.7.10-vs-1.12.2.md](gameplay-differences-1.7.10-vs-1.12.2.md). Agent-facing working
record: what was compared, how, what was found, the status of each finding, and what was fixed. The
user-facing outcome is the todos.md bullets it produces.

## Method

Two legs, run together:

1. **Code audit, fanned out on GLM-5.3-flash** — 18 opencode agents, one slice each (entity lifecycle, AI
   framework, movement/pathfinding, search AIs, item transfer, fluid transfer, work AIs, four board groups,
   station/registry, statements, items/registration, render, zone planner, completeness manifest, test-gap
   map). Each got COPIES of the port slice (`src/main/java/buildcraft/robotics` + api), the matching
   `upstream/7.1.x` files, the port's tests and the background docs, plus the deliberate-divergence list so
   those are not re-reported, and returned a `severity | kind | port:line | ref:line | claim | evidence |
   in-game check` table. 16/18 delivered (the two cross-cutting ones — completeness manifest, test-gap map —
   ran out of steps; their ground is covered by the gameplay-differences doc §2–3 and each slice's
   "untested behaviours" list). Cost ≈ 2,330 Z.ai credits, ~10 min per agent at 8 concurrent. Every GLM
   claim was a CLAIM until adjudicated below against the actual source — the slices routinely "refuted"
   facts that lived outside the files they were given.
2. **In-game verification against the live oracle.** For the first time the original 7.1.27 robots run
   beside the port: McDevBridge's `forge-1.7.10` module + a RetroFuturaGradle-deobfuscated CurseForge
   `buildcraft-7.1.27.jar` (file 6240755 — the same version as the `upstream/7.1.x` head the port was
   translated from) in `E:\GitHub\McDevBridge\forge-1.7.10\run\mods\`, and the port on the `26.2` node in
   this worktree, both driven over the bridge's loopback HTTP API from PowerShell. Setup recipe and traps:
   the `reference_bc71_reference_client` memory note.

### Tooling fixed on the way

- McDevBridge `forge-1.7.10`: the integrated server crashed on the first tick of any world
  (`IllegalAccessError … LoopBridge$1 from ASMEventHandler_19`) — FML's ASM event handlers need a PUBLIC named
  listener class; the drainer was an anonymous `new Object(){}`. Fixed (`ServerTickDrainer`).
- McDevBridge `forge-1.7.10`: new `POST /screenclick` (`{list:true}` | `{buttonText}` | `{x,y}` | `{fx,fy}`) —
  1.7.10 menus have no keyboard focus navigation (arrived in 1.16), so `/screenkeys tab` could not reach a
  single button; the button listing makes menu driving screenshot-free.
- 1.7.10 integrated server: it fires no `ServerTickEvent` while the in-game menu is open (which happens by
  itself when the window loses focus), so every `/command` times out. "Open to LAN" via `/screenclick` makes
  the world unpausable (`getPublic()`), after which commands work with the menu up.
- BuildCraft `neoforge.mods.toml`: `bannerFile` added beside `logoFile` — NeoForge 26.2 parked dev runs on a
  loading-warning screen for the deprecated key (`806d894d1`).
- 26.2 dev client: an unfocused window re-pauses the integrated server every frame (`Minecraft.pauseIfInactive`
  + `pauseOnLostFocus`), so `time query gametime` never advanced and robots never moved; the worktree run dir
  carries `pauseOnLostFocus:false` in `options.txt`.
- 1.7.10 `/give … {id:"buildcraft:boardRobotPicker"}` yields a blank robot: the robot item keeps its board under a
  `board` sub-compound (`{board:{id:…},energy:…}`); `/summon Item` needs NUMERIC item ids there; the era's left-click
  path is dead while the auto-reopening in-game menu is up (`/setblock air` stands in for player breaks). All in the
  `reference_bc71_reference_client` memory note.

## Findings

Status legend: **CONFIRMED** (reproduced in-game, or the exact lines on both sides read and compared),
**REFUTED** (claim wrong — usually the deciding file was outside the agent's slice), **OPEN** (not yet
adjudicated), **DESIGN** (real difference, but a deliberate or defensible port decision — decide, don't
"fix" by reflex), **DEFERRED** (belongs to a planned phase), **FIXING** (handed to the implementor).

### Confirmed player-visible bugs (by severity)

| # | sev | slice | claim | status |
|---|---|---|---|---|
| B1 | HIGH | fluid | `AIRobotLoadFluids.load` / `AIRobotUnloadFluids.unload` dereference `station.getFluidInput()/getFluidOutput()` with no null check; a Provide/Accept Fluids gate on a station whose pipe has no wooden-fluid-pipe input makes every fluid robot's station search NPE on the server thread. 7.1.x returned 0. | FIXING (batch 1) |
| B2 | HIGH | statements | `StatementParameterItemStack.onClick` never reads `clickedStack` — it copies its own stack (EMPTY stays EMPTY). Every plain item-stack gate parameter (Filter, Filter Tool, Provide/Accept Items, Provide/Accept Fluids, Goto Station) can never be set or cleared from the gate GUI. GUI call site passes the held stack (`GuiGate`); the robot-picker and map-location subclasses override onClick and work. | FIXING (batch 2) |
| B3 | HIGH | combat | Knight targets the `Monster` class; 7.1.x targeted the `IMob` interface (= modern `Enemy`). Slimes, magma cubes, ghasts, phantoms, shulkers, the dragon are never attacked. | FIXING (batch 2) |
| B4 | MED | item transfer | `AIRobotFetchAndEquipItemStack` extracts `(filter, 1, 64)` — a whole stack — where 7.1.x `takeSingle` took exactly one. `AIRobotPlant` plants one seed and DROPS the held remainder, so a planter fed from a seed chest plants 1 and spills 63 seeds every cycle. | FIXING (batch 2) |
| B5 | MED | statements | `ActionStationProvideItems.canExtractItem` requires ALL filtered Provide Items actions to match; 7.1.x returned true on the first filtered match (ANY). Two differently-filtered Provide actions on one gate supply nothing. | FIXING (batch 1) |
| B6 | MED | fluid | Pump board ignores the gate fluid filter (7.1.x `matchesGateFilter` via `getGateFluidFilter`); a Filter action holding a lava bucket does not stop it pumping water. | FIXING (batch 1) |
| B7 | MED | boards | The gate "Filter" action is not applied to block search (Lumberjack/Miner/Harvester/Farmer/Planter): port `BoardRobotGenericSearchBlock` filter = `isExpectedBlock && !isTaken`; 7.1.x required `matchesGateFilter` (exact block match against the Filter parameters). The javadoc says "when the statements land" — they landed in Ph6, the boards were never revisited. Likewise "Filter Tool" is not merged into the tool-fetch filter (`AIRobotFetchAndEquipItemStack` stores the board predicate alone; 7.1.x `AggregateFilter(ActionRobotFilterTool.getGateFilter(...), filter)`), and the Planter's seed fetch does not AND the Filter action. | FIXING (batch 2) |
| B8 | MED | AI framework | `AIRobotSleep.preempt` polls the CURRENT dock's gate for Wake Up; 7.1.x polled the LINKED (home) station's. Identical while sleeping at home; a robot asleep elsewhere ignores its home's Wake Up. | FIXING (batch 1) |
| B9 | MED | farming | Cactus (and sugar cane) is never "mature": modern `CactusBlock` is not a `BushBlock`, so the port's stacking rule misses it; 7.1.x's `IPlantable`-with-same-block-below rule harvested cactus tops. Also the port marks ANY `FlowerBlock` mature (7.1.x had no flower rule — lone flowers were never harvested), so a Harvester strips flower gardens. | FIXING (batch 1) |
| B10 | MED | items | Robot item tooltip lost the board's description line (7.1.x `boardNBT.addInformation` + charge line; port: charge line only). | FIXING (batch 2) |
| B11 | LOW | movement | `AIRobotGotoStation(takeAsMain)` flies to `station + side×1`; 7.1.x's `AIRobotGoAndLinkToDock` used `×2` (plain goto-station was ×1 in both). | FIXING (batch 1, optional) |
| B12 | LOW | statements | "Goto Station" with an empty map parameter: 7.1.x re-took the robot's current station as MAIN (redock); port returns early and does nothing. | FIXING (batch 2) |
| B13 | LOW | statements | `StatementParameterRobot` ignores non-robot clicks; 7.1.x also accepted a List or wearable stack into Forbid/Force Robot. | OPEN (Lists exist in the port; wearables are Ph9) |
| B14 | LOW | station | Fluid output is the pipe's sided capability on the face opposite the station; the item output was made dead-end-proof (`insertItemsForce`) after the same shape refused unconnected faces. `PipeFlowFluids.getCapability` behaviour at a dead-end dock decides — needs a red-first game test. | FIXING (batch 2: test first) |
| B16 | MED | entity | Home-station loss no longer shuts the robot down (7.1.x re-resolved the linked station every tick and called `shutdown("no docking station")`; port only checks the unresolved-after-load case) and `removeStation` leaves a main-station robot holding a dead `dockingStation`; `setblock air` under a station leaves it registered. Reproduced in-game on 26.2 (V3). | FIXING (batch 2, added) |
| B15 | LOW | render | Energy exhaust is a vanilla white CLOUD; 7.1.x drew a red, size-scaled smoke puff (`EntityRobotEnergyParticle`) and scaled its rate with the particle setting. Cosmetic but the most visible robot effect. | OPEN — follow-up |

### Refuted claims (kept so nobody re-reports them)

- Zone containment at block centre (+0.5) rejecting the max-edge shell: `ZonePlan.contains(Vec3)` floors x/z, so
  centre and corner give the same column; zones are 2-D. REFUTED.
- Block reservations moved into the registry could persist or be per-robot: `RobotRegistry.blockReservations` is a
  plain in-memory `HashSet` per registry (= per dimension), exactly 7.1.x's static per-dimension map. REFUTED.
- Picker/Carrier fetch filter refusing on a gateless station: `getRobotItemFilter` → `ActionRobotFilter.getGateFilter`
  → `PassThroughStackFilter` when no Filter action → tool-filter fallback, also pass-through. REFUTED in code AND
  in-game (26.2 picker fetched from a gateless home station).
- Break "tier gate" preventing the break: `BlockUtil.breakBlockAndGetDropsWithXp` gates only DROPS/XP; the block is
  still destroyed. REFUTED (matches 7.1.x).
- `BoardRobotPicker.onServerStart()` never called: wired in `BCRobotics` via `ServerAboutToStartEvent`. REFUTED.
- Robots/boards missing from the creative tab: `BCCoreCreativeTabs` adds `ROBOT` and `ZONE_PLANNER` (boards ride the
  same tab). REFUTED.

### Design calls / deferred (real differences, not bugs)

- Blank-board robot placement: 7.1.x refused it (verified in-game on 1.7.10: right-clicking a station with a blank
  robot spawns nothing); the port places it and it idles (test `emptyBoardRobotStillPlaces` pins that on purpose,
  "Ph4 revisits" — never revisited). DECIDE.
- Blank robot / blank board stack to 16 in 7.1.x; port `stacksTo(1)` for both (documented as a dropped nicety). DECIDE.
- `AIRobotGotoBlock` caps a search at 50,000 expansions (~50 s) then fails; 7.1.x's async runner searched until done.
  Bounded is the better engineering; note it under divergence 5.
- Arrival snap: 7.1.x wrote the robot's position onto the path-cell centre / straight-move target on arrival; the port
  zeroes motion where it stalled (IRobotAccess has no setPos). Robots rest ≤0.2 blocks off target; docking self-corrects.
  Low; fix if a setPos seam is added.
- Planter refuses to plant in darkness: port uses `cropState.canSurvive` (modern light ≥ 8 rule, same as a player);
  7.1.x planted anywhere. Consistent with modern vanilla — keep.
- Farmer/Knight/Butcher refuse worn-out tools (7.1.x looped equip→unload forever on a broken sword). Port is better — keep.
- Robot as `/kill` target: modern `/kill @e` removes the port robot without drops (7.1.x had no entity /kill). Note only.
- Void kill at `minY − 64` (= −128 overworld, −64 nether). Note only.
- `RobotsActionProvider` offers all 14 actions on any station-bearing pipe; 7.1.x gated Provide/Accept by pipe kind
  and by the presence of an input. Menu clutter, not behaviour. Follow-up candidate.
- Map Location refuses to re-record over a used map (sneak+use air clears); 7.1.x overwrote in place. BC8 behaviour — keep.
- Zone Planner: baked maps get no name (no name field in the BC8 planner); map terrain doesn't refresh while open;
  200-tick transfers vs 120-tick bake. All divergence 6.
- Docking-station registry type `"pipe"` vs `"dockingStationPipe"`, tracking range 64 vs 50, lang wording
  ("Robot Station", "Picker Board"): note only.
- `boards.blacklist` config option not ported. Follow-up if anyone asks.
- Not ported and not on the plan list: `AIRobotDisposeItems` (spill undeliverable cargo — Delivery board, Ph8),
  `BlockScanner` (unused on both sides), `CropHandlerPlantable.forbidBlock` API.

### Numeric parity

Every slice's constants table came back MATCH after the 10 RF = 1 MJ conversion: battery 10,000 MJ, safety 2,000,
recharge target MAX−500 RF-equivalent, 120-tick retry, sleep 1200 ticks at 0.1 RF/t, all per-AI costs
(1/2/3/5/8/10/11/15/16 RF/t), 0.1 blocks/tick, 50 iterations/tick, 96-block cap, 10,000/1,000 scan budgets, 5 finders,
64-radius scanners, 250-block fetch/search, 2.0 attack range, 10/20-tick attack cadence, 40-tick load/unload/equip
waits, 4-slot inventory, 4,000 mB tank, 8 wearables, 2,600 RF-per-heart damage, 5 RF charge-detect, 30/25/5 latch,
board costs 800/3,200/12,800 MJ, station recipe 2 iron + gold chipset. The only mismatches are the ones listed above.

## In-game verification log

Both clients in creative superflat worlds, driven over McDevBridge (`bridge-262.json` / `bridge-1710.json` copies of
the per-client port+token). Rig: wooden item pipe + Robot Station on its UP face + robot placed by right-click.

| # | scenario | 7.1.27 | port 26.2 | verdict |
|---|---|---|---|---|
| V1 | Place a Picker robot on a gateless station | robot with a blank board (see NBT note) → nothing spawns, item kept | picker robot spawns docked at (3.5, 5.0, 0.5), `linkedStation`+`currentStation` = the pipe's UP face, battery 9,998 MJ | placement matches (blank-board refusal is the 7.1.x rule the port skips — DESIGN) |
| V2 | Drop 4 cobblestone 7 blocks from the docked picker | pending (needs a programmed robot) | no reaction for the first ~50 s (the search-fail sleep, 1200 ticks), then fetched during the sleep boundary, returned, docked with the 4 cobblestone aboard; battery 9,889 MJ; gateless station refuses unload so it parks with cargo | fetch loop works gateless (refutes the filter claim) |
| V3 | Remove the docked robot's home station (26.2: station pluggable broken by the player; also `setblock air` under it; 1.7.10: `setblock air` on the host pipe) | robot drops to the ground within a tick (rests at y+0.25 on the grass, motionless for 16 s+ = shut down); a sneak-wrench then recovers it | robot stays parked at the old dock, `mainAI` keeps running the board, `linkedStation` tag gone but `currentStation` still set; `setblock air` even leaves the station registered (ghost) | port does NOT shut down on home-station loss (7.1.x rule §1.4) — B16, fix batch 2 |
| V2b | Drop an item while the picker sleeps docked | reacts at ~47 s (rest of the 60 s sleep), fetches at 51 s, redocks at 53 s | same class: fetched at the sleep boundary (≤60 s) | parity |
| V4 | Low-battery robot (1,000 MJ) placed on an item-pipe station, a wooden kinesis station 3 blocks away fed by a redstone-powered creative engine | not run (7.1.x rule is code-verified: recharge below 20,000 RF at any `providesPower()` station, to MAX−500) | flew to the kinesis station within 2 s, docked, charged 1,387→3,739 MJ in 30 s (~42 RF/t); with the engine unpowered it docked and waited | recharge E2E works |
| V5 | Sneak + wrench a robot (1.7.10: the shut-down one on the ground; 26.2: the docked charging one) | robot vanishes; the robot ITEM is dropped into the world at its feet (nothing lands in the inventory) | robot converts straight into the player's inventory: `robot[custom_data={board:{id:picker},energy:10000000000L}]` — board and charge preserved | behaviour differs only in where the item goes (inventory vs ground) — DESIGN, port is friendlier |

## Fix batches

- **Batch 1 (Opus, this worktree, tests first):** B1, B5, B6, B8, B9, B11. Status: running at the time of writing.
- **Batch 2 (Opus, tests first):** B2, B3, B4, B7, B10, B12, B14. Status: to launch after batch 1 lands.

## Follow-ups

_(what goes to todos.md — written at the end)_
