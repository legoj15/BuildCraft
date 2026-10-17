/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.marker;

import net.minecraft.world.level.Level;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * Wires {@link MarkerCache#onWorldUnload}, which 1.12.2 hooked to its world-unload event and the port had left
 * without a caller. Each {@link MarkerCache} singleton keeps a sub-cache per dimension holding the loaded marker
 * block entities (and through them their level), so without this a stopped server's world stayed reachable from
 * the mod, and the NEXT world to use the same dimension key was handed the old world's sub-cache — its marker
 * positions, connections and saved-data link — instead of building its own.
 *
 * <p>Server levels only. Dropping the CLIENT sub-cache on unload is the other half of the 1.12.2 hook, but it
 * is only safe together with that version's join-time resync ({@code MarkerCache.onPlayerJoinWorld}, also
 * unwired here), so the client side is left as it was.
 */
@EventBusSubscriber(modid = "buildcraftunofficial")
public final class MarkerCacheEvents {

    private MarkerCacheEvents() {
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level && !level.isClientSide()) {
            MarkerCache.onWorldUnload(level);
        }
    }
}
