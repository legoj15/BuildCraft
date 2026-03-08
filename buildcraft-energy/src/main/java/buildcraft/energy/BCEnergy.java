package buildcraft.energy;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import buildcraft.api.enums.EnumSpring;
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
        BCEnergyBlockEntities.init(modEventBus);

        // Setup event for things that need registries to be frozen
        modEventBus.addListener(this::commonSetup);

        LOGGER.info("BuildCraft Energy initialized");
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // Wire the oil spring enum to our fluid and tile entity
            EnumSpring.OIL.liquidBlock = BCEnergyFluids.OIL_BLOCK.get().defaultBlockState();
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
