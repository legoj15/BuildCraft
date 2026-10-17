/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client.guide.parts.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import buildcraft.api.mj.MjAPI;
import buildcraft.lib.BCLibConfig;
import buildcraft.lib.misc.LocaleUtil;

/** The Assembly Table panel's cost tooltip. The recipe cost is micro-MJ; the tooltip used to print that raw figure
 *  with an "MJ" suffix (a million times too large, and blind to the player's unit settings) because
 *  {@code localizeMj} was assumed to be missing. It must read like every other energy figure: the Laser Table panel,
 *  the machine GUIs and the ledgers all go through {@link LocaleUtil#localizeMj}. */
class GuideAssemblyCostTooltipTest {

    @Test
    void costIsFormattedByTheSharedMjFormatterNotPrintedRaw() {
        long cost = 3 * MjAPI.MJ;

        List<String> tooltip = GuideAssembly.costTooltip(cost);

        assertEquals(List.of(LocaleUtil.localizeMj(cost)), tooltip);
        assertFalse(tooltip.get(0).contains(Long.toString(cost)),
            "the raw micro-MJ figure leaked into the tooltip: " + tooltip);
    }

    @Test
    void costFollowsThePlayersPowerModeLikeEveryOtherEnergyFigure() {
        long cost = 3 * MjAPI.MJ;
        BCLibConfig.PowerMode previous = BCLibConfig.powerMode.get();
        try {
            BCLibConfig.powerMode.set(BCLibConfig.PowerMode.MJ_ONLY);
            List<String> inMj = GuideAssembly.costTooltip(cost);
            BCLibConfig.powerMode.set(BCLibConfig.PowerMode.DISPLAY_RF);
            List<String> inRf = GuideAssembly.costTooltip(cost);

            assertEquals(List.of(LocaleUtil.localizeMj(cost)), inRf);
            assertNotEquals(inMj, inRf, "DISPLAY_RF must re-unit the cost as FE/RF");
        } finally {
            BCLibConfig.powerMode.set(previous);
        }
    }
}
