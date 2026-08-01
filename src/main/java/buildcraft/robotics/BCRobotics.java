/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.robotics;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import buildcraft.api.boards.RedstoneBoardRegistry;
import buildcraft.api.robots.RobotManager;
import buildcraft.core.BCCore;
import buildcraft.transport.BCTransportCreativeTabs;

/**
 * BuildCraft Robotics initializer. No longer a separate @Mod — called from BCCore.
 */
public class BCRobotics {
    public static final String MODID = BCCore.MODID;
    private static final Logger LOGGER = LoggerFactory.getLogger(BCRobotics.class);

    public static void init(IEventBus modEventBus) {
        // Un-orphan the robotics API: wire the registry provider and board registry the rest of the
        // robotics system (DockingStation, boards) reaches through these statics. Harmless without any
        // robots present, and required the moment a docking station or board is created.
        RobotManager.registryProvider = new RobotRegistryProvider();
        RedstoneBoardRegistry.instance = new ImplRedstoneBoardRegistry();
        // Miss this and RobotRegistry.writeToNbt silently drops every pipe-hosted station on save
        // (unregistered docking station type -> logged warning, station skipped).
        RobotManager.registerDockingStation(DockingStationPipe.class, "pipe");

        // Initialize pluggable definitions BEFORE items register (BCTransport.init already ran, so
        // PipeApi.pluggableRegistry is set — see BCRoboticsPlugs).
        BCRoboticsPlugs.preInit();

        // Register all deferred registries
        BCRoboticsBlocks.init(modEventBus);
        BCRoboticsItems.init(modEventBus);
        BCRoboticsBlockEntities.init(modEventBus);
        BCRoboticsEntities.init(modEventBus);
        BCRoboticsMenuTypes.init(modEventBus);

        // Register client-side extensions on the mod event bus
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            buildcraft.robotics.client.BCRoboticsClient.initClient(modEventBus);
        }

        // Register creative tab
        modEventBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            addCreativeTabItems(event);
        });

        LOGGER.info("BuildCraft Robotics initialized");
    }

    private static void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        // BuildCreativeModeTabContentsEvent fires once PER TAB — without this guard the Robot Station
        // is injected into every tab in the game, vanilla ones included. It's a pipe pluggable, so it
        // belongs in the Pluggables tab beside the blocker and power adaptor.
        if (event.getTabKey() == BCTransportCreativeTabs.PLUGS_TAB_KEY) {
            event.accept(BCRoboticsItems.ROBOT_STATION.get());
        }
    }
}
