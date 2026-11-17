/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge;

//? if forge {
/*import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
//? if >=1.21.10 {
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
//?}
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

//? if >=1.21.10 {
import net.minecraftforge.registries.RegisterEvent;
//?}

import buildcraft.platform.ChunkTickets;
import buildcraft.platform.Platform;

/^*
 * {@link ChunkTickets} on MinecraftForge: a ticket type of BuildCraft's own ({@code buildcraftunofficial:chunkloader}),
 * with the owners tracked here so a chunk's ticket goes only when its last owner lets go, exactly as the Fabric twin does
 * (read its class documentation for why a vanilla forced-chunk set is not used and what the in-memory owner map costs
 * after a restart). Forge 26.3 has no chunk manager (the 1.21.1 one is gone), so vanilla's ticket type is the only
 * route; this class is that route, not a Forge API. Forge 1.21.1 still has {@code ForgeChunkManager}, but the same vanilla
 * route works there (Fabric's 1.21.1 arm, which this copies: an unregistered type made by {@code TicketType.create},
 * whose tickets are not saved), so both Forge lines behave alike and the game tests' owner probe holds on both.
 *
 * <p><b>Registering the type.</b> Fabric registers it inside the constructor call; Forge freezes the vanilla registries
 * before mods are constructed and fires {@code RegisterEvent} for each of them afterwards, so {@link #register} builds
 * the type and subscribes to that event, which adds it to vanilla's ticket-type registry (a registered type is what lets
 * the level save and reload its tickets).
 *^/
public final class ForgeChunkTickets implements ChunkTickets {
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
        TicketType created = new TicketType(TicketType.NO_TIMEOUT,
            TicketType.FLAG_PERSIST | TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION
            | TicketType.FLAG_KEEP_DIMENSION_ACTIVE);
        type = created;
        ForgeBus.listenMod(RegisterEvent.class, event -> event.register(
            Registries.TICKET_TYPE, Identifier.fromNamespaceAndPath("buildcraftunofficial", "chunkloader"), () -> created));
        //?} else {
        /^// Vanilla's FORCED type's shape: keyed and ordered by the chunk, no timeout (1.21.1 has no ticket-type registry).
        type = TicketType.create("buildcraftunofficial:chunkloader", java.util.Comparator.comparingLong(ChunkPos::toLong));
        ^///?}
        // The level's tickets die with it, but this instance and its dimension key outlive it (see the Fabric twin): drop
        // the stale owner sets, or no ticket is added for them in the next world. Client levels share the hook and must
        // be ignored; the side is asked, not just the class, as the game tests' fake client level is a ServerLevel
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
     * whose claim adds the ticket. Public as the game tests' seam onto the owner sets, as on Fabric.
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
