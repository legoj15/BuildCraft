/*
 * Copyright (c) 2026 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.pipe.behaviour;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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
 * Filter inventory content sync is part of
 * {@link buildcraft.lib.tile.item.ItemHandlerSimple}'s contract and is not pinned here
 * because populating a slot needs {@link net.minecraft.world.item.ItemStack} support that the
 * JUnit classpath cannot bootstrap.
 */
public class PipeBehaviourWoodDiamondSyncTester {

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
}
