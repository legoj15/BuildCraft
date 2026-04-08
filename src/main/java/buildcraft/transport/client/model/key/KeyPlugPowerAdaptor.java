package buildcraft.transport.client.model.key;

import net.minecraft.core.Direction;

import buildcraft.api.transport.pluggable.PluggableModelKey;

/** Model key for PluggablePowerAdaptor — renders in the cutout layer. */
public class KeyPlugPowerAdaptor extends PluggableModelKey {
    public KeyPlugPowerAdaptor(Direction side) {
        super("cutout", side);
    }
}
