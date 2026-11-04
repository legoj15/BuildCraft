/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.hook;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;

/**
 * A block whose blast resistance depends on its position and the blast (NeoForge's 4-arg
 * {@code getExplosionResistance}).
 */
public interface ExplosionArmouredBlock {
    /**
     * The resistance to {@code explosion}, given {@code own}, what the block (and any fluid in it) would otherwise
     * resist. It may only raise {@code own}, so it applies equally before or after the fluid's resistance is folded in.
     */
    float explosionResistance(BlockGetter level, BlockPos pos, Explosion explosion, float own);
}
