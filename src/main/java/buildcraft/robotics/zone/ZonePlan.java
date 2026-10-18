/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.robotics.zone;

import java.util.ArrayList;
import java.util.Arrays;
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
    /** How far from its planner, in chunks, a stroke may reach: half the survey span, rounded up. Layers are
     *  stored tile-relative, so {@link #paintRect} clips to tile-relative chunks {@code [-r, r - 1]}. (A zone read
     *  in from a map location may sit further out; painting can never reach or erase it.) */
    public static final int MAX_CHUNK_RADIUS = ((SURVEY_SPAN_BLOCKS / 2) + 15) >> 4;
    /** First tile-relative block coordinate (on either axis) inside the survey square. */
    public static final int SURVEY_MIN_BLOCK = -MAX_CHUNK_RADIUS << 4;
    /** Last tile-relative block coordinate (on either axis) inside the survey square. */
    public static final int SURVEY_MAX_BLOCK = (MAX_CHUNK_RADIUS << 4) - 1;

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

    /** Receives one occupied chunk of a plan: its chunk coordinates and the chunk itself (do not modify it). */
    @FunctionalInterface
    public interface ChunkVisitor {
        void visit(int chunkX, int chunkZ, ZoneChunk chunk);
    }

    /**
     * Visits the occupied chunks whose coordinates lie in {@code [minChunkX, maxChunkX] x [minChunkZ, maxChunkZ]}
     * (both inclusive), in no particular order, and touches no other chunk. A small window is probed coordinate by
     * coordinate and a window wider than the plan is filtered from the plan's own chunks, so the cost follows
     * whichever is smaller: a view of a few chunks pays nothing for a layer painted across the whole survey.
     */
    public void forEachChunkIn(int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ, ChunkVisitor visitor) {
        if (minChunkX > maxChunkX || minChunkZ > maxChunkZ || chunkMapping.isEmpty()) {
            return;
        }
        long window = ((long) maxChunkX - minChunkX + 1) * ((long) maxChunkZ - minChunkZ + 1);
        if (window <= chunkMapping.size()) {
            for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                    ZoneChunk chunk = chunkMapping.get(packChunkKey(cx, cz));
                    if (chunk != null) {
                        visitor.visit(cx, cz, chunk);
                    }
                }
            }
        } else {
            for (Map.Entry<Long, ZoneChunk> e : chunkMapping.entrySet()) {
                int cx = unpackChunkX(e.getKey());
                int cz = unpackChunkZ(e.getKey());
                if (cx >= minChunkX && cx <= maxChunkX && cz >= minChunkZ && cz <= maxChunkZ) {
                    visitor.visit(cx, cz, e.getValue());
                }
            }
        }
    }

    /**
     * A copy of this plan with every marked column moved by {@code (offsetX, offsetZ)} blocks: the tile-relative
     * &lt;-&gt; world translation of a planner transfer. A multiple-of-16 offset on both axes only renumbers the
     * chunks. Any other offset shifts each chunk's rows of 16 bits sideways and down into at most four result
     * chunks, so the cost follows the number of chunks, not the (up to millions of) marked columns.
     */
    public ZonePlan getWithOffset(int offsetX, int offsetZ) {
        ZonePlan out = new ZonePlan();
        int chunkShiftX = Math.floorDiv(offsetX, 16);
        int chunkShiftZ = Math.floorDiv(offsetZ, 16);
        int rx = Math.floorMod(offsetX, 16);
        int rz = Math.floorMod(offsetZ, 16);
        if (rx == 0 && rz == 0) {
            for (Map.Entry<Long, ZoneChunk> e : chunkMapping.entrySet()) {
                if (!e.getValue().isEmpty()) {
                    out.chunkMapping.put(packChunkKey(unpackChunkX(e.getKey()) + chunkShiftX,
                            unpackChunkZ(e.getKey()) + chunkShiftZ), new ZoneChunk(e.getValue()));
                }
            }
        } else {
            HashMap<Long, int[]> rowsByChunk = new HashMap<>();
            // The result chunks one source chunk can reach: [0] same x/z, [1] next x, [2] next z, [3] next both.
            int[][] reach = new int[4][];
            for (Map.Entry<Long, ZoneChunk> e : chunkMapping.entrySet()) {
                ZoneChunk chunk = e.getValue();
                if (chunk.isEmpty()) {
                    continue;
                }
                int baseX = unpackChunkX(e.getKey()) + chunkShiftX;
                int baseZ = unpackChunkZ(e.getKey()) + chunkShiftZ;
                Arrays.fill(reach, null);
                for (int z = 0; z < 16; z++) {
                    int row = chunk.rowBits(z);
                    if (row == 0) {
                        continue;
                    }
                    int zSlot = ((z + rz) >> 4) << 1;
                    int destRow = (z + rz) & 15;
                    int moved = row << rx; // at most 31 bits: the low 16 stay in the column's chunk, the rest spill over
                    int stay = moved & 0xFFFF;
                    int spill = moved >>> 16;
                    if (stay != 0) {
                        reachRows(rowsByChunk, reach, zSlot, baseX, baseZ)[destRow] |= stay;
                    }
                    if (spill != 0) {
                        reachRows(rowsByChunk, reach, zSlot | 1, baseX, baseZ)[destRow] |= spill;
                    }
                }
            }
            for (Map.Entry<Long, int[]> e : rowsByChunk.entrySet()) {
                ZoneChunk chunk = ZoneChunk.fromRows(e.getValue());
                if (!chunk.isEmpty()) {
                    out.chunkMapping.put(e.getKey(), chunk);
                }
            }
        }
        out.modCount++; // like a plan built by set(): its watchers see it as edited
        return out;
    }

    /** The 16 row masks of the result chunk at {@code slot} relative to {@code (baseX, baseZ)}, created on first use. */
    private static int[] reachRows(HashMap<Long, int[]> rowsByChunk, int[][] reach, int slot, int baseX, int baseZ) {
        int[] rows = reach[slot];
        if (rows == null) {
            rows = rowsByChunk.computeIfAbsent(packChunkKey(baseX + (slot & 1), baseZ + (slot >> 1)), k -> new int[16]);
            reach[slot] = rows;
        }
        return rows;
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

    /** A random marked column at a y in 7.1.x's fixed {@code 0..254}; see {@link #getRandomBlockPos(Random, int, int)}. */
    @Override
    public BlockPos getRandomBlockPos(Random rand) {
        return getRandomBlockPos(rand, 0, 254);
    }

    /** A random marked column at a y in {@code minY..maxY} (both inclusive), or null when nothing is marked. A zone
     *  plan is columns only, so the height is the caller's level build range. */
    @Override
    public BlockPos getRandomBlockPos(Random rand, int minY, int maxY) {
        if (chunkMapping.isEmpty()) {
            return null;
        }

        int chunkId = rand.nextInt(chunkMapping.size());

        for (Map.Entry<Long, ZoneChunk> e : chunkMapping.entrySet()) {
            if (chunkId == 0) {
                BlockPos i = e.getValue().getRandomBlockPos(rand, minY, maxY);
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
     * Marks or, with {@code val == false}, clears the tile-relative block rectangle between two corners
     * (inclusive, in either order), clipped to the survey square: the one edit the viewport makes, and the one
     * edit a client may ask the server for. The clip means a stroke can neither reach where the map shows
     * nothing nor erase a far zone it cannot see (one read in from a map location may sit anywhere). Chunks the
     * rectangle covers whole are set or dropped in one step, so even a stroke across the whole square (a client
     * message of four ints) costs a few thousand map operations, not millions of cell writes.
     *
     * @return true if any column changed (the modification counter then moves).
     */
    public boolean paintRect(int x0, int z0, int x1, int z1, boolean val) {
        int minX = Math.max(Math.min(x0, x1), SURVEY_MIN_BLOCK);
        int maxX = Math.min(Math.max(x0, x1), SURVEY_MAX_BLOCK);
        int minZ = Math.max(Math.min(z0, z1), SURVEY_MIN_BLOCK);
        int maxZ = Math.min(Math.max(z0, z1), SURVEY_MAX_BLOCK);
        if (minX > maxX || minZ > maxZ) {
            return false;
        }
        boolean changed = false;
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            int lx0 = Math.max(minX, cx << 4) & 0xF;
            int lx1 = Math.min(maxX, (cx << 4) | 0xF) & 0xF;
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                int lz0 = Math.max(minZ, cz << 4) & 0xF;
                int lz1 = Math.min(maxZ, (cz << 4) | 0xF) & 0xF;
                long key = packChunkKey(cx, cz);
                ZoneChunk chunk = chunkMapping.get(key);
                if (lx0 == 0 && lx1 == 0xF && lz0 == 0 && lz1 == 0xF) {
                    if (val && (chunk == null || !chunk.isFull())) {
                        chunkMapping.put(key, ZoneChunk.full());
                        changed = true;
                    } else if (!val && chunk != null) {
                        chunkMapping.remove(key);
                        changed = true;
                    }
                    continue;
                }
                if (chunk == null) {
                    if (!val) {
                        continue;
                    }
                    chunk = new ZoneChunk();
                    chunkMapping.put(key, chunk);
                }
                for (int lx = lx0; lx <= lx1; lx++) {
                    for (int lz = lz0; lz <= lz1; lz++) {
                        if (chunk.get(lx, lz) != val) {
                            chunk.set(lx, lz, val);
                            changed = true;
                        }
                    }
                }
                if (chunk.isEmpty()) {
                    chunkMapping.remove(key);
                }
            }
        }
        if (changed) {
            modCount++;
        }
        return changed;
    }

    /** {@link #paintRect} on a copy of {@code base}, which is not changed: the viewport's stroke preview. */
    public static ZonePlan withRect(ZonePlan base, int x0, int z0, int x1, int z1, boolean val) {
        ZonePlan out = new ZonePlan(base);
        out.paintRect(x0, z0, x1, z1, val);
        return out;
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
