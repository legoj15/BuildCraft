/*
 * Copyright (c) 2026 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.pipe.behaviour;

import java.lang.reflect.Method;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import buildcraft.api.transport.pipe.PipeBehaviour;

/**
 * Regression guard for diamond (sorting) pipe filter state reaching the client.
 * <p>
 * Same NBT-sync invariant as {@link PipeBehaviourStripesSyncTester}: when
 * {@link PipeBehaviourDiamond#writeToNbt()} ships the filter inventory in the BE update tag,
 * the matching {@link PipeBehaviourDiamond#readFromNbt(CompoundTag)} must restore it on the
 * receiving side. Diamond's only NBT-tracked field is the {@code filters} inventory; the
 * content round-trip is part of {@link buildcraft.lib.tile.item.ItemHandlerSimple}'s contract
 * (covered by the {@code item_transactor_*} game tests) and is not re-pinned here because
 * populating a slot needs {@link net.minecraft.world.item.ItemStack} support that the JUnit
 * classpath cannot bootstrap. We instead pin the structural piece the stripes regression
 * showed actually goes wrong: that an override exists at all and that the call wires through
 * cleanly with a null {@code IPipe}.
 */
public class PipeBehaviourDiamondSyncTester {

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

    @Test
    public void nbtConstructorReadsWrittenTag() {
        PipeBehaviourDiamondItem source = new PipeBehaviourDiamondItem(null);
        CompoundTag nbt = source.writeToNbt();

        PipeBehaviourDiamondItem target = new PipeBehaviourDiamondItem(null, nbt);
        Assertions.assertEquals(source.filters.getSlots(), target.filters.getSlots(),
                "the (IPipe, CompoundTag) constructor must accept a writeToNbt-produced tag without crashing");
    }
}
