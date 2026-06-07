package buildcraft.transport.client;

//? if >=26.1 {
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
//?} elif >=1.21.10 {
/*import net.minecraft.client.renderer.block.model.BlockStateModel;*/
//?}
//? if >=1.21.10 {
import net.minecraft.client.renderer.item.ItemModel;
//?}
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
//? if >=1.21.10 {
import net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent;
//?} else {
/*import net.neoforged.neoforge.client.event.RenderHighlightEvent;*/
//?}
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.common.NeoForge;

import buildcraft.api.transport.pipe.PipeApi;
import buildcraft.api.transport.pipe.PipeApiClient;
import buildcraft.api.transport.pipe.PipeDefinition;
import buildcraft.api.transport.pluggable.IPluggableStaticBaker;

import buildcraft.lib.client.model.ModelHolderStatic;
import buildcraft.lib.client.model.plug.PlugBakerSimple;

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
import buildcraft.transport.client.render.PipeFlowRendererFE;
import buildcraft.transport.client.render.PipePlacementHighlight;
import buildcraft.transport.client.render.RenderPipeHolder;
import buildcraft.transport.client.render.PipeBehaviourRendererStripes;
import buildcraft.transport.client.model.key.KeyPlugBlocker;
import buildcraft.transport.client.model.key.KeyPlugPowerAdaptor;
import buildcraft.transport.pipe.flow.PipeFlowFluids;
import buildcraft.transport.pipe.flow.PipeFlowItems;
import buildcraft.transport.pipe.flow.PipeFlowPower;
import buildcraft.transport.pipe.flow.PipeFlowRedstoneFlux;

public class BCTransportClient {
    // Static model holders for plug rendering
    public static final ModelHolderStatic BLOCKER = new ModelHolderStatic("buildcraftunofficial:models/plugs/blocker.json");
    public static final ModelHolderStatic POWER_ADAPTER = new ModelHolderStatic("buildcraftunofficial:models/plugs/power_adapter.json");

    // Bakers that rotate the model to the correct face and produce BakedQuads
    public static final IPluggableStaticBaker<KeyPlugBlocker> BAKER_PLUG_BLOCKER =
        new PlugBakerSimple<>(BLOCKER::getCutoutQuads);
    public static final IPluggableStaticBaker<KeyPlugPowerAdaptor> BAKER_PLUG_POWER_ADAPTOR =
        new PlugBakerSimple<>(POWER_ADAPTER::getCutoutQuads);

    /** Forces class initialization so the static fields above are instantiated. */
    public static void init() {
    }

    public static void initClient(net.neoforged.bus.api.IEventBus modEventBus) {
        modEventBus.register(BCTransportClient.class);
        init();
        modEventBus.register(PipeBlockColourHandler.class);
        // Face/corner-accurate placement-preview outline shown while holding a pluggable or wire.
        //? if >=1.21.10 {
        NeoForge.EVENT_BUS.addListener(ExtractBlockOutlineRenderStateEvent.class,
                PipePlacementHighlight::onExtractBlockOutline);
        //?} else {
        /*NeoForge.EVENT_BUS.addListener(RenderHighlightEvent.Block.class,
                PipePlacementHighlight::onRenderHighlightBlock);*/
        //?}
    }

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
    //? if >=1.21.10 {
    @SubscribeEvent
    public static void registerItemTintSources(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.ItemTintSources event) {
        event.register(
                Identifier.fromNamespaceAndPath(BCTransport.MODID, "pipe_colour"),
                PipeColourTintSource.MAP_CODEC
        );
    }
    //?}
    //? if <1.21.10 {
    /*// 1.21.1 has no data-driven ItemTintSource, so register PipeColourTintSource as a classic ItemColor
    // for every pipe item. PipeItemModel's 1.21.1 painted overlay emits its mask quads at tintIndex 0,
    // which this handler tints with the (semi-transparent) PIPE_COLOUR; the dyed-sprite fluid-pipe
    // variant bakes the colour into the texture and carries no tintindex, so it is unaffected.
    @SubscribeEvent
    public static void registerItemColors(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Item event) {
        java.util.List<Item> pipeItems = new java.util.ArrayList<>();
        for (PipeDefinition def : PipeApi.pipeRegistry.getAllRegisteredPipes()) {
            Item pipeItem = (Item) PipeApi.pipeRegistry.getItemForPipe(def);
            if (pipeItem != null) {
                pipeItems.add(pipeItem);
            }
        }
        if (!pipeItems.isEmpty()) {
            event.register(PipeColourTintSource.INSTANCE, pipeItems.toArray(new Item[0]));
        }
    }*/
    //?}


    /**
     * Swap the vanilla-baked pipe_holder model with PipeBlockStateModel, and
     * wrap each pipe item's vanilla model with PipeItemModel for dynamic colour overlays.
     */
    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        //? if >=1.21.10 {
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
        //?} else {
        /*// 1.21.1: all baked models (block + item) live in one Map<ModelResourceLocation, BakedModel>.
        java.util.Map<net.minecraft.client.resources.model.ModelResourceLocation, net.minecraft.client.resources.model.BakedModel> models =
            event.getModels();
        // Block model swap — keyed by the pipe_holder's blockstate ModelResourceLocation.
        BlockState pipeState = BCTransportBlocks.PIPE_HOLDER.get().defaultBlockState();
        net.minecraft.client.resources.model.ModelResourceLocation blockMrl =
            net.minecraft.client.renderer.block.BlockModelShaper.stateToModelLocation(pipeState);
        net.minecraft.client.resources.model.BakedModel vanillaModel = models.get(blockMrl);
        if (vanillaModel != null) {
            models.put(blockMrl, new PipeBlockStateModel(vanillaModel));
        }
        // Item model swap — each pipe item's "inventory" ModelResourceLocation.
        for (PipeDefinition def : PipeApi.pipeRegistry.getAllRegisteredPipes()) {
            Item pipeItem = (Item) PipeApi.pipeRegistry.getItemForPipe(def);
            if (pipeItem != null) {
                Identifier itemId = BuiltInRegistries.ITEM.getKey(pipeItem);
                net.minecraft.client.resources.model.ModelResourceLocation itemMrl =
                    net.minecraft.client.resources.model.ModelResourceLocation.inventory(itemId);
                net.minecraft.client.resources.model.BakedModel vanillaItemModel = models.get(itemMrl);
                if (vanillaItemModel != null) {
                    models.put(itemMrl, new PipeItemModel(vanillaItemModel, def));
                }
            }
        }*/
        //?}
    }

    /** Called during mod init to register pipe flow renderers and pluggable bakers. */
    public static void registerFlowRenderers() {
        PipeRegistryClient.INSTANCE.registerRenderer(PipeFlowPower.class, PipeFlowRendererPower.INSTANCE);
        PipeRegistryClient.INSTANCE.registerRenderer(PipeFlowRedstoneFlux.class, PipeFlowRendererFE.INSTANCE);
        PipeRegistryClient.INSTANCE.registerRenderer(PipeFlowItems.class, PipeFlowRendererItems.INSTANCE);
        PipeRegistryClient.INSTANCE.registerRenderer(PipeFlowFluids.class, PipeFlowRendererFluids.INSTANCE);

        // Behaviour renderers
        PipeRegistryClient.INSTANCE.registerRenderer(
            buildcraft.transport.pipe.behaviour.PipeBehaviourStripes.class,
            PipeBehaviourRendererStripes.INSTANCE
        );

        // Static pluggable bakers — quads are baked into the chunk mesh
        PipeRegistryClient.INSTANCE.registerBaker(KeyPlugBlocker.class, BAKER_PLUG_BLOCKER);
        PipeRegistryClient.INSTANCE.registerBaker(KeyPlugPowerAdaptor.class, BAKER_PLUG_POWER_ADAPTOR);
    }
}
