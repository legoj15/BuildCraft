/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.fluid;

// Version-split bodies: 1.21.10+ exercises the NeoForge Transfer API directly
// (ResourceHandler / FluidResource / BCFluidTank / Transaction — no such API
// pre-1.21.9); 1.21.1 stands in the classic IFluidHandler surface (BCFluidTank's fill/drain
// plus FluidAction.SIMULATE) and pins the weaker cross-call composite properties — see the
// per-test notes.

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.material.Fluids;

import buildcraft.api.fluids.BCFluidStack;
import buildcraft.platform.transfer.BCFluidStorage;
import buildcraft.platform.transfer.BCTransactionContext;
import buildcraft.platform.transfer.BCTransaction;


/**
 * Tank bookkeeping coverage. The 1.12.2 original tested a {@code TankManager} class that owned
 * an ordered list of {@code Tank}s and routed fill/drain across them with spillover semantics.
 * Neither class exists in the modern port — the composite-tank pattern it used to provide is
 * now hand-rolled per tile (see {@code TileBuilder.tankManager}: an array of single-slot tanks
 * wrapped by a composite that delegates per slot). On 1.21.10+ the composite is a
 * {@code ResourceHandler<FluidResource>} whose slot-less {@code insert}/{@code extract} —
 * interface defaults — accumulate across slots in one call; on 1.21.1 it is a classic
 * {@code IFluidHandler} whose {@code fill}/{@code drain} return at the first matching slot, so
 * the composite tests there pin the weaker cross-call properties instead: ordered routing and
 * drain (slot 0 always fills/empties first — a LIFO reorder lands the amounts in the wrong
 * slots), conservation across calls, and {@code FluidAction.SIMULATE} non-mutation standing in
 * for transaction rollback.
 * <p>
 * These tests pin that pattern's bookkeeping so a refactor of the composite-tank wiring on
 * any tile (Builder, Pump, Tank, FloodGate, …) can't silently regress capacity-respect or
 * cross-slot spillover behaviour.
 */
public class TankManagerTester {

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) throw new IllegalStateException(msg);
    }

    /** Cap-3 single-slot tank: insert 2 → 2; insert 2 more → 1 (only 1 of capacity left). */
    public static void testSingleTankCapacityRespect(GameTestHelper helper) {
        BCFluidTank tank = new BCFluidTank(1, 3);
        BCFluidStack water = new BCFluidStack(Fluids.WATER, 1);
        try (BCTransaction tx = BCTransaction.openRoot()) {
            int first = tank.insert(0, water, 2, tx);
            assertTrue(first == 2, "first insert into cap-3 tank with 2 should fit fully, got " + first);
            int second = tank.insert(0, water, 2, tx);
            assertTrue(second == 1, "second insert into a 2/3-full tank with 2 should only fit 1, got " + second);
            tx.commit();
        }
        assertTrue(tank.getAmount(0) == 3, "tank should be at capacity 3, got " + tank.getAmount(0));
        helper.succeed();
    }

    /** Extract more than is held → returns only what's actually present. */
    public static void testSingleTankExtractReturnsOnlyWhatExists(GameTestHelper helper) {
        BCFluidTank tank = new BCFluidTank(1, 3);
        BCFluidStack water = new BCFluidStack(Fluids.WATER, 1);
        tank.setFluidStack(0, water.copyWithAmount(3));
        try (BCTransaction tx = BCTransaction.openRoot()) {
            int drained = tank.extract(0, water, 5, tx);
            assertTrue(drained == 3, "extract(5) on a tank holding 3 should return 3, got " + drained);
            tx.commit();
        }
        assertTrue(tank.getAmount(0) == 0, "tank should be empty after drain, got " + tank.getAmount(0));
        helper.succeed();
    }

    /**
     * Composite fill spills across slots in order. Modelled on {@code TileBuilder.tankManager}:
     * two cap-3 tanks wrapped as a 2-slot composite, offering 5 of lava → 5 accepted, laid down
     * as 3 in slot 0 then 2 in slot 1. 1.21.1's classic composite only touches the first
     * matching slot per call (and each call re-offers its amount from scratch), so the same
     * layout is built cross-call — fill(5) → 3, then fill(remainder 2) → 2 — and pinned as
     * the weaker cross-call property: total conserved, slot 0 full before slot 1 gains
     * anything (a LIFO reorder ends with the per-slot amounts swapped and fails).
     */
    public static void testCompositeInsertSpillsAcrossSlots(GameTestHelper helper) {
        BCFluidTank t0 = new BCFluidTank(1, 3);
        BCFluidTank t1 = new BCFluidTank(1, 3);
        BCFluidStorage composite = composeTwo(t0, t1);
        BCFluidStack lava = new BCFluidStack(Fluids.LAVA, 1);
        try (BCTransaction tx = BCTransaction.openRoot()) {
            int inserted = composite.insert(lava, 5, tx);
            assertTrue(inserted == 5, "composite insert(5) across two cap-3 tanks should accept all 5, got " + inserted);
            tx.commit();
        }
        assertTrue(t0.getAmount(0) == 3, "slot 0 should be full at 3, got " + t0.getAmount(0));
        assertTrue(t1.getAmount(0) == 2, "slot 1 should hold spillover 2, got " + t1.getAmount(0));
        helper.succeed();
    }

    /**
     * Composite extract drains across slots in order until the request is satisfied. Pre-loaded
     * as {3, 3} of lava, pulling 4 → 4 returned (full slot 0 + 1 from slot 1), leaving 2 in slot
     * 1. 1.21.1's classic composite drains only the first non-empty slot per call, so the 4 is
     * pulled as drain(4) → 3 then drain(remainder 1) → 1 — re-issuing the remainder is the
     * cross-call shape of the same property. Either way a LIFO refactor (drain slot 1 first)
     * leaves the per-slot amounts wrong and fails.
     */
    public static void testCompositeExtractDrainsAcrossSlots(GameTestHelper helper) {
        BCFluidTank t0 = new BCFluidTank(1, 3);
        BCFluidTank t1 = new BCFluidTank(1, 3);
        BCFluidStack lava = new BCFluidStack(Fluids.LAVA, 1);
        t0.setFluidStack(0, lava.copyWithAmount(3));
        t1.setFluidStack(0, lava.copyWithAmount(3));
        BCFluidStorage composite = composeTwo(t0, t1);
        try (BCTransaction tx = BCTransaction.openRoot()) {
            int drained = composite.extract(lava, 4, tx);
            assertTrue(drained == 4, "composite extract(4) across two full cap-3 tanks should return 4, got " + drained);
            tx.commit();
        }
        assertTrue(t0.getAmount(0) == 0, "slot 0 should be drained empty, got " + t0.getAmount(0));
        assertTrue(t1.getAmount(0) == 2, "slot 1 should hold 2 remaining, got " + t1.getAmount(0));
        helper.succeed();
    }

    /**
     * Simulation must not commit. The 1.12.2 {@code TankManager.fill(stack, doFill=false)}
     * "simulate" mode is a real transaction on 1.21.10+ — an un-committed composite insert
     * leaves both tanks untouched (the load-bearing call sites, e.g. {@code BlueprintBuilder}
     * on schematic placement failure, rely on this for fluid conservation under partial
     * failure). 1.21.1 has no transactions: {@code FluidAction.SIMULATE} stands in — the
     * composite must promise the first slot's remaining 3 of the offered 5, mutate nothing,
     * and deliver exactly the promise on execute.
     */
    public static void testCompositeInsertRollsBackOnAbort(GameTestHelper helper) {
        BCFluidTank t0 = new BCFluidTank(1, 3);
        BCFluidTank t1 = new BCFluidTank(1, 3);
        BCFluidStorage composite = composeTwo(t0, t1);
        BCFluidStack water = new BCFluidStack(Fluids.WATER, 1);
        try (BCTransaction tx = BCTransaction.openRoot()) {
            int inserted = composite.insert(water, 5, tx);
            assertTrue(inserted == 5, "insert inside tx should still report 5 accepted, got " + inserted);
            // No tx.commit() — try-with-resources close aborts.
        }
        assertTrue(t0.getAmount(0) == 0, "slot 0 should be untouched after abort, got " + t0.getAmount(0));
        assertTrue(t1.getAmount(0) == 0, "slot 1 should be untouched after abort, got " + t1.getAmount(0));
        helper.succeed();
    }

    /** Mirrors {@code TileBuilder.tankManager}: a 2-slot composite over two single-slot tanks. */
    private static BCFluidStorage composeTwo(BCFluidTank t0,
                                                              BCFluidTank t1) {
        BCFluidTank[] tanks = { t0, t1 };
        return new BCFluidStorage() {
            @Override
            public int size() { return tanks.length; }

            @Override
            public BCFluidStack getStack(int slot) {
                return slot >= 0 && slot < tanks.length ? tanks[slot].getStack(0) : BCFluidStack.EMPTY;
            }

            @Override
            public int getCapacity(int slot, BCFluidStack resource) {
                return slot >= 0 && slot < tanks.length ? tanks[slot].getCapacity(0, resource) : 0;
            }

            @Override
            public boolean isValid(int slot, BCFluidStack resource) {
                return slot >= 0 && slot < tanks.length && tanks[slot].isValid(0, resource);
            }

            @Override
            public int insert(int slot, BCFluidStack resource, int amount, BCTransactionContext ctx) {
                return slot >= 0 && slot < tanks.length ? tanks[slot].insert(0, resource, amount, ctx) : 0;
            }

            @Override
            public int extract(int slot, BCFluidStack resource, int amount, BCTransactionContext ctx) {
                return slot >= 0 && slot < tanks.length ? tanks[slot].extract(0, resource, amount, ctx) : 0;
            }
        };
    }
}
