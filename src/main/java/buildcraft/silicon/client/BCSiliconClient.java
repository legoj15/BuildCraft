/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//? if >=1.21.10 {
import net.minecraft.client.renderer.item.ItemModel;
//?}
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

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
import buildcraft.platform.client.ClientPlatform;
import buildcraft.platform.client.ClientRegistrationSinks;

public class BCSiliconClient {
    private static final Logger LOGGER = LoggerFactory.getLogger("BuildCraft");

    //? if >=1.21.10 {
    /**
     * Set each time the models bake, cleared once the deferred facade dedup has run against them: the dedup reads the
     * block-state models from the live {@code ModelManager} (no loader offers a whole-set bake hook on every line).
     */
    private static volatile boolean dedupPending;
    //?} else {
    /*/^* Cached blockstate model map from the last bake, used for deferred facade dedup. ^/
    private static java.util.Map<net.minecraft.world.level.block.state.BlockState,
            net.minecraft.client.resources.model.BakedModel> cachedBlockStateModels;*/
    //?}

    private static void registerScreens(ClientRegistrationSinks sinks) {
        sinks.menuScreen(BCSiliconMenuTypes.ASSEMBLY_TABLE, GuiAssemblyTable::new);
        sinks.menuScreen(BCSiliconMenuTypes.INTEGRATION_TABLE, GuiIntegrationTable::new);
        sinks.menuScreen(BCSiliconMenuTypes.ADVANCED_CRAFTING_TABLE, GuiAdvancedCraftingTable::new);
        sinks.menuScreen(BCSiliconMenuTypes.GATE, buildcraft.silicon.gui.GuiGate::new);
    }




    /**
     * Register facade baker and swap the vanilla facade item model with FacadeItemModel.
     * This event fires after all models are baked and after EntityRenderersEvent has
     * completed, so PipeApiClient.registry is guaranteed to be set by Transport.
     */
    public static void onModifyBakingResult() {
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
    }

    //? if >=1.21.10 {
    /**
     * Swaps the facade, gate and lens items' JSON models for the dynamic ones (the item-model editor of
     * {@code ClientRegistrationSinks}: every baked item model passes through here, each time the models bake).
     */
    public static ItemModel editItemModel(Identifier id, ItemModel baked) {
        if (id.equals(BuiltInRegistries.ITEM.getKey(BCSiliconItems.PLUG_FACADE.get()))) {
            return new FacadeItemModel();
        }
        if (id.equals(BuiltInRegistries.ITEM.getKey(BCSiliconItems.PLUG_GATE.get()))) {
            return new buildcraft.silicon.client.model.GateItemModel();
        }
        if (id.equals(BuiltInRegistries.ITEM.getKey(BCSiliconItems.PLUG_LENS.get()))) {
            return new buildcraft.silicon.client.model.LensItemModel();
        }
        return baked;
    }

    /** The models baked again: the next world join dedups the facades against them. */
    private static void onModelsBakedForDedup() {
        dedupPending = true;
    }

    /**
     * The live block-state models: every state with a model of its own (the missing model is left out, as states
     * without a model are absent from the bake's own map), read through the {@code ModelManager}.
     */
    //? if >=26.1 {
    private static java.util.Map<net.minecraft.world.level.block.state.BlockState,
            net.minecraft.client.renderer.block.dispatch.BlockStateModel> liveBlockStateModels() {
        var models = net.minecraft.client.Minecraft.getInstance().getModelManager().getBlockStateModelSet();
        var missing = models.missingModel();
        var byState = new java.util.HashMap<net.minecraft.world.level.block.state.BlockState,
                net.minecraft.client.renderer.block.dispatch.BlockStateModel>();
    //?} else {
    /*private static java.util.Map<net.minecraft.world.level.block.state.BlockState,
            net.minecraft.client.renderer.block.model.BlockStateModel> liveBlockStateModels() {
        var manager = net.minecraft.client.Minecraft.getInstance().getModelManager();
        var models = manager.getBlockModelShaper();
        var missing = manager.getMissingBlockStateModel();
        var byState = new java.util.HashMap<net.minecraft.world.level.block.state.BlockState,
                net.minecraft.client.renderer.block.model.BlockStateModel>();*/
    //?}
        for (net.minecraft.world.level.block.Block block : BuiltInRegistries.BLOCK) {
            for (net.minecraft.world.level.block.state.BlockState state : block.getStateDefinition().getPossibleStates()) {
                //? if >=26.1 {
                var model = models.get(state);
                //?} else {
                /*var model = models.getBlockModel(state);*/
                //?}
                if (model != null && model != missing) {
                    byState.put(state, model);
                }
            }
        }
        return byState;
    }
    //?} else {
    /*/^*
     * Swap the vanilla facade, gate and lens item models for the dynamic ones, and cache the blockstate models for
     * the deferred facade deduplication. Runs right after {@link #onModifyBakingResult} on each bake.
     ^/
    public static void onEditBakedModels(java.util.Map<net.minecraft.client.resources.model.ModelResourceLocation,
            net.minecraft.client.resources.model.BakedModel> bakedModels) {
        // 1.21.1: all baked models live in one Map<ModelResourceLocation, BakedModel>. Swap each
        // plug item's "inventory" model with its dynamic BakedModel (Facade/Gate/LensItemModel).
        java.util.Map<net.minecraft.client.resources.model.ModelResourceLocation, net.minecraft.client.resources.model.BakedModel> models = bakedModels;
        Identifier facadeId = BuiltInRegistries.ITEM.getKey(BCSiliconItems.PLUG_FACADE.get());
        net.minecraft.client.resources.model.ModelResourceLocation facadeMrl = net.minecraft.client.resources.model.ModelResourceLocation.inventory(facadeId);
        if (models.get(facadeMrl) != null) {
            models.put(facadeMrl, new FacadeItemModel());
        }
        Identifier gateId = BuiltInRegistries.ITEM.getKey(BCSiliconItems.PLUG_GATE.get());
        net.minecraft.client.resources.model.ModelResourceLocation gateMrl = net.minecraft.client.resources.model.ModelResourceLocation.inventory(gateId);
        if (models.get(gateMrl) != null) {
            models.put(gateMrl, new buildcraft.silicon.client.model.GateItemModel());
        }
        Identifier lensId = BuiltInRegistries.ITEM.getKey(BCSiliconItems.PLUG_LENS.get());
        net.minecraft.client.resources.model.ModelResourceLocation lensMrl = net.minecraft.client.resources.model.ModelResourceLocation.inventory(lensId);
        if (models.get(lensMrl) != null) {
            models.put(lensMrl, new buildcraft.silicon.client.model.LensItemModel());
        }

        // Cache the blockstate models for deferred facade deduplication.
        // We can't run dedup here because ItemStack components aren't bound yet
        // during the initial resource reload. Dedup runs later via runDeferredDedup().
        // 1.21.1 has no blockStateModels() accessor — build a BlockState->BakedModel view of the
        // unified model map (keyed via BlockModelShaper.stateToModelLocation) for FacadeDeduplicator.
        java.util.Map<net.minecraft.world.level.block.state.BlockState, net.minecraft.client.resources.model.BakedModel> byState = new java.util.HashMap<>();
        for (net.minecraft.world.level.block.Block block : BuiltInRegistries.BLOCK) {
            for (net.minecraft.world.level.block.state.BlockState state : block.getStateDefinition().getPossibleStates()) {
                net.minecraft.client.resources.model.BakedModel m =
                    models.get(net.minecraft.client.renderer.block.BlockModelShaper.stateToModelLocation(state));
                if (m != null) {
                    byState.put(state, m);
                }
            }
        }
        cachedBlockStateModels = byState;
    }*/
    //?}

    /**
     * Drop the plug caches that hold sprite UVs once the reload has swapped in the new atlases.
     * ModifyBakingResult is too early: it runs while models prepare, before the new block atlas is
     * uploaded, so a chunk compile in that window would re-cache old-atlas UVs until the next
     * reload. The joined per-pipe cache above these goes after them, in
     * {@code BCTransportClient#onBakingCompleted} (LOWEST priority).
     */
    public static void onBakingCompleted() {
        FacadeItemModel.onModelBake();
        buildcraft.silicon.client.model.GateItemModel.onModelBake();
        buildcraft.silicon.client.model.LensItemModel.onModelBake();
        buildcraft.silicon.client.model.plug.PlugBakerLens.onModelBake();
        buildcraft.silicon.client.render.PlugGateRenderer.onModelBake();
        buildcraft.silicon.client.render.PlugPulsarRenderer.onModelBake();
    }

    /**
     * Runs visual facade deduplication using the cached blockstate models from the
     * last bake. Idempotent — clears the cache reference so subsequent calls no-op.
     *
     * <p>Triggered primarily from {@link #onClientLoggingIn} (deterministic, once per world
     * join, on render thread, after both model bake and {@link FacadeStateManager#init()}).
     * The {@code addCreativeTabItems → FACADE_TAB_KEY} call site still invokes this as a
     * safety net. After an F3+T re-bake the cache is non-null again, but a resource reload
     * alone does not rebuild the creative tab (vanilla's ItemDisplayParameters check sees no
     * change), so in practice the re-dedup against the new textures happens at the next
     * world join. In the steady state this is a no-op.
     */
    public static void runDeferredDedup() {
        if (FacadeStateManager.isFacadeGenerationDisabled()) {
            return;
        }
        //? if >=1.21.10 {
        if (dedupPending) {
            dedupPending = false;
            FacadeDeduplicator.deduplicateVisuallyIdentical(liveBlockStateModels());
        }
        //?} else {
        /*if (cachedBlockStateModels != null) {
            FacadeDeduplicator.deduplicateVisuallyIdentical(cachedBlockStateModels);
            cachedBlockStateModels = null; // release reference
        }*/
        //?}
    }

    /** The silicon client's game hooks. */
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
        // HIGH priority so facade state init runs before other LoggingIn listeners that may name
        // facades. The guide-book content build happens even later — it now triggers off the
        // server's recipe sync arriving (BCLibClient's RecipesReceivedEvent handler), which
        // always follows LoggingIn — so facade state and the assembly registry below are both
        // ready before the guide indexes every item. On a multiplayer client this is the only
        // init trigger for either (ServerAboutToStartEvent never fires there); without it the
        // guide would index facades with no valid states and an empty assembly registry.
        public static void onClientLoggingIn() {
            FacadeStateManager.ensureInitialized();
            // Assembly recipes (gates, chipsets, lenses, wires, facades): populated at
            // ServerAboutToStartEvent on servers; multiplayer clients populate here so the
            // guide book's and JEI's assembly content exists there too.
            buildcraft.silicon.BCSiliconRecipes.ensureInitialized();
            runDeferredDedup();
            FacadeDeduplicator.applyRedirectAuthority();
        }
    }

    public static void initClient() {
        ClientPlatform.sinks().onModelsBaking(BCSiliconClient::onModifyBakingResult);
        //? if >=1.21.10 {
        ClientPlatform.sinks().editItemModels(BCSiliconClient::editItemModel);
        ClientPlatform.sinks().onModelsBaked(buildcraft.platform.event.HookOrder.DEFAULT,
                BCSiliconClient::onModelsBakedForDedup);
        //?} else {
        /*ClientPlatform.sinks().editBakedModels(BCSiliconClient::onEditBakedModels);*/
        //?}
        ClientPlatform.sinks().onModelsBaked(buildcraft.platform.event.HookOrder.DEFAULT,
                BCSiliconClient::onBakingCompleted);
        registerScreens(ClientPlatform.sinks());
        buildcraft.silicon.client.model.plug.PlugBakerSimpleItems.init();
        ClientPlatform.hooks().onLevelRender(buildcraft.silicon.client.RenderLaser::onRenderLevel);
        ClientPlatform.hooks().onLoggingInEarly(GameBus::onClientLoggingIn);
    }
}
