/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge;

//? if forge {
/*import java.util.function.Consumer;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;

import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;

import buildcraft.platform.event.LifecycleHooks;

/^*
 * {@link LifecycleHooks} on MinecraftForge (not NeoForge). The setup and load-complete hooks are listeners on
 * BuildCraft's mod bus group ({@link ForgeModBus}), added at the call, so they run in the order they were registered;
 * the {@code OnMain} ones are the event's {@code enqueueWork}. The server and save hooks listen on the events' own
 * static buses.
 *
 * <p><b>Buses.</b> EventBus 7 has no bus object to hand around: a mod-lifecycle event is reached through
 * {@code FMLCommonSetupEvent.getBus(group)}, a game event through its static {@code BUS} field. NeoForge's listeners
 * on {@code NeoForge.EVENT_BUS} become {@code SomeEvent.BUS.addListener}, and {@code MinecraftForge.EVENT_BUS} (which
 * Forge 26.3 deprecates for removal) is never named.
 *
 * <p><b>Late blocks.</b> Nothing arrives late, as on NeoForge: FML runs the registry events of every mod before any
 * mod's common setup (the javadoc of {@code FMLCommonSetupEvent} says it is called after {@code RegisterEvent}), so a
 * setup action's scan of the block registry already sees every mod's blocks.
 *
 * <p><b>Level save.</b> Forge posts {@code LevelEvent.Save} at the end of {@code ServerLevel.save}, after the level's
 * entities and chunks have been handed to their writers ({@code ServerLevel.java.patch}); a listener that must know the
 * bytes are on disk still reads the file back, as the contract says.
 *^/
public final class ForgeLifecycleHooks implements LifecycleHooks {

    private static BusGroup modBus() {
        return ForgeModBus.group();
    }

    @Override
    public void onCommonSetup(Runnable action) {
        FMLCommonSetupEvent.getBus(modBus()).addListener(event -> action.run());
    }

    @Override
    public void onCommonSetupOnMain(Runnable action) {
        FMLCommonSetupEvent.getBus(modBus()).addListener(event -> {
            event.enqueueWork(action);
        });
    }

    @Override
    public void onLoadCompleteOnMain(Runnable action) {
        FMLLoadCompleteEvent.getBus(modBus()).addListener(event -> {
            event.enqueueWork(action);
        });
    }

    /^* Nothing arrives late: see the class documentation. *^/
    @Override
    public void onLateBlock(Consumer<Block> listener) {}

    @Override
    public void onServerAboutToStart(Consumer<MinecraftServer> listener) {
        ServerAboutToStartEvent.BUS.addListener(event -> listener.accept(event.getServer()));
    }

    @Override
    public void onServerStopped(Consumer<MinecraftServer> listener) {
        ServerStoppedEvent.BUS.addListener(event -> listener.accept(event.getServer()));
    }

    @Override
    public void onLevelSave(Consumer<ServerLevel> listener) {
        LevelEvent.Save.BUS.addListener(event -> {
            if (event.getLevel() instanceof ServerLevel level) {
                listener.accept(level);
            }
        });
    }
}
*///?}
