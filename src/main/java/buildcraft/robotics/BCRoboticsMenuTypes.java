package buildcraft.robotics;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

import buildcraft.robotics.container.ContainerZonePlanner;

public class BCRoboticsMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, BCRobotics.MODID);

    // Menu type for the Zone Planner GUI — mirrors BCRoboticsBlocks.ZONE_PLANNER.
    public static final Supplier<MenuType<ContainerZonePlanner>> ZONE_PLANNER;

    static {
        ZONE_PLANNER = MENU_TYPES.register("zone_planner",
                () -> IMenuTypeExtension.create(ContainerZonePlanner::new));
    }

    public static void init(IEventBus modEventBus) {
        MENU_TYPES.register(modEventBus);
    }
}
