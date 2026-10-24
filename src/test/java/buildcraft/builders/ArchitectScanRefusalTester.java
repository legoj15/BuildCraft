/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
//? if >=1.21.10 {
import net.minecraft.world.level.storage.TagValueInput;
//?}

import buildcraft.builders.container.LibraryUploadBuffer;
import buildcraft.builders.tile.TileArchitectTable;
import buildcraft.lib.test.EntityArenaUtil;

/**
 * An Architect Table whose box is too large for the blueprint it would store to be read back refuses the scan before it
 * starts and keeps the blank blueprint, while a box within the limit still scans. The refused box is one the cell limit
 * alone would accept: a blueprint pays sixteen bytes a cell, which at the cell limit is the whole read budget, so the
 * gate stops short of it (the numbers are {@code ArchitectScanCapTester}'s business; this pins that the table ticks
 * the gate). Two tables share the arena so a table that never ticked cannot pass as one that refused.
 */
public class ArchitectScanRefusalTester {

    /** Polls spent waiting for the arena cell to tick, and spent on the work once it does. */
    private static final int READY = 40;
    private static final int WORK = 60;

    /** A box the width of the arena cell (6 by 7) and this many blocks tall: the height is what pushes the box past
     *  the limit, so the refused scan would be of void air (no chunk is loaded to read it) if the cap failed. */
    private static final int CELL_X = 6;
    private static final int CELL_Z = 7;
    /** Tall enough that a scan (300 cells a tick) is still running when the poll looks. */
    private static final int WITHIN_LIMIT_HEIGHT = 200;

    public static void testOversizedBoxIsRefusedAndASmallOneStillScans(GameTestHelper helper) {
        EntityArenaUtil.forceLoadCell(helper);
        BlockPos bigPos = new BlockPos(1, 2, 1);
        BlockPos smallPos = new BlockPos(4, 2, 1);
        helper.setBlock(bigPos, BCBuildersBlocks.ARCHITECT.get());
        helper.setBlock(smallPos, BCBuildersBlocks.ARCHITECT.get());
        TileArchitectTable big = tileAt(helper, bigPos);
        TileArchitectTable small = tileAt(helper, smallPos);

        BlockPos origin = helper.absolutePos(new BlockPos(0, 1, 0));
        // Just under the cell limit, so the cell count alone would not refuse it: only a blueprint's cost per cell does.
        int overLimitHeight = LibraryUploadBuffer.Limits.readCeiling().maxCells() / (CELL_X * CELL_Z) - 1;
        giveValidBox(helper, big, origin, overLimitHeight);
        giveValidBox(helper, small, origin, WITHIN_LIMIT_HEIGHT);
        big.setSnapshotIn(new ItemStack(BCBuildersItems.BLUEPRINT_CLEAN.get()));
        small.setSnapshotIn(new ItemStack(BCBuildersItems.BLUEPRINT_CLEAN.get()));

        EntityArenaUtil.tickUntil(helper, READY + WORK,
            EntityArenaUtil.whenCellTicking(helper, READY, WORK, () -> big.isScanRefused() && small.isScanning(),
                () -> "the over-limit table refused=" + big.isScanRefused() + " scanning=" + big.isScanning()
                    + ", the small one scanning=" + small.isScanning()),
            () -> {
                helper.assertTrue(!big.isScanning(), "a refused scan must not have started");
                helper.assertTrue(big.getScanTotal() == 0, "a refused scan has no progress to show");
                helper.assertTrue(!big.getSnapshotIn().isEmpty(), "a refused scan keeps the blank blueprint");
                helper.assertTrue(big.getSnapshotOut().isEmpty(), "a refused scan writes nothing");
                helper.assertTrue(small.getScanTotal() > 0, "a box within the limit scans as before");
                helper.succeed();
            },
            "the over-limit table was not refused, or the small one never scanned",
            // Taking the input out ends a scan under way, so nothing finishes and stores a snapshot.
            () -> {
                big.setSnapshotIn(ItemStack.EMPTY);
                small.setSnapshotIn(ItemStack.EMPTY);
            });
    }

    private static TileArchitectTable tileAt(GameTestHelper helper, BlockPos pos) {
        //? if >=1.21.10 {
        return helper.getBlockEntity(pos, TileArchitectTable.class);
        //?} else {
        /*return helper.getBlockEntity(pos);*/
        //?}
    }

    /** Give {@code tile} a scan box that is {@code height} tall above {@code origin}, through the same save tag a
     *  placed table writes (the box and its valid flag are set when it is placed against markers). */
    private static void giveValidBox(GameTestHelper helper, TileArchitectTable tile, BlockPos origin, int height) {
        ServerLevel level = helper.getLevel();
        CompoundTag tag = tile.saveCustomOnly(level.registryAccess());
        tag.putBoolean("box_initialized", true);
        tag.putInt("box_minX", origin.getX());
        tag.putInt("box_minY", origin.getY());
        tag.putInt("box_minZ", origin.getZ());
        tag.putInt("box_maxX", origin.getX() + CELL_X - 1);
        tag.putInt("box_maxY", origin.getY() + height - 1);
        tag.putInt("box_maxZ", origin.getZ() + CELL_Z - 1);
        tag.putBoolean("isValid", true);
        //? if >=1.21.10 {
        tile.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
        //?} else {
        /*tile.loadCustomOnly(tag, level.registryAccess());*/
        //?}
    }
}
