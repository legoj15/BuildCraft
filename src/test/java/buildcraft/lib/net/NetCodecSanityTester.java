/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.net;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import buildcraft.api.enums.EnumSnapshotType;
import buildcraft.builders.snapshot.ArchitectScanPayload;

/**
 * The decoders that pre-size a list (or hand a tick count to {@code DelayedList}) from a wire VarInt clamp it through
 * {@link NetCodecSanity#clampNonNegative} first. This pins the clamp itself: a few bytes on the wire must never name
 * an allocation of gigabytes, and every bound used at a call site behaves as its documentation claims.
 */
public class NetCodecSanityTester {

    private void assertClamp(int value, int max, int expected) {
        Assertions.assertEquals(expected, NetCodecSanity.clampNonNegative(value, max),
            "clampNonNegative(" + value + ", " + max + ")");
    }

    @Test
    public void inRangeValuesPassThrough() {
        assertClamp(0, 10, 0);
        assertClamp(5, 10, 5);
        assertClamp(10, 10, 10);
        assertClamp(1, Short.MAX_VALUE, 1);
        assertClamp(12345, Short.MAX_VALUE, 12345);
    }

    @Test
    public void negativesFoldToZero() {
        assertClamp(-1, 10, 0);
        assertClamp(-1000000, 10, 0);
        assertClamp(Integer.MIN_VALUE, 10, 0);
        assertClamp(Integer.MIN_VALUE, Short.MAX_VALUE, 0);
    }

    @Test
    public void oversizedValuesStopAtTheBound() {
        assertClamp(11, 10, 10);
        assertClamp(Integer.MAX_VALUE, 10, 10);
        // The pipe-item arrival bound: the sending side (PipeFlowItems.sendItemDataToClient) caps at Short.MAX_VALUE,
        // so anything above it off the wire is not a value this game sent.
        assertClamp(Short.MAX_VALUE + 1, Short.MAX_VALUE, Short.MAX_VALUE);
        assertClamp(Integer.MAX_VALUE, Short.MAX_VALUE, Short.MAX_VALUE);
    }

    @Test
    public void zeroBoundCollapsesEverything() {
        assertClamp(0, 0, 0);
        assertClamp(1, 0, 0);
        assertClamp(Integer.MAX_VALUE, 0, 0);
        assertClamp(-1, 0, 0);
    }

    @Test
    public void negativeBoundIsRejected() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> NetCodecSanity.clampNonNegative(0, -1));
    }

    @Test
    public void scanPositionBoundIsTheLargestScanRate() {
        // ArchitectScanPayload clamps its position count to the largest batch a legitimate Architect Table drains in
        // one tick: the fastest snapshot type's maxPerTick (a template's).
        Assertions.assertEquals(EnumSnapshotType.TEMPLATE.maxPerTick, ArchitectScanPayload.MAX_POSITIONS);
        Assertions.assertTrue(ArchitectScanPayload.MAX_POSITIONS >= EnumSnapshotType.BLUEPRINT.maxPerTick);
        Assertions.assertTrue(ArchitectScanPayload.MAX_POSITIONS > 0);
    }

    @Test
    public void debugLineBoundIsPositive() {
        Assertions.assertTrue(MessageDebugResponse.MAX_LINES_PER_SIDE > 0);
    }
}
