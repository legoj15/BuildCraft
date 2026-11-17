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

import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;

import buildcraft.platform.event.LifecycleHooks;

/^*
 * {@link LifecycleHooks} on MinecraftForge (not NeoForge). The setup and load-complete hooks are listeners on
 * BuildCraft's mod bus ({@link ForgeModBus}), added at the call, so they run in the order they were registered;
 * the {@code OnMain} ones are the event's {@code enqueueWork}. The server and save hooks listen on the game bus.
 *
 * <p><b>Buses.</b> Every listener goes through {@link ForgeBus}, which hides the two Forge bus generations (EventBus 7
 * on 26.x, EventBus 6 on 1.21.1): the mod-lifecycle events on the mod bus, the server and save events on the game bus.
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

    @Override
    public void onCommonSetup(Runnable action) {
        ForgeBus.listenMod(FMLCommonSetupEvent.class, event -> action.run());
    }

    @Override
    public void onCommonSetupOnMain(Runnable action) {
        ForgeBus.listenMod(FMLCommonSetupEvent.class, event -> {
            event.enqueueWork(action);
        });
    }

    @Override
    public void onLoadCompleteOnMain(Runnable action) {
        ForgeBus.listenMod(FMLLoadCompleteEvent.class, event -> {
            event.enqueueWork(action);
        });
    }

    /^* Nothing arrives late: see the class documentation. *^/
    @Override
    public void onLateBlock(Consumer<Block> listener) {}

    @Override
    public void onServerAboutToStart(Consumer<MinecraftServer> listener) {
        ForgeBus.listen(ServerAboutToStartEvent.class, event -> listener.accept(event.getServer()));
    }

    @Override
    public void onServerStopped(Consumer<MinecraftServer> listener) {
        ForgeBus.listen(ServerStoppedEvent.class, event -> listener.accept(event.getServer()));
    }

    @Override
    public void onLevelSave(Consumer<ServerLevel> listener) {
        ForgeBus.listen(LevelEvent.Save.class, event -> {
            if (event.getLevel() instanceof ServerLevel level) {
                listener.accept(level);
            }
        });
    }
}
*///?}
