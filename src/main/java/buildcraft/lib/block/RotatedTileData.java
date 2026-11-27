/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.block;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Rotation;

/**
 * A block whose block entity saves a direction that turning the block state cannot reach. A blueprint that is built
 * rotated asks the block to turn that data too: the block state rotates through {@code BlockState.rotate}, while what
 * the tile saved under its own keys (an engine's powered face, a flood gate's open sides, a Builder's saved turn)
 * stays as it was captured unless the block turns it here.
 */
public interface RotatedTileData {
    /**
     * Turns, in place, the directions saved in {@code tileNbt}, a block entity tag of this block that the caller owns
     * (a copy), by {@code rotation}, which is never {@code NONE}. A tag that lacks a key, or holds a value out of
     * range, is left as it is: blueprint data is not guaranteed well formed, and this must not throw on it.
     */
    void rotateTileData(CompoundTag tileNbt, Rotation rotation);
}
