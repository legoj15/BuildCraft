/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric;

//? if fabric {
/*import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
//? if >=1.21.10 {
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
//?}
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import buildcraft.platform.ChunkTickets;
import buildcraft.platform.Platform;

/^*
 * {@link ChunkTickets} on Fabric: a ticket type of BuildCraft's own ({@code buildcraftunofficial:chunkloader}, the
 * twin of NeoForge's ticket controller), with the owners tracked here so a chunk's ticket goes only when its last owner
 * lets go. Not vanilla's forced-chunk set: that set is shared with /forceload, other mods and the game-test framework,
 * so releasing a chunk there unforced it for all of them too. The owner sets are dropped as their level unloads
 * ({@link #register}): dimension keys are shared between worlds, so a set that outlived its level would make every
 * ticket in the next world look already held, and none would be added. Gap: the ticket persists with the level but
 * this owner map is in memory, so after a restart a chunk stays loaded until some tile re-requests and releases it;
 * NeoForge's tickets persist with their owners. On 1.21.1 the ticket type is not registered and its tickets are not
 * saved at all (vanilla saves only its own forced set there), so after a restart a chunk loads again once some tile
 * re-requests it.
 *^/
public final class FabricChunkTickets implements ChunkTickets {
    /^* The forced chunk level (entity ticking), what {@code ServerLevel.setChunkForced} gives a chunk. ^/
    private static final int RADIUS = 2;

    private final Map<ResourceKey<Level>, Map<Long, Set<BlockPos>>> owners = new HashMap<>();
    //? if >=1.21.10 {
    private TicketType type;
    //?} else {
    /^private TicketType<ChunkPos> type;
    ^///?}

    @Override
    public void register() {
        //? if >=1.21.10 {
        // Vanilla's FORCED flags (persisted, loading, simulating, keeping the dimension active) and no timeout.
        type = Registry.register(BuiltInRegistries.TICKET_TYPE,
            Identifier.fromNamespaceAndPath("buildcraftunofficial", "chunkloader"), new TicketType(TicketType.NO_TIMEOUT,
                TicketType.FLAG_PERSIST | TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION
                | TicketType.FLAG_KEEP_DIMENSION_ACTIVE));
        //?} else {
        /^// Vanilla's FORCED type's shape: keyed and ordered by the chunk, no timeout.
        type = TicketType.create("buildcraftunofficial:chunkloader", java.util.Comparator.comparingLong(ChunkPos::toLong));
        ^///?}
        // The level's tickets die with it, but this instance and its dimension key outlive it (see the class doc):
        // drop the stale owner sets, or no ticket is added for them in the next world. Client levels share the hook
        // and must be ignored (a player's dimension change replaces the client's level of a still-loaded server
        // one); the side is asked, not just the class, as the game tests' fake client level is a ServerLevel
        // underneath.
        Platform.gameHooks().onLevelUnload(unloaded -> {
            if (!unloaded.isClientSide() && unloaded instanceof ServerLevel level) {
                forget(level);
            }
        });
    }

    /^* The chunk's packed position ({@code ChunkPos.pack} from 26.1, {@code asLong} before). *^/
    private static long chunkKey(int chunkX, int chunkZ) {
        //? if >=26.1 {
        return ChunkPos.pack(chunkX, chunkZ);
        //?} else {
        /^return ChunkPos.asLong(chunkX, chunkZ);
        ^///?}
    }

    private synchronized void forget(ServerLevel level) {
        owners.remove(level.dimension());
    }

    /^*
     * Records {@code owner} as holding the chunk in {@code dimension}: whether it is the chunk's first owner, the one
     * whose claim adds the ticket. Public as the game tests' seam onto the owner sets
     * ({@code StaticTablesLevelUnloadTester}), which they cannot reach through {@link #forceChunk} without loading a
     * chunk of a level no test stands in.
     ^/
    public synchronized boolean claim(ResourceKey<Level> dimension, BlockPos owner, int chunkX, int chunkZ) {
        Set<BlockPos> held = owners.computeIfAbsent(dimension, k -> new HashMap<>())
            .computeIfAbsent(chunkKey(chunkX, chunkZ), k -> new HashSet<>());
        boolean first = held.isEmpty();
        held.add(owner.immutable());
        return first;
    }

    /^* Lets {@code owner}'s hold on the chunk go: whether it was the last owner, whose release removes the ticket. ^/
    public synchronized boolean release(ResourceKey<Level> dimension, BlockPos owner, int chunkX, int chunkZ) {
        Map<Long, Set<BlockPos>> byChunk = owners.get(dimension);
        long key = chunkKey(chunkX, chunkZ);
        Set<BlockPos> held = byChunk == null ? null : byChunk.get(key);
        if (held == null) {
            return false;
        }
        boolean last = held.remove(owner.immutable()) && held.isEmpty();
        if (held.isEmpty()) {
            byChunk.remove(key);
        }
        return last;
    }

    @Override
    public synchronized void forceChunk(ServerLevel level, BlockPos owner, int chunkX, int chunkZ, boolean add) {
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        if (add) {
            if (claim(level.dimension(), owner, chunkX, chunkZ)) {
                //? if >=1.21.10 {
                level.getChunkSource().addTicketWithRadius(type, pos, RADIUS);
                //?} else {
                /^level.getChunkSource().addRegionTicket(type, pos, RADIUS, pos);
                ^///?}
                level.getChunk(chunkX, chunkZ); // loaded now, as setChunkForced does
            }
        } else if (release(level.dimension(), owner, chunkX, chunkZ)) {
            //? if >=1.21.10 {
            level.getChunkSource().removeTicketWithRadius(type, pos, RADIUS);
            //?} else {
            /^level.getChunkSource().removeRegionTicket(type, pos, RADIUS, pos);
            ^///?}
        }
    }
}
*///?}
