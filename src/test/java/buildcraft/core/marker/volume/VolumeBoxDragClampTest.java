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

    /** Clamp a drag of a box that started as the single block {@code HELD}: only the cap limits it. */
    private static BlockPos clampFromPoint(BlockPos target) {
        return VolumeBox.clampDragTarget(HELD, target, CAP, HELD, HELD);
    }

    @Test
    public void aDragPastTheCapStopsAtTheCapOnThatAxisOnly() {
        // Past the cap on +x only: x stops at held + cap, y and z follow the aim.
        Assertions.assertEquals(new BlockPos(100 + CAP, 70, -30),
            clampFromPoint(new BlockPos(100 + CAP + 25, 70, -30)));
        // Past the cap on -z only, from the other side of the held corner.
        Assertions.assertEquals(new BlockPos(90, 60, -40 - CAP),
            clampFromPoint(new BlockPos(90, 60, -40 - CAP - 1)));
        // Past it on -x and +y at once: each stops on its own.
        Assertions.assertEquals(new BlockPos(100 - CAP, 64 + CAP, -41),
            clampFromPoint(new BlockPos(100 - CAP - 300, 64 + CAP + 7, -41)));
    }

    @Test
    public void aDragWithinTheCapIsUnchanged() {
        BlockPos atTheCap = new BlockPos(100 + CAP, 64 - CAP, -40 + CAP);
        Assertions.assertEquals(atTheCap, clampFromPoint(atTheCap));
        Assertions.assertEquals(HELD, clampFromPoint(HELD));
    }

    @Test
    public void aBoxWiderThanTheCapIsNotShrunkByGrabbingIt() {
        // The box started 100 wide on x (wider than the cap), 0 on y and z. Grabbing its far corner keeps x where it
        // was, and x may be dragged out to that starting width but no further; y keeps the cap.
        BlockPos startMin = new BlockPos(0, 64, -40);
        BlockPos startMax = HELD;
        Assertions.assertEquals(new BlockPos(0, 64, -40),
            VolumeBox.clampDragTarget(HELD, new BlockPos(0, 64, -40), CAP, startMin, startMax));
        Assertions.assertEquals(new BlockPos(0, 64 + CAP, -40),
            VolumeBox.clampDragTarget(HELD, new BlockPos(-30, 64 + CAP + 10, -40), CAP, startMin, startMax));
        // The starting width counts from either side of the held corner.
        Assertions.assertEquals(new BlockPos(200, 64, -40),
            VolumeBox.clampDragTarget(HELD, new BlockPos(250, 64, -40), CAP, startMin, startMax));
    }
}
