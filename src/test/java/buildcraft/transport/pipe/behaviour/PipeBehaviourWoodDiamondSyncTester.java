/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.pipe.behaviour;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.transport.pipe.behaviour.PipeBehaviourWoodDiamond.FilterMode;

/**
 * Regression guard for emerald (wood-diamond) pipe state reaching the client via the BE update tag.
 * <p>
 * Same NBT-sync invariant as {@link PipeBehaviourStripesSyncTester}: every field
 * {@link PipeBehaviourWoodDiamond#writeToNbt()} serialises must be read back by
 * {@link PipeBehaviourWoodDiamond#readFromNbt(CompoundTag)}. {@code filterMode} and
 * {@code currentFilter} drive the round-robin extraction order; {@code filterValid} is derived
 * from {@code filters} content and must be recomputed when the inventory is reloaded.
 * <p>
 * The filter inventory's content round trip is pinned here too: the unit-test JVM boots the loader
 * and the item registries, so a slot can be populated with a real
 * {@link net.minecraft.world.item.ItemStack} (hence {@link VanillaSetupBaseTester}). That a filter
 * edit also reaches disk is a game-test concern ({@code PipeFilterPersistenceTester}).
 */
public class PipeBehaviourWoodDiamondSyncTester extends VanillaSetupBaseTester {

    //? if >=26.1 {
    @Test
    public void readFromNbtRestoresFilterMode() {
        PipeBehaviourWoodDiamond source = new PipeBehaviourWoodDiamond(null);
        source.filterMode = FilterMode.BLACK_LIST;
        CompoundTag nbt = source.writeToNbt();

        PipeBehaviourWoodDiamond target = new PipeBehaviourWoodDiamond(null);
        Assertions.assertEquals(FilterMode.WHITE_LIST, target.filterMode,
                "fresh wood-diamond starts in WHITE_LIST mode");

        target.readFromNbt(nbt);
        Assertions.assertEquals(FilterMode.BLACK_LIST, target.filterMode,
                "readFromNbt must restore filterMode so client extraction matches server");
    }

    @Test
    public void readFromNbtRestoresCurrentFilter() {
        PipeBehaviourWoodDiamond source = new PipeBehaviourWoodDiamond(null);
        source.currentFilter = 5;
        CompoundTag nbt = source.writeToNbt();

        PipeBehaviourWoodDiamond target = new PipeBehaviourWoodDiamond(null);
        Assertions.assertEquals(0, target.currentFilter);

        target.readFromNbt(nbt);
        Assertions.assertEquals(5, target.currentFilter,
                "readFromNbt must restore the round-robin filter pointer");
    }

    @Test
    public void readFromNbtRecomputesFilterValidFalseWhenFiltersCleared() {
        PipeBehaviourWoodDiamond target = new PipeBehaviourWoodDiamond(null);
        target.filterValid = true;

        target.readFromNbt(new CompoundTag());
        Assertions.assertFalse(target.filterValid,
                "filterValid must be recomputed false after readFromNbt clears filter contents");
    }

    @Test
    public void readFromNbtClearsFilterModeWhenAbsent() {
        PipeBehaviourWoodDiamond target = new PipeBehaviourWoodDiamond(null);
        target.filterMode = FilterMode.ROUND_ROBIN;

        target.readFromNbt(new CompoundTag());
        Assertions.assertEquals(FilterMode.WHITE_LIST, target.filterMode,
                "absent mode tag must reset filterMode to the WHITE_LIST default");
    }

    @Test
    public void filterContentSurvivesAnNbtRoundTrip() {
        PipeBehaviourWoodDiamond source = new PipeBehaviourWoodDiamond(null);
        source.filters.setStackInSlot(4, new ItemStack(Items.IRON_INGOT));
        CompoundTag nbt = source.writeToNbt();

        PipeBehaviourWoodDiamond target = new PipeBehaviourWoodDiamond(null);
        target.readFromNbt(nbt);
        Assertions.assertTrue(target.filters.getStackInSlot(4).is(Items.IRON_INGOT),
                "readFromNbt must restore the filter content the server saved");
        Assertions.assertTrue(target.filters.getStackInSlot(3).isEmpty(), "and leave the other slots empty");
        Assertions.assertTrue(target.filterValid, "a restored filter makes filterValid true");

        PipeBehaviourWoodDiamond cold = new PipeBehaviourWoodDiamond(null, nbt);
        Assertions.assertTrue(cold.filters.getStackInSlot(4).is(Items.IRON_INGOT),
                "the (IPipe, CompoundTag) constructor must restore the filter content on cold load");
    }

    @Test
    public void nbtConstructorRestoresFilterModeAndCurrentFilter() {
        PipeBehaviourWoodDiamond source = new PipeBehaviourWoodDiamond(null);
        source.filterMode = FilterMode.ROUND_ROBIN;
        source.currentFilter = 3;
        CompoundTag nbt = source.writeToNbt();

        PipeBehaviourWoodDiamond target = new PipeBehaviourWoodDiamond(null, nbt);
        Assertions.assertEquals(FilterMode.ROUND_ROBIN, target.filterMode,
                "the (IPipe, CompoundTag) constructor must read filterMode on cold load");
        Assertions.assertEquals(3, target.currentFilter);
    }
    //? }
}
