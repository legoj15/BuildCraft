/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.snapshot;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.lib.misc.NBTUtilBC;

public class RequiredExtractorItemsList extends RequiredExtractor {
    private NbtPath path = null;

    @Override
    public boolean pricesTileContents() {
        return path != null;
    }

    @Nonnull
    @Override
    public List<ItemStack> extractItemsFromBlock(@Nonnull BlockState blockState, @Nullable CompoundTag tileNbt) {
        if (tileNbt == null || path == null) return Collections.emptyList();
        Tag result = path.get(tileNbt);
        return NBTUtilBC.readCompoundList(result)
            .map(RequiredExtractor::parseItemStack)
            .filter(stack -> !stack.isEmpty())
            .collect(Collectors.toList());
    }

    @Nonnull
    @Override
    public List<ItemStack> extractItemsFromEntity(@Nonnull CompoundTag entityNbt) {
        if (path == null) return Collections.emptyList();
        Tag result = path.get(entityNbt);
        return NBTUtilBC.readCompoundList(result)
            .map(RequiredExtractor::parseItemStack)
            .filter(stack -> !stack.isEmpty())
            .collect(Collectors.toList());
    }

    @Override
    public boolean isContainerContents() {
        return path != null;
    }

    @Override
    public void clearItemsFromBlock(@Nonnull CompoundTag tileNbt) {
        if (path == null) return;
        path.remove(tileNbt);
    }
}
