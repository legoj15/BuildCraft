/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.zone;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import io.netty.buffer.Unpooled;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import org.jspecify.annotations.Nullable;

import buildcraft.api.core.BCLog;
import buildcraft.lib.misc.RegistryKeyUtil;
import buildcraft.lib.net.PacketBufferBC;

/**
 * The server's record of what one level's terrain looks like from above, for the Zone Planner's survey view past the
 * client's own chunks: the shape 1.7.10 kept in {@code MapWorld}. It is filled <b>only</b> from chunks the server has
 * loaded anyway, for its own reasons (a player walking by, a chunk loader): each chunk is sampled shortly after it
 * loads and again as it unloads, and a chunk still loaded when a planner asks is sampled on the spot. Nothing here
 * loads, generates or waits on a chunk, so a planner costs the server no chunk work at all; terrain no one has visited
 * since this was added simply stays a gap until someone does.
 *
 * <p>Samples are kept per {@link ZoneSurveyRegion} (32&times;32 chunks), loaded from
 * {@code <world>/buildcraft_zone_survey/<dimension>/r.<x>.<z>.bin} when first touched, written back at each level save
 * when changed, and dropped from memory once idle. The files are a cache: deleting them loses nothing but the far
 * view until the terrain is visited again.
 *
 * <p>Server thread only.
 */
public final class ZoneSurveyMap {
    /** Chunks sampled per level tick from those that loaded recently; the rest wait for later ticks. */
    private static final int SAMPLES_PER_TICK = 64;
    /** Most chunks waiting to be sampled. A burst of loads past it (pre-generation) is not queued: those chunks are
     *  sampled as they unload instead. */
    private static final int MAX_PENDING = 4096;
    /** A region nothing has read or written for this many ticks (five minutes), and that has been saved, is dropped. */
    private static final long IDLE_TICKS = 6000;
    /** How often idle regions are looked for. */
    private static final long EVICT_INTERVAL = 1200;
    /** Loaded chunks re-sampled for one far-map message; the rest of a heavily chunk-loaded region goes as last
     *  recorded (at its load, or an earlier send), so one send costs a tick a bounded amount of work. */
    private static final int LIVE_SAMPLES_PER_SEND = 128;
    /** Fewest server ticks between two far-map sends to one player, however many planner screens they open. */
    public static final int SEND_INTERVAL_TICKS = 100;

    /** Emptied by each level's unload and by server stop. */
    private static final Map<ServerLevel, ZoneSurveyMap> MAPS = new HashMap<>();
    /** When each player was last sent a far map (server tick count), so reopening a screen cannot flood them. */
    private static final Map<UUID, Integer> LAST_SEND = new HashMap<>();

    private final ServerLevel level;
    private final Path folder;
    private final Map<Long, ZoneSurveyRegion> regions = new HashMap<>();
    /** Chunks that loaded and are waiting to be sampled, oldest first. */
    private final LinkedHashSet<Long> pending = new LinkedHashSet<>();
    private final short[] scratchHeights = new short[ZoneSurveyRegion.SAMPLES];
    private final byte[] scratchColours = new byte[ZoneSurveyRegion.SAMPLES];

    private ZoneSurveyMap(ServerLevel level) {
        this.level = level;
        Identifier dim = RegistryKeyUtil.id(level.dimension());
        this.folder = level.getServer().getWorldPath(LevelResource.ROOT)
                .resolve("buildcraft_zone_survey").resolve(dim.getNamespace()).resolve(dim.getPath());
    }

    public static ZoneSurveyMap of(ServerLevel level) {
        return MAPS.computeIfAbsent(level, ZoneSurveyMap::new);
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ChunkEvent.Load event) -> {
            // The chunk is not FULL yet and the level must not be touched now (NeoForge's note on the event): it is
            // sampled from the level on a later tick, if it is still there. Posted on the server thread (the FULL
            // status task runs on the main-thread executor, every node), so no guard is needed here.
            if (event.getLevel() instanceof ServerLevel server && of(server).pending.size() < MAX_PENDING) {
                of(server).pending.add(key(event.getChunk().getPos().getMinBlockX() >> 4,
                        event.getChunk().getPos().getMinBlockZ() >> 4));
            }
        });
        NeoForge.EVENT_BUS.addListener((ChunkEvent.Unload event) -> {
            // Saved and about to go: its blocks are still all there, and it is read on its own, not through the level.
            // (1.21.1 types the event's chunk as a ChunkAccess; it is a LevelChunk on every node.)
            // (ChunkMap's unload task runs on the main-thread executor; the guard only keeps a stray caller out.)
            if (event.getLevel() instanceof ServerLevel server && server.getServer().isSameThread()) {
                ZoneSurveyMap map = of(server);
                int cx = event.getChunk().getPos().getMinBlockX() >> 4;
                int cz = event.getChunk().getPos().getMinBlockZ() >> 4;
                map.pending.remove(key(cx, cz));
                if (event.getChunk() instanceof LevelChunk chunk) {
                    map.record(chunk, cx, cz);
                }
            }
        });
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Post event) -> {
            if (event.getLevel() instanceof ServerLevel server) {
                ZoneSurveyMap map = MAPS.get(server);
                if (map != null) {
                    map.tick();
                }
            }
        });
        NeoForge.EVENT_BUS.addListener((LevelEvent.Save event) -> {
            if (event.getLevel() instanceof ServerLevel server) {
                ZoneSurveyMap map = MAPS.get(server);
                if (map != null) {
                    map.saveDirty();
                }
            }
        });
        NeoForge.EVENT_BUS.addListener((LevelEvent.Unload event) -> {
            if (event.getLevel() instanceof ServerLevel server) {
                ZoneSurveyMap map = MAPS.remove(server);
                if (map != null) {
                    map.saveDirty();
                }
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
            MAPS.clear();
            LAST_SEND.clear();
        });
    }

    private static long key(int x, int z) {
        return (x & 0xFFFF_FFFFL) | ((long) z << 32);
    }

    private void tick() {
        for (int n = 0; n < SAMPLES_PER_TICK && !pending.isEmpty(); n++) {
            Iterator<Long> it = pending.iterator();
            long k = it.next();
            it.remove();
            int cx = (int) k;
            int cz = (int) (k >> 32);
            // getChunkNow never loads: a chunk that went again already was sampled as it unloaded.
            if (level.getChunkSource().getChunkNow(cx, cz) instanceof LevelChunk chunk) {
                record(chunk, cx, cz);
            }
        }
        long now = level.getGameTime();
        if (now % EVICT_INTERVAL == 0) {
            regions.values().removeIf(r -> !r.dirty && now - r.lastUsed > IDLE_TICKS);
        }
    }

    private void record(LevelChunk chunk, int cx, int cz) {
        sample(chunk, cx, cz, scratchHeights, scratchColours);
        region(cx >> 5, cz >> 5).put(ZoneSurveyRegion.chunkIndex(cx, cz), scratchHeights, scratchColours);
    }

    /**
     * Samples the lowest-corner column of each {@link ZoneSurveyRegion#CELL}-block cell of {@code chunk}: the topmost
     * block with a map colour, at most 32 blocks under the surface (the client's own scan,
     * {@code ZonePlannerMapChunk}). Block state and colour are read from the chunk itself, never the level.
     */
    static void sample(LevelChunk chunk, int cx, int cz, short[] heights, byte[] colours) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int sx = 0; sx < ZoneSurveyRegion.CELLS; sx++) {
            for (int sz = 0; sz < ZoneSurveyRegion.CELLS; sz++) {
                int lx = sx * ZoneSurveyRegion.CELL;
                int lz = sz * ZoneSurveyRegion.CELL;
                int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz);
                short foundY = ZoneSurveyRegion.NO_DATA;
                byte foundColour = 0;
                for (int y = top; y >= top - 32; y--) {
                    pos.set((cx << 4) + lx, y, (cz << 4) + lz);
                    BlockState state = chunk.getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }
                    int id = state.getMapColor(chunk, pos).id;
                    if (id != 0) {
                        foundY = (short) y;
                        foundColour = (byte) id;
                        break;
                    }
                }
                int s = ZoneSurveyRegion.sampleIndex(lx, lz);
                heights[s] = foundY;
                colours[s] = foundColour;
            }
        }
    }

    // ── Serving ──────────────────────────────────────────────────────────────────────────

    /** True (and the clock restarted) when {@code player} may be sent a far map now. */
    public static boolean tryStartSend(ServerLevel level, UUID player) {
        int now = level.getServer().getTickCount();
        if (LAST_SEND.size() > 64) {
            LAST_SEND.values().removeIf(t -> now - t >= SEND_INTERVAL_TICKS || now < t);
        }
        Integer last = LAST_SEND.get(player);
        if (last != null && now - last < SEND_INTERVAL_TICKS && now >= last) {
            return false;
        }
        LAST_SEND.put(player, now);
        return true;
    }

    /**
     * What this level knows of the chunks of region {@code (regionX, regionZ)} inside the chunk rectangle
     * {@code [minCX, maxCX] x [minCZ, maxCZ]}, encoded for {@link #decode}; {@code null} when it knows none of them.
     * Chunks loaded right now are sampled afresh first (up to {@link #LIVE_SAMPLES_PER_SEND}), so a planner sees the
     * server's current terrain where it has it.
     */
    public byte @Nullable [] encodeRegion(int regionX, int regionZ, int minCX, int minCZ, int maxCX, int maxCZ) {
        int x0 = Math.max(minCX, regionX << 5);
        int x1 = Math.min(maxCX, (regionX << 5) + ZoneSurveyRegion.SIZE - 1);
        int z0 = Math.max(minCZ, regionZ << 5);
        int z1 = Math.min(maxCZ, (regionZ << 5) + ZoneSurveyRegion.SIZE - 1);
        int live = 0;
        for (int cx = x0; cx <= x1 && live < LIVE_SAMPLES_PER_SEND; cx++) {
            for (int cz = z0; cz <= z1 && live < LIVE_SAMPLES_PER_SEND; cz++) {
                if (level.getChunkSource().getChunkNow(cx, cz) instanceof LevelChunk chunk) {
                    record(chunk, cx, cz);
                    live++;
                }
            }
        }
        ZoneSurveyRegion region = region(regionX, regionZ);
        int count = 0;
        for (int cx = x0; cx <= x1; cx++) {
            for (int cz = z0; cz <= z1; cz++) {
                if (region.has(ZoneSurveyRegion.chunkIndex(cx, cz))) {
                    count++;
                }
            }
        }
        if (count == 0) {
            return null;
        }
        PacketBufferBC buf = new PacketBufferBC(Unpooled.buffer(12 + count * 50));
        try {
            buf.writeInt(regionX);
            buf.writeInt(regionZ);
            buf.writeVarInt(count);
            for (int cx = x0; cx <= x1; cx++) {
                for (int cz = z0; cz <= z1; cz++) {
                    int idx = ZoneSurveyRegion.chunkIndex(cx, cz);
                    if (!region.has(idx)) {
                        continue;
                    }
                    buf.writeShort(idx);
                    for (int s = 0; s < ZoneSurveyRegion.SAMPLES; s++) {
                        buf.writeShort(region.height(idx, s));
                    }
                    for (int s = 0; s < ZoneSurveyRegion.SAMPLES; s++) {
                        buf.writeByte(region.colour(idx, s));
                    }
                }
            }
            byte[] out = new byte[buf.readableBytes()];
            buf.readBytes(out);
            return out;
        } finally {
            buf.release();
        }
    }

    /** Receives one decoded chunk of a far-map message. */
    @FunctionalInterface
    public interface ChunkSink {
        void accept(int chunkX, int chunkZ, short[] heights, byte[] colourIds);
    }

    /** Reads a message written by {@link #encodeRegion}, handing each chunk to {@code sink} (fresh arrays each). */
    public static void decode(PacketBufferBC buf, ChunkSink sink) {
        int regionX = buf.readInt();
        int regionZ = buf.readInt();
        int count = buf.readVarInt();
        if (count < 0 || count > ZoneSurveyRegion.CHUNKS) {
            throw new IllegalArgumentException("Bad far-map chunk count " + count);
        }
        for (int i = 0; i < count; i++) {
            int idx = buf.readShort();
            if (idx < 0 || idx >= ZoneSurveyRegion.CHUNKS) {
                throw new IllegalArgumentException("Bad far-map chunk index " + idx);
            }
            short[] heights = new short[ZoneSurveyRegion.SAMPLES];
            byte[] colours = new byte[ZoneSurveyRegion.SAMPLES];
            for (int s = 0; s < ZoneSurveyRegion.SAMPLES; s++) {
                heights[s] = buf.readShort();
            }
            buf.readBytes(colours);
            sink.accept((regionX << 5) + idx / ZoneSurveyRegion.SIZE, (regionZ << 5) + idx % ZoneSurveyRegion.SIZE,
                    heights, colours);
        }
    }

    // ── Storage ──────────────────────────────────────────────────────────────────────────

    private ZoneSurveyRegion region(int regionX, int regionZ) {
        ZoneSurveyRegion region = regions.computeIfAbsent(key(regionX, regionZ), k -> load(regionX, regionZ));
        region.lastUsed = level.getGameTime();
        return region;
    }

    private Path file(int regionX, int regionZ) {
        return folder.resolve("r." + regionX + "." + regionZ + ".bin");
    }

    private ZoneSurveyRegion load(int regionX, int regionZ) {
        Path file = file(regionX, regionZ);
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(new GZIPInputStream(Files.newInputStream(file))))) {
            return ZoneSurveyRegion.read(in);
        } catch (NoSuchFileException e) {
            return new ZoneSurveyRegion();
        } catch (IOException | RuntimeException e) {
            // A cache: a file that cannot be read is rebuilt as the terrain is visited again.
            BCLog.logger.warn("[robotics.zone] Could not read Zone Planner survey file " + file + "; starting it afresh", e);
            return new ZoneSurveyRegion();
        }
    }

    private void saveDirty() {
        for (Map.Entry<Long, ZoneSurveyRegion> e : regions.entrySet()) {
            ZoneSurveyRegion region = e.getValue();
            if (!region.dirty) {
                continue;
            }
            long k = e.getKey();
            Path file = file((int) k, (int) (k >> 32));
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try {
                Files.createDirectories(folder);
                try (DataOutputStream out = new DataOutputStream(
                        new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(tmp))))) {
                    region.write(out);
                }
                try {
                    Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e2) {
                    Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
                }
                region.dirty = false;
            } catch (IOException | RuntimeException e3) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                    // Overwritten by the next save's attempt anyway.
                }
                BCLog.logger.warn("[robotics.zone] Could not write Zone Planner survey file " + file, e3);
            }
        }
    }
}
