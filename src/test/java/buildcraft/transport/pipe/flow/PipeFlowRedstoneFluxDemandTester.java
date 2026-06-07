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
 *  Runs in the game-test runtime because the modern path opens a NeoForge {@code Transaction}. */
public class PipeFlowRedstoneFluxDemandTester {

    public static void testBufferlessReceiverReportsDemand(GameTestHelper helper) {
        //? if >=1.21.10 {
        net.neoforged.neoforge.transfer.energy.EnergyHandler hungry = new MockSink(64);
        net.neoforged.neoforge.transfer.energy.EnergyHandler full = new MockSink(0);
        //?} else {
        /*net.neoforged.neoforge.energy.IEnergyStorage hungry = new MockSink(64);
        net.neoforged.neoforge.energy.IEnergyStorage full = new MockSink(0);
        *///?}

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
    //? if >=1.21.10 {
    private static final class MockSink implements net.neoforged.neoforge.transfer.energy.EnergyHandler {
        private final int accepts;

        MockSink(int accepts) {
            this.accepts = accepts;
        }

        @Override
        public int insert(int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext tx) {
            return Math.min(amount, accepts);
        }

        @Override
        public int extract(int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext tx) {
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
    //?} else {
    /*private static final class MockSink implements net.neoforged.neoforge.energy.IEnergyStorage {
        private final int accepts;

        MockSink(int accepts) {
            this.accepts = accepts;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return Math.min(maxReceive, accepts);
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return 0;
        }

        @Override
        public int getMaxEnergyStored() {
            return 0;
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }
    *///?}
}
