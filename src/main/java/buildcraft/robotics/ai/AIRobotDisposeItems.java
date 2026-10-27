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

/** Empties the robot by any means available: unloads at a station until the cargo is gone, and if no
 *  station will take it, drops everything on the ground. Ported from 7.1.x {@code AIRobotDisposeItems}
 *  (the delivery board's recovery path — a robot stuck holding an order nobody wants any more must not
 *  deadlock the board).
 *
 *  <p>Not 7.1.x: when the only stations that would take the cargo forbid the robot (Forbid Robot, which also evicts a
 *  robot from such a station), the cargo is kept and this ends unsuccessfully with {@link #heldBack()} set. A forbid
 *  is gate state that can lift, and dropping the cargo then lost a Delivery robot's whole load whenever its home
 *  station forbade it. The board sends the robot to sleep and tries again afterwards; the sleep is the board's to
 *  start, not this AI's, because {@code AIRobotGotoSleep} frees every claim the robot holds and only the board knows
 *  which of them it must mark as given back first. */
public class AIRobotDisposeItems extends AIRobot {

    /** Set when the cargo was kept because only stations that forbid the robot would take it (not saved). */
    private boolean heldBack;

    public AIRobotDisposeItems(IRobotAccess iRobot) {
        super(iRobot);
    }

    /** Whether this ended with the cargo still aboard because every station that would take it forbids the robot. */
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
                    terminate();
                }
            } else if (AIRobotGotoStationToUnload.onlyForbiddingStationsTakeCargo(robot)) {
                heldBack = true;
                setSuccess(false);
                terminate();
            } else {
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
                terminate();
            }
        }
    }
}
