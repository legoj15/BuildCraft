/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.tile;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
//? if >=1.21.10 {
import net.minecraft.world.level.storage.TagValueInput;
//?}

import buildcraft.lib.misc.data.Box;

import buildcraft.core.marker.volume.LevelSavedDataVolumeBoxes;
import buildcraft.core.marker.volume.VolumeBox;

import buildcraft.builders.BCBuildersBlocks;
import buildcraft.builders.BCBuildersConfig;

/**
 * A Quarry placed against a box lifted off markers and resized with a wrench (a confirmed one-shot box) takes that box
 * as its frame, down to the smallest frame that leaves a column to mine (3x3). A box narrower than that on either
 * horizontal axis is left alone and the Quarry lays out its default area instead.
 */
public class TileQuarryAreaAdoptionTester {

    public static void adoptsResizedMarkerBox(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LevelSavedDataVolumeBoxes boxes = LevelSavedDataVolumeBoxes.get(level);
        BlockPos quarryRel = new BlockPos(2, 2, 6);
        VolumeBox placed = null;
        try {
            // 5x5, 4x4 and 3x3 boxes north of the quarry (the block behind a south-facing quarry) are adopted. A drag
            // follows the player's look, so the dragged corner often ends below ground: the frame still stands on the
            // quarry's level, over the box's footprint. Each frame must also survive the save/sync round trip, whose
            // validation drops a frame that the quarry does not sit beside.
            // { size, blocks below the quarry, blocks above it }
            int[][] cases = { { 5, 0, 0 }, { 4, 0, 0 }, { 3, 0, 0 }, { 5, 2, 0 }, { 4, 2, 6 } };
            for (int[] c : cases) {
                int size = c[0];
                int sunk = c[1];
                int raised = c[2];
                BlockPos min = helper.absolutePos(new BlockPos(1, 2 - sunk, 6 - size));
                BlockPos max = helper.absolutePos(new BlockPos(size, 2 + raised, 5));
                String what = "a " + size + "x" + size + " resized box " + min + ".." + max;
                placed = VolumeBox.fromMarkers(level, min, max, List.of());
                boxes.volumeBoxes.add(placed);
                TileQuarry quarry = placeQuarry(helper, quarryRel);
                checkFrame(quarry, min, max, helper.absolutePos(quarryRel).getY(), what + " should be the frame");
                if (boxes.volumeBoxes.contains(placed)) {
                    throw new IllegalStateException("the adopted " + what + " should be consumed");
                }
                placed = null;
                CompoundTag tag = quarry.saveCustomOnly(level.registryAccess());
                //? if >=1.21.10 {
                quarry.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
                //?} else {
                /*quarry.loadCustomOnly(tag, level.registryAccess());*/
                //?}
                checkFrame(quarry, min, max, helper.absolutePos(quarryRel).getY(),
                    what + " should survive the save/sync round trip");
                helper.setBlock(quarryRel, Blocks.AIR);
            }

            // 2x5 and 1x1 boxes leave no column to mine: the default area, and the box stays.
            checkFallsBackToDefault(helper, boxes, quarryRel, new BlockPos(1, 2, 1), new BlockPos(2, 2, 5));
            checkFallsBackToDefault(helper, boxes, quarryRel, new BlockPos(2, 2, 5), new BlockPos(2, 2, 5));
            // A 5x3 box around the quarry itself is big enough but would put the quarry inside its own frame.
            checkFallsBackToDefault(helper, boxes, quarryRel, new BlockPos(1, 2, 4), new BlockPos(5, 2, 6));
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        } finally {
            if (placed != null) {
                boxes.volumeBoxes.remove(placed);
            }
            helper.setBlock(quarryRel, Blocks.AIR);
        }
    }

    private static void checkFrame(TileQuarry quarry, BlockPos min, BlockPos max, int baseY, String what) {
        Box frame = quarry.frameBox;
        int top = Math.max(max.getY(), baseY + BCBuildersConfig.quarryFrameMinHeight.get());
        if (!frame.isInitialized() || frame.max().getY() != top
            || frame.min().getX() != min.getX() || frame.min().getZ() != min.getZ()
            || frame.max().getX() != max.getX() || frame.max().getZ() != max.getZ()
            || frame.min().getY() != baseY) {
            throw new IllegalStateException(what + ", got " + frame);
        }
    }

    private static void checkFallsBackToDefault(GameTestHelper helper, LevelSavedDataVolumeBoxes boxes,
            BlockPos quarryRel, BlockPos minRel, BlockPos maxRel) {
        BlockPos min = helper.absolutePos(minRel);
        BlockPos max = helper.absolutePos(maxRel);
        VolumeBox small = VolumeBox.fromMarkers(helper.getLevel(), min, max, List.of());
        boxes.volumeBoxes.add(small);
        try {
            TileQuarry quarry = placeQuarry(helper, quarryRel);
            BlockPos q = helper.absolutePos(quarryRel);
            // A south-facing quarry's default frame runs 11 blocks north of it, 11 wide.
            BlockPos defMin = q.offset(-5, 0, -11);
            BlockPos defMax = q.offset(5, 0, -1);
            if (!quarry.frameBox.isInitialized()
                || !quarry.frameBox.min().equals(defMin)
                || quarry.frameBox.max().getX() != defMax.getX() || quarry.frameBox.max().getZ() != defMax.getZ()) {
                throw new IllegalStateException("a box " + min + ".." + max + " cannot be a quarry frame: expected the "
                    + "default frame from " + defMin + ", got " + quarry.frameBox);
            }
            if (!boxes.volumeBoxes.contains(small)) {
                throw new IllegalStateException("a box the quarry cannot adopt must not be consumed");
            }
        } finally {
            boxes.volumeBoxes.remove(small);
            helper.setBlock(quarryRel, Blocks.AIR);
        }
    }

    private static TileQuarry placeQuarry(GameTestHelper helper, BlockPos rel) {
        helper.setBlock(rel, BCBuildersBlocks.QUARRY.get().defaultBlockState()
            .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        //? if >=1.21.10 {
        TileQuarry quarry = helper.getBlockEntity(rel, TileQuarry.class);
        //?} else {
        /*TileQuarry quarry = helper.getBlockEntity(rel);*/
        //?}
        quarry.onPlacedBy(null, ItemStack.EMPTY);
        return quarry;
    }
}
