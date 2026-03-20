package buildcraft.silicon.client.model.key;

import net.minecraft.core.Direction;

import buildcraft.api.transport.pluggable.PluggableModelKey;
import buildcraft.silicon.gate.GateVariant;

public class KeyPlugGate extends PluggableModelKey {

    public final Direction side;
    public final GateVariant variant;

    public KeyPlugGate(Direction side, GateVariant variant) {
        super(null, side); // null layer is CUTOUT/TRANSLUCENT block layer equivalent
        this.side = side;
        this.variant = variant;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != getClass()) return false;
        KeyPlugGate other = (KeyPlugGate) obj;
        return other.side == side && other.variant.equals(variant);
    }

    @Override
    public int hashCode() {
        return side.hashCode() * 31 + variant.hashCode();
    }
}
