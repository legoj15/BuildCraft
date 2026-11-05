/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.pipe.flow;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;

import buildcraft.platform.transfer.BCTransaction;
import buildcraft.transport.BCTransportBlocks;
import buildcraft.transport.BCTransportItems;
import buildcraft.transport.tile.TilePipeHolder;

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
 *  Runs in the game-test runtime because the probe opens a platform transaction.
 *
 *  <p>The probe must also leave no trace: the pipe's F3 "OFFERED" counter is the power it was handed, so a demand
 *  probe (an insert inside a transaction nobody commits) must not count; 1.21.1 only ever counted a committed insert. */
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

    /** Pins {@code debugPowerOffered}, the figure behind the F3 overlay's "OFFERED" line: it counts what a section
     *  really took (an insert that committed), not what it said it would take when a neighbour asked how much it
     *  wanted. Rig: one wood RF pipe (a receiver), asked for its demand like a neighbouring pipe would, then handed
     *  power for real. */
    public static void testDemandProbeDoesNotCountAsOffered(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, BCTransportBlocks.PIPE_HOLDER.get());
        //? if >=1.21.10 {
        TilePipeHolder tile = helper.getBlockEntity(pos, TilePipeHolder.class);
        //?} else {
        /*TilePipeHolder tile = helper.getBlockEntity(pos);*/
        //?}
        tile.onPlacedBy(null, new ItemStack(BCTransportItems.PIPE_WOOD_RF.get()));
        PipeFlowRedstoneFlux flow = (PipeFlowRedstoneFlux) tile.getPipe().getFlow();
        flow.reconfigure();
        PipeFlowRedstoneFlux.Section section = flow.getSection(Direction.NORTH);

        int demand = PipeFlowRedstoneFlux.queryEnergyDemand(section, 50);
        helper.assertTrue(demand == 50, "fixture: a fresh wood RF pipe must offer to take 50 FE, said " + demand);
        helper.assertTrue(section.debugPowerOffered == 0,
            "a demand probe is rolled back, so it must not count as power offered; the counter reads "
                + section.debugPowerOffered);
        helper.assertTrue(section.internalNextPower == 0,
            "the probe's insert must be rolled back; the section holds " + section.internalNextPower);

        try (BCTransaction tx = BCTransaction.openRoot()) {
            helper.assertTrue(section.insert(30, tx) == 30, "the real insert must be accepted whole");
            tx.commit();
        }
        helper.assertTrue(section.debugPowerOffered == 30,
            "a committed insert counts once; the counter reads " + section.debugPowerOffered);
        helper.assertTrue(section.internalNextPower == 30, "and it stays: " + section.internalNextPower);

        try (BCTransaction tx = BCTransaction.openRoot()) {
            section.insert(20, tx);
            // aborted: the power and its count both go back
        }
        helper.assertTrue(section.debugPowerOffered == 30 && section.internalNextPower == 30,
            "an aborted insert counts nothing; offered " + section.debugPowerOffered + ", held "
                + section.internalNextPower);

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
