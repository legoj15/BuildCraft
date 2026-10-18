/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.zone;

import java.util.BitSet;
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

    @Test
    public void testRandomBlockPosSpansTheGivenBuildRange() {
        // 7.1.x drew y from nextInt(255), the old 0..254 world; a 26.x world runs from -64 to 319, and a robot
        // searching its zone must be able to land anywhere in it.
        ZoneChunk chunk = new ZoneChunk();
        chunk.set(4, 11, true);

        Random rand = new Random(42L);
        boolean below = false;
        boolean above = false;
        for (int i = 0; i < 4000; i++) {
            BlockPos p = chunk.getRandomBlockPos(rand, -64, 319);
            Assertions.assertTrue(p.getY() >= -64 && p.getY() <= 319, "y within the build range: " + p);
            Assertions.assertTrue(chunk.get(p.getX(), p.getZ()), "still a set column");
            below |= p.getY() < 0;
            above |= p.getY() > 254;
        }
        Assertions.assertTrue(below, "samples reach below y=0");
        Assertions.assertTrue(above, "samples reach above y=254");
    }

    @Test
    public void testRandomBlockPosHonoursASingleLayerRange() {
        ZoneChunk chunk = new ZoneChunk();
        chunk.set(0, 0, true);
        Assertions.assertEquals(-3, chunk.getRandomBlockPos(new Random(5L), -3, -3).getY(),
                "both bounds are inclusive");
    }

    // ── Bits beyond the chunk ────────────────────────────────────────────────────────────

    /** NBT as a hand-edited or corrupt save could hold it: real columns plus bits at and above index 256. */
    private static CompoundTag nbtWithBits(int... bitIndexes) {
        BitSet bits = new BitSet();
        for (int b : bitIndexes) {
            bits.set(b);
        }
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("fullSet", false);
        tag.putByteArray("bits", bits.toByteArray());
        return tag;
    }

    @Test
    public void testNbtLoadKeepsOnlyTheChunksOwnBits() {
        ZoneChunk chunk = new ZoneChunk();
        chunk.readFromNBT(nbtWithBits(3 + 2 * 16, 256, 300, 511, 4000));

        Assertions.assertTrue(chunk.get(3, 2), "the real column is kept");
        Assertions.assertEquals(1, countSet(chunk), "and nothing else");
        Assertions.assertTrue(chunk.property.length() <= 256,
                "no bit at or above 256 survives (found length " + chunk.property.length() + ")");
        Assertions.assertEquals(1, chunk.property.cardinality(), "the stray bits do not count as columns");
    }

    @Test
    public void testNbtLoadOfOnlyStrayBitsLeavesAnEmptyChunk() {
        ZoneChunk chunk = new ZoneChunk();
        chunk.readFromNBT(nbtWithBits(256, 257, 1000));

        Assertions.assertTrue(chunk.isEmpty(), "a chunk whose only bits lie outside it marks nothing");

        CompoundTag list = new CompoundTag();
        net.minecraft.nbt.ListTag chunks = new net.minecraft.nbt.ListTag();
        CompoundTag entry = nbtWithBits(256, 257, 1000);
        entry.putInt("chunkX", 4);
        entry.putInt("chunkZ", 4);
        chunks.add(entry);
        list.put("chunkMapping", chunks);
        ZonePlan plan = new ZonePlan();
        plan.readFromNBT(list);
        Assertions.assertTrue(plan.isEmpty(), "so the plan drops it, and a stray-bit layer reads as empty");
    }

    @Test
    public void testStrayBitsDoNotPushPickedColumnsOutsideTheChunk() {
        ZoneChunk chunk = new ZoneChunk();
        chunk.readFromNBT(nbtWithBits(5 + 6 * 16, 256 + 40, 256 + 255, 700));

        Random rand = new Random(7L);
        for (int i = 0; i < 200; i++) {
            BlockPos p = chunk.getRandomBlockPos(rand);
            Assertions.assertTrue(p.getX() >= 0 && p.getX() < 16 && p.getZ() >= 0 && p.getZ() < 16,
                    "a robot sample stays inside the chunk: " + p);
            Assertions.assertTrue(chunk.get(p.getX(), p.getZ()), "and lands on a real column");
        }
    }

    @Test
    public void testStrayBitsDoNotPromoteAChunkToFull() {
        // 200 real columns plus 100 stray bits: cardinality 300 >= 256 would flip a naive set() to "full".
        BitSet bits = new BitSet();
        bits.set(0, 200);
        bits.set(256, 356);
        CompoundTag tag = new CompoundTag();
        tag.putByteArray("bits", bits.toByteArray());

        ZoneChunk chunk = new ZoneChunk();
        chunk.readFromNBT(tag);
        chunk.set(15, 15, true); // bit 255, still clear
        Assertions.assertFalse(chunk.isFull(), "56 columns are still clear");
        Assertions.assertEquals(201, countSet(chunk));
    }

    @Test
    public void testSanitisedChunkStillFitsTheWire() {
        ZoneChunk chunk = new ZoneChunk();
        chunk.readFromNBT(nbtWithBits(1, 2, 300, 5000));

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        chunk.writeToByteBuf(buf);
        ZoneChunk onClient = Assertions.assertDoesNotThrow(() -> new ZoneChunk().readFromByteBuf(buf),
                "the client's bounded read accepts what the server now sends");
        assertSameCells(chunk, onClient);
    }

    @Test
    public void testWireReadKeepsOnlyTheChunksOwnBitsAndRejectsAnOversizedArray() {
        // 32 bytes is the whole chunk: nothing at or above bit 256 can ride in one.
        FriendlyByteBuf ok = new FriendlyByteBuf(Unpooled.buffer());
        ok.writeByte(1);
        ok.writeByteArray(new byte[] {0x05});
        ZoneChunk read = new ZoneChunk().readFromByteBuf(ok);
        Assertions.assertTrue(read.get(0, 0) && read.get(2, 0) && countSet(read) == 2);

        // A longer array is a malformed message and is refused outright, not stored.
        FriendlyByteBuf oversized = new FriendlyByteBuf(Unpooled.buffer());
        oversized.writeByte(1);
        oversized.writeByteArray(new byte[33]);
        Assertions.assertThrows(RuntimeException.class, () -> new ZoneChunk().readFromByteBuf(oversized));
    }

    @Test
    public void testNbtLoadOfAnOversizedBitArrayIsClampedNotKept() {
        CompoundTag tag = new CompoundTag();
        byte[] big = new byte[2000];
        big[0] = 1;
        big[1999] = (byte) 0x80;
        tag.putByteArray("bits", big);

        ZoneChunk chunk = new ZoneChunk();
        chunk.readFromNBT(tag);
        Assertions.assertEquals(1, countSet(chunk), "only column (0, 0) is real");
        Assertions.assertTrue(chunk.property.length() <= 256);
    }

    private static void assertSameCells(ZoneChunk a, ZoneChunk b) {
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                Assertions.assertEquals(a.get(x, z), b.get(x, z), "cell (" + x + ", " + z + ") matches");
            }
        }
    }
}
