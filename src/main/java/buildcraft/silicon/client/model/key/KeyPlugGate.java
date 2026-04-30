package buildcraft.silicon.client.model.key;

import net.minecraft.core.Direction;

import buildcraft.api.transport.pluggable.PluggableModelKey;
import buildcraft.silicon.gate.GateVariant;

public class KeyPlugGate extends PluggableModelKey {

    public final Direction side;
    public final GateVariant variant;
    /** Retained on the key for callers that read it, but DELIBERATELY excluded from equals/hashCode.
     *  The on/off visual is rendered per-frame by PlugGateRenderer (BER), so a flip in this field
     *  must NOT change the chunk geometry hash — otherwise every gate ON/OFF forces a 27-section
     *  chunk re-mesh, which is the cause of the gate-pulse FPS spike this design avoids. */
    public final boolean active;

    public KeyPlugGate(Direction side, GateVariant variant, boolean active) {
        super(null, side); // null layer is CUTOUT/TRANSLUCENT block layer equivalent
        this.side = side;
        this.variant = variant;
        this.active = active;
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
