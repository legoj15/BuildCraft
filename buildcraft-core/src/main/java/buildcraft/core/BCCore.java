package buildcraft.core;

import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.bus.api.IEventBus;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.registries.Registries;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraft.world.item.DyeColor;
import buildcraft.core.item.ItemPaintbrush_BC8;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.component.DataComponentType;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import java.util.function.Supplier;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;

import buildcraft.lib.marker.MarkerCache;
import buildcraft.lib.net.MessageContainerPayload;
import buildcraft.lib.net.MessageDebugRequest;
import buildcraft.lib.net.MessageDebugResponse;
import buildcraft.lib.net.MessageMarker;
import buildcraft.core.marker.PathCache;
import buildcraft.core.marker.VolumeCache;
import buildcraft.lib.BCLibItems;
import buildcraft.lib.item.ItemGuide;

@Mod(BCCore.MODID)
public class BCCore {
    public static final String MODID = "buildcraftcore";
    public static BCCore INSTANCE = null;

    public static final DeferredRegister.DataComponents DATA_COMPONENTS = DeferredRegister
            .createDataComponents(Registries.DATA_COMPONENT_TYPE, BCCore.MODID);
    public static final Supplier<DataComponentType<SimpleFluidContent>> FLUID_CONTENT = DATA_COMPONENTS
            .registerComponentType("fluid_content", builder -> builder.persistent(SimpleFluidContent.CODEC)
                    .networkSynchronized(SimpleFluidContent.STREAM_CODEC));

    public BCCore(IEventBus modEventBus, ModContainer modContainer) {
        INSTANCE = this;
        // BCLibItems.enableGuide();
        // BCLibItems.enableDebugger();

        BCCoreItems.ITEMS.register(modEventBus);
        DATA_COMPONENTS.register(modEventBus);
        BCCoreBlocks.init(modEventBus);
        BCCoreBlockEntities.init(modEventBus);
        BCCoreFeatures.init(modEventBus);
        BCCoreCreativeTabs.init(modEventBus);

        modEventBus.addListener(this::preInit);
        modEventBus.addListener(this::init);
        modEventBus.addListener(this::postInit);
        modEventBus.addListener(this::registerCapabilities);
        modEventBus.addListener(this::buildCreativeTabContents);
        modEventBus.addListener(this::registerPayloads);

        // Register client-side rendering event on the GAME event bus (not mod bus)
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            NeoForge.EVENT_BUS.addListener(
                    net.neoforged.neoforge.client.event.RenderLevelStageEvent.AfterTranslucentBlocks.class,
                    event -> buildcraft.lib.client.render.MarkerRenderer.onRenderLevelStage(event)
            );
            // Register volume box rendering callback
            buildcraft.lib.client.render.MarkerRenderer.setVolumeBoxRenderCallback(
                    buildcraft.core.client.VolumeBoxRenderer::renderAll
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
            // F3 debug overlay: register the overlay layer on the mod bus
            modEventBus.addListener(
                    net.neoforged.neoforge.client.event.RegisterGuiLayersEvent.class,
                    event -> {
                        event.registerAboveAll(
                            net.minecraft.resources.Identifier.parse("buildcraftcore:debug_overlay"),
                            buildcraft.core.client.DebugOverlayRenderer::render
                        );
                    }
            );
        }
    }

    private void preInit(FMLCommonSetupEvent event) {
        // BCCoreConfig.preInit(cfgFolder);
        // CreativeTabBC tab = CreativeTabManager.createTab("buildcraft.main");

        MarkerCache.registerCache(VolumeCache.INSTANCE);
        MarkerCache.registerCache(PathCache.INSTANCE);

        BCCoreItems.preInit();
        BCCoreStatements.preInit();
        BCCoreRecipes.fmlPreInit();

        // BCCoreProxy.getProxy().fmlPreInit();
        // NetworkRegistry.INSTANCE.registerGuiHandler(INSTANCE,
        // BCCoreProxy.getProxy());
        // BCCoreConfig.saveConfigs();
    }

    private void init(FMLCommonSetupEvent event) {
        // BCCoreConfig.saveConfigs();
        // BCCoreProxy.getProxy().fmlInit();
    }

    private void postInit(net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent event) {
        // BCCoreConfig.saveConfigs();
        // BCCoreProxy.getProxy().fmlPostInit();
        // BCCoreConfig.postInit();
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(
                MessageMarker.TYPE,
                MessageMarker.STREAM_CODEC,
                MessageMarker::handle
        );
        registrar.playBidirectional(
                MessageContainerPayload.TYPE,
                MessageContainerPayload.STREAM_CODEC,
                MessageContainerPayload::handle,
                MessageContainerPayload::handle
        );
        // F3 debug overlay networking
        registrar.playToServer(
                MessageDebugRequest.TYPE,
                MessageDebugRequest.STREAM_CODEC,
                MessageDebugRequest::handle
        );
        registrar.playToClient(
                MessageDebugResponse.TYPE,
                MessageDebugResponse.STREAM_CODEC,
                MessageDebugResponse::handle
        );
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerItem(
                Capabilities.Fluid.ITEM,
                (stack, ctx) -> new net.neoforged.neoforge.transfer.fluid.ItemAccessFluidHandler(ctx,
                        FLUID_CONTENT.get(), buildcraft.core.item.ItemFragileFluidContainer.MAX_FLUID_HELD) {
                    @Override
                    public int insert(int index, net.neoforged.neoforge.transfer.fluid.FluidResource resource,
                            int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext transaction) {
                        return 0; // cannot fill!
                    }
                },
                BCCoreItems.FRAGILE_FLUID_CONTAINER);

        // MJ connector capability for engines
        event.registerBlockEntity(
            buildcraft.api.mj.MjAPI.CAP_CONNECTOR,
            BCCoreBlockEntities.ENGINE_REDSTONE.get(),
            (engine, direction) -> engine.getMjConnector()
        );
        event.registerBlockEntity(
            buildcraft.api.mj.MjAPI.CAP_CONNECTOR,
            BCCoreBlockEntities.ENGINE_CREATIVE.get(),
            (engine, direction) -> engine.getMjConnector()
        );
    }

    private void buildCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == BCCoreCreativeTabs.MAIN_TAB_KEY) {
            // Ordered to match 1.12.2 runtime sequence:
            // buildcraftlib loads before buildcraftcore (dependency),
            // then BCCoreBlocks.preInit() runs before BCCoreItems.preInit().

            // --- Lib items (registered by buildcraftlib, loads first) ---
            event.accept(BCLibItems.GUIDE);
            event.accept(ItemGuide.createForBook(BCLibItems.GUIDE.get(), "buildcraftlib:config"));
            event.accept(BCLibItems.GUIDE_NOTE);
            event.accept(BCLibItems.DEBUGGER);

            // --- Blocks (registered in BCCoreBlocks.preInit order) ---
            event.accept(BCCoreItems.SPRING_WATER);
            event.accept(BCCoreItems.SPRING_OIL);
            event.accept(BCCoreItems.DECORATED_DESTROY);
            event.accept(BCCoreItems.DECORATED_BLUEPRINT);
            event.accept(BCCoreItems.DECORATED_TEMPLATE);
            event.accept(BCCoreItems.DECORATED_PAPER);
            event.accept(BCCoreItems.DECORATED_LEATHER);
            event.accept(BCCoreItems.DECORATED_LASER);
            event.accept(BCCoreItems.MARKER_VOLUME);
            event.accept(BCCoreItems.MARKER_PATH);
            event.accept(BCCoreItems.ENGINE_REDSTONE);
            event.accept(BCCoreItems.ENGINE_CREATIVE);

            // --- Items (registered in BCCoreItems.preInit order) ---
            event.accept(BCCoreItems.WRENCH);
            event.accept(BCCoreItems.GEAR_WOOD);
            event.accept(BCCoreItems.GEAR_STONE);
            event.accept(BCCoreItems.GEAR_IRON);
            event.accept(BCCoreItems.GEAR_GOLD);
            event.accept(BCCoreItems.GEAR_DIAMOND);
            // Paintbrush: clean + 16 dye colours
            event.accept(ItemPaintbrush_BC8.createColoredStack(BCCoreItems.PAINTBRUSH.get(), null));
            for (DyeColor colour : DyeColor.values()) {
                event.accept(ItemPaintbrush_BC8.createColoredStack(BCCoreItems.PAINTBRUSH.get(), colour));
            }
            event.accept(BCCoreItems.LIST);
            event.accept(BCCoreItems.MAP_LOCATION);
            event.accept(BCCoreItems.MARKER_CONNECTOR);
            event.accept(BCCoreItems.VOLUME_BOX);
            event.accept(BCCoreItems.GOGGLES);
        }
    }
}
