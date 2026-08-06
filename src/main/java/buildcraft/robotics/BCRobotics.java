/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.robotics;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import buildcraft.api.boards.RedstoneBoardRegistry;
import buildcraft.api.robots.RobotManager;
import buildcraft.core.BCCore;
import buildcraft.robotics.ai.AIRobotFetchItem;
import buildcraft.robotics.ai.AIRobotGoto;
import buildcraft.robotics.ai.AIRobotGotoBlock;
import buildcraft.robotics.ai.AIRobotGotoSleep;
import buildcraft.robotics.ai.AIRobotGotoStation;
import buildcraft.robotics.ai.AIRobotGotoStationAndLoad;
import buildcraft.robotics.ai.AIRobotGotoStationAndUnload;
import buildcraft.robotics.ai.AIRobotGotoStationToLoad;
import buildcraft.robotics.ai.AIRobotGotoStationToUnload;
import buildcraft.robotics.ai.AIRobotLoad;
import buildcraft.robotics.ai.AIRobotMain;
import buildcraft.robotics.ai.AIRobotRecharge;
import buildcraft.robotics.ai.AIRobotSearchAndGotoStation;
import buildcraft.robotics.ai.AIRobotSearchStation;
import buildcraft.robotics.ai.AIRobotShutdown;
import buildcraft.robotics.ai.AIRobotSleep;
import buildcraft.robotics.ai.AIRobotStraightMoveTo;
import buildcraft.robotics.ai.AIRobotUnload;
import buildcraft.robotics.boards.BoardRobotCarrier;
import buildcraft.robotics.boards.BoardRobotCarrierNBT;
import buildcraft.robotics.boards.BoardRobotEmpty;
import buildcraft.robotics.boards.BoardRobotPicker;
import buildcraft.robotics.boards.BoardRobotPickerNBT;
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

        // Ph4: the AI tree + the first two real boards. Each AI (and board, which is itself an AIRobot) is
        // registered by the name its NBT saves, with the 7.1.x legacy class name for old-save migration.
        registerAIsAndBoards();

        // The picker's shared targettedItems set is cleared on every server start — the 7.1.x wiring
        // (BuildCraftRobotics called BoardRobotPicker.onServerStart from its server-start handler) that the
        // port originally dropped. Without it, UUIDs leaked by an interrupted fetch (chunk unload mid-fetch
        // drops the FetchItem without end()) would blacklist their items for the whole JVM session.
        NeoForge.EVENT_BUS.addListener((ServerAboutToStartEvent event) -> BoardRobotPicker.onServerStart());

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

    /** Registers the Ph4 AI tree and the picker/carrier boards. Board costs are 7.1.x's 8000 RF each (the two
     *  green boards), converted to micro-MJ at the canonical 1 MJ = 10 RF bridge (8000 * 100_000); the empty
     *  board was already seeded by {@code ImplRedstoneBoardRegistry}. */
    private static void registerAIsAndBoards() {
        RobotManager.registerAIRobot(AIRobotMain.class, "aiRobotMain", "buildcraft.core.robots.AIRobotMain");
        RobotManager.registerAIRobot(AIRobotRecharge.class, "aiRobotRecharge", "buildcraft.core.robots.AIRobotRecharge");
        RobotManager.registerAIRobot(AIRobotSleep.class, "aiRobotSleep", "buildcraft.core.robots.AIRobotSleep");
        RobotManager.registerAIRobot(AIRobotShutdown.class, "aiRobotShutdown");
        RobotManager.registerAIRobot(AIRobotGoto.class, "aiRobotGoto", "buildcraft.core.robots.AIRobotGoto");
        RobotManager.registerAIRobot(AIRobotStraightMoveTo.class, "aiRobotStraightMoveTo",
                "buildcraft.core.robots.AIRobotStraightMoveTo");
        RobotManager.registerAIRobot(AIRobotGotoBlock.class, "aiRobotGotoBlock", "buildcraft.core.robots.AIRobotGotoBlock");
        RobotManager.registerAIRobot(AIRobotGotoStation.class, "aiRobotGotoStation",
                "buildcraft.core.robots.AIRobotGotoStation");
        RobotManager.registerAIRobot(AIRobotGotoSleep.class, "aiRobotGotoSleep", "buildcraft.core.robots.AIRobotGotoSleep");
        RobotManager.registerAIRobot(AIRobotSearchStation.class, "aiRobotSearchStation",
                "buildcraft.core.robots.AIRobotSearchStation");
        RobotManager.registerAIRobot(AIRobotSearchAndGotoStation.class, "aiRobotSearchAndGotoStation",
                "buildcraft.core.robots.AIRobotSearchAndGotoStation");
        RobotManager.registerAIRobot(AIRobotGotoStationToLoad.class, "aiRobotGotoStationToLoad",
                "buildcraft.core.robots.AIRobotGotoStationToLoad");
        RobotManager.registerAIRobot(AIRobotGotoStationToUnload.class, "aiRobotGotoStationToUnload",
                "buildcraft.core.robots.AIRobotGotoStationToUnload");
        RobotManager.registerAIRobot(AIRobotGotoStationAndLoad.class, "aiRobotGotoStationAndLoad",
                "buildcraft.core.robots.AIRobotGotoStationAndLoad");
        RobotManager.registerAIRobot(AIRobotGotoStationAndUnload.class, "aiRobotGotoStationAndUnload",
                "buildcraft.core.robots.AIRobotGotoStationAndUnload");
        RobotManager.registerAIRobot(AIRobotLoad.class, "aiRobotLoad", "buildcraft.core.robots.AIRobotLoad");
        RobotManager.registerAIRobot(AIRobotUnload.class, "aiRobotUnload", "buildcraft.core.robots.AIRobotUnload");
        RobotManager.registerAIRobot(AIRobotFetchItem.class, "aiRobotFetchItem", "buildcraft.core.robots.AIRobotFetchItem");

        RobotManager.registerAIRobot(BoardRobotEmpty.class, "boardRobotEmpty");
        RobotManager.registerAIRobot(BoardRobotPicker.class, "boardRobotPicker",
                "buildcraft.core.robots.boards.BoardRobotPicker");
        RobotManager.registerAIRobot(BoardRobotCarrier.class, "boardRobotCarrier",
                "buildcraft.core.robots.boards.BoardRobotCarrier");

        RedstoneBoardRegistry.instance.registerBoardType(BoardRobotPickerNBT.INSTANCE, 800_000_000L);
        RedstoneBoardRegistry.instance.registerBoardType(BoardRobotCarrierNBT.INSTANCE, 800_000_000L);
    }
}
