/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.hook;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A block that decides for itself whether redstone wire beside it connects, before 26.3 (where vanilla's
 * {@code shouldRedstoneWireConnectTo} does this): NeoForge's {@code IBlockExtension#canConnectRedstone}, with the same
 * arguments. On Fabric a redstone-wire mixin asks it for the wire's direct neighbour.
 */
public interface RedstoneConnectBlock {
    /** Whether wire on {@code direction}'s side of the block at {@code pos} connects to it (null: no side known). */
    boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction);
}
