package buildcraft.robotics.client;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import buildcraft.robotics.BCRoboticsMenuTypes;
import buildcraft.robotics.client.gui.GuiZonePlanner;

public class BCRoboticsClient {
    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(BCRoboticsMenuTypes.ZONE_PLANNER.get(), GuiZonePlanner::new);
    }
}
