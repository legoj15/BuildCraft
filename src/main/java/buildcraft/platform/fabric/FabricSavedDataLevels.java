/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric;

//? if fabric {
/*import java.util.function.Supplier;

import net.minecraft.server.level.ServerLevel;

/^*
 * Which level the saved-data storage at work on this thread belongs to, NeoForge's level-aware saved data on Fabric:
 * {@code ServerChunkCacheMixin} tells each dimension's storage its level, and {@code SavedDataStorageMixin} wraps the
 * storage calls that build, read or write data in {@link #with}, so a {@code levelSavedDataType}'s factory and codec
 * see it ({@link #current}). The server-wide storage has no level, so its data sees null, as on NeoForge.
 *^/
public final class FabricSavedDataLevels {
    private FabricSavedDataLevels() {}

    private static final ThreadLocal<ServerLevel> CURRENT = new ThreadLocal<>();

    /^* A saved-data storage that knows the level it belongs to (duck interface of {@code SavedDataStorageMixin}). ^/
    public interface LevelAwareStorage {
        void buildcraft$setLevel(ServerLevel level);

        ServerLevel buildcraft$level();
    }

    /^* The level of the storage call running on this thread, or null. ^/
    public static ServerLevel current() {
        return CURRENT.get();
    }

    /^* Runs {@code call} with {@code level} as {@link #current}, restoring the outer one after. ^/
    public static <T> T with(ServerLevel level, Supplier<T> call) {
        ServerLevel outer = CURRENT.get();
        CURRENT.set(level);
        try {
            return call.get();
        } finally {
            CURRENT.set(outer);
        }
    }
}
*///?}
