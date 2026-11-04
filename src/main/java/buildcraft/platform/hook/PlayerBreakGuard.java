/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.hook;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** A block that may keep itself when a player breaks it (NeoForge's {@code onDestroyedByPlayer} returning false). */
public interface PlayerBreakGuard {
    /**
     * Called as {@code player} breaks the block, before it is removed. True keeps the block in place: the guard has
     * handled the break itself (removed only a part of it, say) or refuses it.
     */
    boolean keepsBlockOnPlayerBreak(BlockState state, Level level, BlockPos pos, Player player);
}
