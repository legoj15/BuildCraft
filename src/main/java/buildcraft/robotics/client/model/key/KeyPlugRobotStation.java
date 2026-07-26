package buildcraft.robotics.client.model.key;

import net.minecraft.core.Direction;

import buildcraft.api.transport.pluggable.PluggableModelKey;

/**
 * Model key for the docking-station post — side only, deliberately excluding the docking state
 * (available/reserved/linked). {@code KeyPlugGate} in silicon documents why a state-varying baked
 * key is a real FPS bug: a key flip forces the pipe's whole 27-section neighbourhood to re-mesh, and
 * a dock/undock/reserve happens far more often than a block edit. The state indicator instead renders
 * per-frame via {@code PlugRobotStationRenderer} (an {@code IPlugDynamicRenderer}), so this key never
 * changes for a given side and the baked plinth quads are cached forever.
 */
public class KeyPlugRobotStation extends PluggableModelKey {
    public KeyPlugRobotStation(Direction side) {
        super("cutout", side);
    }
}
