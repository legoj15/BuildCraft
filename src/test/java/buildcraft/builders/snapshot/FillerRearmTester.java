/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.snapshot;

import static buildcraft.lib.test.GameTestUtil.check;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import buildcraft.api.mj.MjAPI;
import buildcraft.api.tiles.IControllable.Mode;

import buildcraft.builders.BCBuildersBlocks;
import buildcraft.builders.BCBuildersStatements;
import buildcraft.builders.tile.TileFiller;
import buildcraft.lib.test.EntityArenaUtil;

/**
 * A Filler woken after sitting idle (switched back to On, or handed more blocks while parked finished) must not trust
 * what its builder last saw. The builder re-reads only a handful of cells a tick and keeps the rest as classified, so a
 * finished Filler (or one that was Off) holds a cache of "correct" cells that the world may have outgrown. Woken with
 * that cache, its first tick reported the build done after looking at ten cells, and On's finished state parks the
 * Filler for good: a block broken in the meantime was rebuilt only if it happened to be one of those ten.
 * <p>
 * Each test builds a small box in full, then takes a block out of it from where the first sweep of the next tick will
 * not reach (the builder's sweep order and position come from {@link SnapshotBuilder#checkSweepCell}, so the block is
 * never one of the cells the stale cache could still catch), and wakes the Filler. The Filler is driven by hand, as in
 * {@link buildcraft.builders.tile.FillerProgressTester}: never added to the level, so its own ticker does not run.
 */
public class FillerRearmTester {

    private static final BlockPos BOX_MIN = new BlockPos(2, 1, 2);
    private static final BlockPos BOX_MAX = new BlockPos(4, 3, 4);
    private static final BlockPos FILLER = new BlockPos(5, 1, 5);
    private static final int CELLS = 3 * 3 * 3;

    /** Ticks to build the box, then to find the missing block, rebuild it and finish again. */
    private static final int BUDGET = 300;

    /** A Filler that finished a Loop cycle and is then switched to On rebuilds a block broken since its last sweep. */
    public static void loopFillerSwitchedToOnRebuildsABrokenBlock(GameTestHelper helper) {
        brokenBlockIsRebuilt(helper, Mode.LOOP, filler -> filler.setControlMode(Mode.ON));
    }

    /** A finished Filler that is switched Off, loses a block, and is switched On rebuilds it. */
    public static void offFillerSwitchedToOnRebuildsABrokenBlock(GameTestHelper helper) {
        brokenBlockIsRebuilt(helper, Mode.ON, filler -> {
            filler.setControlMode(Mode.OFF);
            filler.setControlMode(Mode.ON);
        });
    }

    /** A Filler parked finished in On, given one more block by a pipe or the GUI, rebuilds a block broken meanwhile. */
    public static void finishedOnFillerGivenResourcesRebuildsABrokenBlock(GameTestHelper helper) {
        brokenBlockIsRebuilt(helper, Mode.ON,
            filler -> filler.invResources.insert(new ItemStack(Items.STONE), false, false));
    }

    private static void brokenBlockIsRebuilt(GameTestHelper helper, Mode buildMode, Consumer<TileFiller> wake) {
        EntityArenaUtil.forceLoadCell(helper);
        ServerLevel level = helper.getLevel();
        TileFiller filler = newFiller(helper, buildMode);

        BlockPos[] broken = { null };
        EntityArenaUtil.tickUntil(helper, BUDGET, () -> {
            feed(filler);
            filler.tick();
            if (broken[0] == null) {
                if (!filler.getFinished()) {
                    return false;
                }
                // Built: take a block out where the next tick's ten checks cannot see it, then wake the Filler.
                broken[0] = outsideNextSweep(filler);
                check(level.getBlockState(broken[0]).is(Blocks.STONE), "the cell about to be broken was built, it is "
                        + level.getBlockState(broken[0]));
                level.setBlock(broken[0], Blocks.AIR.defaultBlockState(), 3);
                wake.accept(filler);
                return false;
            }
            boolean rebuilt = level.getBlockState(broken[0]).is(Blocks.STONE);
            check(rebuilt || !filler.getFinished(), "the woken Filler finished with the block it lost still "
                    + "missing at " + broken[0] + " (" + level.getBlockState(broken[0]) + ")");
            return rebuilt && filler.getFinished();
        }, () -> {
            check(allStone(helper), "the box is whole again once the Filler finishes");
            check(filler.builder.placeTasks.isEmpty() && filler.builder.breakTasks.isEmpty(),
                    "a finished Filler holds no queued tasks");
            helper.succeed();
        }, "the woken Filler never rebuilt the block it lost", () -> cleanUp(helper));
    }

    /**
     * Switching a Filler Off and On while it is mid-build re-reads the box, but the blocks it was already paying for
     * stay queued, with the power they drew: the re-read forgets what it classified, never what it was doing. The
     * build still ends with every cell filled and no stone lost or made.
     */
    public static void offOnMidBuildKeepsQueuedTasks(GameTestHelper helper) {
        EntityArenaUtil.forceLoadCell(helper);
        TileFiller filler = newFiller(helper, Mode.ON);

        List<SnapshotBuilder<ITileForTemplateBuilder>.PlaceTask> queued = new ArrayList<>();
        long[] powers = new long[CELLS];
        EntityArenaUtil.tickUntil(helper, BUDGET, () -> {
            if (queued.isEmpty()) {
                // A thin feed leaves tasks queued, each holding what it drew so far.
                filler.getBattery().addPower(MjAPI.MJ / 2, false);
                filler.tick();
                if (filler.builder.placeTasks.stream().noneMatch(task -> task.power > 0)) {
                    return false;
                }
                int i = 0;
                for (SnapshotBuilder<ITileForTemplateBuilder>.PlaceTask task : filler.builder.placeTasks) {
                    queued.add(task);
                    powers[i++] = task.power;
                }
                filler.setControlMode(Mode.OFF);
                filler.setControlMode(Mode.ON);
                check(filler.builder.placeTasks.size() == queued.size(), "switching Off and On kept " + filler.builder
                        .placeTasks.size() + " of the " + queued.size() + " queued tasks");
                int j = 0;
                for (SnapshotBuilder<ITileForTemplateBuilder>.PlaceTask task : queued) {
                    check(filler.builder.placeTasks.contains(task), "a queued task was dropped by switching Off and On");
                    check(task.power == powers[j++], "a queued task lost the power it had drawn");
                }
                return false;
            }
            feed(filler);
            filler.tick();
            return filler.getFinished();
        }, () -> {
            check(allStone(helper), "every cell of the box is filled");
            int left = filler.invResources.getStackInSlot(0).getCount();
            check(left == 64 - CELLS, "the build used exactly " + CELLS + " of the 64 stone, " + left + " are left");
            helper.succeed();
        }, "the Filler switched Off and On mid-build never finished", () -> cleanUp(helper));
    }

    /** A real Filler over a 3x3x3 box with a Fill pattern and 64 stone, set to {@code mode}. */
    private static TileFiller newFiller(GameTestHelper helper, Mode mode) {
        TileFiller filler = new TileFiller(helper.absolutePos(FILLER), BCBuildersBlocks.FILLER.get().defaultBlockState());
        filler.setLevel(helper.getLevel());
        filler.box.reset();
        filler.box.setMin(helper.absolutePos(BOX_MIN));
        filler.box.setMax(helper.absolutePos(BOX_MAX));
        filler.patternStatement.set(BCBuildersStatements.PATTERN_FILL);
        filler.onStatementChange();
        filler.invResources.insert(new ItemStack(Items.STONE, 64), false, false);
        filler.setControlMode(mode);
        return filler;
    }

    private static void feed(TileFiller filler) {
        filler.getBattery().addPower(filler.getBattery().getCapacity() - filler.getBattery().getStored(), false);
    }

    private static boolean allStone(GameTestHelper helper) {
        for (BlockPos rel : BlockPos.betweenClosed(BOX_MIN, BOX_MAX)) {
            if (!helper.getLevel().getBlockState(helper.absolutePos(rel)).is(Blocks.STONE)) {
                return false;
            }
        }
        return true;
    }

    /**
     * A cell of the box that the builder's next tick will not check: the one half a sweep ahead of its position in the
     * check order (the builder checks ten, so any box of more than twenty cells has one).
     */
    private static BlockPos outsideNextSweep(TileFiller filler) {
        int cells = filler.builder.checkSweepLength();
        check(cells >= 2 * SnapshotBuilder.CHECKS_PER_TICK, "the box is too small (" + cells + " cells) to leave a cell "
                + "out of a sweep of " + SnapshotBuilder.CHECKS_PER_TICK);
        return filler.builder.checkSweepCell(cells / 2);
    }

    private static void cleanUp(GameTestHelper helper) {
        for (BlockPos rel : BlockPos.betweenClosed(BOX_MIN, BOX_MAX)) {
            helper.setBlock(rel, Blocks.AIR);
        }
    }
}
