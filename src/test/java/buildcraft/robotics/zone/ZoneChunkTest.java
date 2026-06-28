/*
 * Copyright (c) 2026 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.zone;

import java.util.Random;

import io.netty.buffer.Unpooled;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Pure-JUnit characterization of {@link ZoneChunk}: a 16×16 bitset of a single chunk's marked columns, with a
 * {@code fullSet} promotion (drop the bitset once every cell is set) and the inverse demotion path. No {@code Level}
 * or bootstrap is needed — the class is plain bit-twiddling over vanilla {@link CompoundTag}/{@link FriendlyByteBuf}.
 *
 * <p>Pins the {@code flip(0, 256)} demotion fix: demoting a full chunk and clearing one cell must leave exactly 255,
 * not 254 (the old exclusive-bound {@code flip(0, 255)} silently dropped chunk-local (15, 15)).
 */
public class ZoneChunkTest {

    private static int countSet(ZoneChunk chunk) {
        return chunk.getAll().size();
    }

    @Test
    public void testSetAndGetSingleCell() {
        ZoneChunk chunk = new ZoneChunk();
        Assertions.assertFalse(chunk.get(3, 7), "a fresh chunk is empty");

        chunk.set(3, 7, true);
        Assertions.assertTrue(chunk.get(3, 7), "the set cell reads back");
        Assertions.assertFalse(chunk.get(7, 3), "an unrelated cell stays clear (x/z are not symmetric)");
        Assertions.assertEquals(1, countSet(chunk));
    }

    @Test
    public void testIsEmptyTracksContents() {
        ZoneChunk chunk = new ZoneChunk();
        Assertions.assertTrue(chunk.isEmpty(), "a fresh chunk is empty");

        chunk.set(0, 0, true);
        Assertions.assertFalse(chunk.isEmpty(), "a chunk with one cell is non-empty");

        chunk.set(0, 0, false);
        Assertions.assertTrue(chunk.isEmpty(), "clearing the last cell empties it again");
    }

    @Test
    public void testFillingPromotesToFullSet() {
        ZoneChunk chunk = new ZoneChunk();
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                chunk.set(x, z, true);
            }
        }
        Assertions.assertNull(chunk.property, "filling every cell promotes to fullSet and drops the bitset");
        Assertions.assertTrue(chunk.get(0, 0));
        Assertions.assertTrue(chunk.get(15, 15));
        Assertions.assertEquals(256, countSet(chunk), "a full chunk reports all 256 cells");
        Assertions.assertFalse(chunk.isEmpty());
    }

    /** The flip fix: full chunk, clear ONE cell -> exactly 255 remain, and the corner column (15,15) survives. */
    @Test
    public void testDemoteFromFullThenClearOneCellLeaves255() {
        ZoneChunk chunk = new ZoneChunk();
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                chunk.set(x, z, true);
            }
        }
        // Demote by clearing a single interior cell.
        chunk.set(5, 9, false);

        Assertions.assertFalse(chunk.get(5, 9), "the cleared cell is gone");
        Assertions.assertEquals(255, countSet(chunk), "clearing one of a full chunk leaves exactly 255");
        Assertions.assertTrue(chunk.get(15, 15),
                "the corner column (chunk-local bit 255) must survive demotion (the old off-by-one dropped it)");
        Assertions.assertTrue(chunk.get(0, 0), "an arbitrary other cell survives");
    }

    @Test
    public void testClearingTheCornerCellItselfAfterDemotionLeaves255() {
        ZoneChunk chunk = new ZoneChunk();
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                chunk.set(x, z, true);
            }
        }
        chunk.set(15, 15, false);
        Assertions.assertFalse(chunk.get(15, 15), "clearing the corner clears exactly the corner");
        Assertions.assertEquals(255, countSet(chunk), "and still leaves 255");
    }

    @Test
    public void testCopyConstructorIsDeep() {
        ZoneChunk original = new ZoneChunk();
        original.set(2, 2, true);

        ZoneChunk copy = new ZoneChunk(original);
        original.set(4, 4, true);

        Assertions.assertTrue(copy.get(2, 2), "the copy keeps the original's cells");
        Assertions.assertFalse(copy.get(4, 4), "mutating the original does not leak into the copy");
    }

    @Test
    public void testNbtRoundTripPartial() {
        ZoneChunk before = new ZoneChunk();
        before.set(0, 0, true);
        before.set(15, 0, true);
        before.set(0, 15, true);
        before.set(7, 8, true);

        CompoundTag tag = new CompoundTag();
        before.writeToNBT(tag);

        ZoneChunk after = new ZoneChunk();
        after.readFromNBT(tag);

        assertSameCells(before, after);
    }

    @Test
    public void testNbtRoundTripFullSet() {
        ZoneChunk before = new ZoneChunk();
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                before.set(x, z, true);
            }
        }

        CompoundTag tag = new CompoundTag();
        before.writeToNBT(tag);

        ZoneChunk after = new ZoneChunk();
        after.readFromNBT(tag);

        Assertions.assertNull(after.property, "a fullSet chunk round-trips without re-materialising the bitset");
        Assertions.assertEquals(256, countSet(after));
    }

    @Test
    public void testByteBufRoundTrip() {
        ZoneChunk before = new ZoneChunk();
        before.set(1, 2, true);
        before.set(13, 14, true);

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        before.writeToByteBuf(buf);

        ZoneChunk after = new ZoneChunk().readFromByteBuf(buf);
        assertSameCells(before, after);
        Assertions.assertEquals(0, buf.readableBytes(), "the reader consumes exactly what the writer produced");
    }

    @Test
    public void testRandomBlockPosLandsOnASetCell() {
        ZoneChunk chunk = new ZoneChunk();
        chunk.set(4, 11, true);
        chunk.set(9, 2, true);

        Random rand = new Random(1234L);
        for (int i = 0; i < 50; i++) {
            BlockPos p = chunk.getRandomBlockPos(rand);
            Assertions.assertTrue(chunk.get(p.getX(), p.getZ()),
                    "a random pick must be a cell that is actually set");
        }
    }

    private static void assertSameCells(ZoneChunk a, ZoneChunk b) {
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                Assertions.assertEquals(a.get(x, z), b.get(x, z), "cell (" + x + ", " + z + ") matches");
            }
        }
    }
}
