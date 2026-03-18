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
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.DyeColor;
import buildcraft.core.item.ItemPaintbrush_BC8;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.component.DataComponentType;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import java.util.function.Supplier;
import com.mojang.serialization.Codec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.ModFileScanData;
import org.objectweb.asm.Type;

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

    /** Typed component for paintbrush colour. Present = coloured brush, absent = clean brush. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<DyeColor>> BRUSH_COLOR =
            DATA_COMPONENTS.registerComponentType("brush_color",
                    builder -> builder.persistent(DyeColor.CODEC)
                                      .networkSynchronized(DyeColor.STREAM_CODEC));

    /** Typed component for paintbrush remaining uses. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> BRUSH_USES =
            DATA_COMPONENTS.registerComponentType("brush_uses",
                    builder -> builder.persistent(Codec.INT)
                                      .networkSynchronized(ByteBufCodecs.INT));

    public BCCore(IEventBus modEventBus, ModContainer modContainer) {
        INSTANCE = this;

        // ── JEI Plugin Annotation Injection ──────────────────────────────────
        // JEI's @JeiPlugin has RetentionPolicy.CLASS (not RUNTIME), so NeoForge's
        // dev sourceSet scanner doesn't capture it.  We manually inject the
        // annotation into our mod's scan data so ForgePluginFinder finds us.
        // In production JARs this is harmless — the annotation is already there.
        injectJeiPluginAnnotation(modContainer);
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

        // Initialize the fake player provider for modules that need FakePlayer instances
        buildcraft.api.core.BuildCraftAPI.fakePlayerProvider = new buildcraft.api.core.IFakePlayerProvider() {
            private static final com.mojang.authlib.GameProfile BC_PROFILE =
                new com.mojang.authlib.GameProfile(
                    java.util.UUID.nameUUIDFromBytes("BuildCraft".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                    "[BuildCraft]"
                );

            @Override
            public net.neoforged.neoforge.common.util.FakePlayer getBuildCraftPlayer(
                    net.minecraft.server.level.ServerLevel world) {
                return new net.neoforged.neoforge.common.util.FakePlayer(world, BC_PROFILE);
            }

            @Override
            public net.neoforged.neoforge.common.util.FakePlayer getFakePlayer(
                    net.minecraft.server.level.ServerLevel world, com.mojang.authlib.GameProfile profile) {
                return new net.neoforged.neoforge.common.util.FakePlayer(world, profile);
            }

            @Override
            public net.neoforged.neoforge.common.util.FakePlayer getFakePlayer(
                    net.minecraft.server.level.ServerLevel world, com.mojang.authlib.GameProfile profile,
                    net.minecraft.core.BlockPos pos) {
                net.neoforged.neoforge.common.util.FakePlayer player =
                    new net.neoforged.neoforge.common.util.FakePlayer(world, profile);
                player.setPos(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                return player;
            }
        };

        // Initialize the default crop handler for the CropManager API
        buildcraft.api.crops.CropManager.setDefaultHandler(buildcraft.lib.crops.CropHandlerPlantable.INSTANCE);

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
        buildcraft.core.block.VanillaPaintHandlers.fmlInit();
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
        }
    }

    /**
     * Ensures JEI's {@code @JeiPlugin} annotation on {@code BCCoreJeiPlugin} is present
     * in NeoForge's scan data. This is needed because the annotation has
     * {@code RetentionPolicy.CLASS} (not RUNTIME) so NeoForge's dev-mode sourceSet
     * scanner does not capture it. In production JARs this is a harmless no-op.
     */
    @SuppressWarnings("unchecked")
    private static void injectJeiPluginAnnotation(ModContainer modContainer) {
        try {
            Class.forName("mezz.jei.api.JeiPlugin");
            org.slf4j.LoggerFactory.getLogger("BuildCraft").info("[JEI] JEI detected, checking scan data for @JeiPlugin...");
        } catch (ClassNotFoundException e) {
            return;
        }

        try {
            var logger = org.slf4j.LoggerFactory.getLogger("BuildCraft");
            Type jeiPluginType = Type.getType("Lmezz/jei/api/JeiPlugin;");
            String pluginClassName = "buildcraft.core.compat.jei.BCCoreJeiPlugin";
            Type pluginClassType = Type.getObjectType(pluginClassName.replace('.', '/'));

            var allScanData = ModList.get().getAllScanData();
            logger.info("[JEI] Scan data entries: {}", allScanData.size());

            for (ModFileScanData scanData : allScanData) {
                // Dump all annotation memberNames for debugging
                var annotations = scanData.getAnnotations();
                boolean isBuildCraftCore = annotations.stream()
                        .anyMatch(a -> a.memberName().equals("buildcraft.core.BCCore"));
                if (!isBuildCraftCore) continue;

                logger.info("[JEI] Found buildcraftcore scan data with {} annotations", annotations.size());
                // Log the first few annotation types to understand what's in there
                annotations.stream().limit(5).forEach(a ->
                    logger.info("[JEI]   annotation: type={} member={}", a.annotationType(), a.memberName()));

                boolean alreadyPresent = annotations.stream()
                        .anyMatch(a -> a.annotationType().equals(jeiPluginType)
                                && a.memberName().equals(pluginClassName));
                if (alreadyPresent) {
                    logger.info("[JEI] @JeiPlugin already present in scan data — no injection needed");
                    return;
                }

                logger.info("[JEI] @JeiPlugin NOT in scan data — injecting...");

                var adClass = ModFileScanData.AnnotationData.class;
                var components = adClass.getRecordComponents();
                logger.info("[JEI] AnnotationData has {} components:", components.length);
                var ctorParamTypes = new Class<?>[components.length];
                for (int i = 0; i < components.length; i++) {
                    ctorParamTypes[i] = components[i].getType();
                    logger.info("[JEI]   [{}] {} : {}", i, components[i].getName(), components[i].getType().getSimpleName());
                }
                var ctor = adClass.getDeclaredConstructor(ctorParamTypes);

                Object[] args = new Object[components.length];
                for (int i = 0; i < components.length; i++) {
                    String name = components[i].getName();
                    switch (name) {
                        case "annotationType" -> args[i] = jeiPluginType;
                        case "clazz"          -> args[i] = pluginClassType;
                        case "memberName"     -> args[i] = pluginClassName;
                        case "annotationData" -> args[i] = java.util.Map.of();
                        default -> {
                            args[i] = getDefaultValue(components[i].getType());
                            logger.warn("[JEI]   Unknown component '{}', using default: {}", name, args[i]);
                        }
                    }
                }

                Object ad = ctor.newInstance(args);
                annotations.add((ModFileScanData.AnnotationData) ad);
                logger.info("[JEI] Successfully injected @JeiPlugin annotation data");
                return;
            }
            logger.warn("[JEI] Could not find buildcraftcore scan data!");
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger("BuildCraft").error("[JEI] Failed to inject @JeiPlugin annotation", e);
        }
    }

    /** Returns a sensible default for a given type (used as fallback for unknown record components). */
    private static Object getDefaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type.isPrimitive()) return 0;
        if (type == String.class) return "";
        if (java.util.Map.class.isAssignableFrom(type)) return java.util.Map.of();
        return null;
    }
}
