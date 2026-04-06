package buildcraft.energy;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import buildcraft.api.enums.EnumSpring;
import buildcraft.core.BCCore;
import buildcraft.energy.client.BCEnergyFluidsClient;
import buildcraft.energy.tile.TileSpringOil;
import buildcraft.lib.misc.MultiTankResourceHandler;

/**
 * BuildCraft Energy initializer. No longer a separate @Mod — called from BCCore.
 */
public class BCEnergy {
    public static final String MODID = BCCore.MODID;
    private static final Logger LOGGER = LoggerFactory.getLogger(BCEnergy.class);

    public static void init(IEventBus modEventBus) {
        // Register all deferred registries
        BCEnergyFluids.init(modEventBus);
        BCEnergyBlocks.init(modEventBus);
        BCEnergyItems.init(modEventBus);
        BCEnergyBlockEntities.init(modEventBus);
        BCEnergyMenuTypes.init(modEventBus);

        // Register client-side extensions on the mod event bus
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            modEventBus.register(BCEnergyFluidsClient.class);
            modEventBus.register(buildcraft.energy.client.BCEnergyClient.class);
        }

        // Creative tab
        modEventBus.addListener(net.neoforged.bus.api.EventPriority.LOWEST, (BuildCreativeModeTabContentsEvent event) -> {
            addCreativeTabItems(event);
        });

        // Register NeoForge capabilities for engines
        modEventBus.addListener((RegisterCapabilitiesEvent event) -> {
            registerCapabilities(event);
        });

        // Setup event for things that need registries to be frozen
        modEventBus.addListener((FMLCommonSetupEvent event) -> {
            commonSetup(event);
        });

        // MC 26.1: Register recipes on ServerAboutToStartEvent
        NeoForge.EVENT_BUS.addListener((ServerAboutToStartEvent event) -> {
            onServerAboutToStart(event);
        });

        LOGGER.info("BuildCraft Energy initialized");
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
            Capabilities.Fluid.BLOCK,
            BCEnergyBlockEntities.ENGINE_IRON.get(),
            (engine, direction) -> {
                if (direction == engine.getOrientation()) return null;
                return new MultiTankResourceHandler(
                    engine.tankFuel, engine.tankCoolant, engine.tankResidue
                ) {
                    @Override
                    public int extract(int index, net.neoforged.neoforge.transfer.fluid.FluidResource resource,
                            int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext transaction) {
                        if (index != 2) return 0;
                        return super.extract(index, resource, amount, transaction);
                    }
                };
            }
        );

        event.registerBlockEntity(
            Capabilities.Item.BLOCK,
            BCEnergyBlockEntities.ENGINE_STONE.get(),
            (engine, direction) -> direction == engine.getOrientation() ? null : engine.fuelItemHandler
        );

        event.registerBlockEntity(
            buildcraft.api.mj.MjAPI.CAP_CONNECTOR,
            BCEnergyBlockEntities.ENGINE_STONE.get(),
            (engine, direction) -> engine.getMjConnector()
        );
        event.registerBlockEntity(
            buildcraft.api.mj.MjAPI.CAP_CONNECTOR,
            BCEnergyBlockEntities.ENGINE_IRON.get(),
            (engine, direction) -> engine.getMjConnector()
        );
        event.registerBlockEntity(
            buildcraft.api.mj.MjAPI.CAP_CONNECTOR,
            BCEnergyBlockEntities.ENGINE_FE.get(),
            (engine, direction) -> {
                // MJ output on facing side only
                if (direction != engine.getOrientation()) return null;
                return engine.getMjConnector();
            }
        );
        event.registerBlockEntity(
            buildcraft.api.mj.MjAPI.CAP_CONNECTOR,
            BCEnergyBlockEntities.DYNAMO_MJ.get(),
            (engine, direction) -> {
                // MJ input on non-facing sides only
                if (direction == engine.getOrientation()) return null;
                return engine.getMjConnector();
            }
        );

        event.registerBlockEntity(
            net.neoforged.neoforge.capabilities.Capabilities.Energy.BLOCK, BCEnergyBlockEntities.ENGINE_FE.get(),
            (engine, direction) -> {
                // FE input on non-facing sides only (facing side outputs MJ)
                if (direction == engine.getOrientation()) return null;
                return engine.energyStorage;
            }
        );
        event.registerBlockEntity(
            net.neoforged.neoforge.capabilities.Capabilities.Energy.BLOCK, BCEnergyBlockEntities.DYNAMO_MJ.get(),
            (dynamo, direction) -> {
                // FE output on facing direction only
                if (direction != dynamo.getOrientation()) return null;
                return dynamo.energyStorage;
            }
        );
    }

    private static void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            EnumSpring.OIL.liquidBlock = BCEnergyFluids.OIL_COOL.block().get().defaultBlockState();
            EnumSpring.OIL.tileConstructor = () -> {
                return null;
            };

            BCEnergyWorldGen.init();
        });
    }

    private static void onServerAboutToStart(ServerAboutToStartEvent event) {
        BCEnergyRecipes.init();
    }
}
