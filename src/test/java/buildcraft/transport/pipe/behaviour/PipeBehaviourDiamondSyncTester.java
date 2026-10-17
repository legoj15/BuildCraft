/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.pipe.behaviour;

import java.lang.reflect.Method;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.api.transport.pipe.PipeBehaviour;
import buildcraft.lib.tile.item.ItemHandlerSimple;

/**
 * Regression guard for diamond (sorting) pipe filter state reaching the client.
 * <p>
 * Same NBT-sync invariant as {@link PipeBehaviourStripesSyncTester}: when
 * {@link PipeBehaviourDiamond#writeToNbt()} ships the filter inventory in the BE update tag,
 * the matching {@link PipeBehaviourDiamond#readFromNbt(CompoundTag)} must restore it on the
 * receiving side. Diamond's only NBT-tracked field is the {@code filters} inventory. The tests pin
 * the structural piece the stripes regression showed actually goes wrong (an override exists at all
 * and the call wires through cleanly with a null {@code IPipe}) and the filter content round trip,
 * which works in a unit-test JVM because it boots the loader and the item registries (hence
 * {@link VanillaSetupBaseTester}). A detached Diamond behaviour cannot have a slot edited directly,
 * since the change callback looks up its pipe's holder; the tests fill the inventory through NBT
 * instead, which fires no callback. That a filter edit also reaches disk is a game-test concern
 * ({@code PipeFilterPersistenceTester}).
 */
public class PipeBehaviourDiamondSyncTester extends VanillaSetupBaseTester {

    //? if >=26.1 {
    @Test
    public void diamondDeclaresReadFromNbtOverride() throws NoSuchMethodException {
        // Pins the exact regression shape from the stripes bug: the base PipeBehaviour.readFromNbt
        // is a no-op, so a missing override silently drops every field writeToNbt serialises.
        Method m = PipeBehaviourDiamond.class.getDeclaredMethod("readFromNbt", CompoundTag.class);
        Assertions.assertSame(PipeBehaviourDiamond.class, m.getDeclaringClass(),
                "PipeBehaviourDiamond must declare its own readFromNbt to mirror writeToNbt");
        Assertions.assertNotSame(PipeBehaviour.class, m.getDeclaringClass(),
                "inheriting the no-op base readFromNbt would drop filter sync — same shape as the stripes bug");
    }

    @Test
    public void readFromNbtOnEmptyTagPreservesInventoryShape() {
        PipeBehaviourDiamondItem target = new PipeBehaviourDiamondItem(null);
        int slotsBefore = target.filters.getSlots();
        target.readFromNbt(new CompoundTag());
        Assertions.assertEquals(slotsBefore, target.filters.getSlots(),
                "readFromNbt must not resize the filter inventory");
        for (int i = 0; i < slotsBefore; i++) {
            Assertions.assertTrue(target.filters.getStackInSlot(i).isEmpty(),
                    "an in-place sync with no filters tag must leave the inventory empty");
        }
    }

    @Test
    public void emptyRoundTripIsIdempotent() {
        PipeBehaviourDiamondItem source = new PipeBehaviourDiamondItem(null);
        CompoundTag nbt = source.writeToNbt();

        PipeBehaviourDiamondItem target = new PipeBehaviourDiamondItem(null);
        target.readFromNbt(nbt);
        Assertions.assertEquals(source.filters.getSlots(), target.filters.getSlots());
    }

    /** A tag holding one filter, built from a handler of its own so no slot edit has to run the behaviour's callback. */
    private static CompoundTag tagWithFilterIn(int slot, ItemStack filter) {
        ItemHandlerSimple donor = new ItemHandlerSimple(PipeBehaviourDiamond.FILTERS_PER_SIDE * 6);
        donor.setStackInSlot(slot, filter);
        CompoundTag nbt = new CompoundTag();
        nbt.put("filters", donor.serializeNBT());
        return nbt;
    }

    @Test
    public void filterContentSurvivesAnNbtRoundTrip() {
        PipeBehaviourDiamondItem server = new PipeBehaviourDiamondItem(null, tagWithFilterIn(13, new ItemStack(Items.IRON_INGOT)));
        Assertions.assertTrue(server.filters.getStackInSlot(13).is(Items.IRON_INGOT),
                "the (IPipe, CompoundTag) constructor must restore the filter content on cold load");

        CompoundTag sent = server.writeToNbt();
        PipeBehaviourDiamondItem client = new PipeBehaviourDiamondItem(null);
        client.readFromNbt(sent);
        Assertions.assertTrue(client.filters.getStackInSlot(13).is(Items.IRON_INGOT),
                "readFromNbt must restore the filter content the server sent");
        Assertions.assertTrue(client.filters.getStackInSlot(12).isEmpty(), "and leave the other slots empty");
    }

    @Test
    public void readFromNbtClearsAFilterTheServerNoLongerHas() {
        PipeBehaviourDiamondItem client = new PipeBehaviourDiamondItem(null, tagWithFilterIn(0, new ItemStack(Items.GOLD_INGOT)));
        client.readFromNbt(new PipeBehaviourDiamondItem(null).writeToNbt());
        Assertions.assertTrue(client.filters.getStackInSlot(0).isEmpty(),
                "an in-place sync from a server whose filters are empty must clear the stale filter");
    }

    @Test
    public void nbtConstructorReadsWrittenTag() {
        PipeBehaviourDiamondItem source = new PipeBehaviourDiamondItem(null);
        CompoundTag nbt = source.writeToNbt();

        PipeBehaviourDiamondItem target = new PipeBehaviourDiamondItem(null, nbt);
        Assertions.assertEquals(source.filters.getSlots(), target.filters.getSlots(),
                "the (IPipe, CompoundTag) constructor must accept a writeToNbt-produced tag without crashing");
    }
    //? }
}
