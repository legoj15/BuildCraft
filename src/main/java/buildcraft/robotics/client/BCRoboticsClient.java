package buildcraft.robotics.client;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import buildcraft.lib.BCLib;
import buildcraft.lib.client.BCTooltips;
import buildcraft.robotics.BCRoboticsItems;
import buildcraft.robotics.BCRoboticsMenuTypes;
import buildcraft.robotics.client.gui.GuiZonePlanner;

public class BCRoboticsClient {
    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        if (BCRoboticsMenuTypes.ZONE_PLANNER != null) {
            event.register(BCRoboticsMenuTypes.ZONE_PLANNER.get(), GuiZonePlanner::new);
        }
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // Dev-only Zone Planner gets a red "Dev only" tooltip marker.
        if (BCLib.DEV && BCRoboticsItems.ZONE_PLANNER != null) {
            event.enqueueWork(() -> BCTooltips.markDevOnly(BCRoboticsItems.ZONE_PLANNER.get()));
        }
    }

    public static void initClient(net.neoforged.bus.api.IEventBus modEventBus) {
        modEventBus.register(BCRoboticsClient.class);
    }
}
