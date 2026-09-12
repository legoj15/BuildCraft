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
 *  deadlock the board). */
public class AIRobotDisposeItems extends AIRobot {

    public AIRobotDisposeItems(IRobotAccess iRobot) {
        super(iRobot);
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
