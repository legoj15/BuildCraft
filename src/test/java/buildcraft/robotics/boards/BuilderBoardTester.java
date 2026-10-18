/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.robotics.boards;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.core.IZone;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.EntityRobotBase;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.api.robots.IRobotRegistry;
import buildcraft.api.statements.StatementSlot;
import buildcraft.api.schematics.ISchematicBlock;
import buildcraft.api.schematics.SchematicBlockContext;
import buildcraft.builders.BCBuildersBlocks;
import buildcraft.builders.BCBuildersItems;
import buildcraft.builders.snapshot.Blueprint;
import buildcraft.builders.snapshot.BlueprintBuilder;
import buildcraft.builders.snapshot.GlobalSavedDataSnapshots;
import buildcraft.builders.snapshot.SchematicBlockManager;
import buildcraft.builders.snapshot.Snapshot;
import buildcraft.builders.tile.TileBuilder;
import buildcraft.core.BCCoreStatements;
import buildcraft.lib.test.EntityArenaUtil;
import buildcraft.robotics.BCRoboticsEntities;
import buildcraft.robotics.BCRoboticsStatements;
import buildcraft.robotics.RobotStationPluggable;
import buildcraft.robotics.ZonePinnedRobot;
import buildcraft.robotics.ai.AIRobotDisposeItems;
import buildcraft.robotics.ai.AIRobotGotoBlock;
import buildcraft.robotics.ai.AIRobotGotoSleep;
import buildcraft.robotics.ai.AIRobotGotoStationAndLoad;
import buildcraft.robotics.ai.AIRobotRecharge;
import buildcraft.robotics.ai.MockRobotAccess;
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

import buildcraft.lib.statement.ActionWrapper;
import buildcraft.lib.statement.TriggerWrapper;

/** Ph9's Builder-board tests. The rewritten board supplies Builder MACHINES (7.1.x fed construction
 *  markers, which no longer exist): find the closest needy one inside the flight's reach, fetch its
 *  first missing stack, fly there, insert into its resource inventory.
 *
 *  <p>Two tests: a synchronous state-machine walk (which delegate AI each situation starts — the
 *  sleep-when-nothing-needs, fetch-when-empty, energy-gate-when-low, deliver-when-carrying decisions),
 *  and the full autonomous E2E through the live tick loop (chest → wooden supply pipe → station →
 *  builder board robot → Builder machine's resource grid). The blueprint is built programmatically —
 *  one stone cell — exactly the way the Architect Table's scanner would have captured it. */
public class BuilderBoardTester {

    private static final long SEEDED_CHARGE = 5000L * MjAPI.MJ;

    // ---------- blueprint fixture ----------

    /** A one-cell stone blueprint: the palette entry a real scan would produce for the stone the arena
     *  places, data pointing at it, registered in the level's snapshot store, and the used-blueprint
     *  item stack that hands it to a Builder machine. */
    private static ItemStack makeSingleStoneBlueprint(ServerLevel level, BlockPos samplePos) {
        return makeSingleBlockBlueprint(level, samplePos, Blocks.STONE);
    }

    /** {@link #makeSingleStoneBlueprint} for any plain block: the item it needs is that block's own. */
    private static ItemStack makeSingleBlockBlueprint(ServerLevel level, BlockPos samplePos, Block block) {
        BlockState stone = block.defaultBlockState();
        ISchematicBlock schematic = SchematicBlockManager.getSchematicBlock(
                new SchematicBlockContext(level, samplePos, samplePos, stone, stone.getBlock()));
        if (schematic == null) {
            throw new IllegalStateException("the default schematic must resolve for plain " + block);
        }

        Blueprint blueprint = new Blueprint();
        blueprint.size = new BlockPos(1, 1, 1);
        blueprint.facing = Direction.EAST;
        blueprint.offset = BlockPos.ZERO;
        blueprint.palette.add(schematic);
        blueprint.data = new int[] { 0 };
        blueprint.computeKey();
        GlobalSavedDataSnapshots.get(level).addSnapshot(blueprint);

        return BCBuildersItems.BLUEPRINT_USED.get().createUsedStack(
                new Snapshot.Header(blueprint.key, new UUID(0, 0), new Date(0), "ph9-test"));
    }

    /** Places a Builder machine facing EAST — so its single build cell (the position in front, where
     *  {@code FACING.getOpposite()} points) is one block WEST of the machine — and optionally loads the
     *  given snapshot item. */
    private static TileBuilder placeBuilder(GameTestHelper helper, BlockPos relPos, ItemStack snapshot) {
        helper.setBlock(relPos, BCBuildersBlocks.BUILDER.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
        //? if >=1.21.10 {
        TileBuilder tile = helper.getBlockEntity(relPos, TileBuilder.class);
        //?} else {
        /*TileBuilder tile = helper.getBlockEntity(relPos);*/
        //?}
        if (snapshot != null) {
            tile.setSnapshot(snapshot);
            // TWO manual ticks: the first runs the check sweep that classifies the lone cell TO_PLACE;
            // BlueprintBuilder.tick assembles remainingRequiredItems from the PREVIOUS tick's sweep
            // results, so the list only becomes visible on the second — that ordering is the machine's
            // own, not the test's.
            tile.tick();
            tile.tick();
        }
        return tile;
    }

    private static EntityRobot unaddedRobot(GameTestHelper helper, BlockPos relPos) {
        EntityRobot robot = new EntityRobot(BCRoboticsEntities.ROBOT.get(), helper.getLevel());
        Vec3 pos = Vec3.atCenterOf(helper.absolutePos(relPos));
        robot.setPos(pos.x, pos.y, pos.z);
        robot.getBattery().addPower(SEEDED_CHARGE, false);
        return robot;
    }

    // ---------- the state machine ----------

    /** Each situation's delegate AI, hand-driven exactly as {@code AIRobotMain} would call
     *  {@code board.update()}:
     *  <ul>
     *  <li>a snapshot-less Builder reports no demand ({@code needsItems == false} — asserted against the
     *      TILE rather than through a board update, because the shared game-test level may hold other
     *      tests' needy builders and the board searches every loaded one);</li>
     *  <li>a needy Builder and an empty hold → fetch the missing stack ({@code AIRobotGotoStationAndLoad});</li>
     *  <li>cargo aboard but the battery at/below {@code SAFETY_POWER} → the energy gate → recharge;</li>
     *  <li>cargo aboard and charge healthy → the delivery flight ({@code AIRobotGotoBlock}).</li>
     *  </ul>
     *  Phases 1–3 are race-safe: whichever needy builder the search finds, the delegate type is the
     *  same. The search radius is pinned by {@link #builderBoardSkipsBuilderBeyondFlightReach}. */
    public static void builderBoardStateMachine(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos idleBuilderRel = new BlockPos(1, 2, 2);
        BlockPos needyBuilderRel = new BlockPos(4, 2, 2);

        // Phase 0 — a snapshot-less machine reports no demand at all.
        TileBuilder idleBuilder = placeBuilder(helper, idleBuilderRel, null);
        helper.assertTrue(!BoardRobotBuilder.needsItems(idleBuilder),
                "a snapshot-less builder needs nothing — the board must skip it");

        // Phase 1 — a needy machine: the empty robot starts a fetch.
        TileBuilder needyBuilder = placeBuilder(helper, needyBuilderRel,
                makeSingleStoneBlueprint(level, helper.absolutePos(new BlockPos(2, 1, 2))));
        helper.assertTrue(BoardRobotBuilder.needsItems(needyBuilder),
                "a builder whose blueprint misses its stone reports demand");
        helper.assertTrue(needyBuilder.getBuilder() instanceof BlueprintBuilder,
                "sanity: the machine resolves a blueprint builder");

        EntityRobot robot = unaddedRobot(helper, new BlockPos(3, 2, 2));
        BoardRobotBuilder board = new BoardRobotBuilder(robot);
        board.update();
        AIRobot delegate = board.getDelegateAI();
        helper.assertTrue(delegate instanceof AIRobotGotoStationAndLoad,
                "an empty robot fetches the missing stone, got " + delegate);

        // Phase 2 — cargo aboard, battery drained to the safety floor: the energy gate sends it home.
        robot.setInventoryStack(0, new ItemStack(Items.STONE));
        robot.getBattery().extractPower(SEEDED_CHARGE - IRobotAccess.SAFETY_POWER,
                SEEDED_CHARGE - IRobotAccess.SAFETY_POWER);
        helper.assertTrue(robot.getPower() == IRobotAccess.SAFETY_POWER,
                "sanity: the battery sits exactly at the safety floor");
        board.update();
        delegate = board.getDelegateAI();
        // The recharge chain unwinds SYNCHRONOUSLY here (AIRobotSearchAndGotoStation finds no power
        // station in an empty arena and terminates inside start()). The board answers the failed
        // recharge by going home to sleep rather than leaving itself idle, which would start a fresh
        // recharge every tick (builderBoardSleepsWhenNoPowerStationIsFree pins that). The energy gate
        // HELD either way: the low-battery robot did not start the delivery flight.
        helper.assertTrue(delegate instanceof AIRobotGotoSleep || delegate instanceof AIRobotRecharge,
                "a loaded robot at/below SAFETY_POWER recharges (failing over to sleep with no power "
                        + "station), never flies, got " + delegate);

        // Phase 3 — battery healthy again: the delivery flight.
        robot.getBattery().addPower(SEEDED_CHARGE, false);
        board.update();
        delegate = board.getDelegateAI();
        helper.assertTrue(delegate instanceof AIRobotGotoBlock,
                "a loaded, charged robot flies the delivery leg, got " + delegate);

        helper.succeed();
    }

    // ---------- delivery failures ----------

    /** A robot isolated from the shared test level: its work zone is pinned to one machine
     *  ({@code getZoneToWork()} normally comes from the linked station's gate actions; the board searches
     *  every Builder loaded in the level, and other tests leave needy ones behind) and its registry is an
     *  empty stub (concurrent tests register stations in the real one, and a power-providing station
     *  anywhere would turn "no free power station" into a long flight). */
    private static final class ZonedRobot extends EntityRobot {
        private final IZone zone;
        private IRobotRegistry registry = new MockRobotAccess.InertRobotRegistry();

        ZonedRobot(ServerLevel level, IZone zone) {
            super(BCRoboticsEntities.ROBOT.get(), level);
            this.zone = zone;
        }

        @Override
        public IZone getZoneToWork() {
            return zone;
        }

        /** Stations are not zone-limited (the work zone would otherwise stand in for the load zone, and it
         *  names one machine). */
        @Override
        public IZone getZoneToLoadUnload() {
            return null;
        }

        @Override
        public IRobotRegistry getRegistry() {
            return registry;
        }

        void setRegistry(IRobotRegistry registry) {
            this.registry = registry;
        }
    }

    /** A zone that contains exactly one block's centre. */
    private static IZone justThisBlock(BlockPos block) {
        Vec3 centre = Vec3.atCenterOf(block);
        return new IZone() {
            @Override
            public double distanceTo(BlockPos pos) {
                return Math.sqrt(distanceToSquared(pos));
            }

            @Override
            public double distanceToSquared(BlockPos pos) {
                return Vec3.atCenterOf(pos).distanceToSqr(centre);
            }

            @Override
            public boolean contains(Vec3 point) {
                return point.distanceToSqr(centre) < 0.25;
            }

            @Override
            public BlockPos getRandomBlockPos(Random rand) {
                return block;
            }
        };
    }

    /** A zone that contains exactly these blocks' centres. */
    private static IZone justTheseBlocks(BlockPos... blocks) {
        List<IZone> parts = new ArrayList<>();
        for (BlockPos block : blocks) {
            parts.add(justThisBlock(block));
        }
        return new IZone() {
            @Override
            public double distanceTo(BlockPos pos) {
                return Math.sqrt(distanceToSquared(pos));
            }

            @Override
            public double distanceToSquared(BlockPos pos) {
                double best = Double.MAX_VALUE;
                for (IZone part : parts) {
                    best = Math.min(best, part.distanceToSquared(pos));
                }
                return best;
            }

            @Override
            public boolean contains(Vec3 point) {
                for (IZone part : parts) {
                    if (part.contains(point)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public BlockPos getRandomBlockPos(Random rand) {
                return blocks[rand.nextInt(blocks.length)];
            }
        };
    }

    private static ZonedRobot zonedRobot(GameTestHelper helper, BlockPos robotRel, BlockPos zoneBlock) {
        return zonedRobot(helper, robotRel, justThisBlock(zoneBlock));
    }

    private static ZonedRobot zonedRobot(GameTestHelper helper, BlockPos robotRel, IZone zone) {
        ZonedRobot robot = new ZonedRobot(helper.getLevel(), zone);
        Vec3 pos = Vec3.atCenterOf(helper.absolutePos(robotRel));
        robot.setPos(pos.x, pos.y, pos.z);
        robot.getBattery().addPower(SEEDED_CHARGE, false);
        return robot;
    }

    private static boolean builderGridIsEmpty(TileBuilder builder) {
        for (int slot = 0; slot < TileBuilder.RESOURCE_SLOTS; slot++) {
            if (!builder.getResource(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static boolean gridHas(TileBuilder builder, Item item) {
        for (int slot = 0; slot < TileBuilder.RESOURCE_SLOTS; slot++) {
            if (builder.getResource(slot).is(item)) {
                return true;
            }
        }
        return false;
    }

    /** Takes a placed Builder away without leaving anything for the arena sweep: its resource grid, snapshot item
     *  and block all go (removing the block drops what the machine holds). */
    private static void retire(GameTestHelper helper, TileBuilder builder, BlockPos rel) {
        for (int slot = 0; slot < TileBuilder.RESOURCE_SLOTS; slot++) {
            builder.setResource(slot, ItemStack.EMPTY);
        }
        builder.setSnapshot(ItemStack.EMPTY);
        helper.setBlock(rel, Blocks.AIR);
    }

    /** How many of {@code item} the robot carries. */
    private static int carried(IRobotAccess robot, Item item) {
        int count = 0;
        for (int slot = 0; slot < robot.getInventorySize(); slot++) {
            ItemStack stack = robot.getInventoryStack(slot);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** How many of {@code item} lie on the ground within a block of {@code centre}; with {@code discard} they are
     *  also removed (the board's dispose drops cargo it cannot unload, right at the robot, and a leaked item entity
     *  fails other tests). The radius is small on purpose: arena cells are 6 blocks apart and a neighbour's items
     *  are not ours to count or remove. */
    private static int groundCount(ServerLevel level, Vec3 centre, Item item, boolean discard) {
        int count = 0;
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, new AABB(centre, centre).inflate(1))) {
            if (entity.getItem().is(item)) {
                count += entity.getItem().getCount();
                if (discard) {
                    entity.discard();
                }
            }
        }
        return count;
    }

    /** The delivery flight FAILS (the robot sits in a sealed stone shell far enough from the machine that
     *  the arrival radius does not already count: the pathfinder exhausts and reports no path). The old
     *  board ignored the verdict and inserted the cargo into the machine the robot never reached — the
     *  items teleported, while a robot that cannot get anywhere should still be carrying them. Now the
     *  cargo stays aboard, nothing reaches the machine, and the unreachable machine is skipped for a
     *  retry window (so the next pass does not instantly send the same doomed flight again) that then
     *  expires. */
    public static void builderBoardKeepsCargoWhenTheFlightFails(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos builderRel = new BlockPos(4, 2, 2);
        BlockPos cellRel = new BlockPos(2, 14, 2);

        TileBuilder builder = placeBuilder(helper, builderRel,
                makeSingleStoneBlueprint(level, helper.absolutePos(new BlockPos(3, 1, 2))));
        helper.assertTrue(BoardRobotBuilder.needsItems(builder), "sanity: the machine reports demand");

        // The full 3x3x3 shell around the robot's cell, minus the cell itself.
        List<BlockPos> shell = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dy != 0 || dz != 0) {
                        shell.add(cellRel.offset(dx, dy, dz));
                    }
                }
            }
        }
        try {
            for (BlockPos rel : shell) {
                helper.setBlock(rel, Blocks.STONE);
            }

            ZonedRobot robot = zonedRobot(helper, cellRel, helper.absolutePos(builderRel));
            robot.setInventoryStack(0, new ItemStack(Items.STONE, 4));
            BoardRobotBuilder board = new BoardRobotBuilder(robot);

            board.update();
            helper.assertTrue(board.getDelegateAI() instanceof AIRobotGotoBlock,
                    "a loaded, charged robot starts the delivery flight, got " + board.getDelegateAI());

            // Hand-cycle exactly as AIRobotMain would. The sealed pathfinder gives up within a few cycles.
            int cycles = 0;
            while (board.getDelegateAI() != null && cycles < 200) {
                board.cycle();
                cycles++;
            }
            helper.assertTrue(board.getDelegateAI() == null,
                    "the flight must have ended (failed) within 200 cycles");

            helper.assertTrue(builderGridIsEmpty(builder),
                    "the flight failed — nothing may be inserted into the machine the robot never reached");
            int aboard = 0;
            for (int slot = 0; slot < robot.getInventorySize(); slot++) {
                ItemStack stack = robot.getInventoryStack(slot);
                if (stack.is(Items.STONE)) {
                    aboard += stack.getCount();
                }
            }
            helper.assertTrue(aboard == 4, "the whole cargo is still aboard, got " + aboard);

            // The NEXT pass: the only needy machine is benched, so the search comes back empty while the
            // cargo is still aboard. That must not read as "the need vanished" (dispose, which drops the
            // stack on the ground when no station accepts it) — the robot sleeps with the cargo and retries
            // once the bench window has passed.
            board.cycle();
            helper.assertTrue(board.getDelegateAI() instanceof AIRobotGotoSleep,
                    "with the only candidate benched the robot sleeps with its cargo, got "
                            + board.getDelegateAI());
            for (int i = 0; i < 40; i++) {
                board.cycle();
                helper.assertFalse(board.getDelegateAI() instanceof AIRobotDisposeItems,
                        "the benched machine's cargo must never be disposed of");
            }
            int stillAboard = 0;
            for (int slot = 0; slot < robot.getInventorySize(); slot++) {
                ItemStack stack = robot.getInventoryStack(slot);
                if (stack.is(Items.STONE)) {
                    stillAboard += stack.getCount();
                }
            }
            helper.assertTrue(stillAboard == 4,
                    "the cargo is still aboard after the following passes, got " + stillAboard);

            long now = level.getGameTime();
            helper.assertTrue(board.isSkipped(builder, now),
                    "the unreachable machine is skipped right after the failure");
            helper.assertTrue(board.isSkipped(builder, now + BoardRobotBuilder.UNREACHABLE_RETRY_TICKS - 1),
                    "…for the whole retry window");
            helper.assertFalse(board.isSkipped(builder, now + BoardRobotBuilder.UNREACHABLE_RETRY_TICKS),
                    "…and no longer once the window has passed");
        } finally {
            for (BlockPos rel : shell) {
                helper.setBlock(rel, Blocks.AIR);
            }
            helper.setBlock(builderRel, Blocks.AIR);
        }
        helper.succeed();
    }

    /** Cargo aboard, battery at the safety floor, and no free power station anywhere: the recharge fails
     *  at once. The board used to ignore that and, being idle, start another doomed recharge on the very
     *  next tick, forever. It now goes home to sleep, like every other failure path, and stays asleep
     *  rather than re-running the search each cycle. */
    public static void builderBoardSleepsWhenNoPowerStationIsFree(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos builderRel = new BlockPos(4, 2, 2);
        BlockPos robotRel = new BlockPos(2, 4, 2);

        try {
            placeBuilder(helper, builderRel,
                    makeSingleStoneBlueprint(level, helper.absolutePos(new BlockPos(3, 1, 2))));

            ZonedRobot robot = zonedRobot(helper, robotRel, helper.absolutePos(builderRel));
            robot.setInventoryStack(0, new ItemStack(Items.STONE));
            robot.getBattery().extractPower(SEEDED_CHARGE - IRobotAccess.SAFETY_POWER,
                    SEEDED_CHARGE - IRobotAccess.SAFETY_POWER);
            helper.assertTrue(robot.getPower() <= IRobotAccess.SAFETY_POWER,
                    "sanity: the battery sits at the safety floor");
            BoardRobotBuilder board = new BoardRobotBuilder(robot);

            // Hand-cycle as AIRobotMain would. With the stub registry the recharge search fails inside
            // its own start(), so the old board was left with NO delegate after every cycle — and, being
            // idle, started the same doomed recharge again on the next one, forever. The fixed board
            // hands the failure to a sleep, which then simply runs.
            board.cycle();
            AIRobot sleeper = board.getDelegateAI();
            helper.assertTrue(sleeper instanceof AIRobotGotoSleep,
                    "a failed recharge hands over to sleep instead of leaving the board idle, got "
                            + sleeper);
            for (int i = 0; i < 60; i++) {
                board.cycle();
                helper.assertTrue(board.getDelegateAI() == sleeper,
                        "the board keeps sleeping — it must not start another recharge, got "
                                + board.getDelegateAI());
            }
        } finally {
            helper.setBlock(builderRel, Blocks.AIR);
        }
        helper.succeed();
    }

    /** The board must not pick a Builder its own delivery flight cannot reach. 7.1.x searched three 64-block
     *  radii (about 111 blocks) while {@code AIRobotGotoBlock} rejects any target past
     *  {@link AIRobotGotoBlock#FLIGHT_REACH} (96) from the robot's block: a robot at a station 100 blocks from
     *  the only needy machine picked it, failed the flight at once, benched it, slept and repeated forever with
     *  the cargo aboard. The candidate test now uses the flight's reach — inclusive at exactly 96 blocks,
     *  out at 97 and at 100, with the benched-inclusive search (the sleep-with-cargo check) agreeing.
     *
     *  <p>The robot is never added to the level, so standing it 100 blocks outside the arena touches no
     *  other test's cell; only the finder runs (no delegate AI starts, nothing moves or drops). */
    public static void builderBoardSkipsBuilderBeyondFlightReach(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos builderRel = new BlockPos(4, 2, 2);

        try {
            TileBuilder builder = placeBuilder(helper, builderRel,
                    makeSingleStoneBlueprint(level, helper.absolutePos(new BlockPos(3, 1, 2))));
            helper.assertTrue(BoardRobotBuilder.needsItems(builder), "sanity: the machine reports demand");
            BlockPos builderAbs = helper.absolutePos(builderRel);

            ZonedRobot robot = new ZonedRobot(level, justThisBlock(builderAbs));
            robot.getBattery().addPower(SEEDED_CHARGE, false);
            BoardRobotBuilder board = new BoardRobotBuilder(robot);

            int[] reachable = { 12, AIRobotGotoBlock.FLIGHT_REACH };
            int[] outOfReach = { AIRobotGotoBlock.FLIGHT_REACH + 1, 100, 110 };
            for (int offset : reachable) {
                Vec3 pos = Vec3.atCenterOf(builderAbs.offset(offset, 0, 0));
                robot.setPos(pos.x, pos.y, pos.z);
                helper.assertTrue(board.findClosestBuilder(false) == builder,
                        "a needy machine " + offset + " blocks away is inside the flight's reach");
            }
            for (int offset : outOfReach) {
                Vec3 pos = Vec3.atCenterOf(builderAbs.offset(offset, 0, 0));
                robot.setPos(pos.x, pos.y, pos.z);
                helper.assertTrue(board.findClosestBuilder(false) == null,
                        "a needy machine " + offset + " blocks away is beyond the flight's reach — the board"
                                + " must not pick it");
                helper.assertTrue(board.findClosestBuilder(true) == null,
                        "…nor count it as a benched need worth keeping cargo for (" + offset + " blocks)");
            }
        } finally {
            helper.setBlock(builderRel, Blocks.AIR);
        }
        helper.succeed();
    }

    // ---------- cargo that no longer matches the need ----------

    /** One tick of a flight, by hand: the board's cycle, then the robot moves by the velocity the flight set (the
     *  robots in these tests are never added to the level, so nothing else ever moves them). */
    private static void flyOneTick(BoardRobotBuilder board, EntityRobot robot) {
        board.cycle();
        robot.setPos(robot.position().add(robot.getDeltaMovement()));
    }

    /** The robot loaded stone for Builder X; X stopped needing it before the robot arrived, and the only Builder
     *  still needy (Y) wants dirt. The board used to re-pick Y by "needs anything" alone, fly the stone over, and
     *  empty it into Y's resource grid on arrival. It must pick only a Builder whose requirements accept something
     *  the robot carries: with none, the cargo is returned (disposed of), never delivered to a machine that did not
     *  ask for it. */
    public static void builderBoardDoesNotDumpCargoIntoABuilderThatWantsSomethingElse(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos stoneRel = new BlockPos(4, 2, 2);
        BlockPos dirtRel = new BlockPos(1, 2, 2);
        BlockPos robotRel = new BlockPos(3, 4, 2);
        TileBuilder stoneBuilder = placeBuilder(helper, stoneRel,
                makeSingleBlockBlueprint(level, helper.absolutePos(new BlockPos(3, 1, 2)), Blocks.STONE));
        TileBuilder dirtBuilder = placeBuilder(helper, dirtRel,
                makeSingleBlockBlueprint(level, helper.absolutePos(new BlockPos(0, 1, 2)), Blocks.DIRT));
        ZonedRobot robot = zonedRobot(helper, robotRel,
                justTheseBlocks(helper.absolutePos(stoneRel), helper.absolutePos(dirtRel)));
        try {
            helper.assertTrue(BoardRobotBuilder.needsItems(stoneBuilder) && BoardRobotBuilder.needsItems(dirtBuilder),
                    "sanity: both machines report demand");
            helper.assertTrue(((BlueprintBuilder) stoneBuilder.getBuilder()).remainingRequiredItems.get(0)
                    .is(Items.STONE), "sanity: the first machine wants stone");
            helper.assertTrue(((BlueprintBuilder) dirtBuilder.getBuilder()).remainingRequiredItems.get(0)
                    .is(Items.DIRT), "sanity: the second machine wants dirt");

            robot.setInventoryStack(0, new ItemStack(Items.STONE, 4));
            BoardRobotBuilder board = new BoardRobotBuilder(robot);

            board.cycle();
            helper.assertTrue(board.getDelegateAI() instanceof AIRobotGotoBlock,
                    "the stone goes to the machine that wants stone, got " + board.getDelegateAI());

            // The stone machine loses its need on the way (its blueprint is taken out).
            stoneBuilder.setSnapshot(ItemStack.EMPTY);
            helper.assertFalse(BoardRobotBuilder.needsItems(stoneBuilder),
                    "sanity: the stone machine needs nothing now");

            // Hand-cycle as AIRobotMain would until the robot is empty one way or another.
            int cycles = 0;
            while (robot.containsItems() && cycles < 600) {
                flyOneTick(board, robot);
                cycles++;
            }

            helper.assertFalse(gridHas(dirtBuilder, Items.STONE),
                    "stone must never be emptied into a machine that wants dirt");
            helper.assertTrue(builderGridIsEmpty(dirtBuilder) && builderGridIsEmpty(stoneBuilder),
                    "neither machine received anything");
            helper.assertFalse(robot.containsItems(),
                    "the cargo nobody wants is returned, not carried around forever (" + cycles + " cycles)");
            helper.assertTrue(groundCount(level, robot.position(), Items.STONE, true) == 4,
                    "the returned cargo is all there (nothing lost, nothing delivered)");
        } finally {
            groundCount(level, robot.position(), Items.STONE, true);
            retire(helper, stoneBuilder, stoneRel);
            retire(helper, dirtBuilder, dirtRel);
        }
        helper.succeed();
    }

    /** On arrival the robot inserts only the stacks the Builder still requires. A robot carrying stone and dirt
     *  to a machine that wants stone must leave the dirt aboard (the old board emptied every stack into the grid). */
    public static void builderBoardInsertsOnlyWhatTheBuilderRequires(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos stoneRel = new BlockPos(4, 2, 2);
        BlockPos robotRel = new BlockPos(3, 4, 2);
        TileBuilder stoneBuilder = placeBuilder(helper, stoneRel,
                makeSingleBlockBlueprint(level, helper.absolutePos(new BlockPos(3, 1, 2)), Blocks.STONE));
        try {
            helper.assertTrue(BoardRobotBuilder.needsItems(stoneBuilder), "sanity: the machine reports demand");
            ZonedRobot robot = zonedRobot(helper, robotRel, helper.absolutePos(stoneRel));
            robot.setInventoryStack(0, new ItemStack(Items.STONE, 4));
            robot.setInventoryStack(1, new ItemStack(Items.DIRT, 4));
            BoardRobotBuilder board = new BoardRobotBuilder(robot);

            board.cycle();
            helper.assertTrue(board.getDelegateAI() instanceof AIRobotGotoBlock,
                    "a robot carrying something the machine wants flies to it, got " + board.getDelegateAI());
            int cycles = 0;
            while (board.getDelegateAI() != null && cycles < 400) {
                flyOneTick(board, robot);
                cycles++;
            }
            helper.assertTrue(board.getDelegateAI() == null, "the flight must have ended within 400 cycles");

            helper.assertTrue(gridHas(stoneBuilder, Items.STONE), "the stone the machine requires was delivered");
            helper.assertFalse(gridHas(stoneBuilder, Items.DIRT), "the dirt it did not ask for was not");
            helper.assertTrue(carried(robot, Items.DIRT) == 4, "the dirt is still aboard, got "
                    + carried(robot, Items.DIRT));
            helper.assertTrue(carried(robot, Items.STONE) == 0, "and the stone is not (it was inserted)");
        } finally {
            retire(helper, stoneBuilder, stoneRel);
        }
        helper.succeed();
    }

    // ---------- the fetch leg and the delivery leg share one reach ----------

    /** A docking station that holds a stock of items for robots to take and records whether a robot claimed it
     *  (the real claim goes through the live registry; this one only notes it). */
    private static final class StockedStation extends DockingStation {
        final SimpleContainer input = new SimpleContainer(9);
        boolean claimed;

        StockedStation(BlockPos pos) {
            super(pos, Direction.UP);
        }

        @Override
        public Iterable<StatementSlot> getActiveActions() {
            return Collections.emptyList();
        }

        @Override
        public Container getItemInput() {
            return input;
        }

        @Override
        public boolean take(EntityRobotBase robot) {
            claimed = true;
            return true;
        }

        @Override
        public void release(EntityRobotBase robot) {
            claimed = false;
        }
    }

    private static final class StationRegistry extends MockRobotAccess.InertRobotRegistry {
        final List<DockingStation> stations = new ArrayList<>();

        @Override
        public Collection<DockingStation> getStations() {
            return stations;
        }

        @Override
        public DockingStation getStation(BlockPos pos, Direction side) {
            for (DockingStation station : stations) {
                if (station.getPos().equals(pos) && station.side() == side) {
                    return station;
                }
            }
            return null;
        }
    }

    /** A robot at an absolute block (it is never added to the level, so it may stand outside the arena), zoned to
     *  one block. */
    private static ZonedRobot zonedRobotAt(GameTestHelper helper, BlockPos at, BlockPos zoneBlock) {
        ZonedRobot robot = new ZonedRobot(helper.getLevel(), justThisBlock(zoneBlock));
        Vec3 pos = Vec3.atCenterOf(at);
        robot.setPos(pos.x, pos.y, pos.z);
        robot.getBattery().addPower(SEEDED_CHARGE, false);
        return robot;
    }

    /** The delivery flight leaves from the station the cargo was loaded at, so the reach the board checks when it
     *  picks a Builder must also hold from that station: the old board measured it only from the robot's block when
     *  it chose, then fetched from the nearest stocked station, however far past the flight's reach of the machine
     *  that put it - and the delivery failed at once, the cargo went back, and the next pass did the same, forever.
     *  A stocked station out of reach of the machine is not a candidate for the fetch; a reachable one is chosen
     *  even though it is farther from the robot. */
    public static void builderBoardFetchesOnlyFromStationsInReachOfTheBuilder(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos builderRel = new BlockPos(4, 2, 2);
        TileBuilder builder = placeBuilder(helper, builderRel,
                makeSingleStoneBlueprint(level, helper.absolutePos(new BlockPos(3, 1, 2))));
        BlockPos builderAbs = helper.absolutePos(builderRel);
        try {
            helper.assertTrue(BoardRobotBuilder.needsItems(builder), "sanity: the machine reports demand");

            // The robot is 80 blocks from the machine on one side. The station on that side, 70 blocks from the
            // robot, is 150 from the machine: past the flight's reach of it. The one on the other side is 160
            // from the robot but 80 from the machine.
            StockedStation behindTheRobot = new StockedStation(builderAbs.offset(-150, 0, 0));
            StockedStation besideTheBuilder = new StockedStation(builderAbs.offset(80, 0, 0));
            behindTheRobot.input.setItem(0, new ItemStack(Items.STONE, 8));
            besideTheBuilder.input.setItem(0, new ItemStack(Items.STONE, 8));
            helper.assertTrue(AIRobotGotoBlock.withinFlightReach(builderAbs.offset(-80, 0, 0), builderAbs),
                    "sanity: the robot is inside the flight's reach of the machine");
            helper.assertFalse(AIRobotGotoBlock.withinFlightReach(behindTheRobot.getPos(), builderAbs),
                    "sanity: the near-the-robot station is out of reach of the machine");
            helper.assertTrue(AIRobotGotoBlock.withinFlightReach(besideTheBuilder.getPos(), builderAbs),
                    "sanity: the other station is inside it");

            // Only the out-of-reach station stocks stone: nowhere to fetch from.
            StationRegistry onlyFar = new StationRegistry();
            onlyFar.stations.add(behindTheRobot);
            ZonedRobot first = zonedRobotAt(helper, builderAbs.offset(-80, 0, 0), builderAbs);
            first.setRegistry(onlyFar);
            BoardRobotBuilder board = new BoardRobotBuilder(first);
            board.cycle();
            board.cycle();
            helper.assertFalse(behindTheRobot.claimed,
                    "a station the delivery flight could not leave from is not flown to for the fetch");
            helper.assertTrue(board.getDelegateAI() instanceof AIRobotGotoSleep,
                    "with no usable station the board sleeps, got " + board.getDelegateAI());
            helper.assertTrue(carried(first, Items.STONE) == 0, "and nothing was loaded");

            // Both stocked: the reachable one wins although the other is far nearer to the robot.
            StationRegistry both = new StationRegistry();
            both.stations.add(behindTheRobot);
            both.stations.add(besideTheBuilder);
            ZonedRobot second = zonedRobotAt(helper, builderAbs.offset(-80, 0, 0), builderAbs);
            second.setRegistry(both);
            BoardRobotBuilder secondBoard = new BoardRobotBuilder(second);
            secondBoard.cycle();
            secondBoard.cycle();
            helper.assertFalse(behindTheRobot.claimed, "the station out of the machine's reach is still not used");
            helper.assertTrue(besideTheBuilder.claimed,
                    "the station inside the machine's reach is the one flown to, got " + secondBoard.getDelegateAI());
        } finally {
            retire(helper, builder, builderRel);
        }
        helper.succeed();
    }

    /** A fetch that fails for the chosen Builder must not pin the board to it. Builder X and Builder Y both miss
     *  stone; the robot is nearer X, and the only stocked station is past the flight's reach of X but inside Y's.
     *  The board picks X, the station filter rejects the only station, the fetch fails and the robot sleeps. The
     *  board used to keep X chosen (it still needs stone), so every later pass repeated the doomed fetch for X and
     *  Y was never served. A failed fetch now benches X like a failed delivery and forgets it, so the next pass
     *  re-picks: Y, whose supply is that station. */
    public static void builderBoardFailedFetchLetsAnotherBuilderBeServed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos nearRel = new BlockPos(1, 2, 2);
        BlockPos farRel = new BlockPos(4, 2, 2);
        TileBuilder near = placeBuilder(helper, nearRel,
                makeSingleStoneBlueprint(level, helper.absolutePos(new BlockPos(0, 1, 2))));
        TileBuilder far = placeBuilder(helper, farRel,
                makeSingleStoneBlueprint(level, helper.absolutePos(new BlockPos(3, 1, 2))));
        BlockPos nearAbs = helper.absolutePos(nearRel);
        BlockPos farAbs = helper.absolutePos(farRel);
        try {
            helper.assertTrue(BoardRobotBuilder.needsItems(near) && BoardRobotBuilder.needsItems(far),
                    "sanity: both machines report demand");

            // The station is 97 blocks past the near machine (out of the flight's reach of it) and so 94 past the
            // far one (inside it). The robot stands 10 blocks on the other side of the near machine.
            StockedStation station = new StockedStation(nearAbs.offset(AIRobotGotoBlock.FLIGHT_REACH + 1, 0, 0));
            station.input.setItem(0, new ItemStack(Items.STONE, 8));
            helper.assertFalse(BoardRobotBuilder.flightLeavesStationInReach(station, nearAbs),
                    "sanity: the station is out of the flight's reach of the near machine");
            helper.assertTrue(BoardRobotBuilder.flightLeavesStationInReach(station, farAbs),
                    "sanity: the station is inside the flight's reach of the far machine");

            StationRegistry registry = new StationRegistry();
            registry.stations.add(station);
            ZonedRobot robot = new ZonedRobot(level, justTheseBlocks(nearAbs, farAbs));
            Vec3 robotPos = Vec3.atCenterOf(nearAbs.offset(-10, 0, 0));
            robot.setPos(robotPos.x, robotPos.y, robotPos.z);
            robot.getBattery().addPower(SEEDED_CHARGE, false);
            robot.setRegistry(registry);
            BoardRobotBuilder board = new BoardRobotBuilder(robot);
            helper.assertTrue(board.findClosestBuilder(false) == near, "sanity: the near machine is picked first");

            // First pass: the fetch for the near machine has no usable station.
            board.cycle();
            board.cycle();
            helper.assertFalse(station.claimed, "the station is useless for the near machine's delivery");
            helper.assertTrue(board.getDelegateAI() instanceof AIRobotGotoSleep,
                    "with no usable station the board sleeps, got " + board.getDelegateAI());
            helper.assertTrue(board.isSkipped(near, board.gameTime()),
                    "the machine the fetch failed for is benched like one a delivery failed to");

            // The robot wakes: the next pass must look at the far machine, whose supply that station is.
            board.abortDelegateAI();
            board.cycle();
            board.cycle();
            helper.assertTrue(station.claimed,
                    "after the failed fetch the robot heads for the other machine's supply, got "
                            + board.getDelegateAI());
            helper.assertTrue(carried(robot, Items.STONE) == 0, "and nothing was loaded yet (still on the way)");
        } finally {
            retire(helper, near, nearRel);
            retire(helper, far, farRel);
        }
        helper.succeed();
    }

    // ---------- a Builder the flight can never reach ----------

    /** A board whose bench windows run on a clock the test moves. */
    private static final class ClockedBoard extends BoardRobotBuilder {
        long now = 1_000_000L;

        ClockedBoard(IRobotAccess robot) {
            super(robot);
        }

        @Override
        long gameTime() {
            return now;
        }
    }

    /** A Builder the delivery flight can never reach (here a sealed room) used to be retried for ever with the
     *  cargo aboard, about 1000 ticks of pathfinding and 300 MJ an attempt. After
     *  {@link BoardRobotBuilder#MAX_FAILED_DELIVERIES} failed deliveries of the same cargo the robot now returns it
     *  (the dispose), and the machine stays benched for the longer give-up window. */
    public static void builderBoardGivesUpOnABuilderItCanNeverReach(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos builderRel = new BlockPos(4, 2, 2);
        BlockPos cellRel = new BlockPos(2, 14, 2);
        TileBuilder builder = placeBuilder(helper, builderRel,
                makeSingleStoneBlueprint(level, helper.absolutePos(new BlockPos(3, 1, 2))));
        helper.assertTrue(BoardRobotBuilder.needsItems(builder), "sanity: the machine reports demand");

        List<BlockPos> shell = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dy != 0 || dz != 0) {
                        shell.add(cellRel.offset(dx, dy, dz));
                    }
                }
            }
        }
        Vec3 cellCentre = Vec3.atCenterOf(helper.absolutePos(cellRel));
        try {
            for (BlockPos rel : shell) {
                helper.setBlock(rel, Blocks.STONE);
            }
            ZonedRobot robot = zonedRobot(helper, cellRel, helper.absolutePos(builderRel));
            robot.setInventoryStack(0, new ItemStack(Items.STONE, 4));
            ClockedBoard board = new ClockedBoard(robot);

            for (int attempt = 1; attempt <= BoardRobotBuilder.MAX_FAILED_DELIVERIES; attempt++) {
                // The bench from the last failure has run out, and the robot wakes.
                board.now += BoardRobotBuilder.UNREACHABLE_RETRY_TICKS;
                board.abortDelegateAI();
                board.cycle();
                helper.assertTrue(board.getDelegateAI() instanceof AIRobotGotoBlock,
                        "attempt " + attempt + ": the loaded robot starts the delivery flight, got "
                                + board.getDelegateAI());
                int cycles = 0;
                while (board.getDelegateAI() != null && cycles < 200) {
                    board.cycle();
                    cycles++;
                }
                helper.assertTrue(board.getDelegateAI() == null,
                        "attempt " + attempt + ": the flight must have failed within 200 cycles");
                if (attempt < BoardRobotBuilder.MAX_FAILED_DELIVERIES) {
                    helper.assertTrue(carried(robot, Items.STONE) == 4,
                            "attempt " + attempt + ": the cargo is kept for another try, got "
                                    + carried(robot, Items.STONE));
                    board.cycle();
                    helper.assertTrue(board.getDelegateAI() instanceof AIRobotGotoSleep,
                            "attempt " + attempt + ": with the machine benched the robot sleeps with its cargo, got "
                                    + board.getDelegateAI());
                }
            }

            // The last failure was the one that tipped it: the cargo goes back at the next pass.
            int cycles = 0;
            while (robot.containsItems() && cycles < 60) {
                board.cycle();
                cycles++;
            }
            helper.assertFalse(robot.containsItems(),
                    "after " + BoardRobotBuilder.MAX_FAILED_DELIVERIES
                            + " failed deliveries the cargo is returned, not carried on");
            helper.assertTrue(groundCount(level, cellCentre, Items.STONE, true) == 4,
                    "...all of it (there is no station to take it, so it is dropped)");
            helper.assertTrue(builderGridIsEmpty(builder), "nothing reached the machine");

            long now = board.now;
            helper.assertTrue(board.isSkipped(builder, now + BoardRobotBuilder.GIVE_UP_RETRY_TICKS - 1),
                    "the machine given up on stays benched for the whole give-up window");
            helper.assertFalse(board.isSkipped(builder, now + BoardRobotBuilder.GIVE_UP_RETRY_TICKS),
                    "...and no longer once it has passed");
            helper.assertTrue(BoardRobotBuilder.GIVE_UP_RETRY_TICKS > BoardRobotBuilder.UNREACHABLE_RETRY_TICKS,
                    "the give-up bench is longer than the ordinary one");
        } finally {
            groundCount(level, cellCentre, Items.STONE, true);
            for (BlockPos rel : shell) {
                helper.setBlock(rel, Blocks.AIR);
            }
            retire(helper, builder, builderRel);
        }
        helper.succeed();
    }

    // ---------- the E2E ----------

    /** The full autonomous loop: a builder-board robot, a wooden supply pipe over a stone chest with an
     *  always-on Provide-Items gate, and a one-stone blueprint loaded in a Builder machine. The robot
     *  must fetch the stone and hand it to the machine's resource grid. */
    public static void builderBoardSuppliesBuilderMachine(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pipeRel = new BlockPos(2, 2, 2);
        BlockPos chestRel = new BlockPos(2, 1, 2);
        BlockPos robotRel = new BlockPos(2, 4, 2);
        BlockPos builderRel = new BlockPos(4, 2, 2);
        EntityArenaUtil.forceLoadEntityArena(helper, robotRel);

        helper.setBlock(pipeRel, BCTransportBlocks.PIPE_HOLDER.get());
        //? if >=1.21.10 {
        TilePipeHolder pipe = helper.getBlockEntity(pipeRel, TilePipeHolder.class);
        //?} else {
        /*TilePipeHolder pipe = helper.getBlockEntity(pipeRel);*/
        //?}
        pipe.onPlacedBy(null, new ItemStack(BCTransportItems.PIPE_WOOD_ITEM.get()));
        pipe.replacePluggable(Direction.UP,
                new RobotStationPluggable(buildcraft.robotics.BCRoboticsPlugs.robotStation, pipe, Direction.UP));
        PluggableGate gate = new PluggableGate(BCSiliconPlugs.gate, pipe, Direction.SOUTH,
                new GateVariant(EnumGateLogic.AND, EnumGateMaterial.CLAY_BRICK,
                        EnumGateModifier.NO_MODIFIER));
        gate.logic.statements[0].trigger.set(TriggerWrapper.wrap(BCCoreStatements.TRIGGER_TRUE, null));
        gate.logic.statements[0].action.set(ActionWrapper.wrap(
                BCRoboticsStatements.ACTION_STATION_PROVIDE_ITEMS, null));
        pipe.replacePluggable(Direction.SOUTH, gate);
        gate.logic.resolveActions();

        helper.setBlock(chestRel, Blocks.CHEST);
        //? if >=1.21.10 {
        ChestBlockEntity chest = helper.getBlockEntity(chestRel, ChestBlockEntity.class);
        //?} else {
        /*ChestBlockEntity chest = helper.getBlockEntity(chestRel);*/
        //?}
        chest.setItem(0, new ItemStack(Items.STONE, 8));

        TileBuilder builder = placeBuilder(helper, builderRel,
                makeSingleStoneBlueprint(level, helper.absolutePos(new BlockPos(3, 1, 2))));

        // Zone-pinned to this cell: the board searches every Builder loaded in the level, and other tests leave
        // needy ones behind.
        EntityRobot robot = new ZonePinnedRobot(helper, BoardRobotBuilderNBT.INSTANCE);
        Vec3 pos = Vec3.atCenterOf(helper.absolutePos(robotRel));
        robot.setPos(pos.x, pos.y, pos.z);
        robot.getBattery().addPower(SEEDED_CHARGE, false);
        level.addFreshEntity(robot);

        // The assertions live in the success block. They used to follow the tickUntil call, which only SCHEDULES
        // the poll, so they ran on the test's first tick against an empty grid and the test passed at once.
        EntityArenaUtil.tickUntil(helper, 400, () -> {
            for (int slot = 0; slot < TileBuilder.RESOURCE_SLOTS; slot++) {
                if (!builder.getResource(slot).isEmpty()) {
                    return true;
                }
            }
            return false;
        }, () -> {
            for (int slot = 0; slot < TileBuilder.RESOURCE_SLOTS; slot++) {
                ItemStack stack = builder.getResource(slot);
                helper.assertTrue(stack.isEmpty() || stack.is(Items.STONE),
                        "the machine received the blueprint's stone, got " + stack);
            }
            helper.succeed();
        }, "the builder-board robot must fetch the stone and deliver it to the machine's grid",
                () -> EntityArenaUtil.discard(robot));
    }
}
