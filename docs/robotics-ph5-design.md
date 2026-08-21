# Robotics Ph5 — Board catalog: design & seams

Working design for [robotics-resurrection.md](robotics-resurrection.md) Ph5 — the board catalog. This file is the
handoff for the implementing agents: every seam is verified against the current tree and recorded here so no agent
re-explores. Read [robotics-ph3-design.md](robotics-ph3-design.md) amendments 5–9 and the Ph4 plan first — this builds
on their seams (D1 station policy, D2 transactor, D3 sync pathfinding, D4 UUID item keys).

**Ph5 scope (from robotics-resurrection.md):** port the remaining work/break/harvest AIs + boards (Lumberjack,
Harvester, Miner, Planter, Farmer, Pump, Knight, Butcher) + the 2 abstract generic-search/break bases, and resolve
the dead per-board item identity. **Decision (2026-08-20): tier-color chips pulled from upstream** — NOT per-board
icons (upstream never had those), NOT one shared icon.

---

## Verified seams (all current on main)

### A. The robot programming surface — `IRobotAccess` (api/robots)

`IRobotAccess` is the interface every AI and board programs against. Verified present: `position()`, `blockPosition()`,
`getDistance(x,y,z)` (default), `getBattery()`, `getPower()`, `getHeldItem()`, `setItemInUse(ItemStack)`,
`setItemActive(boolean)`, `aimItemAt(float yaw,float pitch)` + `aimItemAt(BlockPos)`, `getAimYaw()`, `getAimPitch()`,
`dock/undock`, `getDockingStation()`, `getLinkedStation()`, `setMainStation()`, `getRegistry()`, `getRobotId()`,
`releaseResources()`, `getZoneToWork()`, `getZoneToLoadUnload()`, `getInventorySize()`, `getInventoryStack(int)`,
`setInventoryStack(int,ItemStack)`, `getTransactor()` (IItemTransactor), `containsItems()`, `hasFreeSlot()`,
`receiveItem(BlockEntity,ItemStack)`, `unreachableEntityDetected(Entity)`, `isKnownUnreachable(Entity)`,
`getBoard()`, `isMoving()`. Constants: `MAX_POWER`, `SAFETY_POWER`, `SHUTDOWN_POWER`, `NULL_ROBOT_ID`,
`DAMAGE_ENERGY_PER_POINT`.

**Two seams must be ADDED to IRobotAccess** for Ph5:

1. **Fluid access** (Pump + fluid AIs): `EntityRobotBase implements IFluidHandlerAdv` (api/core) and the concrete
   `EntityRobot` has a single 4000 mB tank behind it with `fill(FluidStack)`. Add
   `IFluidHandlerAdv getFluidHandler()` to `IRobotAccess` (the concrete robot returns itself; a mock returns a stub).
   This mirrors the D2 `getTransactor()` seam. Cross-node: `IFluidHandlerAdv` already has the `>=1.21.10` fork baked in.
2. **Attack** (Knight/Butcher): no attack method exists. Add `void attackTargetEntityWithCurrentItem(Entity target)`
   to `IRobotAccess`; the concrete robot implements it (hurt the target with the held item / `doHurtTarget`).

### B. World properties (core/properties)

`BuildCraftAPI.getWorldProperty(String)` / `registerWorldProperty(String, IWorldProperty)` (api/core) back a plain
`HashMap`. `IWorldProperty.get(Level world, BlockPos pos)`. Only `"soft"` is registered today (BCCore, `WorldPropertyIsSoft`).
`isSoftBlock` NPEs on a missing "soft" — the new properties must be registered or a missing-key call throws.

Port from `upstream/7.1.x:common/buildcraft/core/properties/` — all carry upstream copyright; recover the exact notice
from that ref (do NOT copy from a neighbour; `WorldPropertyIsSoft.java` is the in-tree example with the correct MMPL
header):

| class | property key | consumer |
|---|---|---|
| `WorldPropertyIsWood` | `"wood"` | Lumberjack |
| `WorldPropertyIsHarvestable` | `"harvestable"` | Harvester board + AIRobotHarvest |
| `WorldPropertyIsOre` | `"ore@hardness=N"` | Miner (`ore@hardness=min(3, harvestLevel)`) |
| `WorldPropertyIsDirt` | `"dirt"` | Farmer |
| `WorldPropertyIsReplaceable` | `"replaceable"` | Planter |
| `WorldPropertyIsFluidSource` | `"fluidSource"` | Pump |

Register all in `BCCore` alongside `"soft"` (`registerWorldProperty(...)` in the same init). Also port
`WorldPropertyIsFarmland` + `WorldPropertyIsLeaf` if the crop/harvest logic references them (verify against the
ported WorldPropertyIsHarvestable). The upstream `ChunkProperty`/`DimensionProperty`/`WorldProperty` base classes are
1.7.10-era; the modern `IWorldProperty` is a flat single-method interface, so port each `WorldPropertyIs*` as a
standalone class matching `WorldPropertyIsSoft`'s shape (no base-class hierarchy needed unless one of the ported
classes genuinely shares logic — prefer flat).

### C. Filters

- `IStackFilter` — api/core ✓ (`matches(ItemStack)`)
- `IFluidFilter` — api/core ✓
- `AggregateFilter`, `PassThroughFluidFilter` — lib/inventory/filter ✓
- `IBlockFilter` — robotics/path ✓ — **`matches(BlockPos)`** (collapsed from upstream's `(World,x,y,z)`), with
  `default IBlockFilter within(IZone)`. Board/AI filters must be written against `BlockPos`.
- `IEntityFilter` — **DOES NOT EXIST**. Create it. 7.1.x had it in `buildcraft.core.lib.utils.IEntityFilter`; put the
  modern equivalent in `buildcraft.robotics` (or `lib`) as `@FunctionalInterface boolean matches(Entity entity)`.

### D. Pathfinding search machinery (robotics/path)

- `PathFinding` ✓ (sync `IIterableAlgorithm`)
- `PathFindingSearch` ✓ (ported in Ph0) — constructor:
  `PathFindingSearch(SoftBlockAccess access, BlockPos start, Iterator<BlockPos> blockIter, IBlockFilter pathFound, double maxDistanceToEnd, float maxDistance, IZone zone, Set<BlockPos> reservations)`.
  Its `blockIter` yields **deltas** relative to `start` (it computes `start + delta` internally). It has
  `iterate()`, `isDone()`, `getResult()`, `getResultTarget()`, `unreserve(BlockPos)`.
- `SoftBlockAccess` ✓ — `SoftBlockAccess.of(Level)`; has `isSoft(BlockPos)` + `isChunkLoaded(BlockPos)`.
- `IBlockFilter` ✓ (see C).

**`BlockScanner*` iterator classes do NOT exist on main.** `AIRobotSearchBlock` needs them:
- `BlockScannerExpanding` (hollow-cube spiral, radius 0→64) — used by generic-search (random=false) + Farmer.
- `BlockScannerRandom` (random, radius 64) — Planter (random=true, no zone).
- `BlockScannerZoneRandom` — Planter (random=true, with zone).
Port all three from `upstream/7.1.x:common/buildcraft/core/lib/utils/`. They produce `BlockIndex` deltas upstream;
on main produce `BlockPos` deltas (a `BlockPos` with relative coords is fine — the `getX/getY/getZ` are used as deltas).
`AIRobotSearchBlock` (see E) wires one of these iterators into `PathFindingSearch`.

### E. The abstract board bases

Port from 7.1.x. The **statement-engine dependency is a Ph6 seam** — same shape as Ph4's D1. Upstream
`BoardRobotGenericSearchBlock` reads `robot.getLinkedStation().getActiveActions()` for its gate filter
(`updateFilter`/`matchesGateFilter`). **Ph5 keeps the D1-permissive stance**: the gate filter matches everything until
Ph6 lands. Concretely:
- `BoardRobotGenericSearchBlock` (abstract, extends `RedstoneBoardRobot`): abstract `isExpectedBlock(BlockPos)`;
  `update()` → `startDelegateAI(new AIRobotSearchAndGotoBlock(robot, false, filter))` where the filter checks
  `isExpectedBlock && !registry.isTaken(ResourceIdBlock)` and matches gate; `delegateAIEnded` stores `blockFound` /
  starts `AIRobotGotoSleep` on failure; `end()` releases; `writeSelfToNBT`/`loadSelfFromNBT` round-trip `blockFound`
  as a `BlockIndex`. `matchesGateFilter` returns true in Ph5 (updateFilter is a no-op).
- `BoardRobotGenericBreakBlock` (abstract, extends GenericSearchBlock): abstract `isExpectedTool(ItemStack)`;
  `update()` = equip tool → unload worn-out tool → break `blockFound` → else `super.update()`. Uses
  `AIRobotFetchAndEquipItemStack`, `AIRobotGotoStationAndUnload`, `AIRobotBreak`.
- **`BlockIndex`** is `buildcraft.api.core.BlockIndex` — verify it's the modern one already used by Ph4 AIs (yes:
  `ResourceIdBlock(BlockIndex)` and `blockFound.writeTo`/`new BlockIndex(compound)` patterns are already in Ph4).

### F. AIs to port (ai/)

All ported from `upstream/7.1.x:common/buildcraft/robotics/ai/`. Register each in `RobotManager` (see H). Copyright:
**ported → upstream notice verbatim** (recover from the 7.1.x ref, not neighbours).

- `AIRobotSearchBlock` — wires a BlockScanner iterator into `PathFindingSearch`. Modern `PathFindingSearch` already
  reserves internally (`Set<BlockPos>`), so the `takeResource`/`unreserve` logic maps onto `PathFindingSearch.unreserve`
  + `registry.take(ResourceIdBlock)`. `pathFound` must be `IBlockFilter` over `BlockPos`.
- `AIRobotSearchAndGotoBlock` — compose SearchBlock → GotoBlock(path). `getBlockFound()` returns `BlockIndex`.
- `AIRobotBreak` — break-speed math. 7.1.x used `ForgeHooks`/`getToolClasses`/`getHarvestLevel`/`EnchantmentHelper`.
  **Modern mapping:** use `Block#getDestroyProgress(player, state)` (or `player.getDestroySpeed`/`player.digSpeed`),
  `BlockUtil.computeBlockBreakPower` / `BlockUtil.breakBlockAndGetDrops`, and `EnchantmentHelper.getBlockEfficiency`
  (modern name) for the Efficiency modifier. Verify exact signatures in `.neoforge-ref/sources-26.2.0.49-beta`. The
  fake-player-with-tool approach (`CoreProxy`) may not exist — check `IFakePlayerProvider`/`fakePlayerProvider`
  (api/core, `BuildCraftAPI.fakePlayerProvider`) and prefer whatever the modern break helpers need.
- `AIRobotHarvest` — via `CropManager.harvestCrop(level, pos, drops)` ✓ + `BuildCraftAPI.getWorldProperty("harvestable")`; drop
  items via `ItemEntity` spawn (modern) instead of 7.1.x `BlockUtils.dropItem`.
- `AIRobotPlant` — `CropManager.plantCrop(level, player, seed, pos)` ✓ + a fake player (see Break seam).
- `AIRobotPumpBlock` — `BlockUtil.drainBlock(level, pos, doDrain)` ✓ (exists at lib/misc/BlockUtil:223), robot tank via
  the new `IRobotAccess.getFluidHandler()`.
- `AIRobotUseToolOnBlock` — use item on block. Modern: `Item#useOn` / `Block#useItemOn` with a `BlockHitResult`
  (or the `>=1.21.10` `useItemOn` signature). Damage via `stack.hurtAndBreak(1, ...)` (modern) instead of
  `damageItem(1, robot)`.
- `AIRobotAttack` — melee. Uses the new `IRobotAccess.attackTargetEntityWithCurrentItem(Entity)` + `GotoBlock` +
  `unreachableEntityDetected`. `getEnergyCost` = `BuilderAPI.BREAK_ENERGY * 2 / 20`.
- `AIRobotSearchEntity` — modern search like Ph4's `AIRobotFetchItem.scanForItem`: `level.getEntitiesOfClass(...)`
  with an AABB, `IEntityFilter`, zone, `isKnownUnreachable`. `getEnergyCost` = 2.
- `AIRobotFetchAndEquipItemStack` — tool equip. Upstream used `AIRobotLoad.takeSingle` (does not exist on main).
  **Modern mapping:** reach the station input transactor directly — extract one matching stack via
  `inputTransactor.extract(filter, 1, 64, false)` then `robot.setItemInUse(stack)`. The gate filter
  (`ActionRobotFilterTool`) is Ph6 → match-all until then (D1).

**Fluid AIs (for Pump)** — port: `AIRobotLoadFluids`, `AIRobotUnloadFluids`, `AIRobotGotoStationToLoadFluids`,
`AIRobotGotoStationToUnloadFluids`, `AIRobotGotoStationAndUnloadFluids`, `AIRobotGotoStationAndLoadFluids`.
These operate through `DockingStation.getFluidInput()`/`getFluidOutput()` (both `ResourceHandler<FluidResource>`
on >=1.21.10 — already present on `DockingStation`, verified) and the robot's `IRobotAccess.getFluidHandler()`
(new seam). The 7.1.x `IFluidTransactor`/`IFluidFilter` pattern maps onto the modern `ResourceHandler<FluidResource>`
`insert/extract` (TransactionContext). Verify against the Ph4 item Load/Unload AIs for the transactor pattern shape.

### G. Boards to port (boards/)

All extend the bases or `RedstoneBoardRobot` directly, exactly as upstream. Each needs a `BoardRobot*NBT` factory
(INSTANCE singleton + ID + texture + addInformation + getDisplayName + getItemModelLocation + create + getRobotTexture).
Robot-skin textures already exist for every board (`textures/entity/robot_lumberjack.png` … `robot_butcher.png`).
Model the NBT classes on the existing `BoardRobotPickerNBT`/`BoardRobotEmptyNBT` (the in-tree template).

- **Lumberjack** — extends GenericBreakBlock. `isExpectedTool` = can axe-dig; `isExpectedBlock` = `"wood"` property.
- **Harvester** — extends GenericSearchBlock. `isExpectedBlock` = `"harvestable"`; overrides `update` to use
  `AIRobotHarvest`.
- **Miner** — extends GenericBreakBlock. `MAX_HARVEST_LEVEL=3`, `detectHarvestLevel()` from held pickaxe tier;
  `isExpectedBlock` = `"ore@hardness=min(3, level)"`. Harvest-level via modern tool tier (`Tier.getLevel()` or
  `stack.getTier()` — verify; fall back to `Tool` rules / `isCorrectForDrops`).
- **Planter** — extends RedstoneBoardRobot directly. `CropManager.isSeed` filter, `"replaceable"` property,
  `AIRobotSearchAndGotoBlock(robot, true, filter, 1)`, `AIRobotPlant`.
- **Farmer** — extends RedstoneBoardRobot directly. `ItemHoe` equivalent (`stack.canPerformAction(ItemAbilities.HOE_TILL)`),
  `"dirt"` property + `isAirAbove`, `AIRobotUseToolOnBlock`.
- **Pump** — extends RedstoneBoardRobot directly. `"fluidSource"` property, robot tank via `getFluidHandler()`,
  `AIRobotGotoStationAndUnloadFluids`, `AIRobotPumpBlock`.
- **Knight** — extends RedstoneBoardRobot directly. `ItemSword` equivalent (`stack.canPerformAction(ItemAbilities.SWORD_SWEEP)`),
  `IEntityFilter` for `IMob` + angry `Wolf`, `AIRobotSearchEntity` → `AIRobotAttack`.
- **Butcher** — same as Knight but `EntityAnimal`.

### H. Registration (BCRobotics)

`BCRobotics.init` already registers Ph4 AIs via `RobotManager.registerAIRobot(...)` and boards via
`RedstoneBoardRegistry.instance.registerBoardType(NBT, cost)`. Add:
- Each new AI (all Ph5 AIs) to `RobotManager`.
- Each new board NBT with 7.1.x costs (converted at 10 RF/MJ — these are "chosen, not derived" pins from
  `BuildCraftRobotics.java`): picker/carrier=8000 green, **lumberjack/harvester/miner/planter/farmer/butcher/pump=32000
  blue**, **knight=128000 red**.

### I. Item identity — tier chips (DECISION 2026-08-20)

- Extract the 6 real 7.1.x chip PNGs from `upstream/7.1.x:buildcraft_resources/assets/buildcraftrobotics/textures/items/board/`
  (`blue.png`, `clean.png`, `green.png`, `red.png`, `unknown.png`, `yellow.png`) into
  `src/main/resources/assets/buildcraftunofficial/textures/item/board_<color>.png`.
- Per-tier item models in the modern 1.21.4+ two-file structure (`items/<id>.json` → `models/item/<id>.json` →
  texture). Same-tier boards share a chip, exactly as upstream's BCBoardNBT tier strings: picker/carrier=green,
  the 7 blue boards=blue, knight=red. `RedstoneBoardNBT.getItemModelLocation()` stays as dead API surface (no
  consumer on any line; the NBT classes return the board id, not a model id) — the per-stack switching happens
  entirely in the item model, below.
- **Render path CONFIRMED (2026-08-20), LANDED (2026-08-21)**: the modern item-model system switches per-stack.
  `items/redstone_board.json` is a chain of `minecraft:condition` models (the type id is `condition`, not
  `conditional` — verified against `ItemModels.ID_MAPPER` on all four modern lines) that each branch on the
  `minecraft:component` property (`ComponentMatches` → `CustomDataPredicate` → `NbtPredicate`), matching the
  nested `board.id` sub-compound:
  ```json
  { "model": {
      "type": "minecraft:condition",
      "property": "minecraft:component",
      "predicate": "minecraft:custom_data",
      "value": { "board": { "id": "<boardId>" } },
      "on_true":  { "type": "minecraft:model", "model": "buildcraftunofficial:item/board_blue" },
      "on_false": { "type": "minecraft:condition", "...": "next" } } }
  ```
  The property/predicate fields are **flat strings, not nested objects**: the codec chain is
  `dispatchMap` (`ConditionalItemModelProperties` → `DataComponentPredicate.singleCodec`) → `KeyDispatchCodec`,
  whose decode runs the target codec against the *same* MapLike (verified in DFU 10.0.21 bytecode) — the type ids
  are bare string fields (`"property": "minecraft:component"`, `"predicate": "minecraft:custom_data"`) with the
  payload fields (`value`) at the same level. A nested `"property": {"type": ...}` form fails `fieldOf`'s string
  read and the whole model fails to load (this exact defect shipped in `33fb40c45` and was caught in-client; the
  flat form is what renders — see the landed chain in `items/redstone_board.json`).
  `createBoard` stamps `board.id` = `getID()` (a namespaced string, e.g. `buildcraftunofficial:boardRobotPicker`), so
  the `value` partial-match is exactly `{"board": {"id": "<getID()>"}}`. `NbtPredicate` is a raw NBT compound
  compared by `CustomData.matchedBy` (partial match), so only the `board.id` path needs to match — partial
  (not exact) matching matters because a board item picked up from a broken robot carries its config alongside
  the id. One condition per board id (there is no OR in the codec), so the chain peels the 10 ids in order —
  green ×2, blue ×7, red (knight) — then the empty board id → `board_clean` (7.1.x's standalone board item showed
  the clean chip) and a final `redstone_board` fallback for bare stacks. `minecraft:select` + `minecraft:component`
  was the flatter alternative (multi-value cases) but matches the whole component by exact equality — rejected for
  the config-blob reason above. The 1.21.1 node has no item-model system: `items/*.json` is ignored there and every
  board renders the single `models/item/redstone_board.json` chip (line-inherent, accepted). `board_unknown.png` /
  `board_yellow.png` stay staged for Ph6+ boards (builder/stripes/delivery are yellow in 7.1.x).

### J. Lang keys

Add `buildcraft.boardRobotLumberjack`(+`.desc`), `harvester`, `miner`, `planter`, `farmer`, `pump`, `knight`,
`butcher` to the en lang file (and the other shipped languages as `en_us` fallback — check the existing convention).

---

## Test plan (written FIRST, red, against skeletons)

Mirror the Ph4 discipline (JUnit extends `VanillaSetupBaseTester` where an ItemStack is built; GameTests need the
3-part wiring — Java method + `BuildCraftGameTests` registration + manifest JSON, count must increment).

**Pure JUnit:**
- `BoardPredicateSweepTest` — flat per-board predicate sweep: Lumberjack `isExpectedTool`(axe)/`isExpectedBlock`(wood),
  Miner harvest-level clamp, Planter seed filter, Farmer hoe filter, Knight/Butcher sword filter.
- `BoardNbtRoundTripTest` — each new board `writeSelfToNBT`/`loadSelfFromNBT` round-trips (board blob → create → write → read).
- `AIRobotLoadUnloadFluidMathTest` — Load/Unload(+Fluids) move-math: conservation + quantity cap.
- `MinerHarvestLevelTest` — harvest-level clamp + `ore@hardness` key selection.

**GameTests:**
- `SearchBlockTester` / `SearchAndGotoBlockTester` — orchestration (search → reserve → goto; failure releases).
- `BoardRegistrySweepTester` — every registered Ph5 board instantiable + self-resolves through the registry.
- Representative end-to-end per board family (harvest, plant, break, pump) in a private `test_environment` where
  serialization matters.

---

## Sequencing / commit strategy

1. **Skeleton + red tests first** — write the failing tests against empty skeletons; commit the red baseline (the
   phase's tests-written-first gate, mirroring Ph3's `06ff16900` / Ph4's red commit).
2. **Foundations in ONE commit** — the 6 world properties + registration, `IEntityFilter`, `IRobotAccess` fluid+attack
   seams, 3 BlockScanners, the 2 abstract bases. These are cross-cutting; half-applied seams leave the tree uncompilable.
3. **AIs in dependency order** (search → break/harvest/plant/pump/useTool → combat → fluid), then **boards**.
4. **Registration + assets + item identity + lang.**
5. **Green the full game-test suite** (implementation gate = ALL tests, not just new), then in-client verification
   on 26.2 + 1.21.1 via McDevBridge.
