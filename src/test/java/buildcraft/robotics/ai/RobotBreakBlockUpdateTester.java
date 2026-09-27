/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.ai;

import java.util.List;
import java.util.Optional;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.mj.MjAPI;
import buildcraft.lib.misc.BlockUtil;
import buildcraft.lib.test.EntityArenaUtil;
import buildcraft.robotics.BCRoboticsEntities;
import buildcraft.robotics.boards.BoardRobotLumberjackNBT;
import buildcraft.robotics.entity.EntityRobot;

/**
 * Regression coverage for the user-reported "lumberjack robots leave behind unsupported vines" bug.
 * <p>
 * The robot break path is {@link AIRobotBreak} → {@link BlockUtil#breakBlockAndGetDrops} →
 * {@code Level.destroyBlock(pos, false)}. Vines (and every other support-driven block — torches,
 * ladders, rails, redstone...) only pop when the removal reaches them as a neighbour/shape update,
 * so these tests pin that the shared break helper keeps flag-3 semantics: after the tree is chopped,
 * nothing unsupported may be left hanging.
 */
public class RobotBreakBlockUpdateTester {

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) throw new IllegalStateException(msg);
    }

    /** One log with a vine clinging to its side; chopping the log must pop the vine. */
    public static void testBreakPopsDirectlyAttachedVine(GameTestHelper helper) {
        try {
            ServerLevel level = (ServerLevel) helper.getLevel();
            BlockPos logPos = helper.absolutePos(new BlockPos(2, 2, 2));
            BlockPos vinePos = logPos.south();
            level.setBlock(logPos, Blocks.OAK_LOG.defaultBlockState(), 3);
            level.setBlock(vinePos, Blocks.VINE.defaultBlockState().setValue(VineBlock.NORTH, true), 3);
            assertTrue(level.getBlockState(vinePos).is(Blocks.VINE), "precondition: vine placed");

            Optional<List<ItemStack>> result = BlockUtil.breakBlockAndGetDrops(
                level, logPos, new ItemStack(Items.DIAMOND_AXE), (GameProfile) null);

            assertTrue(result.isPresent(), "log break must succeed");
            assertTrue(level.getBlockState(logPos).isAir(), "log position must be air after break");
            assertTrue(level.getBlockState(vinePos).isAir(),
                    "vine clinging to a broken log must pop (neighbour update must fire)");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /**
     * The jungle-tree shape: a two-tall trunk, each segment with a vine column beside it (every
     * vine clings to the trunk, and each also "hangs" from the vine above). The lumberjack chops
     * the whole trunk, so every support is gone — the whole vine column must cascade down through
     * the chained shape updates, not just the vine next to the last chopped log.
     */
    public static void testFullChopCascadesVineColumn(GameTestHelper helper) {
        try {
            ServerLevel level = (ServerLevel) helper.getLevel();
            BlockPos logBottom = helper.absolutePos(new BlockPos(2, 2, 2));
            BlockPos logTop = logBottom.above();
            BlockPos vineBottom = logBottom.south();
            BlockPos vineTop = logTop.south();
            level.setBlock(logBottom, Blocks.OAK_LOG.defaultBlockState(), 3);
            level.setBlock(logTop, Blocks.OAK_LOG.defaultBlockState(), 3);
            level.setBlock(vineBottom, Blocks.VINE.defaultBlockState().setValue(VineBlock.NORTH, true), 3);
            level.setBlock(vineTop, Blocks.VINE.defaultBlockState().setValue(VineBlock.NORTH, true), 3);
            assertTrue(level.getBlockState(vineBottom).is(Blocks.VINE), "precondition: lower vine placed");
            assertTrue(level.getBlockState(vineTop).is(Blocks.VINE), "precondition: upper vine placed");

            Optional<List<ItemStack>> bottom = BlockUtil.breakBlockAndGetDrops(
                level, logBottom, new ItemStack(Items.DIAMOND_AXE), (GameProfile) null);
            Optional<List<ItemStack>> top = BlockUtil.breakBlockAndGetDrops(
                level, logTop, new ItemStack(Items.DIAMOND_AXE), (GameProfile) null);

            assertTrue(bottom.isPresent() && top.isPresent(), "both log breaks must succeed");
            assertTrue(level.getBlockState(logBottom).isAir() && level.getBlockState(logTop).isAir(),
                    "trunk must be gone");
            assertTrue(level.getBlockState(vineTop).isAir(),
                    "top vine must pop once its trunk segment is chopped");
            assertTrue(level.getBlockState(vineBottom).isAir(),
                    "lower vine must cascade — it was hanging only from the top vine");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /**
     * The Quarry and Mining Well remove blocks with {@code Level.removeBlock(pos, false)} rather
     * than the BlockUtil helper ("could be widespread" — pin both removal routes). Same vine
     * scenario: removing a supported block must pop what clings to it.
     */
    public static void testRemoveBlockPopsAttachedVine(GameTestHelper helper) {
        try {
            ServerLevel level = (ServerLevel) helper.getLevel();
            BlockPos logPos = helper.absolutePos(new BlockPos(2, 2, 2));
            BlockPos vinePos = logPos.south();
            level.setBlock(logPos, Blocks.OAK_LOG.defaultBlockState(), 3);
            level.setBlock(vinePos, Blocks.VINE.defaultBlockState().setValue(VineBlock.NORTH, true), 3);

            level.removeBlock(logPos, false);

            assertTrue(level.getBlockState(logPos).isAir(), "log position must be air after removeBlock");
            assertTrue(level.getBlockState(vinePos).isAir(),
                    "vine must pop when its support is removed via Level.removeBlock");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /** Charge above the {@code AIRobotMain} ladder's shutdown floor (same seeding as the picker/carrier
     *  E2Es), enough for a three-log chop at the 1.1 MJ-per-break + per-tick costs. */
    private static final long SEEDED_CHARGE = 5000L * MjAPI.MJ;

    /**
     * The user report end to end: a REAL lumberjack robot (live {@code AIRobotMain} tree — search,
     * fly, equip check, {@link AIRobotBreak}) sent against a vined tree. Once every log is gone, no
     * vine may be left floating: each vine's support chain ends at a log, and the log that closes the
     * chain must cascade its whole column down through the neighbour updates fired by the break.
     *
     * <p>Tree: a three-tall oak trunk; side vines on every segment (the classic jungle/swamp hang),
     * a vine clinging to the trunk's other side at the top, and a hanging segment below the bottom
     * vine that is supported by nothing but the vine above it — the longest cascade in the batch.
     */
    public static void testLumberjackRobotChopsTreeAndDropsItsVines(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos robotRel = new BlockPos(4, 3, 3);
        EntityArenaUtil.forceLoadEntityArena(helper, robotRel);

        BlockPos logBottom = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos logMid = logBottom.above();
        BlockPos logTop = logMid.above();
        BlockPos vineBottomSide = helper.absolutePos(new BlockPos(2, 2, 3));
        BlockPos vineMidSide = helper.absolutePos(new BlockPos(2, 3, 3));
        BlockPos vineTopSide = helper.absolutePos(new BlockPos(2, 4, 3));
        BlockPos vineTopOtherSide = helper.absolutePos(new BlockPos(1, 4, 2));
        BlockPos vineHangingSegment = helper.absolutePos(new BlockPos(2, 1, 3));

        level.setBlock(logBottom, Blocks.OAK_LOG.defaultBlockState(), 3);
        level.setBlock(logMid, Blocks.OAK_LOG.defaultBlockState(), 3);
        level.setBlock(logTop, Blocks.OAK_LOG.defaultBlockState(), 3);
        level.setBlock(vineBottomSide, Blocks.VINE.defaultBlockState().setValue(VineBlock.NORTH, true), 3);
        level.setBlock(vineMidSide, Blocks.VINE.defaultBlockState().setValue(VineBlock.NORTH, true), 3);
        level.setBlock(vineTopSide, Blocks.VINE.defaultBlockState().setValue(VineBlock.NORTH, true), 3);
        level.setBlock(vineTopOtherSide, Blocks.VINE.defaultBlockState().setValue(VineBlock.EAST, true), 3);

        List<BlockPos> vines = List.of(vineBottomSide, vineMidSide, vineTopSide, vineTopOtherSide,
                vineHangingSegment);

        // Hanging below vineBottomSide: its north neighbour is air; it survives only via the
        // above-vine rule, so it pops last in the cascade.
        level.setBlock(vineHangingSegment, Blocks.VINE.defaultBlockState().setValue(VineBlock.NORTH, true), 3);
        // Precondition: every vine must still be standing BEFORE the robot starts — a vine lost to a
        // placement-time update would let the cascade assertion pass without exercising the chop.
        for (BlockPos vine : vines) {
            helper.assertTrue(level.getBlockState(vine).is(Blocks.VINE),
                    "precondition: vine at " + vine + " must survive placement");
        }

        EntityRobot robot = new EntityRobot(level, BoardRobotLumberjackNBT.INSTANCE);
        Vec3 pos = Vec3.atCenterOf(helper.absolutePos(robotRel));
        robot.setPos(pos.x, pos.y, pos.z);
        robot.getBattery().addPower(SEEDED_CHARGE, false);
        // Pre-equipped: the fetch-and-equip leg needs a supply station, which this arena has none of.
        robot.setItemInUse(new ItemStack(Items.DIAMOND_AXE));
        level.addFreshEntity(robot);

        EntityArenaUtil.tickUntil(helper, 900,
                () -> level.getBlockState(logBottom).isAir()
                        && level.getBlockState(logMid).isAir()
                        && level.getBlockState(logTop).isAir(),
                () -> {
                    StringBuilder remaining = null;
                    for (BlockPos vine : vines) {
                        if (!level.getBlockState(vine).isAir()) {
                            if (remaining == null) remaining = new StringBuilder();
                            remaining.append(vine).append(" ");
                        }
                    }
                    helper.assertTrue(remaining == null,
                            "the lumberjack chopped the whole trunk but left unsupported vines at: "
                                    + remaining);
                    // Cleanup: dropped logs must not leak into neighbouring arenas, and the robot
                    // would keep scanning (or sleeping) past the end of the test.
                    for (net.minecraft.world.entity.item.ItemEntity drop : level.getEntitiesOfClass(
                            net.minecraft.world.entity.item.ItemEntity.class,
                            new AABB(Vec3.atLowerCornerOf(helper.absolutePos(new BlockPos(0, 0, 0))),
                                    Vec3.atLowerCornerOf(helper.absolutePos(new BlockPos(6, 7, 6))))
                                    .inflate(1))) {
                        drop.discard();
                    }
                    robot.discard();
                    helper.succeed();
                },
                "the lumberjack robot never finished chopping the three-log trunk");
    }

    /**
     * The full real-world lifecycle the bare-trunk E2E deliberately skipped: a tree WITH a canopy.
     * A natural jungle/swamp tree grows vines on the trunk AND long hanging strands anchored to
     * canopy leaves, and the lumberjack (a log-only board) never breaks leaves — so at chop time the
     * leaf-anchored strands legitimately remain. This test pins that they still do not outlive their
     * anchors: the decaying canopy (driven through its vanilla random-tick handler) must take every
     * strand down with it. If a vine survives both a chopped trunk and a decayed canopy, it is truly
     * unsupported and that is the reported bug.
     *
     * <p>Tree: three-tall trunk, a two-layer leaf canopy (natural, i.e. non-persistent), one skirt
     * leaf per side at mid-height, three trunk-side vines, a three-vine strand anchored to the west
     * skirt leaf, and a two-vine strand anchored to the east skirt leaf.
     */
    public static void testLumberjackCanopyTreeVinesFallWithLeafDecay(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos robotRel = new BlockPos(4, 3, 4);
        EntityArenaUtil.forceLoadEntityArena(helper, robotRel);

        BlockState leaf = Blocks.OAK_LEAVES.defaultBlockState()
                .setValue(LeavesBlock.DISTANCE, 1)
                .setValue(LeavesBlock.PERSISTENT, false);
        BlockPos logBottom = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos logMid = logBottom.above();
        BlockPos logTop = logMid.above();

        level.setBlock(logBottom, Blocks.OAK_LOG.defaultBlockState(), 3);
        level.setBlock(logMid, Blocks.OAK_LOG.defaultBlockState(), 3);
        level.setBlock(logTop, Blocks.OAK_LOG.defaultBlockState(), 3);
        // Canopy ring around the top log, then a 3x3 cap, then two skirt leaves at mid-height.
        for (BlockPos p : List.of(new BlockPos(1, 4, 1), new BlockPos(1, 4, 2), new BlockPos(1, 4, 3),
                new BlockPos(2, 4, 1), new BlockPos(2, 4, 3),
                new BlockPos(3, 4, 1), new BlockPos(3, 4, 2), new BlockPos(3, 4, 3),
                new BlockPos(1, 5, 1), new BlockPos(1, 5, 2), new BlockPos(1, 5, 3),
                new BlockPos(2, 5, 1), new BlockPos(2, 5, 2), new BlockPos(2, 5, 3),
                new BlockPos(3, 5, 1), new BlockPos(3, 5, 2), new BlockPos(3, 5, 3),
                new BlockPos(1, 3, 2), new BlockPos(3, 3, 2))) {
            level.setBlock(helper.absolutePos(p), leaf, 3);
        }

        // Trunk-side vines (anchor: the log beside them).
        List<BlockPos> trunkVines = List.of(
                helper.absolutePos(new BlockPos(2, 2, 3)),
                helper.absolutePos(new BlockPos(2, 3, 3)),
                helper.absolutePos(new BlockPos(2, 4, 3)));
        // West strand: three vines hanging off the west skirt leaf, the lower two supported by
        // nothing but the vine above them.
        List<BlockPos> westStrand = List.of(
                helper.absolutePos(new BlockPos(1, 3, 1)),
                helper.absolutePos(new BlockPos(1, 2, 1)),
                helper.absolutePos(new BlockPos(1, 1, 1)));
        // East strand: two vines off the east skirt leaf.
        List<BlockPos> eastStrand = List.of(
                helper.absolutePos(new BlockPos(3, 3, 3)),
                helper.absolutePos(new BlockPos(3, 2, 3)));

        for (BlockPos v : trunkVines) {
            level.setBlock(v, Blocks.VINE.defaultBlockState().setValue(VineBlock.NORTH, true), 3);
        }
        level.setBlock(westStrand.get(0), Blocks.VINE.defaultBlockState().setValue(VineBlock.SOUTH, true), 3);
        for (BlockPos v : westStrand.subList(1, 3)) {
            level.setBlock(v, Blocks.VINE.defaultBlockState().setValue(VineBlock.SOUTH, true), 3);
        }
        for (BlockPos v : eastStrand) {
            level.setBlock(v, Blocks.VINE.defaultBlockState().setValue(VineBlock.NORTH, true), 3);
        }

        List<BlockPos> allVines = List.of(
                trunkVines.get(0), trunkVines.get(1), trunkVines.get(2),
                westStrand.get(0), westStrand.get(1), westStrand.get(2),
                eastStrand.get(0), eastStrand.get(1));
        for (BlockPos v : allVines) {
            helper.assertTrue(level.getBlockState(v).is(Blocks.VINE),
                    "precondition: vine at " + v + " must survive placement");
        }

        EntityRobot robot = new EntityRobot(level, BoardRobotLumberjackNBT.INSTANCE);
        Vec3 pos = Vec3.atCenterOf(helper.absolutePos(robotRel));
        robot.setPos(pos.x, pos.y, pos.z);
        robot.getBattery().addPower(SEEDED_CHARGE, false);
        robot.setItemInUse(new ItemStack(Items.DIAMOND_AXE));
        level.addFreshEntity(robot);

        // The whole lifecycle in one poll: the chop is done when the trunk is gone, and the canopy's
        // aftermath is done when no leaf remains. Each poll drives one manual leaf-decay wave through
        // the public BlockState.randomTick delegate (the natural random-tick path, just not left to
        // chance) — decaying leaves must pop their anchored strands the same way decaying logs do.
        BlockPos regionMin = helper.absolutePos(new BlockPos(0, 0, 0));
        BlockPos regionMax = helper.absolutePos(new BlockPos(5, 6, 4));
        EntityArenaUtil.tickUntil(helper, 900,
                () -> {
                    boolean chopped = level.getBlockState(logBottom).isAir()
                            && level.getBlockState(logMid).isAir()
                            && level.getBlockState(logTop).isAir();
                    if (!chopped) {
                        return false;
                    }
                    boolean anyLeaf = false;
                    for (BlockPos p : BlockPos.betweenClosed(regionMin, regionMax)) {
                        BlockState s = level.getBlockState(p);
                        if (s.getBlock() instanceof LeavesBlock) {
                            anyLeaf = true;
                            s.randomTick(level, p.immutable(), level.getRandom());
                        }
                    }
                    return !anyLeaf;
                },
                () -> {
                    for (BlockPos v : allVines) {
                        helper.assertTrue(level.getBlockState(v).isAir(),
                                "vine at " + v + " must fall — its trunk or its anchor leaves are gone");
                    }
                    for (net.minecraft.world.entity.item.ItemEntity drop : level.getEntitiesOfClass(
                            net.minecraft.world.entity.item.ItemEntity.class,
                            new AABB(Vec3.atLowerCornerOf(regionMin), Vec3.atLowerCornerOf(regionMax))
                                    .inflate(1))) {
                        drop.discard();
                    }
                    robot.discard();
                    helper.succeed();
                },
                "the lumberjack robot never finished the chop-and-decay lifecycle");
    }

    /**
     * Pins the client-ghost repair at the diff level: {@code snapshotSupportsForResync} taken
     * before a break must, once the break has cascaded, report exactly the support-driven
     * positions whose state changed — the vine clinging to the log and the vine hanging beneath
     * it — so {@code resyncChangedBlocks} pushes each of them to clients explicitly. (That the
     * break path itself invokes this snapshot-and-resync pair is a one-line call inside
     * {@link BlockUtil#breakBlockAndGetDropsWithXp}; what the client does with the pushed states
     * is vanilla's chunk sync and is not observable from a server-side game test.)
     */
    public static void testResyncDiffCatchesCascadedVines(GameTestHelper helper) {
        try {
            ServerLevel level = (ServerLevel) helper.getLevel();
            BlockPos logPos = helper.absolutePos(new BlockPos(2, 2, 2));
            BlockPos vinePos = logPos.south();
            BlockPos vineBelow = vinePos.below();
            level.setBlock(logPos, Blocks.OAK_LOG.defaultBlockState(), 3);
            level.setBlock(vinePos, Blocks.VINE.defaultBlockState().setValue(VineBlock.NORTH, true), 3);
            // hangs from vinePos by the above-vine rule only
            level.setBlock(vineBelow, Blocks.VINE.defaultBlockState().setValue(VineBlock.NORTH, true), 3);

            var before = BlockUtil.snapshotSupportsForResync(level, logPos);

            Optional<List<ItemStack>> result = BlockUtil.breakBlockAndGetDrops(
                level, logPos, new ItemStack(Items.DIAMOND_AXE), (GameProfile) null);
            assertTrue(result.isPresent(), "log break must succeed");
            assertTrue(level.getBlockState(vinePos).isAir() && level.getBlockState(vineBelow).isAir(),
                    "precondition for the diff check: the cascade popped both vines");

            List<BlockPos> resynced = BlockUtil.resyncChangedBlocks(level, before);
            assertTrue(resynced.contains(vinePos), "the vine beside the broken log must be resynced to clients");
            assertTrue(resynced.contains(vineBelow), "the cascaded vine below it must be resynced to clients");
            assertTrue(resynced.contains(logPos),
                    "the broken block itself must be re-pushed too — when vanilla's chunk-status gate "
                            + "skipped its removal sync (machines working far from players) this is the "
                            + "state that would otherwise ghost on the client");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }
}
