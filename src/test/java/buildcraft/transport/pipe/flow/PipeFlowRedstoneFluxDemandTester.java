/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.pipe.flow;

import net.minecraft.gametest.framework.GameTestHelper;

/** Regression guard for FE pipes feeding bufferless pass-through receivers such as AE2's
 *  Energy Acceptor.
 *
 *  <p>The Energy Acceptor is an FE&#8594;AE converter with NO internal buffer: its
 *  {@code getCapacityAsLong()} and {@code getAmountAsLong()} both read 0, yet it accepts power
 *  via {@code insert} whenever the ME grid has demand. BuildCraft used to size a receiver's
 *  demand as {@code capacity - stored}, which is 0 for such a block, so the pipe never requested
 *  or sent power to it (while the ME Controller, with an 8000 AE buffer, worked). The fix sizes
 *  demand with a rolled-back simulated insert instead.
 *
 *  <p>This pins {@link PipeFlowRedstoneFlux#queryEnergyDemand}: it must report positive demand
 *  for a zero-capacity receiver that still accepts power, and zero for one that accepts nothing.
 *  Runs in the game-test runtime because the probe opens a platform transaction. */
public class PipeFlowRedstoneFluxDemandTester {

    public static void testBufferlessReceiverReportsDemand(GameTestHelper helper) {
        buildcraft.platform.transfer.BCEnergyStorage hungry = new MockSink(64);
        buildcraft.platform.transfer.BCEnergyStorage full = new MockSink(0);

        int demand = PipeFlowRedstoneFlux.queryEnergyDemand(hungry, 100);
        helper.assertTrue(demand == 64,
            "Bufferless receiver (capacity 0) with grid demand must report its true demand via a "
                + "simulated insert; expected 64 but got " + demand);

        int none = PipeFlowRedstoneFlux.queryEnergyDemand(full, 100);
        helper.assertTrue(none == 0,
            "A receiver that accepts nothing must report zero demand; got " + none);

        helper.succeed();
    }

    /** Mimics AE2's bufferless Energy Acceptor: zero capacity/amount, but accepts up to
     *  {@code accepts} energy per insert (its grid's current demand). */
    private static final class MockSink implements buildcraft.platform.transfer.BCEnergyStorage {
        private final int accepts;

        MockSink(int accepts) {
            this.accepts = accepts;
        }

        @Override
        public int insert(int amount, buildcraft.platform.transfer.BCTransactionContext tx) {
            return Math.min(amount, accepts);
        }

        @Override
        public int extract(int amount, buildcraft.platform.transfer.BCTransactionContext tx) {
            return 0;
        }

        @Override
        public long getAmountAsLong() {
            return 0;
        }

        @Override
        public long getCapacityAsLong() {
            return 0;
        }
    }
}
