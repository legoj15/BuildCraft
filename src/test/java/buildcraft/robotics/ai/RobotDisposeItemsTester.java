/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.robotics.ai;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.mj.MjAPI;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.IRobotRegistry;
import buildcraft.lib.misc.data.Box;
import buildcraft.lib.test.EntityArenaUtil;
import buildcraft.robotics.BCRoboticsEntities;
import buildcraft.robotics.DockingStationPipe;
import buildcraft.robotics.ZonePinnedRobot;
import buildcraft.robotics.entity.EntityRobot;

/**
 * {@link AIRobotDisposeItems} - 7.1.x's recovery path for a robot holding cargo nobody wants (the delivery board's
 * leftovers, the builder's surplus): unload at a station until the cargo is gone, and when no station will take it,
 * drop everything where the robot hovers instead of deadlocking the board. Already ported and wired where 7.1.x
 * used it (the delivery and builder boards; its carrier deliberately sleeps with its cargo instead); this pins the
 * spill half against a real robot, which is the half a mock cannot reach.
 *
 * <p>The robot is never added to the level, so nothing ticks it behind the test's back: the AI is cycled by hand.
 * The spill and the scan for it share one tick, though, and that tick must come after the robot's chunk is
 * entity-ticking: the framework waits only for the 1x1 structure's own chunk, (2, 2, 2) lands in a neighbouring
 * chunk whenever the arena origin sits near a chunk edge, and items dropped into a chunk whose entities are not
 * loaded yet are invisible to the scan (and leak).
 */
public class RobotDisposeItemsTester {

    public static void disposeItemsDropsCargoWhenNoStationWillTakeIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos robotRel = new BlockPos(2, 2, 2);
        EntityArenaUtil.forceLoadEntityArena(helper, robotRel);

        List<ItemEntity> dropped = new ArrayList<>();
        EntityArenaUtil.tickUntil(helper, 80, () -> EntityArenaUtil.isEntityTicking(helper, robotRel), () -> {
            // "No station anywhere" must hold in a level other tests share: concurrent robot tests register real
            // docking stations, and one that accepts items turns the spill into a long flight. An empty registry
            // keeps the premise true whatever else is running.
            IRobotRegistry noStations = new MockRobotAccess.InertRobotRegistry();
            EntityRobot robot = new EntityRobot(BCRoboticsEntities.ROBOT.get(), level) {
                @Override
                public IRobotRegistry getRegistry() {
                    return noStations;
                }
            };
            Vec3 at = Vec3.atCenterOf(helper.absolutePos(robotRel));
            robot.setPos(at.x, at.y, at.z);
            robot.getBattery().addPower(5000L * MjAPI.MJ, false);
            AABB dropZone = new AABB(at, at).inflate(1.5);
            for (ItemEntity stale : level.getEntitiesOfClass(ItemEntity.class, dropZone)) {
                stale.discard();
            }

            robot.setInventoryStack(0, new ItemStack(Items.COBBLESTONE, 5));
            robot.setInventoryStack(2, new ItemStack(Items.PAPER, 3));
            helper.assertTrue(robot.containsItems(), "precondition: the robot carries cargo");

            boolean[] ended = { false };
            AIRobotDisposeItems dispose = new AIRobotDisposeItems(robot) {
                @Override
                public void end() {
                    ended[0] = true;
                }
            };
            dispose.start();
            for (int guard = 0; guard < 400 && !ended[0]; guard++) {
                dispose.cycle();
            }

            dropped.addAll(level.getEntitiesOfClass(ItemEntity.class, dropZone));
            int cobble = dropped.stream().filter(e -> e.getItem().is(Items.COBBLESTONE))
                    .mapToInt(e -> e.getItem().getCount()).sum();
            int paper = dropped.stream().filter(e -> e.getItem().is(Items.PAPER))
                    .mapToInt(e -> e.getItem().getCount()).sum();

            helper.assertTrue(ended[0],
                    "with no station anywhere the disposal still ends - it must not hang the board");
            helper.assertFalse(robot.containsItems(), "the robot's cargo slots are empty again");
            helper.assertTrue(cobble == 5, "all five cobblestone were dropped at the robot, found " + cobble);
            helper.assertTrue(paper == 3, "all three paper were dropped at the robot, found " + paper);
            helper.succeed();
        }, "the robot's chunk never became entity-ticking", () -> dropped.forEach(ItemEntity::discard));
    }

    /** Far enough from the arena grid that its chunk is never loaded during the run; z 448 keeps it 8 chunks from the
     *  other robot tests' far stations on the same x. */
    private static final BlockPos UNLOADED_OFFSET = new BlockPos(4096, 0, 448);

    /** #68: the only station that could take the cargo sits in the robot's load/unload zone, in a chunk that has not
     *  loaded since the server started, so it is the bare record read back from the save with no pipe to ask whether it
     *  would take cobblestone. It may well (the robot unloaded there last session), and the chunk loads again once a
     *  player comes near, so the cargo is kept for the board to sleep on, not dropped. */
    public static void disposeItemsKeepsCargoForAStationInAnUnloadedChunk(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos robotRel = new BlockPos(2, 2, 2);
        EntityArenaUtil.forceLoadEntityArena(helper, robotRel);

        List<ItemEntity> dropped = new ArrayList<>();
        EntityArenaUtil.tickUntil(helper, 80, () -> EntityArenaUtil.isEntityTicking(helper, robotRel), () -> {
            BlockPos far = helper.absolutePos(robotRel).offset(UNLOADED_OFFSET);
            DockingStationPipe station = new DockingStationPipe();
            CompoundTag record = new CompoundTag();
            record.putIntArray("pos", new int[] { far.getX(), far.getY(), far.getZ() });
            record.putByte("side", (byte) Direction.UP.ordinal());
            station.readFromNbt(record);
            station.world = level;
            helper.assertFalse(station.isChunkLoaded(), "precondition: the station's chunk is not loaded");

            // An isolated registry holding only that station: concurrent robot tests' real stations stay out of it.
            IRobotRegistry onlyFar = new MockRobotAccess.InertRobotRegistry() {
                @Override
                public Collection<DockingStation> getStations() {
                    return List.of(station);
                }
            };
            ZonePinnedRobot robot = new ZonePinnedRobot(level, null, new Box(far, far)).registry(onlyFar);
            Vec3 at = Vec3.atCenterOf(helper.absolutePos(robotRel));
            robot.setPos(at.x, at.y, at.z);
            robot.getBattery().addPower(5000L * MjAPI.MJ, false);
            AABB dropZone = new AABB(at, at).inflate(1.5);
            for (ItemEntity stale : level.getEntitiesOfClass(ItemEntity.class, dropZone)) {
                stale.discard();
            }
            robot.setInventoryStack(0, new ItemStack(Items.COBBLESTONE, 4));

            boolean[] ended = { false };
            AIRobotDisposeItems dispose = new AIRobotDisposeItems(robot) {
                @Override
                public void end() {
                    ended[0] = true;
                }
            };
            dispose.start();
            for (int guard = 0; guard < 400 && !ended[0]; guard++) {
                dispose.cycle();
            }
            dropped.addAll(level.getEntitiesOfClass(ItemEntity.class, dropZone));

            helper.assertTrue(ended[0], "the disposal ends, so the board can sleep on it");
            helper.assertTrue(dispose.heldBack(), "a station in an unloaded chunk in the zone holds the cargo back");
            helper.assertTrue(ItemStack.matches(robot.getInventoryStack(0), new ItemStack(Items.COBBLESTONE, 4)),
                    "the robot keeps its cargo, holding " + robot.getInventoryStack(0));
            helper.assertTrue(dropped.isEmpty(), "nothing is dropped, found " + dropped.size() + " item entities");
            helper.assertFalse(station.isChunkLoaded(), "nothing may load the station's chunk");
            helper.succeed();
        }, "the robot's chunk never became entity-ticking", () -> dropped.forEach(ItemEntity::discard));
    }
}
