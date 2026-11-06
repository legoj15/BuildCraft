/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.event;

import java.util.function.Consumer;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;

/**
 * The mod-loading and server lifecycle points BuildCraft hooks. The setup and load-complete hooks must be registered
 * while the mod is being constructed (BuildCraft's init); actions registered on one hook run in registration order.
 */
public interface LifecycleHooks {

    /**
     * Runs {@code action} once at common setup, after every registry is filled and the configs are loaded. It may
     * run off the main thread, in parallel with other mods' setup; use {@link #onCommonSetupOnMain} for work that
     * touches shared game state.
     */
    void onCommonSetup(Runnable action);

    /** Runs {@code action} once at common setup, on the main thread, after the parallel setup work. */
    void onCommonSetupOnMain(Runnable action);

    /** Runs {@code action} once loading is complete (every mod has set up), on the main thread. */
    void onLoadCompleteOnMain(Runnable action);

    /**
     * Runs {@code listener} for each block registered after the common setup actions ran, so a scan of the block
     * registry made by one of them can take in the stragglers. NeoForge fills every registry before common setup, so
     * the listener never runs there; Fabric runs the setup actions inside BuildCraft's own mod initializer, and a
     * mod initialised after it registers its blocks later. Register the listener from a common setup action, after
     * the scan.
     */
    void onLateBlock(Consumer<Block> listener);

    /** Runs {@code listener} each time a server is about to start, before its levels load. */
    void onServerAboutToStart(Consumer<MinecraftServer> listener);

    /** Runs {@code listener} each time a server has stopped: its levels are saved and unloaded. */
    void onServerStopped(Consumer<MinecraftServer> listener);

    /**
     * Runs {@code listener} each time a server level saves, after the save of the level has been issued. The write
     * itself may still be pending (NeoForge on 1.21.1 posts the event before it joins the IO worker), so a listener
     * that must know its data reached the disk reads the file back; it never assumes the bytes are there.
     */
    void onLevelSave(Consumer<ServerLevel> listener);
}
