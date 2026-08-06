# Robotics Ph4 — First boards + recharge AI: implementation plan

Working plan for [robotics-resurrection.md](robotics-resurrection.md) Ph4 — **MVP completion**. This is
the phase that turns the Ph3 "empty board robot" into real robots: a placeable, dockable,
self-recharging robot that picks up / carries items.

The Ph3 design record ([robotics-ph3-design.md](robotics-ph3-design.md)) is the required prerequisite
read — its post-implementation amendments 5–9 carry the Ph4 pre-notes this plan builds on. Cited
line numbers below refer to `upstream/7.1.x:common/buildcraft/robotics/` unless marked.

**MVP gate (verbatim from robotics-resurrection.md):** *a placeable, dockable, self-recharging robot
that picks up / carries items.* Concretely: Picker and Carrier boards + the ~12 core AIs +
`ItemRedstoneBoard`, with the full green test suite as the implementation gate (not just the new
tests).

---

## Why this is the phase that makes "the other robots"

BuildCraft has ONE robot entity (`EntityRobot`). The "robot types" are **boards** — each
`RedstoneBoardRobot` is both the robot's skin (via `RedstoneBoardRobotNBT.getRobotTexture()`) and the
behaviour that `AIRobotMain` delegates to every tick (`EntityRobot.java:701` runs `mainAI.cycle()`).
Right now only `BoardRobotEmpty` exists, and its `update()` is deliberately empty (Ph3, no AI tree
yet). Ph4 builds the AI tree + the first two real boards, so `EntityRobot` finally does something.

---

## The four seams that decide the shape of this phase

Ph4 is mostly mechanical AI-porting, but four seams determine how much cross-phase coupling leaks in.
All four are decided here so the AI code compiles and behaves correctly *before* Ph6 lands the
statement engine.

### D1 — Station access policy: the statement-seam (THE design decision)

7.1.x's AI/board layer is entangled with the statement engine (Ph6). Every load/unload/search/fetch
AI calls a gate-action helper, and in Ph4 those gates do not exist yet. The seam: **put the policy on
`DockingStation` with permissive defaults**, so the AI logic is identical to 7.1.x's shape and Ph6
fills the real gate-driven behaviour in one place.

Add four defaulted methods to abstract `buildcraft.api.robots.DockingStation` (all return the
permissive answer until overridden):

| 7.1.x call site | New `DockingStation` method | Ph4 default | Ph6 override |
|---|---|---|---|
| `ActionStationProvideItems.canExtractItem(station, stack)` | `canRobotExtractItem(ItemStack)` | `true` | read "provide items" gate action |
| `ActionRobotFilter.canInteractWithItem(station, filter, ActionStationAcceptItems.class)` | `canRobotAcceptItem(ItemStack)` | `true` | read "accept items" gate action |
| `ActionStationForbidRobot.isForbidden(station, robot)` | `isRobotForbidden(IRobotAccess)` | `false` | read "forbid robot" gate action |
| `ActionRobotFilter.getGateFilter(station)` | `getRobotItemFilter()` → `IStackFilter` | match-all | build from gate actions |

`DockingStationPipe` inherits the permissive defaults unchanged (its own javadoc already says
`getActiveActions` is Ph6). The AI call sites in `AIRobotLoad`/`AIRobotUnload`/`AIRobotSearchStation`/
`BoardRobotPicker`/`BoardRobotCarrier` swap the statement-helper call for the station method — same
shape, no statement dependency. `AIRobotSleep.preempt`'s `ActionRobotWakeUp` check becomes a no-op in
Ph4 (the 60s sleep timer still wakes it; the wake-up *statement* is Ph6).

This is chosen over porting the four `Action*` helper classes as stubs because those extend statement
base classes that do not exist on main — porting them drags the statements API forward whole.

### D2 — Item transfer: main's `IItemTransactor`, not 7.1.x's `Transactor`

7.1.x load/unload/fetch use `Transactor.getTransactorFor(robot)`, `ITransactor.add/extract`,
`InventoryIterator`, `IInvSlot`. Main has no transactor lib — it has a **modern, already-present
equivalent**: `buildcraft.api.inventory.IItemTransactor` (`insert(stack, allOrNone, simulate)` /
`extract(filter, min, max, simulate)`), with `InventoryWrapper` wrapping a `Container` and
`ItemHandlerSimple` extending `AbstractInvItemTransactor`. The robot's 4-slot inventory IS an
`ItemHandlerSimple`.

- Robot side: expose the robot inventory as an `IItemTransactor` on `IRobotAccess` — add
  `IItemTransactor getTransactor()` (delegates to the robot's `ItemHandlerSimple`). This is a small
  `api/robots → api/inventory` (both api — no lib leak) addition; the existing
  `getInventoryStack`/`setInventoryStack` pair stays for GUI/other use.
- Station supply side: `station.getItemInput()` returns a `Container` → wrap `new InventoryWrapper(container)`.
- The `doLoad`/`doTake` dry-run flag maps to `IItemTransactor`'s `simulate`; quantity-limited load uses
  `insert(copy, allOrNone=false, simulate)` then commits.

### D3 — `AIRobotGotoBlock`: adapted to main's synchronous pathfinding

7.1.x uses a threaded `IterableAlgorithmRunner` (50 iters/tick) around `PathFinding`. Main's
`PathFinding` (ported in Ph0) is synchronous `IIterableAlgorithm`: construct
`new PathFinding(SoftBlockAccess.of(level), start, end, maxDistanceToEnd, maxTotalDistance)`, then
spread the work: each `update()` calls `iterate(50)` until `isDone()`, then `getResult()` returns the
`LinkedList<BlockPos>` path. This preserves 7.1.x's spread-across-ticks behaviour with no runner class.
`SoftBlockAccess.of(Level)` already exists (`path/SoftBlockAccess.java:46`). Follow-the-path logic
(`setNextInPath`, soft-block validation, snap-to-final) ports 1:1. **Must short-circuit "already
there"** — Ph0's tests pinned that `PathFinding` start==end yields the degenerate 3-cell out-and-back,
so GotoBlock (and GotoStation's final approach) return success immediately when `start == end`.

### D4 — Picker `targettedItems`: `Set<Integer>` → `Set<UUID>`

7.1.x keys the "this item is being fetched by a picker" set on `int` entity ids — ambiguous across
dimensions (ids are recycled). Modern `ItemEntity.getUUID()` is globally unique, so the static
`Set<UUID>` is correct even though the set is global. `AIRobotFetchItem` scans
`level.getEntitiesOfClass(ItemEntity.class, ...)` (replaces the raw `loadedEntityList` iteration),
reads `getUUID()`, and the goto uses the item's block.

---

## Scope

### Lands fully

**AIs (registered in `RobotManager` by name; each `canLoadFromNBT()=true` + NBT round-trip):**

- `AIRobotMain` — the controller. `preempt` ladder: shutdown → recharge → overridingAI; `update`
  delegates to the board. Energy refs map: `getEnergy()`→`getPower()`,
  `SHUTDOWN_ENERGY/SAFETY_ENERGY`→`IRobotAccess.SHUTDOWN_POWER/SAFETY_POWER`,
  `getDockingStation().providesPower()` unchanged.
- `AIRobotRecharge` — release resources, zero motion, `SearchAndGotoStation(providesPower)`, terminate
  at `getPower() >= MAX_POWER - MAX_POWER/200` (Decision 3's headroom, not a hard 500).
- `AIRobotSleep` — 60s timer + statement-wake (stubbed no-op); `getPowerCost` 0.1 RF/tick shape.
- `AIRobotShutdown` — undock, fall-and-park (`getCollidingBoundingBoxes`→`level.getCollisions` /
  `isUnobstructed`); Ph3's `EntityRobot.shutdown(String)`/`convertToItems` stub is the entry.
- `AIRobotGoto` (abstract base) + `AIRobotStraightMoveTo` — movement via
  `position()`/`getDeltaMovement()/setDeltaMovement()`, `aimItemAt(BlockPos)`.
- `AIRobotGotoBlock` — see D3.
- `AIRobotGotoStation` — reserve + `GotoBlock` to face-adjacent, then `StraightMoveTo` the dock point,
  `dock()`. Serializes station pos via the int[3] pos + side-byte helper (Ph3 Decision 5 shape, NOT
  `BlockIndex`), bounds-checked side byte.
- `AIRobotGotoSleep` — release resources, `GotoStation(linked)`, then `Sleep`. **This also becomes
  `BoardRobotEmpty`'s real `update()`** — the parked-home behaviour its javadoc always promised.
- `AIRobotSearchStation` — nearest matching station (via D1 `isRobotForbidden`), `IZone`-gated (null
  in Ph4 → no zone filter).
- `AIRobotSearchAndGotoStation` — compose search + goto.
- `AIRobotGotoStationToLoad`/`ToUnload`, `AIRobotGotoStationAndLoad`/`AndUnload` — the load/unload
  orchestration pairs.
- `AIRobotLoad` / `AIRobotUnload` — via D2 transactor; quantity/filter logic ports; station policy via
  D1. `AIRobotLoad.ANY_QUANTITY = -1` preserved. `AIRobotUnload` outputs through `station.getItemOutput()`
  (`IInjectable`) with `getItemOutputSide()`.
- `AIRobotFetchItem` — via D4; `IStackFilter` from D1; inject via transactor with simulate.

**Boards:**
- `BoardRobotPicker` + NBT — fetch items within range, unload at a station, sleep when nothing.
- `BoardRobotCarrier` + NBT — load from a supply station (any quantity), unload elsewhere.
- `BoardRobotEmpty.update()` → `startDelegateAI(new AIRobotGotoSleep(robot))` (the parked behaviour).

**`ItemRedstoneBoard`** — see D5 below.

**Supply-side `getItemInput` on `DockingStationPipe`** — see D6 below.

**Registration** — `BCRobotics.init`: `RobotManager.registerAIRobot(...)` for every AI (with the legacy
class-name arg where 7.1.x had one), and `RedstoneBoardRegistry.instance.registerBoardType(NBT, cost)`
for picker/carrier. Board costs: 7.1.x's per-board `microJoules`, converted at 10 RF/MJ — pull the
exact picker/carrier values from 7.1.x `ImplRedstoneBoardRegistry` and pin them with "chosen, not
derived" comments.

### Stubbed with API kept (→ later)

- **`getZoneToWork()`/`getZoneToLoadUnload()` stay null** (Ph6) — AI zone filters are pass-through.
- **Wearable acceptance / skull render / melee** (Ph9) — untouched.
- **Requester / Delivery** (Ph8) — untouched; the supply-side input here is the wooden-pipe discovery,
  NOT the request network.

### Waits entirely

- Statements/gates/triggers (Ph6) — only the D1 policy seam is pulled forward.
- **All board/robot crafting recipes** — the programming table + `RobotIntegrationRecipe` (Ph7) is the
  final, all-or-nothing obtainability. Ph4 ships none (see D5); boards are creative-tab-only.
- The `AIRobot.writeToNbt` unregistered-name NPE fix is **Ph4 work** (amendment 5 pre-note).

---

## ItemRedstoneBoard (D5)

Follows `ItemRobot`'s `DataComponents.CUSTOM_DATA` blob pattern. Reads board via
`NbtApiUtil.getString(getCompound(custom, ...), "id")` → `RedstoneBoardRegistry.getRedstoneBoard(id)`
(fallback empty board). `getBoardNBT(stack)` / `createStack(boardNBT)` helpers; `getItemModelLocation()`
returns the board id so each board gets its own model/texture. Tooltip from `RedstoneBoardNBT.addInformation`.
`stacksTo(1)` always (drop 7.1.x's 16-when-empty nicety — modern stack limits are static).

**Obtainability — NO crafting recipe in Ph4 (decision, 2026-08-02).** The board→robot mechanism is the
programming table + `RobotIntegrationRecipe` (Ph7), and that is the final, all-or-nothing state. We do
not ship a temporary board-craft or robot+board-merge recipe now and tear it out later. So Ph4 ships no
board or robot recipe at all: boards and boards-on-robots are **creative-tab only**, exactly as
`ItemRobot` shipped recipe-less in Ph3 (the tab already grows per-registered-board entries — Ph3
Decision 8). The MVP is still fully demonstrable: game tests construct boards programmatically, and the
creative tab yields a robot-with-board directly. Survival obtainability lands whole with Ph7.

**Redstone Crystal decision (deferred from Ph3) moves entirely to Ph7** — with no Ph4 recipe to price,
it is decided with the programming table. Record that in `robotics-resurrection.md` where Ph3 flagged it.

---

## Supply-side input on `DockingStationPipe` (D6)

`DockingStationPipe.getItemInput()` currently returns null (inherited from `DockingStation`), so the
Carrier cannot LOAD from a chest. This is the one genuine scope addition beyond the todos.md Ph4
wording — and it is needed: 7.1.x's `getItemInput()` returned the inventory the wooden pipe faces
(`PipeItemsWood`). Port the discovery: when the station's pipe is an item pipe, find the block
adjacent to the pipe on its facing, and if it is a `Container` return it (wrapped) with
`getItemInputSide()` = the pipe's facing. **Decision (2026-08-02): promoted into Ph4 as a firm
requirement.** The Carrier loads from a supply station; without it the MVP's "carries items" is
unfulfilled. Pull only this — the requester-facing half (`IRequestProvider`) stays Ph8.

---

## Test plan (written FIRST, red, against skeletons)

**Pure JUnit** (extends `VanillaSetupBaseTester` where an ItemStack is built):
- `AIRobotMain` preempt ladder against a mock `IRobotAccess` + real `MjBattery` (shutdown → recharge →
  override order; 0-cost Main/Recharge; recharge cooldown on failure).
- `AIRobotRecharge`/`AIRobotSleep` thresholds (complete at `MAX_POWER - MAX_POWER/200`; sleep 60s + power-cost shape).
- Per-leaf cost table + relative ordering (FetchItem 15 > Unload 10 > Load 8 > Goto 3 > Recharge/Main 0 —
  MJ constants are decisions, pin with "chosen, not derived").
- `AIRobotGotoBlock` "already there" short-circuit (no degenerate out-and-back when start==end).
- Board NBT round-trip: picker/carrier blob → create → write → read.

**GameTests** (each: Java method + `BuildCraftGameTests` registration + manifest JSON; count must
increment; gate on observed state via `EntityArenaUtil.tickUntil/tickUntilThen`, never fixed ticks):
- Registry-completeness sweep: every registered board instantiable + self-resolves through the registry.
- Board blob `CUSTOM_DATA` round-trip on `ItemRedstoneBoard` (no recipe — creative-tab/merge is Ph7).
- **Picker end-to-end**: item in range → robot picks it up → unloads at station → sleeps when none.
- **Carrier end-to-end**: loads from supply station → carries → unloads elsewhere.
- **Recharge-over-ticks**: low-battery robot flies to a power station and recharges (chunk-force-load).
- Spawn + dock + recharge smoke (the MVP gate).
- `AIRobotFetchItem` in a private `test_environment` (serialize the shared `targettedItems` set);
  watch the int-id→UUID migration.
- Death/drops: battery-exhausted hit converts to items, robot item carries board+charge (extends Ph3's
  death-frees-all test).
- Partial wrench-dismantle: `makeMockPlayer` is not a `ServerPlayer` — document the skip.
- `ticksCharging` read accessor (amendment 5 pre-note) so "simulate does not bump the latch" pins directly.

---

## File plan

New under `src/main/java/buildcraft/robotics/`:
- `ai/AIRobotMain.java`, `AIRobotRecharge.java`, `AIRobotSleep.java`, `AIRobotShutdown.java`,
  `AIRobotGoto.java`, `AIRobotStraightMoveTo.java`, `AIRobotGotoBlock.java`, `AIRobotGotoStation.java`,
  `AIRobotGotoSleep.java`, `AIRobotSearchStation.java`, `AIRobotSearchAndGotoStation.java`,
  `AIRobotGotoStationToLoad.java`, `AIRobotGotoStationToUnload.java`, `AIRobotGotoStationAndLoad.java`,
  `AIRobotGotoStationAndUnload.java`, `AIRobotLoad.java`, `AIRobotUnload.java`, `AIRobotFetchItem.java`.
- `boards/BoardRobotPicker.java`, `BoardRobotPickerNBT.java`, `BoardRobotCarrier.java`,
  `BoardRobotCarrierNBT.java`.
- `item/ItemRedstoneBoard.java` (+ registration in `BCRoboticsItems`).

New under `src/test/java/buildcraft/robotics/`:
- `ai/AIRobotMainTest.java`, `AIRobotRechargeTest.java`, `AIRobotSleepTest.java`,
  `AIRobotGotoBlockTest.java`, `AIRobotCostTableTest.java` (JUnit); `ai/AIRobotFetchItemTester.java` (game);
  `boards/PickerCarrierTester.java` (game), `boards/BoardNbtRoundTripTest.java` (JUnit);
  `item/ItemRedstoneBoardTester.java` (game).

Assets: board item models/textures (`items/board_*.json` per `getItemModelLocation`, textures already
moved in Ph3 for the robot skins); **no new recipes** (see D5 — obtainability is Ph7); lang keys for
`buildcraft.boardRobotPicker` / `buildcraft.boardRobotCarrier` (+ `.desc`).

> **As-built nit (2026-08-06):** the per-board `items/board_*.json` split was NOT shipped — the board
> item has a single shared model/icon (`items/redstone_board.json` + `textures/item/redstone_board.png`)
> for every board, and nothing in main code calls `getItemModelLocation()`. The per-board item identity
> is a dead API until Ph5's catalog makes it worth wiring (each board its own icon, as 7.1.x had) — or it
> should be dropped then. Noted in robotics-resurrection.md's Ph5 section.

Modified:
- `api/robots/DockingStation.java` (D1 policy methods), `api/robots/IRobotAccess.java` (D2 `getTransactor()`
  + D7 `getDistance(x,y,z)` default), `api/robots/AIRobot.java` (writeToNbt null-guard fix),
  `robotics/DockingStationPipe.java` (D6 input), `robotics/boards/BoardRobotEmpty.java` (GotoSleep),
  `robotics/BCRobotics.java` (AI + board registration), `robotics/BCRoboticsItems.java`,
  `robotics/entity/EntityRobot.java` (wire `mainAI.cycle()` to the real tree; `ticksCharging` accessor),
  `docs/robotics-resurrection.md` (Ph4 supersessions + the crystal/recipe decisions).

Copyright: **all AI and board files are PORTED** — carry the 7.1.x upstream notice verbatim (recover
from `upstream/7.1.x`, not neighbours). `ItemRedstoneBoard` is also ported. The policy methods, tests,
and the D2/D7 interface additions are NEW — BuildCraftUnofficial contributors header only.

---

## Sequencing / commit strategy

1. **Skeleton + red tests first** — write the AI/board skeletons and the failing tests; commit the red
   baseline (the phase's `tests-written-first` gate, mirroring Ph3's `06ff16900`).
2. **Land the four seams + registration in ONE commit** — D1 policy methods, D2 `getTransactor()`,
   D7 `getDistance()`, the AI-registration + board-registration block. These are cross-cutting; a
   half-applied seam leaves the tree uncompilable, exactly as Ph3's rebase did.
3. Then the AIs in dependency order (goto family → search → load/unload → fetch → control), then the
   two boards, then `ItemRedstoneBoard` (no recipes — see D5).
4. Green the full game-test suite (implementation gate = ALL tests, not just the new ones), then
   in-client visual verification on 26.2 + 1.21.1 (the two extreme renderer generations) via McDevBridge.

**Open items to verify during implementation (not decisions, just compile-checks):**
- `SoftBlockAccess.of(level)` from `AIRobotGotoBlock` (D3) — confirm it needs no per-node directive.
- `level.getEntitiesOfClass(ItemEntity.class, ...)` AABB construction on all five nodes.
- `IItemTransactor` insertion semantics for the quantity-limited load (partial + commit vs simulate).
- Exact board microJoule costs from 7.1.x `ImplRedstoneBoardRegistry`.
