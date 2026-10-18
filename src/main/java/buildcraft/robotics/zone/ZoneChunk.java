/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.robotics.zone;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Random;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

import buildcraft.lib.misc.NBTUtilBC;

public class ZoneChunk {
    public BitSet property;
    private boolean fullSet = false;

    public ZoneChunk() {}

    public ZoneChunk(ZoneChunk old) {
        if (old.property != null) {
            property = BitSet.valueOf(old.property.toLongArray());
        }
        fullSet = old.fullSet;
    }

    /** A chunk with every column marked. */
    public static ZoneChunk full() {
        ZoneChunk chunk = new ZoneChunk();
        chunk.fullSet = true;
        return chunk;
    }

    /** True when every column is marked. */
    public boolean isFull() {
        return fullSet;
    }

    public boolean get(int xChunk, int zChunk) {
        return fullSet || property != null && property.get(xChunk + zChunk * 16);
    }

    public void set(int xChunk, int zChunk, boolean value) {
        if (value) {
            if (fullSet) {
                return;
            }

            if (property == null) {
                property = new BitSet(16 * 16);
            }

            property.set(xChunk + zChunk * 16, true);

            if (property.cardinality() >= 16 * 16) {
                property = null;
                fullSet = true;
            }
        } else {
            if (fullSet) {
                property = new BitSet(16 * 16);
                // BitSet.flip's upper bound is EXCLUSIVE: to set all 256 bits we flip [0, 256), not [0, 255).
                // The old `16 * 16 - 1` left bit 255 unset, so demoting a full chunk and clearing one cell left
                // 254 cells instead of 255 (and silently dropped the corner column at chunk-local (15, 15)).
                property.flip(0, 16 * 16);
                fullSet = false;
            } else if (property == null) {
                // Note - ZonePlan should usually destroy such chunks
                property = new BitSet(16 * 16);
            }

            property.set(xChunk + zChunk * 16, false);
        }
    }

    /** Returns all set positions as [x, z] pairs relative to this chunk (0-15). */
    public List<int[]> getAll() {
        List<int[]> result = new ArrayList<>();
        for (int zChunk = 0; zChunk < 16; zChunk++) {
            for (int xChunk = 0; xChunk < 16; xChunk++) {
                if (get(xChunk, zChunk)) {
                    result.add(new int[]{xChunk, zChunk});
                }
            }
        }
        return result;
    }

    /** Receives one marked column of a chunk, as chunk-local coordinates 0-15. */
    @FunctionalInterface
    public interface ColumnVisitor {
        void visit(int xChunk, int zChunk);
    }

    /** Visits every marked column, in the order {@link #getAll()} lists them, without allocating (a full-survey
     *  layer holds millions of columns, so a per-column array is not affordable on a render path). */
    public void forEachSet(ColumnVisitor visitor) {
        if (fullSet) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    visitor.visit(x, z);
                }
            }
        } else if (property != null) {
            for (int bit = property.nextSetBit(0); bit >= 0 && bit < BITS; bit = property.nextSetBit(bit + 1)) {
                visitor.visit(bit & 15, bit >> 4);
            }
        }
    }

    /** The marked columns of one row (fixed {@code zChunk}) as a 16-bit mask, bit {@code x} for column {@code x}. */
    public int rowBits(int zChunk) {
        if (fullSet) {
            return 0xFFFF;
        }
        if (property == null) {
            return 0;
        }
        int end = (zChunk + 1) << 4;
        int row = 0;
        for (int bit = property.nextSetBit(zChunk << 4); bit >= 0 && bit < end; bit = property.nextSetBit(bit + 1)) {
            row |= 1 << (bit & 15);
        }
        return row;
    }

    /** A chunk marking the columns in {@code rows} (16 masks, as {@link #rowBits} reads them); empty rows give an
     *  empty chunk, and 16 full rows give the same chunk {@link #full()} does. */
    public static ZoneChunk fromRows(int[] rows) {
        BitSet bits = new BitSet(BITS);
        for (int z = 0; z < 16; z++) {
            int row = rows[z] & 0xFFFF;
            while (row != 0) {
                int x = Integer.numberOfTrailingZeros(row);
                bits.set((z << 4) | x);
                row &= row - 1;
            }
        }
        ZoneChunk chunk = new ZoneChunk();
        chunk.adoptBits(bits);
        return chunk;
    }

    public void writeToNBT(CompoundTag nbt) {
        nbt.putBoolean("fullSet", fullSet);

        if (property != null) {
            nbt.putByteArray("bits", property.toByteArray());
        }
    }

    public void readFromNBT(CompoundTag nbt) {
        fullSet = NBTUtilBC.getBoolean(nbt, "fullSet", false);

        byte[] bytes = NBTUtilBC.getByteArray(nbt, "bits", null);
        if (bytes != null) {
            // Untrusted: a corrupt or hand-edited save may carry bits past the chunk's 256 columns. Bytes beyond
            // the 32 that hold them are cut off before the set is built (so a huge array costs nothing).
            adoptBits(BitSet.valueOf(bytes.length > MAX_BIT_BYTES ? Arrays.copyOf(bytes, MAX_BIT_BYTES) : bytes));
        }
    }

    /** Takes {@code bits} (already limited to indexes 0..255) as this chunk's columns, in canonical form: an empty
     *  set is no set, and a set marking all 256 columns is the full flag. Call after {@link #fullSet} is read. */
    private void adoptBits(BitSet bits) {
        int count = bits.cardinality();
        if (fullSet || count == 0) {
            property = null;
        } else if (count == BITS) {
            property = null;
            fullSet = true;
        } else {
            property = bits;
        }
    }

    /** A random set column at a y in 7.1.x's fixed {@code 0..254} ({@code nextInt(255)}), for callers with no level
     *  to take a height range from. Same draws as {@code getRandomBlockPos(rand, 0, 254)}. */
    public BlockPos getRandomBlockPos(Random rand) {
        return getRandomBlockPos(rand, 0, 254);
    }

    /** A random set column at a y drawn from {@code minY..maxY}, both inclusive: the robot level's build range, which
     *  on the 26.x worlds runs from -64 to 319 rather than 7.1.x's 0..254. */
    public BlockPos getRandomBlockPos(Random rand, int minY, int maxY) {
        int x, z;

        if (fullSet) {
            x = rand.nextInt(16);
            z = rand.nextInt(16);
        } else {
            int bitId = rand.nextInt(property.cardinality());
            int bitPosition = property.nextSetBit(0);

            while (bitId > 0) {
                bitId--;
                bitPosition = property.nextSetBit(bitPosition + 1);
            }

            z = bitPosition / 16;
            x = bitPosition - 16 * z;
        }
        int y = minY + rand.nextInt(maxY - minY + 1);

        return new BlockPos(x, y, z);
    }

    public boolean isEmpty() {
        return !fullSet && (property == null || property.isEmpty());
    }

    /** A chunk's bit set covers 16x16 = 256 columns, so its byte form is 32 bytes at most. */
    private static final int BITS = 16 * 16;
    private static final int MAX_BIT_BYTES = BITS / 8;

    /** Whether both chunks mark the same columns ("every column" is the same whether it is flagged full or set
     *  bit by bit). */
    public boolean sameContent(ZoneChunk other) {
        return effectiveBits().equals(other.effectiveBits());
    }

    private BitSet effectiveBits() {
        if (fullSet) {
            BitSet all = new BitSet(16 * 16);
            all.set(0, 16 * 16);
            return all;
        }
        return property == null ? new BitSet(0) : property;
    }

    public ZoneChunk readFromByteBuf(FriendlyByteBuf buf) {
        int flags = buf.readUnsignedByte();
        fullSet = (flags & 2) != 0;
        if ((flags & 1) != 0) {
            // Bounded: a chunk's bit set is 32 bytes at most (so no bit past column 255 can ride in one), and an
            // unbounded read would let a malformed message allocate whatever length it declares.
            adoptBits(BitSet.valueOf(buf.readByteArray(MAX_BIT_BYTES)));
        } else {
            property = null;
        }

        return this;
    }

    public void writeToByteBuf(FriendlyByteBuf buf) {
        int flags = (fullSet ? 2 : 0) | (property != null ? 1 : 0);
        buf.writeByte(flags);
        if (property != null) {
            buf.writeByteArray(property.toByteArray());
        }
    }
}
