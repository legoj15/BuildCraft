/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.robotics.zone;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.core.IZone;
import buildcraft.lib.misc.NBTUtilBC;

/**
 * One dye layer of a Zone Planner: a sparse mosaic of {@link ZoneChunk}s keyed by chunk coordinate.
 *
 * <p>Chunks are keyed by a packed {@code long} ({@code chunkX} in the low 32 bits, {@code chunkZ} in the high
 * 32 — the same layout vanilla uses for its chunk maps) rather than a {@code ChunkPos} object. That keeps the
 * set/get hot path (hit once per painted column) allocation-free, and — because this class is then free of any
 * vanilla type whose static init needs the registries up — lets the pure zone-math be exercised by cheap JUnit
 * on every node. The on-disk / on-wire form is unchanged: each chunk still serialises its {@code chunkX} /
 * {@code chunkZ} as plain ints (the {@code "chunkMapping"} contract shared with {@code ItemMapLocation}).
 */
public class ZonePlan implements IZone {
    private final HashMap<Long, ZoneChunk> chunkMapping = new HashMap<>();

    public ZonePlan() {}

    public ZonePlan(ZonePlan old) {
        for (Map.Entry<Long, ZoneChunk> entry : old.chunkMapping.entrySet()) {
            chunkMapping.put(entry.getKey(), new ZoneChunk(entry.getValue()));
        }
    }

    /** Packs a chunk coordinate into the map key: {@code chunkX} low, {@code chunkZ} high. */
    public static long packChunkKey(int chunkX, int chunkZ) {
        return (chunkX & 0xFFFF_FFFFL) | ((long) chunkZ << 32);
    }

    public static int unpackChunkX(long key) {
        return (int) (key & 0xFFFF_FFFFL);
    }

    public static int unpackChunkZ(long key) {
        return (int) (key >> 32);
    }

    public boolean get(int x, int z) {
        long key = packChunkKey(x >> 4, z >> 4);
        ZoneChunk property = chunkMapping.get(key);
        if (property == null) {
            return false;
        }
        return property.get(x & 0xF, z & 0xF);
    }

    public void set(int x, int z, boolean val) {
        long key = packChunkKey(x >> 4, z >> 4);
        ZoneChunk property = chunkMapping.get(key);

        if (property == null) {
            if (val) {
                property = new ZoneChunk();
                chunkMapping.put(key, property);
            } else {
                return;
            }
        }

        property.set(x & 0xF, z & 0xF, val);

        if (property.isEmpty()) {
            chunkMapping.remove(key);
        }
    }

    public List<int[]> getAll() {
        List<int[]> result = new ArrayList<>();
        chunkMapping.forEach((key, zoneChunk) -> {
            int startX = unpackChunkX(key) << 4;
            int startZ = unpackChunkZ(key) << 4;
            for (int[] p : zoneChunk.getAll()) {
                result.add(new int[]{p[0] + startX, p[1] + startZ});
            }
        });
        return result;
    }

    public ZonePlan getWithOffset(int offsetX, int offsetZ) {
        ZonePlan zonePlan = new ZonePlan();
        getAll().forEach(p -> zonePlan.set(p[0] + offsetX, p[1] + offsetZ, true));
        return zonePlan;
    }

    public boolean hasChunk(int chunkX, int chunkZ) {
        return chunkMapping.containsKey(packChunkKey(chunkX, chunkZ));
    }

    /** The packed chunk keys currently occupied — decode with {@link #unpackChunkX}/{@link #unpackChunkZ}. */
    public Set<Long> getChunkKeys() {
        return chunkMapping.keySet();
    }

    public HashMap<Long, ZoneChunk> getChunkMapping() {
        return chunkMapping;
    }

    public void writeToNBT(CompoundTag nbt) {
        ListTag list = new ListTag();
        for (Map.Entry<Long, ZoneChunk> entry : chunkMapping.entrySet()) {
            CompoundTag zoneChunkTag = new CompoundTag();
            entry.getValue().writeToNBT(zoneChunkTag);
            zoneChunkTag.putInt("chunkX", unpackChunkX(entry.getKey()));
            zoneChunkTag.putInt("chunkZ", unpackChunkZ(entry.getKey()));
            list.add(zoneChunkTag);
        }
        nbt.put("chunkMapping", list);
    }

    public void readFromNBT(CompoundTag nbt) {
        chunkMapping.clear();
        ListTag list = NBTUtilBC.getList(nbt, "chunkMapping", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag zoneChunkTag = NBTUtilBC.getCompound(list, i);
            ZoneChunk chunk = new ZoneChunk();
            chunk.readFromNBT(zoneChunkTag);
            chunkMapping.put(
                packChunkKey(
                    NBTUtilBC.getInt(zoneChunkTag, "chunkX", 0),
                    NBTUtilBC.getInt(zoneChunkTag, "chunkZ", 0)
                ),
                chunk
            );
        }
    }

    @Override
    public double distanceTo(BlockPos pos) {
        return Math.sqrt(distanceToSquared(pos));
    }

    @Override
    public double distanceToSquared(BlockPos pos) {
        double maxSqrDistance = Double.MAX_VALUE;

        for (Map.Entry<Long, ZoneChunk> e : chunkMapping.entrySet()) {
            double dx = (unpackChunkX(e.getKey()) << 4) + 8 - pos.getX();
            double dz = (unpackChunkZ(e.getKey()) << 4) + 8 - pos.getZ();

            double sqrDistance = dx * dx + dz * dz;

            if (sqrDistance < maxSqrDistance) {
                maxSqrDistance = sqrDistance;
            }
        }

        return maxSqrDistance;
    }

    @Override
    public boolean contains(Vec3 point) {
        int xBlock = (int) Math.floor(point.x);
        int zBlock = (int) Math.floor(point.z);

        return get(xBlock, zBlock);
    }

    @Override
    public BlockPos getRandomBlockPos(Random rand) {
        if (chunkMapping.isEmpty()) {
            return null;
        }

        int chunkId = rand.nextInt(chunkMapping.size());

        for (Map.Entry<Long, ZoneChunk> e : chunkMapping.entrySet()) {
            if (chunkId == 0) {
                BlockPos i = e.getValue().getRandomBlockPos(rand);
                int x = (unpackChunkX(e.getKey()) << 4) + i.getX();
                int z = (unpackChunkZ(e.getKey()) << 4) + i.getZ();

                return new BlockPos(x, i.getY(), z);
            }

            chunkId--;
        }

        return null;
    }

    public ZonePlan readFromByteBuf(FriendlyByteBuf buf) {
        chunkMapping.clear();
        int size = buf.readInt();
        for (int i = 0; i < size; i++) {
            long key = packChunkKey(buf.readInt(), buf.readInt());
            ZoneChunk value = new ZoneChunk();
            value.readFromByteBuf(buf);
            chunkMapping.put(key, value);
        }
        return this;
    }

    public void writeToByteBuf(FriendlyByteBuf buf) {
        buf.writeInt(chunkMapping.size());
        for (Map.Entry<Long, ZoneChunk> e : chunkMapping.entrySet()) {
            buf.writeInt(unpackChunkX(e.getKey()));
            buf.writeInt(unpackChunkZ(e.getKey()));
            e.getValue().writeToByteBuf(buf);
        }
    }
}
