# Gameplay differences: 1.7.10 vs 1.12.2 / this port

Reference for the port team — what 1.7.10 played like, what 1.12.2 changed, and where this port
stands. Every claim below was read out of source, not from memory or wikis.

**What was compared**

| Era | Branch | Snapshot | Notes |
|---|---|---|---|
| 1.7.10 | `upstream/7.1.x` | **7.1.27** | The final 1.7.10 line; includes robots, blueprints, lists, gate copier |
| 1.12.2 | `upstream/8.0.x-1.12.2` | **8.0.1-pre.2** | The "7.99.x" line players ran. `common/` is the live build; `src_old_license/buildcraft/robotics/` is a dead, never-built 123-file robot port |
| This port | `main` | 2026.2.0 (post-br2, robotics Ph0–Ph6 landed) | Single mod `buildcraftunofficial`, nodes 1.21.1 → 26.2 |

**Why `upstream/7.2.x` isn't in the table** (audited 2026-09-08): it is the **MC 1.8.9** line (never 1.9) — 7.2.0 (2016-03) through 7.2.8 (2017-01), all beta, officially dead ("7.2.x is dead, and is extremely unlikely to receive updates" — mod-buildcraft.com FAQ). Real development stopped mid-2016: the "Neptune" 8.0.x line forked FROM 7.2.x in 2016-03 and repeatedly merged it in (last 2016-11-16), so **everything 7.2.x authored — robot goggles, the drag-drop gate GUI, advanced kinesis power-loss + opt-in pipe explosions, map-location display, robot naming (#3372) — reached 8.0.x-1.12.2 by ancestry** (our goggles item is literally renamed 7.2.x code). Only one fix sits at the 7.2.x tip outside that ancestry — builder-dupe #3316 (`790d05d4c`) — and the port's snapshot builder is structurally immune to it. Zero 7.2.x-only code was found anywhere in this port (139-identifier tripwire, three-way line tests). Two footnotes: `8.0.x-1.12.2`'s dead `src_old_license/buildcraft/robotics/` is textually **7.2.x-lineage** (a 2016-05 snapshot of the 1.8 port; 31/123 files byte-identical) and was used as a mechanical diff-aid for the robotics port; and the 7.1.x-side gap that DOES exist — 51 commits of 7.1.20→7.1.27 that no 8.0.x branch took — is tracked in [todo-details.md](todo-details.md#upstream-71x-tail-sweep). Upstream's `8.0.x-1.16.5/-1.18.2/-1.20.1` branches are empty bookmarks at a 2023 1.12.2 perf-merge — no modern-MC BuildCraft exists upstream.

**Units:** BC 7.1 (1.7.10) speaks **RF**, BC8 (1.12.2+) speaks **MJ**, and 10 RF = 1 MJ — divide
7.1.x numbers by 10 to compare. The port keeps MJ.

---

## 1. Robots in 1.7.10 — the system being ported

### 1.1 The gameplay loop

1. **Craft a Robot** — `PPP / PRP / C C`: 5 iron + **Redstone Crystal** (silicon item) +
   **Diamond Chipset ×2**. The robot is blank and stackable (16).
2. **Craft a blank Redstone Board** — paper + redstone dust.
3. **Program the board** at the **Programming Table** (silicon, laser-fed): a 6×4 grid of every
   registered board sorted by cost; the table drains laser power until the board's price is paid,
   then outputs the programmed chip. Cost tiers: **8,000 / 32,000 / 128,000 / 512,000 RF**
   (800 / 3,200 / 12,800 / 51,200 MJ).
4. **Insert the board into the robot** at the **Integration Table** — 50,000 RF, preserves charge,
   consumes the board. Re-programming = wrench the robot back to an item, integrate again. No
   hot-swap on a deployed robot.
5. **Craft a Docking Station** — 3 iron + Gold Chipset — and attach it to any pipe face. It is a
   **pipe pluggable**, not a block.
6. **Right-click the station** with the robot item. The robot spawns docked, and that station
   becomes its permanent home ("linked").
7. The robot flies at 0.1 blocks/tick doing its board's job, **recharges by docking at any station
   whose host pipe is a kinesis pipe** (station acts as an RF receiver into the robot's battery),
   sleeps when idle, and **shuts down inert at 0 energy** rather than dying.

### 1.2 Board catalog (all 17 registered in 7.1.27)

Costs are Programming Table RF. "Zone" = constrained by the station's Work/Load-Unload area action
when present; without a zone, zone-capable boards roam all loaded chunks.

| Board | Cost | Tier | What it does | Zone? |
|---|---|---|---|---|
| Empty | — | — | Registered so board-less robots resolve; robot just sleeps | — |
| **Picker** | 8,000 | green | Fetches dropped items within 250 blocks to its dock | filter/zone |
| **Carrier** | 8,000 | green | Shuttles filtered items between a Provide station and an Accept station | load/unload |
| **Tank** (FluidCarrier) | 8,000 | green | Same loop for fluids; carries 4 buckets internally | load/unload |
| **Delivery** | 128,000 | green | The Requester-network courier: fulfils open StackRequests | load/unload |
| **Lumberjack** | 32,000 | blue | Equips an axe, breaks `wood` blocks, swaps axes as they break | yes |
| **Harvester** | 32,000 | blue | Right-click-harvests `harvestable` blocks (mature crops) | yes |
| **Miner** | 32,000 | blue | Equips a pickaxe, mines `ore@hardness ≤ pick level (max 3)` where it stands — no shaft | yes |
| **Planter** | 32,000 | blue | Plants seeds via the CropManager handler | yes |
| **Farmer** | 32,000 | blue | Hoes `dirt` with air above | yes |
| **Leaf Cutter** | 32,000 | blue | Shears `leaves` | yes |
| **Butcher** | 32,000 | blue | Sword-hunts passive animals within 250 blocks | yes |
| **Shovelman** | 32,000 | blue | Shovels dirt/sand-family blocks | yes |
| **Pump** | 32,000 | blue | Flies to fluid source blocks in the zone, deletes them, hauls 4 buckets at a time | yes |
| **Knight** | 128,000 | red | Sword-hunts hostile mobs in the zone | yes |
| **Bomber** | 128,000 | red | Loads TNT at its station, drops lit TNT ≥20 blocks down in the zone | yes |
| **Stripes** | 128,000 | red | Flies to a random air cell and *uses* whatever item it holds (stripes protocol) | yes |
| **Builder** | 512,000 | yellow | Serves **Construction Marks** within 192 blocks: fetches materials from stations, launches building items (requires Builders module) | filters markers |

Boards that use tools fetch the right tool class (axe/pick/hoe/shears/sword/shovel) from stations
via the **Filter Tool** gate action; all targeting goes through the world **reservation registry**
so two robots never claim the same block, item, or request. The lang file still carries
**Crafter** and **Breaker** board names — those were cut before 7.1.27 and are *not* registered;
they're 6.x leftovers, not porting targets.

### 1.3 Zones — Zone Planner + Map Location

- **Zone Planner** (robotics block, recipe: iron/redstone/map + gold & diamond gears): renders a
  **flat top-down 2048×2048-block map** of the surrounding area on a background thread, 16
  colour-coded named areas per planner, left-drag paints, +/− toggles add/erase.
- Writing a blank **Map Location** in the planner's input slot bakes one zone into the item
  (120-tick craft). Map Locations have four kinds: point, area (box from landmarks), path, zone.
- Zones are **2D columns** (Y is ignored; random targets pick any Y).
- Robots never store zones: the **"Work in Area"** / **"Load-Unload in Area"** gate actions on the
  robot's *home* station carry a Map Location parameter. No zone action = work the whole loaded
  world.

### 1.4 Stations & docking

- One robot **occupies** a station; its home station is "linked", temporary stops are "reserved".
- **Item/fluid plumbing** at a station: robots *unload into* any item/fluid pipe; robots *load
  from* an adjacent inventory only through a **wooden pipe's** extraction face (this port made
  that rule stricter — see §2.3).
- **Charging** = any station on a kinesis pipe; robots pick the nearest allowed station (zone
  filters, "Forbid/Force Robot" gate actions), recharging to full − 500 RF.
- Breaking the station/pipe a robot is docked to undocks it; losing the **home** station puts the
  robot into permanent shutdown — it lies on the ground until sneak-wrenched.
- Sneak-wrench a robot to recover it as an item (board + charge + cargo preserved).

### 1.5 The Requester network

- **Requester** block (iron/piston/chest/gears/redstone; **no power needed**): 20 ghost request
  slots + 20 delivery slots.
- A docking station adjacent to a Requester publishes its requests; a station with no Requester
  instead publishes its own **"Request Items"** gate-action parameters (up to 127).
- A **Delivery** robot reserves an open request (registry-enforced, so no double-delivery), loads
  the goods at a station whose **"Provide Items"** filter allows them, delivers to the requesting
  station, and dumps leftovers per **"Accept Items"** / dispose behaviour.

### 1.6 Gate integration (22 robot/statement hooks)

Triggers: **Sleep**, **Robot In Station**, **Station Linked**, **Station Reserved**.
Actions: **Goto Station**, **Work in Area**, **Load/Unload in Area**, **Wake Up**, **Filter**
(the master item/fluid/block filter), **Filter Tool**, **Request Items**, **Provide Items**,
**Accept Items** ("Drop Items In Pipe"), **Provide Fluids**, **Accept Fluids**,
**Request Needed Items**, **Forbid/Force Robot**. Parameter widgets: robot-picker (cycles boards),
map-location picker, exact-stack picker.

### 1.7 Numbers cheat-sheet (7.1.x RF → MJ in parentheses)

| Quantity | Value |
|---|---|
| Battery | 100,000 RF (10,000 MJ); safety threshold 20,000 (2,000); shutdown at 0 |
| Recharge target / retry | MAX − 500 RF; re-search every 120 ticks |
| Max battery input | 100 RF/t (10 MJ/t) while docked |
| Move speed | 0.1 blocks/tick |
| Sleep cost / period | 0.1 RF/t; 60 s sleep cycle |
| Activity costs | idle 1, moving 3, searching 2, item fetch 15, load 8, unload 10, use-tool 8, pump 5 RF/t; break ≈11, attack 16 RF/t |
| Pathfinding | 3D A*, 96-block path cap, unreachable targets blacklisted 1,200 ticks |
| Chunk loading | **none** — robots stop when their chunk unloads; registry links survive |
| Robot inventory | 4 stacks + 4 buckets of fluid; wearables up to 8 (armor reduces damage→energy) |
| Damage model | no health; player damage = 2,600 RF per point (armor-scaled); void < y −128 kills; mobs can't hurt it |
| Reservations | world-saved `robotRegistry` (stations, robot IDs, block/request claims) |

### 1.8 What 1.7.10 shipped *unfinished* (don't feel bound to it)

- **Crafter** and **Breaker** boards: lang entries only, cut from the registry.
- **Recipe Packager + Package + Stamping Table + Charging Table**: registered (the laser-table
  block even has the metas) but **no crafting recipes** — an unshipped "robots craft for you"
  experiment. Its orphan is visible even in the port: the `station.allow_craft` lang key with no
  implementing class.
- The **"Request Needed Items"** station action exists but its activation is an empty stub in
  7.1.x — machine-driven requests never worked there either.

---

## 2. Robots in 1.12.2 and in this port

### 2.1 What 1.12.2 actually had

The live 8.0.x-1.12.2 build contains **no robots at all**: the robotics module is the **Zone
Planner** + Map Location with its own 3D map viewport — and both ship **without crafting recipes**
(tooltips literally say *"Robots aren't coming to 1.12.2, so this is useless :("*). The
Programming/Charging tables are dev-build-only with the same tooltip. `src_old_license/buildcraft/
robotics/` (123 files — EntityRobot, boards, AIs, requester) was mechanically migrated to 1.12
APIs but excluded from the build and never finished; useful as a coordinate-system diff for the
port, not as working code.

### 2.2 What this port has today (Ph0–Ph6 complete)

| System | Status |
|---|---|
| Zone Planner + Map Location | **In**, improved: live 3D map viewport (BC8-style camera), both with survival recipes (since 2026.2.0-br1) |
| Docking Station | **In** — pipe pluggable, pedestal model, available/reserved/linked states, MJ charge handoff, recipe (3 iron + gold chipset — identical in 1.7.10) |
| EntityRobot + ItemRobot | **In** — bare `Entity` (not LivingEntity), damage-drains-battery, death drops robot+board+cargo, charge tooltip, both renderer generations |
| Boards | **11 of 17**: Empty, Picker, Carrier, Tank/FluidCarrier (Ph4) + Lumberjack, Harvester, Miner, Planter, Farmer, Pump, Butcher, Knight (Ph5). Missing: Delivery, Bomber, Stripes, Shovelman, Leaf Cutter, Builder |
| AI framework | **In** — 34 AIs, NBT round-trips, reservations, recharge/sleep/shutdown ladder |
| Gate statements | **In** (Ph6) — all robot/station triggers & actions + both parameter widgets; stations without gates refuse robot transfers (7.1.x semantics restored) |
| Programming Table + board recipes | **Ph7 — next up** (boards/robots are creative-tab-only until then) |
| Requester network + Delivery robot | **Ph8** |
| Builder robot + advanced boards + wearables | **Ph9** (Builder rewritten against the snapshot system — the 1.7.10 Construction Mark no longer exists, see §3) |

### 2.3 Deliberate divergences from 7.1.x already decided

- **Robots are plain entities**, not `LivingEntity` — nothing in the gameplay relied on vanilla
  living plumbing (they had no health, no collisions, no AI goals anyway). Damage-as-energy and
  death-drops are preserved.
- **Battery pinned at 10,000 MJ** — the same capacity as 7.1.x's 100,000 RF, now expressed natively.
- **Station access policy is stricter than 7.1.x on the supply side**: `getItemInput` requires a
  wooden pipe's wrench-set extraction face, which kills 7.1.x's "robot drains a chest through plain
  cobblestone" exploit (a free unpowered hopper). Gate-side defaults still follow 7.1.x: no gate
  on the station ⇒ no item/fluid transfers.
- **Board icons**: per-board icons via the modern item-model system on 1.21.10+; the 1.21.1 node
  (no item-model system) shows a single chip texture — line-inherent, not a porting gap.
- **Pathfinding runs synchronously** (50 iterations/server tick) instead of 7.1.x's async job
  runner; same 96-block cap.
- **Zone Planner UI is BC8's** single-zone 3D viewport, not 7.1.x's 16-area 2D editor. One zone
  per Map Location in both.

---

## 3. 1.7.10 content that does not exist in 1.12.2

| 1.7.10 item | What it did | 1.12.2 fate / port plan |
|---|---|---|
| **Robot** | The mob-like worker | Removed; **ported (Ph3)**, recipe pending Ph7 (the Redstone-Crystal question) |
| **Redstone Board** (+16 programmed chips) | Robot personality | Removed; **ported (Ph4/5)**, programming pending Ph7 |
| **Docking Station** (pluggable item) | Where robots live/charge | Removed; **ported (Ph2)** |
| **Requester** block | Power-free item ordering | Removed; **Ph8** |
| **Programming Table** | Boards from laser power | Dev-only husk; **Ph7** |
| **Charging Table** | Unshipped experiment | Dev-only in 1.12.2, unobtainable in 7.1.27 too — not planned |
| **Construction Mark** | Build sites for the Builder robot | Removed outright; Ph9's builder targets snapshots instead |
| **Redstone Crystal** | Robot crafting ingredient | Unobtainable lang-remnant; needs a new recipe/source decision (Ph7 Decision 8) |
| **Emerald Transport Pipe** | Filtered extraction (whitelist/blacklist/round-robin) | Replaced by **Wooden Diamond** item pipe |
| **Emerald Fluid Pipe** | Filtered fluid extraction | Replaced by **Wooden Diamond** fluid pipe (1.12.2's didn't actually filter; fixed in this port) |
| **Emerald Kinesis Pipe** | Mid-tier power pipe | Simply gone — no replacement |
| **Emerald / Pulsating / Comp Chipsets** | Emerald gates, Pulsar & Fader expansion crafting | Gone; 1.12.2 keeps Red/Iron/Gold/Quartz/Diamond only |
| **Gate expansions** (Pulsar/autarchic, Timer, Redstone Fader, Light Sensor, Note) | Integration-table gate upgrades — autarchic gates self-powered wooden pipes | Replaced by standalone pluggables (Pulsar, Timer, Light Sensor). **Redstone Fader and Note have no 1.12.2 equivalent** |
| **Recipe Packager / Package** | Unshipped crafting-request experiment | Gone (recipe-less even in 7.1.27) |
| **Crafter / Breaker robot boards** | Dead lang from 6.x | Never shipped — ignore |
| **Stamping Table** | Unshipped experiment | Gone |

Note the **pipe-wire colours**: 1.7.10 gates have **4** wires (red/blue/green/yellow); 1.12.2 and
this port have **16**, which multiplies every wire-signal trigger/action.

---

## 4. Content 1.12.2 (and this port) added that 1.7.10 never had

- **Distiller + Heat Exchanger** and the multi-stage fuel chain (see §5) — 1.7.10's single Refinery
  block is not registered in 1.12.2 at all.
- **Marker Connector** (explicitly links Land Marks), **Replacer** (blueprint block-swap utility),
  **Filler Planner** and **Single Block Schematic** (registered but recipe-less), **Water
  Gellifier + Gelled Water** (temporary water solids).
- **RF Engine** and **MJ Dynamo** (config-gated RF⇄MJ bridges).
- **FE/RF pipe family** — this port only; a full second power-pipe set mirroring the MJ kinesis set.
- Port-era quality-of-life: waterloggable + recolourable pipes, bare-hand pipe breaks drop
  everything, facade solid⇄hollow toggle, Stirling Engine pickaxe-style crafting, AE2 energy
  interop, guide book.

---

## 5. Same items, different gameplay

### Gates — completely rebuilt

- **1.7.10**: material tiers **Iron (2 slots) → Gold (4) → Diamond (8)** plus **Quartz (2 slots,
  1+1 params)** and **Emerald (4 slots, 3+3 params)**; each in AND/OR form, crafted at the
  assembly table from a chipset + pipe wire. Expansions (autarchic pulsing, timer, fader…) bolted
  on at the Integration Table.
- **1.12.2 / port**: **Basic (clay brick, 1 slot, crafting-table recipe) → Iron (2) → Nether Brick
  (4) → Gold (8)**, AND/OR converted in a crafting grid, and **modifiers** (lapis +1 param,
  quartz +1+1, diamond +3+3, at the cost of slots) instead of quartz/emerald gates. Autarchic is
  gone (the Pipe Pulsar plug and Power Adapter plug cover adjacent niches); **Gate Copier** exists
  in both eras (added late in 7.1.x).

### Pipes

- **Item pipes**: 1.7.10 wood/cobble/stone/sandstone/iron/quartz/gold/**emerald**/diamond/daizuli/
  emzuli/lazuli/obsidian/clay/void/stripes/structure → 1.12.2 identical **minus emerald, plus
  Wooden Diamond**. This port adds the **FE/RF mirror family**.
- **Kinesis**: 1.7.10 caps ~80 RF/t default, **iron** limiter 20–1,280 RF/t; 1.12.2 caps 4–256 MJ/t
  with **iron and diamond** limiters; this port keeps both plus FE limiters. **Both eras are
  lossless by default** — 1.7.10's perdition was already opt-in ("like BC pre-3.7"), 1.12.2's too.
- **Fluid pipes**: same material set both eras; sealant crafting both (1.12.2 makes it reversible
  and adds residue→8× sealant).
- Behaviour quirks that changed: stone pipes now demand true stone (recrafting), 1.12.2 added
  per-face 9-slot GUIs to diamond/wooden-diamond, emzuli presets survive unchanged.

### Engines & fuel

| Engine | 1.7.10 | 1.12.2 / port |
|---|---|---|
| Redstone | 1 RF/t (0.1 MJ) | 0.05 MJ/t (slight nerf), 1 MJ internal |
| Stirling | furnace fuels, throttled, ≤10 RF/t (1 MJ), can explode | same shape: ⅓–1 MJ/t PID throttle, explosion config |
| Combustion | liquid fuel + water coolant, config output: oil 30 RF/t, fuel 60 RF/t (3 / 6 MJ) | same plus **heat stages Cool/Hot/Searing** and the whole 9-fluid fuel chain below |

Fuel chain: 1.7.10 is **Oil → Refinery → Fuel** (two fluids, period). 1.12.2 replaces the refinery
block with **Distiller + Heat Exchanger** and splits oil into Distilled/Heavy/Dense Oil, Residue
(→ pipe sealant), Mixed fuels, and **Gaseous/Light/Dense Fuel** (8/6/4 MJ per cycle; dirty vs
clean). This port inherits all of it and plans heat-tiered fuel + Nether oil.

### Builders

- **Architect Table / Blueprint / Template / Electronic Library / Builder / Filler / Quarry** all
  exist in both eras — blueprints and templates are the same two snapshot kinds.
- **Caveat on the 1.12.2 branch**: the 8.0.1-pre.2 snapshot ships **Builder, Filler and Electronic
  Library without crafting recipes** (tooltip: "Not usable in survival"). This port restored real
  recipes for all of them — the port is more survival-complete than the 1.12.2 branch we forked.
- **Filler patterns**: 1.7.10 has Fill, Clear, Box, Flatten, Horizon, Frame, Pyramid, Stairs,
  Cylinder (+ hollow/centre params). 1.12.2/this port drop Cylinder, **add the whole geometry
  family** (Triangle/Square/Pentagon/Hexagon/Octagon/Arc/Semi-Circle/Circle/Sphere/Hemisphere/
  Quarter/Eighth + rotation/axis), and keep **Flatten/Horizon commented out "broken ATM"**.
- **Quarry**: same frame-and-strip-mine both eras; 1.12.2 adds auto chunk-loading (config),
  one-time-use option, and stops requesting power when finished (port).

### Everything else that carried over unchanged in spirit

Tanks (16 buckets, stack in displays), Flood Gate, Chute, Filtered Buffer, Mining Well (+ tube
shaft), Auto Workbench, Advanced Crafting Table, Assembly Table + Laser (chipsets 10/20/40/60/80
MJ both eras), Land Marks + Path Marks, Wrench, Gears (still craft-chain items, still used in a
handful of recipes), Paintbrush, **List** item (already in 7.1.x — not a BC8 invention), Map
Location, Debugger. **Goggles never shipped in any era** (dev-only in 1.12.2/port, absent from
7.1.x entirely).

Oil worldgen is recognisably the same in all three: oil-desert/oil-ocean biomes, small/medium/
large lakes, tall spouts, and a regenerating **Oil Spring under large lakes** (25 % in 7.1.x,
config-gated). This port adds Eroded Badlands as a rich-oil biome and reworked generation on 1.21.1.

---

## 6. Practical takeaways for the robot port

1. **The 1.7.10 robot economy must be re-priced, not copied**: 7.1.x RF costs ÷10 land exactly on
   the port's MJ pins (8k→800 MJ boards, 50k→5,000 MJ integration, 100k RF battery→10,000 MJ).
2. **Three item recipes have no 1.12.2 reference**: Robot (needs the Redstone-Crystal decision),
   Redstone Board, and Programming Table — Ph7 owns all three.
3. **The Construction Mark is gone**, so the Builder board (Ph9) is a rewrite against
   `SnapshotBuilder`/`BlueprintBuilder`, not a port.
4. **Requester (Ph8)** is the only remaining *from-scratch server system*; the station-side hooks
   (reservation registry, gate actions) already exist from Ph0/Ph6.
5. **The unfinished 1.7.10 experiments** (Crafter board, packages, `allow_craft`) are documented
   here so nobody "restores" them by accident — though the orphaned `station.allow_craft` lang key
   suggests deciding its fate during Ph8.

## Sources

- `upstream/7.1.x` @ 7.1.27: `common/buildcraft/robotics/`, `BuildCraftRobotics.java`,
  `buildcraft_resources/assets/buildcraft/lang/en_US.lang`, `common/buildcraft/core/lib/robots/`
- `upstream/8.0.x-1.12.2` @ 8.0.1-pre.2: `common/buildcraft/**` (live), `src_old_license/buildcraft/robotics/` (dead)
- `main`: `src/main/java/buildcraft/**`, `docs/robotics-resurrection.md`, `changelog.md`
- Both old trees can be re-checked out read-only with
  `git worktree add --detach .refs/7.1.x upstream/7.1.x` (same for `8.0.x-1.12.2`).
