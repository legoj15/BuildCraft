/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.fluid;
// Version-split bodies: 1.21.10+ exercises the NeoForge Transfer API directly
// (ResourceHandler / FluidResource / FluidStacksResourceHandler / Transaction — no such API
// pre-1.21.9); 1.21.1 stands in the classic IFluidHandler surface (BCFluidTank's fill/drain
// plus FluidAction.SIMULATE) and pins the weaker cross-call composite properties — see the
// per-test notes.

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.fluids.FluidStack;
//? if >=1.21.10 {
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
//?} else {
/*import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;*/
//?}

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
        //? if >=1.21.10 {
        FluidStacksResourceHandler tank = new FluidStacksResourceHandler(1, 3);
        FluidResource water = FluidResource.of(new FluidStack(Fluids.WATER, 1));
        try (Transaction tx = Transaction.openRoot()) {
            int first = tank.insert(0, water, 2, tx);
            assertTrue(first == 2, "first insert into cap-3 tank with 2 should fit fully, got " + first);
            int second = tank.insert(0, water, 2, tx);
            assertTrue(second == 1, "second insert into a 2/3-full tank with 2 should only fit 1, got " + second);
            tx.commit();
        }
        assertTrue(tank.getAmountAsLong(0) == 3, "tank should be at capacity 3, got " + tank.getAmountAsLong(0));
        //?} else {
        /*BCFluidTank tank = new BCFluidTank(1, 3);
        FluidStack water = new FluidStack(Fluids.WATER, 2);
        int first = tank.fill(0, water, false);
        assertTrue(first == 2, "first fill into cap-3 tank with 2 should fit fully, got " + first);
        int second = tank.fill(0, water, false);
        assertTrue(second == 1, "second fill into a 2/3-full tank with 2 should only fit 1, got " + second);
        assertTrue(tank.getAmountMb(0) == 3, "tank should be at capacity 3, got " + tank.getAmountMb(0));*/
        //?}
        helper.succeed();
    }

    /** Extract more than is held → returns only what's actually present. */
    public static void testSingleTankExtractReturnsOnlyWhatExists(GameTestHelper helper) {
        //? if >=1.21.10 {
        FluidStacksResourceHandler tank = new FluidStacksResourceHandler(1, 3);
        FluidResource water = FluidResource.of(new FluidStack(Fluids.WATER, 1));
        tank.set(0, water, 3);
        try (Transaction tx = Transaction.openRoot()) {
            int drained = tank.extract(0, water, 5, tx);
            assertTrue(drained == 3, "extract(5) on a tank holding 3 should return 3, got " + drained);
            tx.commit();
        }
        assertTrue(tank.getAmountAsLong(0) == 0, "tank should be empty after drain, got " + tank.getAmountAsLong(0));
        //?} else {
        /*BCFluidTank tank = new BCFluidTank(1, 3);
        tank.setFluidStack(0, new FluidStack(Fluids.WATER, 3));
        FluidStack drained = tank.drain(0, 5, false);
        assertTrue(drained.getAmount() == 3, "drain(5) on a tank holding 3 should return 3, got " + drained.getAmount());
        assertTrue(tank.getAmountMb(0) == 0, "tank should be empty after drain, got " + tank.getAmountMb(0));*/
        //?}
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
        //? if >=1.21.10 {
        FluidStacksResourceHandler t0 = new FluidStacksResourceHandler(1, 3);
        FluidStacksResourceHandler t1 = new FluidStacksResourceHandler(1, 3);
        ResourceHandler<FluidResource> composite = composeTwo(t0, t1);
        FluidResource lava = FluidResource.of(new FluidStack(Fluids.LAVA, 1));
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = composite.insert(lava, 5, tx);
            assertTrue(inserted == 5, "composite insert(5) across two cap-3 tanks should accept all 5, got " + inserted);
            tx.commit();
        }
        assertTrue(t0.getAmountAsLong(0) == 3, "slot 0 should be full at 3, got " + t0.getAmountAsLong(0));
        assertTrue(t1.getAmountAsLong(0) == 2, "slot 1 should hold spillover 2, got " + t1.getAmountAsLong(0));
        //?} else {
        /*// 1.21.1: no in-one-call spillover — the classic composite returns at the first
        // matching slot, and each call re-offers its amount from scratch, so the caller
        // re-issues only the remainder to lay the 5 out as {3, 2}.
        BCFluidTank t0 = new BCFluidTank(1, 3);
        BCFluidTank t1 = new BCFluidTank(1, 3);
        IFluidHandler composite = composeTwo(t0, t1);
        FluidStack lava = new FluidStack(Fluids.LAVA, 5);
        int first = composite.fill(lava, FluidAction.EXECUTE);
        assertTrue(first == 3, "first fill(5) can only top up slot 0's cap of 3, got " + first);
        int second = composite.fill(new FluidStack(Fluids.LAVA, 5 - first), FluidAction.EXECUTE);
        assertTrue(second == 2, "second fill of the remaining 2 should land in slot 1, got " + second);
        assertTrue(first + second == 5, "conservation: the offered 5 must all be stored, got " + (first + second));
        assertTrue(t0.getAmountMb(0) == 3, "slot 0 should be full at 3, got " + t0.getAmountMb(0));
        assertTrue(t1.getAmountMb(0) == 2, "slot 1 should hold spillover 2, got " + t1.getAmountMb(0));*/
        //?}
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
        //? if >=1.21.10 {
        FluidStacksResourceHandler t0 = new FluidStacksResourceHandler(1, 3);
        FluidStacksResourceHandler t1 = new FluidStacksResourceHandler(1, 3);
        FluidResource lava = FluidResource.of(new FluidStack(Fluids.LAVA, 1));
        t0.set(0, lava, 3);
        t1.set(0, lava, 3);
        ResourceHandler<FluidResource> composite = composeTwo(t0, t1);
        try (Transaction tx = Transaction.openRoot()) {
            int drained = composite.extract(lava, 4, tx);
            assertTrue(drained == 4, "composite extract(4) across two full cap-3 tanks should return 4, got " + drained);
            tx.commit();
        }
        assertTrue(t0.getAmountAsLong(0) == 0, "slot 0 should be drained empty, got " + t0.getAmountAsLong(0));
        assertTrue(t1.getAmountAsLong(0) == 2, "slot 1 should hold 2 remaining, got " + t1.getAmountAsLong(0));
        //?} else {
        /*// 1.21.1: the classic composite drains only the first non-empty slot per call — the
        // caller re-issues the remainder to finish the pull across calls.
        BCFluidTank t0 = new BCFluidTank(1, 3);
        BCFluidTank t1 = new BCFluidTank(1, 3);
        t0.setFluidStack(0, new FluidStack(Fluids.LAVA, 3));
        t1.setFluidStack(0, new FluidStack(Fluids.LAVA, 3));
        IFluidHandler composite = composeTwo(t0, t1);
        FluidStack first = composite.drain(4, FluidAction.EXECUTE);
        assertTrue(first.getAmount() == 3, "first drain(4) can only pull slot 0's 3, got " + first.getAmount());
        FluidStack second = composite.drain(4 - first.getAmount(), FluidAction.EXECUTE);
        assertTrue(second.getAmount() == 1, "second drain of the remaining 1 should come from slot 1, got " + second.getAmount());
        assertTrue(first.getAmount() + second.getAmount() == 4, "conservation: 4 total drained");
        assertTrue(t0.getAmountMb(0) == 0, "slot 0 should be drained empty, got " + t0.getAmountMb(0));
        assertTrue(t1.getAmountMb(0) == 2, "slot 1 should hold 2 remaining, got " + t1.getAmountMb(0));*/
        //?}
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
        //? if >=1.21.10 {
        FluidStacksResourceHandler t0 = new FluidStacksResourceHandler(1, 3);
        FluidStacksResourceHandler t1 = new FluidStacksResourceHandler(1, 3);
        ResourceHandler<FluidResource> composite = composeTwo(t0, t1);
        FluidResource water = FluidResource.of(new FluidStack(Fluids.WATER, 1));
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = composite.insert(water, 5, tx);
            assertTrue(inserted == 5, "insert inside tx should still report 5 accepted, got " + inserted);
            // No tx.commit() — try-with-resources close aborts.
        }
        assertTrue(t0.getAmountAsLong(0) == 0, "slot 0 should be untouched after abort, got " + t0.getAmountAsLong(0));
        assertTrue(t1.getAmountAsLong(0) == 0, "slot 1 should be untouched after abort, got " + t1.getAmountAsLong(0));
        //?} else {
        /*// 1.21.1: no transactions — FluidAction.SIMULATE is the rollback stand-in.
        BCFluidTank t0 = new BCFluidTank(1, 3);
        BCFluidTank t1 = new BCFluidTank(1, 3);
        IFluidHandler composite = composeTwo(t0, t1);
        FluidStack water = new FluidStack(Fluids.WATER, 5);
        int promised = composite.fill(water, FluidAction.SIMULATE);
        assertTrue(promised == 3, "simulate fill should promise only slot 0's 3 of the offered 5, got " + promised);
        assertTrue(t0.getAmountMb(0) == 0, "SIMULATE must not mutate slot 0, got " + t0.getAmountMb(0));
        assertTrue(t1.getAmountMb(0) == 0, "SIMULATE must not mutate slot 1, got " + t1.getAmountMb(0));
        // The promise must be honest: executing the promised amount lands exactly that.
        int landed = composite.fill(new FluidStack(Fluids.WATER, promised), FluidAction.EXECUTE);
        assertTrue(landed == promised, "execute should deliver the promised " + promised + ", got " + landed);
        assertTrue(t0.getAmountMb(0) == 3 && t1.getAmountMb(0) == 0,
                "executed fill must land only in slot 0, got slot0=" + t0.getAmountMb(0) + " slot1=" + t1.getAmountMb(0));*/
        //?}
        helper.succeed();
    }

    //? if >=1.21.10 {
    /** Mirrors {@code TileBuilder.tankManager}: a 2-slot composite over two single-slot tanks. */
    private static ResourceHandler<FluidResource> composeTwo(FluidStacksResourceHandler t0,
                                                              FluidStacksResourceHandler t1) {
        FluidStacksResourceHandler[] tanks = { t0, t1 };
        return new ResourceHandler<>() {
            @Override
            public int size() { return tanks.length; }

            @Override
            public FluidResource getResource(int slot) {
                return slot >= 0 && slot < tanks.length ? tanks[slot].getResource(0) : FluidResource.EMPTY;
            }

            @Override
            public long getAmountAsLong(int slot) {
                return slot >= 0 && slot < tanks.length ? tanks[slot].getAmountAsLong(0) : 0;
            }

            @Override
            public long getCapacityAsLong(int slot, FluidResource resource) {
                return slot >= 0 && slot < tanks.length ? tanks[slot].getCapacityAsLong(0, resource) : 0;
            }

            @Override
            public boolean isValid(int slot, FluidResource resource) {
                return slot >= 0 && slot < tanks.length && tanks[slot].isValid(0, resource);
            }

            @Override
            public int insert(int slot, FluidResource resource, int amount, TransactionContext ctx) {
                return slot >= 0 && slot < tanks.length ? tanks[slot].insert(0, resource, amount, ctx) : 0;
            }

            @Override
            public int extract(int slot, FluidResource resource, int amount, TransactionContext ctx) {
                return slot >= 0 && slot < tanks.length ? tanks[slot].extract(0, resource, amount, ctx) : 0;
            }
        };
    }
    //?} else {
    /*// 1.21.1 mirror of TileBuilder.tankManager's classic composite: fill and drain both
    // return at the first matching slot per call — no in-call spillover.
    private static IFluidHandler composeTwo(BCFluidTank t0, BCFluidTank t1) {
        BCFluidTank[] tanks = { t0, t1 };
        return new IFluidHandler() {
            @Override
            public int getTanks() { return tanks.length; }

            @Override
            public FluidStack getFluidInTank(int slot) {
                return slot >= 0 && slot < tanks.length ? tanks[slot].getFluidStack(0) : FluidStack.EMPTY;
            }

            @Override
            public int getTankCapacity(int slot) {
                return slot >= 0 && slot < tanks.length ? tanks[slot].getCapacityMb(0) : 0;
            }

            @Override
            public boolean isFluidValid(int slot, FluidStack stack) {
                return slot >= 0 && slot < tanks.length && tanks[slot].isFluidValid(0, stack);
            }

            @Override
            public int fill(FluidStack resource, FluidAction action) {
                for (int i = 0; i < tanks.length; i++) {
                    int n = tanks[i].fill(0, resource, action.simulate());
                    if (n > 0) {
                        return n;
                    }
                }
                return 0;
            }

            @Override
            public FluidStack drain(FluidStack resource, FluidAction action) {
                for (int i = 0; i < tanks.length; i++) {
                    FluidStack inTank = tanks[i].getFluidStack(0);
                    if (!inTank.isEmpty() && FluidStack.isSameFluidSameComponents(inTank, resource)) {
                        return tanks[i].drain(0, resource.getAmount(), action.simulate());
                    }
                }
                return FluidStack.EMPTY;
            }

            @Override
            public FluidStack drain(int maxDrain, FluidAction action) {
                for (int i = 0; i < tanks.length; i++) {
                    if (!tanks[i].getFluidStack(0).isEmpty()) {
                        return tanks[i].drain(0, maxDrain, action.simulate());
                    }
                }
                return FluidStack.EMPTY;
            }
        };
    }*/
    //?}
}
