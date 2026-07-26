/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

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
