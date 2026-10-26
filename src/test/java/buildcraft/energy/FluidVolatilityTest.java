/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.energy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import buildcraft.energy.FluidVolatility.Band;
import buildcraft.energy.block.BCFluidBlock;

/**
 * Which oil and fuel variants burn, how fast, and which explode. The table here is the design written out by hand
 * (issue #36), independent of {@link FluidVolatility#derive}, so a change to the rule or to the fluid table that moves
 * a fluid into another band fails here by name. The numbers each band hands to vanilla fire and the ignition sources
 * are covered by the {@code oil_burns_with_vanilla_fire} and {@code gas_detonates_and_chain_is_bounded} game tests
 * (block and fluid tags only load with a server, so they cannot be read here).
 */
public class FluidVolatilityTest {

    /** Band of each base fluid at heat 0 (cool), 1 (hot), 2 (searing). */
    private static final Map<String, List<Band>> EXPECTED = Map.of(
        "oil", List.of(Band.SLOW, Band.FAST, Band.FAST),
        "oil_residue", List.of(Band.INERT, Band.INERT, Band.INERT),
        "oil_heavy", List.of(Band.SLOW, Band.FAST, Band.FAST),
        "oil_dense", List.of(Band.SLOW, Band.FAST, Band.FAST),
        "oil_distilled", List.of(Band.SLOW, Band.FAST, Band.EXPLOSIVE),
        "fuel_dense", List.of(Band.SLOW, Band.FAST, Band.EXPLOSIVE),
        "fuel_mixed_heavy", List.of(Band.SLOW, Band.FAST, Band.EXPLOSIVE),
        "fuel_light", List.of(Band.FAST, Band.EXPLOSIVE, Band.EXPLOSIVE),
        "fuel_mixed_light", List.of(Band.FAST, Band.EXPLOSIVE, Band.EXPLOSIVE),
        "fuel_gaseous", List.of(Band.EXPLOSIVE, Band.EXPLOSIVE, Band.EXPLOSIVE));

    @Test
    public void everyVariantLandsInItsDesignedBand() {
        Assertions.assertEquals(EXPECTED.size() * 3, BCEnergyFluids.ALL.size(),
            "the table above must cover every registered variant");
        for (BCEnergyFluids.FluidEntry entry : BCEnergyFluids.ALL) {
            List<Band> bands = EXPECTED.get(entry.baseName());
            Assertions.assertNotNull(bands, "no expected bands for base fluid " + entry.baseName());
            Assertions.assertEquals(bands.get(entry.heat()), entry.volatility(), entry.name());
        }
    }

    @Test
    public void everyGaseousVariantIsExplosiveAndNoLiquidIs() {
        for (BCEnergyFluids.FluidEntry entry : BCEnergyFluids.ALL) {
            Assertions.assertEquals(entry.gaseous(), entry.volatility() == Band.EXPLOSIVE, entry.name());
        }
    }

    @Test
    public void aBlockCarriesTheBandOfItsEntry() {
        for (BCEnergyFluids.FluidEntry entry : BCEnergyFluids.ALL) {
            BCFluidBlock block = (BCFluidBlock) entry.block().get();
            Assertions.assertEquals(entry.volatility(), block.volatility(), entry.name());
        }
    }
}
