/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Owner-keyed chunk forcing: a chunk stays loaded while at least one owner holds it. */
public interface ChunkTickets {
    /** Registers the ticket owner with the loader; call once from the mod constructor, before any world loads. */
    void register();

    /** Adds ({@code add} true) or removes the ticket {@code owner} holds on the chunk at {@code chunkX, chunkZ}. */
    void forceChunk(ServerLevel level, BlockPos owner, int chunkX, int chunkZ, boolean add);
}
