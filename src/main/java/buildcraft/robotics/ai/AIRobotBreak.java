/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.ai;

import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayer;

import buildcraft.api.core.BuildCraftAPI;
import buildcraft.api.core.NbtApiUtil;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.lib.misc.BlockUtil;

/** Breaks {@code blockToBreak} with the held tool, replicating vanilla's destroy-progress math (hardness,
 *  tool speed, efficiency's squared bonus, the correct-tool 30-vs-100 divisor) a few ticks per cycle, then
 *  finishes the job through the in-tree {@code BlockUtil.breakBlockAndGetDrops} (tier gate + drops).
 *  Ported from 7.1.x {@code AIRobotBreak}.
 *
 *  <p>The per-cycle progress is one tick of {@link BlockState#getDestroyProgress} measured for a fresh
 *  BuildCraft fake player holding the robot's tool — the fake player is what carries the tool (and its
 *  efficiency enchantment) into vanilla's math, exactly as 7.1.x's {@code getFakePlayerWithTool} did.
 *  The 7.1.x crack-particle SFX is dropped; the crack overlay itself is kept through
 *  {@code destroyBlockProgress}. */
public class AIRobotBreak extends AIRobot {

    private BlockPos blockToBreak;
    private float blockDamage = 0;

    public AIRobotBreak(IRobotAccess iRobot) {
        super(iRobot);
    }

    public AIRobotBreak(IRobotAccess iRobot, BlockPos iBlockToBreak) {
        super(iRobot);

        blockToBreak = iBlockToBreak;
    }

    @Override
    public void start() {
        if (blockToBreak != null) {
            robot.aimItemAt(blockToBreak);
            robot.setItemActive(true);
        }
    }

    @Override
    public void update() {
        if (blockToBreak == null || !(robot.level() instanceof ServerLevel serverLevel)) {
            setSuccess(false);
            terminate();
            return;
        }

        BlockState state = serverLevel.getBlockState(blockToBreak);
        float hardness = state.getDestroySpeed(serverLevel, blockToBreak);
        if (state.isAir() || hardness < 0) {
            setSuccess(false);
            terminate();
            return;
        }

        // A fresh context-local fake player per cycle (the provider does not cache); the held tool goes in
        // its main hand so getDestroyProgress sees the tool speed and the efficiency bonus.
        FakePlayer player = BuildCraftAPI.fakePlayerProvider.getBuildCraftPlayer(serverLevel);
        player.setItemInHand(InteractionHand.MAIN_HAND, robot.getHeldItem());

        if (hardness != 0) {
            blockDamage += state.getDestroyProgress(player, serverLevel, blockToBreak);
        } else {
            // Instantly break the block (7.1.x forced 1.1F here).
            blockDamage = 1.1F;
        }

        if (blockDamage > 1.0F) {
            blockDamage = 0;
            ItemStack held = robot.getHeldItem();
            Optional<List<ItemStack>> drops = BlockUtil.breakBlockAndGetDrops(
                    serverLevel, blockToBreak, held, player.getGameProfile());
            if (drops.isPresent()) {
                for (ItemStack stack : drops.get()) {
                    // 7.1.x's harvestBlock dropped at the broken block; keep that (the harvester/plant
                    // AIs drop at the robot instead — each keeps its 7.1.x anchor).
                    serverLevel.addFreshEntity(new ItemEntity(serverLevel,
                            blockToBreak.getX() + 0.5, blockToBreak.getY() + 0.5, blockToBreak.getZ() + 0.5, stack));
                }
                // 7.1.x's onBlockDestroyed: one durability point, and an empty hand when it breaks.
                // The no-op break consumer matters: hurtAndBreak invokes it when the tool breaks, and a
                // robot has no player to notify (the 4-arg overload with a null consumer NPEs there).
                if (held.isDamageableItem()) {
                    held.hurtAndBreak(1, serverLevel, null, item -> {
                    });
                    if (held.isEmpty()) {
                        robot.setItemInUse(ItemStack.EMPTY);
                    }
                }
            } else {
                setSuccess(false);
            }
            serverLevel.destroyBlockProgress(robot.getId(), blockToBreak, -1);
            terminate();
        } else {
            serverLevel.destroyBlockProgress(robot.getId(), blockToBreak, (int) (blockDamage * 10.0F) - 1);
        }
    }

    @Override
    public void end() {
        robot.setItemActive(false);
        if (blockToBreak != null && robot.level() instanceof ServerLevel serverLevel) {
            serverLevel.destroyBlockProgress(robot.getId(), blockToBreak, -1);
        }
    }

    @Override
    public long getPowerCost() {
        // 7.1.x charged ceil(BREAK_ENERGY * 2 / 30) = 11 RF; at the 1 RF = 100_000 micro-MJ bridge.
        return 1_100_000;
    }

    @Override
    public boolean canLoadFromNBT() {
        return true;
    }

    @Override
    public void writeSelfToNBT(CompoundTag nbt) {
        super.writeSelfToNBT(nbt);

        if (blockToBreak != null) {
            int[] arr = {blockToBreak.getX(), blockToBreak.getY(), blockToBreak.getZ()};
            nbt.putIntArray("blockToBreak", arr);
        }
    }

    @Override
    public void loadSelfFromNBT(CompoundTag nbt) {
        super.loadSelfFromNBT(nbt);

        int[] arr = NbtApiUtil.getIntArray(nbt, "blockToBreak", null);
        if (arr != null && arr.length == 3) {
            blockToBreak = new BlockPos(arr[0], arr[1], arr[2]);
        }
    }
}
