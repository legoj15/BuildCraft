/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.tile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.BitSet;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import buildcraft.api.enums.EnumSnapshotType;
import buildcraft.builders.container.LibraryUploadBuffer;
import buildcraft.builders.snapshot.Blueprint;
import buildcraft.builders.snapshot.SchematicBlockAir;
import buildcraft.builders.snapshot.Snapshot;
import buildcraft.builders.snapshot.Template;
import buildcraft.lib.nbt.NbtSquisher;

/**
 * The Architect Table must not store a snapshot the game could not read back. A stored snapshot is read with the
 * library's read ceiling, which refuses a size that claims more cells than it allows and NBT that unpacks to more
 * than its byte budget; a scan that went past either was saved, then skipped as corrupt after a restart, and its
 * preview failed to decode. A template costs next to nothing past its header, so for it the cell limit is the whole
 * story; a blueprint pays sixteen bytes a cell, which uses up the byte budget at exactly the cell limit and leaves
 * nothing for its palette and entities, so its gate keeps room under the limit and the finished scan is checked
 * against the real budget.
 */
public class ArchitectScanCapTester {

    private static final LibraryUploadBuffer.Limits CEILING = LibraryUploadBuffer.Limits.readCeiling();
    /** A budget small enough to build and read blueprints at its boundary in memory (the game's own is 128 MB). */
    private static final LibraryUploadBuffer.Limits SMALL = LibraryUploadBuffer.Limits.ofMegabytes(4);

    private static <S extends Snapshot> S shaped(S snapshot, BlockPos size) {
        snapshot.size = size;
        snapshot.facing = Direction.NORTH;
        snapshot.offset = BlockPos.ZERO;
        return snapshot;
    }

    private static Template emptyTemplateOf(BlockPos size) {
        Template template = shaped(new Template(), size);
        // A scan of air sets no bit, and a template drops trailing zero bits, so even a box at the limit costs nothing.
        template.data = new BitSet();
        template.computeKey();
        return template;
    }

    /** A blueprint of {@code size} that is all air, with {@code paletteEntries} palette entries (the first is what
     *  every cell holds): the palette is what a real scan adds on top of the cells. */
    private static Blueprint blueprintOf(BlockPos size, int paletteEntries) {
        Blueprint blueprint = shaped(new Blueprint(), size);
        for (int i = 0; i < paletteEntries; i++) {
            blueprint.palette.add(new SchematicBlockAir());
        }
        blueprint.data = new int[Snapshot.getDataSize(size)];
        blueprint.computeKey();
        return blueprint;
    }

    /** Whether the store reads {@code snapshot} back the way it reads a file after a restart (and a preview, an
     *  upload, and the library read it): compressed as saved, expanded under the limits' byte budget, and the size
     *  it claims checked against their cell limit. */
    private static boolean theStoreReadsBack(Snapshot snapshot, LibraryUploadBuffer.Limits limits) {
        try {
            ByteArrayOutputStream file = new ByteArrayOutputStream();
            NbtSquisher.squishVanilla(Snapshot.writeToNBT(snapshot), file);
            Snapshot.readFromNBT(
                NbtSquisher.expand(new ByteArrayInputStream(file.toByteArray()), limits.maxExpandedBytes()),
                limits.maxCells());
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Test
    public void aTemplateBoxIsRefusedExactlyWhenItsSnapshotWouldNotReadBack() {
        long limit = CEILING.maxCells();
        // The widest box the largest marker range allows (256 a side) at the height that puts it on the limit, and
        // one layer more.
        int height = (int) (limit / (256 * 256));
        BlockPos atLimit = new BlockPos(256, height, 256);
        BlockPos oneLayerOver = new BlockPos(256, height + 1, 256);
        BlockPos cubeUnder = new BlockPos(100, 100, 100);
        // Past what the default upload option allows (it holds about 2 million cells) but within what a store reads.
        BlockPos cubeBetween = new BlockPos(150, 150, 150);
        BlockPos cubeOver = new BlockPos(1000, 1000, 1000);

        // Preconditions: the read path draws the line where this test puts it, so the comparison below is not two
        // answers that agree by both being wrong.
        Assertions.assertEquals(limit, (long) 256 * height * 256, "the box at the limit is on it");
        Assertions.assertTrue(theStoreReadsBack(emptyTemplateOf(atLimit), CEILING),
            "precondition: a template exactly at the limit reads back");
        Assertions.assertFalse(theStoreReadsBack(emptyTemplateOf(oneLayerOver), CEILING),
            "precondition: one layer over the limit does not");
        Assertions.assertTrue(theStoreReadsBack(emptyTemplateOf(cubeBetween), CEILING));
        Assertions.assertTrue(theStoreReadsBack(emptyTemplateOf(cubeUnder), CEILING));
        Assertions.assertFalse(theStoreReadsBack(emptyTemplateOf(cubeOver), CEILING));

        for (BlockPos size : new BlockPos[] { atLimit, oneLayerOver, cubeBetween, cubeUnder, cubeOver }) {
            boolean refused = TileArchitectTable.tooLargeToScan(size, EnumSnapshotType.TEMPLATE, CEILING);
            Assertions.assertEquals(!theStoreReadsBack(emptyTemplateOf(size), CEILING), refused,
                "scan refusal for a " + size.getX() + " x " + size.getY() + " x " + size.getZ() + " template box");
            // The gate the table uses is the read ceiling's, not the server's upload option (which may be lower, and
            // which no read follows).
            Assertions.assertEquals(refused, TileArchitectTable.tooLargeToScan(size, EnumSnapshotType.TEMPLATE),
                "the table gates on the read ceiling");
        }
    }

    @Test
    public void aBlueprintBoxIsRefusedBeforeItsCellsAloneUseUpTheBudget() {
        // 64 x 64 x 64 is exactly the cell limit of the small budget: sixteen bytes a cell fill all of it, so a
        // blueprint of that many cells (and any palette at all) cannot be read back, though it is within the cell limit.
        BlockPos atCellLimit = new BlockPos(64, 64, 64);
        Assertions.assertEquals(SMALL.maxCells(), Snapshot.getDataSize(atCellLimit), "the box is on the cell limit");
        Assertions.assertFalse(theStoreReadsBack(blueprintOf(atCellLimit, 1), SMALL),
            "precondition: a blueprint at the cell limit does not read back");
        Assertions.assertTrue(theStoreReadsBack(emptyTemplateOf(atCellLimit), SMALL),
            "precondition: a template at the cell limit does");
        Assertions.assertTrue(TileArchitectTable.tooLargeToScan(atCellLimit, EnumSnapshotType.BLUEPRINT, SMALL),
            "a blueprint of that box is refused");
        Assertions.assertFalse(TileArchitectTable.tooLargeToScan(atCellLimit, EnumSnapshotType.TEMPLATE, SMALL),
            "a template of it is not");

        // Every layer height under the footprint: the gate has to land where a blueprint with a palette still reads
        // back, and has to be one boundary (a box is accepted up to some height and refused above it).
        int accepted = 0;
        for (int height = 1; height <= 64; height++) {
            BlockPos size = new BlockPos(64, height, 64);
            if (!TileArchitectTable.tooLargeToScan(size, EnumSnapshotType.BLUEPRINT, SMALL)) {
                Assertions.assertEquals(height - 1, accepted, "no gap below the boundary");
                accepted = height;
                Assertions.assertTrue(theStoreReadsBack(blueprintOf(size, 200), SMALL),
                    "a blueprint of the accepted " + height + "-layer box, with a palette of 200, reads back");
            }
        }
        Assertions.assertTrue(accepted > 0 && accepted < 64, "the boundary is inside the sweep: " + accepted);
    }

    @Test
    public void theRoomKeptUnderTheCellLimitIsAFractionOfTheBudget() {
        // A reserve that grew would turn away boxes that read back: it is for a palette and entities, not a margin.
        Assertions.assertTrue(TileArchitectTable.BLUEPRINT_SCAN_RESERVE_BYTES > 0, "some room is kept");
        Assertions.assertTrue(TileArchitectTable.BLUEPRINT_SCAN_RESERVE_BYTES <= CEILING.maxExpandedBytes() / 32,
            "and no more than a thirty-second of the budget");
    }

    @Test
    public void aFinishedScanIsStoredOnlyIfItReadsBack() {
        // A box the gate accepts, whose palette may still push the blueprint over the budget: only the finished scan
        // knows, so the table asks the reader's own question of it, at exactly the reader's answer.
        BlockPos size = new BlockPos(60, 60, 50);
        Assertions.assertFalse(TileArchitectTable.tooLargeToScan(size, EnumSnapshotType.BLUEPRINT, SMALL),
            "precondition: the gate accepts the box");

        int fits = 0;
        int refused = 0;
        for (int palette : new int[] { 1, 200, 4_000, 20_000 }) {
            Blueprint blueprint = blueprintOf(size, palette);
            boolean reads = theStoreReadsBack(blueprint, SMALL);
            Assertions.assertEquals(reads, TileArchitectTable.readsBack(blueprint, SMALL),
                "a palette of " + palette + ": stored exactly when the store reads it back");
            if (reads) {
                fits++;
            } else {
                refused++;
            }
        }
        Assertions.assertTrue(fits > 0 && refused > 0,
            "the palettes straddle the budget, so the comparison saw both answers: fits=" + fits + " refused=" + refused);

        // And to the byte: the smallest budget the blueprint reads back under accepts it, one byte less does not.
        Blueprint blueprint = blueprintOf(size, 200);
        long cost = readsBackUnder(blueprint);
        LibraryUploadBuffer.Limits exact = new LibraryUploadBuffer.Limits(SMALL.maxUploadBytes(), SMALL.maxChunks(), cost);
        LibraryUploadBuffer.Limits oneLess =
            new LibraryUploadBuffer.Limits(SMALL.maxUploadBytes(), SMALL.maxChunks(), cost - 1);
        Assertions.assertTrue(TileArchitectTable.readsBack(blueprint, exact), "accepted at its own cost of " + cost);
        Assertions.assertFalse(TileArchitectTable.readsBack(blueprint, oneLess), "refused one byte under it");
    }

    /** The smallest byte budget {@code snapshot} reads back under: what the game's accounter charges for it. */
    private static long readsBackUnder(Snapshot snapshot) {
        long fails = 0;
        long fits = 1L << 32;
        while (fits - fails > 1) {
            long mid = (fails + fits) >>> 1;
            if (theStoreReadsBack(snapshot, new LibraryUploadBuffer.Limits(Integer.MAX_VALUE, 1, mid))) {
                fits = mid;
            } else {
                fails = mid;
            }
        }
        return fits;
    }
}
