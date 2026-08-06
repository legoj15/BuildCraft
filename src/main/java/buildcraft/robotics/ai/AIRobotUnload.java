/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.ai;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.api.transport.IInjectable;

/** Pushes the robot's cargo out through the docked station's item output. The station's permissive
 *  {@code canRobotAcceptItem} policy (D1) gates each stack where {@code ActionRobotFilter} did. Modern
 *  {@code IInjectable.injectItem} returns the leftover {@code ItemStack} (7.1.x returned an int), so the
 *  count actually accepted is {@code stack.getCount() - leftover.getCount()}. */
public class AIRobotUnload extends AIRobot {

    private int waitedCycles = 0;

    public AIRobotUnload(IRobotAccess iRobot) {
        super(iRobot);
    }

    @Override
    public void update() {
        waitedCycles++;

        if (waitedCycles > 40) {
            if (unload(robot, robot.getDockingStation(), true)) {
                waitedCycles = 0;
            } else {
                setSuccess(!robot.containsItems());
                terminate();
            }
        }
    }

    public static boolean unload(IRobotAccess robot, DockingStation station, boolean doUnload) {
        if (station == null) {
            return false;
        }

        IInjectable output = station.getItemOutput();
        if (output == null) {
            return false;
        }

        Direction injectSide = station.getItemOutputSide().face != null
                ? station.getItemOutputSide().face : Direction.DOWN;
        if (!output.canInjectItems(injectSide)) {
            return false;
        }

        // The robot's four transfer slots, reached directly (no InventoryIterator on the modern robot).
        for (int slot = 0; slot < robot.getInventorySize(); slot++) {
            ItemStack stack = robot.getInventoryStack(slot);
            if (stack.isEmpty()) {
                continue;
            }

            if (!station.canRobotAcceptItem(stack)) {
                continue;
            }

            int used = injectInto(output, stack, doUnload, injectSide);
            if (used > 0) {
                if (doUnload) {
                    shrinkSlot(robot, slot, stack, used);
                }
                return true;
            }
        }

        ItemStack held = robot.getHeldItem();
        if (!held.isEmpty()) {
            if (!station.canRobotAcceptItem(held)) {
                return false;
            }

            int used = injectInto(output, held, doUnload, injectSide);
            if (used > 0) {
                if (doUnload) {
                    if (held.getCount() <= used) {
                        robot.setItemInUse(ItemStack.EMPTY);
                    } else {
                        held.shrink(used);
                        robot.setItemInUse(held);
                    }
                }
                return true;
            }
        }

        return false;
    }

    private static int injectInto(IInjectable output, ItemStack stack, boolean doUnload, Direction injectSide) {
        ItemStack leftover = output.injectItem(stack, doUnload, injectSide, null, 0);
        int used = stack.getCount() - leftover.getCount();
        return Math.max(used, 0);
    }

    private static void shrinkSlot(IRobotAccess robot, int slot, ItemStack stack, int used) {
        ItemStack remaining = stack.copy();
        remaining.shrink(used);
        robot.setInventoryStack(slot, remaining.isEmpty() ? ItemStack.EMPTY : remaining);
    }

    @Override
    public long getPowerCost() {
        // 7.1.x charged 10 RF per tick; at the canonical 1 MJ = 10 RF bridge that is 10 * 100_000 micro-MJ.
        return 1_000_000;
    }
}
