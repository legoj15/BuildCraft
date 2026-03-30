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
    public static final String MODID = "buildcraftunofficial";
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

        // ─── Initialize all modules ─────────────────────────────────────────
        // Lib (must be first — other modules depend on it)
        buildcraft.lib.BCLib.init(modEventBus);

        // Core registries
        BCCoreItems.ITEMS.register(modEventBus);
        DATA_COMPONENTS.register(modEventBus);
        BCCoreBlocks.init(modEventBus);
        BCCoreBlockEntities.init(modEventBus);
        BCCoreFeatures.init(modEventBus);
        BCCoreCreativeTabs.init(modEventBus);
        BCCoreMenuTypes.init(modEventBus);

        // Module initializers (order matches 1.12.2 load order)
        buildcraft.transport.BCTransport.init(modEventBus);
        buildcraft.factory.BCFactory.init(modEventBus);
        buildcraft.energy.BCEnergy.init(modEventBus);
        buildcraft.silicon.BCSilicon.init(modEventBus);
        buildcraft.builders.BCBuilders.init(modEventBus);
        buildcraft.robotics.BCRobotics.init(modEventBus);

        // ─── Single unified config ──────────────────────────────────────────
        modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON, BCUnifiedConfig.SPEC);
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            modContainer.registerExtensionPoint(net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
                    net.neoforged.neoforge.client.gui.ConfigurationScreen::new);
        }

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

        // Vanilla Brush for cleaning pipes & painted blocks
        NeoForge.EVENT_BUS.addListener(
            net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock.class,
            interactEvent -> {
                if (interactEvent.getItemStack().is(net.minecraft.world.item.Items.BRUSH)) {
                    // Try to un-paint
                    net.minecraft.world.phys.Vec3 hitLoc = interactEvent.getHitVec().getLocation();
                    net.minecraft.world.InteractionResult result = buildcraft.api.blocks.CustomPaintHelper.INSTANCE.attemptPaintBlock(
                        interactEvent.getLevel(), interactEvent.getPos(), interactEvent.getLevel().getBlockState(interactEvent.getPos()),
                        hitLoc, interactEvent.getFace(), null);
                    
                    if (result == net.minecraft.world.InteractionResult.SUCCESS) {
                        if (!interactEvent.getLevel().isClientSide()) {
                            try {
                                // Find any sweeping sound using reflection to be safe
                                net.minecraft.sounds.SoundEvent sweep = null;
                                for (java.lang.reflect.Field f : net.minecraft.sounds.SoundEvents.class.getFields()) {
                                    if (f.getName().contains("BRUSH")) {
                                        sweep = (net.minecraft.sounds.SoundEvent) f.get(null);
                                        break;
                                    }
                                }
                                if (sweep != null) {
                                    interactEvent.getLevel().playSound(null, interactEvent.getPos(), sweep, net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);
                                }
                            } catch (Exception e) {}
                            buildcraft.lib.misc.ParticleUtil.showChangeColour(interactEvent.getLevel(), hitLoc, null);
                        }
                        interactEvent.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
                        interactEvent.setCanceled(true);
                    }
                }
            }
        );
    }

    private void preInit(FMLCommonSetupEvent event) {
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
    }

    private void init(FMLCommonSetupEvent event) {
        buildcraft.core.block.VanillaPaintHandlers.fmlInit();
    }

    private void postInit(net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent event) {
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

                    @Override
                    public int extract(int index, net.neoforged.neoforge.transfer.fluid.FluidResource resource, int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext transaction) {
                        java.util.Objects.checkIndex(index, size());
                        net.neoforged.neoforge.transfer.TransferPreconditions.checkNonEmptyNonNegative(resource, amount);

                        int accessAmount = itemAccess.getAmount();
                        if (accessAmount == 0) {
                            return 0;
                        }

                        net.neoforged.neoforge.transfer.item.ItemResource accessResource = itemAccess.getResource();
                        net.neoforged.neoforge.transfer.fluid.FluidResource currentResource = getResourceFrom(accessResource, index);

                        if (resource.equals(currentResource)) {
                            int currentAmountPerItem = getAmountFrom(accessResource, index);
                            int extractedPerItem = Math.min(amount / accessAmount, currentAmountPerItem);

                            if (extractedPerItem > 0) {
                                net.neoforged.neoforge.transfer.item.ItemResource emptiedResource = update(accessResource, index, resource, currentAmountPerItem - extractedPerItem);

                                if (!emptiedResource.isEmpty()) {
                                    return extractedPerItem * itemAccess.exchange(emptiedResource, accessAmount, transaction);
                                } else if (currentAmountPerItem - extractedPerItem == 0) {
                                    // Fully consume the item since it has no valid remaining state
                                    int extractedItems = itemAccess.extract(accessResource, accessAmount, transaction);
                                    if (extractedItems == accessAmount) {
                                        return extractedPerItem * accessAmount;
                                    }
                                }
                            }
                        }
                        return 0;
                    }

                    @Override
                    protected net.neoforged.neoforge.transfer.item.ItemResource update(
                            net.neoforged.neoforge.transfer.item.ItemResource accessResource,
                            int index,
                            net.neoforged.neoforge.transfer.fluid.FluidResource newResource,
                            int newAmount) {
                        if (newAmount == 0) {
                            return net.neoforged.neoforge.transfer.item.ItemResource.EMPTY;
                        }
                        return super.update(accessResource, index, newResource, newAmount);
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
    }

}
