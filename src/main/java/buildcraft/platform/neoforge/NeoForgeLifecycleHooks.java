/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.neoforge;

//? if neoforge {
import java.util.function.Consumer;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import buildcraft.platform.event.LifecycleHooks;

/**
 * {@link LifecycleHooks} on NeoForge. The setup and load-complete hooks are listeners on BuildCraft's mod bus
 * ({@link NeoForgeModBus}; the calls are made from its init), added at the call, so they run in the order they were
 * registered; the {@code OnMain} ones are the event's {@code enqueueWork}. The server and save hooks are listeners on
 * {@link NeoForge#EVENT_BUS}.
 */
public final class NeoForgeLifecycleHooks implements LifecycleHooks {

    private static IEventBus modBus() {
        return NeoForgeModBus.get();
    }

    @Override
    public void onCommonSetup(Runnable action) {
        modBus().addListener((FMLCommonSetupEvent event) -> action.run());
    }

    @Override
    public void onCommonSetupOnMain(Runnable action) {
        modBus().addListener((FMLCommonSetupEvent event) -> event.enqueueWork(action));
    }

    @Override
    public void onLoadCompleteOnMain(Runnable action) {
        modBus().addListener((FMLLoadCompleteEvent event) -> event.enqueueWork(action));
    }

    @Override
    public void onServerAboutToStart(Consumer<MinecraftServer> listener) {
        NeoForge.EVENT_BUS.addListener((ServerAboutToStartEvent event) -> listener.accept(event.getServer()));
    }

    @Override
    public void onServerStopped(Consumer<MinecraftServer> listener) {
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> listener.accept(event.getServer()));
    }

    @Override
    public void onLevelSave(Consumer<ServerLevel> listener) {
        NeoForge.EVENT_BUS.addListener((LevelEvent.Save event) -> {
            if (event.getLevel() instanceof ServerLevel level) {
                listener.accept(level);
            }
        });
    }
}
//?}
