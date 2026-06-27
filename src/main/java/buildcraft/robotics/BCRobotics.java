package buildcraft.robotics;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import buildcraft.api.boards.RedstoneBoardRegistry;
import buildcraft.api.robots.RobotManager;
import buildcraft.core.BCCore;

/**
 * BuildCraft Robotics initializer. No longer a separate @Mod — called from BCCore.
 */
public class BCRobotics {
    public static final String MODID = BCCore.MODID;
    private static final Logger LOGGER = LoggerFactory.getLogger(BCRobotics.class);

    public static void init(IEventBus modEventBus) {
        // Un-orphan the robotics API: wire the registry provider and board registry the rest of the
        // robotics system (DockingStation, boards) reaches through these statics. Harmless without any
        // robots present, and required the moment a docking station or board is created.
        RobotManager.registryProvider = new RobotRegistryProvider();
        RedstoneBoardRegistry.instance = new ImplRedstoneBoardRegistry();

        // Register all deferred registries
        BCRoboticsBlocks.init(modEventBus);
        BCRoboticsItems.init(modEventBus);
        BCRoboticsBlockEntities.init(modEventBus);
        BCRoboticsMenuTypes.init(modEventBus);

        // Register client-side extensions on the mod event bus
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            buildcraft.robotics.client.BCRoboticsClient.initClient(modEventBus);
        }

        // Register creative tab
        modEventBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            addCreativeTabItems(event);
        });

        LOGGER.info("BuildCraft Robotics initialized");
    }

    private static void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
    }
}
