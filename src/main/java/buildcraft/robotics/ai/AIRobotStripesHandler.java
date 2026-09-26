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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.common.util.FakePlayer;

import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.api.transport.IStripesActivator;
import buildcraft.api.transport.pipe.PipeApi;

import buildcraft.lib.misc.FakePlayerUtil;
import buildcraft.robotics.RobotProtection;

/** The stripes board's item-use cycle: aim the held item at the reserved block, "use" it there through
 *  every registered stripes item handler (a fake BuildCraft player standing on the target, facing north,
 *  exactly as 7.1.x posed it), keeping what is left of the stack in hand after a handled use
 *  ({@link #takeBackFrom}). Ported from 7.1.x
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

            // Leased: the rotation set here never leaks into the next user of the shared player (a stripes
            // pipe placing blocks would otherwise inherit yRot 180), and closing the lease drops whatever the
            // handler put in its hands.
            // As the home station's owner (like the stripes pipe's owner), so a block placed in a claim is asked
            // about as that player, and an advancement a used item grants reaches them.
            try (FakePlayerUtil.Lease lease = FakePlayerUtil.lease((ServerLevel) robot.level(),
                    RobotProtection.ownerOf(robot), null)) {
                FakePlayer player = lease.player();
                player.setPos(useToBlock.getX() + 0.5, useToBlock.getY(), useToBlock.getZ() + 0.5);
                player.setXRot(0);
                player.setYRot(180);
                // In the player's hand, as the stripes pipe does it: handlers that act through the player
                // (shearing or milking a mob, using an item) read the hand, not their stack argument.
                player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                ItemStack original = stack.copy();

                // The handlers act on pos.relative(direction) (the stripes pipe's convention: pos is the pipe), so
                // they are handed the cell just behind the reserved one. 7.1.x's handlers took the target itself.
                BlockPos activatorPos = useToBlock.relative(direction.getOpposite());
                if (PipeApi.stripeRegistry.handleItem(robot.level(), activatorPos, direction, stack, player, this)) {
                    takeBackFrom(player, original);
                }
            }
            terminate();
        }
    }

    /**
     * After a handled use: what is left of the stack (a handler places or uses ONE item and shrinks the rest in
     * place) stays in the robot's hand for the next cell, and anything else the handler left on the player — a
     * filled bucket, a sheared-off drop — is dropped at the robot's feet, where 7.1.x's handlers dropped their
     * results. Before this the hand was simply cleared and the remainder of the stack deleted; 7.1.x itself dropped
     * the remainder on the ground. The stripes pipe does the same, sending it all back down the pipe.
     */
    private void takeBackFrom(FakePlayer player, ItemStack original) {
        ItemStack hand = player.getMainHandItem();
        ItemStack keep = !hand.isEmpty() && ItemStack.isSameItem(hand, original) ? hand : ItemStack.EMPTY;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack left = player.getInventory().removeItemNoUpdate(i);
            if (!left.isEmpty() && left != keep) {
                sendItem(left, Direction.NORTH);
            }
        }
        robot.setItemInUse(keep);
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
