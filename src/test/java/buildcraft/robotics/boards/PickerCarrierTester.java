/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.boards;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.boards.RedstoneBoardNBT;
import buildcraft.api.boards.RedstoneBoardRobot;
import buildcraft.api.boards.RedstoneBoardRobotNBT;
import buildcraft.api.boards.RedstoneBoardRegistry;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.properties.BuildCraftProperties;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.api.robots.RobotManager;
import buildcraft.api.statements.IStatement;
import buildcraft.api.statements.IStatementParameter;
import buildcraft.api.statements.StatementParameterItemStack;

import buildcraft.core.BCCoreBlocks;
import buildcraft.core.BCCoreStatements;
import buildcraft.core.tile.TileEngineCreative;
import buildcraft.lib.BCLibConfig;
import buildcraft.lib.statement.ActionWrapper;
import buildcraft.lib.statement.TriggerWrapper;
import buildcraft.lib.test.EntityArenaUtil;
import buildcraft.lib.test.FeHandlerAccess;

import buildcraft.robotics.BCRoboticsEntities;
import buildcraft.robotics.BCRoboticsPlugs;
import buildcraft.robotics.BCRoboticsStatements;
import buildcraft.robotics.DockingStationPipe;
import buildcraft.robotics.RfStationTestSupport;
import buildcraft.robotics.RobotStationPluggable;
import buildcraft.robotics.ZonePinnedRobot;
import buildcraft.robotics.ai.AIRobotUnload;
import buildcraft.robotics.entity.EntityRobot;
import buildcraft.silicon.BCSiliconPlugs;
import buildcraft.silicon.gate.EnumGateLogic;
import buildcraft.silicon.gate.EnumGateMaterial;
import buildcraft.silicon.gate.EnumGateModifier;
import buildcraft.silicon.gate.GateVariant;
import buildcraft.silicon.plug.PluggableGate;
import buildcraft.transport.BCTransportBlocks;
import buildcraft.transport.BCTransportItems;
import buildcraft.transport.tile.TilePipeHolder;

/**
 * Ph4 game tests for the two real boards, driven against a LIVE {@link EntityRobot} with its
 * {@code AIRobotMain} tree actually cycling every tick. They prove the whole loop a mock cannot: a board
 * robot placed into the world autonomously finds its work — the picker flies to a dropped item and picks it
 * up, the carrier finds a supply station and pulls the chest's contents into its own inventory — and both do
 * it with no hand-holding beyond a seeded charge.
 *
 * <p>Both E2Es deliberately start the robot UNLINKED (no main station): that is the realistic
 * {@code /summon}-or-stationless case, and it is what proves the boards do not depend on being told where
 * home is. {@code AIRobotGotoStation} null-guards the stationless {@code AIRobotGotoSleep} fallback (see the
 * class javadoc there), so the robots fall to sleep cleanly once there is nothing left to do instead of NPE.
 *
 * <p>Position discipline is the same as {@code EntityRobotTester}: every relative position stays inside the
 * 6x7 arena cell (with the {@code minecraft:empty} structure the framework spaces arenas 6 apart in X and 7
 * in Z), the arena chunks are force-loaded, and nothing asserts on a fixed tick — each phase is gated on
 * observed state via {@link EntityArenaUtil#tickUntil}.
 */
public class PickerCarrierTester {

    /** Charge above {@code SAFETY_POWER} so the {@code AIRobotMain} ladder neither shuts the robot down nor
     *  sends it recharging mid-task. */
    private static final long SEEDED_CHARGE = 5000L * MjAPI.MJ;

    /** The picker test's poll, in two budgets that must not share a clock: {@link EntityArenaUtil#CELL_READY_TICKS}
     *  for the arena cell's chunks to start ticking (asynchronous, so the machine's, not the picker's),
     *  {@link #PICKER_WORK_TICKS} for the pick-up after that (~35 ticks when it runs). {@link #PICKER_POLL_TICKS} is
     *  their sum, the poll's budget (a sum of constants, which the poll-budget guard resolves); the manifest's
     *  {@code max_ticks} stays above it. */
    private static final int PICKER_WORK_TICKS = 150;
    private static final int PICKER_POLL_TICKS = EntityArenaUtil.CELL_READY_TICKS + PICKER_WORK_TICKS;

    /** The work budget of the recharge polls (the MJ rig, its unloaded-home variant and the RF twin), counted from
     *  the arena cell ticking: each poll's own budget adds {@code EntityArenaUtil.CELL_READY_TICKS} (read as such by
     *  {@code GameTestPollBudgetTester}), and each manifest's {@code max_ticks} stays above that sum. It covers the
     *  station's registration, the robot's flight to it, docking and the charge climbing past the threshold. */
    private static final int RECHARGE_WORK_TICKS = 380;

    /** The work budgets of the station-registration poll (the supply and unload pins) and of the carrier load polls,
     *  counted from the arena cell ticking like the ones above: each poll's own budget adds
     *  {@code EntityArenaUtil.CELL_READY_TICKS} (read as such by {@code GameTestPollBudgetTester}), and each
     *  manifest's {@code max_ticks} stays above that sum. A station registers on its pipe's first tick, and the pipe,
     *  the chest and the robot sit in different columns of a cell that straddles a chunk border in about 57% of runs. */
    private static final int REGISTER_WORK_TICKS = 120;
    private static final int CARRIER_WORK_TICKS = 260;

    // ---------- fixtures ----------

    private static TilePipeHolder placeItemPipe(GameTestHelper helper, BlockPos relPos) {
        helper.setBlock(relPos, BCTransportBlocks.PIPE_HOLDER.get());
        //? if >=1.21.10 {
        TilePipeHolder tile = helper.getBlockEntity(relPos, TilePipeHolder.class);
        //?} else {
        /*TilePipeHolder tile = helper.getBlockEntity(relPos);*/
        //?}
        tile.onPlacedBy(null, new ItemStack(BCTransportItems.PIPE_WOOD_ITEM.get()));
        return tile;
    }

    private static TilePipeHolder installStation(GameTestHelper helper, BlockPos relPos, Direction side) {
        TilePipeHolder tile = placeItemPipe(helper, relPos);
        tile.replacePluggable(side, new RobotStationPluggable(BCRoboticsPlugs.robotStation, tile, side));
        return tile;
    }

    /** The 1-slot, param-less gate. */
    private static final GateVariant BASIC_GATE =
            new GateVariant(EnumGateLogic.AND, EnumGateMaterial.CLAY_BRICK, EnumGateModifier.NO_MODIFIER);

    /** An always-on gate whose single slot carries {@code action} with no parameters — the D1 way to make
     *  a station's provide/accept policy pass EVERYTHING (7.1.x: an action with an empty filter set
     *  matches anything). Resolved synchronously, before the robot's first search, so the action is
     *  active the moment the station registers. */
    private static void addAlwaysOnGate(TilePipeHolder tile, Direction side, IStatement action) {
        PluggableGate gate = new PluggableGate(BCSiliconPlugs.gate, tile, side, BASIC_GATE);
        gate.logic.statements[0].trigger.set(TriggerWrapper.wrap(BCCoreStatements.TRIGGER_TRUE, null));
        gate.logic.statements[0].action.set(ActionWrapper.wrap(action, null));
        tile.replacePluggable(side, gate);
        gate.logic.resolveActions();
    }

    /** The 1-slot gate WITH action parameters (iron + quartz) - what a Provide Items action needs to say WHICH
     *  item it provides. */
    private static final GateVariant PARAM_GATE =
            new GateVariant(EnumGateLogic.AND, EnumGateMaterial.IRON, EnumGateModifier.QUARTZ);

    /** An always-on gate holding {@code action} with {@code params} as its parameters; resolved synchronously, as
     *  {@link #addAlwaysOnGate}. */
    private static void addAlwaysOnParamGate(TilePipeHolder tile, Direction side, IStatement action,
                                        IStatementParameter... params) {
        PluggableGate gate = new PluggableGate(BCSiliconPlugs.gate, tile, side, PARAM_GATE);
        gate.logic.statements[0].trigger.set(TriggerWrapper.wrap(BCCoreStatements.TRIGGER_TRUE, null));
        gate.logic.statements[0].action.set(ActionWrapper.wrap(action, null));
        for (int i = 0; i < params.length; i++) {
            gate.logic.statements[0].action.set(i, params[i]);
        }
        tile.replacePluggable(side, gate);
        gate.logic.resolveActions();
    }

    private static TilePipeHolder placePowerPipe(GameTestHelper helper, BlockPos relPos,
                                                 net.minecraft.world.item.Item pipeItem) {
        helper.setBlock(relPos, BCTransportBlocks.PIPE_HOLDER.get());
        //? if >=1.21.10 {
        TilePipeHolder tile = helper.getBlockEntity(relPos, TilePipeHolder.class);
        //?} else {
        /*TilePipeHolder tile = helper.getBlockEntity(relPos);*/
        //?}
        tile.onPlacedBy(null, new ItemStack(pipeItem));
        return tile;
    }

    /** As {@link #installStation} but on a plain COBBLESTONE item pipe — an ordinary transport pipe with no
     *  extraction ability, used to pin that such a pipe is not a supply station. */
    private static TilePipeHolder installStationOnPlainPipe(GameTestHelper helper, BlockPos relPos, Direction side) {
        helper.setBlock(relPos, BCTransportBlocks.PIPE_HOLDER.get());
        //? if >=1.21.10 {
        TilePipeHolder tile = helper.getBlockEntity(relPos, TilePipeHolder.class);
        //?} else {
        /*TilePipeHolder tile = helper.getBlockEntity(relPos);*/
        //?}
        tile.onPlacedBy(null, new ItemStack(BCTransportItems.PIPE_COBBLE_ITEM.get()));
        tile.replacePluggable(side, new RobotStationPluggable(BCRoboticsPlugs.robotStation, tile, side));
        return tile;
    }

    /** The registered station for a pipe face, or null while {@code RobotStationPluggable.onTick()} has not
     *  yet lazily registered it. */
    private static DockingStationPipe stationAt(GameTestHelper helper, BlockPos relPos, Direction side) {
        DockingStation station = RobotManager.registryProvider.getRegistry(helper.getLevel())
                .getStation(helper.absolutePos(relPos), side);
        return station instanceof DockingStationPipe pipe ? pipe : null;
    }

    // Every live robot below comes from ZonePinnedRobot.spawn: the board's constructor starts its AIRobotMain, so the
    // robot begins cycling the moment its first tick registers it, and a board with no zone searches the whole
    // dimension (sibling arenas' drops and stations included). Pinned to this cell, it cannot.

    // ---------- registry completeness ----------

    /** Every robot board in the live registry must instantiate against a real robot and resolve back to its
     *  own NBT handler — the sweep that catches a board registered with a broken {@code create} or a handler
     *  that does not point back at itself. The JUnit {@code BoardNbtRoundTripTest} pins the item-stack blob
     *  side; this pins the {@code create(IRobotAccess)} side against a live entity. */
    public static void everyRegisteredBoardSelfResolves(GameTestHelper helper) {
        RedstoneBoardRegistry registry = RedstoneBoardRegistry.instance;
        helper.assertTrue(registry != null, "the board registry must be wired by the mod load");

        EntityRobot robot = new EntityRobot(BCRoboticsEntities.ROBOT.get(), helper.getLevel());
        boolean anyRobotBoard = false;
        for (RedstoneBoardNBT<?> nbt : registry.getAllBoardNBTs()) {
            if (nbt instanceof RedstoneBoardRobotNBT robotNBT) {
                anyRobotBoard = true;
                RedstoneBoardRobot board = robotNBT.create(robot);
                helper.assertTrue(board != null,
                        "board '" + robotNBT.getID() + "' must create a board against a live robot");
                helper.assertTrue(board.getNBTHandler() == robotNBT,
                        "board '" + robotNBT.getID() + "' must resolve back to its own NBT handler");
            }
        }
        helper.assertTrue(anyRobotBoard,
                "the registry must contain at least one robot board (empty/picker/carrier)");
        helper.succeed();
    }

    // ---------- picker E2E ----------

    /** A charged picker with no home station autonomously flies to a dropped diamond two blocks away and
     *  picks it up into a transfer slot. Exercises the whole fetch loop in a live world: scan -> GotoBlock
     *  (undock + fly) -> transactor insert. */
    public static void pickerRobotPicksUpDroppedItem(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos robotRel = new BlockPos(3, 4, 2);
        BlockPos itemRel = new BlockPos(5, 4, 2);
        // The whole cell, not just the robot's chunk: the robot only ticks once its whole 5x5 neighbourhood is
        // FULL (with a 3x3 force that ring-2 stayed at the arena's own level), and it flies two blocks to the drop,
        // which can sit in the chunk next door, where it stops ticking until that chunk is entity-ticking too.
        EntityArenaUtil.forceLoadCell(helper);

        EntityRobot robot = ZonePinnedRobot.spawn(helper, robotRel, BoardRobotPickerNBT.INSTANCE);
        robot.getBattery().addPower(SEEDED_CHARGE, false);

        // A dropped item two blocks away, pinned in mid-air with noGravity so it cannot fall through the
        // arena floor or into the void before the robot reaches it (the fetch targets Math.floor(item pos),
        // and a floor-resting item resolves to the solid block under it).
        Vec3 itemPos = Vec3.atCenterOf(helper.absolutePos(itemRel));
        ItemEntity item = new ItemEntity(level, itemPos.x, itemPos.y, itemPos.z, new ItemStack(Items.DIAMOND));
        item.setNoGravity(true);
        level.addFreshEntity(item);

        // The work budget starts once the cell is entity-ticking (CELL_READY_TICKS is the wait for that, a matter of
        // the machine's load): the pick-up takes ~35 ticks from there.
        EntityArenaUtil.tickUntil(helper, PICKER_POLL_TICKS,
                EntityArenaUtil.whenCellTicking(helper, EntityArenaUtil.CELL_READY_TICKS, PICKER_WORK_TICKS,
                        () -> robot.containsItems()), () -> {
            helper.assertTrue(ItemStack.matches(robot.getInventoryStack(0), new ItemStack(Items.DIAMOND, 1)),
                    "the picker must pick up the dropped diamond into a transfer slot");
            // The robot must have FLOWN to the item (pathfinding + delta-movement), not teleported it in:
            // the fetch's pick-up is distance-independent, so a dead pathfinder would still "succeed"
            // while the robot sat at its spawn. The goto target is the item's own block centre.
            helper.assertTrue(robot.position().distanceToSqr(itemPos) < 2.25,
                    "the picker must fly to the dropped item: robot at " + robot.position()
                            + " but item at " + itemPos);
            // And the drop must be CONSUMED from the world, not left behind: the pick's taken-count used
            // to read the transactor-mutated stack (always 0), so the robot gained the diamond while the
            // drop stayed intact and the same item was re-fetched forever — unbounded duplication.
            helper.assertTrue(item.isRemoved(),
                    "the picked-up drop must be removed from the world — a surviving item entity means "
                            + "the pick duplicated the stack instead of consuming it");
            helper.succeed();
        }, "the picker robot never picked up the dropped diamond",
                // Remove the robot on every exit: the picker's 250-block fetch range is inherent to the board,
                // and a robot left alive would keep scanning — and flying toward — items in neighbouring tests'
                // arenas. Discarding it (and running this test in its own private environment) keeps the batch
                // isolation clean.
                () -> EntityArenaUtil.discard(robot, item));
    }

    // ---------- supply-station discovery (D6) ----------

    /** A robot station is only a SUPPLY station when its pipe is a wooden (extraction) pipe, and the
     *  inventory it supplies is the one that pipe is pointed at — not merely whatever sits opposite the
     *  station's own face.
     *
     * <p>Both halves regressed silently and only in-client testing caught it: a station on a plain
     * cobblestone pipe let a Carrier drain the chest below it, making a robot station a free unpowered
     * hopper on any pipe in the game; and the search ignored the wooden pipe's wrench-set face entirely.
     * This pins both against the same chest, so a revert of either half fails here.
     *
     * <p>Deliberately asserts on {@code getItemInput()} directly rather than driving a live robot: the
     * negative case ("the carrier never loads") is unprovable by waiting, since a passing run and a merely
     * slow one look identical. */
    public static void supplyStationNeedsAWoodenPipePointedAtIt(GameTestHelper helper) {
        BlockPos plainRel = new BlockPos(1, 3, 1);
        BlockPos plainChestRel = new BlockPos(1, 2, 1);
        BlockPos woodRel = new BlockPos(4, 3, 1);
        BlockPos woodChestRel = new BlockPos(4, 2, 1);
        EntityArenaUtil.forceLoadCell(helper);

        helper.setBlock(plainChestRel, Blocks.CHEST);
        installStationOnPlainPipe(helper, plainRel, Direction.UP);
        helper.setBlock(woodChestRel, Blocks.CHEST);
        installStation(helper, woodRel, Direction.UP);

        // The wooden pipe aims itself on its first server tick (PipeBehaviourDirectional.onTick advances to
        // the first face carrying a TILE connection), so gate on the station existing AND the wooden one
        // having resolved its chest — never on a fixed tick.
        EntityArenaUtil.tickUntil(helper, EntityArenaUtil.CELL_READY_TICKS + REGISTER_WORK_TICKS,
                EntityArenaUtil.whenCellTicking(helper, EntityArenaUtil.CELL_READY_TICKS, REGISTER_WORK_TICKS,
                        () -> {
                            DockingStationPipe wood = stationAt(helper, woodRel, Direction.UP);
                            return wood != null && wood.getItemInput() != null
                                    && stationAt(helper, plainRel, Direction.UP) != null;
                        }),
                () -> {
                    DockingStationPipe plain = stationAt(helper, plainRel, Direction.UP);
                    helper.assertTrue(plain.getItemInput() == null,
                            "a station on a plain cobblestone pipe must NOT supply the chest beneath it - "
                                    + "extraction takes a wooden pipe, or every robot station is a free hopper");

                    DockingStationPipe wood = stationAt(helper, woodRel, Direction.UP);
                    helper.assertTrue(wood.getItemInput() != null,
                            "a station on a wooden pipe pointed at a chest must supply that chest");
                    // The input side is the face of the INVENTORY the pipe touches, i.e. the opposite of the
                    // direction searched. Pipe faces DOWN at the chest, so the chest is entered from UP.
                    helper.assertTrue(wood.getItemInputSide().face == Direction.UP,
                            "the item input side must be the chest's own face (UP for a pipe facing DOWN), "
                                    + "not the search direction: got " + wood.getItemInputSide());
                    helper.succeed();
                },
                "the wooden supply station never resolved the chest below it");
    }

    // ---------- unload path (station item output) ----------

    /** A docked robot unloads into its station's pipe even when the pipe has NO connection on the face
     *  opposite the station — a lone dead-end pipe is a perfectly good unload target. 7.1.x achieved
     *  this with a synthetic station-side {@code IInjectable} whose {@code canInjectItems} returned true
     *  unconditionally and which pushed the {@code TravelingItem} straight into the transport; exposing
     *  the raw {@code PipeFlowItems} instead inherits its {@code canInjectItems} =
     *  {@code pipe.isConnected(from)}, so with the station on the UP face every unload bails unless
     *  something happens to be connected to the pipe's DOWN face. That makes the picker's "unloads them
     *  at a station" silently never happen at any normal dead-end dock — it only works when a connection
     *  sits exactly opposite the station, which is the geometry the carrier E2E happens to build.
     *
     * <p>Ph6 (D1): the unload additionally needs the station's gate to hold an accept-items action — the
     *  policy refuses a gateless station exactly as 7.1.x did — so the rig carries an always-on,
     *  unfiltered {@code ActionStationAcceptItems} gate, which passes everything. */
    public static void unloadStationDoesNotNeedAnOppositeFaceConnection(GameTestHelper helper) {
        BlockPos pipeRel = new BlockPos(2, 3, 4);
        BlockPos robotRel = new BlockPos(2, 4, 4);
        EntityArenaUtil.forceLoadCell(helper);

        TilePipeHolder tile = installStationOnPlainPipe(helper, pipeRel, Direction.UP);
        addAlwaysOnGate(tile, Direction.WEST, BCRoboticsStatements.ACTION_STATION_ACCEPT_ITEMS);

        EntityRobot[] spawned = { null };
        EntityArenaUtil.tickUntil(helper, EntityArenaUtil.CELL_READY_TICKS + REGISTER_WORK_TICKS,
                EntityArenaUtil.whenCellTicking(helper, EntityArenaUtil.CELL_READY_TICKS, REGISTER_WORK_TICKS,
                        () -> stationAt(helper, pipeRel, Direction.UP) != null),
                () -> {
                    DockingStationPipe station = stationAt(helper, pipeRel, Direction.UP);
                    EntityRobot robot = ZonePinnedRobot.spawn(helper, robotRel, BoardRobotEmptyNBT.INSTANCE);
                    spawned[0] = robot;
                    station.takeAsMain(robot);
                    robot.dock(station);
                    robot.setInventoryStack(0, new ItemStack(Items.DIAMOND, 7));

                    helper.assertTrue(AIRobotUnload.unload(robot, station, false),
                            "the unload dry-run must accept a dead-end pipe — a robot docked at a lone "
                                    + "pipe's station is a valid item source regardless of what the pipe "
                                    + "connects to (7.1.x's station-side IInjectable never consulted the "
                                    + "network)");
                    helper.assertTrue(AIRobotUnload.unload(robot, station, true),
                            "a docked robot must be able to unload into a dead-end pipe");
                    helper.assertTrue(robot.getInventoryStack(0).isEmpty(),
                            "the unload must actually take the cargo, not just report success");
                    helper.succeed();
                },
                "the station never registered",
                () -> EntityArenaUtil.discard(spawned[0]));
    }

    // ---------- carrier E2E ----------

    /** A charged carrier with no home station must autonomously find a loadable supply station and pull the
     *  chest's contents into its own inventory. Exercises search -> goto (fly + dock) -> {@code AIRobotLoad}
     *  from {@code DockingStationPipe.getItemInput} (D6).
     *
     *  <p>Ph6 (D1): the station's gate must hold an unfiltered provide-items action or the policy refuses
     *  the load (gateless stations refuse, as in 7.1.x) — so the rig carries an always-on, unfiltered
     *  {@code ActionStationProvideItems} gate, which passes everything. */
    public static void carrierRobotLoadsFromSupplyChest(GameTestHelper helper) {
        BlockPos pipeRel = new BlockPos(2, 3, 4);
        // One cell below the pipe: the station's input is on side().getOpposite() = DOWN for an UP face.
        BlockPos chestRel = new BlockPos(2, 2, 4);
        BlockPos robotRel = new BlockPos(4, 3, 4);
        EntityArenaUtil.forceLoadCell(helper);

        TilePipeHolder tile = installStation(helper, pipeRel, Direction.UP);
        addAlwaysOnGate(tile, Direction.WEST, BCRoboticsStatements.ACTION_STATION_PROVIDE_ITEMS);
        helper.setBlock(chestRel, Blocks.CHEST);
        //? if >=1.21.10 {
        ChestBlockEntity chest = helper.getBlockEntity(chestRel, ChestBlockEntity.class);
        //?} else {
        /*ChestBlockEntity chest = helper.getBlockEntity(chestRel);*/
        //?}
        chest.setItem(0, new ItemStack(Items.DIAMOND, 5));

        // Lazy-spawn on the first tick the supply station has registered: the carrier must not search before
        // there is anything to find, or its first search comes up empty and it goes to sleep. The spawn is
        // guarded on a single tick so it happens exactly once, and the condition then waits for the load.
        EntityRobot[] robot = { null };
        EntityArenaUtil.tickUntil(helper, EntityArenaUtil.CELL_READY_TICKS + CARRIER_WORK_TICKS,
                EntityArenaUtil.whenCellTicking(helper, EntityArenaUtil.CELL_READY_TICKS, CARRIER_WORK_TICKS,
                () -> {
                    if (stationAt(helper, pipeRel, Direction.UP) == null) {
                        return false;
                    }
                    if (robot[0] == null) {
                        robot[0] = ZonePinnedRobot.spawn(helper, robotRel, BoardRobotCarrierNBT.INSTANCE);
                        robot[0].getBattery().addPower(SEEDED_CHARGE, false);
                    }
                    return robot[0].containsItems();
                },
                () -> robot[0] == null ? "the supply station never registered, so the carrier was never added"
                        : "the carrier robot never loaded the diamonds from the supply chest"),
                () -> {
                    helper.assertTrue(ItemStack.matches(robot[0].getInventoryStack(0),
                            new ItemStack(Items.DIAMOND, 5)),
                            "the carrier must load the diamonds from the supply chest into its inventory");
                    // Same false-positive guard as the picker test: the load reads the chest from the
                    // STATION's position, so a regression that docks the robot without flying would load
                    // fine while the robot sat at its spawn. A genuinely docked robot is snapped to the
                    // station's face centre every tick.
                    Vec3 faceCentre = Vec3.atCenterOf(helper.absolutePos(pipeRel)).add(0, 0.5, 0);
                    helper.assertTrue(robot[0].position().distanceToSqr(faceCentre) < 2.25,
                            "the carrier must FLY to and dock at the supply station, not load from its "
                                    + "spawn: robot at " + robot[0].position() + " but station face at "
                                    + faceCentre);
                    helper.succeed();
                },
                "the carrier robot never loaded the diamonds from the supply chest",
                () -> EntityArenaUtil.discard(robot[0]));
    }

    /** The same carrier against a chest whose FIRST stack the station's gate refuses: Provide Items = Diamond over
     *  a chest of [cobblestone, diamond]. "Load a single stack" used to try only the chest's first matching
     *  stack, so the refused cobblestone hid the diamonds behind it - the station read as having nothing to
     *  give, and the carrier never even flew there. It must put the refused stack back, look past it, and load
     *  the diamonds, leaving the cobblestone untouched. */
    public static void carrierLoadsPastAStackTheGateRefuses(GameTestHelper helper) {
        BlockPos pipeRel = new BlockPos(2, 3, 4);
        BlockPos chestRel = new BlockPos(2, 2, 4);
        BlockPos robotRel = new BlockPos(4, 3, 4);
        EntityArenaUtil.forceLoadCell(helper);

        TilePipeHolder tile = installStation(helper, pipeRel, Direction.UP);
        addAlwaysOnParamGate(tile, Direction.WEST, BCRoboticsStatements.ACTION_STATION_PROVIDE_ITEMS,
                new StatementParameterItemStack(new ItemStack(Items.DIAMOND)));
        helper.setBlock(chestRel, Blocks.CHEST);
        //? if >=1.21.10 {
        ChestBlockEntity chest = helper.getBlockEntity(chestRel, ChestBlockEntity.class);
        //?} else {
        /*ChestBlockEntity chest = helper.getBlockEntity(chestRel);*/
        //?}
        chest.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        chest.setItem(1, new ItemStack(Items.DIAMOND, 5));

        EntityRobot[] robot = { null };
        EntityArenaUtil.tickUntil(helper, EntityArenaUtil.CELL_READY_TICKS + CARRIER_WORK_TICKS,
                EntityArenaUtil.whenCellTicking(helper, EntityArenaUtil.CELL_READY_TICKS, CARRIER_WORK_TICKS,
                () -> {
                    if (stationAt(helper, pipeRel, Direction.UP) == null) {
                        return false;
                    }
                    if (robot[0] == null) {
                        // Zone-pinned to this cell: the load search would otherwise take any providing station
                        // in the dimension that has diamonds, a sibling arena's included.
                        robot[0] = ZonePinnedRobot.spawn(helper, robotRel, BoardRobotCarrierNBT.INSTANCE);
                        robot[0].getBattery().addPower(SEEDED_CHARGE, false);
                    }
                    return robot[0].containsItems();
                },
                () -> robot[0] == null ? "the supply station never registered, so the carrier was never added"
                        : "the carrier never loaded the diamonds from behind the refused cobblestone"),
                () -> {
                    helper.assertTrue(ItemStack.matches(robot[0].getInventoryStack(0),
                            new ItemStack(Items.DIAMOND, 5)),
                            "the carrier must load the diamonds the gate offers, past the cobblestone it refuses, got "
                                    + robot[0].getInventoryStack(0));
                    helper.assertTrue(ItemStack.matches(chest.getItem(0), new ItemStack(Items.COBBLESTONE, 64)),
                            "the refused cobblestone must be back in the chest, untouched, got " + chest.getItem(0));
                    helper.assertTrue(chest.getItem(1).isEmpty(), "the diamonds left the chest");
                    helper.succeed();
                },
                "the carrier never loaded the diamonds from behind the refused cobblestone",
                () -> EntityArenaUtil.discard(robot[0]));
    }

    // The Ph4 permissive-loop pin (carrierUnloadsAtTheStationItLoadedFrom) is GONE: the D1 reconciliation
    // gave DockingStationPipe the 7.1.x refuse-by-default accept policy, so a gateless carrier can no
    // longer unload at its supply station and loop load -> unload -> load forever. The replacement pin is
    // RobotGateTester.forbidRobotActionRefusesUnloadAtStation (a gate-forbidden station refusing the
    // unload, observed through the live getActiveActions() aggregation).

    // ---------- recharge E2E (AIRobotMain ladder) ----------

    /** The recharge leg of the {@code AIRobotMain} ladder, end to end: a robot below
     *  {@code SAFETY_POWER} autonomously finds the kinesis-pipe station, flies to it, docks, and its
     *  battery climbs back past the threshold. The rig mirrors
     *  {@code RobotStationPluggableTester.testKinesisPipeChargesDockedRobot} (creative engine at full
     *  output -> wood kinesis -> stone kinesis, station on the top pipe), but here the robot docks
     *  ITSELF — the pluggable test hand-docks a fixture robot, so nothing else exercises
     *  {@code AIRobotRecharge}'s search -> goto -> dock chain against a live power network. Runs in its
     *  own environment: the station search is global, and the default batch contains another powered
     *  station (that very rig's) the robot could legitimately prefer. */
    public static void lowPowerRobotRechargesAtPoweredStation(GameTestHelper helper) {
        rechargeAtPoweredStation(helper, false);
    }

    /** The same, for a robot homed on a station whose chunk is not loaded: a low robot with an unreachable home waits
     *  for it rather than working itself flat ({@code AIRobotMain}), but only once no charger is in reach. With one in
     *  reach it must still fly there and charge, not wait. Its own environment, as above. */
    public static void lowPowerRobotWithUnloadedHomeRechargesAtPoweredStation(GameTestHelper helper) {
        rechargeAtPoweredStation(helper, true);
    }

    private static void rechargeAtPoweredStation(GameTestHelper helper, boolean unloadedHome) {
        BlockPos redstoneRel = new BlockPos(1, 1, 6);
        BlockPos engineRel = new BlockPos(1, 2, 6);
        BlockPos woodPipeRel = new BlockPos(1, 3, 6);
        BlockPos stonePipeRel = new BlockPos(1, 4, 6);
        BlockPos robotRel = new BlockPos(3, 4, 6);
        // The rig's pipes (x=1) and the robot (x=3) can sit in different chunks: force the whole cell, and start the
        // poll's work budget only once it ticks.
        EntityArenaUtil.forceLoadCell(helper);

        if (BCCoreBlocks.ENGINE_CREATIVE == null) {
            throw new IllegalStateException(
                    "ENGINE_CREATIVE not registered — test JVM was launched without -Dbuildcraft.dev=true. "
                            + "Check build.gradle gameTestServer run config.");
        }

        // Engine -> WOOD kinesis -> stone kinesis -> station. The wood pipe is load-bearing, not
        // decoration: only wood/diaWood kinesis pipes accept power from an engine, so an engine feeding
        // the stone pipe directly would transfer nothing at all.
        TilePipeHolder stonePipe = placePowerPipe(helper, stonePipeRel, BCTransportItems.PIPE_STONE_POWER.get());
        TilePipeHolder woodPipe = placePowerPipe(helper, woodPipeRel, BCTransportItems.PIPE_WOOD_POWER.get());
        stonePipe.replacePluggable(Direction.WEST,
                new RobotStationPluggable(BCRoboticsPlugs.robotStation, stonePipe, Direction.WEST));

        BlockState engineState = BCCoreBlocks.ENGINE_CREATIVE.get().defaultBlockState()
                .setValue(BuildCraftProperties.BLOCK_FACING_6, Direction.UP);
        helper.setBlock(engineRel, engineState);
        helper.setBlock(redstoneRel, Blocks.REDSTONE_BLOCK);

        //? if >=1.21.10 {
        TileEngineCreative engine = helper.getBlockEntity(engineRel, TileEngineCreative.class);
        //?} else {
        /*TileEngineCreative engine = helper.getBlockEntity(engineRel);*/
        //?}
        engine.currentOutputIndex = TileEngineCreative.OUTPUTS.length - 1; // 256 MJ/t

        // Same reason as PipeFlowPowerTester: setBlock + onPlacedBy skips the neighbour-changed cascade a
        // real placement triggers, so connections are never computed without this.
        woodPipe.getPipe().markForUpdate();
        stonePipe.getPipe().markForUpdate();

        // A home station far outside the arena grid, in a chunk that stays unloaded, for the unloaded-home variant.
        DockingStationPipe home = unloadedHome
                ? buildcraft.robotics.RobotStationRigTester.unloadedStation(helper.getLevel(),
                        helper.absolutePos(robotRel).offset(4096, 0, 256),
                        buildcraft.robotics.BCRoboticsStatements.ACTION_STATION_ACCEPT_ITEMS, null)
                : null;
        if (home != null) {
            helper.assertFalse(helper.getLevel().isLoaded(home.getPos()), "precondition: the home is not loaded");
            RobotManager.registryProvider.getRegistry(helper.getLevel()).registerStation(home);
        }

        EntityRobot[] robot = { null };
        EntityArenaUtil.tickUntil(helper, EntityArenaUtil.CELL_READY_TICKS + RECHARGE_WORK_TICKS,
                EntityArenaUtil.whenCellTicking(helper, EntityArenaUtil.CELL_READY_TICKS, RECHARGE_WORK_TICKS, () -> {
                    DockingStationPipe station = stationAt(helper, stonePipeRel, Direction.WEST);
                    if (station == null) {
                        return false;
                    }
                    if (robot[0] == null) {
                        // Pinned like every live board robot, though the recharge search itself takes no zone
                        // (AIRobotRecharge reaches every powered station, as in 7.1.x): what keeps it on this
                        // rig is the private environment and picking the NEAREST powered station, 2 blocks off.
                        ZonePinnedRobot spawned = new ZonePinnedRobot(helper, BoardRobotEmptyNBT.INSTANCE);
                        Vec3 at = Vec3.atCenterOf(helper.absolutePos(robotRel));
                        spawned.setPos(at.x, at.y, at.z);
                        // Below SAFETY_POWER so the ladder preempts into AIRobotRecharge on the first
                        // cycle; comfortably above SHUTDOWN_POWER so the robot stays alive.
                        spawned.getBattery().addPower(1000L * MjAPI.MJ, false);
                        if (home != null) {
                            spawned.setUniqueRobotId(
                                    RobotManager.registryProvider.getRegistry(helper.getLevel()).getNextRobotId());
                            helper.assertTrue(home.takeAsMain(spawned), "precondition: the robot is homed there");
                        }
                        helper.getLevel().addFreshEntity(spawned);
                        robot[0] = spawned;
                        return false;
                    }
                    return robot[0].getDockingStation() == station
                            && robot[0].getBattery().getStored() > IRobotAccess.SAFETY_POWER;
                }, () -> "station registered: " + (stationAt(helper, stonePipeRel, Direction.WEST) != null)
                        + ", robot spawned: " + (robot[0] != null)
                        + (robot[0] == null ? "" : ", docked: "
                                + (robot[0].getDockingStation() != null) + ", battery: "
                                + robot[0].getBattery().getStored())),
                () -> {
                    helper.assertTrue(robot[0].getBattery().getStored() > IRobotAccess.SAFETY_POWER,
                            "a robot docked at the powered station must charge past SAFETY_POWER — the "
                                    + "only power source in the arena is the kinesis rig");
                    helper.succeed();
                },
                "a robot below SAFETY_POWER never docked at the powered station and recharged past it",
                () -> {
                    EntityArenaUtil.discard(robot[0]);
                    if (home != null) {
                        RobotManager.registryProvider.getRegistry(helper.getLevel()).removeStation(home);
                    }
                });
    }

    /** The Forge-Energy twin of {@link #lowPowerRobotRechargesAtPoweredStation}: the same ladder, but the only
     *  power in the arena is FE. A test-driven source keeps pushing a full packet per tick into the free face of
     *  a wood RF kinesis pipe whose other face carries the station; with autoconversion on, a robot below
     *  {@code SAFETY_POWER} must search the RF station out ({@code providesPower}), fly to it, dock, and have its
     *  battery converted up past the threshold. Before the station could take FE, the search found no charger at
     *  all and a docked robot would have shut down at {@code SHUTDOWN_POWER}. Own environment for the same
     *  reason as the MJ test (the station search is global) and because it holds {@code powerMode} flipped for
     *  the length of the run. */
    public static void lowPowerRobotRechargesAtRfStation(GameTestHelper helper) {
        BlockPos pipeRel = new BlockPos(1, 4, 6);
        BlockPos robotRel = new BlockPos(3, 4, 6);
        // The pipe (x=1) and the robot (x=3) can sit in different chunks: force the whole cell, and start the
        // poll's work budget only once it ticks.
        EntityArenaUtil.forceLoadCell(helper);

        RfStationTestSupport.ConfigScope scope = new RfStationTestSupport.ConfigScope(
                BCLibConfig.PowerMode.MJ_AUTOCONVERT_RF, 0.2, false);
        try {
            TilePipeHolder rfPipe = placePowerPipe(helper, pipeRel, BCTransportItems.PIPE_WOOD_RF.get());
            rfPipe.replacePluggable(Direction.WEST,
                    new RobotStationPluggable(BCRoboticsPlugs.robotStation, rfPipe, Direction.WEST));
            rfPipe.getPipe().markForUpdate();

            EntityRobot[] robot = { null };
            scope.succeedWhen(helper, EntityArenaUtil.CELL_READY_TICKS + RECHARGE_WORK_TICKS,
                    EntityArenaUtil.whenCellTicking(helper, EntityArenaUtil.CELL_READY_TICKS, RECHARGE_WORK_TICKS, () -> {
                DockingStationPipe station = stationAt(helper, pipeRel, Direction.WEST);
                if (station == null) {
                    return false;
                }
                // A full packet every tick. Nothing accumulates while no robot is docked: the pipe's buffer
                // fills once and then simply refuses.
                Object source = RfStationTestSupport.pipeEnergyHandler(rfPipe, Direction.EAST);
                if (source != null) {
                    FeHandlerAccess.insert(source, 1000, true);
                }
                if (robot[0] == null) {
                    // Zone-pinned for uniformity; the recharge search is zone-free (see the MJ twin).
                    robot[0] = ZonePinnedRobot.spawn(helper, robotRel, BoardRobotEmptyNBT.INSTANCE);
                    // Below SAFETY_POWER so the ladder preempts into AIRobotRecharge on the first cycle;
                    // comfortably above SHUTDOWN_POWER so the robot stays alive.
                    robot[0].getBattery().addPower(1000L * MjAPI.MJ, false);
                    return false;
                }
                return robot[0].getDockingStation() == station
                        && robot[0].getBattery().getStored() > IRobotAccess.SAFETY_POWER;
            }, () -> "station registered: " + (stationAt(helper, pipeRel, Direction.WEST) != null)
                    + ", robot spawned: " + (robot[0] != null)
                    + (robot[0] == null ? "" : ", docked: " + (robot[0].getDockingStation() != null)
                            + ", battery: " + robot[0].getBattery().getStored())),
                    "a robot below SAFETY_POWER never docked at the RF-kinesis station and recharged past it", () -> {
                EntityArenaUtil.discard(robot[0]);
                RfStationTestSupport.removeStation(helper, helper.absolutePos(pipeRel), Direction.WEST);
            });
        } catch (Throwable thrown) {
            scope.close();
            throw thrown;
        }
    }
}
