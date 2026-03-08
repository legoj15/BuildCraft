package buildcraft.energy.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import buildcraft.energy.BCEnergyMenuTypes;
import buildcraft.energy.client.gui.ScreenEngineStone;
import buildcraft.energy.client.gui.ScreenEngineIron;

@OnlyIn(Dist.CLIENT)
public class BCEnergyClient {
    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(BCEnergyMenuTypes.ENGINE_STONE.get(), ScreenEngineStone::new);
        event.register(BCEnergyMenuTypes.ENGINE_IRON.get(), ScreenEngineIron::new);
    }
}
