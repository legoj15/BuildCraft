package buildcraft.transport.client;

import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;


import buildcraft.transport.BCTransportBlockEntities;
import buildcraft.transport.BCTransportBlocks;
import buildcraft.transport.BCTransportMenuTypes;
import buildcraft.transport.client.gui.GuiDiamondPipe;
import buildcraft.transport.client.gui.GuiDiamondWoodPipe;
import buildcraft.transport.client.gui.GuiEmzuliPipe;
import buildcraft.transport.client.gui.GuiFilteredBuffer;
import buildcraft.transport.client.model.PipeBlockStateModel;
import buildcraft.transport.client.render.PipeFlowRendererFluids;
import buildcraft.transport.client.render.PipeFlowRendererItems;
import buildcraft.transport.client.render.PipeFlowRendererPower;
import buildcraft.transport.client.render.PipeFlowRendererRf;
import buildcraft.transport.client.render.RenderPipeHolder;
import buildcraft.transport.client.render.PipeBehaviourRendererStripes;
import buildcraft.transport.pipe.flow.PipeFlowFluids;
import buildcraft.transport.pipe.flow.PipeFlowItems;
import buildcraft.transport.pipe.flow.PipeFlowPower;
import buildcraft.transport.pipe.flow.PipeFlowRedstoneFlux;

public class BCTransportClient {
    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(BCTransportMenuTypes.FILTERED_BUFFER.get(), GuiFilteredBuffer::new);
        event.register(BCTransportMenuTypes.DIAMOND_PIPE.get(), GuiDiamondPipe::new);
        event.register(BCTransportMenuTypes.DIAMOND_WOOD_PIPE.get(), GuiDiamondWoodPipe::new);
        event.register(BCTransportMenuTypes.EMZULI_PIPE.get(), GuiEmzuliPipe::new);
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

    /**
     * Swap the vanilla-baked pipe_holder model with PipeBlockStateModel.
     * The vanilla model provides only a particle texture; PipeBlockStateModel wraps it
     * and dynamically generates pipe quads for both chunk-mesh rendering and the breaking overlay.
     */
    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        BlockState pipeState = BCTransportBlocks.PIPE_HOLDER.get().defaultBlockState();
        var models = event.getBakingResult().blockStateModels();
        BlockStateModel vanillaModel = models.get(pipeState);
        if (vanillaModel != null) {
            models.put(pipeState, new PipeBlockStateModel(vanillaModel));
        }
    }

    /** Called during mod init to register pipe flow renderers. */
    public static void registerFlowRenderers() {
        PipeRegistryClient.INSTANCE.registerRenderer(PipeFlowPower.class, PipeFlowRendererPower.INSTANCE);
        PipeRegistryClient.INSTANCE.registerRenderer(PipeFlowRedstoneFlux.class, PipeFlowRendererRf.INSTANCE);
        PipeRegistryClient.INSTANCE.registerRenderer(PipeFlowItems.class, PipeFlowRendererItems.INSTANCE);
        PipeRegistryClient.INSTANCE.registerRenderer(PipeFlowFluids.class, PipeFlowRendererFluids.INSTANCE);

        // Behaviour renderers
        PipeRegistryClient.INSTANCE.registerRenderer(
            buildcraft.transport.pipe.behaviour.PipeBehaviourStripes.class,
            PipeBehaviourRendererStripes.INSTANCE
        );
    }
}
