package buildcraft.transport.client;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import buildcraft.transport.BCTransportMenuTypes;
import buildcraft.transport.client.gui.GuiFilteredBuffer;

public class BCTransportClient {
    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(BCTransportMenuTypes.FILTERED_BUFFER.get(), GuiFilteredBuffer::new);
    }
}
