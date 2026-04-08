package buildcraft.robotics;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.bus.api.IEventBus;

public class BCRoboticsItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BCRobotics.MODID);

    public static final DeferredItem<?> ZONE_PLANNER =
            ITEMS.registerSimpleBlockItem(BCRoboticsBlocks.ZONE_PLANNER);

    public static void init(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
