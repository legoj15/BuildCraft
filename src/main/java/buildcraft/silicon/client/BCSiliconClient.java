package buildcraft.silicon.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.renderer.block.model.BlockStateModelWrapper;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import buildcraft.api.transport.pipe.PipeApiClient;

import buildcraft.silicon.BCSiliconItems;
import buildcraft.silicon.BCSiliconMenuTypes;
import buildcraft.silicon.client.model.FacadeItemModel;
import buildcraft.silicon.client.model.key.KeyPlugFacade;
import buildcraft.silicon.client.model.key.KeyPlugGate;
import buildcraft.silicon.client.model.plug.PlugBakerFacade;
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
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(BCSiliconMenuTypes.ASSEMBLY_TABLE.get(), GuiAssemblyTable::new);
        // Integration Table is dev-only — absent from public builds.
        if (BCSiliconMenuTypes.INTEGRATION_TABLE != null) {
            event.register(BCSiliconMenuTypes.INTEGRATION_TABLE.get(), GuiIntegrationTable::new);
        }
        event.register(BCSiliconMenuTypes.ADVANCED_CRAFTING_TABLE.get(), GuiAdvancedCraftingTable::new);
        event.register(BCSiliconMenuTypes.GATE.get(), buildcraft.silicon.gui.GuiGate::new);
    }

    @SubscribeEvent
    public static void onClientSetup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        // Dev-only Integration Table gets a red "Dev only" tooltip marker.
        if (buildcraft.lib.BCLib.DEV && BCSiliconItems.INTEGRATION_TABLE != null) {
            event.enqueueWork(() ->
                buildcraft.lib.client.BCTooltips.markDevOnly(BCSiliconItems.INTEGRATION_TABLE.get()));
        }
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
            PipeApiClient.registry.registerRenderer(buildcraft.silicon.plug.PluggableGate.class, buildcraft.silicon.client.render.PlugGateRenderer.INSTANCE);
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

        // Swap vanilla lens item model with dynamic programmatic model
        Identifier lensId = BuiltInRegistries.ITEM.getKey(BCSiliconItems.PLUG_LENS.get());
        ItemModel vanillaLensModel = itemModels.get(lensId);
        if (vanillaLensModel != null) {
            itemModels.put(lensId, new buildcraft.silicon.client.model.LensItemModel());
        }

        FacadeItemModel.onModelBake();
        buildcraft.silicon.client.model.GateItemModel.onModelBake();
        buildcraft.silicon.client.model.LensItemModel.onModelBake();
        buildcraft.silicon.client.model.plug.PlugGateBaker.onModelBake();
        buildcraft.silicon.client.render.PlugGateRenderer.onModelBake();
        // Also invalidate the upstream PipeModelCacheAll cache so any pipe model that previously
        // baked non-empty gate quads (back when PlugGateBaker.bake didn't return an empty list)
        // gets re-baked. Without this, those old chunk-mesh gate quads can persist in the
        // joined-cache layer above PlugGateBaker and render UNDERNEATH the BER, producing dim
        // gates that look like the BER is broken when the actual issue is stale chunk geometry.
        buildcraft.transport.client.model.PipeModelCacheAll.clearModels();
        buildcraft.silicon.client.model.plug.PlugBakerSimpleItems.onModelBake();

        // Cache the blockstate models for deferred facade deduplication.
        // We can't run dedup here because ItemStack components aren't bound yet
        // during the initial resource reload. Dedup runs later via runDeferredDedup().
        cachedBlockStateModels = event.getBakingResult().blockStateModels();
    }

    /**
     * Runs visual facade deduplication using the cached blockstate models from the
     * last bake. Idempotent — clears the cache reference so subsequent calls no-op.
     *
     * <p>Triggered primarily from {@link #onClientLoggingIn} (deterministic, once per world
     * join, on render thread, after both model bake and {@link FacadeStateManager#init()}).
     * The {@code addCreativeTabItems → FACADE_TAB_KEY} call site still invokes this as a
     * safety net for the rare F3+T (resource reload) case — after a re-bake the cache is
     * non-null again, and the next time the tab is rebuilt this re-runs dedup against the
     * new textures. In the steady state this is a no-op.
     */
    public static void runDeferredDedup() {
        if (cachedBlockStateModels != null) {
            FacadeDeduplicator.deduplicateVisuallyIdentical(cachedBlockStateModels);
            cachedBlockStateModels = null; // release reference
        }
    }

    /**
     * Game-bus (NeoForge.EVENT_BUS) listeners for the silicon client. Kept in a separate
     * inner class so the mod-event-bus and game-event-bus listener sets don't share a
     * @SubscribeEvent surface — each registration goes to exactly the right bus.
     */
    public static final class GameBus {
        /**
         * Run facade dedup at world-join time so it doesn't happen lazily inside JEI's
         * first-screen-open ingredient scan (which used to land it on the same render-thread
         * critical path as the player opening a GUI). At LoggingIn:
         * <ul>
         *   <li>Models are baked (ModifyBakingResult fired during the launch reload).</li>
         *   <li>For an integrated server, {@link FacadeStateManager#init()} has run via
         *       {@code ServerAboutToStartEvent}; for a multiplayer client we kick it here
         *       as a safety net (the scan only touches BlockState registry — no server context).</li>
         * </ul>
         *
         * <p>{@link FacadeDeduplicator#applyRedirectAuthority()} is called unconditionally <em>after</em>
         * {@code runDeferredDedup()} because the dedup pass only re-runs when a re-bake refilled the model
         * cache — after the first pass {@code runDeferredDedup()} is a no-op. The redirect-authority
         * decision (is this client's JVM running the server?), by contrast, can change every join: a
         * player can leave a single-player world (redirects valid) and join a dedicated server (redirects
         * must be withheld) without any model re-bake. Re-evaluating here keeps
         * {@link FacadeStateManager#stackRedirects} honest across that transition. (On the very first join
         * the dedup pass calls it too; calling twice is idempotent.)
         */
        @SubscribeEvent
        public static void onClientLoggingIn(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingIn event) {
            FacadeStateManager.ensureInitialized();
            runDeferredDedup();
            FacadeDeduplicator.applyRedirectAuthority();
        }
    }

    public static void initClient(net.neoforged.bus.api.IEventBus modEventBus) {
        modEventBus.register(BCSiliconClient.class);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(buildcraft.silicon.client.RenderLaser.class);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(GameBus.class);
    }
}
