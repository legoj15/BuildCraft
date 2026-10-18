/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.ai;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.core.EnumHandlerPriority;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.transport.IStripesActivator;
import buildcraft.api.transport.IStripesHandlerItem;
import buildcraft.api.transport.pipe.PipeApi;
import buildcraft.lib.test.EntityArenaUtil;
import buildcraft.robotics.BCRoboticsEntities;
import buildcraft.robotics.entity.EntityRobot;

/**
 * The Stripes board's use cycle ({@link AIRobotStripesHandler}) with a stack of blocks: ONE block goes down on the
 * reserved cell itself — not on a neighbour of it — and the rest of the stack stays in the robot's hand for the next
 * cell. Before the fix the whole remainder was deleted when the hand was cleared, and the block landed one cell
 * north of the reserved one (the handlers target {@code pos.relative(direction)}, the pipe-relative convention).
 * <p>
 * The swap tests use a test-only stripes handler that only ever acts on paper carrying its own marker name, so it is
 * inert for every other test (the registry has no removal).
 */
public class RobotStripesTester {

    private static final String SWAP_ASIDE = "bc_takeback_swap_aside";
    private static final String SWAP_GONE = "bc_takeback_swap_gone";
    private static boolean swapHandlerRegistered;

    /**
     * A third-party style handler that replaces the hand stack with a same-item stack of ANOTHER count: either after
     * setting the stack it was given aside in the player's inventory ({@link #SWAP_ASIDE}), or discarding it
     * ({@link #SWAP_GONE}).
     */
    private static final class SwapHandler implements IStripesHandlerItem {
        @Override
        public boolean handle(Level world, BlockPos pos, Direction direction, ItemStack stack, Player player,
                IStripesActivator activator) {
            Component name = stack.get(DataComponents.CUSTOM_NAME);
            String mode = name == null ? "" : name.getString();
            if (!mode.equals(SWAP_ASIDE) && !mode.equals(SWAP_GONE)) {
                return false;
            }
            if (mode.equals(SWAP_ASIDE)) {
                player.getInventory().setItem(9, stack);
            }
            player.setItemInHand(InteractionHand.MAIN_HAND, stack.copyWithCount(2));
            return true;
        }
    }

    private static synchronized void registerSwapHandler() {
        if (!swapHandlerRegistered) {
            swapHandlerRegistered = true;
            PipeApi.stripeRegistry.addHandler(new SwapHandler(), EnumHandlerPriority.HIGHEST);
        }
    }

    private static ItemStack marked(String mode, int count) {
        ItemStack stack = new ItemStack(Items.PAPER, count);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(mode));
        return stack;
    }

    /** One stripes use of a marked 5-paper stack; returns the robot, with the paper dropped at its feet in {@code drops[0]}. */
    private static EntityRobot useMarkedStack(GameTestHelper helper, String mode, int[] drops) {
        ServerLevel level = helper.getLevel();
        BlockPos target = helper.absolutePos(new BlockPos(2, 1, 3));

        EntityRobot robot = new EntityRobot(BCRoboticsEntities.ROBOT.get(), level);
        Vec3 at = Vec3.atCenterOf(helper.absolutePos(ROBOT_REL));
        robot.setPos(at.x, at.y, at.z);
        robot.getBattery().addPower(5000L * MjAPI.MJ, false);
        robot.setItemInUse(marked(mode, 5));
        AABB dropZone = new AABB(at, at).inflate(1.5);
        for (Entity e : level.getEntities((Entity) null, dropZone, e -> e instanceof ItemEntity)) {
            e.discard();
        }

        AIRobotStripesHandler handler = new AIRobotStripesHandler(robot, target);
        handler.start();
        // The use fires on the 61st cycle; stop there (a terminated AI cycled again would fire once more).
        for (int i = 0; i < 61; i++) {
            handler.cycle();
        }

        drops[0] = 0;
        for (Entity e : level.getEntities((Entity) null, dropZone, e -> e instanceof ItemEntity)) {
            if (e instanceof ItemEntity item && item.getItem().is(Items.PAPER)) {
                drops[0] += item.getItem().getCount();
            }
            e.discard();
        }
        return robot;
    }

    /**
     * A handler that sets the stack it was given aside in the player's inventory and puts a same-item stack of another
     * count in the hand: the robot's own stack is the one that was used, so it is what the robot keeps (5 paper, found
     * by identity); the stack the handler conjured into the hand is a product and is dropped. Before the fix the
     * hand's stack was kept by item type alone and the robot's own stack was the one dropped.
     */
    public static void stripesKeepsTheUsedStackWhenAHandlerSwapsTheHand(GameTestHelper helper) {
        registerSwapHandler();
        whenRobotCellIsEntityTicking(helper, () -> {
            int[] drops = new int[1];
            ItemStack held = useMarkedStack(helper, SWAP_ASIDE, drops).getHeldItem();

            helper.assertTrue(held.is(Items.PAPER) && held.getCount() == 5,
                    "the robot keeps its own used stack of 5, holds " + held);
            helper.assertTrue(drops[0] == 2,
                    "the stack the handler put in the hand is dropped (2), dropped " + drops[0]);
            helper.succeed();
        });
    }

    /**
     * A handler that discards the stack it was given and puts a same-item stack of another count in the hand: nothing
     * of the robot's own stack is left on the player, so the hand's stack is the remainder the robot keeps.
     */
    public static void stripesKeepsASwappedHandStackWhenTheUsedOneIsGone(GameTestHelper helper) {
        registerSwapHandler();
        whenRobotCellIsEntityTicking(helper, () -> {
            int[] drops = new int[1];
            ItemStack held = useMarkedStack(helper, SWAP_GONE, drops).getHeldItem();

            helper.assertTrue(held.is(Items.PAPER) && held.getCount() == 2,
                    "the robot keeps the swapped-in stack of 2, holds " + held);
            helper.assertTrue(drops[0] == 0, "nothing is dropped, dropped " + drops[0]);
            helper.succeed();
        });
    }

    /** The robot's cell in the test arena (the robot is never added to the level, only positioned there). */
    private static final BlockPos ROBOT_REL = new BlockPos(2, 2, 2);

    /** Runs {@code body}, which drops an item and scans for it in the same tick, once the robot's chunk is
     *  entity-ticking. The framework waits only for the 1x1 structure's own chunk, and {@code ROBOT_REL} lands in a
     *  neighbouring chunk whenever the arena sits near a chunk edge: an item dropped there is invisible to the scan
     *  (measured: the swap test saw 0 of its 2 dropped paper at tick 0, intermittently). Any item the body leaves
     *  behind is swept out of this cell on every exit. */
    private static void whenRobotCellIsEntityTicking(GameTestHelper helper, Runnable body) {
        EntityArenaUtil.forceLoadEntityArena(helper, ROBOT_REL);
        EntityArenaUtil.tickUntil(helper, 80, () -> EntityArenaUtil.isEntityTicking(helper, ROBOT_REL), body,
                "the robot's chunk never became entity-ticking",
                () -> helper.getLevel().getEntities((Entity) null, EntityArenaUtil.cellBounds(helper),
                        e -> e instanceof ItemEntity).forEach(Entity::discard));
    }

    public static void stripesPlacesOnReservedCellAndKeepsRemainder(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos robotRel = new BlockPos(2, 2, 2);
        BlockPos targetRel = new BlockPos(2, 1, 3);
        BlockPos target = helper.absolutePos(targetRel);

        EntityRobot robot = new EntityRobot(BCRoboticsEntities.ROBOT.get(), level);
        Vec3 at = Vec3.atCenterOf(helper.absolutePos(robotRel));
        robot.setPos(at.x, at.y, at.z);
        robot.getBattery().addPower(5000L * MjAPI.MJ, false);
        robot.setItemInUse(new ItemStack(Items.COBBLESTONE, 5));
        // Whatever the robot does not keep is dropped at its own position, so that is the only place checked —
        // a tight box well inside this 6x7 cell, cleared first so nothing already lying there is misread.
        AABB dropZone = new AABB(at, at).inflate(1.5);
        for (Entity e : level.getEntities((Entity) null, dropZone, e -> e instanceof ItemEntity)) {
            e.discard();
        }

        AIRobotStripesHandler handler = new AIRobotStripesHandler(robot, target);
        handler.start();
        // The use fires on the 61st cycle; stop there (a terminated AI cycled again would fire once more).
        for (int i = 0; i < 61; i++) {
            handler.cycle();
        }

        helper.assertTrue(level.getBlockState(target).is(Blocks.COBBLESTONE),
                "the block goes down on the reserved cell itself");
        helper.assertTrue(level.getBlockState(target.north()).isAir(), "nothing is placed beside the reserved cell");
        ItemStack held = robot.getHeldItem();
        helper.assertTrue(held.is(Items.COBBLESTONE) && held.getCount() == 4,
                "the robot keeps the 4 unplaced cobblestone in hand, holds " + held);

        List<Entity> dropped = level.getEntities((Entity) null, dropZone, e -> e instanceof ItemEntity);
        dropped.forEach(Entity::discard);
        helper.assertTrue(dropped.isEmpty(), "nothing is dropped at the robot's feet, found " + dropped);
        helper.succeed();
    }

    /**
     * A stripes use that no handler takes reports FAILURE (7.1.x reported nothing at all), so the board can tell
     * "used it" from "tried and could not" and stop looping on an item nothing can use. The item stays in hand and
     * the world is untouched; a handled use (cobblestone on the same cell) still reports success.
     */
    public static void stripesReportsFailureForAnItemNoHandlerCanUse(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos target = helper.absolutePos(new BlockPos(2, 1, 3));

        EntityRobot robot = new EntityRobot(BCRoboticsEntities.ROBOT.get(), level);
        Vec3 at = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
        robot.setPos(at.x, at.y, at.z);
        robot.getBattery().addPower(5000L * MjAPI.MJ, false);
        AABB dropZone = new AABB(at, at).inflate(1.5);
        for (Entity e : level.getEntities((Entity) null, dropZone, e -> e instanceof ItemEntity)) {
            e.discard();
        }

        robot.setItemInUse(new ItemStack(Items.STICK, 3));
        AIRobotStripesHandler unusable = new AIRobotStripesHandler(robot, target);
        unusable.start();
        for (int i = 0; i < 61; i++) {
            unusable.cycle();
        }
        helper.assertFalse(unusable.success(), "no handler can use a stick - the use must report failure");
        helper.assertTrue(ItemStack.matches(new ItemStack(Items.STICK, 3), robot.getHeldItem()),
                "a failed use leaves the item in hand, holds " + robot.getHeldItem());
        helper.assertTrue(level.getBlockState(target).isAir(), "a failed use changes nothing in the world");

        robot.setItemInUse(new ItemStack(Items.COBBLESTONE, 2));
        AIRobotStripesHandler usable = new AIRobotStripesHandler(robot, target);
        usable.start();
        for (int i = 0; i < 61; i++) {
            usable.cycle();
        }
        helper.assertTrue(usable.success(), "a handled use (cobblestone on air) reports success");
        helper.assertTrue(level.getBlockState(target).is(Blocks.COBBLESTONE), "...and the block went down");

        level.setBlock(target, Blocks.AIR.defaultBlockState(), 3);
        List<Entity> dropped = level.getEntities((Entity) null, dropZone, e -> e instanceof ItemEntity);
        dropped.forEach(Entity::discard);
        helper.succeed();
    }
}
