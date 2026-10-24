/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.nbt;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

/**
 * {@link NbtSquisher#requireExpandsWithin} must answer exactly what the reader would: a tag is accepted when reading
 * it back under the limit succeeds and refused otherwise, measured by the game's own accounter. It also must not hold
 * more than it can use: the bytes it writes for the check are capped, and a write past the cap is abandoned at once.
 */
public class NbtSquisherTester {

    private static byte[] write(CompoundTag tag) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        NbtIo.write(tag, new DataOutputStream(bytes));
        return bytes.toByteArray();
    }

    /** What the reader charges for {@code tag}: the smallest quota it reads back under. */
    private static long accounted(CompoundTag tag) throws IOException {
        NbtAccounter accounter = NbtAccounter.create(Long.MAX_VALUE);
        NbtIo.read(new DataInputStream(new ByteArrayInputStream(write(tag))), accounter);
        return accounter.getUsage();
    }

    /** One tag of each family of structure whose written size and accounted size differ differently. */
    private static Map<String, CompoundTag> shapes() {
        Map<String, CompoundTag> shapes = new LinkedHashMap<>();
        shapes.put("empty", new CompoundTag());

        CompoundTag ascii = new CompoundTag();
        ascii.putString("text", "x".repeat(5_000));
        shapes.put("ascii string", ascii);

        // U+4E2D takes three bytes written and, in the reader's heap estimate, the least of any string character.
        CompoundTag multiByte = new CompoundTag();
        multiByte.putString("text", "中".repeat(10_000));
        multiByte.putString("中文键", "中");
        shapes.put("three-byte string characters", multiByte);

        CompoundTag arrays = new CompoundTag();
        arrays.putByteArray("bytes", new byte[10_000]);
        arrays.putIntArray("ints", new int[10_000]);
        arrays.putLongArray("longs", new long[10_000]);
        shapes.put("arrays", arrays);

        // One written byte a tag; the most the reader charges per written byte is for the smallest tags.
        CompoundTag small = new CompoundTag();
        ListTag bytes = new ListTag();
        for (int i = 0; i < 5_000; i++) {
            bytes.add(ByteTag.valueOf((byte) i));
        }
        small.put("bytes", bytes);
        ListTag compounds = new ListTag();
        for (int i = 0; i < 1_000; i++) {
            compounds.add(new CompoundTag());
        }
        small.put("compounds", compounds);
        shapes.put("many small tags", small);

        CompoundTag nested = new CompoundTag();
        CompoundTag cursor = nested;
        for (int i = 0; i < 100; i++) {
            CompoundTag next = new CompoundTag();
            cursor.put("k", next);
            cursor.putInt("i", i);
            cursor = next;
        }
        shapes.put("deeply nested", nested);
        return shapes;
    }

    @Test
    public void aTagIsAcceptedAtExactlyTheCostTheReaderCharges() throws IOException {
        for (Map.Entry<String, CompoundTag> shape : shapes().entrySet()) {
            long cost = accounted(shape.getValue());
            Assertions.assertDoesNotThrow(() -> NbtSquisher.requireExpandsWithin(shape.getValue(), cost),
                shape.getKey() + ": accepted at its own cost of " + cost);
            if (cost > 0) {
                Assertions.assertThrows(IOException.class,
                    () -> NbtSquisher.requireExpandsWithin(shape.getValue(), cost - 1),
                    shape.getKey() + ": refused one byte under its cost");
            }
        }
    }

    @Test
    public void whatTheReaderChargesIsNeverFarBelowWhatIsWritten() throws IOException {
        // The check caps the bytes it writes at 3/2 of the limit: a stream longer than that cannot read back under the
        // limit, so abandoning the write there never refuses a tag the reader would accept. That holds while the reader
        // charges at least two thirds of a byte per byte written (vanilla charges two per string character against at
        // most three written; NeoForge charges the written length itself). A change of the accounting that charged
        // less fails here, before it rejects a valid upload.
        for (Map.Entry<String, CompoundTag> shape : shapes().entrySet()) {
            long written = write(shape.getValue()).length;
            long cost = accounted(shape.getValue());
            Assertions.assertTrue(written * 2 <= cost * 3,
                shape.getKey() + ": " + written + " bytes written, " + cost + " charged");
        }
    }

    @Test
    public void theWriteCapIsThreeHalvesOfTheLimitAndNeverOverflows() {
        Assertions.assertEquals(0, NbtSquisher.writtenCap(0));
        Assertions.assertEquals(150, NbtSquisher.writtenCap(100));
        Assertions.assertEquals(128L * 1024 * 1024 * 3 / 2, NbtSquisher.writtenCap(128L * 1024 * 1024));
        Assertions.assertEquals(Long.MAX_VALUE, NbtSquisher.writtenCap(Long.MAX_VALUE));
        Assertions.assertEquals(Long.MAX_VALUE, NbtSquisher.writtenCap(Long.MAX_VALUE - 1));
        // A negative limit allows nothing, however large its magnitude (the halving must not wrap it into a huge cap).
        Assertions.assertEquals(0, NbtSquisher.writtenCap(-1));
        Assertions.assertEquals(0, NbtSquisher.writtenCap(-10));
        Assertions.assertEquals(0, NbtSquisher.writtenCap(Long.MIN_VALUE));
    }

    @Test
    public void aTagPastTheLimitIsRefused() {
        CompoundTag huge = new CompoundTag();
        huge.putByteArray("padding", new byte[1024 * 1024]);
        Assertions.assertThrows(IOException.class, () -> NbtSquisher.requireExpandsWithin(huge, 4 * 1024));
        Assertions.assertThrows(IOException.class, () -> NbtSquisher.requireExpandsWithin(huge, 0));
        Assertions.assertThrows(IOException.class, () -> NbtSquisher.requireExpandsWithin(huge, -1));
        Assertions.assertThrows(IOException.class, () -> NbtSquisher.requireExpandsWithin(huge, -10));
        Assertions.assertDoesNotThrow(() -> NbtSquisher.requireExpandsWithin(huge, 4L * 1024 * 1024));
    }

    // ---------- the capped buffer ----------

    private static byte[] pattern(int length) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = (byte) (i * 31 + 7);
        }
        return bytes;
    }

    @Test
    public void theBufferReadsBackWhatWasWrittenAcrossItsChunks() throws IOException {
        byte[] data = pattern(1_000);
        CappedByteBuffer buffer = new CappedByteBuffer(10_000, 7);
        buffer.write(data, 0, 3);
        buffer.write(data[3]);
        buffer.write(data, 4, 500);
        buffer.write(data, 504, 496);
        Assertions.assertEquals(1_000, buffer.size());

        byte[] viaBulk = new byte[1_000];
        new DataInputStream(buffer.openRead()).readFully(viaBulk);
        Assertions.assertArrayEquals(data, viaBulk);

        InputStream single = buffer.openRead();
        Assertions.assertEquals(1_000, single.available());
        for (int i = 0; i < data.length; i++) {
            Assertions.assertEquals(data[i] & 0xFF, single.read(), "byte " + i);
        }
        Assertions.assertEquals(-1, single.read());
        Assertions.assertEquals(-1, single.read(new byte[4], 0, 4));
    }

    @Test
    public void aWritePastTheLimitIsRefusedAndKeepsNothingPastIt() throws IOException {
        CappedByteBuffer buffer = new CappedByteBuffer(100, 16);
        buffer.write(pattern(60), 0, 60);
        buffer.write(pattern(40), 0, 40);
        Assertions.assertEquals(100, buffer.size(), "exactly at the limit is fine");

        Assertions.assertThrows(IOException.class, () -> buffer.write(1));
        Assertions.assertEquals(100, buffer.size());

        CappedByteBuffer crossing = new CappedByteBuffer(100, 16);
        crossing.write(pattern(90), 0, 90);
        Assertions.assertThrows(IOException.class, () -> crossing.write(pattern(11), 0, 11));
        Assertions.assertEquals(90, crossing.size(), "a write that would cross the limit is refused whole, not cut");

        CappedByteBuffer oneBig = new CappedByteBuffer(100, 16);
        Assertions.assertThrows(IOException.class, () -> oneBig.write(new byte[1024 * 1024], 0, 1024 * 1024));
        Assertions.assertEquals(0, oneBig.size());
    }
}
