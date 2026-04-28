package buildcraft.lib.client;

import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.common.NeoForge;

import buildcraft.api.registry.EventBuildCraftReload;

import buildcraft.lib.client.guide.GuideManager;
import buildcraft.lib.client.model.ModelHolderRegistry;
import buildcraft.lib.misc.data.ModelVariableData;

public class BCLibClient {
    public static void initClient(IEventBus modEventBus) {
        modEventBus.addListener(ModelEvent.BakingCompleted.class, event -> {
            java.util.HashSet<Identifier> sprites = new java.util.HashSet<>();
            ModelHolderRegistry.onTextureStitchPre(sprites);
            ModelHolderRegistry.onModelBake();
            ModelVariableData.onModelBake();
        });

        // Catch data-pack reloads (`/reload`) so the guide regenerates against the new entries.
        // Guarded internally by isInReload + reloadingRegistries-contains-GuideBookRegistry.
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
