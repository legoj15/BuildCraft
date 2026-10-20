/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import buildcraft.api.core.NbtApiUtil;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotAccess;

import buildcraft.lib.misc.FakePlayerUtil;
import buildcraft.robotics.RobotProtection;

/** Right-clicks the held tool on the block at {@code useToBlock} (the farmer's hoe-on-dirt), damaging the
 *  tool per use. Ported from 7.1.x {@code AIRobotUseToolOnBlock} (7.1.x used the block's
 *  {@code onBlockActivated}; the modern equivalent is the item's {@code useOn} through a
 *  {@code BlockHitResult}, with durability via {@code hurtAndBreak}).
 *
 *  <p>Two 7.1.x behaviours kept verbatim: on a FAILED use the hand is cleared and the held item dropped
 *  at the robot's feet only when it is undamageable (a damageable tool that failed — wrong block — is
 *  kept for the next attempt); and 7.1.x's NBT key was the typo {@code "blockFound"} — this port writes
 *  the field's real name (no 7.1.x save format is read, so nothing migrates).
 *
 *  <p>The tool is in the fake player's main hand for the use (so a claim or tool listener reading the hand sees
 *  it) and the robot takes back what is in that hand afterwards. */
public class AIRobotUseToolOnBlock extends AIRobot {

    private BlockPos useToBlock;
    private int useCycles = 0;

    public AIRobotUseToolOnBlock(IRobotAccess iRobot) {
        super(iRobot);
    }

    public AIRobotUseToolOnBlock(IRobotAccess iRobot, BlockPos index) {
        super(iRobot);

        useToBlock = index;
    }

    @Override
    public void start() {
        if (useToBlock != null) {
            robot.aimItemAt(useToBlock);
            robot.setItemActive(true);
        }
    }

    @Override
    public void update() {
        useCycles++;

        if (useCycles > 40) {
            if (useToBlock != null && robot.level() instanceof ServerLevel serverLevel) {
                ItemStack held = robot.getHeldItem();
                boolean used;
                ItemStack inHand;
                // As the home station's owner (the generic player when there is none), so a claim mod judges
                // the owner and an advancement the tool grants reaches them.
                try (FakePlayerUtil.Lease lease = RobotProtection.lease(robot, serverLevel)) {
                    FakePlayer player = lease.player();
                    player.setPos(robot.position());
                    // The tool is in the player's main hand for the use, as it would be for a real player: a claim
                    // or tool listener reads the hand, not the stack the context carries.
                    RobotProtection.holdInMainHand(player, held);
                    // A straight-up hit on the block's center, the modern form of 7.1.x's ForgeDirection.UP.
                    BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(useToBlock), Direction.UP, useToBlock, false);
                    // Ask first, as a player's right-click does (ServerPlayerGameMode.useItemOn posts this before
                    // the item's useOn): a claim mod refuses right-clicks through it on every node. On 26.3 it is
                    // the ONLY ask a till gets - hoes became a BLOCK_TRANSFORMER component and NeoForge no longer
                    // posts the tool-modification event for tilling.
                    PlayerInteractEvent.RightClickBlock ask =
                            CommonHooks.onRightClickBlock(player, InteractionHand.MAIN_HAND, useToBlock, hit);
                    if (ask.isCanceled() || ask.getUseItem().isFalse()) {
                        used = false;
                    } else {
                        UseOnContext useOnContext = new UseOnContext(serverLevel, player, InteractionHand.MAIN_HAND, held, hit);
                        used = held.useOn(useOnContext).consumesAction();
                    }
                    // Read before the lease closes and takes the hand's contents away.
                    inHand = player.getMainHandItem();
                }
                if (used) {
                    // The modern item damages itself inside useOn (a hoe's till does), so there is no
                    // 7.1.x-style explicit damageItem here — a tool wears exactly as it would in a
                    // player's hand, and a broken one leaves the hand empty. The robot takes back what is in
                    // the hand afterwards: the same (now worn) stack, unless the item swapped it for another.
                    if (inHand != held) {
                        robot.setItemInUse(inHand);
                    } else if (held.isEmpty()) {
                        robot.setItemInUse(ItemStack.EMPTY);
                    }
                } else {
                    setSuccess(false);
                    if (!held.isDamageableItem()) {
                        serverLevel.addFreshEntity(new ItemEntity(serverLevel,
                                robot.position().x, robot.position().y, robot.position().z, held));
                        robot.setItemInUse(ItemStack.EMPTY);
                    }
                }
            } else {
                setSuccess(false);
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
        // Ph5 cost table: 8 RF per tick, at the 1 RF = 100_000 micro-MJ bridge; 7.1.x left this AI on
        // the base default cost (no getPowerCost override in the 7.1.x source).
        return 800_000;
    }

    @Override
    public boolean canLoadFromNBT() {
        return true;
    }

    @Override
    public void writeSelfToNBT(CompoundTag nbt) {
        super.writeSelfToNBT(nbt);

        if (useToBlock != null) {
            int[] arr = {useToBlock.getX(), useToBlock.getY(), useToBlock.getZ()};
            nbt.putIntArray("useToBlock", arr);
        }
    }

    @Override
    public void loadSelfFromNBT(CompoundTag nbt) {
        super.loadSelfFromNBT(nbt);

        int[] arr = NbtApiUtil.getIntArray(nbt, "useToBlock", null);
        if (arr != null && arr.length == 3) {
            useToBlock = new BlockPos(arr[0], arr[1], arr[2]);
        }
    }
}
