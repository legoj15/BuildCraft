/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.marker;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

import buildcraft.platform.client.ClientHooks;
import buildcraft.platform.event.GameHooks;

/**
 * Wires {@link MarkerCache#onWorldUnload} and {@link MarkerCache#onPlayerJoinWorld}, the two halves of 1.12.2's
 * marker lifecycle ({@code BCLibEventDist}) that the port had left without a caller. They only make sense
 * together:
 * <ul>
 * <li><b>Unload</b> drops a level's sub-cache, on BOTH sides. A {@link MarkerCache} singleton keeps a sub-cache
 *     per dimension key and per side. On the server it holds the loaded marker block entities (and through them
 *     their level), so without the drop a stopped server's world stayed reachable from the mod. On the client
 *     it holds the markers and connections the server has sent, and a dimension key such as
 *     {@code minecraft:overworld} is the same key in every world a client ever joins, so the NEXT world to use
 *     it was handed the old world's markers and connections instead of its own. NeoForge posts this event for
 *     a client level when it is replaced ({@code Minecraft.setLevel}: dimension change, new connection) and when
 *     the connection closes ({@code Minecraft.disconnect}), which covers every way a client leaves a world
 *     (BuildCraft's Fabric hooks post it at the same three points). The client also drops all its sub-caches on
 *     the client level-unload hook ({@link #registerClientHooks}: a level change or a disconnect).
 *     Nothing here touches a client-only class; on a dedicated server a client level never exists.</li>
 * <li><b>Join</b> refills what the unload dropped: the live {@code MessageMarker} updates only reach players
 *     already in the dimension, so a player entering a level (login, dimension change, respawn) is sent every
 *     marker and connection of it. Without this a client that reset its cache would never see a connection
 *     that was made before it arrived, and neither would a fresh client on a dedicated server.</li>
 * </ul>
 */
public final class MarkerCacheEvents {

    private MarkerCacheEvents() {
    }

    /** Registers the unload and join listeners. Called once from BCCore. */
    public static void registerHooks(GameHooks hooks) {
        hooks.onLevelUnload(MarkerCacheEvents::onLevelUnload);
        hooks.onEntityJoinLevel(MarkerCacheEvents::onEntityJoinLevel);
    }

    /**
     * Registers the client half of the unload: every client sub-cache is dropped when the client's level closes.
     * Called once from BCCoreClient, on a physical client only. On NeoForge {@link #onLevelUnload} has already dropped
     * the closing level's own sub-cache (the client holds one level, so no other client table has anything in it), and
     * so has Fabric's; this is the reset for any client table the level's own unload missed.
     */
    public static void registerClientHooks(ClientHooks hooks) {
        hooks.onClientLevelUnload(MarkerCache::onClientWorldClosed);
    }

    public static void onLevelUnload(LevelAccessor unloaded) {
        if (unloaded instanceof Level level) {
            MarkerCache.onWorldUnload(level);
        }
    }

    public static void onEntityJoinLevel(Entity entity, ServerLevel level, boolean loadedFromDisk) {
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }
        // One server tick later, like 1.12.2: the event fires from inside the join/respawn/teleport handling,
        // before that has finished sending the player their new level.
        server.execute(() -> {
            if (!player.hasDisconnected()) {
                MarkerCache.onPlayerJoinWorld(player);
            }
        });
    }
}
