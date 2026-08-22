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
import buildcraft.robotics.ai.AIRobotAttack;
import buildcraft.robotics.ai.AIRobotBreak;
import buildcraft.robotics.ai.AIRobotFetchAndEquipItemStack;
import buildcraft.robotics.ai.AIRobotFetchItem;
import buildcraft.robotics.ai.AIRobotGoto;
import buildcraft.robotics.ai.AIRobotGotoBlock;
import buildcraft.robotics.ai.AIRobotGotoSleep;
import buildcraft.robotics.ai.AIRobotGotoStation;
import buildcraft.robotics.ai.AIRobotGotoStationAndLoad;
import buildcraft.robotics.ai.AIRobotGotoStationAndLoadFluids;
import buildcraft.robotics.ai.AIRobotGotoStationAndUnload;
import buildcraft.robotics.ai.AIRobotGotoStationAndUnloadFluids;
import buildcraft.robotics.ai.AIRobotGotoStationToLoad;
import buildcraft.robotics.ai.AIRobotGotoStationToLoadFluids;
import buildcraft.robotics.ai.AIRobotGotoStationToUnload;
import buildcraft.robotics.ai.AIRobotGotoStationToUnloadFluids;
import buildcraft.robotics.ai.AIRobotHarvest;
import buildcraft.robotics.ai.AIRobotLoad;
import buildcraft.robotics.ai.AIRobotLoadFluids;
import buildcraft.robotics.ai.AIRobotMain;
import buildcraft.robotics.ai.AIRobotPlant;
import buildcraft.robotics.ai.AIRobotPumpBlock;
import buildcraft.robotics.ai.AIRobotRecharge;
import buildcraft.robotics.ai.AIRobotSearchAndGotoBlock;
import buildcraft.robotics.ai.AIRobotSearchAndGotoStation;
import buildcraft.robotics.ai.AIRobotSearchBlock;
import buildcraft.robotics.ai.AIRobotSearchEntity;
import buildcraft.robotics.ai.AIRobotSearchStation;
import buildcraft.robotics.ai.AIRobotShutdown;
import buildcraft.robotics.ai.AIRobotSleep;
import buildcraft.robotics.ai.AIRobotStraightMoveTo;
import buildcraft.robotics.ai.AIRobotUnload;
import buildcraft.robotics.ai.AIRobotUnloadFluids;
import buildcraft.robotics.ai.AIRobotUseToolOnBlock;
import buildcraft.robotics.boards.BoardRobotButcher;
import buildcraft.robotics.boards.BoardRobotButcherNBT;
import buildcraft.robotics.boards.BoardRobotCarrier;
import buildcraft.robotics.boards.BoardRobotCarrierNBT;
import buildcraft.robotics.boards.BoardRobotEmpty;
import buildcraft.robotics.boards.BoardRobotFarmer;
import buildcraft.robotics.boards.BoardRobotFarmerNBT;
import buildcraft.robotics.boards.BoardRobotHarvester;
import buildcraft.robotics.boards.BoardRobotHarvesterNBT;
import buildcraft.robotics.boards.BoardRobotKnight;
import buildcraft.robotics.boards.BoardRobotKnightNBT;
import buildcraft.robotics.boards.BoardRobotLumberjack;
import buildcraft.robotics.boards.BoardRobotLumberjackNBT;
import buildcraft.robotics.boards.BoardRobotMiner;
import buildcraft.robotics.boards.BoardRobotMinerNBT;
import buildcraft.robotics.boards.BoardRobotPicker;
import buildcraft.robotics.boards.BoardRobotPickerNBT;
import buildcraft.robotics.boards.BoardRobotPlanter;
import buildcraft.robotics.boards.BoardRobotPlanterNBT;
import buildcraft.robotics.boards.BoardRobotPump;
import buildcraft.robotics.boards.BoardRobotPumpNBT;
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

        // Ph6: the robot/station statement catalog (triggers, actions, parameters, providers). The
        // statements self-register into StatementManager; this wires the providers into the gate UI.
        BCRoboticsStatements.preInit();

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

    /** Registers the full AI tree (Ph4 core + Ph5 board catalog) and every robot board class (the Ph5 work
     *  boards plus Ph4's empty/picker/carrier). Every name and legacy
     *  class name mirrors 7.1.x's own {@code BuildCraftRobotics} registration list (the legacy names are the
     *  6.x-era {@code buildcraft.core.robots.*} classes that old-save NBT still records; the three AIs 7.1.x
     *  itself registered without a legacy name — Harvest, Plant, SearchAndGotoBlock — postdate 6.x). Board
     *  costs are 7.1.x's, converted to micro-MJ at the canonical 1 RF = 100_000 micro-MJ bridge: green boards
     *  8000 RF, blue boards 32000 RF, red (knight) 128000 RF. The empty board was already seeded by
     *  {@code ImplRedstoneBoardRegistry}. */
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

        // Ph5 board-catalog AIs. 7.1.x registered Harvest, Plant, and SearchAndGotoBlock without a legacy
        // name (post-6.x additions); SearchAndGotoBlock's 7.1.x name keeps its "GoToBlock" capital-T spelling.
        RobotManager.registerAIRobot(AIRobotSearchBlock.class, "aiRobotSearchBlock",
                "buildcraft.core.robots.AIRobotSearchBlock");
        RobotManager.registerAIRobot(AIRobotSearchAndGotoBlock.class, "aiRobotSearchAndGoToBlock");
        RobotManager.registerAIRobot(AIRobotSearchEntity.class, "aiRobotSearchEntity",
                "buildcraft.core.robots.AIRobotSearchEntity");
        RobotManager.registerAIRobot(AIRobotBreak.class, "aiRobotBreak", "buildcraft.core.robots.AIRobotBreak");
        RobotManager.registerAIRobot(AIRobotAttack.class, "aiRobotAttack", "buildcraft.core.robots.AIRobotAttack");
        RobotManager.registerAIRobot(AIRobotFetchAndEquipItemStack.class, "aiRobotFetchAndEquipItemStack",
                "buildcraft.core.robots.AIRobotFetchAndEquipItemStack");
        RobotManager.registerAIRobot(AIRobotUseToolOnBlock.class, "aiRobotUseToolOnBlock",
                "buildcraft.core.robots.AIRobotUseToolOnBlock");
        RobotManager.registerAIRobot(AIRobotHarvest.class, "aiRobotHarvest");
        RobotManager.registerAIRobot(AIRobotPlant.class, "aiRobotPlant");
        RobotManager.registerAIRobot(AIRobotPumpBlock.class, "aiRobotPumpBlock",
                "buildcraft.core.robots.AIRobotPumpBlock");
        RobotManager.registerAIRobot(AIRobotLoadFluids.class, "aiRobotLoadFluids",
                "buildcraft.core.robots.AIRobotLoadFluids");
        RobotManager.registerAIRobot(AIRobotUnloadFluids.class, "aiRobotUnloadFluids",
                "buildcraft.core.robots.AIRobotUnloadFluids");
        RobotManager.registerAIRobot(AIRobotGotoStationToLoadFluids.class, "aiRobotGotoStationToLoadFluids",
                "buildcraft.core.robots.AIRobotGotoStationToLoadFluids");
        RobotManager.registerAIRobot(AIRobotGotoStationToUnloadFluids.class, "aiRobotGotoStationToUnloadFluids",
                "buildcraft.core.robots.AIRobotGotoStationToUnloadFluids");
        RobotManager.registerAIRobot(AIRobotGotoStationAndLoadFluids.class, "aiRobotGotoStationAndLoadFluids",
                "buildcraft.core.robots.AIRobotGotoStationAndLoadFluids");
        RobotManager.registerAIRobot(AIRobotGotoStationAndUnloadFluids.class, "aiRobotGotoStationAndUnloadFluids",
                "buildcraft.core.robots.AIRobotGotoStationAndUnloadFluids");

        RobotManager.registerAIRobot(BoardRobotEmpty.class, "boardRobotEmpty");
        RobotManager.registerAIRobot(BoardRobotPicker.class, "boardRobotPicker",
                "buildcraft.core.robots.boards.BoardRobotPicker");
        RobotManager.registerAIRobot(BoardRobotCarrier.class, "boardRobotCarrier",
                "buildcraft.core.robots.boards.BoardRobotCarrier");

        // Ph5 work boards (the two abstract 7.1.x bases, GenericSearchBlock/GenericBreakBlock, register
        // nothing — they are internal composition only).
        RobotManager.registerAIRobot(BoardRobotLumberjack.class, "boardRobotLumberjack",
                "buildcraft.core.robots.boards.BoardRobotLumberjack");
        RobotManager.registerAIRobot(BoardRobotHarvester.class, "boardRobotHarvester",
                "buildcraft.core.robots.boards.BoardRobotHarvester");
        RobotManager.registerAIRobot(BoardRobotMiner.class, "boardRobotMiner",
                "buildcraft.core.robots.boards.BoardRobotMiner");
        RobotManager.registerAIRobot(BoardRobotPlanter.class, "boardRobotPlanter",
                "buildcraft.core.robots.boards.BoardRobotPlanter");
        RobotManager.registerAIRobot(BoardRobotFarmer.class, "boardRobotFarmer",
                "buildcraft.core.robots.boards.BoardRobotFarmer");
        RobotManager.registerAIRobot(BoardRobotPump.class, "boardRobotPump",
                "buildcraft.core.robots.boards.BoardRobotPump");
        RobotManager.registerAIRobot(BoardRobotKnight.class, "boardRobotKnight",
                "buildcraft.core.robots.boards.BoardRobotKnight");
        RobotManager.registerAIRobot(BoardRobotButcher.class, "boardRobotButcher",
                "buildcraft.core.robots.boards.BoardRobotButcher");

        RedstoneBoardRegistry.instance.registerBoardType(BoardRobotPickerNBT.INSTANCE, 800_000_000L);
        RedstoneBoardRegistry.instance.registerBoardType(BoardRobotCarrierNBT.INSTANCE, 800_000_000L);

        // 7.1.x board costs: the seven blue boards at 32000 RF, the red knight at 128000 RF.
        RedstoneBoardRegistry.instance.registerBoardType(BoardRobotLumberjackNBT.INSTANCE, 3_200_000_000L);
        RedstoneBoardRegistry.instance.registerBoardType(BoardRobotHarvesterNBT.INSTANCE, 3_200_000_000L);
        RedstoneBoardRegistry.instance.registerBoardType(BoardRobotMinerNBT.INSTANCE, 3_200_000_000L);
        RedstoneBoardRegistry.instance.registerBoardType(BoardRobotPlanterNBT.INSTANCE, 3_200_000_000L);
        RedstoneBoardRegistry.instance.registerBoardType(BoardRobotFarmerNBT.INSTANCE, 3_200_000_000L);
        RedstoneBoardRegistry.instance.registerBoardType(BoardRobotPumpNBT.INSTANCE, 3_200_000_000L);
        RedstoneBoardRegistry.instance.registerBoardType(BoardRobotButcherNBT.INSTANCE, 3_200_000_000L);
        RedstoneBoardRegistry.instance.registerBoardType(BoardRobotKnightNBT.INSTANCE, 12_800_000_000L);
    }
}
