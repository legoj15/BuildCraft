package buildcraft.transport.client;

//? if >=1.21.10 {
import net.minecraft.client.renderer.item.ItemModel;
//?}
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import buildcraft.api.transport.pipe.PipeApi;
import buildcraft.api.transport.pipe.PipeApiClient;
import buildcraft.api.transport.pipe.PipeDefinition;
import buildcraft.api.transport.pluggable.IPluggableStaticBaker;

import buildcraft.lib.client.model.ModelHolderStatic;
import buildcraft.lib.client.model.plug.PlugBakerSimple;

import buildcraft.transport.BCTransportBlockEntities;
import buildcraft.transport.BCTransportBlocks;
import buildcraft.transport.BCTransportMenuTypes;
import buildcraft.transport.client.gui.GuiDiamondPipe;
import buildcraft.transport.client.gui.GuiDiamondWoodPipe;
import buildcraft.transport.client.gui.GuiEmzuliPipe;
import buildcraft.transport.client.gui.GuiFilteredBuffer;
import buildcraft.transport.client.model.PipeBaseModelGenStandard;
import buildcraft.transport.client.model.PipeBlockModelQuads;
import buildcraft.transport.client.model.PipeItemModel;
import buildcraft.transport.client.model.PipeModelCacheAll;
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
import buildcraft.transport.wire.ClientWireSystems;
import buildcraft.platform.client.ClientPlatform;
import buildcraft.platform.client.ClientRegistrationSinks;
import buildcraft.platform.event.HookOrder;

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

    public static void initClient() {
        ClientPlatform.sinks().onModelsBaked(HookOrder.LATE, BCTransportClient::onBakingCompleted);
        // The pipe_holder block model (every blockstate, waterlogged true/false, so a flooded pipe doesn't keep the
        // un-swapped placeholder model), then the pipe item models.
        ClientPlatform.sinks().pipeBlockModel(BCTransportBlocks.PIPE_HOLDER, PipeBlockModelQuads.INSTANCE);
        ClientPlatform.sinks().editBakedModels(BCTransportClient::onModifyBakingResult);
        init();
        registerClientContent(ClientPlatform.sinks());
        // Set the API registry so other modules (e.g. silicon) can register bakers, and register the flow
        // renderers PipeRegistryClient dispatches to. Both only have to be in place before the first model bake
        // (silicon and robotics register their bakers from ModifyBakingResult); they used to ride along with the
        // pipe BER registration.
        PipeApiClient.registry = PipeRegistryClient.INSTANCE;
        registerFlowRenderers();
        // The client's wire systems belong to the level they were sent for. A client level closing (disconnect, or the
        // old dimension on a dimension change; Minecraft posts Unload for it on every node) must not leave them for the
        // next level: the server sends none for a level with no wire in view, so nothing else would clear them. Client
        // levels only: an integrated server's levels post the same event.
        ClientPlatform.hooks().onClientLevelUnload(ClientWireSystems.INSTANCE::clear);
        // Face/corner-accurate placement-preview outline shown while holding a pluggable or wire.
        ClientPlatform.hooks().onBlockOutline(PipePlacementHighlight::outline);
    }

    private static void registerClientContent(ClientRegistrationSinks sinks) {
        sinks.menuScreen(BCTransportMenuTypes.FILTERED_BUFFER, GuiFilteredBuffer::new);
        sinks.menuScreen(BCTransportMenuTypes.DIAMOND_PIPE, GuiDiamondPipe::new);
        sinks.menuScreen(BCTransportMenuTypes.DIAMOND_WOOD_PIPE, GuiDiamondWoodPipe::new);
        sinks.menuScreen(BCTransportMenuTypes.EMZULI_PIPE, GuiEmzuliPipe::new);

        sinks.blockEntityRenderer(BCTransportBlockEntities.PIPE_HOLDER, RenderPipeHolder::new);

        // Painted pipe items: >=1.21.10 PipeItemModel paints them itself (no item JSON has a tint source for it);
        // only 1.21.1 tints through a classic ItemColor.
        //? if <1.21.10 {
        /*// 1.21.1 has no data-driven ItemTintSource, so register PipeColourTintSource as a classic ItemColor
        // for every pipe item. PipeItemModel's 1.21.1 painted overlay emits its mask quads at tintIndex 0,
        // which this handler tints with the (semi-transparent) PIPE_COLOUR; the dyed-sprite fluid-pipe
        // variant bakes the colour into the texture and carries no tintindex, so it is unaffected.
        sinks.itemColor(PipeColourTintSource.INSTANCE, () -> {
            java.util.List<Item> pipeItems = new java.util.ArrayList<>();
            for (PipeDefinition def : PipeApi.pipeRegistry.getAllRegisteredPipes()) {
                Item pipeItem = (Item) PipeApi.pipeRegistry.getItemForPipe(def);
                if (pipeItem != null) {
                    pipeItems.add(pipeItem);
                }
            }
            return pipeItems.toArray(new net.minecraft.world.level.ItemLike[0]);
        });*/
        //?}

        PipeBlockColourHandler.register(sinks);
        sinks.blockParticles(BCTransportBlocks.PIPE_HOLDER, PipeHolderClientExtensions.INSTANCE);
    }



    /**
     * Drop the pipe sprite arrays and the joined per-pipe quad cache once a reload has swapped in
     * the new atlases (F3+T, a pack change, the High Contrast toggle). BakingCompleted is the
     * earliest mod hook after the atlas swap: reload listeners apply in
     * registration order with the atlases ahead of the model manager (on 1.21.1 the model manager
     * uploads them itself just before this event). LOWEST priority so the joined cache goes after
     * the plug bakers' caches (silicon, robotics), or it could re-join their stale quads.
     */
    public static void onBakingCompleted() {
        PipeBaseModelGenStandard.clearSpriteCaches();
        PipeBehaviourRendererStripes.onModelBake();
        PipeFlowRendererItems.onModelBake();
        PipeModelCacheAll.clearModels();
    }

    /**
     * Wrap each pipe item's vanilla model with PipeItemModel for dynamic colour overlays. (The pipe_holder block model
     * is swapped by {@code ClientRegistrationSinks.pipeBlockModel}, registered just before this.)
     */
    //? if >=1.21.10 {
    public static void onModifyBakingResult(net.minecraft.client.resources.model.ModelBakery.BakingResult bakingResult) {
    //?} else {
    /*public static void onModifyBakingResult(java.util.Map<net.minecraft.client.resources.model.ModelResourceLocation,
            net.minecraft.client.resources.model.BakedModel> bakedModels) {*/
    //?}
        //? if >=1.21.10 {
        // Item model swap — wrap each pipe item with PipeItemModel
        var itemModels = bakingResult.itemStackModels();
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
            bakedModels;
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
