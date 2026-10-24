/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.misc;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * Saves an {@link ItemStack}, data components included, without losing a count the plain codec cannot carry.
 *
 * <p>{@code ItemStack.CODEC} bounds a count to 1..{@value #CODEC_MAX_COUNT} and never checks the item's own stack
 * limit, which a mod can raise ({@code IItemExtension#getMaxStackSize}): a stack of more fails to encode, and a site
 * that saved it through the codec alone saved nothing. Here a stack the codec can carry goes in exactly as the codec
 * writes it, so a save from before this helper reads unchanged and nothing beside the stack is added; a larger one
 * goes in at count 1 with its real count under a sibling {@code countKey}. On read a sibling count, when present,
 * replaces the stack's own, which also covers the older writers that saved a count-1 stack with the real count beside
 * it (the item handler, the stone engine).
 *
 * <p>The {@link CompoundTag} forms leave the two keys holding exactly the stack written: an earlier entry under them
 * (a side count included) is replaced, and an empty stack clears it. The {@link BCValueOutput}/{@link BCValueInput}
 * forms are the same thing for a block entity's {@code writeData} and {@code readData}, version-neutral across the
 * 1.21.10 cliff because those two classes are; a value output cannot drop a key, so each key is written once into
 * the fresh output a save hands out. An empty stack writes no entry and a missing or unreadable entry reads empty, so
 * callers need no guard of their own: the codec rejects an empty stack outright.
 */
public final class StackNbt {
    /** The largest count {@code ItemStack.CODEC} carries (it bounds a count to 1..99). */
    public static final int CODEC_MAX_COUNT = 99;

    /** What a count read back as when none was saved beside the stack; a real count is never this. */
    private static final int NO_SIDE_COUNT = Integer.MIN_VALUE;

    private StackNbt() {}

    /** True when {@code stack} is more than the codec carries, so its count has to be saved beside it. */
    private static boolean needsSideCount(ItemStack stack) {
        return stack.getCount() > CODEC_MAX_COUNT;
    }

    /** The stack the codec can encode: {@code stack} itself, or at count 1 when its count is past the bound. */
    private static ItemStack codecForm(ItemStack stack) {
        return needsSideCount(stack) ? stack.copyWithCount(1) : stack;
    }

    /** The decoded stack at its real count: {@code sideCount} when one was saved, else the count it decoded with. */
    private static ItemStack restore(ItemStack decoded, int sideCount) {
        if (decoded.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (sideCount == NO_SIDE_COUNT) {
            return decoded;
        }
        return sideCount <= 0 ? ItemStack.EMPTY : decoded.copyWithCount(sideCount);
    }

    /** Writes {@code stack} under {@code stackKey} in {@code tag}, and its count under {@code countKey} when the
     *  codec cannot carry it, replacing whatever the two keys held. Nothing is left under them for an empty stack
     *  or one the codec cannot encode. */
    public static void write(CompoundTag tag, String stackKey, String countKey, ItemStack stack) {
        tag.remove(stackKey);
        tag.remove(countKey);
        if (stack.isEmpty()) {
            return;
        }
        ItemStack.CODEC.encodeStart(NBTUtilBC.registryAwareOps(), codecForm(stack))
                .resultOrPartial()
                .ifPresent(payload -> {
                    tag.put(stackKey, payload);
                    if (needsSideCount(stack)) {
                        tag.putInt(countKey, stack.getCount());
                    }
                });
    }

    /** The stack {@link #write(CompoundTag, String, String, ItemStack)} saved, or {@link ItemStack#EMPTY}. */
    public static ItemStack read(CompoundTag tag, String stackKey, String countKey) {
        Tag payload = tag.get(stackKey);
        if (payload == null) {
            return ItemStack.EMPTY;
        }
        ItemStack decoded = ItemStack.CODEC.parse(NBTUtilBC.registryAwareOps(), payload)
                .resultOrPartial()
                .orElse(ItemStack.EMPTY);
        return restore(decoded, NBTUtilBC.getInt(tag, countKey, NO_SIDE_COUNT));
    }

    /** As {@link #write(CompoundTag, String, String, ItemStack)}, into a block entity's value output. */
    public static void write(BCValueOutput output, String stackKey, String countKey, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        output.store(stackKey, ItemStack.CODEC, codecForm(stack));
        if (needsSideCount(stack)) {
            output.putInt(countKey, stack.getCount());
        }
    }

    /** As {@link #read(CompoundTag, String, String)}, from a block entity's value input. */
    public static ItemStack read(BCValueInput input, String stackKey, String countKey) {
        ItemStack decoded = input.read(stackKey, ItemStack.CODEC).orElse(ItemStack.EMPTY);
        return restore(decoded, input.getIntOr(countKey, NO_SIDE_COUNT));
    }
}
