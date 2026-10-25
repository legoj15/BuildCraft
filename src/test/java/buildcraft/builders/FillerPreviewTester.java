/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import buildcraft.builders.filler.FillPreview;
import buildcraft.builders.filler.FillPreviewCache;
import buildcraft.builders.snapshot.Template;
import buildcraft.builders.tile.TileFiller;
import buildcraft.lib.test.EntityArenaUtil;

/**
 * What a Filler's preview draws, against a real level: the cells its pattern fills that nothing has been built in
 * yet, ordered farthest first for the translucent blend, the nearest few kept when there are too many to draw, and
 * the box walked once and then trusted for a while instead of on every frame.
 */
public class FillerPreviewTester {

    /** A 3x3x3 box in the arena cell, with the Filler beside it. */
    private static final BlockPos BOX_MIN = new BlockPos(2, 1, 2);
    private static final BlockPos BOX_MAX = new BlockPos(4, 3, 4);
    private static final BlockPos FILLER = new BlockPos(5, 1, 5);
    private static final BlockPos FIRST_STONE = new BlockPos(3, 2, 3);
    private static final BlockPos SECOND_STONE = new BlockPos(2, 1, 2);

    /** The wait, once the cell ticks, before the box is set up (immediate: the gate's condition is already true). */
    private static final int READY_WORK_TICKS = 10;
    /** How long the second look waits: more than {@code FillPreviewCache.RESCAN_TICKS}, so the cache has had to scan
     *  again (checked in the test). Each poll's own budget adds {@code EntityArenaUtil.CELL_READY_TICKS}. */
    private static final int RESCAN_WAIT_TICKS = 15;
    /** Cells mirrored about the camera are the same distance away; the cache sorts on a float, so the order among
     *  them is arbitrary and the doubles here differ in their last bits. */
    private static final double TIE = 1e-6;

    public static void previewDrawsOnlyWhatIsStillEmptyFarthestFirst(GameTestHelper helper) {
        // The scan reads cells only from chunks that are in memory, and the 3x3x3 box may straddle a chunk border.
        EntityArenaUtil.forceLoadCell(helper);
        State state = new State();
        EntityArenaUtil.tickUntilThen(helper, EntityArenaUtil.CELL_READY_TICKS + READY_WORK_TICKS + RESCAN_WAIT_TICKS,
            EntityArenaUtil.whenCellTicking(helper, EntityArenaUtil.CELL_READY_TICKS, READY_WORK_TICKS, () -> true),
            () -> firstLook(helper, state), RESCAN_WAIT_TICKS, () -> secondLook(helper, state),
            "The arena cell never started ticking", () -> cleanUp(helper));
    }

    /** What the two phases share. */
    private static final class State {
        Template.BuildingInfo info;
        Vec3 camera;
        final FillPreviewCache cache = new FillPreviewCache();
    }

    private static void firstLook(GameTestHelper helper, State state) {
        helper.assertTrue(RESCAN_WAIT_TICKS > FillPreviewCache.RESCAN_TICKS,
            "the second look has to be later than the cache's trust window");
        ServerLevel level = helper.getLevel();

        helper.setBlock(FILLER, BCBuildersBlocks.FILLER.get());
        TileFiller filler = (TileFiller) level.getBlockEntity(helper.absolutePos(FILLER));
        helper.assertTrue(filler != null, "the Filler block should have a tile");
        filler.box.reset();
        filler.box.setMin(helper.absolutePos(BOX_MIN));
        filler.box.setMax(helper.absolutePos(BOX_MAX));
        filler.patternStatement.set(BCBuildersStatements.PATTERN_FILL);
        filler.onStatementChange();
        state.info = filler.getTemplateBuildingInfo();
        helper.assertTrue(state.info != null, "a solid fill over the box should give the Filler building info");
        // Off the box's near-bottom corner.
        state.camera = Vec3.atLowerCornerOf(helper.absolutePos(BOX_MIN)).add(-0.3, -0.9, -0.3);

        helper.setBlock(FIRST_STONE, Blocks.STONE);
        BlockPos stone = helper.absolutePos(FIRST_STONE);

        List<BlockPos> fillable = FillPreview.collectFillable(level, state.info);
        helper.assertTrue(fillable.size() == 26 && !fillable.contains(stone),
            "27 cells minus the one already built should be left to fill, got " + fillable.size());

        List<BlockPos> drawn = state.cache.cellsToDraw(level, state.info, state.camera);
        helper.assertTrue(drawn.size() == 26 && !drawn.contains(stone), "the preview draws the 26 empty cells");
        for (int i = 1; i < drawn.size(); i++) {
            helper.assertTrue(distance(drawn.get(i - 1), state.camera) + TIE >= distance(drawn.get(i), state.camera),
                "cell " + i + " is farther than the one drawn before it: farthest must come first for the blend");
        }

        // Too many to draw: the nearest few stay, still farthest first.
        List<BlockPos> nearest = new FillPreviewCache(10).cellsToDraw(level, state.info, state.camera);
        helper.assertTrue(nearest.size() == 10, "a cap of 10 draws 10 cubes, got " + nearest.size());
        double farthestKept = distance(nearest.get(0), state.camera);
        Set<BlockPos> kept = new HashSet<>(nearest);
        for (BlockPos cell : drawn) {
            if (!kept.contains(cell)) {
                helper.assertTrue(distance(cell, state.camera) + TIE >= farthestKept,
                    "the cap dropped " + cell + " but kept a cell farther from the camera");
            }
        }
        for (int i = 1; i < nearest.size(); i++) {
            helper.assertTrue(distance(nearest.get(i - 1), state.camera) + TIE >= distance(nearest.get(i), state.camera),
                "the capped list is not farthest first at " + i);
        }

        // A block that lands in the box is not noticed until the cache scans again, which is the point of the cache.
        helper.setBlock(SECOND_STONE, Blocks.STONE);
        helper.assertTrue(state.cache.cellsToDraw(level, state.info, state.camera).size() == 26,
            "the box was walked again within the cache's trust window");
    }

    private static void secondLook(GameTestHelper helper, State state) {
        ServerLevel level = helper.getLevel();
        BlockPos second = helper.absolutePos(SECOND_STONE);
        List<BlockPos> drawn = state.cache.cellsToDraw(level, state.info, state.camera);
        helper.assertTrue(drawn.size() == 25 && !drawn.contains(second) && !drawn.contains(helper.absolutePos(FIRST_STONE)),
            "after the trust window the preview should leave out both built cells, got " + drawn.size() + " cells");
        helper.succeed();
    }

    private static void cleanUp(GameTestHelper helper) {
        helper.setBlock(FILLER, Blocks.AIR);
        helper.setBlock(FIRST_STONE, Blocks.AIR);
        helper.setBlock(SECOND_STONE, Blocks.AIR);
    }

    private static double distance(BlockPos cell, Vec3 camera) {
        return Vec3.atCenterOf(cell).distanceToSqr(camera);
    }
}
