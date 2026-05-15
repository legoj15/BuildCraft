package buildcraft.lib.client;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.RegisterSpriteSourcesEvent;
import net.neoforged.neoforge.common.NeoForge;

import buildcraft.api.registry.EventBuildCraftReload;

import buildcraft.lib.client.guide.GuideManager;
import buildcraft.lib.client.model.ModelHolderRegistry;
import buildcraft.lib.client.render.BCLibRenderTypes;
import buildcraft.lib.client.sprite.DyeReplaceSpriteSource;
import buildcraft.lib.misc.data.ModelVariableData;

public class BCLibClient {
    public static void initClient(IEventBus modEventBus) {
        // Persist ledger open/closed state across MC restarts. Stored alongside the other
        // BuildCraft per-mod files, separate from the NeoForge ModConfigSpec configs (which
        // would have been overkill for a couple of booleans). Loads existing state synchronously
        // so the first GUI open already sees restored values.
        buildcraft.lib.gui.config.GuiConfigManager.init(
            net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get()
                .resolve("buildcraft").resolve("gui_state.json")
        );

        // Register custom render pipelines (LED indicator pipeline, etc.). Must fire on the
        // mod event bus during the registration phase so the pipeline is known to the
        // rendering engine before any RenderType referencing it is queried.
        modEventBus.addListener(RegisterRenderPipelinesEvent.class, event ->
            event.registerPipeline(BCLibRenderTypes.LED_PIPELINE)
        );

        // Register custom SpriteSource types. dye_replace synthesises the 16 dyed
        // variants of each fluid-pipe base sprite at atlas stitch time, replacing
        // the on-disk *_dyed_<colour>.png files that used to ship with the mod.
        modEventBus.addListener(RegisterSpriteSourcesEvent.class, event ->
            event.register(DyeReplaceSpriteSource.ID, DyeReplaceSpriteSource.MAP_CODEC)
        );

        modEventBus.addListener(ModelEvent.BakingCompleted.class, event -> {
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
        // ResourceManagerReloadListener single-method form (the event takes the broader
        // PreparableReloadListener interface, which would otherwise be ambiguous).
        modEventBus.addListener(AddClientReloadListenersEvent.class, event ->
            event.addListener(
                Identifier.fromNamespaceAndPath("buildcraftunofficial", "guide"),
                (ResourceManagerReloadListener) GuideManager.INSTANCE::onResourceManagerReload
            )
        );

        // Drop PipeBaseModelGenStandard's sprite caches whenever the client resource manager
        // reloads. Three scenarios trigger this: F3+T (player-initiated reload), a resource
        // pack list change in the menu, AND vanilla MC's Options → Accessibility → High
        // Contrast toggle (which enables/disables the built-in `minecraft:high_contrast`
        // pack and calls Minecraft#reloadResourcePacks under the hood). The cached
        // TextureAtlasSprite references point at slots in the previous atlas stitch and
        // would render garbage UVs against the new atlas if not invalidated. Doing this
        // unconditionally — rather than gating on "did the cb state actually change" — is
        // both simpler and necessary: the atlas restitches even when only the high-contrast
        // pack toggles (no cb state change for ON/OFF users), and our cached sprites would
        // be just as stale in that case. The next chunk re-bake will populate the cache
        // afresh on demand.
        modEventBus.addListener(AddClientReloadListenersEvent.class, event ->
            event.addListener(
                Identifier.fromNamespaceAndPath("buildcraftunofficial", "pipe_sprite_cache"),
                (ResourceManagerReloadListener) rm ->
                    buildcraft.transport.client.model.PipeBaseModelGenStandard.clearSpriteCaches()
            )
        );

        // Catch BC-registry reloads (anything routed through ReloadableRegistryManager) so
        // the guide regenerates against the new entries. Guarded internally by isInReload
        // + reloadingRegistries-contains-GuideBookRegistry. Note: the vanilla `/reload`
        // command does NOT trigger this — it's a datapack reload (server-side), and our
        // guide content is resource-pack content (client-side). F3+T is the right gesture.
        NeoForge.EVENT_BUS.addListener(
            EventBuildCraftReload.FinishLoad.class,
            GuideManager.INSTANCE::onRegistryReload
        );

        NeoForge.EVENT_BUS.register(BCDebugOverlay.class);
        NeoForge.EVENT_BUS.register(BCTooltips.class);
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
        net.minecraft.client.Minecraft.getInstance().setScreen(new buildcraft.lib.client.guide.GuiGuide(bookName));
    }
}
