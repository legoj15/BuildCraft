/*
 * Copyright (c) 2026 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import buildcraft.factory.tile.TileHeatExchange.OutputTank;

/**
 * Regression coverage for the heat exchanger fluid restrictions.
 * <p>
 * 1.12.2 limited fluid input on the multi-block: only the START side accepted heatants
 * (water/oil/fuel) and only the END side accepted coolants (lava/hot oil/hot fuel).
 * The output tanks were marked drain-only. The 26.1.1 port lost both restrictions, so
 * lava could be pushed into any block of the multi-block via pipes, buckets, or GUI
 * clicks.
 * <p>
 * The OutputTank class is the load-bearing piece: external {@code insert} returns 0 via
 * {@code isValid=false}, while internal {@code insertInternal} flips the gate so the
 * craft loop can still write recipe results. Loss of either side breaks the exchanger.
 */
public class HeatExchangerTester {

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) throw new IllegalStateException(msg);
    }

    /**
     * External {@code insert} on an output tank must return 0 — pipes, buckets, and GUI
     * tank widgets all funnel through this path, and prior to the fix any of them could
     * shove arbitrary fluids into the cooled-coolant or heated-heatant output tanks.
     */
    public static void testOutputTankRejectsExternalInsert(GameTestHelper helper) {
        OutputTank tank = new OutputTank();
        FluidResource lava = FluidResource.of(new FluidStack(Fluids.LAVA, 1000));
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = tank.insert(0, lava, 1000, tx);
            assertTrue(inserted == 0,
                    "External insert on OutputTank must return 0, got " + inserted);
            tx.commit();
        }
        assertTrue(tank.getAmountAsLong(0) == 0,
                "OutputTank should still be empty after rejected external insert");
        helper.succeed();
    }

    /**
     * The craft loop's {@code insertInternal} path must still work — that's how cooled
     * coolant and heated heatant land in the output tanks each tick. If this stops
     * accepting fluid the exchanger silently halts even with valid inputs on both sides.
     */
    public static void testOutputTankAcceptsInternalInsert(GameTestHelper helper) {
        OutputTank tank = new OutputTank();
        FluidResource water = FluidResource.of(new FluidStack(Fluids.WATER, 500));
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = tank.insertInternal(0, water, 500, tx);
            assertTrue(inserted == 500,
                    "Internal insert on OutputTank should accept full amount, got " + inserted);
            tx.commit();
        }
        assertTrue(tank.getAmountAsLong(0) == 500,
                "OutputTank should hold 500mb after internal insert, got " + tank.getAmountAsLong(0));
        helper.succeed();
    }

    /**
     * The internal-insert flag must reset after the call, so a subsequent external insert
     * is still blocked. Without the {@code finally} reset, one craft tick would leave the
     * tank permanently writable from the outside.
     */
    public static void testOutputTankInternalFlagResetsAfterCall(GameTestHelper helper) {
        OutputTank tank = new OutputTank();
        FluidResource water = FluidResource.of(new FluidStack(Fluids.WATER, 100));
        try (Transaction tx = Transaction.openRoot()) {
            tank.insertInternal(0, water, 100, tx);
            tx.commit();
        }
        // External insert (same fluid even) must still be blocked
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = tank.insert(0, water, 100, tx);
            assertTrue(inserted == 0,
                    "External insert after internal must still be blocked, got " + inserted);
            tx.commit();
        }
        assertTrue(tank.getAmountAsLong(0) == 100,
                "OutputTank amount should be unchanged after rejected external insert");
        helper.succeed();
    }
}
