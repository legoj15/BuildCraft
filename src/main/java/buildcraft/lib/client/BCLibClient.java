/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.common.NeoForge;

import buildcraft.api.registry.EventBuildCraftReload;

import buildcraft.lib.client.guide.GuideManager;
import buildcraft.lib.client.model.ModelHolderRegistry;
import buildcraft.lib.client.render.BCLibRenderTypes;
import buildcraft.lib.client.sprite.DyeReplaceSpriteSource;
import buildcraft.lib.client.sprite.FluidLerpSpriteSource;
import buildcraft.lib.misc.data.ModelVariableData;
import buildcraft.platform.Platform;
import buildcraft.platform.client.ClientPlatform;
import buildcraft.platform.client.ClientRegistrationSinks;

public class BCLibClient {
    public static void initClient(IEventBus modEventBus) {
        // Persist ledger open/closed state across MC restarts. Stored alongside the other
        // BuildCraft per-mod files, separate from the NeoForge ModConfigSpec configs (which
        // would have been overkill for a couple of booleans). Loads existing state synchronously
        // so the first GUI open already sees restored values.
        buildcraft.lib.gui.config.GuiConfigManager.init(
            Platform.loader().configDir()
                .resolve("buildcraft").resolve("gui_state.json")
        );

        // Register custom render pipelines (LED indicator pipeline, etc.). Must fire on the
        // mod event bus during the registration phase so the pipeline is known to the
        // rendering engine before any RenderType referencing it is queried.
        ClientRegistrationSinks sinks = ClientPlatform.sinks();
        //? if >=1.21.10 {
        sinks.renderPipeline(() -> BCLibRenderTypes.LED_PIPELINE);
        //?}
        // (1.21.1 has no RenderPipeline GPU API — the LED RenderType is built directly in
        // BCLibRenderTypes via the classic CompositeState, so there is nothing to register here.)

        // Register custom SpriteSource types, both replacing on-disk PNGs with
        // stitch-time synthesis: dye_replace synthesises the 16 dyed variants of
        // each fluid-pipe base sprite; fluid_lerp recolors the grayscale heat
        // base into every energy-fluid sprite.
        //? if >=1.21.10 {
        sinks.spriteSource(DyeReplaceSpriteSource.ID, DyeReplaceSpriteSource.MAP_CODEC);
        sinks.spriteSource(FluidLerpSpriteSource.ID, FluidLerpSpriteSource.MAP_CODEC);
        //?} else {
        /*DyeReplaceSpriteSource.TYPE = new net.minecraft.client.renderer.texture.atlas.SpriteSourceType(DyeReplaceSpriteSource.MAP_CODEC);
        sinks.spriteSourceType(DyeReplaceSpriteSource.ID, DyeReplaceSpriteSource.TYPE);
        FluidLerpSpriteSource.TYPE = new net.minecraft.client.renderer.texture.atlas.SpriteSourceType(FluidLerpSpriteSource.MAP_CODEC);
        sinks.spriteSourceType(FluidLerpSpriteSource.ID, FluidLerpSpriteSource.TYPE);*/
        //?}

        modEventBus.addListener(ModelEvent.BakingCompleted.class, event -> {
            // The atlases are live by now; every holder re-resolves its sprite on next use.
            buildcraft.lib.client.sprite.SpriteHolderRegistry.invalidateAll();
            java.util.HashSet<Identifier> sprites = new java.util.HashSet<>();
            ModelHolderRegistry.onTextureStitchPre(sprites);
            ModelHolderRegistry.onModelBake();
            ModelVariableData.onModelBake();
        });

        // Wire the guide into the client resource-manager reload pipeline so F3+T (and any
        // other client-side resource reload) regenerates guide content against the new
        // resource pack contents. Without this, GuideManager#onResourceManagerReload is
        // never invoked — so editing a guide .md file in the resource pack and pressing
        // F3+T appeared to be a no-op. The cast routes through the
        // ResourceManagerReloadListener single-method form (the sink takes the broader
        // PreparableReloadListener interface, which would otherwise be ambiguous).
        sinks.clientReloadListener(Identifier.fromNamespaceAndPath("buildcraftunofficial", "guide"),
                (ResourceManagerReloadListener) GuideManager.INSTANCE::onResourceManagerReload);

        // Catch BC-registry reloads (anything routed through ReloadableRegistryManager) so
        // the guide regenerates against the new entries. Guarded internally by isInReload
        // + reloadingRegistries-contains-GuideBookRegistry. Note: the vanilla `/reload`
        // command does NOT trigger this — it's a datapack reload (server-side), and our
        // guide content is resource-pack content (client-side). F3+T is the right gesture.
        NeoForge.EVENT_BUS.addListener(
            EventBuildCraftReload.FinishLoad.class,
            GuideManager.INSTANCE::onRegistryReload
        );

        // Feed the guide's recipe store from the server's recipe sync, then (re)build guide
        // content. This event fires on every world join (and again on datapack /reload), so it
        // replaces the old LoggingIn ensureLoaded() warm-up — same render thread, still hidden
        // by the "Loading world" screen, but now guaranteed to parse AFTER recipe data is
        // present, so recipe panels are never baked empty. Re-fires also re-bake panels, which
        // keeps an open guide correct across server switches and /reload (GuiGuide polls
        // reloadGeneration and rebuilds open pages). The synchronous ensureLoaded() in
        // openGuideScreen() stays as a fallback for any path that opens the book first.
        // The facade scan stays on LoggingIn (BCSiliconClient, HIGH priority), which always
        // precedes the recipe packets — ordering with the guide parse is preserved.
        // On >=1.21.10 the source is NeoForge's opt-in sync (requested server-side in BCCore
        // via OnDatapackSyncEvent.sendRecipes): real RecipeHolders over the wire on Neo<->Neo
        // connections; empty maps (no mod requested recipes) still refresh the store,
        // correctly degrading to no recipe panels. On 1.21.1 vanilla still syncs the full
        // recipe list to every client itself, and RecipesUpdatedEvent fires once it has been
        // received (same join + /reload lifecycle).
        //? if >=1.21.10 {
        NeoForge.EVENT_BUS.addListener(
            net.neoforged.neoforge.client.event.RecipesReceivedEvent.class,
            event -> {
                boolean firstSync = !buildcraft.lib.client.guide.parts.recipe.ClientGuideRecipeCache
                    .hasSeenSyncThisConnection();
                buildcraft.lib.client.guide.parts.recipe.ClientGuideRecipeCache
                    .setSynced(event.getRecipeMap().values());
                GuideManager.INSTANCE.onRecipeDataReceived(firstSync);
            }
        );
        //?} else {
        /*NeoForge.EVENT_BUS.addListener(
            net.neoforged.neoforge.client.event.RecipesUpdatedEvent.class,
            event -> {
                boolean firstSync = !buildcraft.lib.client.guide.parts.recipe.ClientGuideRecipeCache
                    .hasSeenSyncThisConnection();
                buildcraft.lib.client.guide.parts.recipe.ClientGuideRecipeCache
                    .setSynced(event.getRecipeManager().getRecipes());
                GuideManager.INSTANCE.onRecipeDataReceived(firstSync);
            }
        );*/
        //?}

        // Forget the previous server's recipes on disconnect (the next join re-fills the store;
        // recommended cleanup per RecipesReceivedEvent's own javadoc).
        NeoForge.EVENT_BUS.addListener(
            ClientPlayerNetworkEvent.LoggingOut.class,
            event -> buildcraft.lib.client.guide.parts.recipe.ClientGuideRecipeCache.clear()
        );

        // (BCDebugOverlay removed: it was a second, redundant F3-debug renderer that drew the
        // targeted tile's client info with a "[BC] " prefix — duplicating DebugOverlayRenderer, which
        // already renders the same info plus the server/client diff. Both were registered, so the F3
        // overlay showed the client info twice. DebugOverlayRenderer (RegisterGuiLayersEvent) is the keeper.)
        NeoForge.EVENT_BUS.register(BCTooltips.class);

        // Advanced-debug overlay: draws the in-world highlight for whatever tile the player last
        // right-clicked with the Debugger item. Client-authoritative, no networking.
        ClientPlatform.hooks().onLevelRender(buildcraft.lib.debug.AdvDebugRenderer::onRenderLevel);
    }

    public static void openGuideScreen(String bookName) {
        // Populate the GuideBookRegistry / GuidePageRegistry synchronously before the
        // GuiGuide constructor calls GuideBookRegistry.getBook(bookName). Without this,
        // the first guide-book open used to fall through to a synchronous reload triggered
        // from inside GuidePageContents.loadMainGui — at which point gui.book had already
        // been resolved to null (registry empty) and gui.bookData locked to BOOK_ALL_DATA.
        // Calling ensureLoaded() up here means by the time the constructor runs the registry
        // is populated, so getBook(bookName) returns the correct GuideBook for both the
        // gameplay and configuration guide-book items. Subsequent opens are no-ops.
        GuideManager.INSTANCE.ensureLoaded();
        //? if >=26.2 {
        net.minecraft.client.Minecraft.getInstance().setScreenAndShow(new buildcraft.lib.client.guide.GuiGuide(bookName));
        //?} else {
        /*net.minecraft.client.Minecraft.getInstance().setScreen(new buildcraft.lib.client.guide.GuiGuide(bookName));
        *///?}
    }
}
