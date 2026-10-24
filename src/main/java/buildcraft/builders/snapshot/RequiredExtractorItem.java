/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.snapshot;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public class RequiredExtractorItem extends RequiredExtractor {
    private NbtPath path = null;
    /** Optional: where the stack's real count lives when the stack itself is saved at one (a stone engine's fuel,
     *  whose stack the codec cannot carry past 99). Absent, the stack's own count is used. */
    private NbtPath countPath = null;

    @Override
    public boolean pricesTileContents() {
        return path != null;
    }

    private List<ItemStack> extract(CompoundTag nbt) {
        if (path.get(nbt) instanceof CompoundTag ct) {
            ItemStack stack = parseItemStack(ct);
            if (!stack.isEmpty() && countPath != null) {
                stack = stack.copyWithCount(Math.max(1, readInt(countPath.get(nbt), stack.getCount())));
            }
            if (!stack.isEmpty()) {
                return Collections.singletonList(stack);
            }
        }
        return Collections.emptyList();
    }

    @Nonnull
    @Override
    public List<ItemStack> extractItemsFromBlock(@Nonnull BlockState blockState, @Nullable CompoundTag tileNbt) {
        if (tileNbt == null || path == null) return Collections.emptyList();
        return extract(tileNbt);
    }

    @Nonnull
    @Override
    public List<ItemStack> extractItemsFromEntity(@Nonnull CompoundTag entityNbt) {
        if (path == null) return Collections.emptyList();
        return extract(entityNbt);
    }

    @Override
    public boolean isContainerContents() {
        return path != null;
    }

    @Override
    public void clearItemsFromBlock(@Nonnull CompoundTag tileNbt) {
        if (path == null) return;
        path.remove(tileNbt);
        if (countPath != null) {
            countPath.remove(tileNbt);
        }
    }
}
