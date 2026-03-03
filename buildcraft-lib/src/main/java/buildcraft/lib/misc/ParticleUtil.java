package buildcraft.lib.misc;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Stub for particle utilities — will be fleshed out when client rendering is ported. */
public class ParticleUtil {
    /** Show a colour-change particle effect at the given world position. */
    public static void showChangeColour(Level level, Vec3 hitPos, @Nullable DyeColor colour) {
        // Stub — requires client-side particle system
    }
}
