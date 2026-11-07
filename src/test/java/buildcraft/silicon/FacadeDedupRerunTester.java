/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.silicon;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.lib.misc.ItemStackKey;
import buildcraft.silicon.client.FacadeDeduplicator;
import buildcraft.silicon.plug.FacadeBlockStateInfo;
import buildcraft.silicon.plug.FacadeStateManager;

/**
 * Issue #149: a dedup re-run (F3+T, resource-pack change) must start from the full enumeration, not from the
 * previous pass's narrowed lists, or a state removed under the old textures never comes back until restart.
 *
 * <p>Simulates "an earlier pass removed BRICKS" by publishing narrowed display maps, then re-runs the dedup with
 * no models (every fingerprint null, so the new textures find no duplicates) and expects BRICKS restored to both
 * display maps.
 */
public class FacadeDedupRerunTester extends VanillaSetupBaseTester {

    @Test
    public void rerunRestoresStatesRemovedByEarlierPass() {
        FacadeStateManager.ensureInitialized();
        BlockState bricks = Blocks.BRICKS.defaultBlockState();
        FacadeBlockStateInfo info = FacadeStateManager.allFacadeStates.get(bricks);
        Assertions.assertNotNull(info, "precondition: BRICKS is a facade state");
        ItemStackKey key = new ItemStackKey(info.requiredStack);

        SortedMap<BlockState, FacadeBlockStateInfo> priorValid = FacadeStateManager.validFacadeStates;
        Map<ItemStackKey, List<FacadeBlockStateInfo>> priorStacks = FacadeStateManager.stackFacades;
        Map<ItemStackKey, List<FacadeBlockStateInfo>> priorRedirects = FacadeStateManager.stackRedirects;
        Assertions.assertTrue(priorValid.containsKey(bricks), "precondition: BRICKS starts in the display map");
        try {
            SortedMap<BlockState, FacadeBlockStateInfo> narrowed = new TreeMap<>(priorValid.comparator());
            narrowed.putAll(priorValid);
            narrowed.remove(bricks);
            Map<ItemStackKey, List<FacadeBlockStateInfo>> narrowedStacks = new HashMap<>(priorStacks);
            narrowedStacks.remove(key);
            FacadeStateManager.validFacadeStates = Collections.unmodifiableSortedMap(narrowed);
            FacadeStateManager.stackFacades = Map.copyOf(narrowedStacks);

            FacadeDeduplicator.deduplicateVisuallyIdentical(Map.of());

            Assertions.assertSame(info, FacadeStateManager.validFacadeStates.get(bricks),
                "a re-run must restore a state the earlier pass removed");
            List<FacadeBlockStateInfo> stacks = FacadeStateManager.stackFacades.get(key);
            Assertions.assertTrue(stacks != null && stacks.contains(info),
                "a re-run must restore the removed state's stack entry");
            // The next re-run is only as good as the baseline this one left behind.
            Assertions.assertSame(info, FacadeStateManager.allFacadeStates.get(bricks),
                "a dedup pass must never narrow the state baseline it starts from");
            Assertions.assertTrue(FacadeStateManager.allStackFacades.get(key).contains(info),
                "a dedup pass must never narrow the stack baseline it starts from");
        } finally {
            FacadeStateManager.validFacadeStates = priorValid;
            FacadeStateManager.stackFacades = priorStacks;
            FacadeStateManager.stackRedirects = priorRedirects;
        }
    }
}
