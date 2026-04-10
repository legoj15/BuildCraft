package buildcraft.core.client;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.resources.Identifier;
import buildcraft.core.BCCoreBlockEntities;
import buildcraft.core.BCCoreModels;
import buildcraft.core.BCCoreMenuTypes;

public class BCCoreClient {
    public static void initClient(IEventBus modEventBus) {
        NeoForge.EVENT_BUS.addListener(
                net.neoforged.neoforge.client.event.RenderLevelStageEvent.AfterTranslucentBlocks.class,
                event -> buildcraft.lib.client.render.MarkerRenderer.onRenderLevelStage(event)
        );
        buildcraft.lib.client.render.MarkerRenderer.setVolumeBoxRenderCallback(
                buildcraft.core.client.VolumeBoxRenderer::renderAll
        );
        buildcraft.lib.client.render.MarkerRenderer.setHoldingConnectorCheck(
                player -> player.getMainHandItem().getItem() instanceof buildcraft.core.item.ItemMarkerConnector
                       || player.getOffhandItem().getItem() instanceof buildcraft.core.item.ItemMarkerConnector
        );
        modEventBus.addListener(
                net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers.class,
                event -> {
                    event.registerBlockEntityRenderer(BCCoreBlockEntities.ENGINE_REDSTONE.get(),
                            ctx -> new buildcraft.lib.client.render.tile.RenderEngine_BC8(
                                    BCCoreModels::getWoodEngineQuads));
                    event.registerBlockEntityRenderer(BCCoreBlockEntities.ENGINE_CREATIVE.get(),
                            ctx -> new buildcraft.lib.client.render.tile.RenderEngine_BC8(
                                    BCCoreModels::getCreativeEngineQuads));
                }
        );
        NeoForge.EVENT_BUS.addListener(
                net.neoforged.neoforge.client.event.ClientTickEvent.Post.class,
                event -> buildcraft.core.client.DebugOverlayHelper.onClientTick()
        );
        modEventBus.register(buildcraft.core.client.DynamicFluidShardModel.class);
        modEventBus.addListener(
                net.neoforged.neoforge.client.event.RegisterGuiLayersEvent.class,
                event -> {
                    event.registerAboveAll(
                        Identifier.parse("buildcraftcore:debug_overlay"),
                        buildcraft.core.client.DebugOverlayRenderer::render
                    );
                }
        );
        modEventBus.addListener(
                net.neoforged.neoforge.client.event.RegisterMenuScreensEvent.class,
                event -> event.register(BCCoreMenuTypes.LIST.get(),
                        buildcraft.core.list.GuiList::new)
        );
        NeoForge.EVENT_BUS.register(buildcraft.core.list.ListTooltipHandler.INSTANCE);
    }
}
