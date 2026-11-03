/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import net.minecraft.server.MinecraftServer;

/**
 * What BuildCraft asks the mod loader about itself: which side this JVM is, which mods are installed and where they
 * live, where the game and config directories are, and which server is running.
 *
 * <p>This is a contract (see the package documentation): JDK and vanilla types only. Implementations read the
 * loader's state on every call, so a value that changes (the current server) is never stale and nothing loader-side
 * is touched before it is asked for.
 */
public interface LoaderServices {

    /** Whether this JVM is the physical client (it has a window and its own integrated server), as opposed to a
     *  dedicated server. */
    boolean isPhysicalClient();

    /** Whether a mod with this id is installed. */
    boolean isModLoaded(String modId);

    /** The id of every installed mod, in the loader's own order. */
    List<String> modIds();

    /** The version string of the installed mod with this id, or empty if there is none. */
    Optional<String> modVersion(String modId);

    /** Where the installed mod with this id was loaded from (its jar, or its class directory in a development
     *  environment): empty if there is no such mod, otherwise one entry per root the loader reports. */
    List<Path> modSourcePaths(String modId);

    /** The directory the loader keeps mod configuration in. */
    Path configDir();

    /** The game directory: the root that holds the saves, the logs and the config directory. */
    Path gameDir();

    /** The running server (dedicated, or the integrated one on a client), or {@code null} if none is running. */
    MinecraftServer currentServer();
}
