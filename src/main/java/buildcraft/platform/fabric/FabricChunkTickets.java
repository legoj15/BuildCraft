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
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import buildcraft.platform.ChunkTickets;

/^*
 * {@link ChunkTickets} on Fabric: vanilla's forced-chunk set, with the owners tracked here so a chunk is unforced only
 * when its last owner lets go. Gap: vanilla persists the forced set but this owner map is in memory, so after a restart
 * a forced chunk stays forced until some tile re-requests and releases it; NeoForge's tickets persist with their owners.
 *^/
public final class FabricChunkTickets implements ChunkTickets {
    private final Map<ResourceKey<Level>, Map<Long, Set<BlockPos>>> owners = new HashMap<>();

    @Override
    public void register() {
        // Nothing to register: vanilla's forced-chunk set needs no owner registry.
    }

    @Override
    public synchronized void forceChunk(ServerLevel level, BlockPos owner, int chunkX, int chunkZ, boolean add) {
        Map<Long, Set<BlockPos>> byChunk = owners.computeIfAbsent(level.dimension(), k -> new HashMap<>());
        long key = ChunkPos.pack(chunkX, chunkZ);
        Set<BlockPos> held = byChunk.computeIfAbsent(key, k -> new HashSet<>());
        BlockPos immutable = owner.immutable();
        if (add) {
            held.add(immutable);
            level.setChunkForced(chunkX, chunkZ, true);
        } else {
            held.remove(immutable);
            if (held.isEmpty()) {
                byChunk.remove(key);
                level.setChunkForced(chunkX, chunkZ, false);
            }
        }
    }
}
*///?}
