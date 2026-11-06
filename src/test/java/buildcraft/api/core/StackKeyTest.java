/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.api.core;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.world.level.material.Fluids;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.api.fluids.BCFluidStack;

/** {@link StackKey} matches stacks ignoring their count, so keys that are equal must also hash alike. */
public class StackKeyTest extends VanillaSetupBaseTester {

    @Test
    public void fluidKeysOfDifferentAmountsAreEqualAndHashAlike() {
        StackKey bucket = StackKey.fluid(new BCFluidStack(Fluids.WATER, 1000));
        StackKey drop = StackKey.fluid(new BCFluidStack(Fluids.WATER, 1));
        Assertions.assertEquals(bucket, drop);
        Assertions.assertEquals(bucket.hashCode(), drop.hashCode(), "equal keys hash alike");

        Map<StackKey, String> byKey = new HashMap<>();
        byKey.put(bucket, "water");
        Assertions.assertEquals("water", byKey.get(drop), "a lookup by another amount finds the entry");
        Assertions.assertNull(byKey.get(StackKey.fluid(new BCFluidStack(Fluids.LAVA, 1000))), "another fluid does not");
    }
}
