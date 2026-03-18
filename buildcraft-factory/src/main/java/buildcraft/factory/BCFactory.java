package buildcraft.factory;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import buildcraft.api.mj.MjAPI;
import buildcraft.lib.misc.FluidTankResourceHandler;

@Mod(BCFactory.MODID)
public class BCFactory {
    public static final String MODID = "buildcraftfactory";
    private static final Logger LOGGER = LoggerFactory.getLogger(BCFactory.class);

    public static BCFactory INSTANCE;

    public BCFactory(IEventBus modEventBus, ModContainer modContainer) {
        INSTANCE = this;

        // Register all deferred registries
        BCFactoryBlocks.init(modEventBus);
        BCFactoryItems.init(modEventBus);
        BCFactoryBlockEntities.init(modEventBus);
        BCFactoryMenuTypes.init(modEventBus);

        // Register client-side extensions on the mod event bus
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            modEventBus.register(buildcraft.factory.client.BCFactoryClient.class);
        }

        // Register capabilities and creative tab
        modEventBus.addListener(this::registerCapabilities);
        modEventBus.addListener(this::addCreativeTabItems);

        LOGGER.info("BuildCraft Factory initialized");
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        // MJ receiver capability — allows engines to detect and send power to the auto workbench
        event.registerBlockEntity(
            MjAPI.CAP_RECEIVER,
            BCFactoryBlockEntities.AUTO_WORKBENCH_ITEMS.get(),
            (workbench, direction) -> workbench.getMjReceiver()
        );

        // MJ connector capability — allows visual connection checks
        event.registerBlockEntity(
            MjAPI.CAP_CONNECTOR,
            BCFactoryBlockEntities.AUTO_WORKBENCH_ITEMS.get(),
            (workbench, direction) -> workbench.getMjReceiver()
        );

        // MJ capabilities for the mining well
        event.registerBlockEntity(
            MjAPI.CAP_RECEIVER,
            BCFactoryBlockEntities.MINING_WELL.get(),
            (miner, direction) -> miner.getMjReceiver()
        );
        event.registerBlockEntity(
            MjAPI.CAP_CONNECTOR,
            BCFactoryBlockEntities.MINING_WELL.get(),
            (miner, direction) -> miner.getMjReceiver()
        );

        // MJ capabilities for the pump
        event.registerBlockEntity(
            MjAPI.CAP_RECEIVER,
            BCFactoryBlockEntities.PUMP.get(),
            (pump, direction) -> pump.getMjReceiver()
        );
        event.registerBlockEntity(
            MjAPI.CAP_CONNECTOR,
            BCFactoryBlockEntities.PUMP.get(),
            (pump, direction) -> pump.getMjReceiver()
        );

        // Fluid capabilities — expose tank column as ResourceHandler<FluidResource>
        event.registerBlockEntity(
            Capabilities.Fluid.BLOCK,
            BCFactoryBlockEntities.TANK.get(),
            (tank, direction) -> new buildcraft.factory.tile.TankColumnResourceHandler(tank)
        );
        event.registerBlockEntity(
            Capabilities.Fluid.BLOCK,
            BCFactoryBlockEntities.PUMP.get(),
            (pump, direction) -> new FluidTankResourceHandler(pump.getTank())
        );
        event.registerBlockEntity(
            Capabilities.Fluid.BLOCK,
            BCFactoryBlockEntities.FLOOD_GATE.get(),
            (floodGate, direction) -> new FluidTankResourceHandler(floodGate.getTank())
        );

        // MJ capabilities for the chute
        event.registerBlockEntity(
            MjAPI.CAP_RECEIVER,
            BCFactoryBlockEntities.CHUTE.get(),
            (chute, direction) -> chute.getMjReceiver()
        );
        event.registerBlockEntity(
            MjAPI.CAP_CONNECTOR,
            BCFactoryBlockEntities.CHUTE.get(),
            (chute, direction) -> chute.getMjReceiver()
        );

        // Item handler capability for the chute — not needed since the chute
        // manages its own item pickup/insertion logic directly

        // MJ capabilities for the distiller
        event.registerBlockEntity(
            MjAPI.CAP_RECEIVER,
            BCFactoryBlockEntities.DISTILLER.get(),
            (distiller, direction) -> distiller.getMjReceiver()
        );
        event.registerBlockEntity(
            MjAPI.CAP_CONNECTOR,
            BCFactoryBlockEntities.DISTILLER.get(),
            (distiller, direction) -> distiller.getMjReceiver()
        );

        // Fluid capabilities for the distiller (directional)
        // Horizontal sides → input tank, UP → gas out, DOWN → liquid out
        event.registerBlockEntity(
            Capabilities.Fluid.BLOCK,
            BCFactoryBlockEntities.DISTILLER.get(),
            (distiller, direction) -> {
                net.neoforged.neoforge.fluids.capability.templates.FluidTank tank = distiller.getTankForSide(direction);
                return tank != null ? new FluidTankResourceHandler(tank) : null;
            }
        );

        // Fluid capabilities for the heat exchanger (direction-specific, matching 1.12.2)
        // START: tankInput on DOWN, tankOutput on facing.getClockWise()
        // END: tankOutput on UP, tankInput on facing.getCounterClockWise()
        // MIDDLE / no section: no connections
        event.registerBlockEntity(
            Capabilities.Fluid.BLOCK,
            BCFactoryBlockEntities.HEAT_EXCHANGE.get(),
            (heatExchange, direction) -> {
                net.neoforged.neoforge.fluids.capability.templates.FluidTank tank = heatExchange.getFluidTankForDirection(direction);
                return tank != null ? new FluidTankResourceHandler(tank) : null;
            }
        );
    }

    private void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == buildcraft.core.BCCoreCreativeTabs.MAIN_TAB_KEY) {
            event.accept(new ItemStack(BCFactoryItems.AUTOWORKBENCH_ITEM.get()));
            event.accept(new ItemStack(BCFactoryItems.MINING_WELL.get()));
            event.accept(new ItemStack(BCFactoryItems.PUMP.get()));
            event.accept(new ItemStack(BCFactoryItems.FLOOD_GATE.get()));
            event.accept(new ItemStack(BCFactoryItems.TANK.get()));
            event.accept(new ItemStack(BCFactoryItems.CHUTE.get()));
            event.accept(new ItemStack(BCFactoryItems.DISTILLER.get()));
            event.accept(new ItemStack(BCFactoryItems.HEAT_EXCHANGE.get()));
            event.accept(new ItemStack(BCFactoryItems.WATER_GEL_SPAWN.get()));
            event.accept(new ItemStack(BCFactoryItems.GELLED_WATER.get()));
        }
    }
}
