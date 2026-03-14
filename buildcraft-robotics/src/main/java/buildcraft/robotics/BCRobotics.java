package buildcraft.robotics;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(BCRobotics.MODID)
public class BCRobotics {
    public static final String MODID = "buildcraftrobotics";
    private static final Logger LOGGER = LoggerFactory.getLogger(BCRobotics.class);

    public static BCRobotics INSTANCE;

    public BCRobotics(IEventBus modEventBus, ModContainer modContainer) {
        INSTANCE = this;

        // Register all deferred registries
        BCRoboticsBlocks.init(modEventBus);
        BCRoboticsItems.init(modEventBus);
        BCRoboticsBlockEntities.init(modEventBus);
        BCRoboticsMenuTypes.init(modEventBus);

        // Register client-side extensions on the mod event bus
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            modEventBus.register(buildcraft.robotics.client.BCRoboticsClient.class);
        }

        // Register creative tab
        modEventBus.addListener(this::addCreativeTabItems);

        LOGGER.info("BuildCraft Robotics initialized");
    }

    private void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == buildcraft.core.BCCoreCreativeTabs.MAIN_TAB_KEY) {
            event.accept(new ItemStack(BCRoboticsItems.ZONE_PLANNER.get()));
        }
    }
}
