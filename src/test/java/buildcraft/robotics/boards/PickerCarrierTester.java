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
import net.minecraft.world.phys.Vec3;

import buildcraft.api.boards.RedstoneBoardNBT;
import buildcraft.api.boards.RedstoneBoardRobot;
import buildcraft.api.boards.RedstoneBoardRobotNBT;
import buildcraft.api.boards.RedstoneBoardRegistry;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.RobotManager;

import buildcraft.lib.test.EntityArenaUtil;

import buildcraft.robotics.BCRoboticsEntities;
import buildcraft.robotics.BCRoboticsPlugs;
import buildcraft.robotics.DockingStationPipe;
import buildcraft.robotics.RobotStationPluggable;
import buildcraft.robotics.entity.EntityRobot;
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
 * 6x8 arena cell, the arena chunks are force-loaded, and nothing asserts on a fixed tick — each phase is
 * gated on observed state via {@link EntityArenaUtil#tickUntil}.
 */
public class PickerCarrierTester {

    /** Charge above {@code SAFETY_POWER} so the {@code AIRobotMain} ladder neither shuts the robot down nor
     *  sends it recharging mid-task. */
    private static final long SEEDED_CHARGE = 5000L * MjAPI.MJ;

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

    private static void installStation(GameTestHelper helper, BlockPos relPos, Direction side) {
        TilePipeHolder tile = placeItemPipe(helper, relPos);
        tile.replacePluggable(side, new RobotStationPluggable(BCRoboticsPlugs.robotStation, tile, side));
    }

    /** The registered station for a pipe face, or null while {@code RobotStationPluggable.onTick()} has not
     *  yet lazily registered it. */
    private static DockingStationPipe stationAt(GameTestHelper helper, BlockPos relPos, Direction side) {
        DockingStation station = RobotManager.registryProvider.getRegistry(helper.getLevel())
                .getStation(helper.absolutePos(relPos), side);
        return station instanceof DockingStationPipe pipe ? pipe : null;
    }

    /** Spawns a robot carrying {@code boardNBT} at {@code relPos}. The {@code (Level, RedstoneBoardRobotNBT)}
     *  constructor calls {@code setBoard}, which creates and starts the {@code AIRobotMain} — so the robot
     *  begins cycling the moment its first tick registers it with the registry. */
    private static EntityRobot addBoardRobot(GameTestHelper helper, BlockPos relPos, RedstoneBoardRobotNBT boardNBT) {
        EntityRobot robot = new EntityRobot(helper.getLevel(), boardNBT);
        Vec3 pos = Vec3.atCenterOf(helper.absolutePos(relPos));
        robot.setPos(pos.x, pos.y, pos.z);
        helper.getLevel().addFreshEntity(robot);
        return robot;
    }

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
        EntityArenaUtil.forceLoadEntityArena(helper);
        ServerLevel level = helper.getLevel();
        BlockPos robotRel = new BlockPos(3, 4, 2);
        BlockPos itemRel = new BlockPos(5, 4, 2);

        EntityRobot robot = addBoardRobot(helper, robotRel, BoardRobotPickerNBT.INSTANCE);
        robot.getBattery().addPower(SEEDED_CHARGE, false);

        // A dropped item two blocks away, pinned in mid-air with noGravity so it cannot fall through the
        // arena floor or into the void before the robot reaches it (the fetch targets Math.floor(item pos),
        // and a floor-resting item resolves to the solid block under it).
        Vec3 itemPos = Vec3.atCenterOf(helper.absolutePos(itemRel));
        ItemEntity item = new ItemEntity(level, itemPos.x, itemPos.y, itemPos.z, new ItemStack(Items.DIAMOND));
        item.setNoGravity(true);
        level.addFreshEntity(item);

        EntityArenaUtil.tickUntil(helper, 240, robot::containsItems, () -> {
            helper.assertTrue(ItemStack.matches(robot.getInventoryStack(0), new ItemStack(Items.DIAMOND, 1)),
                    "the picker must pick up the dropped diamond into a transfer slot");
            // The robot must have FLOWN to the item (pathfinding + delta-movement), not teleported it in:
            // the fetch's pick-up is distance-independent, so a dead pathfinder would still "succeed"
            // while the robot sat at its spawn. The goto target is the item's own block centre.
            helper.assertTrue(robot.position().distanceToSqr(itemPos) < 2.25,
                    "the picker must fly to the dropped item: robot at " + robot.position()
                            + " but item at " + itemPos);
            // Remove the robot before the test ends: the picker's 250-block fetch range is inherent to the
            // board, and a robot left alive would keep scanning — and flying toward — items in neighbouring
            // tests' arenas. Discarding it here (and running this test in its own private environment)
            // keeps the batch isolation clean.
            robot.discard();
            helper.succeed();
        }, "the picker robot never picked up the dropped diamond");
    }

    // ---------- carrier E2E ----------

    /** A charged carrier with no home station must autonomously find a loadable supply station and pull the
     *  chest's contents into its own inventory. Exercises search -> goto (fly + dock) -> {@code AIRobotLoad}
     *  from {@code DockingStationPipe.getItemInput} (D6). */
    public static void carrierRobotLoadsFromSupplyChest(GameTestHelper helper) {
        EntityArenaUtil.forceLoadEntityArena(helper);
        BlockPos pipeRel = new BlockPos(2, 3, 4);
        // One cell below the pipe: the station's input is on side().getOpposite() = DOWN for an UP face.
        BlockPos chestRel = new BlockPos(2, 2, 4);
        BlockPos robotRel = new BlockPos(4, 3, 4);

        installStation(helper, pipeRel, Direction.UP);
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
        EntityArenaUtil.tickUntil(helper, 260,
                () -> {
                    if (stationAt(helper, pipeRel, Direction.UP) == null) {
                        return false;
                    }
                    if (robot[0] == null) {
                        robot[0] = addBoardRobot(helper, robotRel, BoardRobotCarrierNBT.INSTANCE);
                        robot[0].getBattery().addPower(SEEDED_CHARGE, false);
                    }
                    return robot[0].containsItems();
                },
                () -> {
                    helper.assertTrue(ItemStack.matches(robot[0].getInventoryStack(0),
                            new ItemStack(Items.DIAMOND, 5)),
                            "the carrier must load the diamonds from the supply chest into its inventory");
                    robot[0].discard();
                    helper.succeed();
                },
                "the carrier robot never loaded the diamonds from the supply chest");
    }
}
