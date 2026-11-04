/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.hook;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A block that takes part in BuildCraft's shearing ({@code ShearServices}), on every loader: the loader-neutral twin of
 * NeoForge's block {@code IShearable}, with the same defaults (shearable, no drops). A block that should also be sheared
 * by another NeoForge mod's shears implements {@code IShearable} as well.
 */
public interface ShearableBlock {
    /** Whether the block at {@code pos} can be sheared now by {@code player} (optional) with {@code tool}. */
    default boolean isShearable(@Nullable Player player, ItemStack tool, Level level, BlockPos pos) {
        return true;
    }

    /** Shears the block at {@code pos} and returns what it drops; the caller removes the block. */
    default List<ItemStack> onSheared(@Nullable Player player, ItemStack tool, Level level, BlockPos pos) {
        return List.of();
    }
}
