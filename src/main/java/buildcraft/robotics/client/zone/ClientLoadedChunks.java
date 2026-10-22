/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.client.zone;

import javax.annotation.Nullable;

import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.world.level.Level;

/**
 * The area of chunks a client level can hold. Vanilla keeps no public record of it: {@link ClientChunkCache} stores its
 * chunks in a square around the view centre the server last sent, and answers {@code null} for any chunk outside it,
 * but exposes neither the centre nor the radius. The access transformer opens the three numbers this reads, and this
 * class is the only place that touches them, so a change to vanilla's storage breaks the build here and nowhere else.
 *
 * <p>Kept apart from {@link ZonePlannerMapDataClient} so that class, which a dedicated-server test also loads, never
 * names a client-only type itself.
 */
final class ClientLoadedChunks {

    private ClientLoadedChunks() {}

    /**
     * {@code {minChunkX, minChunkZ, maxChunkX, maxChunkZ}} (inclusive) of the square of chunks {@code level} could be
     * holding right now, or {@code null} if its chunk source is not a client's. The square is a little wider than the
     * chunks actually sent (vanilla pads the server's view distance), so it never leaves out a loaded chunk.
     */
    @Nullable
    static int[] bounds(Level level) {
        if (!(level.getChunkSource() instanceof ClientChunkCache cache)) {
            return null;
        }
        ClientChunkCache.Storage storage = cache.storage;
        int radius = storage.chunkRadius;
        int centreX = storage.viewCenterX;
        int centreZ = storage.viewCenterZ;
        return new int[] {centreX - radius, centreZ - radius, centreX + radius, centreZ + radius};
    }
}
