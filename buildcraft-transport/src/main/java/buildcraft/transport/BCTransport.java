package buildcraft.transport;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import buildcraft.api.mj.IMjConnector;
import buildcraft.api.mj.IMjReceiver;
import buildcraft.api.mj.MjAPI;
import buildcraft.transport.net.MessageMultiPipeItem;
import buildcraft.transport.net.PipeItemMessageQueue;
import buildcraft.transport.pipe.Pipe;

@Mod(BCTransport.MODID)
public class BCTransport {
    public static final String MODID = "buildcrafttransport";
    private static final Logger LOGGER = LoggerFactory.getLogger(BCTransport.class);

    public static BCTransport INSTANCE;

    public BCTransport(IEventBus modEventBus, ModContainer modContainer) {
        INSTANCE = this;

        // Register all deferred registries
        BCTransportBlocks.init(modEventBus);
        BCTransportItems.init(modEventBus);
        BCTransportBlockEntities.init(modEventBus);
        BCTransportMenuTypes.init(modEventBus);
        BCTransportCreativeTabs.init(modEventBus);

        // Register client-side extensions on the mod event bus
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            modEventBus.register(buildcraft.transport.client.BCTransportClient.class);
        }

        // Initialize pipe definitions and registry
        BCTransportPipes.preInit();

        // Register creative tab — LOW priority so transport items appear after core/factory/silicon items
        modEventBus.addListener(EventPriority.LOW, this::addCreativeTabItems);

        // Register NeoForge capabilities for pipe holders
        modEventBus.addListener(this::registerCapabilities);

        // Register network payloads
        modEventBus.addListener(this::registerPayloads);

        // Register server tick event for flushing pipe item packets
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class,
                event -> PipeItemMessageQueue.serverTick());

        LOGGER.info("BuildCraft Transport initialized");
    }

    private void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        // Non-pipe items go in the main BuildCraft tab
        if (event.getTabKey() == buildcraft.core.BCCoreCreativeTabs.MAIN_TAB_KEY) {
            event.accept(new ItemStack(BCTransportItems.FILTERED_BUFFER.get()));
            event.accept(new ItemStack(BCTransportItems.WATERPROOF.get()));
        }

        // All pipe items go in the dedicated Pipes tab
        if (event.getTabKey() == BCTransportCreativeTabs.PIPES_TAB_KEY) {
            // Structure pipe
            event.accept(BCTransportItems.PIPE_STRUCTURE.get());

            // Item transport pipes (1.12.2 ordering)
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

            // Fluid transport pipes
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

            // Power transport pipes
            event.accept(BCTransportItems.PIPE_WOOD_POWER.get());
            event.accept(BCTransportItems.PIPE_COBBLE_POWER.get());
            event.accept(BCTransportItems.PIPE_STONE_POWER.get());
            event.accept(BCTransportItems.PIPE_QUARTZ_POWER.get());
            event.accept(BCTransportItems.PIPE_IRON_POWER.get());
            event.accept(BCTransportItems.PIPE_GOLD_POWER.get());
            event.accept(BCTransportItems.PIPE_SANDSTONE_POWER.get());
            event.accept(BCTransportItems.PIPE_DIAMOND_POWER.get());
            event.accept(BCTransportItems.PIPE_DIAMOND_WOOD_POWER.get());
        }
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(
                MessageMultiPipeItem.TYPE,
                MessageMultiPipeItem.STREAM_CODEC,
                MessageMultiPipeItem::handle
        );
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        // MJ Receiver — needed for engines to discover wood pipes as power consumers
        event.registerBlockEntity(
            MjAPI.CAP_RECEIVER, BCTransportBlockEntities.PIPE_HOLDER.get(),
            (tile, side) -> {
                Pipe pipe = tile.getPipe();
                if (pipe == null || side == null) return null;
                // Check behaviour first (wood pipe), then flow (power pipe)
                IMjReceiver r = pipe.getBehaviour().getCapability(MjAPI.CAP_RECEIVER, side);
                if (r != null) return r;
                return pipe.getFlow().getCapability(MjAPI.CAP_RECEIVER, side);
            }
        );

        // MJ Connector — needed for power pipe connection checks
        event.registerBlockEntity(
            MjAPI.CAP_CONNECTOR, BCTransportBlockEntities.PIPE_HOLDER.get(),
            (tile, side) -> {
                Pipe pipe = tile.getPipe();
                if (pipe == null || side == null) return null;
                IMjConnector c = pipe.getBehaviour().getCapability(MjAPI.CAP_CONNECTOR, side);
                if (c != null) return c;
                return pipe.getFlow().getCapability(MjAPI.CAP_CONNECTOR, side);
            }
        );
    }
}
