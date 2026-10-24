/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.misc;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.lib.test.ValueIoTestUtil;

/**
 * Counts across a save. {@code ItemStack.CODEC} bounds a count to 1..99 and never checks the item's own stack limit,
 * which a mod can raise ({@code IItemExtension#getMaxStackSize}): a larger stack fails to encode, and whatever saved it
 * through the plain codec saved nothing. {@link StackNbt} carries the count beside the stack instead, in both of the
 * shapes BuildCraft saves through (a bare tag, and a block entity's value output).
 */
public class StackNbtTest extends VanillaSetupBaseTester {

    private static final String STACK = "stack";
    private static final String COUNT = "count";

    private static ItemStack hoard(int count) {
        ItemStack stack = new ItemStack(Items.ENDER_PEARL, count);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Hoard"));
        return stack;
    }

    private static CompoundTag writeTag(ItemStack stack) {
        CompoundTag tag = new CompoundTag();
        StackNbt.write(tag, STACK, COUNT, stack);
        return tag;
    }

    private static CompoundTag writeValueIo(ItemStack stack) {
        return ValueIoTestUtil.write(out -> StackNbt.write(out, STACK, COUNT, stack));
    }

    private static ItemStack readTag(CompoundTag tag) {
        return StackNbt.read(tag, STACK, COUNT);
    }

    private static ItemStack readValueIo(CompoundTag tag) {
        return StackNbt.read(ValueIoTestUtil.inputOf(tag), STACK, COUNT);
    }

    private static void assertSameStack(ItemStack want, ItemStack got, String what) {
        Assertions.assertTrue(ItemStack.isSameItemSameComponents(want, got),
                what + ": item and components must survive — wanted " + want + " " + want.getComponentsPatch()
                        + " but got " + got + " " + got.getComponentsPatch());
        Assertions.assertEquals(want.getCount(), got.getCount(), what + ": count");
    }

    @Test
    public void aCountAboveWhatTheItemCodecCarriesSurvives() {
        ItemStack big = hoard(128);

        assertSameStack(big, readTag(writeTag(big)), "bare tag");
        assertSameStack(big, readValueIo(writeValueIo(big)), "value output");
    }

    @Test
    public void everyCountSurvivesAcrossTheCodecBound() {
        for (int count : new int[] {1, 64, StackNbt.CODEC_MAX_COUNT, StackNbt.CODEC_MAX_COUNT + 1}) {
            assertSameStack(hoard(count), readTag(writeTag(hoard(count))), "bare tag, count " + count);
            assertSameStack(hoard(count), readValueIo(writeValueIo(hoard(count))), "value output, count " + count);
        }
    }

    @Test
    public void writingOverAnEarlierEntryLeavesOnlyTheNewStack() {
        // A count past the bound leaves a sibling count behind it; a later, smaller stack in the same tag must not
        // read back at the stale one, and an empty stack clears the entry rather than leaving the old stack.
        CompoundTag tag = new CompoundTag();
        StackNbt.write(tag, STACK, COUNT, hoard(128));
        StackNbt.write(tag, STACK, COUNT, hoard(5));
        assertSameStack(hoard(5), readTag(tag), "5 written over 128");

        StackNbt.write(tag, STACK, COUNT, ItemStack.EMPTY);
        Assertions.assertTrue(readTag(tag).isEmpty(), "empty written over 5: " + tag);
    }

    @Test
    public void aSaveWithoutASideCountReadsAsBefore() {
        // What every site wrote before the helper: the plain codec payload, no count beside it.
        ItemStack old = hoard(5);
        CompoundTag tag = new CompoundTag();
        tag.put(STACK, ItemStack.CODEC.encodeStart(NBTUtilBC.registryAwareOps(), old).result().orElseThrow());

        assertSameStack(old, readTag(tag), "plain codec payload, bare tag");
        assertSameStack(old, readValueIo(tag), "plain codec payload, value input");
    }

    @Test
    public void aStackOfOneWithTheRealCountBesideItReads() {
        // The shape ItemHandlerSimple and the stone engine wrote before the helper took it over: the stack at count 1
        // and the real count under a sibling key.
        CompoundTag tag = new CompoundTag();
        tag.put(STACK, ItemStack.CODEC.encodeStart(NBTUtilBC.registryAwareOps(), hoard(1)).result().orElseThrow());
        tag.putInt(COUNT, 17);

        assertSameStack(hoard(17), readTag(tag), "side count 17, bare tag");
        assertSameStack(hoard(17), readValueIo(tag), "side count 17, value input");
    }

    @Test
    public void nothingIsWrittenForAnEmptyStackAndAMissingOrBrokenEntryReadsEmpty() {
        Assertions.assertTrue(writeTag(ItemStack.EMPTY).isEmpty(), "empty stack, bare tag");
        Assertions.assertTrue(writeValueIo(ItemStack.EMPTY).isEmpty(), "empty stack, value output");

        Assertions.assertTrue(readTag(new CompoundTag()).isEmpty(), "no entry, bare tag");
        Assertions.assertTrue(readValueIo(new CompoundTag()).isEmpty(), "no entry, value input");

        CompoundTag zero = writeTag(hoard(3));
        zero.putInt(COUNT, 0);
        Assertions.assertTrue(readTag(zero).isEmpty(), "a side count of zero is no stack");
    }
}
