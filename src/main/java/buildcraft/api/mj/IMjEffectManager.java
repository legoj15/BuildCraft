package buildcraft.api.mj;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;

/** Various effects for showing power loss visibly, and for large amounts of power, causes some damage to nearby
 * entities. */
public interface IMjEffectManager {
    void createPowerLossEffect(Level world, Vec3 center, long microJoulesLost);

    void createPowerLossEffect(Level world, Vec3 center, Direction direction, long microJoulesLost);

    void createPowerLossEffect(Level world, Vec3 center, Vec3 direction, long microJoulesLost);
}

