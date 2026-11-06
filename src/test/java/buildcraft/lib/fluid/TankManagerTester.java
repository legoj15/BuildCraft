/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.fluid;

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
 * wrapped by a composite that delegates per slot). Every body runs on every node over
 * {@link BCFluidTank} and {@link BCTransaction}; the composite's resource-wide
 * {@code insert}/{@code extract} are the {@link BCFluidStorage} defaults, which differ by node:
 * on 1.21.10+ they accumulate across slots in one call (NeoForge's {@code ResourceHandler}),
 * on 1.21.1 they stop at the first slot that moves anything (the classic {@code IFluidHandler}
 * the Builder had there). The spillover tests pin each node's rule, and both rules fill and
 * drain slot 0 first, so a LIFO reorder fails on every node.
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
        // The simulate flag (heat exchanger and distiller crafts check with it): promises, mutates nothing.
        int simulated = tank.fill(0, water.copyWithAmount(5), true);
        assertTrue(simulated == 3, "simulated fill(5) into an empty cap-3 tank should promise 3, got " + simulated);
        assertTrue(tank.getAmount(0) == 0, "a simulated fill must not mutate the tank, got " + tank.getAmount(0));
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
        BCFluidStack simulated = tank.drain(0, 2, true);
        assertTrue(simulated.getAmount() == 2, "simulated drain(2) should promise 2, got " + simulated.getAmount());
        assertTrue(tank.getAmount(0) == 3, "a simulated drain must not mutate the tank, got " + tank.getAmount(0));
        try (BCTransaction tx = BCTransaction.openRoot()) {
            int drained = tank.extract(0, water, 5, tx);
            assertTrue(drained == 3, "extract(5) on a tank holding 3 should return 3, got " + drained);
            tx.commit();
        }
        assertTrue(tank.getAmount(0) == 0, "tank should be empty after drain, got " + tank.getAmount(0));
        helper.succeed();
    }

    /**
     * Composite fill, in slot order. Modelled on {@code TileBuilder.tankManager}: two cap-3 tanks
     * wrapped as a 2-slot composite, offering 5 of lava. On 1.21.10+ all 5 are accepted, laid down
     * as 3 in slot 0 then 2 in slot 1. On 1.21.1 the call stops at slot 0 (3 accepted, slot 1
     * untouched), as the Builder's classic fill did there; the remainder goes in only on a second
     * call.
     */
    public static void testCompositeInsertSpillsAcrossSlots(GameTestHelper helper) {
        BCFluidTank t0 = new BCFluidTank(1, 3);
        BCFluidTank t1 = new BCFluidTank(1, 3);
        BCFluidStorage composite = composeTwo(t0, t1);
        BCFluidStack lava = new BCFluidStack(Fluids.LAVA, 1);
        try (BCTransaction tx = BCTransaction.openRoot()) {
            int inserted = composite.insert(lava, 5, tx);
            //? if >=1.21.10 {
            assertTrue(inserted == 5, "composite insert(5) across two cap-3 tanks should accept all 5, got " + inserted);
            //?} else {
            /*assertTrue(inserted == 3, "1.21.1: composite insert(5) should stop at slot 0's 3, got " + inserted);
            assertTrue(t1.getAmount(0) == 0, "1.21.1: slot 1 is not tried in the same call, got " + t1.getAmount(0));
            inserted += composite.insert(lava, 5 - inserted, tx);
            assertTrue(inserted == 5, "1.21.1: the second call should place the remaining 2, got " + inserted);*/
            //?}
            tx.commit();
        }
        assertTrue(t0.getAmount(0) == 3, "slot 0 should be full at 3, got " + t0.getAmount(0));
        assertTrue(t1.getAmount(0) == 2, "slot 1 should hold spillover 2, got " + t1.getAmount(0));
        helper.succeed();
    }

    /**
     * Composite extract, in slot order. Pre-loaded as {3, 3} of lava, pulling 4: on 1.21.10+ 4
     * come back in one call (full slot 0 + 1 from slot 1); on 1.21.1 the call stops at slot 0's 3
     * (a Builder needing 4 there finds only 3), and the remainder needs a second call. Either way
     * 2 are left in slot 1, and a LIFO refactor (drain slot 1 first) fails.
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
            //? if >=1.21.10 {
            assertTrue(drained == 4, "composite extract(4) across two full cap-3 tanks should return 4, got " + drained);
            //?} else {
            /*assertTrue(drained == 3, "1.21.1: composite extract(4) should stop at slot 0's 3, got " + drained);
            assertTrue(t1.getAmount(0) == 3, "1.21.1: slot 1 is not tried in the same call, got " + t1.getAmount(0));
            drained += composite.extract(lava, 4 - drained, tx);
            assertTrue(drained == 4, "1.21.1: the second call should take the remaining 1, got " + drained);*/
            //?}
            tx.commit();
        }
        assertTrue(t0.getAmount(0) == 0, "slot 0 should be drained empty, got " + t0.getAmount(0));
        assertTrue(t1.getAmount(0) == 2, "slot 1 should hold 2 remaining, got " + t1.getAmount(0));
        helper.succeed();
    }

    /**
     * Simulation must not commit. The 1.12.2 {@code TankManager.fill(stack, doFill=false)}
     * "simulate" mode is an un-committed transaction on every node — an un-committed composite
     * insert leaves both tanks untouched (the load-bearing call sites, e.g.
     * {@code BlueprintBuilder} on schematic placement failure, rely on this for fluid
     * conservation under partial failure).
     */
    public static void testCompositeInsertRollsBackOnAbort(GameTestHelper helper) {
        BCFluidTank t0 = new BCFluidTank(1, 3);
        BCFluidTank t1 = new BCFluidTank(1, 3);
        BCFluidStorage composite = composeTwo(t0, t1);
        BCFluidStack water = new BCFluidStack(Fluids.WATER, 1);
        try (BCTransaction tx = BCTransaction.openRoot()) {
            int inserted = composite.insert(water, 5, tx);
            //? if >=1.21.10 {
            assertTrue(inserted == 5, "insert inside tx should still report 5 accepted, got " + inserted);
            //?} else {
            /*assertTrue(inserted == 3, "1.21.1: insert inside tx should report slot 0's 3 accepted, got " + inserted);*/
            //?}
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
