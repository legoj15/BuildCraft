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
 *
 * <p>Invariant: every chunk in the map has at least one marked column. {@link #set} prunes on the last clear;
 * the deserialisers ({@link #readFromNBT}, {@link #readFromByteBuf}) and the copy constructor drop
 * column-less chunks from crafted or legacy data, so the invariant holds at every trust boundary. It is what
 * keeps {@link #isEmpty()} a truthful "nothing is marked" for the planner's output gate, and what keeps
 * {@code ZoneChunk.getRandomBlockPos} from NPE-ing on a chunk with no columns.
 */
public class ZonePlan implements IZone {
    /** Width, in blocks, of the Zone Planner's survey view: the map window is 213 px wide and zooms out to
     *  0.125 px/block ({@code GuiZonePlanner}, {@code ZoneMapCamera.MIN_PX_PER_BLOCK}), "about 1,700 blocks". */
    public static final int SURVEY_SPAN_BLOCKS = 1704;
    /** How far from its planner, in chunks, a layer may reach: half the survey span, rounded up. Layers are
     *  stored tile-relative, so this bounds the chunk coordinates of a layer's keys. */
    public static final int MAX_CHUNK_RADIUS = ((SURVEY_SPAN_BLOCKS / 2) + 15) >> 4;
    /** Every chunk of the survey square: the most a layer painted inside it can hold. */
    public static final int MAX_CHUNKS = (2 * MAX_CHUNK_RADIUS) * (2 * MAX_CHUNK_RADIUS);
    /** Most chunk entries one wire message may declare. Comfortably above {@link #MAX_CHUNKS} (a full square
     *  plus a ring of chunks read in from a far-away map location), far below what a packet could declare. */
    public static final int MAX_CHUNKS_ON_WIRE = 1 << 15;

    private final HashMap<Long, ZoneChunk> chunkMapping = new HashMap<>();
    /** Bumped on every mutation made through this class (not through {@link #getChunkMapping()}'s raw map), so a
     *  watcher can tell "unchanged since I last looked" without re-serialising. */
    private int modCount;

    public ZonePlan() {}

    public ZonePlan(ZonePlan old) {
        for (Map.Entry<Long, ZoneChunk> entry : old.chunkMapping.entrySet()) {
            ZoneChunk chunk = entry.getValue();
            if (!chunk.isEmpty()) {
                chunkMapping.put(entry.getKey(), new ZoneChunk(chunk));
            }
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
        modCount++;

        if (property.isEmpty()) {
            chunkMapping.remove(key);
        }
    }

    /** A counter that moves whenever this plan is mutated through its own methods (never through the raw map
     *  {@link #getChunkMapping()} exposes). Equal counter on the same instance means unchanged. */
    public int modCount() {
        return modCount;
    }

    /** Whether a tile-relative chunk lies inside the survey square: {@code [-r, r - 1]} on each axis, since
     *  block offsets floor into chunks. */
    public static boolean inSurveyRange(int chunkX, int chunkZ) {
        return chunkX >= -MAX_CHUNK_RADIUS && chunkX < MAX_CHUNK_RADIUS
                && chunkZ >= -MAX_CHUNK_RADIUS && chunkZ < MAX_CHUNK_RADIUS;
    }

    /**
     * What the server stores when a client sends a whole layer: the client's chunks inside the survey square,
     * plus {@code existing}'s own chunks outside it. A viewer can only see and paint within the square, so it can
     * neither introduce a far chunk nor erase or rewrite one (a zone read in from a map location may sit
     * anywhere; the client gets such chunks in its copy and hands them back untouched). Neither input is changed.
     */
    public static ZonePlan clampToSurvey(ZonePlan received, ZonePlan existing) {
        ZonePlan out = new ZonePlan();
        for (Map.Entry<Long, ZoneChunk> e : received.chunkMapping.entrySet()) {
            long key = e.getKey();
            if (inSurveyRange(unpackChunkX(key), unpackChunkZ(key)) && !e.getValue().isEmpty()) {
                out.chunkMapping.put(key, new ZoneChunk(e.getValue()));
            }
        }
        for (Map.Entry<Long, ZoneChunk> e : existing.chunkMapping.entrySet()) {
            long key = e.getKey();
            if (!inSurveyRange(unpackChunkX(key), unpackChunkZ(key)) && !e.getValue().isEmpty()) {
                out.chunkMapping.put(key, new ZoneChunk(e.getValue()));
            }
        }
        return out;
    }

    /** True when both plans mark exactly the same columns, however they were built (unlike comparing the wire
     *  bytes, which follow the hash map's iteration order). */
    public boolean sameContent(ZonePlan other) {
        if (other == this) {
            return true;
        }
        if (chunkMapping.size() != other.chunkMapping.size()) {
            return false;
        }
        for (Map.Entry<Long, ZoneChunk> e : chunkMapping.entrySet()) {
            ZoneChunk theirs = other.chunkMapping.get(e.getKey());
            if (theirs == null || !e.getValue().sameContent(theirs)) {
                return false;
            }
        }
        return true;
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

    /** True when no column is marked — a painted nothing. Gates the planner's output transfer. */
    public boolean isEmpty() {
        return chunkMapping.isEmpty();
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
        modCount++;
        ListTag list = NBTUtilBC.getList(nbt, "chunkMapping", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag zoneChunkTag = NBTUtilBC.getCompound(list, i);
            ZoneChunk chunk = new ZoneChunk();
            chunk.readFromNBT(zoneChunkTag);
            if (chunk.isEmpty()) {
                continue;
            }
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
        modCount++;
        int size = buf.readInt();
        for (int i = 0; i < size; i++) {
            long key = packChunkKey(buf.readInt(), buf.readInt());
            ZoneChunk value = new ZoneChunk();
            value.readFromByteBuf(buf);
            if (value.isEmpty()) {
                continue;
            }
            chunkMapping.put(key, value);
        }
        return this;
    }

    /**
     * {@link #readFromByteBuf} for a payload from an untrusted sender (a client's paint message): refuses a
     * declared chunk count above {@link #MAX_CHUNKS_ON_WIRE} before reading any entry, caps each chunk's bit
     * set at its real size, and reports a truncated or malformed message as a rejection rather than throwing.
     * Coordinates are NOT filtered here &mdash; see {@link #clampToSurvey}. On {@code false} this plan is left
     * empty.
     */
    public boolean readFromByteBufBounded(FriendlyByteBuf buf) {
        chunkMapping.clear();
        modCount++;
        try {
            int size = buf.readInt();
            if (size < 0 || size > MAX_CHUNKS_ON_WIRE) {
                return false;
            }
            for (int i = 0; i < size; i++) {
                long key = packChunkKey(buf.readInt(), buf.readInt());
                ZoneChunk value = new ZoneChunk();
                value.readFromByteBuf(buf);
                if (!value.isEmpty()) {
                    chunkMapping.put(key, value);
                }
            }
            return true;
        } catch (RuntimeException malformed) { // IndexOutOfBounds (truncated), DecoderException (oversized array)
            chunkMapping.clear();
            return false;
        }
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
