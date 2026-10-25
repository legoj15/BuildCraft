/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.marker.volume;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

/** Dragging a volume-box corner stops at the marker reach on each axis, measured from the corner that stays put. */
public class VolumeBoxDragClampTest {

    private static final BlockPos HELD = new BlockPos(100, 64, -40);
    private static final int CAP = 64;

    @Test
    public void aDragPastTheCapStopsAtTheCapOnThatAxisOnly() {
        // Past the cap on +x only: x stops at held + cap, y and z follow the aim.
        Assertions.assertEquals(new BlockPos(100 + CAP, 70, -30),
            VolumeBox.clampDragTarget(HELD, new BlockPos(100 + CAP + 25, 70, -30), CAP));
        // Past the cap on -z only, from the other side of the held corner.
        Assertions.assertEquals(new BlockPos(90, 60, -40 - CAP),
            VolumeBox.clampDragTarget(HELD, new BlockPos(90, 60, -40 - CAP - 1), CAP));
        // Past it on -x and +y at once: each stops on its own.
        Assertions.assertEquals(new BlockPos(100 - CAP, 64 + CAP, -41),
            VolumeBox.clampDragTarget(HELD, new BlockPos(100 - CAP - 300, 64 + CAP + 7, -41), CAP));
    }

    @Test
    public void aDragWithinTheCapIsUnchanged() {
        BlockPos atTheCap = new BlockPos(100 + CAP, 64 - CAP, -40 + CAP);
        Assertions.assertEquals(atTheCap, VolumeBox.clampDragTarget(HELD, atTheCap, CAP));
        Assertions.assertEquals(HELD, VolumeBox.clampDragTarget(HELD, HELD, CAP));
    }
}
