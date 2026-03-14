package buildcraft.transport.client.model.key;

import net.minecraft.core.Direction;

import buildcraft.api.transport.pluggable.PluggableModelKey;

/** Stub model key for PluggableBlocker rendering — will be implemented when pipe model baking is ported. */
public class KeyPlugBlocker extends PluggableModelKey {
    public KeyPlugBlocker(Direction side) {
        super(null, side);
    }
}
