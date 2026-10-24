/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.snapshot;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.mojang.serialization.DataResult;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
//? if >=26.3 {
import net.minecraft.world.item.ItemStackTemplate;
//?}
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.PotDecorations;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.lib.misc.NBTUtilBC;

/**
 * Prices the sides of a decorated pot, the items its {@code sherds} data at {@link #path} names. A pot gives its sides
 * back as items when it is cracked, whatever items they name, so a rebuilt pot must be paid for as many of them as it
 * can drop. The data is read through the node's own {@link PotDecorations#CODEC}, the one the block entity loads it
 * with and as it loads it (see {@link #decode}), so what is charged is exactly what the placed pot wears: a list of four
 * item ids on 1.21.1 to 26.2, a record of item stacks (which can carry a count) on 26.3, and data the node would not
 * load as sides prices nothing.
 * <p>
 * A blank side is a brick (the pot is made of four), which the plain pot a rule charges through
 * {@code item_from_block} already includes, so a brick side costs nothing. The sides are decoration, not what the pot
 * holds: this extractor is not {@link #isContainerContents() contents} and is charged and placed in every contents mode.
 */
public class RequiredExtractorPotSherds extends RequiredExtractor {
    private NbtPath path = null;

    @Nonnull
    @Override
    public List<ItemStack> extractItemsFromBlock(@Nonnull BlockState blockState, @Nullable CompoundTag tileNbt) {
        if (tileNbt == null || path == null) return Collections.emptyList();
        return sides(decode(path.get(tileNbt)))
            .filter(stack -> !stack.isEmpty())
            .collect(Collectors.toList());
    }

    /**
     * {@code sherds} as the node's block entity loads it, partial results and all: from 1.21.10 on it reads the field
     * through {@code ValueInput.read}, which keeps the sides that did decode of a list with a bad entry; 1.21.1's
     * {@code PotDecorations.load} drops that partial result and loads a plain pot. Charging more or less than what the
     * placed pot wears would sell sherds the pot never gets, or give away ones it does.
     */
    private static PotDecorations decode(Tag found) {
        DataResult<PotDecorations> parsed = PotDecorations.CODEC.parse(NBTUtilBC.registryAwareOps(), found);
        //? if >=1.21.10 {
        return parsed.resultOrPartial().orElse(PotDecorations.EMPTY);
        //?} else {
        /*return parsed.result().orElse(PotDecorations.EMPTY);*/
        //?}
    }

    /** The items {@code decorations} puts on the pot's sides, a blank side (a plain brick) left out. */
    private static Stream<ItemStack> sides(PotDecorations decorations) {
        //? if >=26.3 {
        ItemStack brick = new ItemStack(Items.BRICK);
        return Stream.of(decorations.back(), decorations.left(), decorations.right(), decorations.front())
            .flatMap(Optional::stream)
            .map(ItemStackTemplate::create)
            .filter(stack -> !(stack.getCount() == 1 && ItemStack.isSameItemSameComponents(stack, brick)));
        //?} else {
        /*return Stream.of(decorations.back(), decorations.left(), decorations.right(), decorations.front())
            .flatMap(Optional::stream)
            .map(ItemStack::new);*/
        //?}
    }
}
