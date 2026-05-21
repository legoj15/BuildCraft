package buildcraft.robotics;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.bus.api.IEventBus;

import buildcraft.lib.BCLib;

public class BCRoboticsItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BCRobotics.MODID);

    // Dev-only — mirrors BCRoboticsBlocks.ZONE_PLANNER. Null in public releases.
    public static final DeferredItem<?> ZONE_PLANNER;

    static {
        ZONE_PLANNER = (BCLib.DEV && BCRoboticsBlocks.ZONE_PLANNER != null)
                ? ITEMS.registerSimpleBlockItem(BCRoboticsBlocks.ZONE_PLANNER)
                : null;
    }

    public static void init(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
