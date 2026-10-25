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
    /** How long the second look waits: more than {@code FillPreviewCache.RESCAN_TICKS} (10), so the cache has had to
     *  scan again. Each poll's own budget adds {@code EntityArenaUtil.CELL_READY_TICKS}. */
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
        /** Reads ten template cells a frame (the box has 27), and keeps all 26 that are left. */
        final FillPreviewCache slow = new FillPreviewCache(30, 10);
    }

    private static void firstLook(GameTestHelper helper, State state) {
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

        List<BlockPos> fillable = FillPreview.collect(state.info, pos -> FillPreview.isFillable(level, pos));
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

        // A big box is read a slice of a frame at a time: nothing to show until the pass has read all 27 cells.
        helper.assertTrue(state.slow.cellsToDraw(level, state.info, state.camera).isEmpty(),
            "10 of 27 cells read: the first pass has nothing to show yet");
        helper.assertTrue(state.slow.cellsToDraw(level, state.info, state.camera).isEmpty(),
            "20 of 27 cells read: the first pass has nothing to show yet");
        helper.assertTrue(state.slow.cellsToDraw(level, state.info, state.camera).size() == 26,
            "27 of 27 cells read: the pass is done and shows the 26 empty cells");

        // A changed pattern builds new building info, and what was kept for the old one must never be drawn again.
        switchesPatternWithoutDrawingTheOldCells(helper, level, filler, state);

        // A block that lands in the box is not noticed until the cache scans again, which is the point of the cache.
        helper.setBlock(SECOND_STONE, Blocks.STONE);
        helper.assertTrue(state.cache.cellsToDraw(level, state.info, state.camera).size() == 26,
            "the box was walked again within the cache's trust window");
    }

    /**
     * The Filler's pattern goes from a solid fill to a frame, and both caches are 8-cells-a-frame so a pass takes
     * several frames. The cell in the middle of a face is in the fill and not in the frame: seeing it after the switch
     * means the old cells were drawn. Two cases, one per identity check in the cache: the old pass had finished (the
     * kept cells must go the moment the new info shows up), and the old pass was still running (it must be dropped,
     * not carried on into the new info's result).
     */
    private static void switchesPatternWithoutDrawingTheOldCells(GameTestHelper helper, ServerLevel level,
                                                                   TileFiller filler, State state) {
        BlockPos oldOnly = helper.absolutePos(new BlockPos(3, 2, 2));
        filler.patternStatement.set(BCBuildersStatements.PATTERN_FRAME);
        filler.onStatementChange();
        Template.BuildingInfo frame = filler.getTemplateBuildingInfo();
        helper.assertTrue(frame != null && frame != state.info, "a new pattern should give new building info");
        Set<BlockPos> frameCells = new HashSet<>(FillPreview.collect(frame, pos -> FillPreview.isFillable(level, pos)));
        helper.assertTrue(frameCells.size() == 20 && !frameCells.contains(oldOnly),
            "the frame of the 3x3x3 box is its 20 edge cells, none of them a face's middle, got " + frameCells.size());

        // The old pass had finished.
        FillPreviewCache finished = new FillPreviewCache(30, 8);
        for (int frameNumber = 1; frameNumber <= 4; frameNumber++) {
            finished.cellsToDraw(level, state.info, state.camera);
        }
        helper.assertTrue(finished.cellsToDraw(level, state.info, state.camera).contains(oldOnly),
            "precondition: the solid fill's cells are being drawn");
        helper.assertTrue(finished.cellsToDraw(level, frame, state.camera).isEmpty(),
            "8 of the frame's 20 cells read: none of the old fill may be drawn while the new pass runs");
        helper.assertTrue(finished.cellsToDraw(level, frame, state.camera).isEmpty(),
            "16 of the frame's 20 cells read: none of the old fill may be drawn while the new pass runs");
        helper.assertTrue(new HashSet<>(finished.cellsToDraw(level, frame, state.camera)).equals(frameCells),
            "the frame's pass is done: exactly its 20 cells");

        // The old pass was only 8 of 27 cells in.
        FillPreviewCache midPass = new FillPreviewCache(30, 8);
        helper.assertTrue(midPass.cellsToDraw(level, state.info, state.camera).isEmpty(),
            "precondition: 8 of the fill's 27 cells read, nothing to show yet");
        helper.assertTrue(midPass.cellsToDraw(level, frame, state.camera).isEmpty(),
            "8 of the frame's 20 cells read: the old pass must not be carried on");
        helper.assertTrue(midPass.cellsToDraw(level, frame, state.camera).isEmpty(),
            "16 of the frame's 20 cells read: the old pass must not be carried on");
        helper.assertTrue(new HashSet<>(midPass.cellsToDraw(level, frame, state.camera)).equals(frameCells),
            "the frame's pass is done: exactly its 20 cells, not what the abandoned fill pass had found");
    }

    private static void secondLook(GameTestHelper helper, State state) {
        ServerLevel level = helper.getLevel();
        BlockPos second = helper.absolutePos(SECOND_STONE);
        List<BlockPos> drawn = state.cache.cellsToDraw(level, state.info, state.camera);
        helper.assertTrue(drawn.size() == 25 && !drawn.contains(second) && !drawn.contains(helper.absolutePos(FIRST_STONE)),
            "after the trust window the preview should leave out both built cells, got " + drawn.size() + " cells");

        // The slow cache starts its next pass but keeps drawing the old result until the pass has read every cell.
        for (int frame = 1; frame <= 2; frame++) {
            helper.assertTrue(state.slow.cellsToDraw(level, state.info, state.camera).size() == 26,
                "frame " + frame + " of the second pass: the previous result should still be drawn");
        }
        helper.assertTrue(state.slow.cellsToDraw(level, state.info, state.camera).size() == 25,
            "the second pass is done: the preview now leaves out both built cells");
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
