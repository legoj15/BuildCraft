package buildcraft.builders.client;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;

import buildcraft.builders.BCBuildersMenuTypes;
import buildcraft.builders.BCBuildersEntities;
import buildcraft.builders.BCBuildersEventDist;
import buildcraft.builders.client.tooltip.BlueprintTooltipOverlay;
import buildcraft.builders.client.tooltip.SchematicSingleTooltipOverlay;
import buildcraft.builders.gui.GuiArchitectTable;
import buildcraft.builders.gui.GuiElectronicLibrary;
import buildcraft.builders.gui.GuiFiller;
import buildcraft.builders.gui.GuiFillerPlanner;
import buildcraft.builders.gui.GuiReplacer;
import buildcraft.builders.gui.GuiBuilder;

public class BCBuildersClient {
    public static void initClient(IEventBus modEventBus) {
        modEventBus.register(BCBuildersClient.class);
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.AfterTranslucentBlocks.class,
                event -> {
                    BCBuildersEventDist.INSTANCE.renderAllQuarries(event);
                    BCBuildersEventDist.INSTANCE.renderAllFillers(event);
                    BCBuildersEventDist.INSTANCE.renderAllArchitectTables(event);
                    BCBuildersEventDist.INSTANCE.renderAllBuilders(event);
                });
        // Fade out architect "digitizing" cubes one tick at a time on the client.
        NeoForge.EVENT_BUS.addListener(
                net.neoforged.neoforge.client.event.ClientTickEvent.Post.class,
                event -> buildcraft.builders.snapshot.ClientArchitectScans.INSTANCE.tick());
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent.class,
                event -> {
                    BCBuildersEventDist.INSTANCE.renderAllFillersCustomGeometry(event);
                    BCBuildersEventDist.INSTANCE.renderAllBuildersCustomGeometry(event);
                });
        // Draws a second tooltip-shaped panel below Blueprint/Template tooltips with a rotating
        // 3D preview. Mirrors the 1.12.2 BCBuildersEventDist#onPostText pattern; the 1.12.2
        // PostText event was removed in modern NeoForge, so we hook Pre and run the layout
        // math ourselves. See BlueprintTooltipOverlay for the full explanation.
        NeoForge.EVENT_BUS.addListener(RenderTooltipEvent.Pre.class,
                BlueprintTooltipOverlay::onPreTooltip);
        // Same treatment for used single-block schematics: synthesize a 1×1×1 Blueprint and
        // render through the same PiP pipeline. Without this, two different "used" schematics
        // in the inventory look identical — a 1.12.2 UX gap we're closing here.
        NeoForge.EVENT_BUS.addListener(RenderTooltipEvent.Pre.class,
                SchematicSingleTooltipOverlay::onPreTooltip);
    }

    @SubscribeEvent
    public static void setupClient(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            buildcraft.lib.client.BCTooltips.addTooltip(buildcraft.builders.BCBuildersItems.FILLER.get(), "tip.block.filler");
            buildcraft.lib.client.BCTooltips.addTooltip(buildcraft.builders.BCBuildersItems.BUILDER.get(), "tip.block.builder");
            buildcraft.lib.client.BCTooltips.addTooltip(buildcraft.builders.BCBuildersItems.ARCHITECT.get(), "tip.block.architect");
            buildcraft.lib.client.BCTooltips.addTooltip(buildcraft.builders.BCBuildersItems.LIBRARY.get(), "tip.block.library");
            buildcraft.lib.client.BCTooltips.addTooltip(buildcraft.builders.BCBuildersItems.REPLACER.get(), "tip.block.replacer");
            buildcraft.lib.client.BCTooltips.addTooltip(buildcraft.builders.BCBuildersItems.QUARRY.get(), "tip.block.quarry");
        });
    }

    @SubscribeEvent
    public static void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(BCBuildersMenuTypes.FILLER.get(), GuiFiller::new);
        event.register(BCBuildersMenuTypes.BUILDER.get(), GuiBuilder::new);
        event.register(BCBuildersMenuTypes.ARCHITECT.get(), GuiArchitectTable::new);
        event.register(BCBuildersMenuTypes.LIBRARY.get(), GuiElectronicLibrary::new);
        event.register(BCBuildersMenuTypes.REPLACER.get(), GuiReplacer::new);
        event.register(BCBuildersMenuTypes.FILLER_PLANNER.get(), GuiFillerPlanner::new);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(BCBuildersEntities.QUARRY_RIG.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
    }

    /**
     * Register the PictureInPicture renderer that paints blueprint previews into an offscreen
     * texture as real 3D block models. Without this, any
     * {@code BlueprintPipRenderState} submitted by {@link buildcraft.builders.client.render.BlueprintRenderer}
     * would be silently dropped by the GuiRenderer (no matching renderer class).
     */
    @SubscribeEvent
    public static void registerPipRenderers(
            net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent event) {
        event.register(
                buildcraft.builders.client.render.pip.BlueprintPipRenderState.class,
                buildcraft.builders.client.render.pip.BlueprintPipRenderer::new);
    }
}
