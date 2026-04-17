package buildcraft.lib.client;

import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.common.NeoForge;

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
        NeoForge.EVENT_BUS.register(BCDebugOverlay.class);
        NeoForge.EVENT_BUS.register(BCTooltips.class);
    }

    public static void openGuideScreen(String bookName) {
        net.minecraft.client.Minecraft.getInstance().setScreen(new buildcraft.lib.client.guide.GuiGuide(bookName));
    }
}
