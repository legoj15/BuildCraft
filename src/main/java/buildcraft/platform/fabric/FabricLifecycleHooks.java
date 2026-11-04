/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric;

//? if fabric {
/*import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import buildcraft.platform.event.LifecycleHooks;

/^*
 * {@link LifecycleHooks} on Fabric. Fabric has no setup phases after mod initialisation: the setup and load-complete
 * actions are queued, in registration order, and {@link #finishInit} runs them (common setup, then its main-thread
 * work, then load complete), all on the thread that initialises the mod. The Fabric entrypoint calls it once
 * BuildCraft's init has returned; until a Fabric entrypoint exists nothing does, which is harmless because this node
 * runs nothing.
 *
 * <p>Fabric's {@code SERVER_STARTING} runs where NeoForge's about-to-start does, before the levels load. A level save is
 * Fabric's {@code AFTER_SAVE} of the whole server, told to each of its levels: there is no per-level save event.
 *^/
public final class FabricLifecycleHooks implements LifecycleHooks {

    private final List<Runnable> setup = new ArrayList<>();
    private final List<Runnable> setupOnMain = new ArrayList<>();
    private final List<Runnable> loadComplete = new ArrayList<>();

    @Override
    public void onCommonSetup(Runnable action) {
        setup.add(action);
    }

    @Override
    public void onCommonSetupOnMain(Runnable action) {
        setupOnMain.add(action);
    }

    @Override
    public void onLoadCompleteOnMain(Runnable action) {
        loadComplete.add(action);
    }

    /^* Runs the queued setup and load-complete actions, once; the Fabric entrypoint calls it after BuildCraft's init. *^/
    public void finishInit() {
        for (List<Runnable> phase : List.of(setup, setupOnMain, loadComplete)) {
            List<Runnable> actions = List.copyOf(phase);
            phase.clear();
            actions.forEach(Runnable::run);
        }
    }

    @Override
    public void onServerAboutToStart(Consumer<MinecraftServer> listener) {
        ServerLifecycleEvents.SERVER_STARTING.register(listener::accept);
    }

    @Override
    public void onServerStopped(Consumer<MinecraftServer> listener) {
        ServerLifecycleEvents.SERVER_STOPPED.register(listener::accept);
    }

    @Override
    public void onLevelSave(Consumer<ServerLevel> listener) {
        ServerLifecycleEvents.AFTER_SAVE.register((server, flush, force) -> {
            for (ServerLevel level : server.getAllLevels()) {
                listener.accept(level);
            }
        });
    }
}
*///?}
