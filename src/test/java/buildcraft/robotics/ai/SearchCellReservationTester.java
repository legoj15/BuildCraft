/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.mj.MjAPI;
import buildcraft.api.robots.IRobotRegistry;
import buildcraft.api.robots.ResourceIdBlock;
import buildcraft.robotics.BCRoboticsEntities;
import buildcraft.robotics.entity.EntityRobot;

/** A finished search holds its target cell in the dimension's shared reservation set until the NEXT cycle of its
 *  AI collects it ({@code AIRobotSearchBlock.takeResource} then unreserves). A robot that unloads or dies in that
 *  one-cycle window never runs the AI again, and the set had no idea whose cell it was, so the block stayed
 *  untargetable by every search in the dimension until the registry itself died. Driven synchronously on an
 *  unadded robot registered by hand, exactly as {@link SearchBlockTester}: the search is pure block computation,
 *  hand-cycled, and a robot that never enters the world cannot disturb any sibling test.
 *
 *  <p>The window is observed, not assumed: the AI is cycled until the registry shows the cell reserved while the
 *  search still has not reported success. The filter is pinned to this test's own log for the reason
 *  {@link SearchBlockTester} gives, and the log sits at a position the sibling search tests do not use. */
public class SearchCellReservationTester {

    private static final long SEEDED_CHARGE = 5000L * MjAPI.MJ;

    private static final BlockPos ROBOT_REL = new BlockPos(1, 2, 3);
    private static final BlockPos LOG_REL = new BlockPos(4, 2, 4);

    /** A registered robot whose search has locked onto the log (cell reserved) but not yet reported it. */
    private static final class Window implements AutoCloseable {
        final GameTestHelper helper;
        final IRobotRegistry registry;
        final EntityRobot robot;
        final AIRobotSearchBlock search;
        final BlockPos logPos;

        Window(GameTestHelper helper) {
            this.helper = helper;
            ServerLevel level = helper.getLevel();
            logPos = helper.absolutePos(LOG_REL);
            helper.setBlock(LOG_REL, Blocks.OAK_LOG);

            robot = new EntityRobot(BCRoboticsEntities.ROBOT.get(), level);
            Vec3 spawn = Vec3.atCenterOf(helper.absolutePos(ROBOT_REL));
            robot.setPos(spawn.x, spawn.y, spawn.z);
            robot.getBattery().addPower(SEEDED_CHARGE, false);

            registry = robot.getRegistry();
            helper.assertTrue(registry != null, "the dimension's robot registry must exist");
            robot.setUniqueRobotId(registry.getNextRobotId());
            registry.registerRobot(robot);

            search = new AIRobotSearchBlock(robot, false,
                    pos -> level.getBlockState(pos).is(Blocks.OAK_LOG) && pos.equals(logPos), 32);
            try {
                for (int i = 0; i < 20000 && !registry.getBlockReservations().contains(logPos); i++) {
                    search.cycle();
                }
                helper.assertTrue(registry.getBlockReservations().contains(logPos),
                        "precondition: the search must lock onto the log and reserve its cell");
                helper.assertFalse(search.success(),
                        "precondition: the AI has not collected the cell yet - this is the window under test");
            } catch (RuntimeException e) {
                close(); // a constructor that throws is never closed by try-with-resources
                throw e;
            }
        }

        @Override
        public void close() {
            search.abort();
            registry.killRobot(robot);
            // A failing run must not leave the cell pinned for the sibling tests that share the registry.
            // Through the robot's own view: the raw set is read-only, and a raw removal would skip the owner index.
            registry.getBlockReservations(robot.getRobotId()).remove(logPos);
        }
    }

    /** The robot is destroyed between "search found it" and "AI collected it". */
    public static void searchCellIsReleasedWhenTheRobotDies(GameTestHelper helper) {
        try (Window w = new Window(helper)) {
            w.registry.killRobot(w.robot);

            helper.assertFalse(w.registry.getBlockReservations().contains(w.logPos),
                    "a killed robot's search cell must be freed - otherwise the block is untargetable for "
                            + "every search in the dimension until the server restarts");
            helper.succeed();
        }
    }

    /** The robot's chunk unloads between "search found it" and "AI collected it". */
    public static void searchCellIsReleasedWhenTheRobotUnloads(GameTestHelper helper) {
        try (Window w = new Window(helper)) {
            w.registry.unloadRobot(w.robot);

            helper.assertFalse(w.registry.getBlockReservations().contains(w.logPos),
                    "an unloaded robot's search cell must be freed - the AI that would have collected it is "
                            + "not saved, so nothing else ever will");
            helper.succeed();
        }
    }

    /** The search is aborted (a gate action overrides the robot's AI, the robot is ordered elsewhere) in that same
     *  window. The robot lives on, so no registry release path runs: the AI's own {@code end()} is the only thing
     *  that can give the cell back, and it must not wait for the robot's next sleep or recharge to do so. */
    public static void searchCellIsReleasedWhenTheSearchIsAborted(GameTestHelper helper) {
        try (Window w = new Window(helper)) {
            w.search.abort();

            helper.assertFalse(w.registry.getBlockReservations().contains(w.logPos),
                    "an aborted search must give its locked-on cell back at once - the robot is still alive, so "
                            + "no kill/unload release will ever do it");
            helper.succeed();
        }
    }

    /** A search that ends normally hands its cell to the caller, which reserves the block in the registry before
     *  the search lets go of it - the end() fix for the abort window must not open a gap there. */
    public static void searchCellHandsOverToTheRegistryReservation(GameTestHelper helper) {
        try (Window w = new Window(helper)) {
            // One more cycle collects the result: success, and the AI terminates itself.
            w.search.cycle();
            helper.assertTrue(w.search.success() && w.logPos.equals(w.search.blockFound),
                    "precondition: the next cycle reports the found block");
            helper.assertTrue(w.search.takeResource(), "the caller reserves the found block");

            helper.assertTrue(w.registry.isTaken(new buildcraft.api.robots.ResourceIdBlock(w.logPos)),
                    "the registry now holds the block for this robot");
            helper.assertFalse(w.registry.getBlockReservations().contains(w.logPos),
                    "the search's own cell reservation is gone once the registry reservation replaces it");
            helper.succeed();
        }
    }

    /** The composed search-then-fly AI holds a REGISTRY reservation on the found block for the whole flight. If it is
     *  aborted mid-flight (the robot lives on) nothing else ever releases that block until the robot's next sleep or
     *  recharge - the block is untargetable for every other robot meanwhile. Unlike the search cell, this one is a
     *  ResourceId the registry already frees on kill/unload; it is the abort path that was open. */
    public static void searchAndGotoReservationIsReleasedWhenAbortedMidFlight(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos logPos = helper.absolutePos(LOG_REL);
        helper.setBlock(LOG_REL, Blocks.OAK_LOG);

        EntityRobot robot = new EntityRobot(BCRoboticsEntities.ROBOT.get(), level);
        Vec3 spawn = Vec3.atCenterOf(helper.absolutePos(ROBOT_REL));
        robot.setPos(spawn.x, spawn.y, spawn.z);
        robot.getBattery().addPower(SEEDED_CHARGE, false);
        IRobotRegistry registry = robot.getRegistry();
        robot.setUniqueRobotId(registry.getNextRobotId());
        registry.registerRobot(robot);
        ResourceIdBlock block = new ResourceIdBlock(logPos);

        AIRobotSearchAndGotoBlock ai = new AIRobotSearchAndGotoBlock(robot, false,
                pos -> level.getBlockState(pos).is(Blocks.OAK_LOG) && pos.equals(logPos));
        try {
            // The robot is never moved, so the flight leg stays in progress once the search has found the block.
            for (int i = 0; i < 20000 && ai.getBlockFound() == null; i++) {
                ai.cycle();
            }
            helper.assertTrue(ai.getBlockFound() != null && ai.getDelegateAI() != null,
                    "precondition: the search found the block and the flight leg has started");
            helper.assertTrue(registry.isTaken(block), "precondition: the registry holds the block for the flight");

            ai.abort();

            helper.assertFalse(registry.isTaken(block),
                    "aborting the flight must give the found block back - the robot is still alive, so no "
                            + "kill/unload release will ever do it");
            helper.succeed();
        } finally {
            ai.abort();
            registry.release(block);
            registry.killRobot(robot);
        }
    }

    /** The between-jobs release ({@code AIRobotGotoSleep}, {@code AIRobotRecharge}) is the backstop for an AI that
     *  was merely aborted; it must cover the search cell as it covers every other reservation. */
    public static void searchCellIsReleasedByReleaseResources(GameTestHelper helper) {
        try (Window w = new Window(helper)) {
            w.registry.releaseResources(w.robot);

            helper.assertFalse(w.registry.getBlockReservations().contains(w.logPos),
                    "releaseResources must give back the robot's search cell too");
            helper.assertTrue(w.registry.getLoadedRobot(w.robot.getRobotId()) == w.robot,
                    "...and the robot itself stays loaded");
            helper.succeed();
        }
    }
}
