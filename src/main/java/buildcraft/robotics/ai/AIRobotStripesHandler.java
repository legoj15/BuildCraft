/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.common.util.FakePlayer;

import buildcraft.api.core.BuildCraftAPI;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.api.transport.IStripesActivator;
import buildcraft.api.transport.pipe.PipeApi;

/** The stripes board's item-use cycle: aim the held item at the reserved block, "use" it there through
 *  every registered stripes item handler (a fake BuildCraft player standing on the target, facing north,
 *  exactly as 7.1.x posed it), and clear the hand when a handler consumed the item. Ported from 7.1.x
 *  {@code AIRobotStripesHandler}: 7.1.x's manual iteration over {@code PipeManager.stripesHandlers} is
 *  the modern single {@link PipeApi#stripeRegistry}{@code .handleItem} call, which walks the same
 *  handler set the stripes pipe uses. Both {@link IStripesActivator} outputs drop the stack at the
 *  robot's feet, as 7.1.x's {@code InvUtils.dropItems} did — a robot has no pipe to send through. */
public class AIRobotStripesHandler extends AIRobot implements IStripesActivator {
    private BlockPos useToBlock;
    private int useCycles = 0;

    public AIRobotStripesHandler(IRobotAccess iRobot) {
        super(iRobot);
    }

    public AIRobotStripesHandler(IRobotAccess iRobot, BlockPos index) {
        this(iRobot);

        useToBlock = index;
    }

    @Override
    public void start() {
        robot.aimItemAt(useToBlock);
        robot.setItemActive(true);
    }

    @Override
    public void update() {
        if (useToBlock == null) {
            setSuccess(false);
            terminate();
            return;
        }

        useCycles++;

        if (useCycles > 60) {
            ItemStack stack = robot.getHeldItem();

            Direction direction = Direction.NORTH;

            FakePlayer player = BuildCraftAPI.fakePlayerProvider.getBuildCraftPlayer(
                    (ServerLevel) robot.level());
            player.setPos(useToBlock.getX() + 0.5, useToBlock.getY(), useToBlock.getZ() + 0.5);
            player.setXRot(0);
            player.setYRot(180);

            if (PipeApi.stripeRegistry.handleItem(robot.level(), useToBlock, direction, stack, player, this)) {
                robot.setItemInUse(ItemStack.EMPTY);
            }
            terminate();
        }
    }

    @Override
    public void end() {
        robot.setItemActive(false);
    }

    @Override
    public long getPowerCost() {
        // 7.1.x charged 15 RF per handler cycle.
        return 1_500_000;
    }

    @Override
    public boolean sendItem(ItemStack stack, Direction from) {
        ItemEntity entity = new ItemEntity(
                robot.level(),
                robot.position().x,
                robot.position().y,
                robot.position().z,
                stack);
        return robot.level().addFreshEntity(entity);
    }

    @Override
    public void dropItem(ItemStack stack, Direction from) {
        sendItem(stack, from);
    }
}
