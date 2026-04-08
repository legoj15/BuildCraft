package buildcraft.factory;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import buildcraft.api.mj.MjAPI;
import buildcraft.core.BCCore;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * BuildCraft Factory initializer. No longer a separate @Mod — called from BCCore.
 */
public class BCFactory {
    public static final String MODID = BCCore.MODID;
    private static final Logger LOGGER = LoggerFactory.getLogger(BCFactory.class);

    public static void init(IEventBus modEventBus) {
        // Register all deferred registries
        BCFactoryBlocks.init(modEventBus);
        BCFactoryItems.init(modEventBus);
        BCFactoryBlockEntities.init(modEventBus);
        BCFactoryMenuTypes.init(modEventBus);

        // Register client-side extensions on the mod event bus
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            buildcraft.factory.client.BCFactoryClient.initClient(modEventBus);
        }

        // Register capabilities and creative tab
        modEventBus.addListener((RegisterCapabilitiesEvent event) -> {
            registerCapabilities(event);
        });
        modEventBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            addCreativeTabItems(event);
        });

        LOGGER.info("BuildCraft Factory initialized");
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(MjAPI.CAP_RECEIVER, BCFactoryBlockEntities.AUTO_WORKBENCH_ITEMS.get(),
            (workbench, direction) -> workbench.getMjReceiver());
        event.registerBlockEntity(MjAPI.CAP_CONNECTOR, BCFactoryBlockEntities.AUTO_WORKBENCH_ITEMS.get(),
            (workbench, direction) -> workbench.getMjReceiver());
        event.registerBlockEntity(MjAPI.CAP_RECEIVER, BCFactoryBlockEntities.MINING_WELL.get(),
            (miner, direction) -> miner.getMjReceiver());
        event.registerBlockEntity(MjAPI.CAP_CONNECTOR, BCFactoryBlockEntities.MINING_WELL.get(),
            (miner, direction) -> miner.getMjReceiver());
        event.registerBlockEntity(MjAPI.CAP_RECEIVER, BCFactoryBlockEntities.PUMP.get(),
            (pump, direction) -> pump.getMjReceiver());
        event.registerBlockEntity(MjAPI.CAP_CONNECTOR, BCFactoryBlockEntities.PUMP.get(),
            (pump, direction) -> pump.getMjReceiver());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, BCFactoryBlockEntities.TANK.get(),
            (tank, direction) -> new buildcraft.factory.tile.TankColumnResourceHandler(tank));
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, BCFactoryBlockEntities.PUMP.get(),
            (pump, direction) -> new ResourceHandler<FluidResource>() {
                private final ResourceHandler<FluidResource> tank = pump.getTank();
                @Override public int size() { return tank.size(); }
                @Override public FluidResource getResource(int index) { return tank.getResource(index); }
                @Override public long getAmountAsLong(int index) { return tank.getAmountAsLong(index); }
                @Override public long getCapacityAsLong(int index, FluidResource resource) { return tank.getCapacityAsLong(index, resource); }
                @Override public boolean isValid(int index, FluidResource resource) { return tank.isValid(index, resource); }
                @Override public int extract(int index, FluidResource resource, int amount, TransactionContext tx) { return tank.extract(index, resource, amount, tx); }
                @Override public int insert(int index, FluidResource resource, int amount, TransactionContext tx) { return 0; }
            });
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, BCFactoryBlockEntities.FLOOD_GATE.get(),
            (floodGate, direction) -> floodGate.getTank());
        event.registerBlockEntity(MjAPI.CAP_RECEIVER, BCFactoryBlockEntities.CHUTE.get(),
            (chute, direction) -> chute.getMjReceiver());
        event.registerBlockEntity(MjAPI.CAP_CONNECTOR, BCFactoryBlockEntities.CHUTE.get(),
            (chute, direction) -> chute.getMjReceiver());
        event.registerBlockEntity(MjAPI.CAP_RECEIVER, BCFactoryBlockEntities.DISTILLER.get(),
            (distiller, direction) -> distiller.getMjReceiver());
        event.registerBlockEntity(MjAPI.CAP_CONNECTOR, BCFactoryBlockEntities.DISTILLER.get(),
            (distiller, direction) -> distiller.getMjReceiver());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, BCFactoryBlockEntities.DISTILLER.get(),
            (distiller, direction) -> {
                ResourceHandler<FluidResource> tank = distiller.getTankForSide(direction);
                if (tank == null) return null;
                if (tank == distiller.getTankIn()) {
                    return new ResourceHandler<FluidResource>() {
                        @Override public int size() { return tank.size(); }
                        @Override public FluidResource getResource(int index) { return tank.getResource(index); }
                        @Override public long getAmountAsLong(int index) { return tank.getAmountAsLong(index); }
                        @Override public long getCapacityAsLong(int index, FluidResource resource) { return tank.getCapacityAsLong(index, resource); }
                        @Override public boolean isValid(int index, FluidResource resource) { return tank.isValid(index, resource); }
                        @Override public int insert(int index, FluidResource resource, int amount, TransactionContext tx) { return tank.insert(index, resource, amount, tx); }
                        @Override public int extract(int index, FluidResource resource, int amount, TransactionContext tx) { return 0; }
                    };
                } else {
                    return new ResourceHandler<FluidResource>() {
                        @Override public int size() { return tank.size(); }
                        @Override public FluidResource getResource(int index) { return tank.getResource(index); }
                        @Override public long getAmountAsLong(int index) { return tank.getAmountAsLong(index); }
                        @Override public long getCapacityAsLong(int index, FluidResource resource) { return tank.getCapacityAsLong(index, resource); }
                        @Override public boolean isValid(int index, FluidResource resource) { return tank.isValid(index, resource); }
                        @Override public int extract(int index, FluidResource resource, int amount, TransactionContext tx) { return tank.extract(index, resource, amount, tx); }
                        @Override public int insert(int index, FluidResource resource, int amount, TransactionContext tx) { return 0; }
                    };
                }
            });
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, BCFactoryBlockEntities.HEAT_EXCHANGE.get(),
            (heatExchange, direction) -> heatExchange.getFluidTankForDirection(direction));
    }

    private static void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
    }
}
