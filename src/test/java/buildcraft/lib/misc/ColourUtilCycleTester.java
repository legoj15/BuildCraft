/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.misc;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.world.item.DyeColor;

/**
 * The 17-step "no colour → the 16 dyes → no colour" cycle the Emzuli pipe's paint buttons step through
 * (left click forwards, right click backwards), as 1.12.2's {@code ColourUtil.getNextOrNull/getPrevOrNull}.
 */
public class ColourUtilCycleTester {

    @Test
    public void forwardsStartsAtWhiteAndEndsBackAtNoColour() {
        Assertions.assertEquals(DyeColor.WHITE, ColourUtil.getNextOrNull(null));
        Assertions.assertEquals(DyeColor.ORANGE, ColourUtil.getNextOrNull(DyeColor.WHITE));
        Assertions.assertNull(ColourUtil.getNextOrNull(DyeColor.BLACK));
    }

    @Test
    public void backwardsStartsAtBlackAndEndsBackAtNoColour() {
        Assertions.assertEquals(DyeColor.BLACK, ColourUtil.getPrevOrNull(null));
        Assertions.assertEquals(DyeColor.RED, ColourUtil.getPrevOrNull(DyeColor.BLACK));
        Assertions.assertNull(ColourUtil.getPrevOrNull(DyeColor.WHITE));
    }

    @Test
    public void aFullForwardLapVisitsEveryDyeOnceInOrder() {
        List<DyeColor> seen = new ArrayList<>();
        DyeColor c = ColourUtil.getNextOrNull(null);
        while (c != null) {
            seen.add(c);
            c = ColourUtil.getNextOrNull(c);
        }
        Assertions.assertEquals(List.of(DyeColor.values()), seen);
    }

    @Test
    public void backwardsUndoesForwards() {
        Assertions.assertNull(ColourUtil.getPrevOrNull(ColourUtil.getNextOrNull(null)));
        for (DyeColor colour : DyeColor.values()) {
            Assertions.assertEquals(colour, ColourUtil.getPrevOrNull(ColourUtil.getNextOrNull(colour)), colour.name());
            Assertions.assertEquals(colour, ColourUtil.getNextOrNull(ColourUtil.getPrevOrNull(colour)), colour.name());
        }
    }
}
