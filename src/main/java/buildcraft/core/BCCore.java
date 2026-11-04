/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package buildcraft.core;

import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.DyeColor;
import buildcraft.core.item.ItemPaintbrush_BC8;
import net.minecraft.core.component.DataComponentType;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import java.util.function.Supplier;
import com.mojang.serialization.Codec;


import buildcraft.lib.marker.MarkerCache;
import buildcraft.lib.net.MessageContainerPayload;
import buildcraft.lib.net.MessageDebugRequest;
import buildcraft.lib.net.MessageDebugResponse;
import buildcraft.lib.net.MessageMarker;
import buildcraft.core.marker.PathCache;
import buildcraft.core.marker.VolumeCache;
import buildcraft.core.properties.WorldPropertyIsDirt;
import buildcraft.core.properties.WorldPropertyIsFluidSource;
import buildcraft.core.properties.WorldPropertyIsHarvestable;
import buildcraft.core.properties.WorldPropertyIsLeaf;
import buildcraft.core.properties.WorldPropertyIsOre;
import buildcraft.core.properties.WorldPropertyIsReplaceable;
import buildcraft.core.properties.WorldPropertyIsShoveled;
import buildcraft.core.properties.WorldPropertyIsSoft;
import buildcraft.core.properties.WorldPropertyIsWood;
import buildcraft.lib.BCLibItems;
import buildcraft.lib.item.ItemGuide;
import buildcraft.platform.Platform;
import buildcraft.platform.network.BCNetwork;
import buildcraft.robotics.zone.ZoneMapRef;
import buildcraft.platform.registry.BCRegistrar;
import buildcraft.platform.registry.BCHolder;

@Mod(BCCore.MODID)
@SuppressWarnings("this-escape")
public class BCCore {
    public static final String MODID = "buildcraftunofficial";
    public static BCCore INSTANCE = null;

    public static final BCRegistrar.DataComponents DATA_COMPONENTS = BCRegistrar
            .createDataComponents(BCCore.MODID);
    public static final Supplier<DataComponentType<SimpleFluidContent>> FLUID_CONTENT = DATA_COMPONENTS
            .registerComponentType("fluid_content", builder -> builder.persistent(SimpleFluidContent.CODEC)
                    .networkSynchronized(SimpleFluidContent.STREAM_CODEC));

    /** Typed component for paintbrush colour. Present = coloured brush, absent = clean brush. */
    public static final BCHolder<DataComponentType<?>, DataComponentType<DyeColor>> BRUSH_COLOR =
            DATA_COMPONENTS.registerComponentType("brush_color",
                    builder -> builder.persistent(DyeColor.CODEC)
                                      .networkSynchronized(DyeColor.STREAM_CODEC));

    /** Typed component for paintbrush remaining uses. */
    public static final BCHolder<DataComponentType<?>, DataComponentType<Integer>> BRUSH_USES =
            DATA_COMPONENTS.registerComponentType("brush_uses",
                    builder -> builder.persistent(Codec.INT)
                                      .networkSynchronized(ByteBufCodecs.INT));

    /** The zone a written ZONE map location names (its id plus the store's token); the zone itself lives in
     *  {@link buildcraft.robotics.zone.ZoneMapSavedData} (vanilla filled-map style). */
    public static final BCHolder<DataComponentType<?>, DataComponentType<ZoneMapRef>> ZONE_MAP_ID =
            DATA_COMPONENTS.registerComponentType("zone_map_id",
                    builder -> builder.persistent(ZoneMapRef.CODEC)
                                      .networkSynchronized(ZoneMapRef.STREAM_CODEC));

    public BCCore(IEventBus modEventBus, ModContainer modContainer) {
        INSTANCE = this;

        // ─── Initialize each subsystem ──────────────────────────────────────
        // Lib must be first — other subsystems depend on its shared utilities.
        buildcraft.lib.BCLib.init(modEventBus);

        // Core registries
        BCCoreItems.ITEMS.subscribe();
        DATA_COMPONENTS.subscribe();
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

        // ─── Backwards-compat aliases for older world / inventory IDs ────────
        // Must run after every subsystem register is populated (LegacyAliases reads them) and
        // before RegisterEvent fires (constructor time qualifies).
        buildcraft.lib.registry.LegacyAliases.init(modEventBus);

        // ─── Single unified config ──────────────────────────────────────────
        // Both sides, never synced, in the legacy file name so a tuned buildcraftunofficial-common.toml is still the
        // file that gets read (the 26.3 FML 12 type split lives in the platform implementation).
        Platform.configRegistrar().register(buildcraft.platform.config.BCConfigType.LOCAL, BCUnifiedConfig.SPEC,
                MODID + "-common.toml");
        Platform.configHooks().onLoad(BCUnifiedConfig.SPEC, BCCore::applyConfig);
        Platform.configHooks().onReload(BCUnifiedConfig.SPEC, BCCore::applyConfig);
        if (Platform.loader().isPhysicalClient()) {
            BCCoreClient.init(modContainer, modEventBus);
        }

        Platform.lifecycle().onCommonSetup(this::preInit);
        Platform.lifecycle().onCommonSetup(this::init);
        // Last, after every listener the other mods registered while loading: see the method's javadoc.
        Platform.lifecycle().onLoadCompleteOnMain(buildcraft.lib.misc.MachineRefusals::registerRecorder);
        modEventBus.addListener(this::registerCapabilities);
        registerPayloads();

        // Block-break notifier: the break-event class differs across 26.1.x, so register it
        // through PlatformProtection#onBreak instead of @SubscribeEvent.
        buildcraft.lib.block.LocalBlockUpdateNotifier.registerBreakListener();

        // Vanilla Brush for cleaning pipes & painted blocks
        Platform.gameHooks().onRightClickBlock(
            (player, level, hand, hit, stack) -> {
                if (stack.is(net.minecraft.world.item.Items.BRUSH)) {
                    // Try to un-paint
                    net.minecraft.world.phys.Vec3 hitLoc = hit.getLocation();
                    net.minecraft.core.BlockPos pos = hit.getBlockPos();
                    net.minecraft.world.InteractionResult result = buildcraft.api.blocks.CustomPaintHelper.INSTANCE.attemptPaintBlock(
                        level, pos, level.getBlockState(pos), hitLoc, hit.getDirection(), null);

                    if (result == net.minecraft.world.InteractionResult.SUCCESS) {
                        if (!level.isClientSide()) {
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
                                    level.playSound(null, pos, sweep, net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);
                                }
                            } catch (Exception e) {}
                            buildcraft.lib.misc.ParticleUtil.showChangeColour(level, hitLoc, null);
                        }
                        return net.minecraft.world.InteractionResult.SUCCESS;
                    }
                }
                return null;
            }
        );

        // Volume Box server tick (drives editing-state updates and lock expiry)
        Platform.gameHooks().onLevelTickEnd(
            sl -> buildcraft.core.marker.volume.LevelSavedDataVolumeBoxes.get(sl).tick()
        );

        // Volume Box: send initial state to player on login
        Platform.gameHooks().onPlayerLoggedIn(
            sp -> {
                if (sp.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                    buildcraft.core.marker.volume.LevelSavedDataVolumeBoxes data =
                        buildcraft.core.marker.volume.LevelSavedDataVolumeBoxes.get(sl);
                    data.sendTo(sp);
                }
            }
        );

        // Volume Box: re-sync when the player switches dimension
        Platform.gameHooks().onPlayerChangedDimension(
            sp -> {
                if (sp.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                    buildcraft.core.marker.volume.LevelSavedDataVolumeBoxes
                        .get(sl)
                        .sendTo(sp);
                }
            }
        );

        // Guide book recipe panels: request NeoForge's opt-in recipe sync so clients get the
        // full crafting + smelting lists on join and after datapack /reload (vanilla stopped
        // syncing recipes to clients in 1.21.2, and the guide's old integrated-server lookup
        // was null in multiplayer). Received client-side via RecipesReceivedEvent — see
        // BCLibClient and ClientGuideRecipeCache. The 1.21.1 line still gets vanilla's own
        // full sync, so there the request does nothing.
        Platform.gameHooks().syncRecipeTypes(java.util.List.of(
            net.minecraft.world.item.crafting.RecipeType.CRAFTING,
            net.minecraft.world.item.crafting.RecipeType.SMELTING
        ));

        registerFormerSubscribers();
    }

    /**
     * The listeners of the classes that were {@code @EventBusSubscriber}s. FML registered those after this constructor
     * returned (FML 12 {@code FMLModContainer.constructMod}: the constructor, then {@code AutomaticEventSubscriber}),
     * so they are registered last here, keeping each event's listener order.
     */
    private static void registerFormerSubscribers() {
        buildcraft.platform.event.GameHooks hooks = Platform.gameHooks();
        buildcraft.lib.block.LocalBlockUpdateNotifier.registerHooks(hooks);
        buildcraft.lib.marker.MarkerCacheEvents.registerHooks(hooks);
        buildcraft.lib.misc.FakePlayerUtil.registerHooks(hooks);
        buildcraft.core.command.SoundTestCommand.registerHooks(hooks);
        buildcraft.core.item.ZombieWrenches.registerHooks(hooks);
        buildcraft.transport.stripes.PipeExtensionManager.registerHooks(hooks);
    }

    /**
     * Re-applies config-derived static state whenever the BuildCraft config is loaded or
     * reloaded. Currently threads {@link BCCoreConfig#miningMultiplier} into
     * {@link buildcraft.lib.misc.BlockUtil#miningMultiplier}, which drives the MJ-per-block
     * cost math for every mining machine (Quarry, Mining Well, Builder/Filler clear-mode).
     */
    private static void applyConfig() {
        buildcraft.lib.misc.BlockUtil.miningMultiplier = BCCoreConfig.miningMultiplier.get();
    }

    private void preInit() {
        // Fake players for every subsystem: cached per (level, profile) and reset on each fetch — see FakePlayerUtil.
        buildcraft.api.core.BuildCraftAPI.fakePlayerProvider = buildcraft.lib.misc.FakePlayerUtil.PROVIDER;

        // The CropManager handlers, as 1.12.2's BCLibRegistries set them: the plant-umbrella default, plus
        // sugar cane, which is not a plant block and so needs its own handler.
        buildcraft.api.crops.CropManager.setDefaultHandler(buildcraft.lib.crops.CropHandlerPlantable.INSTANCE);
        buildcraft.api.crops.CropManager.registerHandler(buildcraft.lib.crops.CropHandlerReeds.INSTANCE);
        // A refused planting click ends the crop dispatch, as it ends the stripes one (the api cannot see lib).
        buildcraft.api.crops.CropManager.setRefusalCounter(buildcraft.lib.misc.MachineRefusals::count);

        // The "soft" world property the robotics pathfinder queries via BuildCraftAPI.isSoftBlock. 7.1.x
        // registered this in BuildCraftCore's postInit (FMLPostInitializationEvent); the port kept the API
        // lookup but dropped the registration, so the first robot pathfind NPE'd on the null "soft" property.
        // Mirror upstream: fluids and plantables wholesale (1.21.1's plant umbrella is BushBlock; 1.21.10+
        // widened it to VegetationBlock), plus snow/vine/fire and the cane/chorus the old IPlantable check
        // covered, and the property itself.
        for (Block block : BuiltInRegistries.BLOCK) {
            //? if >=1.21.10 {
            if (block instanceof LiquidBlock || block instanceof net.minecraft.world.level.block.VegetationBlock) {
            //?} else {
            /*if (block instanceof LiquidBlock || block instanceof net.minecraft.world.level.block.BushBlock) {*/
            //?}
                buildcraft.api.core.BuildCraftAPI.softBlocks.add(block);
            }
        }
        buildcraft.api.core.BuildCraftAPI.softBlocks.add(Blocks.SNOW);
        buildcraft.api.core.BuildCraftAPI.softBlocks.add(Blocks.VINE);
        buildcraft.api.core.BuildCraftAPI.softBlocks.add(Blocks.FIRE);
        // 1.12.2's IPlantable covered these two; neither is in the BushBlock/VegetationBlock tree on any
        // node, so they need the explicit listing or robots pathfind around them.
        buildcraft.api.core.BuildCraftAPI.softBlocks.add(Blocks.SUGAR_CANE);
        buildcraft.api.core.BuildCraftAPI.softBlocks.add(Blocks.CHORUS_FLOWER);
        buildcraft.api.core.BuildCraftAPI.softBlocks.add(Blocks.CHORUS_PLANT);
        // Cactus was IPlantable upstream too (soft) but extends plain Block on every node — list it or
        // robots detour around every cactus. Cocoa is NOT listed: it was never IPlantable upstream
        // (checked 7.1.x and 8.0.x-1.12.2 — both register IPlantable wholesale, nothing more), so hard
        // cocoa here is parity, not a gap.
        buildcraft.api.core.BuildCraftAPI.softBlocks.add(Blocks.CACTUS);
        buildcraft.api.core.BuildCraftAPI.registerWorldProperty("soft", new WorldPropertyIsSoft());

        // The Ph5 board world properties. Red-baseline stubs (matches() == false) until the foundations
        // commit lands the real implementations; registering them now keeps the boards' property lookups
        // non-null so the phase's tests fail on assertions, not on a missing-property NPE. "ore" is one
        // instance per pickaxe tier, exactly as 7.1.x registered it.
        buildcraft.api.core.BuildCraftAPI.registerWorldProperty("wood", new WorldPropertyIsWood());
        buildcraft.api.core.BuildCraftAPI.registerWorldProperty("harvestable", new WorldPropertyIsHarvestable());
        for (int level = 0; level <= 3; level++) {
            buildcraft.api.core.BuildCraftAPI.registerWorldProperty("ore@hardness=" + level,
                    new WorldPropertyIsOre(level));
        }
        buildcraft.api.core.BuildCraftAPI.registerWorldProperty("dirt", new WorldPropertyIsDirt());
        buildcraft.api.core.BuildCraftAPI.registerWorldProperty("replaceable", new WorldPropertyIsReplaceable());
        buildcraft.api.core.BuildCraftAPI.registerWorldProperty("fluidSource", new WorldPropertyIsFluidSource());

        // The Ph9 board world properties: leaves for the leave cutter, shoveled ground for the
        // shovelman.
        buildcraft.api.core.BuildCraftAPI.registerWorldProperty("leaves", new WorldPropertyIsLeaf());
        buildcraft.api.core.BuildCraftAPI.registerWorldProperty("shoveled", new WorldPropertyIsShoveled());

        MarkerCache.registerCache(VolumeCache.INSTANCE);
        MarkerCache.registerCache(PathCache.INSTANCE);

        BCCoreItems.preInit();
        BCCoreStatements.preInit();
        BCCoreRecipes.fmlPreInit();
    }

    private void init() {
        buildcraft.core.block.VanillaPaintHandlers.fmlInit();
        buildcraft.lib.block.VanillaRotationHandlers.fmlInit();
        buildcraft.lib.list.VanillaListHandlers.register();
    }

    private static void registerPayloads() {
        final BCNetwork network = Platform.network();
        // Version 2: the marker count is a VarInt now (it was a short). NeoForge compares versions per payload at
        // login, so an older client or server is refused with a version message instead of misreading marker syncs.
        network.clientbound(
                MessageMarker.TYPE,
                MessageMarker.STREAM_CODEC,
                MessageMarker::handle,
                "2"
        );
        network.clientbound(
                buildcraft.core.marker.volume.MessageVolumeBoxes.TYPE,
                buildcraft.core.marker.volume.MessageVolumeBoxes.STREAM_CODEC,
                buildcraft.core.marker.volume.MessageVolumeBoxes::handle
        );
        // Volume-marker distance readout: the server names the marker and its own connection limit
        network.clientbound(
                buildcraft.core.marker.MessageMarkerTrack.TYPE,
                buildcraft.core.marker.MessageMarkerTrack.STREAM_CODEC,
                buildcraft.core.marker.MessageMarkerTrack::handle
        );
        network.bidirectional(
                MessageContainerPayload.TYPE,
                MessageContainerPayload.STREAM_CODEC,
                MessageContainerPayload::handle
        );
        // F3 debug overlay networking
        network.serverbound(
                MessageDebugRequest.TYPE,
                MessageDebugRequest.STREAM_CODEC,
                MessageDebugRequest::handle
        );
        network.clientbound(
                MessageDebugResponse.TYPE,
                MessageDebugResponse.STREAM_CODEC,
                MessageDebugResponse::handle
        );
        // Action-bar messages naming an energy figure: the server sends the raw amount, the client formats it
        network.clientbound(
                buildcraft.lib.net.MessageEnergyOverlay.TYPE,
                buildcraft.lib.net.MessageEnergyOverlay.STREAM_CODEC,
                buildcraft.lib.net.MessageEnergyOverlay::handle
        );
        // Snapshots / Blueprints
        network.serverbound(
                buildcraft.builders.snapshot.SnapshotRequestPayload.TYPE,
                buildcraft.builders.snapshot.SnapshotRequestPayload.STREAM_CODEC,
                buildcraft.builders.snapshot.SnapshotRequestPayload::handle
        );
        network.clientbound(
                buildcraft.builders.snapshot.SnapshotResponsePayload.TYPE,
                buildcraft.builders.snapshot.SnapshotResponsePayload.STREAM_CODEC,
                buildcraft.builders.snapshot.SnapshotResponsePayload::handle
        );
        // Architect Table live-area preview
        network.serverbound(
                buildcraft.builders.snapshot.ArchitectPreviewRequestPayload.TYPE,
                buildcraft.builders.snapshot.ArchitectPreviewRequestPayload.STREAM_CODEC,
                buildcraft.builders.snapshot.ArchitectPreviewRequestPayload::handle
        );
        network.clientbound(
                buildcraft.builders.snapshot.ArchitectPreviewResponsePayload.TYPE,
                buildcraft.builders.snapshot.ArchitectPreviewResponsePayload.STREAM_CODEC,
                buildcraft.builders.snapshot.ArchitectPreviewResponsePayload::handle
        );
        // Architect Table scan-cube digitizing effect
        network.clientbound(
                buildcraft.builders.snapshot.ArchitectScanPayload.TYPE,
                buildcraft.builders.snapshot.ArchitectScanPayload.STREAM_CODEC,
                buildcraft.builders.snapshot.ArchitectScanPayload::handle
        );
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        //? if >=1.21.10 {
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
        //?} else {
        /*// 1.21.1: the fragile container is an extract-only, consume-on-empty fluid item. The classic
        // FluidHandlerItemStack.Consumable already shatters the shard when drained (setContainerToEmpty
        // -> container.shrink(1)); overriding canFillFluidType to false makes it un-fillable (the modern
        // ItemAccessFluidHandler override returned 0 from insert for the same reason).
        event.registerItem(
                Capabilities.FluidHandler.ITEM,
                (stack, ctx) -> new net.neoforged.neoforge.fluids.capability.templates.FluidHandlerItemStack.Consumable(
                        FLUID_CONTENT, stack, buildcraft.core.item.ItemFragileFluidContainer.MAX_FLUID_HELD) {
                    @Override
                    public boolean canFillFluidType(net.neoforged.neoforge.fluids.FluidStack fluid) {
                        return false;
                    }
                },
                BCCoreItems.FRAGILE_FLUID_CONTAINER);*/
        //?}

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

        // The power tester's tile implements IMjReceiver on every side.
        event.registerBlockEntity(
            buildcraft.api.mj.MjAPI.CAP_RECEIVER,
            BCCoreBlockEntities.POWER_TESTER.get(),
            (tester, direction) -> tester
        );
    }

}
