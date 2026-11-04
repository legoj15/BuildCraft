/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.hook;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.SoundType;

/** A block whose sound depends on its position (NeoForge's 4-arg {@code getSoundType}). */
public interface PositionalSoundBlock {
    /** The sound of this block at {@code pos} for {@code entity} (optional), or null for the block's default. */
    @Nullable
    SoundType soundType(LevelReader level, BlockPos pos, @Nullable Entity entity);
}
