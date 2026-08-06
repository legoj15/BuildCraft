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
import net.minecraft.world.phys.AABB;

import buildcraft.api.core.IZone;
import buildcraft.api.core.IStackFilter;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.robotics.boards.BoardRobotPicker;

/** Flies to a nearby dropped item and picks it up. The shared "this item is being fetched" set keys on
 *  {@code ItemEntity.getUUID()} (D4) — 7.1.x keyed it on recycled {@code int} entity ids, which collide across
 *  dimensions; UUIDs are globally unique. */
public class AIRobotFetchItem extends AIRobot {

    private ItemEntity target;

    private float maxRange;
    private IStackFilter stackFilter;
    private int pickTime = -1;
    private IZone zone;

    public AIRobotFetchItem(IRobotAccess iRobot) {
        super(iRobot);
    }

    public AIRobotFetchItem(IRobotAccess iRobot, float iMaxRange, IStackFilter iStackFilter, IZone iZone) {
        this(iRobot);

        maxRange = iMaxRange;
        stackFilter = iStackFilter;
        zone = iZone;
    }

    @Override
    public void preempt(AIRobot ai) {
        if (target != null && target.isRemoved()) {
            terminate();
        }
    }

    @Override
    public void update() {
        if (target == null) {
            scanForItem();
        } else {
            if (target.isRemoved()) {
                // preempt() already terminated this AI this same tick; a same-tick update() must not pick
                // from the removed entity's stale stack — that would duplicate items the world (a player,
                // a hopper, another robot) already took. Simply stay out of the way.
                return;
            }
            pickTime++;

            if (pickTime > 5) {
                ItemStack entityStack = target.getItem();
                // The transactor never mutates the offered stack and returns the leftover, so taken =
                // offered minus leftover is exact — no defensive copy needed.
                ItemStack overflow = robot.getTransactor().insert(entityStack, false, false);
                int taken = entityStack.getCount() - overflow.getCount();

                entityStack.shrink(taken);
                if (entityStack.isEmpty()) {
                    target.discard();
                } else {
                    target.setItem(entityStack);
                }

                terminate();
            }
        }
    }

    @Override
    public void delegateAIEnded(AIRobot ai) {
        if (ai instanceof AIRobotGotoBlock) {
            if (target == null) {
                // Reached the item location but the item is gone — treat it as not there and let the board
                // look for another one.
                setSuccess(false);
                terminate();
                return;
            }

            if (!ai.success()) {
                robot.unreachableEntityDetected(target);
                setSuccess(false);
                terminate();
            }
        }
    }

    @Override
    public void end() {
        if (target != null) {
            BoardRobotPicker.targettedItems.remove(target.getUUID());
        }
    }

    private void scanForItem() {
        double previousDistance = Double.MAX_VALUE;
        AABB box = AABB.ofSize(robot.position(), maxRange * 2, maxRange * 2, maxRange * 2);

        for (ItemEntity item : robot.level().getEntitiesOfClass(ItemEntity.class, box)) {
            if (item.isRemoved()
                    || BoardRobotPicker.targettedItems.contains(item.getUUID())
                    || robot.isKnownUnreachable(item)
                    || (zone != null && !zone.contains(item.position()))) {
                continue;
            }

            double dx = item.getX() - robot.position().x;
            double dy = item.getY() - robot.position().y;
            double dz = item.getZ() - robot.position().z;

            double sqrDistance = dx * dx + dy * dy + dz * dz;
            double maxDistance = maxRange * maxRange;

            if (sqrDistance >= maxDistance) {
                continue;
            }

            ItemStack stack = item.getItem();
            if (stackFilter != null && !stackFilter.matches(stack)) {
                continue;
            }

            // The drop must offer the robot at least something (dry-run on a copy — the scan never drains
            // it). 7.1.x accepted any drop the robot could PARTIALLY absorb (inject(...) > 0) and took what
            // fit, leaving the remainder; demanding a full fit would strand overflow drops forever.
            ItemStack leftover = robot.getTransactor().insert(stack.copy(), false, true);
            if (leftover.getCount() >= stack.getCount()) {
                continue;
            }

            if (target == null) {
                previousDistance = sqrDistance;
                target = item;
            } else if (sqrDistance < previousDistance) {
                previousDistance = sqrDistance;
                target = item;
            }
        }

        if (target != null) {
            BoardRobotPicker.targettedItems.add(target.getUUID());
            if (Math.floor(target.getX()) != Math.floor(robot.position().x)
                    || Math.floor(target.getY()) != Math.floor(robot.position().y)
                    || Math.floor(target.getZ()) != Math.floor(robot.position().z)) {
                startDelegateAI(new AIRobotGotoBlock(robot,
                        (int) Math.floor(target.getX()), (int) Math.floor(target.getY()),
                        (int) Math.floor(target.getZ())));
            }
        } else {
            // No item was found, terminate this AI
            setSuccess(false);
            terminate();
        }
    }

    @Override
    public long getPowerCost() {
        // 7.1.x charged 15 RF per tick; at the canonical 1 MJ = 10 RF bridge that is 15 * 100_000 micro-MJ.
        return 1_500_000;
    }
}
