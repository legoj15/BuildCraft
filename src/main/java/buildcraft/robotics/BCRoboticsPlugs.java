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
        // All THREE hooks, not two. Explicit reader + loader rather than the single-creator overload,
        // because the creator's default loadFromBuffer IGNORES the buffer, which would silently drop the
        // synced indicator state (and leave the buffer unread) — see RobotStationPluggable's network
        // constructor. But the creator has to be supplied as well: ItemPluggableSimple.onPlace returns null
        // when it is absent, and BlockPipeHolder.useItemOn then falls through with no pluggable, no sound
        // and no message — i.e. the station item is completely inert in the player's hand. That was the
        // state of things from Ph2 until this was fixed, and no test caught it because every test installs
        // the pluggable programmatically (see RobotStationPluggableTester#testPlayerPlacesRobotStationByHand,
        // which now drives the real item path).
        robotStation = register("robot_station",
            (PluggableDefinition.IPluggableNbtReader) RobotStationPluggable::new,
            (PluggableDefinition.IPluggableNetLoader) RobotStationPluggable::new,
            RobotStationPluggable::new);
    }

    private static PluggableDefinition register(String name, PluggableDefinition.IPluggableNbtReader reader,
        PluggableDefinition.IPluggableNetLoader loader, PluggableDefinition.IPluggableCreator creator) {
        PluggableDefinition def = new PluggableDefinition(idFor(name), reader, loader, creator);
        PipeApi.pluggableRegistry.register(def);
        return def;
    }

    private static Identifier idFor(String name) {
        return Identifier.fromNamespaceAndPath(BCRobotics.MODID, name);
    }
}
