package buildcraft.robotics;

import net.minecraft.resources.Identifier;

import buildcraft.api.transport.pipe.PipeApi;
import buildcraft.api.transport.pluggable.PluggableDefinition;

/**
 * Creates and registers the docking-station {@link PluggableDefinition}. Unlike silicon (which loads
 * before transport and must defer registration via a pending-list/{@code registerAll} dance), robotics
 * initializes after transport in {@code BCCore}'s init order, so {@code PipeApi.pluggableRegistry} is
 * already set here — register directly, matching {@code BCTransportPlugs}.
 */
public class BCRoboticsPlugs {
    public static PluggableDefinition robotStation;

    public static void preInit() {
        robotStation = register("robot_station", RobotStationPluggable::new);
    }

    private static PluggableDefinition register(String name, PluggableDefinition.IPluggableCreator creator) {
        PluggableDefinition def = new PluggableDefinition(idFor(name), creator);
        PipeApi.pluggableRegistry.register(def);
        return def;
    }

    private static Identifier idFor(String name) {
        return Identifier.fromNamespaceAndPath(BCRobotics.MODID, name);
    }
}
