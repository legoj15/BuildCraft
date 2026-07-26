package buildcraft.robotics;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.bus.api.IEventBus;

import buildcraft.lib.misc.RegistrationUtilBC;
import buildcraft.transport.item.ItemPluggableSimple;

public class BCRoboticsItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BCRobotics.MODID);

    // BlockItem for the Zone Planner — mirrors BCRoboticsBlocks.ZONE_PLANNER.
    public static final DeferredItem<?> ZONE_PLANNER;

    /** Docking Station Plug — the simple {@link PluggableDefinition.IPluggableCreator} path suffices
     *  (the pluggable itself writes no persistent NBT; the {@code DockingStationPipe} it registers is
     *  tracked separately by the {@code RobotRegistry}), so no bespoke {@code ItemRobotStation} class
     *  is needed — matches {@code PLUG_BLOCKER}/{@code PLUG_POWER_ADAPTOR} in transport. */
    public static final DeferredItem<ItemPluggableSimple> ROBOT_STATION;

    static {
        ZONE_PLANNER = ITEMS.registerSimpleBlockItem(BCRoboticsBlocks.ZONE_PLANNER);
        ROBOT_STATION = RegistrationUtilBC.registerItem(ITEMS, "robot_station",
            props -> new ItemPluggableSimple(props, BCRoboticsPlugs.robotStation, null,
                buildcraft.robotics.RobotStationPluggable::boundingBoxFor));
    }

    public static void init(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
