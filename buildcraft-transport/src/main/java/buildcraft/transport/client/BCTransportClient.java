package buildcraft.transport.client;

import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.model.BlockStateModelWrapper;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

import buildcraft.api.transport.pipe.PipeApi;
import buildcraft.api.transport.pipe.PipeApiClient;
import buildcraft.api.transport.pipe.PipeDefinition;

import buildcraft.transport.BCTransport;
import buildcraft.transport.BCTransportBlockEntities;
import buildcraft.transport.BCTransportBlocks;
import buildcraft.transport.BCTransportMenuTypes;
import buildcraft.transport.client.gui.GuiDiamondPipe;
import buildcraft.transport.client.gui.GuiDiamondWoodPipe;
import buildcraft.transport.client.gui.GuiEmzuliPipe;
import buildcraft.transport.client.gui.GuiFilteredBuffer;
import buildcraft.transport.client.model.PipeBlockStateModel;
import buildcraft.transport.client.model.PipeItemModel;
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
        // Set the API registry so other modules (e.g. silicon) can register bakers
        PipeApiClient.registry = PipeRegistryClient.INSTANCE;
        // Register flow renderers so PipeRegistryClient can dispatch render calls
        registerFlowRenderers();
    }

    @SubscribeEvent
    public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerBlock(PipeHolderClientExtensions.INSTANCE, BCTransportBlocks.PIPE_HOLDER.get());
    }

    /**
     * Register the pipe colour tint source so item model JSON can reference it.
     */
    @SubscribeEvent
    public static void registerItemTintSources(RegisterColorHandlersEvent.ItemTintSources event) {
        event.register(
                Identifier.fromNamespaceAndPath(BCTransport.MODID, "pipe_colour"),
                PipeColourTintSource.MAP_CODEC
        );
    }

    /**
     * Swap the vanilla-baked pipe_holder model with PipeBlockStateModel, and
     * wrap each pipe item's vanilla model with PipeItemModel for dynamic colour overlays.
     */
    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        // Block model swap
        BlockState pipeState = BCTransportBlocks.PIPE_HOLDER.get().defaultBlockState();
        var blockModels = event.getBakingResult().blockStateModels();
        BlockStateModel vanillaModel = blockModels.get(pipeState);
        if (vanillaModel != null) {
            blockModels.put(pipeState, new PipeBlockStateModel(vanillaModel));
        }

        // Item model swap — wrap each pipe item with PipeItemModel
        var itemModels = event.getBakingResult().itemStackModels();
        for (PipeDefinition def : PipeApi.pipeRegistry.getAllRegisteredPipes()) {
            Item pipeItem = (Item) PipeApi.pipeRegistry.getItemForPipe(def);
            if (pipeItem != null) {
                Identifier itemId = BuiltInRegistries.ITEM.getKey(pipeItem);
                ItemModel vanillaItemModel = itemModels.get(itemId);
                // MC 26.1: PipeItemModel no longer reflects into BlockStateModelWrapper;
                // accepts any ItemModel as delegate for base pipe rendering
                if (vanillaItemModel != null) {
                    itemModels.put(itemId, new PipeItemModel(vanillaItemModel, def));
                }
            }
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
