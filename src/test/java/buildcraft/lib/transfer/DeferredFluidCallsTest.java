/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.transfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import net.minecraft.world.level.material.Fluids;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.api.core.IFluidFilter;
import buildcraft.api.core.IFluidHandlerAdv;
import buildcraft.api.fluids.BCFluidStack;
import buildcraft.lib.fluid.BCFluidTank;
import buildcraft.platform.transfer.BCFluidStorage;
import buildcraft.platform.transfer.BCTransaction;

/**
 * A view over a handler that can only simulate ({@link DeferredFluidCalls}, here through another mod's robot-API
 * {@link IFluidHandlerAdv}) promises nothing twice: its simulations cannot see what the open transaction already owes,
 * so without the bookkeeping a second fill was answered from the handler's untouched state and lost at the replay.
 */
public class DeferredFluidCallsTest extends VanillaSetupBaseTester {
    private static final BCFluidStack WATER = new BCFluidStack(Fluids.WATER, 1);

    /** One 1000 mB tank behind the simulate-flag API, not one of BuildCraft's own adapters. */
    private static final class OneTank implements IFluidHandlerAdv {
        BCFluidStack held = BCFluidStack.EMPTY;

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public BCFluidStack getFluidInTank(int tank) {
            return held;
        }

        @Override
        public int getTankCapacity(int tank) {
            return 1000;
        }

        @Override
        public int fill(BCFluidStack resource, boolean simulate) {
            if (!held.isEmpty() && !BCFluidStack.isSameFluidSameComponents(held, resource)) {
                return 0;
            }
            int filled = Math.min(resource.getAmount(), 1000 - held.getAmount());
            if (!simulate && filled > 0) {
                held = resource.copyWithAmount(held.getAmount() + filled);
            }
            return filled;
        }

        @Override
        public BCFluidStack drain(BCFluidStack resource, boolean simulate) {
            if (held.isEmpty() || !BCFluidStack.isSameFluidSameComponents(held, resource)) {
                return BCFluidStack.EMPTY;
            }
            return drain(resource.getAmount(), simulate);
        }

        @Override
        public BCFluidStack drain(int maxDrain, boolean simulate) {
            int drained = Math.min(maxDrain, held.getAmount());
            if (drained <= 0) {
                return BCFluidStack.EMPTY;
            }
            BCFluidStack out = held.copyWithAmount(drained);
            if (!simulate) {
                held = drained == held.getAmount() ? BCFluidStack.EMPTY : held.copyWithAmount(held.getAmount() - drained);
            }
            return out;
        }

        @Override
        public BCFluidStack drain(IFluidFilter filter, int maxDrain, boolean simulate) {
            return held.isEmpty() || !filter.matches(held) ? BCFluidStack.EMPTY : drain(maxDrain, simulate);
        }
    }

    @Test
    public void aSecondFillInOneTransactionGetsOnlyWhatIsLeft() {
        OneTank handler = new OneTank();
        BCFluidStorage view = FluidHandlerAdvs.storage(handler);
        try (BCTransaction tx = BCTransaction.openRoot()) {
            assertEquals(700, view.insert(WATER, 700, tx));
            assertEquals(300, view.insert(WATER, 700, tx), "1000 mB of room, 700 already promised");
            assertEquals(0, view.insert(WATER, 700, tx));
            assertEquals(0, handler.held.getAmount(), "nothing real happens before the root commits");
            tx.commit();
        }
        assertEquals(1000, handler.held.getAmount(), "the replay delivered every promise");
    }

    @Test
    public void aSecondDrainInOneTransactionGetsOnlyWhatIsLeft() {
        OneTank handler = new OneTank();
        handler.held = WATER.copyWithAmount(500);
        BCFluidStorage view = FluidHandlerAdvs.storage(handler);
        try (BCTransaction tx = BCTransaction.openRoot()) {
            assertEquals(400, view.extract(WATER, 400, tx));
            assertEquals(100, view.extract(WATER, 400, tx), "500 held, 400 already promised");
            tx.commit();
        }
        assertEquals(0, handler.held.getAmount());
    }

    /** A station's input is asked for on every candidate of a robot's search: each lookup of one handler or
     *  storage is the same view, so it is not wrapped again and its callers share one queue of owed calls. */
    @Test
    public void eachHandlerOrStorageIsOneViewWhileItIsInUse() {
        OneTank handler = new OneTank();
        BCFluidStorage view = FluidHandlerAdvs.storage(handler);
        assertSame(view, FluidHandlerAdvs.storage(handler));
        assertSame(handler, FluidHandlerAdvs.of(view), "a view unwraps to the handler itself");

        BCFluidTank tank = new BCFluidTank(1, 1000);
        IFluidHandlerAdv asHandler = FluidHandlerAdvs.of(tank);
        assertSame(asHandler, FluidHandlerAdvs.of(tank));
        assertSame(tank, FluidHandlerAdvs.storage(asHandler));
    }

    @Test
    public void anAbortedNestedCallIsNoLongerOwed() {
        OneTank handler = new OneTank();
        BCFluidStorage view = FluidHandlerAdvs.storage(handler);
        try (BCTransaction tx = BCTransaction.openRoot()) {
            try (BCTransaction nested = tx.openNested()) {
                assertEquals(1000, view.insert(WATER, 1000, nested));
            }
            assertEquals(1000, view.insert(WATER, 1000, tx), "the aborted promise was dropped");
            tx.commit();
        }
        assertEquals(1000, handler.held.getAmount());
    }
}
