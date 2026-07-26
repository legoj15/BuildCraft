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
        // Explicit reader + loader rather than the single-creator overload: the creator's default
        // loadFromBuffer IGNORES the buffer, which would silently drop the synced indicator state
        // (and leave the buffer unread) — see RobotStationPluggable's network constructor.
        robotStation = register("robot_station",
            (PluggableDefinition.IPluggableNbtReader) RobotStationPluggable::new,
            (PluggableDefinition.IPluggableNetLoader) RobotStationPluggable::new);
    }

    private static PluggableDefinition register(String name, PluggableDefinition.IPluggableNbtReader reader,
        PluggableDefinition.IPluggableNetLoader loader) {
        PluggableDefinition def = new PluggableDefinition(idFor(name), reader, loader);
        PipeApi.pluggableRegistry.register(def);
        return def;
    }

    private static Identifier idFor(String name) {
        return Identifier.fromNamespaceAndPath(BCRobotics.MODID, name);
    }
}
