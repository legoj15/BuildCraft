/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge;

//? if forge {
/*import java.util.function.Supplier;

import net.minecraft.server.level.ServerLevel;

/^*
 * Which level the saved-data storage at work on this thread belongs to: NeoForge's level-aware saved data, which Forge
 * does not patch, the way {@code FabricSavedDataLevels} provides it on Fabric. The storage calls that build, read or
 * write data are meant to run inside {@link #with}, so a {@code levelSavedDataType}'s factory and codec see the level
 * through {@link #current} (see {@link ForgeVanillaExtensions}). Nothing calls {@link #with} yet: it needs the Forge
 * mixins on the storage and on the chunk source, so until then every factory and codec is handed null, the value the
 * server-wide storage has on NeoForge.
 *^/
public final class ForgeSavedDataLevels {
    private ForgeSavedDataLevels() {}

    private static final ThreadLocal<ServerLevel> CURRENT = new ThreadLocal<>();

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
