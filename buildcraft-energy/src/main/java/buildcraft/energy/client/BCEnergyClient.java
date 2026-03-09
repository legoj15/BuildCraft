package buildcraft.energy.client;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import buildcraft.energy.BCEnergyMenuTypes;
import buildcraft.energy.client.gui.ScreenEngineStone;
import buildcraft.energy.client.gui.ScreenEngineIron;


public class BCEnergyClient {
    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(BCEnergyMenuTypes.ENGINE_STONE.get(), ScreenEngineStone::new);
        event.register(BCEnergyMenuTypes.ENGINE_IRON.get(), ScreenEngineIron::new);
    }
}
