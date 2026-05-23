package buildcraft.transport;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import buildcraft.api.core.EnumHandlerPriority;
import buildcraft.api.mj.IMjConnector;
import buildcraft.api.mj.IMjReceiver;
import buildcraft.api.mj.IMjRedstoneReceiver;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.transport.pipe.PipeApi;
import buildcraft.core.BCCore;
import buildcraft.lib.misc.CapUtil;
import buildcraft.transport.net.MessageMultiPipeItem;
import buildcraft.transport.net.MessagePipePayload;
import buildcraft.transport.net.PipeItemMessageQueue;
import buildcraft.transport.pipe.Pipe;
import buildcraft.transport.pipe.StripesRegistry;
import buildcraft.transport.stripes.*;

/**
 * BuildCraft Transport initializer. No longer a separate @Mod — called from BCCore.
 */
public class BCTransport {
    public static final String MODID = BCCore.MODID;
    private static final Logger LOGGER = LoggerFactory.getLogger(BCTransport.class);

    public static void init(IEventBus modEventBus) {
        // Initialize pipe and pluggable definitions BEFORE items register
        BCTransportPipes.preInit();
        BCTransportPlugs.preInit();
        BCTransportStatements.preInit();

        // Register all deferred registries
        BCTransportBlocks.init(modEventBus);
        BCTransportItems.init(modEventBus);
        BCTransportBlockEntities.init(modEventBus);
        BCTransportMenuTypes.init(modEventBus);
        BCTransportCreativeTabs.init(modEventBus);
        BCTransportAttachments.init(modEventBus);

        // Register client-side extensions on the mod event bus
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            buildcraft.transport.client.BCTransportClient.initClient(modEventBus);
        }

        // Register power/RF/fluid transfer data for pipes (deferred to commonSetup
        // because config values aren't available during mod construction)
        modEventBus.addListener((FMLCommonSetupEvent event) -> {
            event.enqueueWork(() -> {
                BCTransportConfig.registerPowerTransferData();
                BCTransportConfig.registerRfTransferData();
                BCTransportConfig.registerFluidTransferData();
            });
        });

        // Initialize stripes registry and handlers
        initStripesRegistry();

        // Register creative tab — LOW priority so transport items appear after core/factory/silicon items
        modEventBus.addListener(EventPriority.LOW, (BuildCreativeModeTabContentsEvent event) -> {
            addCreativeTabItems(event);
        });

        // Register NeoForge capabilities for pipe holders
        modEventBus.addListener((RegisterCapabilitiesEvent event) -> {
            registerCapabilities(event);
        });

        // Register network payloads
        modEventBus.addListener((RegisterPayloadHandlersEvent event) -> {
            registerPayloads(event);
        });

        // Register server tick event for flushing pipe item packets
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class,
                event -> {
                    PipeItemMessageQueue.serverTick();
                });

        // Register server-side wire system tick
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.tick.LevelTickEvent.Post.class,
                event -> {
                    if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                        buildcraft.transport.wire.SavedDataWireSystems.get(serverLevel).tick();
                    }
                });

        LOGGER.info("BuildCraft Transport initialized");
    }

    private static void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {

        // Pluggable items go in the dedicated Pluggables tab
        if (event.getTabKey() == BCTransportCreativeTabs.PLUGS_TAB_KEY) {
            event.accept(BCTransportItems.PLUG_BLOCKER.get());
            event.accept(BCTransportItems.PLUG_POWER_ADAPTOR.get());
            // Wire items
            for (net.minecraft.world.item.DyeColor color : net.minecraft.world.item.DyeColor.values()) {
                event.accept(BCTransportItems.WIRE_ITEMS.get(color).get());
            }
        }

        // All pipe items go in the dedicated Pipes tab
        if (event.getTabKey() == BCTransportCreativeTabs.PIPES_TAB_KEY) {
            event.accept(BCTransportItems.PIPE_STRUCTURE.get());
            event.accept(BCTransportItems.PIPE_WOOD_ITEM.get());
            event.accept(BCTransportItems.PIPE_COBBLE_ITEM.get());
            event.accept(BCTransportItems.PIPE_STONE_ITEM.get());
            event.accept(BCTransportItems.PIPE_QUARTZ_ITEM.get());
            event.accept(BCTransportItems.PIPE_IRON_ITEM.get());
            event.accept(BCTransportItems.PIPE_GOLD_ITEM.get());
            event.accept(BCTransportItems.PIPE_CLAY_ITEM.get());
            event.accept(BCTransportItems.PIPE_SANDSTONE_ITEM.get());
            event.accept(BCTransportItems.PIPE_VOID_ITEM.get());
            event.accept(BCTransportItems.PIPE_OBSIDIAN_ITEM.get());
            event.accept(BCTransportItems.PIPE_DIAMOND_ITEM.get());
            event.accept(BCTransportItems.PIPE_DIAMOND_WOOD_ITEM.get());
            event.accept(BCTransportItems.PIPE_LAPIS_ITEM.get());
            event.accept(BCTransportItems.PIPE_DAIZULI_ITEM.get());
            event.accept(BCTransportItems.PIPE_EMZULI_ITEM.get());
            event.accept(BCTransportItems.PIPE_STRIPES_ITEM.get());
            event.accept(BCTransportItems.PIPE_WOOD_FLUID.get());
            event.accept(BCTransportItems.PIPE_COBBLE_FLUID.get());
            event.accept(BCTransportItems.PIPE_STONE_FLUID.get());
            event.accept(BCTransportItems.PIPE_QUARTZ_FLUID.get());
            event.accept(BCTransportItems.PIPE_GOLD_FLUID.get());
            event.accept(BCTransportItems.PIPE_IRON_FLUID.get());
            event.accept(BCTransportItems.PIPE_CLAY_FLUID.get());
            event.accept(BCTransportItems.PIPE_SANDSTONE_FLUID.get());
            event.accept(BCTransportItems.PIPE_VOID_FLUID.get());
            event.accept(BCTransportItems.PIPE_DIAMOND_FLUID.get());
            event.accept(BCTransportItems.PIPE_DIAMOND_WOOD_FLUID.get());
            event.accept(BCTransportItems.PIPE_WOOD_POWER.get());
            event.accept(BCTransportItems.PIPE_COBBLE_POWER.get());
            event.accept(BCTransportItems.PIPE_STONE_POWER.get());
            event.accept(BCTransportItems.PIPE_QUARTZ_POWER.get());
            event.accept(BCTransportItems.PIPE_IRON_POWER.get());
            event.accept(BCTransportItems.PIPE_GOLD_POWER.get());
            event.accept(BCTransportItems.PIPE_SANDSTONE_POWER.get());
            event.accept(BCTransportItems.PIPE_DIAMOND_POWER.get());
            event.accept(BCTransportItems.PIPE_DIAMOND_WOOD_POWER.get());
            event.accept(BCTransportItems.PIPE_WOOD_RF.get());
            event.accept(BCTransportItems.PIPE_COBBLE_RF.get());
            event.accept(BCTransportItems.PIPE_STONE_RF.get());
            event.accept(BCTransportItems.PIPE_QUARTZ_RF.get());
            event.accept(BCTransportItems.PIPE_IRON_RF.get());
            event.accept(BCTransportItems.PIPE_GOLD_RF.get());
            event.accept(BCTransportItems.PIPE_SANDSTONE_RF.get());
            event.accept(BCTransportItems.PIPE_DIAMOND_RF.get());
            event.accept(BCTransportItems.PIPE_DIAMOND_WOOD_RF.get());
        }
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(
                MessageMultiPipeItem.TYPE,
                MessageMultiPipeItem.STREAM_CODEC,
                MessageMultiPipeItem::handle
        );
        registrar.playToClient(
                MessagePipePayload.TYPE,
                MessagePipePayload.STREAM_CODEC,
                MessagePipePayload::handle
        );
        registrar.playToClient(
                buildcraft.transport.wire.PayloadWireSystems.TYPE,
                buildcraft.transport.wire.PayloadWireSystems.STREAM_CODEC,
                buildcraft.transport.wire.PayloadWireSystems::handle
        );
        registrar.playToClient(
                buildcraft.transport.wire.PayloadWireSystemsPowered.TYPE,
                buildcraft.transport.wire.PayloadWireSystemsPowered.STREAM_CODEC,
                buildcraft.transport.wire.PayloadWireSystemsPowered::handle
        );
        registrar.playToClient(
                buildcraft.transport.net.MessagePipeLandingEffect.TYPE,
                buildcraft.transport.net.MessagePipeLandingEffect.STREAM_CODEC,
                buildcraft.transport.net.MessagePipeLandingEffect::handle
        );
    }

    @SuppressWarnings("unchecked")
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
            MjAPI.CAP_RECEIVER, BCTransportBlockEntities.PIPE_HOLDER.get(),
            (tile, side) -> {
                Pipe pipe = tile.getPipe();
                if (pipe == null || side == null) return null;
                buildcraft.api.transport.pluggable.PipePluggable plug = tile.getPluggable(side);
                if (plug != null) {
                    IMjReceiver r = plug.getCapability(MjAPI.CAP_RECEIVER);
                    if (r != null) return r;
                    if (plug.isBlocking()) return null;
                }
                IMjReceiver r = pipe.getBehaviour().getCapability(MjAPI.CAP_RECEIVER, side);
                if (r != null) return r;
                return pipe.getFlow().getCapability(MjAPI.CAP_RECEIVER, side);
            }
        );

        event.registerBlockEntity(
            MjAPI.CAP_REDSTONE_RECEIVER, BCTransportBlockEntities.PIPE_HOLDER.get(),
            (tile, side) -> {
                Pipe pipe = tile.getPipe();
                if (pipe == null || side == null) return null;
                buildcraft.api.transport.pluggable.PipePluggable plug = tile.getPluggable(side);
                if (plug != null) {
                    IMjRedstoneReceiver r = plug.getCapability(MjAPI.CAP_REDSTONE_RECEIVER);
                    if (r != null) return r;
                    if (plug.isBlocking()) return null;
                }
                IMjRedstoneReceiver r = pipe.getBehaviour().getCapability(MjAPI.CAP_REDSTONE_RECEIVER, side);
                if (r != null) return r;
                return pipe.getFlow().getCapability(MjAPI.CAP_REDSTONE_RECEIVER, side);
            }
        );

        event.registerBlockEntity(
            MjAPI.CAP_CONNECTOR, BCTransportBlockEntities.PIPE_HOLDER.get(),
            (tile, side) -> {
                Pipe pipe = tile.getPipe();
                if (pipe == null || side == null) return null;
                buildcraft.api.transport.pluggable.PipePluggable plug = tile.getPluggable(side);
                if (plug != null) {
                    IMjConnector c = plug.getCapability(MjAPI.CAP_CONNECTOR);
                    if (c != null) return c;
                    if (plug.isBlocking()) return null;
                }
                IMjConnector c = pipe.getBehaviour().getCapability(MjAPI.CAP_CONNECTOR, side);
                if (c != null) return c;
                return pipe.getFlow().getCapability(MjAPI.CAP_CONNECTOR, side);
            }
        );

        event.registerBlockEntity(
            Capabilities.Fluid.BLOCK, BCTransportBlockEntities.PIPE_HOLDER.get(),
            (tile, side) -> {
                Pipe pipe = tile.getPipe();
                if (pipe == null || side == null) return null;
                buildcraft.api.transport.pluggable.PipePluggable plug = tile.getPluggable(side);
                if (plug != null && plug.isBlocking()) return null;
                return pipe.getFlow().getCapability(CapUtil.CAP_FLUIDS, side);
            }
        );

        event.registerBlockEntity(
            net.neoforged.neoforge.capabilities.Capabilities.Energy.BLOCK, BCTransportBlockEntities.PIPE_HOLDER.get(),
            (tile, side) -> {
                if (side != null) {
                    buildcraft.api.transport.pluggable.PipePluggable plug = tile.getPluggable(side);
                    if (plug != null && plug.isBlocking()) return null;
                }
                
                buildcraft.api.transport.pipe.IPipe pipe = tile.getPipe();
                if (pipe != null && pipe.getFlow() != null) {
                    return pipe.getFlow().getCapability(net.neoforged.neoforge.capabilities.Capabilities.Energy.BLOCK, side);
                }
                return null;
            }
        );

        event.registerBlockEntity(
            net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK, 
            BCTransportBlockEntities.FILTERED_BUFFER.get(),
            (tile, side) -> tile.getItemHandler(side)
        );
    }

    private static void initStripesRegistry() {
        PipeApi.stripeRegistry = StripesRegistry.INSTANCE;

        PipeApi.stripeRegistry.addHandler(StripesHandlerPlant.INSTANCE);
        PipeApi.stripeRegistry.addHandler(StripesHandlerShears.INSTANCE);
        PipeApi.stripeRegistry.addHandler(new StripesHandlerPipes());
        PipeApi.stripeRegistry.addHandler(StripesHandlerEntityInteract.INSTANCE, EnumHandlerPriority.LOW);
        PipeApi.stripeRegistry.addHandler(StripesHandlerHoe.INSTANCE);
        PipeApi.stripeRegistry.addHandler(StripesHandlerDispenser.INSTANCE, EnumHandlerPriority.LOW);
        PipeApi.stripeRegistry.addHandler(StripesHandlerPlaceBlock.INSTANCE, EnumHandlerPriority.LOW);
        PipeApi.stripeRegistry.addHandler(StripesHandlerUse.INSTANCE, EnumHandlerPriority.LOW);

        PipeApi.stripeRegistry.addHandler(StripesHandlerMinecartDestroy.INSTANCE);
    }
}
