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
import buildcraft.lib.misc.FluidTankResourceHandler;

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
            modEventBus.register(buildcraft.factory.client.BCFactoryClient.class);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                    net.neoforged.neoforge.client.event.RenderLevelStageEvent.AfterTranslucentBlocks.class,
                    event -> buildcraft.factory.client.render.TubeRenderer.onRenderLevel(event)
            );
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
            (pump, direction) -> new FluidTankResourceHandler(pump.getTank()) {
                @Override
                public int insert(int index, net.neoforged.neoforge.transfer.fluid.FluidResource resource,
                                  int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext tx) {
                    return 0;
                }
            });
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, BCFactoryBlockEntities.FLOOD_GATE.get(),
            (floodGate, direction) -> new FluidTankResourceHandler(floodGate.getTank()));
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
                net.neoforged.neoforge.fluids.capability.templates.FluidTank tank = distiller.getTankForSide(direction);
                if (tank == null) return null;
                if (tank == distiller.getTankIn()) {
                    return new FluidTankResourceHandler(tank) {
                        @Override
                        public int extract(int index, net.neoforged.neoforge.transfer.fluid.FluidResource resource,
                                           int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext tx) {
                            return 0;
                        }
                    };
                } else {
                    return new FluidTankResourceHandler(tank) {
                        @Override
                        public int insert(int index, net.neoforged.neoforge.transfer.fluid.FluidResource resource,
                                          int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext tx) {
                            return 0;
                        }
                    };
                }
            });
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, BCFactoryBlockEntities.HEAT_EXCHANGE.get(),
            (heatExchange, direction) -> {
                net.neoforged.neoforge.fluids.capability.templates.FluidTank tank = heatExchange.getFluidTankForDirection(direction);
                return tank != null ? new FluidTankResourceHandler(tank) : null;
            });
    }

    private static void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
    }
}
