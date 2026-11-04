/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric;

//? if fabric {
/*import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.server.MinecraftServer;

import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModOrigin;

import buildcraft.platform.LoaderServices;

/^*
 * {@link LoaderServices} on Fabric: Fabric Loader's environment, mod list and paths, and Fabric API's server
 * lifecycle events. Everything but the current server is read from {@link FabricLoader} on every call.
 *
 * <p><b>Current server.</b> Fabric has no static "current server" accessor, so this class keeps the one handed to
 * {@link ServerLifecycleEvents#SERVER_STARTING} until {@link ServerLifecycleEvents#SERVER_STOPPED} fires for it. The
 * listeners are registered once, idempotently, by {@link #registerLifecycleListeners()}, which the constructor calls,
 * so the first {@code Platform.loader()} registers them. That is lazy, and a server that started before then is
 * missed: once BuildCraft has a Fabric mod initializer it must call {@link #registerLifecycleListeners()} (or touch
 * {@code Platform.loader()}) from {@code onInitialize}, which runs before any server can start.
 *
 * <p><b>Mod order.</b> {@link #modIds()} is sorted by mod id. Fabric Loader's own {@code getAllMods()} order is the
 * resolver's id order in production, but in a development environment it is shuffled on every launch (on purpose,
 * to flush out order reliance, unless {@code -Dfabric.debug.disableModShuffle} is set), so sorting is what makes it
 * stable. NeoForge's {@code ModList} order is different again: dependencies first, ties in discovery order.
 *
 * <p><b>Aliases.</b> Fabric resolves a mod's {@code provides} aliases too, so {@link #isModLoaded} and
 * {@link #modVersion} answer for an alias as for the mod that provides it; NeoForge matches the mod id alone.
 *^/
public final class FabricLoaderServices implements LoaderServices {
    private static final AtomicBoolean LISTENING = new AtomicBoolean();
    private static final AtomicReference<MinecraftServer> CURRENT_SERVER = new AtomicReference<>();

    public FabricLoaderServices() {
        registerLifecycleListeners();
    }

    /^*
     * Registers the server lifecycle listeners that track {@link #currentServer()}. Safe to call any number of
     * times; only the first call registers. On Fabric, registration must happen from the mod initializer once one
     * exists (see the class documentation).
     *^/
    public static void registerLifecycleListeners() {
        if (!LISTENING.compareAndSet(false, true)) {
            return;
        }
        ServerLifecycleEvents.SERVER_STARTING.register(CURRENT_SERVER::set);
        // Clear only the server that stopped, in case a second one has already started.
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> CURRENT_SERVER.compareAndSet(server, null));
    }

    @Override
    public boolean isPhysicalClient() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public List<String> modIds() {
        List<String> ids = new ArrayList<>();
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            ids.add(mod.getMetadata().getId());
        }
        ids.sort(null);
        return List.copyOf(ids);
    }

    @Override
    public Optional<String> modVersion(String modId) {
        return FabricLoader.getInstance().getModContainer(modId)
            .map(mod -> mod.getMetadata().getVersion().getFriendlyString());
    }

    @Override
    public List<Path> modSourcePaths(String modId) {
        Optional<ModContainer> mod = FabricLoader.getInstance().getModContainer(modId);
        if (mod.isEmpty()) {
            return List.of();
        }
        ModOrigin origin = mod.get().getOrigin();
        // Only a PATH origin has files of its own. A NESTED (jar-in-jar) or UNKNOWN origin throws on getPaths().
        if (origin.getKind() != ModOrigin.Kind.PATH) {
            return List.of();
        }
        return List.copyOf(origin.getPaths());
    }

    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public Path gameDir() {
        return FabricLoader.getInstance().getGameDir();
    }

    @Override
    public MinecraftServer currentServer() {
        return CURRENT_SERVER.get();
    }
}
*///?}
