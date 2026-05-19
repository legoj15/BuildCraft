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

        // ─── Initialize each subsystem ──────────────────────────────────────
        // Lib must be first — other subsystems depend on its shared utilities.
        buildcraft.lib.BCLib.init(modEventBus);

        // Core registries
        BCCoreItems.ITEMS.register(modEventBus);
        DATA_COMPONENTS.register(modEventBus);
        BCCoreBlocks.init(modEventBus);
        BCCoreBlockEntities.init(modEventBus);
        BCCoreFeatures.init(modEventBus);
        BCCoreCreativeTabs.init(modEventBus);
        BCCoreMenuTypes.init(modEventBus);

        // Per-subsystem registration helpers (order matches 1.12.2 load order)
        buildcraft.transport.BCTransport.init(modEventBus);
        buildcraft.factory.BCFactory.init(modEventBus);
        buildcraft.energy.BCEnergy.init(modEventBus);
        buildcraft.silicon.BCSilicon.init(modEventBus);
        buildcraft.builders.BCBuilders.init(modEventBus);
        buildcraft.robotics.BCRobotics.init(modEventBus);

        // ─── Single unified config ──────────────────────────────────────────
        modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON, BCUnifiedConfig.SPEC);
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            BCCoreClient.init(modContainer, modEventBus);
        }

        modEventBus.addListener(this::preInit);
        modEventBus.addListener(this::init);
        modEventBus.addListener(this::postInit);
        modEventBus.addListener(this::registerCapabilities);
        modEventBus.addListener(this::buildCreativeTabContents);
        modEventBus.addListener(this::registerPayloads);



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

        // Volume Box server tick (drives editing-state updates and lock expiry)
        NeoForge.EVENT_BUS.addListener(
            net.neoforged.neoforge.event.tick.LevelTickEvent.Post.class,
            tickEvent -> {
                if (tickEvent.getLevel() instanceof net.minecraft.server.level.ServerLevel sl) {
                    buildcraft.core.marker.volume.LevelSavedDataVolumeBoxes.get(sl).tick();
                }
            }
        );

        // Volume Box: send initial state to player on login, resume any paused edits
        NeoForge.EVENT_BUS.addListener(
            net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent.class,
            loginEvent -> {
                if (loginEvent.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp
                        && sp.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                    buildcraft.core.marker.volume.LevelSavedDataVolumeBoxes data =
                        buildcraft.core.marker.volume.LevelSavedDataVolumeBoxes.get(sl);
                    data.sendTo(sp);
                    data.volumeBoxes.stream()
                        .filter(vb -> vb.isPausedEditingBy(sp))
                        .forEach(buildcraft.core.marker.volume.VolumeBox::resumeEditing);
                }
            }
        );

        // Volume Box: re-sync when the player switches dimension
        NeoForge.EVENT_BUS.addListener(
            net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent.class,
            dimEvent -> {
                if (dimEvent.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp
                        && sp.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                    buildcraft.core.marker.volume.LevelSavedDataVolumeBoxes
                        .get(sl)
                        .sendTo(sp);
                }
            }
        );
    }

    private void preInit(FMLCommonSetupEvent event) {
        // Initialize the fake player provider for subsystems that need FakePlayer instances
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
        buildcraft.lib.block.VanillaRotationHandlers.fmlInit();
        buildcraft.lib.list.VanillaListHandlers.register();
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
        registrar.playToClient(
                buildcraft.core.marker.volume.MessageVolumeBoxes.TYPE,
                buildcraft.core.marker.volume.MessageVolumeBoxes.STREAM_CODEC,
                buildcraft.core.marker.volume.MessageVolumeBoxes::handle
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
        // Snapshots / Blueprints
        registrar.playToServer(
                buildcraft.builders.snapshot.SnapshotRequestPayload.TYPE,
                buildcraft.builders.snapshot.SnapshotRequestPayload.STREAM_CODEC,
                buildcraft.builders.snapshot.SnapshotRequestPayload::handle
        );
        registrar.playToClient(
                buildcraft.builders.snapshot.SnapshotResponsePayload.TYPE,
                buildcraft.builders.snapshot.SnapshotResponsePayload.STREAM_CODEC,
                buildcraft.builders.snapshot.SnapshotResponsePayload::handle
        );
        // Architect Table live-area preview
        registrar.playToServer(
                buildcraft.builders.snapshot.ArchitectPreviewRequestPayload.TYPE,
                buildcraft.builders.snapshot.ArchitectPreviewRequestPayload.STREAM_CODEC,
                buildcraft.builders.snapshot.ArchitectPreviewRequestPayload::handle
        );
        registrar.playToClient(
                buildcraft.builders.snapshot.ArchitectPreviewResponsePayload.TYPE,
                buildcraft.builders.snapshot.ArchitectPreviewResponsePayload.STREAM_CODEC,
                buildcraft.builders.snapshot.ArchitectPreviewResponsePayload::handle
        );
        // Architect Table scan-cube digitizing effect
        registrar.playToClient(
                buildcraft.builders.snapshot.ArchitectScanPayload.TYPE,
                buildcraft.builders.snapshot.ArchitectScanPayload.STREAM_CODEC,
                buildcraft.builders.snapshot.ArchitectScanPayload::handle
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
