/*
 * Copyright (c) 2026 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.fluid;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Tank bookkeeping coverage. The 1.12.2 original tested a {@code TankManager} class that owned
 * an ordered list of {@code Tank}s and routed fill/drain across them with spillover semantics.
 * Neither class exists in 26.1.x — NeoForge's {@link ResourceHandler}{@code <FluidResource>}
 * with explicit {@link Transaction}s replaced both. The composite-tank pattern that {@code
 * TankManager} used to provide is now hand-rolled per tile (see {@code TileBuilder.tankManager}
 * around lines 145-181 of {@code TileBuilder.java}: an array of single-slot {@link
 * FluidStacksResourceHandler}s wrapped by a {@code ResourceHandler<FluidResource>} that
 * delegates per slot, with the slot-less {@code insert(resource, amount, tx)} variant
 * inherited from the interface's default implementation walking slots in order).
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
        helper.succeed();
    }

    /** Extract more than is held → returns only what's actually present. */
    public static void testSingleTankExtractReturnsOnlyWhatExists(GameTestHelper helper) {
        FluidStacksResourceHandler tank = new FluidStacksResourceHandler(1, 3);
        FluidResource water = FluidResource.of(new FluidStack(Fluids.WATER, 1));
        tank.set(0, water, 3);
        try (Transaction tx = Transaction.openRoot()) {
            int drained = tank.extract(0, water, 5, tx);
            assertTrue(drained == 3, "extract(5) on a tank holding 3 should return 3, got " + drained);
            tx.commit();
        }
        assertTrue(tank.getAmountAsLong(0) == 0, "tank should be empty after drain, got " + tank.getAmountAsLong(0));
        helper.succeed();
    }

    /**
     * Slot-less {@code insert(resource, amount, ctx)} on a composite handler must spill across
     * slots in order. Modelled on {@code TileBuilder.tankManager}: two cap-3 tanks wrapped as
     * a 2-slot composite, insert 5 of lava → 5 accepted (3 in slot 0, 2 in slot 1).
     */
    public static void testCompositeInsertSpillsAcrossSlots(GameTestHelper helper) {
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
        helper.succeed();
    }

    /**
     * Slot-less {@code extract(resource, amount, ctx)} on a composite must drain across slots
     * in order until the request is satisfied. Pre-loaded as {3, 3} of lava, drain 4 → 4 returned
     * (full slot 0 + 1 from slot 1), leaving 2 in slot 1. If anyone refactors the composite to
     * drain slot 1 first (e.g. LIFO), the slot-1 amount assertion catches it.
     */
    public static void testCompositeExtractDrainsAcrossSlots(GameTestHelper helper) {
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
        helper.succeed();
    }

    /**
     * Transaction rollback must un-do composite inserts so the underlying tanks see no change.
     * This is the behaviour the 1.12.2 {@code TankManager.fill(stack, doFill=false)} "simulate"
     * mode used to approximate; the modern transaction system gives it for real and the load-
     * bearing call sites (e.g. {@code BlueprintBuilder} on schematic placement failure) rely on
     * it for fluid conservation under partial-failure conditions.
     */
    public static void testCompositeInsertRollsBackOnAbort(GameTestHelper helper) {
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
        helper.succeed();
    }

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
}
