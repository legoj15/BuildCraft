/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.boards;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.boards.RedstoneBoardRobot;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotRegistry;
import buildcraft.api.robots.ResourceId;
import buildcraft.api.robots.ResourceIdBlock;
import buildcraft.robotics.BCRoboticsEntities;
import buildcraft.robotics.RobotRegistryLiveTester.LiveStation;
import buildcraft.robotics.StackRequest;
import buildcraft.robotics.ai.AIRobotDeliverRequested;
import buildcraft.robotics.ai.AIRobotGotoBlock;
import buildcraft.robotics.ai.AIRobotGotoStation;
import buildcraft.robotics.ai.AIRobotMain;
import buildcraft.robotics.ai.AIRobotPlant;
import buildcraft.robotics.ai.AIRobotPumpBlock;
import buildcraft.robotics.ai.AIRobotRecharge;
import buildcraft.robotics.ai.AIRobotSearchAndGotoBlock;
import buildcraft.robotics.ai.AIRobotUseToolOnBlock;
import buildcraft.robotics.entity.EntityRobot;

/** A robot that unloads mid-job loses every registry reservation, but its board and AIs come back from NBT
 *  remembering the job: the block a search-and-goto is flying to, the block a farmer/planter/pump is working, the
 *  request a delivery board is filling. Another robot may have claimed that job meanwhile (or, after a server
 *  restart, the stale claim was dropped the first time anyone asked about it). The reloaded robot must re-claim
 *  its job on its first cycle, or give it up to the robot that holds it - never work it unreserved alongside.
 *
 *  <p>Every reload is a real {@code writeToNbt} / {@code AIRobot.loadAI} round trip with the registry's unload
 *  release in between. Robots are unadded and registered by hand and boards are cycled synchronously (as
 *  {@code BreakBoardReloadReservationTester}), so no tick can interfere. A board that yields its job runs only its
 *  {@code preempt} (the first step of a cycle): the {@code update()} a full cycle would run next starts a new search
 *  over the dimension-wide station registry, which would find - and reserve - a sibling test's stations and orders. */
public class ReloadedJobReservationTester {

    private static final long SEEDED_CHARGE = 5000L * MjAPI.MJ;

    private static final BlockPos ROBOT_A_REL = new BlockPos(1, 2, 3);
    private static final BlockPos ROBOT_B_REL = new BlockPos(2, 2, 3);
    private static final BlockPos TARGET_REL = new BlockPos(4, 2, 4);
    private static final BlockPos STATION_REL = new BlockPos(4, 2, 1);

    /** Robots A and B; robot A is the one that unloads and comes back. */
    private static final class Robots implements AutoCloseable {
        final GameTestHelper helper;
        final IRobotRegistry registry;
        final EntityRobot robotA;
        final EntityRobot robotB;
        final List<Runnable> cleanup = new ArrayList<>();

        Robots(GameTestHelper helper) {
            this.helper = helper;
            robotA = robot(helper, ROBOT_A_REL);
            robotB = robot(helper, ROBOT_B_REL);
            registry = robotA.getRegistry();
        }

        private static EntityRobot robot(GameTestHelper helper, BlockPos rel) {
            EntityRobot robot = new EntityRobot(BCRoboticsEntities.ROBOT.get(), helper.getLevel());
            Vec3 spawn = Vec3.atCenterOf(helper.absolutePos(rel));
            robot.setPos(spawn.x, spawn.y, spawn.z);
            robot.getBattery().addPower(SEEDED_CHARGE, false);
            IRobotRegistry reg = robot.getRegistry();
            robot.setUniqueRobotId(reg.getNextRobotId());
            reg.registerRobot(robot);
            return robot;
        }

        /** Saves {@code before} as the robot entity would, unloads robot A (the registry frees everything it
         *  held), and reads the board back. */
        <T extends AIRobot> T reload(AIRobot before, Class<T> type) {
            CompoundTag saved = new CompoundTag();
            before.writeToNbt(saved);
            before.abortDelegateAI();
            registry.releaseResources(robotA);
            AIRobot loaded = AIRobot.loadAI(saved, robotA);
            helper.assertTrue(type.isInstance(loaded),
                    "precondition: the board reloads as " + type.getSimpleName() + " - got " + loaded);
            T board = type.cast(loaded);
            cleanup.add(board::abortDelegateAI);
            return board;
        }

        /** Owner-checked, like a robot's own release: drops the claim only from whichever test robot still holds it,
         *  never one some other robot of the shared registry holds. */
        void free(ResourceId id) {
            cleanup.add(() -> {
                registry.release(id, robotA.getRobotId());
                registry.release(id, robotB.getRobotId());
            });
        }

        @Override
        public void close() {
            for (Runnable r : cleanup) {
                r.run();
            }
            registry.killRobot(robotA);
            registry.killRobot(robotB);
        }
    }

    // ---------- the search-and-goto flight ----------

    /** Robot A's lumberjack board, reloaded while its search-and-goto flies to a log. */
    private static BoardRobotLumberjack reloadMidFlight(Robots r, BlockPos log) {
        GameTestHelper helper = r.helper;
        helper.setBlock(TARGET_REL, Blocks.OAK_LOG);
        ResourceIdBlock logId = new ResourceIdBlock(log);
        r.free(logId);

        BoardRobotLumberjack before = new BoardRobotLumberjack(r.robotA);
        AIRobotSearchAndGotoBlock flight = new AIRobotSearchAndGotoBlock(r.robotA, false, pos -> false);
        before.startDelegateAI(flight);
        CompoundTag found = new CompoundTag();
        found.putIntArray("blockFound", new int[] { log.getX(), log.getY(), log.getZ() });
        flight.loadSelfFromNBT(found);
        helper.assertTrue(r.registry.take(logId, r.robotA), "precondition: robot A reserved the log it flies to");
        LinkedList<BlockPos> path = new LinkedList<>();
        path.add(helper.absolutePos(new BlockPos(1, 2, 4)));
        path.add(helper.absolutePos(new BlockPos(2, 2, 4)));
        path.add(helper.absolutePos(new BlockPos(3, 2, 4)));
        flight.startDelegateAI(new AIRobotGotoBlock(r.robotA, path));

        BoardRobotLumberjack board = r.reload(before, BoardRobotLumberjack.class);
        helper.assertTrue(board.getDelegateAI() instanceof AIRobotSearchAndGotoBlock loaded
                        && log.equals(loaded.getBlockFound()) && loaded.getDelegateAI() instanceof AIRobotGotoBlock,
                "precondition: the reloaded board resumes the flight to the log - got " + board.getDelegateAI());
        helper.assertFalse(r.registry.isTaken(logId), "precondition: the unload left the log unreserved");
        return board;
    }

    private static boolean fliesTo(BoardRobotLumberjack board, BlockPos log) {
        return board.getDelegateAI() instanceof AIRobotSearchAndGotoBlock flight && log.equals(flight.getBlockFound());
    }

    /** Robot B took the log while A was away: A's resumed flight must give it up, not land and break it. */
    public static void reloadedFlightYieldsABlockAnotherRobotTook(GameTestHelper helper) {
        try (Robots r = new Robots(helper)) {
            BlockPos log = helper.absolutePos(TARGET_REL);
            ResourceIdBlock logId = new ResourceIdBlock(log);
            BoardRobotLumberjack board = reloadMidFlight(r, log);
            helper.assertTrue(r.registry.take(logId, r.robotB), "robot B claims the log while A is away");

            board.cycle();

            helper.assertTrue(r.registry.robotIdTaking(logId) == r.robotB.getRobotId(), "robot B still holds the log");
            helper.assertFalse(fliesTo(board, log),
                    "the reloaded flight must drop a block another robot now holds - got " + board.getDelegateAI());
            helper.assertTrue(board.blockFound() == null, "...and the board must not take it up either");
            helper.succeed();
        }
    }

    /** Nobody took the log: A's resumed flight re-reserves it and flies on. */
    public static void reloadedFlightReclaimsAFreeBlock(GameTestHelper helper) {
        try (Robots r = new Robots(helper)) {
            BlockPos log = helper.absolutePos(TARGET_REL);
            ResourceIdBlock logId = new ResourceIdBlock(log);
            BoardRobotLumberjack board = reloadMidFlight(r, log);

            board.cycle();

            helper.assertTrue(r.registry.robotIdTaking(logId) == r.robotA.getRobotId(),
                    "the reloaded flight must re-reserve its block before landing on it");
            helper.assertTrue(fliesTo(board, log)
                            && board.getDelegateAI().getDelegateAI() instanceof AIRobotGotoBlock,
                    "...and keep flying to it - got " + board.getDelegateAI());
            helper.succeed();
        }
    }

    /** The board's half: a search-and-goto that reports a block it never reserved this session must not leave the
     *  board believing it holds that block. The board re-claims (here: yields) on its next cycle. */
    public static void boardDoesNotTrustAnUnreservedFlightResult(GameTestHelper helper) {
        try (Robots r = new Robots(helper)) {
            BlockPos log = helper.absolutePos(TARGET_REL);
            ResourceIdBlock logId = new ResourceIdBlock(log);
            helper.setBlock(TARGET_REL, Blocks.OAK_LOG);
            r.free(logId);

            BoardRobotLumberjack board = new BoardRobotLumberjack(r.robotA);
            r.cleanup.add(board::abortDelegateAI);
            AIRobotSearchAndGotoBlock reloadedFlight = new AIRobotSearchAndGotoBlock(r.robotA);
            CompoundTag found = new CompoundTag();
            found.putIntArray("blockFound", new int[] { log.getX(), log.getY(), log.getZ() });
            reloadedFlight.loadSelfFromNBT(found);
            helper.assertTrue(r.registry.take(logId, r.robotB), "robot B holds the log");

            board.delegateAIEnded(reloadedFlight);
            board.preempt(board.getDelegateAI());

            helper.assertTrue(r.registry.robotIdTaking(logId) == r.robotB.getRobotId(), "robot B still holds the log");
            helper.assertTrue(board.blockFound() == null,
                    "a board handed a block nobody reserved for it must check the registry, not assume");
            helper.succeed();
        }
    }

    // ---------- the flight to a station ----------

    /** Robot A reloaded while its {@link AIRobotGotoStation} flies to a station it only visits (a plain claim, not its
     *  home): the unload freed the claim, but the goto resumes its approach leg. */
    private static AIRobotGotoStation reloadMidStationFlight(Robots r, LiveStation station) {
        GameTestHelper helper = r.helper;
        station.world = helper.getLevel();
        r.registry.registerStation(station);
        r.cleanup.add(() -> r.registry.removeStation(station));

        AIRobotGotoStation before = new AIRobotGotoStation(r.robotA, station);
        before.start();
        helper.assertTrue(station.robotIdTaking() == r.robotA.getRobotId()
                        && before.getDelegateAI() instanceof AIRobotGotoBlock,
                "precondition: robot A claimed the station and flies to it - got " + before.getDelegateAI());

        AIRobotGotoStation flight = r.reload(before, AIRobotGotoStation.class);
        helper.assertTrue(flight.getDelegateAI() instanceof AIRobotGotoBlock,
                "precondition: the reloaded goto resumes its approach leg - got " + flight.getDelegateAI());
        helper.assertFalse(station.isTaken(), "precondition: the unload left the station unclaimed");
        return flight;
    }

    /** Robot B took the station while A was away: A's resumed flight must give it up, not dock beside B. */
    public static void reloadedGotoStationYieldsAStationAnotherRobotTook(GameTestHelper helper) {
        try (Robots r = new Robots(helper)) {
            LiveStation station = new LiveStation(helper.absolutePos(STATION_REL), Direction.UP);
            AIRobotGotoStation flight = reloadMidStationFlight(r, station);
            helper.assertTrue(station.take(r.robotB), "robot B claims the station while A is away");

            flight.cycle();

            helper.assertTrue(station.robotIdTaking() == r.robotB.getRobotId(), "robot B still holds the station");
            helper.assertTrue(flight.getDelegateAI() == null,
                    "the reloaded goto must stop flying to a station another robot holds - got "
                            + flight.getDelegateAI());
            helper.assertFalse(flight.success(), "...and report the flight as failed, never as docked");
            station.release(r.robotB);
            helper.succeed();
        }
    }

    /** Nobody took the station: A's resumed flight claims it again, flies on, and - being the AI that claimed it -
     *  gives it back when it is cut short. */
    public static void reloadedGotoStationReclaimsAFreeStation(GameTestHelper helper) {
        try (Robots r = new Robots(helper)) {
            LiveStation station = new LiveStation(helper.absolutePos(STATION_REL), Direction.UP);
            AIRobotGotoStation flight = reloadMidStationFlight(r, station);

            flight.cycle();

            helper.assertTrue(station.robotIdTaking() == r.robotA.getRobotId(),
                    "the reloaded goto must claim its station again before docking - unclaimed, any robot can take it");
            helper.assertTrue(flight.getDelegateAI() instanceof AIRobotGotoBlock,
                    "...and keep flying to it - got " + flight.getDelegateAI());

            flight.abort();
            helper.assertFalse(station.isTaken(), "an aborted flight gives back the claim it re-took");
            helper.succeed();
        }
    }

    // ---------- the farmer / planter / pump action ----------

    private record ActionBoard(String name, Block block, Function<EntityRobot, RedstoneBoardRobot> board,
            BiFunction<EntityRobot, BlockPos, AIRobot> action, Class<? extends AIRobot> actionType,
            Function<RedstoneBoardRobot, BlockPos> blockFound) {}

    private static final List<ActionBoard> ACTION_BOARDS = List.of(
            new ActionBoard("farmer", Blocks.DIRT, BoardRobotFarmer::new, AIRobotUseToolOnBlock::new,
                    AIRobotUseToolOnBlock.class, b -> ((BoardRobotFarmer) b).blockFound()),
            new ActionBoard("planter", Blocks.FARMLAND, BoardRobotPlanter::new, AIRobotPlant::new,
                    AIRobotPlant.class, b -> ((BoardRobotPlanter) b).blockFound()),
            new ActionBoard("pump", Blocks.WATER, BoardRobotPump::new, AIRobotPumpBlock::new,
                    AIRobotPumpBlock.class, b -> ((BoardRobotPump) b).blockFound()));

    /** Robot A's board reloaded mid-action on the target block. */
    private static RedstoneBoardRobot reloadMidAction(Robots r, ActionBoard kind, BlockPos target) {
        GameTestHelper helper = r.helper;
        helper.setBlock(TARGET_REL, kind.block());
        ResourceIdBlock targetId = new ResourceIdBlock(target);
        r.free(targetId);

        RedstoneBoardRobot before = kind.board().apply(r.robotA);
        CompoundTag self = new CompoundTag();
        self.putIntArray("blockFound", new int[] { target.getX(), target.getY(), target.getZ() });
        before.loadSelfFromNBT(self);
        helper.assertTrue(r.registry.take(targetId, r.robotA), "precondition: robot A reserved its block");
        before.startDelegateAI(kind.action().apply(r.robotA, target));

        RedstoneBoardRobot board = r.reload(before, RedstoneBoardRobot.class);
        helper.assertTrue(target.equals(kind.blockFound().apply(board))
                        && kind.actionType().isInstance(board.getDelegateAI()),
                "precondition: the reloaded " + kind.name() + " remembers its block and resumes working it - got "
                        + board.getDelegateAI());
        helper.assertFalse(r.registry.isTaken(targetId), "precondition: the unload left the block unreserved");
        return board;
    }

    /** Robot B took the block while A was away: each reloaded board must let it go, not work it alongside B. */
    public static void reloadedActionBoardsYieldABlockAnotherRobotTook(GameTestHelper helper) {
        BlockPos target = helper.absolutePos(TARGET_REL);
        ResourceIdBlock targetId = new ResourceIdBlock(target);
        for (ActionBoard kind : ACTION_BOARDS) {
            try (Robots r = new Robots(helper)) {
                RedstoneBoardRobot board = reloadMidAction(r, kind, target);
                helper.assertTrue(r.registry.take(targetId, r.robotB), "robot B claims the block while A is away");

                board.preempt(board.getDelegateAI());

                helper.assertTrue(r.registry.robotIdTaking(targetId) == r.robotB.getRobotId(),
                        kind.name() + ": robot B still holds the block");
                helper.assertTrue(kind.blockFound().apply(board) == null,
                        kind.name() + ": the reloaded board must drop a block another robot now holds");
                helper.assertFalse(kind.actionType().isInstance(board.getDelegateAI()),
                        kind.name() + ": ...and stop working it - two robots would otherwise work one block");
            }
        }
        helper.setBlock(TARGET_REL, Blocks.AIR);
        helper.succeed();
    }

    /** Robot B's SEARCH locked onto the block while A was away (the cell ledger, before B's AI collects it as a job):
     *  A's reloaded board must yield it too. A block is one robot's in either ledger; re-claiming it past a search
     *  cell would hand it to two robots at once. */
    public static void reloadedActionBoardsYieldABlockAnotherRobotsSearchHolds(GameTestHelper helper) {
        BlockPos target = helper.absolutePos(TARGET_REL);
        ResourceIdBlock targetId = new ResourceIdBlock(target);
        for (ActionBoard kind : ACTION_BOARDS) {
            try (Robots r = new Robots(helper)) {
                RedstoneBoardRobot board = reloadMidAction(r, kind, target);
                java.util.Set<BlockPos> searchOfB = r.registry.getBlockReservations(r.robotB.getRobotId());
                helper.assertTrue(searchOfB.add(target), "robot B's search locks onto the block while A is away");
                r.cleanup.add(() -> searchOfB.remove(target));

                board.preempt(board.getDelegateAI());

                helper.assertFalse(r.registry.robotIdTaking(targetId) == r.robotA.getRobotId(),
                        kind.name() + ": robot A must not take the block robot B's search holds");
                helper.assertTrue(kind.blockFound().apply(board) == null,
                        kind.name() + ": the reloaded board must drop it");
                helper.assertTrue(r.registry.getBlockReservations().contains(target),
                        kind.name() + ": ...and B's search keeps its cell");
            }
        }
        helper.setBlock(TARGET_REL, Blocks.AIR);
        helper.succeed();
    }

    /** Nobody took the block: each reloaded board re-claims it and carries on. */
    public static void reloadedActionBoardsReclaimAFreeBlock(GameTestHelper helper) {
        BlockPos target = helper.absolutePos(TARGET_REL);
        ResourceIdBlock targetId = new ResourceIdBlock(target);
        for (ActionBoard kind : ACTION_BOARDS) {
            try (Robots r = new Robots(helper)) {
                RedstoneBoardRobot board = reloadMidAction(r, kind, target);

                board.cycle();

                helper.assertTrue(r.registry.robotIdTaking(targetId) == r.robotA.getRobotId(),
                        kind.name() + ": the reloaded board must re-reserve its block - unreserved, any search can "
                                + "take it");
                helper.assertTrue(target.equals(kind.blockFound().apply(board))
                                && kind.actionType().isInstance(board.getDelegateAI()),
                        kind.name() + ": the board keeps its block and keeps working it");
            }
        }
        helper.setBlock(TARGET_REL, Blocks.AIR);
        helper.succeed();
    }

    // ---------- the delivery request ----------

    /** Robot A's delivery board reloaded while it delivers an order from a registered station. */
    private static BoardRobotDelivery reloadMidDelivery(Robots r, ResourceId[] requestId) {
        GameTestHelper helper = r.helper;
        LiveStation station = new LiveStation(helper.absolutePos(STATION_REL), Direction.UP);
        station.world = helper.getLevel();
        r.registry.registerStation(station);
        r.cleanup.add(() -> r.registry.removeStation(station));

        StackRequest request = new StackRequest(null, 0, new ItemStack(Items.DIAMOND, 3));
        request.setStation(station);
        requestId[0] = request.getResourceId(helper.getLevel());
        r.free(requestId[0]);

        BoardRobotDelivery before = new BoardRobotDelivery(r.robotA);
        CompoundTag self = new CompoundTag();
        CompoundTag requestNbt = new CompoundTag();
        request.writeToNBT(requestNbt);
        self.put("currentRequest", requestNbt);
        before.loadSelfFromNBT(self);
        helper.assertTrue(r.registry.take(requestId[0], r.robotA), "precondition: robot A reserved the order");
        before.startDelegateAI(new AIRobotDeliverRequested(r.robotA, request));

        BoardRobotDelivery board = r.reload(before, BoardRobotDelivery.class);
        helper.assertTrue(board.currentRequest() != null && board.getDelegateAI() instanceof AIRobotDeliverRequested,
                "precondition: the reloaded board remembers the order and resumes delivering it - got "
                        + board.getDelegateAI());
        helper.assertFalse(r.registry.isTaken(requestId[0]), "precondition: the unload left the order unreserved");
        return board;
    }

    /** Robot B took the order while A was away: A must not deliver it too. */
    public static void reloadedDeliveryYieldsAnOrderAnotherRobotTook(GameTestHelper helper) {
        try (Robots r = new Robots(helper)) {
            ResourceId[] requestId = new ResourceId[1];
            BoardRobotDelivery board = reloadMidDelivery(r, requestId);
            helper.assertTrue(r.registry.take(requestId[0], r.robotB), "robot B claims the order while A is away");

            board.preempt(board.getDelegateAI());

            helper.assertTrue(r.registry.robotIdTaking(requestId[0]) == r.robotB.getRobotId(),
                    "robot B still holds the order");
            helper.assertTrue(board.currentRequest() == null,
                    "the reloaded board must drop an order another robot now holds");
            helper.assertFalse(board.getDelegateAI() instanceof AIRobotDeliverRequested,
                    "...and stop delivering it - two robots would otherwise fill one order");
            helper.succeed();
        }
    }

    /** Nobody took the order: A re-claims it and carries on delivering. */
    public static void reloadedDeliveryReclaimsAFreeOrder(GameTestHelper helper) {
        try (Robots r = new Robots(helper)) {
            ResourceId[] requestId = new ResourceId[1];
            BoardRobotDelivery board = reloadMidDelivery(r, requestId);

            board.cycle();

            helper.assertTrue(r.registry.robotIdTaking(requestId[0]) == r.robotA.getRobotId(),
                    "the reloaded board must re-reserve its order - unreserved, any other robot's search can take it");
            helper.assertTrue(board.currentRequest() != null && board.getDelegateAI() instanceof AIRobotDeliverRequested,
                    "the board keeps its order and keeps delivering it");
            helper.succeed();
        }
    }
    /** A delivery that ends with the robot docked at the requester's station (the delivery flight docks it there):
     *  the board gives the order back, but the station is the robot's dock now, so it stays claimed until the robot
     *  undocks - as {@code AIRobotGotoStation.end()} leaves it. Released here, a second robot could claim and dock at
     *  a station the first one still sits on. */
    public static void deliveryEndedWhileDockedKeepsTheDock(GameTestHelper helper) {
        try (Robots r = new Robots(helper)) {
            LiveStation station = new LiveStation(helper.absolutePos(STATION_REL), Direction.UP);
            station.world = helper.getLevel();
            r.registry.registerStation(station);
            r.cleanup.add(() -> r.registry.removeStation(station));
            r.cleanup.add(r.robotA::undock);

            StackRequest request = new StackRequest(null, 0, new ItemStack(Items.DIAMOND, 3));
            request.setStation(station);
            ResourceId requestId = request.getResourceId(helper.getLevel());
            r.free(requestId);

            BoardRobotDelivery board = new BoardRobotDelivery(r.robotA);
            CompoundTag self = new CompoundTag();
            CompoundTag requestNbt = new CompoundTag();
            request.writeToNBT(requestNbt);
            self.put("currentRequest", requestNbt);
            board.loadSelfFromNBT(self);
            helper.assertTrue(r.registry.take(requestId, r.robotA), "precondition: robot A reserved the order");
            helper.assertTrue(station.take(r.robotA), "precondition: robot A claimed the requester's station");
            r.robotA.dock(station);

            board.delegateAIEnded(new AIRobotDeliverRequested(r.robotA, request));

            helper.assertTrue(board.currentRequest() == null && !r.registry.isTaken(requestId),
                    "the finished delivery gives its order back");
            helper.assertTrue(station.robotIdTaking() == r.robotA.getRobotId() && r.robotA.getDockingStation() == station,
                    "...but not the station robot A is docked at");
            r.robotA.undock();
            helper.assertFalse(station.isTaken(), "undocking is what frees it");
            helper.succeed();
        }
    }

    /** The recharge-aborted board with nobody contending: the recharge released the order, and when the main AI
     *  resumes the same board instance it takes the order (and the requester's station) back and carries on with
     *  it, as 7.1.x's board kept its order across a recharge. */
    public static void deliveryAbortedByARechargeRetakesAFreeOrder(GameTestHelper helper) {
        try (Robots r = new Robots(helper)) {
            LiveStation station = new LiveStation(helper.absolutePos(STATION_REL), Direction.UP);
            station.world = helper.getLevel();
            r.registry.registerStation(station);
            r.cleanup.add(() -> r.registry.removeStation(station));

            StackRequest request = new StackRequest(null, 0, new ItemStack(Items.DIAMOND, 3));
            request.setStation(station);
            ResourceId requestId = request.getResourceId(helper.getLevel());
            r.free(requestId);

            BoardRobotDelivery board = new BoardRobotDelivery(r.robotA);
            CompoundTag self = new CompoundTag();
            CompoundTag requestNbt = new CompoundTag();
            request.writeToNBT(requestNbt);
            self.put("currentRequest", requestNbt);
            board.loadSelfFromNBT(self);
            helper.assertTrue(r.registry.take(requestId, r.robotA), "precondition: robot A reserved the order");

            helper.assertTrue(station.take(r.robotA), "precondition: robot A claimed the requester's station");

            AIRobotMain main = new AIRobotMain(r.robotA);
            r.cleanup.add(main::abortDelegateAI);
            main.startDelegateAI(board);
            board.startDelegateAI(new AIRobotDeliverRequested(r.robotA, request));
            board.preempt(board.getDelegateAI());

            // The recharge (see deliveryAbortedByARechargeGivesUpItsOrder): abort the board, release everything.
            main.abortDelegateAI();
            r.registry.releaseResources(r.robotA);
            helper.assertFalse(r.registry.isTaken(requestId) || station.isTaken(),
                    "precondition: the recharge released the order and the station");

            main.startDelegateAI(board);
            board.preempt(board.getDelegateAI());

            helper.assertTrue(r.registry.robotIdTaking(requestId) == r.robotA.getRobotId(),
                    "the resumed board takes its free order back - got holder " + r.registry.robotIdTaking(requestId));
            helper.assertTrue(board.currentRequest() != null, "...and keeps working it");
            helper.assertTrue(station.robotIdTaking() == r.robotA.getRobotId(),
                    "...with the requester's station claimed again - got " + station);
            helper.succeed();
        }
    }

    /** A delivery cut short by a recharge: {@code AIRobotMain} aborts the board to start {@link AIRobotRecharge},
     *  whose start frees every reservation the robot holds - the order included. While robot A charges, robot B
     *  takes the order. When A's main AI resumes the same board instance, it must not go back to delivering an
     *  order it no longer holds: the order would be filled twice. */
    public static void deliveryAbortedByARechargeGivesUpItsOrder(GameTestHelper helper) {
        try (Robots r = new Robots(helper)) {
            LiveStation station = new LiveStation(helper.absolutePos(STATION_REL), Direction.UP);
            station.world = helper.getLevel();
            r.registry.registerStation(station);
            r.cleanup.add(() -> r.registry.removeStation(station));

            StackRequest request = new StackRequest(null, 0, new ItemStack(Items.DIAMOND, 3));
            request.setStation(station);
            ResourceId requestId = request.getResourceId(helper.getLevel());
            r.free(requestId);

            BoardRobotDelivery board = new BoardRobotDelivery(r.robotA);
            CompoundTag self = new CompoundTag();
            CompoundTag requestNbt = new CompoundTag();
            request.writeToNBT(requestNbt);
            self.put("currentRequest", requestNbt);
            board.loadSelfFromNBT(self);
            helper.assertTrue(r.registry.take(requestId, r.robotA), "precondition: robot A reserved the order");

            AIRobotMain main = new AIRobotMain(r.robotA);
            r.cleanup.add(main::abortDelegateAI);
            main.startDelegateAI(board);
            board.startDelegateAI(new AIRobotDeliverRequested(r.robotA, request));
            board.preempt(board.getDelegateAI());
            helper.assertTrue(board.currentRequest() != null && board.getDelegateAI() instanceof AIRobotDeliverRequested
                            && r.registry.robotIdTaking(requestId) == r.robotA.getRobotId(),
                    "precondition: robot A is delivering the order it holds - got " + board.getDelegateAI());

            // What AIRobotMain.startDelegateAI(new AIRobotRecharge(robot)) does, minus the recharge's own station
            // search (it would reserve a sibling test's charger): abort the board, then the recharge's start
            // releases everything the robot holds.
            main.abortDelegateAI();
            r.registry.releaseResources(r.robotA);
            helper.assertTrue(r.registry.take(requestId, r.robotB), "robot B claims the order while A recharges");

            // The recharge is over: AIRobotMain.update() hands control back to the same board instance.
            main.startDelegateAI(board);
            board.preempt(board.getDelegateAI());

            helper.assertTrue(r.registry.robotIdTaking(requestId) == r.robotB.getRobotId(),
                    "robot B still holds the order");
            helper.assertTrue(board.currentRequest() == null,
                    "the resumed board must not keep an order its recharge released - it would deliver it a "
                            + "second time alongside robot B");
            helper.succeed();
        }
    }
}
