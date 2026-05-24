/*
 * Copyright (c) 2026 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.pipe.behaviour;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeColor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import buildcraft.lib.misc.NBTUtilBC;

import buildcraft.transport.pipe.behaviour.PipeBehaviourEmzuli.SlotIndex;

/**
 * Regression guard for emzuli pipe state reaching the client via the BE update tag.
 * <p>
 * Same NBT-sync invariant as {@link PipeBehaviourStripesSyncTester}: every field
 * {@link PipeBehaviourEmzuli#writeToNbt()} serialises must be read back by
 * {@link PipeBehaviourEmzuli#readFromNbt(CompoundTag)}. {@code currentSlot} drives the
 * round-robin extraction pointer and is referenced by the GUI; the per-slot dye colours feed
 * the paint applied to extracted items. Filter inventory content sync is part of
 * {@link buildcraft.lib.tile.item.ItemHandlerSimple}'s contract and is not pinned here
 * because populating a slot needs {@link net.minecraft.world.item.ItemStack} support that the
 * JUnit classpath cannot bootstrap.
 */
public class PipeBehaviourEmzuliSyncTester {

    @Test
    public void readFromNbtRestoresCurrentSlot() {
        CompoundTag nbt = new CompoundTag();
        nbt.put("currentSlot", NBTUtilBC.writeEnum(SlotIndex.CIRCLE));

        PipeBehaviourEmzuli target = new PipeBehaviourEmzuli(null);
        Assertions.assertNull(target.getCurrentSlot(), "fresh emzuli starts with null currentSlot");

        target.readFromNbt(nbt);
        Assertions.assertEquals(SlotIndex.CIRCLE, target.getCurrentSlot(),
                "readFromNbt must restore currentSlot so the round-robin pointer survives sync");
    }

    @Test
    public void readFromNbtClearsCurrentSlotWhenAbsent() {
        PipeBehaviourEmzuli target = new PipeBehaviourEmzuli(null);
        CompoundTag seed = new CompoundTag();
        seed.put("currentSlot", NBTUtilBC.writeEnum(SlotIndex.SQUARE));
        target.readFromNbt(seed);
        Assertions.assertEquals(SlotIndex.SQUARE, target.getCurrentSlot());

        target.readFromNbt(new CompoundTag());
        Assertions.assertNull(target.getCurrentSlot(),
                "an in-place sync with no currentSlot tag must clear a previously set value");
    }

    @Test
    public void readFromNbtRestoresSlotColours() {
        PipeBehaviourEmzuli source = new PipeBehaviourEmzuli(null);
        source.slotColours.put(SlotIndex.SQUARE, DyeColor.RED);
        source.slotColours.put(SlotIndex.TRIANGLE, DyeColor.BLUE);
        CompoundTag nbt = source.writeToNbt();

        PipeBehaviourEmzuli target = new PipeBehaviourEmzuli(null);
        target.readFromNbt(nbt);
        Assertions.assertEquals(DyeColor.RED, target.slotColours.get(SlotIndex.SQUARE));
        Assertions.assertEquals(DyeColor.BLUE, target.slotColours.get(SlotIndex.TRIANGLE));
        Assertions.assertNull(target.slotColours.get(SlotIndex.CIRCLE));
        Assertions.assertNull(target.slotColours.get(SlotIndex.CROSS));
    }

    @Test
    public void readFromNbtClearsSlotColoursWhenAbsent() {
        PipeBehaviourEmzuli target = new PipeBehaviourEmzuli(null);
        target.slotColours.put(SlotIndex.SQUARE, DyeColor.RED);

        target.readFromNbt(new CompoundTag());
        Assertions.assertNull(target.slotColours.get(SlotIndex.SQUARE),
                "an absent slotColors[i] tag must clear previously set colours");
    }

    @Test
    public void nbtConstructorRestoresCurrentSlotAndColours() {
        PipeBehaviourEmzuli source = new PipeBehaviourEmzuli(null);
        source.slotColours.put(SlotIndex.CROSS, DyeColor.YELLOW);
        CompoundTag nbt = source.writeToNbt();
        nbt.put("currentSlot", NBTUtilBC.writeEnum(SlotIndex.TRIANGLE));

        PipeBehaviourEmzuli target = new PipeBehaviourEmzuli(null, nbt);
        Assertions.assertEquals(SlotIndex.TRIANGLE, target.getCurrentSlot(),
                "the (IPipe, CompoundTag) constructor must read currentSlot on cold load");
        Assertions.assertEquals(DyeColor.YELLOW, target.slotColours.get(SlotIndex.CROSS));
    }
}
