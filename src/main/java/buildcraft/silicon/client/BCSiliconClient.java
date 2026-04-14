package buildcraft.silicon.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.renderer.block.model.BlockStateModelWrapper;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import buildcraft.api.transport.pipe.PipeApiClient;

import buildcraft.silicon.BCSiliconBlockEntities;
import buildcraft.silicon.BCSiliconItems;
import buildcraft.silicon.BCSiliconMenuTypes;
import buildcraft.silicon.client.model.FacadeItemModel;
import buildcraft.silicon.client.model.key.KeyPlugFacade;
import buildcraft.silicon.client.model.key.KeyPlugGate;
import buildcraft.silicon.client.model.plug.PlugBakerFacade;
import buildcraft.silicon.client.render.RenderLaser;
import buildcraft.silicon.gui.GuiAdvancedCraftingTable;
import buildcraft.silicon.gui.GuiAssemblyTable;
import buildcraft.silicon.gui.GuiIntegrationTable;
import buildcraft.silicon.plug.FacadeStateManager;

public class BCSiliconClient {
    private static final Logger LOGGER = LoggerFactory.getLogger("BuildCraft");

    /** Cached blockstate model map from the last bake, used for deferred facade dedup. */
    private static java.util.Map<net.minecraft.world.level.block.state.BlockState,
            net.minecraft.client.renderer.block.dispatch.BlockStateModel> cachedBlockStateModels;

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(BCSiliconBlockEntities.LASER.get(), RenderLaser::new);
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(BCSiliconMenuTypes.ASSEMBLY_TABLE.get(), GuiAssemblyTable::new);
        event.register(BCSiliconMenuTypes.INTEGRATION_TABLE.get(), GuiIntegrationTable::new);
        event.register(BCSiliconMenuTypes.ADVANCED_CRAFTING_TABLE.get(), GuiAdvancedCraftingTable::new);
        event.register(BCSiliconMenuTypes.GATE.get(), buildcraft.silicon.gui.GuiGate::new);
    }




    /**
     * Register facade baker and swap the vanilla facade item model with FacadeItemModel.
     * This event fires after all models are baked and after EntityRenderersEvent has
     * completed, so PipeApiClient.registry is guaranteed to be set by Transport.
     */
    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        // Register facade baker via API (fires after Transport's registerRenderers)
        if (PipeApiClient.registry != null) {
            PipeApiClient.registry.registerBaker(KeyPlugGate.class, buildcraft.silicon.client.model.plug.PlugGateBaker.INSTANCE);
            PipeApiClient.registry.registerBaker(KeyPlugFacade.class, PlugBakerFacade.INSTANCE);
            PipeApiClient.registry.registerBaker(buildcraft.silicon.client.model.key.KeyPlugLens.class, buildcraft.silicon.client.model.plug.PlugBakerLens.INSTANCE);
            PipeApiClient.registry.registerBaker(buildcraft.silicon.client.model.key.KeyPlugSimple.class, buildcraft.silicon.client.model.plug.PlugBakerSimpleItems.INSTANCE);
            // Dynamic renderers
            PipeApiClient.registry.registerRenderer(buildcraft.silicon.plug.PluggablePulsar.class, buildcraft.silicon.client.render.PlugPulsarRenderer.INSTANCE);
        } else {
            LOGGER.warn("[silicon.client] PipeApiClient.registry is null at ModifyBakingResult! "
                + "Facade in-world rendering will not work.");
        }

        // Swap vanilla item model with dynamic facade model
        var itemModels = event.getBakingResult().itemStackModels();
        Identifier facadeId = BuiltInRegistries.ITEM.getKey(BCSiliconItems.PLUG_FACADE.get());
        // MC 26.1: FacadeItemModel no longer needs the vanilla wrapper
        // (BlockStateModelWrapper fields completely changed)
        ItemModel vanillaModel = itemModels.get(facadeId);
        if (vanillaModel != null) {
            itemModels.put(facadeId, new FacadeItemModel());
        }

        // Swap vanilla gate item model with 3D programmatic model
        Identifier gateId = BuiltInRegistries.ITEM.getKey(BCSiliconItems.PLUG_GATE.get());
        ItemModel vanillaGateModel = itemModels.get(gateId);
        if (vanillaGateModel != null) {
            itemModels.put(gateId, new buildcraft.silicon.client.model.GateItemModel());
        }

        FacadeItemModel.onModelBake();
        buildcraft.silicon.client.model.GateItemModel.onModelBake();
        buildcraft.silicon.client.model.plug.PlugGateBaker.onModelBake();
        buildcraft.silicon.client.model.plug.PlugBakerSimpleItems.onModelBake();

        // Cache the blockstate models for deferred facade deduplication.
        // We can't run dedup here because ItemStack components aren't bound yet
        // during the initial resource reload. Dedup runs later via runDeferredDedup().
        cachedBlockStateModels = event.getBakingResult().blockStateModels();
    }

    /**
     * Runs visual facade deduplication using the cached blockstate models from the
     * last bake. Should be called after FacadeStateManager has been initialized
     * (i.e. after registry components are bound).
     */
    public static void runDeferredDedup() {
        if (cachedBlockStateModels != null) {
            FacadeDeduplicator.deduplicateVisuallyIdentical(cachedBlockStateModels);
            cachedBlockStateModels = null; // release reference
        }
    }

    public static void initClient(net.neoforged.bus.api.IEventBus modEventBus) {
        modEventBus.register(BCSiliconClient.class);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(buildcraft.silicon.client.RenderLaser.class);
    }
}
