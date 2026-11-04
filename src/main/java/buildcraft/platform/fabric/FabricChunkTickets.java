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
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
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
 * NeoForge's tickets persist with their owners.
 *^/
public final class FabricChunkTickets implements ChunkTickets {
    /^* The forced chunk level (entity ticking), what {@code ServerLevel.setChunkForced} gives a chunk. ^/
    private static final int RADIUS = 2;

    private final Map<ResourceKey<Level>, Map<Long, Set<BlockPos>>> owners = new HashMap<>();
    private TicketType type;

    @Override
    public void register() {
        // Vanilla's FORCED flags (persisted, loading, simulating, keeping the dimension active) and no timeout.
        type = Registry.register(BuiltInRegistries.TICKET_TYPE,
            Identifier.fromNamespaceAndPath("buildcraftunofficial", "chunkloader"), new TicketType(TicketType.NO_TIMEOUT,
                TicketType.FLAG_PERSIST | TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION
                | TicketType.FLAG_KEEP_DIMENSION_ACTIVE));
        // The level's tickets die with it, but this instance and its dimension key outlive it (see the class doc):
        // drop the stale owner sets, or no ticket is added for them in the next world. Client levels share the hook
        // and must be ignored (a player's dimension change replaces the client's level of a still-loaded server one).
        Platform.gameHooks().onLevelUnload(unloaded -> {
            if (unloaded instanceof ServerLevel level) {
                forget(level);
            }
        });
    }

    private synchronized void forget(ServerLevel level) {
        owners.remove(level.dimension());
    }

    @Override
    public synchronized void forceChunk(ServerLevel level, BlockPos owner, int chunkX, int chunkZ, boolean add) {
        Map<Long, Set<BlockPos>> byChunk = owners.computeIfAbsent(level.dimension(), k -> new HashMap<>());
        long key = ChunkPos.pack(chunkX, chunkZ);
        Set<BlockPos> held = byChunk.computeIfAbsent(key, k -> new HashSet<>());
        BlockPos immutable = owner.immutable();
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        if (add) {
            boolean first = held.isEmpty();
            held.add(immutable);
            if (first) {
                level.getChunkSource().addTicketWithRadius(type, pos, RADIUS);
                level.getChunk(chunkX, chunkZ); // loaded now, as setChunkForced does
            }
        } else if (held.remove(immutable) && held.isEmpty()) {
            byChunk.remove(key);
            level.getChunkSource().removeTicketWithRadius(type, pos, RADIUS);
        } else if (held.isEmpty()) {
            byChunk.remove(key);
        }
    }
}
*///?}
