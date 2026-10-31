/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.ai;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.robotics.ai.AIRobotSearchStation.Hold;

/** Empties the robot by any means available: unloads at a station until the cargo is gone, and if no
 *  station will take it, drops everything on the ground. Ported from 7.1.x {@code AIRobotDisposeItems}
 *  (the delivery board's recovery path — a robot stuck holding an order nobody wants any more must not
 *  deadlock the board).
 *
 *  <p>Not 7.1.x: when the only stations that could take the cargo are out of reach for a reason that passes
 *  ({@link AIRobotSearchStation#unavailableTakers} lists them), the cargo is kept and this ends unsuccessfully with
 *  {@link #heldBack()} set. Dropping it then lost a Delivery robot's whole load whenever its home station forbade it,
 *  or a taker was busy for a moment. A forbid is waited for as long as it lasts; a claim or an unloaded chunk only
 *  {@link #MAX_TRANSIENT_HOLDS} times until the next unload, since nobody may ever release or load it. The board
 *  sends the robot to sleep and tries again afterwards; the sleep is the board's to start, not this AI's, because
 *  {@code AIRobotGotoSleep} frees every claim the robot holds and only the board knows which of them it must mark as
 *  given back first. */
public class AIRobotDisposeItems extends AIRobot {

    /** How many disposals may keep the cargo for takers that are only claimed by another robot or not loaded
     *  ({@link Hold#TRANSIENT}) before one drops it, counted on the robot ({@link IRobotAccess#getHeldDisposals})
     *  until the robot next empties its cargo at a station ({@link AIRobotUnload} or a Delivery drop-off in
     *  {@link AIRobotDeliverRequested}).
     *  The board sleeps a minute after each. The count is not reset by the drop, so the next leftover does not wait
     *  again for takers that just failed to show. */
    static final int MAX_TRANSIENT_HOLDS = 3;

    /** Set when the cargo was kept because only stations that are out of reach for now could take it (not saved). */
    private boolean heldBack;

    public AIRobotDisposeItems(IRobotAccess iRobot) {
        super(iRobot);
    }

    /** Whether this ended with the cargo still aboard because every station that could take it is out of reach for
     *  now ({@link AIRobotSearchStation#unavailableTakers}). */
    public boolean heldBack() {
        return heldBack;
    }

    @Override
    public void start() {
        startDelegateAI(new AIRobotGotoStationAndUnload(robot));
    }

    @Override
    public void delegateAIEnded(AIRobot ai) {
        if (ai instanceof AIRobotGotoStationAndUnload) {
            if (ai.success()) {
                if (robot.containsItems()) {
                    startDelegateAI(new AIRobotGotoStationAndUnload(robot));
                } else {
                    robot.setHeldDisposals(0);
                    robot.setEvictedFromStation(null);
                    terminate();
                }
                return;
            }
            Hold hold = AIRobotGotoStationToUnload.unavailableTakers(robot);
            int held = robot.getHeldDisposals();
            if (hold == Hold.FORBIDDEN || (hold == Hold.TRANSIENT && held < MAX_TRANSIENT_HOLDS)) {
                if (hold == Hold.TRANSIENT) {
                    robot.setHeldDisposals(held + 1);
                }
                heldBack = true;
                setSuccess(false);
            } else {
                dropCargo();
                robot.setEvictedFromStation(null);
            }
            terminate();
        }
    }

    /** Spills every cargo slot where the robot hovers. Package-private so a test can see that a disposal gave up
     *  without needing a level to drop into. */
    void dropCargo() {
        for (int i = 0; i < robot.getInventorySize(); i++) {
            ItemStack stack = robot.getInventoryStack(i);
            if (!stack.isEmpty()) {
                ItemEntity entity = new ItemEntity(
                        robot.level(),
                        robot.position().x,
                        robot.position().y,
                        robot.position().z,
                        stack);
                robot.level().addFreshEntity(entity);

                robot.setInventoryStack(i, ItemStack.EMPTY);
            }
        }
    }
}
