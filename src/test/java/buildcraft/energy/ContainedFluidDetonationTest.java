/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.energy;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import buildcraft.energy.BCEnergyConfig.ContainedDetonation;

/**
 * When a tank or pipe of gas explodes and how big a blast it makes in each {@code containedFluidDetonation} mode
 * (issue #61): the design
 * written out by hand, so a change to the yield rule fails here by number.
 */
public class ContainedFluidDetonationTest {

    private static final float TNT = 4.0F;

    /** The mode is forced off while either fire setting is off, whatever the file says. */
    @Test
    void fireSettingsGateTheMode() {
        boolean canBurnBefore = BCEnergyConfig.canBurn.get();
        boolean gasExplodesBefore = BCEnergyConfig.gasExplodes.get();
        ContainedDetonation modeBefore = BCEnergyConfig.containedFluidDetonation.get();
        try {
            BCEnergyConfig.containedFluidDetonation.set(ContainedDetonation.BY_VOLUME);
            BCEnergyConfig.canBurn.set(true);
            BCEnergyConfig.gasExplodes.set(true);
            Assertions.assertEquals(ContainedDetonation.BY_VOLUME, BCEnergyConfig.containedDetonationMode());
            BCEnergyConfig.canBurn.set(false);
            Assertions.assertEquals(ContainedDetonation.OFF, BCEnergyConfig.containedDetonationMode(),
                "canBurn off must switch contained detonation off");
            BCEnergyConfig.canBurn.set(true);
            BCEnergyConfig.gasExplodes.set(false);
            Assertions.assertEquals(ContainedDetonation.OFF, BCEnergyConfig.containedDetonationMode(),
                "gasExplodes off must switch contained detonation off");
        } finally {
            BCEnergyConfig.canBurn.set(canBurnBefore);
            BCEnergyConfig.gasExplodes.set(gasExplodesBefore);
            BCEnergyConfig.containedFluidDetonation.set(modeBefore);
        }
    }

    @Test
    void offNeverExplodes() {
        Assertions.assertEquals(0, ContainedFluidDetonation.blastPower(ContainedDetonation.OFF, 16_000));
    }

    @Test
    void perBlockIsOneGasBlockFromABucketUp() {
        Assertions.assertEquals(0, ContainedFluidDetonation.blastPower(ContainedDetonation.PER_BLOCK, 999));
        Assertions.assertEquals(TNT, ContainedFluidDetonation.blastPower(ContainedDetonation.PER_BLOCK, 1_000));
        Assertions.assertEquals(TNT, ContainedFluidDetonation.blastPower(ContainedDetonation.PER_BLOCK, 16_000));
    }

    @Test
    void byVolumeGrowsWithTheCubeRootOfTheGas() {
        Assertions.assertEquals(0, ContainedFluidDetonation.blastPower(ContainedDetonation.BY_VOLUME, 0));
        Assertions.assertEquals(TNT / 2, ContainedFluidDetonation.blastPower(ContainedDetonation.BY_VOLUME, 125), 1e-4);
        Assertions.assertEquals(TNT, ContainedFluidDetonation.blastPower(ContainedDetonation.BY_VOLUME, 1_000), 1e-4);
        Assertions.assertEquals(2 * TNT, ContainedFluidDetonation.blastPower(ContainedDetonation.BY_VOLUME, 8_000),
            1e-4);
        // A full tank: about two and a half times TNT
        Assertions.assertEquals(10.08F, ContainedFluidDetonation.blastPower(ContainedDetonation.BY_VOLUME, 16_000),
            0.01);
        // A nearly empty pipe barely pops
        Assertions.assertTrue(ContainedFluidDetonation.blastPower(ContainedDetonation.BY_VOLUME, 10) < 1);
        Assertions.assertEquals(ContainedFluidDetonation.MAX_BLAST_POWER,
            ContainedFluidDetonation.blastPower(ContainedDetonation.BY_VOLUME, 1_000_000));
    }
}
