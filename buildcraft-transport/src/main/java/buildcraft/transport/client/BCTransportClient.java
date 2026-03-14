package buildcraft.transport.client;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

import buildcraft.transport.BCTransportBlockEntities;
import buildcraft.transport.BCTransportBlocks;
import buildcraft.transport.BCTransportMenuTypes;
import buildcraft.transport.client.gui.GuiFilteredBuffer;
import buildcraft.transport.client.render.PipeFlowRendererFluids;
import buildcraft.transport.client.render.PipeFlowRendererItems;
import buildcraft.transport.client.render.PipeFlowRendererPower;
import buildcraft.transport.client.render.PipeFlowRendererRf;
import buildcraft.transport.client.render.RenderPipeHolder;
import buildcraft.transport.pipe.flow.PipeFlowFluids;
import buildcraft.transport.pipe.flow.PipeFlowItems;
import buildcraft.transport.pipe.flow.PipeFlowPower;
import buildcraft.transport.pipe.flow.PipeFlowRedstoneFlux;

public class BCTransportClient {
    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(BCTransportMenuTypes.FILTERED_BUFFER.get(), GuiFilteredBuffer::new);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(BCTransportBlockEntities.PIPE_HOLDER.get(), RenderPipeHolder::new);
        // Register flow renderers so PipeRegistryClient can dispatch render calls
        registerFlowRenderers();
    }

    @SubscribeEvent
    public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerBlock(PipeHolderClientExtensions.INSTANCE, BCTransportBlocks.PIPE_HOLDER.get());
    }

    /** Called during mod init to register pipe flow renderers. */
    public static void registerFlowRenderers() {
        PipeRegistryClient.INSTANCE.registerRenderer(PipeFlowPower.class, PipeFlowRendererPower.INSTANCE);
        PipeRegistryClient.INSTANCE.registerRenderer(PipeFlowRedstoneFlux.class, PipeFlowRendererRf.INSTANCE);
        PipeRegistryClient.INSTANCE.registerRenderer(PipeFlowItems.class, PipeFlowRendererItems.INSTANCE);
        PipeRegistryClient.INSTANCE.registerRenderer(PipeFlowFluids.class, PipeFlowRendererFluids.INSTANCE);
    }
}

