/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.robotics.zone;

import java.util.ArrayList;
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
            property = BitSet.valueOf(bytes);
        }
    }

    public BlockPos getRandomBlockPos(Random rand) {
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
        int y = rand.nextInt(255);

        return new BlockPos(x, y, z);
    }

    public boolean isEmpty() {
        return !fullSet && (property == null || property.isEmpty());
    }

    public ZoneChunk readFromByteBuf(FriendlyByteBuf buf) {
        int flags = buf.readUnsignedByte();
        if ((flags & 1) != 0) {
            property = BitSet.valueOf(buf.readByteArray());
        }
        fullSet = (flags & 2) != 0;

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
