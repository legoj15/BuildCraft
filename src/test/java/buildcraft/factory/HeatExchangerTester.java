/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.fluids.FluidStack;

//? if >=1.21.10 {
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
//?} else {
/*// 1.21.1: no Transfer API — the version-neutral BCFluidTank surface (fill/drain/
// setFluidStack/getAmountMb/getCapacityMb) and ItemHandlerSimple's classic IItemHandler
// methods stand in.*/
//?}

import buildcraft.factory.tile.TileHeatExchange.OutputTank;
import buildcraft.lib.fluid.BCFluidTank;
import buildcraft.lib.tile.item.ItemHandlerSimple;

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
        //? if >=1.21.10 {
        FluidResource lava = FluidResource.of(new FluidStack(Fluids.LAVA, 1000));
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = tank.insert(0, lava, 1000, tx);
            assertTrue(inserted == 0,
                    "External insert on OutputTank must return 0, got " + inserted);
            tx.commit();
        }
        assertTrue(tank.getAmountAsLong(0) == 0,
                "OutputTank should still be empty after rejected external insert");
        //?} else {
        /*FluidStack lava = new FluidStack(Fluids.LAVA, 1000);
        int inserted = tank.fill(0, lava, false);
        assertTrue(inserted == 0,
                "External insert on OutputTank must return 0, got " + inserted);
        assertTrue(tank.getAmountMb(0) == 0,
                "OutputTank should still be empty after rejected external insert");*/
        //?}
        helper.succeed();
    }

    /**
     * The craft loop's {@code insertInternal} path must still work — that's how cooled
     * coolant and heated heatant land in the output tanks each tick. If this stops
     * accepting fluid the exchanger silently halts even with valid inputs on both sides.
     */
    public static void testOutputTankAcceptsInternalInsert(GameTestHelper helper) {
        OutputTank tank = new OutputTank();
        //? if >=1.21.10 {
        FluidResource water = FluidResource.of(new FluidStack(Fluids.WATER, 500));
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = tank.insertInternal(0, water, 500, tx);
            assertTrue(inserted == 500,
                    "Internal insert on OutputTank should accept full amount, got " + inserted);
            tx.commit();
        }
        assertTrue(tank.getAmountAsLong(0) == 500,
                "OutputTank should hold 500mb after internal insert, got " + tank.getAmountAsLong(0));
        //?} else {
        /*int inserted = tank.fillInternal(new FluidStack(Fluids.WATER, 500), false);
        assertTrue(inserted == 500,
                "Internal insert on OutputTank should accept full amount, got " + inserted);
        assertTrue(tank.getAmountMb(0) == 500,
                "OutputTank should hold 500mb after internal insert, got " + tank.getAmountMb(0));*/
        //?}
        helper.succeed();
    }

    /**
     * The internal-insert flag must reset after the call, so a subsequent external insert
     * is still blocked. Without the {@code finally} reset, one craft tick would leave the
     * tank permanently writable from the outside.
     */
    public static void testOutputTankInternalFlagResetsAfterCall(GameTestHelper helper) {
        OutputTank tank = new OutputTank();
        //? if >=1.21.10 {
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
        //?} else {
        /*FluidStack water = new FluidStack(Fluids.WATER, 100);
        int filled = tank.fillInternal(water, false);
        assertTrue(filled == 100,
                "Internal insert on OutputTank should accept full amount, got " + filled);
        // External insert (same fluid even) must still be blocked
        int inserted = tank.fill(0, water, false);
        assertTrue(inserted == 0,
                "External insert after internal must still be blocked, got " + inserted);
        assertTrue(tank.getAmountMb(0) == 100,
                "OutputTank amount should be unchanged after rejected external insert");*/
        //?}
        helper.succeed();
    }

    /**
     * Simulates one craft cycle: fill an output tank and drain an input tank inside a single
     * transaction. When everything fits, both halves commit and the totals balance. The
     * exchanger relies on this for fluid conservation — without the atomic transaction the
     * old code committed each operation separately, so a simulation/execution mismatch on
     * the fill (e.g. only 4mb fits when 5mb was promised) still drained the full 5mb from
     * the input, leaking fluid one tick at a time.
     */
    public static void testAtomicCraftCommitsBalancedFillAndDrain(GameTestHelper helper) {
        //? if >=1.21.10 {
        OutputTank out = new OutputTank();
        FluidStacksResourceHandler in = new FluidStacksResourceHandler(1, 2000);
        FluidResource water = FluidResource.of(new FluidStack(Fluids.WATER, 1));
        // Pre-fill input
        in.set(0, water, 2000);
        // One craft step transfers 5mb
        try (Transaction tx = Transaction.openRoot()) {
            int filled = out.insertInternal(0, water, 5, tx);
            int drained = in.extract(0, water, 5, tx);
            assertTrue(filled == 5, "fill should put 5mb, got " + filled);
            assertTrue(drained == 5, "drain should pull 5mb, got " + drained);
            tx.commit();
        }
        assertTrue(out.getAmountAsLong(0) == 5,
                "Output should hold 5mb, got " + out.getAmountAsLong(0));
        assertTrue(in.getAmountAsLong(0) == 1995,
                "Input should hold 1995mb, got " + in.getAmountAsLong(0));
        //?} else {
        /*// 1.21.1 has no transactions — production craft() (TileHeatExchange) simulates every
        // operation first and only executes once all simulations promise the full amount.
        // Mirror that here: fill 5 into the output and drain 5 from the input.
        OutputTank out = new OutputTank();
        BCFluidTank in = new BCFluidTank(1, 2000);
        in.setFluidStack(0, new FluidStack(Fluids.WATER, 2000));
        FluidStack five = new FluidStack(Fluids.WATER, 5);
        int simFilled = out.fillInternal(five, true);
        assertTrue(simFilled == 5, "simulate fill should promise 5mb, got " + simFilled);
        assertTrue(out.getAmountMb(0) == 0, "simulate fill must not mutate the output");
        FluidStack simDrained = in.drain(0, 5, true);
        assertTrue(simDrained.getAmount() == 5, "simulate drain should promise 5mb, got " + simDrained.getAmount());
        assertTrue(in.getAmountMb(0) == 2000, "simulate drain must not mutate the input");
        // Execute both halves — the balanced craft.
        out.fillInternal(five, false);
        in.drain(0, 5, false);
        assertTrue(out.getAmountMb(0) == 5,
                "Output should hold 5mb, got " + out.getAmountMb(0));
        assertTrue(in.getAmountMb(0) == 1995,
                "Input should hold 1995mb, got " + in.getAmountMb(0));*/
        //?}
        helper.succeed();
    }

    /**
     * Heat-exchanger container slots are constructed as {@code new ItemHandlerSimple(4, 1)}
     * to enforce one bucket per slot. Without this, dropping a stack of buckets into an
     * output slot triggered a data-loss path: the auto-fill loop's {@code tryFillContainer}
     * extracted one bucket and {@code setStackInSlot}'d the single filled bucket back over
     * the entire stack, vaporising the rest. Both the slot capacity (read by the slot
     * widget's {@code getMaxStackSize}) and the per-call {@code insert} cap must reflect
     * the limit; previously only the inserter respected it while {@code getCapacityAsLong}
     * always returned 64 — so the slot widget cheerfully accepted the full stack.
     */
    public static void testItemHandlerRespectsConfiguredMaxStackSize(GameTestHelper helper) {
        //? if >=1.21.10 {
        ItemHandlerSimple handler = new ItemHandlerSimple(4, 1);
        long cap = handler.getCapacityAsLong(0, ItemResource.of(new ItemStack(Items.BUCKET)));
        assertTrue(cap == 1,
                "Slot capacity should be 1 for ItemHandlerSimple(4, 1), got " + cap);
        // insert() is also limited to 1 by the inserter
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = handler.insert(0, ItemResource.of(new ItemStack(Items.BUCKET)), 5, tx);
            assertTrue(inserted == 1,
                    "insert() should cap at 1 even when 5 are offered, got " + inserted);
            tx.commit();
        }
        assertTrue(handler.getAmountAsLong(0) == 1,
                "After capped insert, slot should hold 1 bucket, got " + handler.getAmountAsLong(0));
        //?} else {
        /*// 1.21.1: the classic IItemHandler surface — getSlotLimit drives SlotBase.getMaxStackSize,
        // and insertItem must cap at 1 via the limited inserter. getSlotLimit returning 64 (the
        // pre-fix default) is exactly the data-loss regression this pins.
        ItemHandlerSimple handler = new ItemHandlerSimple(4, 1);
        assertTrue(handler.getSlotLimit(0) == 1,
                "Slot limit should be 1 for ItemHandlerSimple(4, 1), got " + handler.getSlotLimit(0));
        ItemStack remainder = handler.insertItem(0, new ItemStack(Items.BUCKET, 5), false);
        assertTrue(remainder.getCount() == 4,
                "insertItem should cap at 1 and return the 4 rejected, got remainder " + remainder.getCount());
        assertTrue(handler.getStackInSlot(0).getCount() == 1,
                "After capped insert, slot should hold 1 bucket, got " + handler.getStackInSlot(0).getCount());*/
        //?}
        helper.succeed();
    }

    /**
     * Round-trip the tank state through {@code set(0, EMPTY, 0)} the way the new loadTank
     * helper does when the server's saveAdditional emits no key for an empty tank. Without
     * this clear, a client that previously held fluid would never see a server drain (the
     * NBT round-trip omits the absent key, the old guard skipped the update, and the stale
     * value lingered on the client — exactly the screenshot bug where the END's tank_output
     * showed empty server-side and 1000mB hot oil client-side).
     */
    public static void testTankClearsWhenLoadedFromEmptySave(GameTestHelper helper) {
        //? if >=1.21.10 {
        OutputTank tank = new OutputTank();
        // Seed with a non-empty value, mirroring a stale client-side cache.
        FluidResource hotOil = FluidResource.of(new FluidStack(Fluids.WATER, 1));
        try (Transaction tx = Transaction.openRoot()) {
            tank.insertInternal(0, hotOil, 1000, tx);
            tx.commit();
        }
        assertTrue(tank.getAmountAsLong(0) == 1000,
                "Setup: tank should hold 1000mB before the simulated load");
        // Simulated load: server saved no key (tank was empty there), so loadTank clears.
        tank.set(0, FluidResource.EMPTY, 0);
        assertTrue(tank.getAmountAsLong(0) == 0,
                "Tank must be empty after load-of-empty, got " + tank.getAmountAsLong(0));
        assertTrue(tank.getResource(0).isEmpty(),
                "Tank's resource must be EMPTY after load-of-empty");
        //?} else {
        /*// 1.21.1: setFluidStack(0, EMPTY) is the load-of-empty path (the NBT round-trip omits the
        // absent key; production's readData hands the empty stack through).
        OutputTank tank = new OutputTank();
        tank.fillInternal(new FluidStack(Fluids.WATER, 1000), false);
        assertTrue(tank.getAmountMb(0) == 1000,
                "Setup: tank should hold 1000mB before the simulated load");
        tank.setFluidStack(0, FluidStack.EMPTY);
        assertTrue(tank.getAmountMb(0) == 0,
                "Tank must be empty after load-of-empty, got " + tank.getAmountMb(0));
        assertTrue(tank.getFluidStack(0).isEmpty(),
                "Tank's resource must be EMPTY after load-of-empty");*/
        //?}
        helper.succeed();
    }

    /**
     * If any half of the craft step undersizes (here, the output is nearly full and accepts
     * less than requested), the wrapper code declines to commit the surrounding transaction.
     * Both the partial fill and the drain must roll back so no input fluid is consumed
     * without a matching output.
     */
    public static void testAtomicCraftRollsBackOnUndersizedFill(GameTestHelper helper) {
        //? if >=1.21.10 {
        OutputTank out = new OutputTank();
        FluidStacksResourceHandler in = new FluidStacksResourceHandler(1, 2000);
        FluidResource water = FluidResource.of(new FluidStack(Fluids.WATER, 1));
        // Output is 1mb shy of full — only 1mb of the requested 5 will fit.
        try (Transaction setup = Transaction.openRoot()) {
            out.insertInternal(0, water, 1999, setup);
            setup.commit();
        }
        in.set(0, water, 100);
        // Mimic the production code: probe both halves, abort the whole transaction if
        // either falls short of the requested amount.
        try (Transaction tx = Transaction.openRoot()) {
            int filled = out.insertInternal(0, water, 5, tx);
            int drained = in.extract(0, water, 5, tx);
            boolean ok = filled == 5 && drained == 5;
            if (ok) tx.commit();
        }
        assertTrue(out.getAmountAsLong(0) == 1999,
                "Output amount should be unchanged after rolled-back partial fill, got " + out.getAmountAsLong(0));
        assertTrue(in.getAmountAsLong(0) == 100,
                "Input amount should be unchanged after rolled-back drain, got " + in.getAmountAsLong(0));
        //?} else {
        /*// 1.21.1: no transactions — mirror production's simulate-first craft(): the partial
        // fill is never executed, so the drain must not happen either.
        OutputTank out = new OutputTank();
        BCFluidTank in = new BCFluidTank(1, 2000);
        // Output is 1mb shy of full — only 1mb of the requested 5 will fit.
        out.fillInternal(new FluidStack(Fluids.WATER, 1999), false);
        in.setFluidStack(0, new FluidStack(Fluids.WATER, 100));
        // Probe first: the partial fill must not be executed.
        FluidStack five = new FluidStack(Fluids.WATER, 5);
        int simFilled = out.fillInternal(five, true);
        assertTrue(simFilled == 1, "simulate fill should promise only 1mb, got " + simFilled);
        boolean ok = simFilled == 5;
        if (ok) in.drain(0, 5, false);
        assertTrue(out.getAmountMb(0) == 1999,
                "Output amount should be unchanged after rolled-back partial fill, got " + out.getAmountMb(0));
        assertTrue(in.getAmountMb(0) == 100,
                "Input amount should be unchanged after rolled-back drain, got " + in.getAmountMb(0));*/
        //?}
        helper.succeed();
    }
}
