package buildcraft.builders.client;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;

import buildcraft.builders.BCBuildersMenuTypes;
import buildcraft.builders.BCBuildersEntities;
import buildcraft.builders.BCBuildersEventDist;
import buildcraft.builders.gui.GuiArchitectTable;
import buildcraft.builders.gui.GuiElectronicLibrary;
import buildcraft.builders.gui.GuiFiller;
import buildcraft.builders.gui.GuiReplacer;
import buildcraft.builders.gui.GuiBuilder;

public class BCBuildersClient {
    public static void initClient(IEventBus modEventBus) {
        modEventBus.register(BCBuildersClient.class);
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.AfterTranslucentBlocks.class,
                event -> BCBuildersEventDist.INSTANCE.renderAllQuarries(event));
    }

    @SubscribeEvent
    public static void setupClient(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            buildcraft.lib.client.BCTooltips.addTooltip(buildcraft.builders.BCBuildersItems.FILLER.get(), "tip.block.filler");
            buildcraft.lib.client.BCTooltips.addTooltip(buildcraft.builders.BCBuildersItems.BUILDER.get(), "tip.block.builder");
            buildcraft.lib.client.BCTooltips.addTooltip(buildcraft.builders.BCBuildersItems.ARCHITECT.get(), "tip.block.architect");
            buildcraft.lib.client.BCTooltips.addTooltip(buildcraft.builders.BCBuildersItems.LIBRARY.get(), "tip.block.library");
        });
    }

    @SubscribeEvent
    public static void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(BCBuildersMenuTypes.FILLER.get(), GuiFiller::new);
        event.register(BCBuildersMenuTypes.BUILDER.get(), GuiBuilder::new);
        event.register(BCBuildersMenuTypes.ARCHITECT.get(), GuiArchitectTable::new);
        event.register(BCBuildersMenuTypes.LIBRARY.get(), GuiElectronicLibrary::new);
        event.register(BCBuildersMenuTypes.REPLACER.get(), GuiReplacer::new);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(BCBuildersEntities.QUARRY_RIG.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
    }
}
