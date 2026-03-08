package buildcraft.energy;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import buildcraft.api.enums.EnumSpring;
import buildcraft.energy.client.BCEnergyFluidsClient;
import buildcraft.energy.tile.TileSpringOil;

@Mod(BCEnergy.MODID)
public class BCEnergy {
    public static final String MODID = "buildcraftenergy";
    private static final Logger LOGGER = LoggerFactory.getLogger(BCEnergy.class);

    public static BCEnergy INSTANCE;

    public BCEnergy(IEventBus modEventBus, ModContainer modContainer) {
        INSTANCE = this;

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
        modEventBus.addListener(this::addCreativeTabItems);

        // Setup event for things that need registries to be frozen
        modEventBus.addListener(this::commonSetup);

        LOGGER.info("BuildCraft Energy initialized");
    }

    private void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        // Add engines to the BuildCraft creative tab
        if (event.getTabKey() == buildcraft.core.BCCoreCreativeTabs.MAIN_TAB_KEY) {
            event.accept(BCEnergyItems.ENGINE_STONE);
            event.accept(BCEnergyItems.ENGINE_IRON);
        }
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // Register fuel and coolant definitions
            BCEnergyRecipes.init();

            // Wire the oil spring enum to our fluid and tile entity
            EnumSpring.OIL.liquidBlock = BCEnergyFluids.OIL_COOL.block().get().defaultBlockState();
            EnumSpring.OIL.tileConstructor = () -> {
                // This is used by BlockSpring to create the tile entity
                // We return null here — the block entity is created via BlockEntityType
                // The spring block should use the registered BlockEntityType instead
                return null;
            };

            // Initialize world gen
            BCEnergyWorldGen.init();
        });
    }
}
