/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.tile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.builders.BCBuildersBlocks;
import buildcraft.core.marker.volume.LevelSavedDataVolumeBoxes;
import buildcraft.core.marker.volume.Lock;
import buildcraft.core.marker.volume.VolumeBox;
import buildcraft.lib.test.EntityArenaUtil;
import buildcraft.lib.test.ValueIoTestUtil;

/**
 * The server tick side of {@code TileFillerPlannerMigrationTest}: a real Filler block in a real level, loaded with the
 * tag a planner-driven Filler saved, beside a Volume Box (in the level's own saved data) loaded from the tag of a
 * planner box, takes the box, the pattern and the invert flag over by ticking alone.
 */
public class FillerPlannerMigrationTester {

    private static final BlockPos BOX_MIN = new BlockPos(2, 2, 2);
    private static final BlockPos BOX_MAX = new BlockPos(3, 3, 3);
    /** East of the box, facing east: the cell behind the Filler is the box's. */
    private static final BlockPos FILLER = new BlockPos(4, 2, 2);

    /** Ticks, once the cell ticks, for the Filler to take the box over: its first tick does it. */
    private static final int WORK_TICKS = 40;

    public static void aFillerBesideAPlannerBoxTakesItOverByTicking(GameTestHelper helper) {
        EntityArenaUtil.forceLoadCell(helper);
        ServerLevel level = helper.getLevel();
        LevelSavedDataVolumeBoxes savedBoxes = LevelSavedDataVolumeBoxes.get(level);
        BlockPos fillerPos = helper.absolutePos(FILLER);
        TileFiller[] filler = { null };
        VolumeBox[] box = { null };

        EntityArenaUtil.tickUntil(helper, EntityArenaUtil.CELL_READY_TICKS + WORK_TICKS,
            EntityArenaUtil.whenCellTicking(helper, EntityArenaUtil.CELL_READY_TICKS, WORK_TICKS, () -> {
                if (filler[0] == null) {
                    BlockState state = BCBuildersBlocks.FILLER.get().defaultBlockState()
                        .setValue(HorizontalDirectionalBlock.FACING, Direction.EAST);
                    helper.setBlock(FILLER, state);
                    filler[0] = (TileFiller) level.getBlockEntity(fillerPos);
                    helper.assertTrue(filler[0] != null, "the Filler block should have a tile");
                    box[0] = new VolumeBox(level, PlannerSaveFixtures.volumeBoxTag(
                        level, helper.absolutePos(BOX_MIN), helper.absolutePos(BOX_MAX), fillerPos, true, true));
                    savedBoxes.volumeBoxes.add(box[0]);
                    filler[0].readData(ValueIoTestUtil.inputOf(PlannerSaveFixtures.fillerTag(fillerPos, state)));
                    helper.assertTrue(!filler[0].box.isInitialized(), "precondition: the old save has no box in the Filler");
                    return false;
                }
                return filler[0].box.isInitialized();
            }, () -> "the Filler beside the planner box never took the box over by ticking"),
            () -> {
                TileFiller tile = filler[0];
                helper.assertTrue(tile.box.min().equals(helper.absolutePos(BOX_MIN))
                        && tile.box.max().equals(helper.absolutePos(BOX_MAX)),
                    "the Filler should hold the Volume Box's extent, got " + tile.box);
                helper.assertTrue(tile.patternStatement.get() == PlannerSaveFixtures.plannerPattern(),
                    "the planner's pattern should have moved to the Filler, got " + tile.patternStatement.get());
                helper.assertTrue(tile.inverted, "the planner's invert flag should have moved to the Filler");
                helper.assertTrue(tile.getTemplateBuildingInfo() != null, "a Filler with a box and a pattern can build");
                long locks = box[0].locks.stream()
                    .filter(lock -> lock.cause instanceof Lock.Cause.CauseBlock cause && fillerPos.equals(cause.pos))
                    .count();
                helper.assertTrue(locks == 1, "the box should hold the Filler's one lock, found " + locks);
                helper.assertTrue(box[0].takeRetiredPlanner() == null, "the Filler should have consumed the planner's state");
                helper.succeed();
            }, "The Filler beside the planner box never took the box over", () -> {
                if (box[0] != null) {
                    savedBoxes.volumeBoxes.remove(box[0]);
                    savedBoxes.markDirtyAndBroadcast();
                }
                helper.setBlock(FILLER, Blocks.AIR);
            });
    }
}
