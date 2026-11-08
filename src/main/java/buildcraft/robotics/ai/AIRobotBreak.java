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

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.core.NbtApiUtil;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.core.BCCoreConfig;
import buildcraft.lib.misc.BlockUtil;
import buildcraft.lib.misc.FakePlayerUtil;
import buildcraft.robotics.RobotProtection;

/** Breaks {@code blockToBreak} with the held tool, replicating vanilla's destroy-progress math (hardness,
 *  tool speed, efficiency's squared bonus, the correct-tool 30-vs-100 divisor) a few ticks per cycle, then
 *  finishes the job the way a player's break does: through the leased fake player's own
 *  {@code ServerPlayerGameMode.destroyBlock}. That posts the loader's block-break event for the fake player (so a
 *  mod that reacts to a break - a tree-felling mod, a claim mod, a logger - sees a robot's as anyone's, and a
 *  cancelled event leaves the block and fails the AI), wears the robot's own tool through the item's mine hook
 *  (one durability point for a tool that takes damage from breaking blocks), applies the tier gate (a tool that is not
 *  the right one destroys the block without drops, as before) and spawns the drops and experience at the block.
 *  Ported from 7.1.x {@code AIRobotBreak}.
 *
 *  <p>The per-cycle progress is one tick of {@link BlockState#getDestroyProgress} measured for a freshly reset
 *  fake player holding the robot's tool — the fake player is what carries the tool (and its
 *  efficiency enchantment) into vanilla's math, exactly as 7.1.x's {@code getFakePlayerWithTool} did. It is the
 *  fake player of the identity the robot acts as ({@link RobotProtection#identityOf}: its deployer, or its home
 *  station's owner for a robot with no recorded deployer), so the events that maths posts - the break-speed and
 *  harvest checks a claim mod may listen to - name that player, one of the two the permission probe below asks
 *  about.
 *  The 7.1.x crack-particle SFX is dropped; the crack overlay itself is kept through
 *  {@code destroyBlockProgress}.
 *
 *  <p>Before the first swing it asks protection mods, for the robot's deployer and its home station's owner alike
 *  ({@link RobotProtection#canBreak}); a refusal fails the AI without touching the block. The break then posts the
 *  event again, for real, and a protection mod may still refuse that one.
 *
 *  <p>While {@link BCCoreConfig#minePlayerProtected} is on - the "ignore protection mods" override that
 *  {@code RobotProtection#canBreak} already honours by asking nobody - the robot breaks silently through
 *  {@link BlockUtil#breakBlockAndGetDrops} as it did before it broke like a player: the real event would otherwise
 *  hand the same mods a veto the override is there to remove. */
public class AIRobotBreak extends AIRobot {

    private BlockPos blockToBreak;
    private float blockDamage = 0;
    /** Whether protection mods let this robot break {@code blockToBreak}; null until asked. */
    private Boolean permitted = null;

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

        // Ask protection mods once, before the first swing (7.1.x asked through its harvest call's break event; here
        // it is a probe, and the break itself posts the event again for real). Not persisted: a reloaded break asks
        // again. The answer sticks, so a refused AI stays refused however often it is cycled.
        if (permitted == null) {
            permitted = RobotProtection.canBreak(robot, serverLevel, blockToBreak);
        }
        if (!permitted) {
            setSuccess(false);
            terminate();
            return;
        }

        // The cached fake player, leased (reset on lease, scrubbed on close) for this cycle, as the identity the robot
        // acts as (its deployer; the station's owner when none is recorded) - one the permission probe above asked
        // about, so a break-speed or harvest listener and a claim mod see the same player. The held tool goes in its
        // main hand so getDestroyProgress sees the tool speed and the efficiency bonus.
        try (FakePlayerUtil.Lease lease = RobotProtection.lease(robot, serverLevel)) {
            breakCycle(serverLevel, state, hardness, lease.player());
        }
    }

    private void breakCycle(ServerLevel serverLevel, BlockState state, float hardness, ServerPlayer player) {
        RobotProtection.holdInMainHand(player, robot.getHeldItem());
        // The same never-ticked-player trap as the hand bridge there, part two: vanilla's getDestroySpeed divides by 5 for a
        // miner that is not on the ground, and a fresh fake player's flag is false — a diamond pickaxe
        // read 1.6 instead of 8, and stone needed 151 cycles instead of 6. 7.1.x read the tool's dig
        // speed directly, with no player-standing penalty at all, so the flag is pinned instead.
        player.setOnGround(true);

        if (hardness != 0) {
            blockDamage += state.getDestroyProgress(player, serverLevel, blockToBreak);
        } else {
            // Instantly break the block (7.1.x forced 1.1F here).
            blockDamage = 1.1F;
        }

        if (blockDamage > 1.0F) {
            blockDamage = 0;
            if (BCCoreConfig.minePlayerProtected.get()) {
                breakSilently(serverLevel, player);
            } else {
                breakAsPlayer(serverLevel, state, player);
            }
            serverLevel.destroyBlockProgress(robot.getId(), blockToBreak, -1);
            terminate();
        } else {
            serverLevel.destroyBlockProgress(robot.getId(), blockToBreak, (int) (blockDamage * 10.0F) - 1);
        }
    }

    /** The player's break: {@code ServerPlayerGameMode.destroyBlock} for the leased fake player, which holds the
     *  robot's own tool stack (never a copy), so the wear lands on it. Succeeds when the block is no longer the state
     *  that was there: a cancelled break event, or a block that refused to go (its own removal hook answered no),
     *  leaves it and fails the AI. The effect decides, not the method's answer: a mod that cancels the event and
     *  takes the block down itself (a tree-felling mod does, with the rest of the tree) has still done the job. The
     *  support cascade the removal sets off is re-synced to clients afterwards, as
     *  {@link BlockUtil#breakBlockAndGetDropsWithXp} does for the machines. */
    private void breakAsPlayer(ServerLevel serverLevel, BlockState state, ServerPlayer player) {
        boolean hadTool = !robot.getHeldItem().isEmpty();
        Long2ObjectOpenHashMap<BlockState> supportsBefore = BlockUtil.snapshotSupportsForResync(serverLevel, blockToBreak);
        player.gameMode.destroyBlock(blockToBreak);
        BlockUtil.resyncChangedBlocks(serverLevel, supportsBefore);
        if (serverLevel.getBlockState(blockToBreak) == state) {
            setSuccess(false);
        }
        // The tool broke on this block: the hand was left with the emptied stack, and the robot goes and fetches one.
        if (hadTool && robot.getHeldItem().isEmpty()) {
            robot.setItemInUse(ItemStack.EMPTY);
        }
    }

    /** The break that asks and tells nobody ({@link BCCoreConfig#minePlayerProtected} on): the block is removed by
     *  the leased player without its break event, the drops are placed at the block and the tool is charged by hand.
     *  Kept exactly as the port broke before it broke like a player. */
    private void breakSilently(ServerLevel serverLevel, ServerPlayer player) {
        ItemStack held = robot.getHeldItem();
        // By the leased player itself (the acting identity's, or the generic one when that has no name).
        Optional<List<ItemStack>> drops = BlockUtil.breakBlockAndGetDrops(serverLevel, blockToBreak, held, player);
        if (drops.isPresent()) {
            for (ItemStack stack : drops.get()) {
                // 7.1.x's harvestBlock dropped at the broken block; keep that (the harvester/plant
                // AIs drop at the robot instead - each keeps its 7.1.x anchor).
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
