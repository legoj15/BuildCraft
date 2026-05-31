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
import buildcraft.lib.mj.MjBatteryEnergyHandler;
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
        BCFactoryAttachments.init(modEventBus);

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

        // Notify TilePump when COMMON config reloads so running pumps re-evaluate
        // pumpsConsumeWater on the next tick instead of waiting for natural queue
        // exhaustion (which never happens if the pump's tank stays full).
        modEventBus.addListener((net.neoforged.fml.event.config.ModConfigEvent.Reloading event) -> {
            if (event.getConfig().getSpec() == buildcraft.core.BCUnifiedConfig.SPEC) {
                buildcraft.factory.tile.TilePump.onConfigReloaded();
            }
        });

        LOGGER.info("BuildCraft Factory initialized");
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(MjAPI.CAP_RECEIVER, BCFactoryBlockEntities.AUTO_WORKBENCH_ITEMS.get(),
            (workbench, direction) -> workbench.getMjReceiver());
        event.registerBlockEntity(MjAPI.CAP_CONNECTOR, BCFactoryBlockEntities.AUTO_WORKBENCH_ITEMS.get(),
            (workbench, direction) -> workbench.getMjReceiver());
        event.registerBlockEntity(Capabilities.Item.BLOCK, BCFactoryBlockEntities.AUTO_WORKBENCH_ITEMS.get(),
            (workbench, direction) -> workbench.getItemHandler(direction));
        event.registerBlockEntity(MjAPI.CAP_RECEIVER, BCFactoryBlockEntities.MINING_WELL.get(),
            (miner, direction) -> miner.getMjReceiver());
        event.registerBlockEntity(MjAPI.CAP_CONNECTOR, BCFactoryBlockEntities.MINING_WELL.get(),
            (miner, direction) -> miner.getMjReceiver());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, BCFactoryBlockEntities.MINING_WELL.get(),
            (miner, direction) -> MjBatteryEnergyHandler.createIfRfEnabled(miner.getBattery()));
        // Mining Well has no internal item buffer — it pushes mined drops straight into adjacent
        // pipes via InventoryUtil.addToBestAcceptor. Expose an empty item handler (like the Quarry)
        // purely so item pipes render a connection to it; in 1.12.2 the AutomaticProvidingTransactor
        // capability served that role.
        event.registerBlockEntity(Capabilities.Item.BLOCK, BCFactoryBlockEntities.MINING_WELL.get(),
            (miner, direction) -> net.neoforged.neoforge.transfer.EmptyResourceHandler.instance());
        event.registerBlockEntity(MjAPI.CAP_RECEIVER, BCFactoryBlockEntities.PUMP.get(),
            (pump, direction) -> pump.getMjReceiver());
        event.registerBlockEntity(MjAPI.CAP_CONNECTOR, BCFactoryBlockEntities.PUMP.get(),
            (pump, direction) -> pump.getMjReceiver());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, BCFactoryBlockEntities.PUMP.get(),
            (pump, direction) -> MjBatteryEnergyHandler.createIfRfEnabled(pump.getBattery()));
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
        event.registerBlockEntity(Capabilities.Energy.BLOCK, BCFactoryBlockEntities.CHUTE.get(),
            (chute, direction) -> MjBatteryEnergyHandler.createIfRfEnabled(chute.getBattery()));
        event.registerBlockEntity(Capabilities.Item.BLOCK, BCFactoryBlockEntities.CHUTE.get(),
            (chute, direction) -> chute.getItemHandler(direction));
        event.registerBlockEntity(MjAPI.CAP_RECEIVER, BCFactoryBlockEntities.DISTILLER.get(),
            (distiller, direction) -> distiller.getMjReceiver());
        event.registerBlockEntity(MjAPI.CAP_CONNECTOR, BCFactoryBlockEntities.DISTILLER.get(),
            (distiller, direction) -> distiller.getMjReceiver());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, BCFactoryBlockEntities.DISTILLER.get(),
            (distiller, direction) -> MjBatteryEnergyHandler.createIfRfEnabled(distiller.getBattery()));
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
