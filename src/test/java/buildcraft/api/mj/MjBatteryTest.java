/*
 * Copyright (c) 2026 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.api.mj;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link MjBattery#setStored(long)}, the absolute-set API added to fix a Builder
 * client/server battery desync. ClientboundBlockEntityDataPacket re-runs loadAdditional on the
 * client every 5 ticks; the previous code path did {@code battery.addPower(stored, false)} in
 * {@code TileBuilder.loadAdditional}, which is additive — every sync was adding the server's
 * snapshot value into the client's existing battery instead of replacing it. Within ~13 seconds
 * the client battery climbed past 65× capacity (reported: 1,036,410 / 16,000 MJ), which then
 * tripped {@code SnapshotBuilder.clientTick}'s {@code stored > 0} extrapolation gate and
 * produced phantom laser firing (visual breaks while the actual server battery sat at 0 and no
 * task ever progressed).
 */
public class MjBatteryTest {

    private static final long CAPACITY = 16_000L;

    @Test
    public void testSetStoredReplacesAbsolutely() {
        MjBattery battery = new MjBattery(CAPACITY);
        battery.addPower(5_000L, false);
        Assertions.assertEquals(5_000L, battery.getStored(), "precondition: addPower set value");
        battery.setStored(2_000L);
        Assertions.assertEquals(2_000L, battery.getStored(),
                "setStored must replace the field absolutely, not add");
    }

    @Test
    public void testSetStoredClampsAboveCapacity() {
        MjBattery battery = new MjBattery(CAPACITY);
        battery.setStored(CAPACITY * 10);
        Assertions.assertEquals(CAPACITY, battery.getStored(),
                "setStored must clamp at capacity; otherwise the original bug recurs");
    }

    @Test
    public void testSetStoredClampsBelowZero() {
        MjBattery battery = new MjBattery(CAPACITY);
        battery.addPower(5_000L, false);
        battery.setStored(-1_000L);
        Assertions.assertEquals(0L, battery.getStored(),
                "setStored must clamp negatives to 0");
    }

    /**
     * The actual regression test for the user's bug. Repeated calls with the same authoritative
     * value must not accumulate — the original client-side sync loop did exactly this.
     */
    @Test
    public void testRepeatedSetStoredDoesNotAccumulate() {
        MjBattery battery = new MjBattery(CAPACITY);
        for (int i = 0; i < 100; i++) {
            battery.setStored(5_000L);
        }
        Assertions.assertEquals(5_000L, battery.getStored(),
                "100 setStored(5000) calls must leave the value at 5000, not 500000 — that's the original loadAdditional bug");
    }

    /**
     * Guards against accidentally clamping the additive {@link MjBattery#addPower} path. External
     * energy receivers (engines, cables, MjBatteryEnergyHandler) rely on addPower's additive
     * semantics; only NBT-load / sync paths should use setStored.
     */
    @Test
    public void testAddPowerStillAdditive() {
        MjBattery battery = new MjBattery(CAPACITY);
        battery.addPower(3_000L, false);
        battery.addPower(2_000L, false);
        Assertions.assertEquals(5_000L, battery.getStored(),
                "addPower must remain additive; energy receive paths depend on it");
    }

    /**
     * Simulates the actual sync packet flow: server reaches 8 kMJ, then drops to 0 (drained by
     * a task), and the client receives the new snapshot. Without setStored, the client would
     * still have the 8 kMJ from the prior sync added on top of the 0 from the new sync.
     */
    @Test
    public void testSyncPacketFlowProducesAuthoritativeValue() {
        MjBattery battery = new MjBattery(CAPACITY);
        // Sync 1: server says 8000.
        battery.setStored(8_000L);
        Assertions.assertEquals(8_000L, battery.getStored());
        // Sync 2: server now says 0 (got drained).
        battery.setStored(0L);
        Assertions.assertEquals(0L, battery.getStored(),
                "client must reflect the server's authoritative snapshot, not accumulate");
    }
}
