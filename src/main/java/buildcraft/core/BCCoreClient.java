package buildcraft.core;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;

public class BCCoreClient {
    public static void init(ModContainer modContainer, IEventBus modEventBus) {
        modContainer.registerExtensionPoint(net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
                net.neoforged.neoforge.client.gui.ConfigurationScreen::new);

        NeoForge.EVENT_BUS.addListener(
                net.neoforged.neoforge.client.event.RenderLevelStageEvent.AfterTranslucentBlocks.class,
                event -> buildcraft.lib.client.render.MarkerRenderer.onRenderLevelStage(event)
        );
        // Register volume box rendering callback
        buildcraft.lib.client.render.MarkerRenderer.setVolumeBoxRenderCallback(
                buildcraft.core.client.VolumeBoxRenderer::renderAll
        );
        // Register held-connector check for preview beams
        buildcraft.lib.client.render.MarkerRenderer.setHoldingConnectorCheck(
                player -> player.getMainHandItem().getItem() instanceof buildcraft.core.item.ItemMarkerConnector
                       || player.getOffhandItem().getItem() instanceof buildcraft.core.item.ItemMarkerConnector
        );
        // Register engine BERs on the mod bus
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
        // F3 debug overlay: tick handler for polling IDebuggable + sending server requests
        NeoForge.EVENT_BUS.addListener(
                net.neoforged.neoforge.client.event.ClientTickEvent.Post.class,
                event -> buildcraft.core.client.DebugOverlayHelper.onClientTick()
        );
        // Register the fluid shard tint source for fragile fluid containers
        modEventBus.addListener(
                net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.ItemTintSources.class,
                event -> event.register(
                        net.minecraft.resources.Identifier.parse("buildcraftunofficial:fluid_shard_tint"),
                        buildcraft.core.client.FluidShardTintSource.MAP_CODEC
                )
        );
        // F3 debug overlay: register the overlay layer on the mod bus
        modEventBus.addListener(
                net.neoforged.neoforge.client.event.RegisterGuiLayersEvent.class,
                event -> {
                    event.registerAboveAll(
                        net.minecraft.resources.Identifier.parse("buildcraftunofficial:debug_overlay"),
                        buildcraft.core.client.DebugOverlayRenderer::render
                    );
                }
        );
        // Register List GUI screen
        modEventBus.addListener(
                net.neoforged.neoforge.client.event.RegisterMenuScreensEvent.class,
                event -> event.register(BCCoreMenuTypes.LIST.get(),
                        buildcraft.core.list.GuiList::new)
        );
        // Register static tooltips
        modEventBus.addListener(
                net.neoforged.fml.event.lifecycle.FMLClientSetupEvent.class,
                event -> event.enqueueWork(() -> {
                        buildcraft.lib.client.BCTooltips.addTooltip(BCCoreItems.ENGINE_CREATIVE.get(), "tip.block.engine_creative");
                        buildcraft.lib.client.BCTooltips.addTooltip(BCCoreItems.ENGINE_REDSTONE.get(), "tip.block.engine_redstone");
                })
        );
        // Register List tooltip handler (shows 'Matches' in tooltip while List GUI is open)
        NeoForge.EVENT_BUS.register(buildcraft.core.list.ListTooltipHandler.INSTANCE);

        // Custom particles for brushing pipes (suppresses the underlying block's vanilla wood/invisible particles)
        NeoForge.EVENT_BUS.addListener(
            net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Tick.class,
            event -> {
                if (event.getEntity().level().isClientSide() && event.getItem().is(net.minecraft.world.item.Items.BRUSH)) {
                    int maxDuration = event.getItem().getUseDuration(event.getEntity());
                    int used = maxDuration - event.getDuration();
                    // BrushItem spawns dust every 10 ticks
                    if (used > 0 && (used % 10) == 0) {
                        net.minecraft.world.phys.HitResult hit = net.minecraft.client.Minecraft.getInstance().hitResult;
                        if (hit instanceof net.minecraft.world.phys.BlockHitResult blockHit) {
                            net.minecraft.core.BlockPos pos = blockHit.getBlockPos();
                            net.minecraft.world.level.block.state.BlockState state = event.getEntity().level().getBlockState(pos);
                            if (state.getBlock() instanceof buildcraft.transport.block.BlockPipeHolder) {
                                buildcraft.transport.client.PipeHolderClientExtensions.INSTANCE.addHitEffects(
                                    state, event.getEntity().level(), blockHit, net.minecraft.client.Minecraft.getInstance().particleEngine
                                );
                            }
                        }
                    }
                }
            }
        );
    }
}
