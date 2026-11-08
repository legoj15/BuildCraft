/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.core.BCLog;
import buildcraft.api.mj.MjAPI;
import buildcraft.lib.test.EntityArenaUtil;
import buildcraft.platform.Platform;
import buildcraft.platform.event.PlatformProtection;
import buildcraft.robotics.ZonePinnedRobot;
import buildcraft.robotics.boards.BoardRobotLumberjackNBT;
import buildcraft.robotics.entity.EntityRobot;

/**
 * A Lumberjack robot against a REAL tree-felling mod (FallingTree). A robot's break is the leased fake player's own
 * {@code ServerPlayerGameMode.destroyBlock}, so the loader's block-break event is posted and a mod that fells the
 * whole tree from one cut sees a robot's cut as anyone's. The mod is opt-in in the dev runtime
 * ({@code -PwithFallingTree}, on the 26.3 and 26.3-fabric nodes); without it the test succeeds at once, so a normal
 * run is unchanged and the test only means something when the flag is given.
 *
 * <p>What it pins, with the mod loaded: the whole trunk comes down together from the Lumberjack's one cut at the
 * base (not log by log), the axe is charged for the extra logs, and every log drops; and, the risk this test exists
 * for, that nothing is felled by BuildCraft's permission PROBE. The probe ({@code BlockUtil.canMachineBreak}) posts
 * the same break event a real break does, at search approval while the robot is still on its way, and a tree feller
 * cannot tell it from a break: it would fell the tree during the ask if the probe's player held an axe. It holds
 * nothing (a leased fake player is reset empty), and the watch below fails the test the moment a log disappears
 * before the robot's own break event at the base has been posted, and that at least one probe was posted for the trunk
 * and every probe's player held nothing (so "the probe does not fell" is earned, not vacuous).
 *
 * <p>The exact wear and drop counts are FallingTree's defaults. It persists its config in the run directory's
 * {@code config/fallingtree.json}, so a manually edited file there changes the result.
 */
public class RobotFallingTreeTester {

    /** The mod id of FallingTree, on both loaders. */
    private static final String FALLING_TREE = "fallingtree";

    /** System property the build sets on the game-test run when {@code -PwithFallingTree} put the mod on its classpath:
     *  the mod is then expected, and not finding it loaded fails the test. */
    private static final String EXPECTED_PROPERTY = "buildcraft.test.fallingTreeExpected";

    /** Logs in the trunk: more than the two or three the vine tests chop, so a one-by-one chop is unmistakable. */
    private static final int TRUNK_LOGS = 4;

    /** Charge above the {@code AIRobotMain} ladder's shutdown floor, as the other lumberjack E2Es seed it. */
    private static final long SEEDED_CHARGE = 5000L * MjAPI.MJ;

    /** The work budget of the poll, counted from the arena cell ticking (the poll's own budget adds
     *  {@code EntityArenaUtil.CELL_READY_TICKS}, and the manifest's {@code max_ticks} stays above that sum). The
     *  robot's flight, the break and the felling take ~100 ticks once the cell ticks; this is the same generous
     *  allowance the bare-trunk chop gets. */
    private static final int FELL_WORK_TICKS = 900;

    /** How many ticks may lie between the first log disappearing and the last: a felling takes the trunk in the one
     *  tick of the robot's cut, a Lumberjack chopping the base-up column on its own needs a flight and a break cycle
     *  per log. */
    private static final int MAX_FELL_SPREAD_TICKS = 2;

    /** Trunk base, relative to the arena; the logs stand above it. Away from the framework's barrier corner. */
    private static final BlockPos BASE_REL = new BlockPos(3, 2, 3);

    /**
     * A four-log trunk on a dirt block with a leaf canopy, and a Lumberjack robot with a diamond axe set on it.
     * With FallingTree loaded the robot's first cut must bring the whole trunk down.
     */
    public static void lumberjackRobotFellsTheWholeTreeWithFallingTree(GameTestHelper helper) {
        if (!Platform.loader().isModLoaded(FALLING_TREE)) {
            if (Boolean.getBoolean(EXPECTED_PROPERTY)) {
                helper.fail("-PwithFallingTree was given (" + EXPECTED_PROPERTY + " is set) but the mod '" + FALLING_TREE
                        + "' is not loaded: the check below would not have run");
            }
            BCLog.logger.info("[robot_break_fallingtree_fells_whole_tree] FallingTree is not loaded; nothing to check"
                    + " (run with -PwithFallingTree)");
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();
        // The whole cell, not just the robot's chunk: the tree can sit in the chunk next door.
        EntityArenaUtil.forceLoadCell(helper);

        List<BlockPos> logs = buildTree(helper, level);
        BlockPos base = logs.get(0);
        Watch watch = new Watch(level, logs, base);

        EntityRobot robot = new ZonePinnedRobot(helper, BoardRobotLumberjackNBT.INSTANCE);
        Vec3 pos = Vec3.atCenterOf(helper.absolutePos(new BlockPos(4, 2, 5)));
        robot.setPos(pos.x, pos.y, pos.z);
        robot.getBattery().addPower(SEEDED_CHARGE, false);
        // Pre-equipped: the fetch-and-equip leg needs a supply station, which this arena has none of.
        robot.setItemInUse(new ItemStack(Items.DIAMOND_AXE));
        level.addFreshEntity(robot);

        PlatformProtection.Registration listener = Platform.protection().onBreak(watch::onBreak);
        EntityArenaUtil.tickUntil(helper, EntityArenaUtil.CELL_READY_TICKS + FELL_WORK_TICKS,
                EntityArenaUtil.whenCellTicking(helper, EntityArenaUtil.CELL_READY_TICKS, FELL_WORK_TICKS,
                        () -> watch.poll(helper), watch::stalled),
                () -> {
                    long spread = watch.allGoneAt - watch.firstGoneAt;
                    int wear = robot.getHeldItem().getDamageValue();
                    int logItems = countDroppedLogs(helper, level);
                    BCLog.logger.info("[robot_break_fallingtree_fells_whole_tree] tick {}: first log gone, tick {}: all"
                                    + " {} gone (spread {} ticks); the robot's real break event at the base on tick {};"
                                    + " axe damage {}; {} oak log items on the ground; permission probes seen: {}"
                                    + " (probe player's main hand: {}); break events by position: {}",
                            watch.firstGoneAt, watch.allGoneAt, TRUNK_LOGS, spread, watch.realBaseEventAt, wear,
                            logItems, watch.probes.size(), watch.probeHands, watch.realEvents);
                    helper.assertTrue(spread <= MAX_FELL_SPREAD_TICKS, "the trunk came down log by log, over " + spread
                            + " ticks (first log gone on tick " + watch.firstGoneAt + ", last on " + watch.allGoneAt
                            + "), not felled at once by FallingTree");
                    helper.assertTrue(watch.realBaseEventAt >= 0,
                            "no real break event was posted at the base (the poll fails a log that goes before one)");
                    // The probes must have happened, and held nothing, for "the probe did not fell the tree" to mean
                    // anything: a probe whose player held an axe would have felled it (the poll above fails that).
                    helper.assertTrue(!watch.probes.isEmpty(),
                            "no permission probe was posted for the trunk, so the probe check proved nothing");
                    helper.assertTrue(watch.probeHands.stream().allMatch("empty"::equals),
                            "a permission probe's player held " + watch.probeHands + " in its main hand, not nothing");
                    // One point for the cut and one per extra log (FallingTree's default), so the same as chopping them one
                    // by one: more is a double charge (the robot's own wear on top of the break's), fewer a free log.
                    helper.assertTrue(wear == TRUNK_LOGS, "the axe was charged " + wear + " durability for a "
                            + TRUNK_LOGS + "-log tree, not one point per log");
                    helper.assertTrue(logItems == TRUNK_LOGS, logItems + " oak logs dropped, not " + TRUNK_LOGS);
                    helper.succeed();
                },
                "the Lumberjack robot never felled the whole trunk",
                () -> {
                    listener.close();
                    cleanUp(helper, level, robot);
                });
    }

    /** Dirt, {@link #TRUNK_LOGS} oak logs above it (base first) and a natural leaf canopy over the top three. */
    private static List<BlockPos> buildTree(GameTestHelper helper, ServerLevel level) {
        BlockPos baseRel = BASE_REL;
        level.setBlock(helper.absolutePos(baseRel.below()), Blocks.DIRT.defaultBlockState(), 3);
        List<BlockPos> logs = new ArrayList<>();
        for (int i = 0; i < TRUNK_LOGS; i++) {
            BlockPos log = helper.absolutePos(baseRel.above(i));
            level.setBlock(log, Blocks.OAK_LOG.defaultBlockState(), 3);
            logs.add(log);
        }
        BlockState leaf = Blocks.OAK_LEAVES.defaultBlockState()
                .setValue(LeavesBlock.DISTANCE, 1)
                .setValue(LeavesBlock.PERSISTENT, false);
        // Rings around the upper trunk and a cap on top; the arena cell ends at relative y 6.
        for (int y = baseRel.getY() + 2; y <= baseRel.getY() + 4; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos rel = new BlockPos(baseRel.getX() + dx, y, baseRel.getZ() + dz);
                    BlockPos abs = helper.absolutePos(rel);
                    if (level.getBlockState(abs).isAir()) {
                        level.setBlock(abs, leaf, 3);
                    }
                }
            }
        }
        for (BlockPos log : logs) {
            helper.assertTrue(level.getBlockState(log).is(Blocks.OAK_LOG), "precondition: log at " + log);
        }
        return logs;
    }

    private static int standing(ServerLevel level, List<BlockPos> logs) {
        int standing = 0;
        for (BlockPos log : logs) {
            if (level.getBlockState(log).is(Blocks.OAK_LOG)) {
                standing++;
            }
        }
        return standing;
    }

    private static int countDroppedLogs(GameTestHelper helper, ServerLevel level) {
        int total = 0;
        for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, EntityArenaUtil.cellBounds(helper))) {
            if (drop.getItem().is(Items.OAK_LOG)) {
                total += drop.getItem().getCount();
            }
        }
        return total;
    }

    /** Every exit: the robot goes (it would keep scanning, or sleeping, past the test), and so do the drops. The sweep
     *  is this arena cell exactly. */
    private static void cleanUp(GameTestHelper helper, ServerLevel level, EntityRobot robot) {
        EntityArenaUtil.discard(robot);
        for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, EntityArenaUtil.cellBounds(helper))) {
            drop.discard();
        }
    }

    /** Watches the trunk across the poll's ticks and the break events posted for it. */
    private static final class Watch {
        private final ServerLevel level;
        private final List<BlockPos> logs;
        private final Set<BlockPos> logSet;
        private final BlockPos base;

        /** Game time the first log was seen gone / the last one; -1 until then. */
        long firstGoneAt = -1;
        long allGoneAt = -1;
        /** Game time the robot's real (non-probe) break event at the base was posted; -1 until then. */
        long realBaseEventAt = -1;
        /** Permission probes posted for a trunk log, with what the probe's player held in its main hand. */
        final List<BlockPos> probes = new ArrayList<>();
        final List<String> probeHands = new ArrayList<>();
        /** Every real break event posted for a trunk log, as "offset-from-base@tick". */
        final List<String> realEvents = new ArrayList<>();

        Watch(ServerLevel level, List<BlockPos> logs, BlockPos base) {
            this.level = level;
            this.logs = logs;
            this.logSet = Set.copyOf(logs);
            this.base = base;
        }

        void onBreak(PlatformProtection.BlockBreak event) {
            BlockPos at = event.pos();
            if (!logSet.contains(at)) {
                return; // another arena's break
            }
            if (event.isProbe()) {
                probes.add(at.immutable());
                probeHands.add(event.player().getMainHandItem().isEmpty() ? "empty"
                        : event.player().getMainHandItem().getItem().toString());
                return;
            }
            if (at.equals(base) && realBaseEventAt < 0) {
                realBaseEventAt = level.getGameTime();
            }
            realEvents.add("+" + (at.getY() - base.getY()) + "@" + level.getGameTime());
        }

        /** One tick's poll: fails the moment a log is gone with no real break event at the base behind it (the probe
         *  felling the tree, or anything else taking a log), records when the first and last logs went, and answers
         *  whether the trunk is gone. */
        boolean poll(GameTestHelper helper) {
            long now = level.getGameTime();
            int standing = standing(level, logs);
            if (standing < logs.size() && firstGoneAt < 0) {
                firstGoneAt = now;
                helper.assertTrue(realBaseEventAt >= 0, "a log disappeared on tick " + now + " before the robot's real"
                        + " break event at the base (probes so far: " + probes.size() + ", the probe player's main hand: "
                        + probeHands + "): the tree was felled by something other than the robot's cut");
            }
            if (standing == 0) {
                allGoneAt = now;
                return true;
            }
            return false;
        }

        String stalled() {
            return standing(level, logs) + " of " + logs.size() + " logs still stand; the robot's real break event at the"
                    + " base: " + (realBaseEventAt >= 0 ? "tick " + realBaseEventAt : "never posted") + "; probes "
                    + probes.size() + ", real events " + realEvents;
        }
    }
}
