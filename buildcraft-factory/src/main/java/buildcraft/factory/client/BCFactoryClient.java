package buildcraft.factory.client;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import buildcraft.factory.BCFactoryMenuTypes;
import buildcraft.factory.client.gui.GuiAutoCraftItems;

public class BCFactoryClient {
    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(BCFactoryMenuTypes.AUTO_WORKBENCH_ITEMS.get(), GuiAutoCraftItems::new);
    }
}
