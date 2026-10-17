/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.marker;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

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
 *     the connection closes ({@code Minecraft.disconnect}), which covers every way a client leaves a world.
 *     Nothing here touches a client-only class; on a dedicated server a client level never exists.</li>
 * <li><b>Join</b> refills what the unload dropped: the live {@code MessageMarker} updates only reach players
 *     already in the dimension, so a player entering a level (login, dimension change, respawn) is sent every
 *     marker and connection of it. Without this a client that reset its cache would never see a connection
 *     that was made before it arrived, and neither would a fresh client on a dedicated server.</li>
 * </ul>
 */
@EventBusSubscriber(modid = "buildcraftunofficial")
public final class MarkerCacheEvents {

    private MarkerCacheEvents() {
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level) {
            MarkerCache.onWorldUnload(level);
        }
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        Level level = event.getLevel();
        if (level.isClientSide() || !(event.getEntity() instanceof ServerPlayer player)) {
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
