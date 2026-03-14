package buildcraft.transport.client;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import buildcraft.transport.BCTransportBlockEntities;
import buildcraft.transport.BCTransportMenuTypes;
import buildcraft.transport.client.gui.GuiFilteredBuffer;
import buildcraft.transport.client.render.RenderPipeHolder;

public class BCTransportClient {
    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(BCTransportMenuTypes.FILTERED_BUFFER.get(), GuiFilteredBuffer::new);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(BCTransportBlockEntities.PIPE_HOLDER.get(), RenderPipeHolder::new);
    }
}
