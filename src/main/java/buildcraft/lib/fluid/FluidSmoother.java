/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.fluid;

import buildcraft.api.fluids.BCFluidStack;

/**
 * Provides smooth client-side interpolation of fluid tank levels for rendering.
 * <p>
 * The server sets the target amount via block entity sync packets. Each client
 * tick, the displayed amount moves toward the target at a rate proportional to
 * the distance, producing a smooth fill/drain animation instead of sudden jumps.
 * <p>
 * Simplified port of the 1.12.2 FluidSmoother that works with the standard
 * version-neutral {@link BCFluidTank} facade and {@code sendBlockUpdated()} sync mechanism.
 */
public class FluidSmoother {

    private final BCFluidTank tank;

    /** The amount currently displayed on the client. */
    private double displayAmount;
    /** The previous tick's display amount, for per-frame interpolation. */
    private double displayAmountPrev;
    /** Whether the smoother has been initialized with a starting value. */
    private boolean initialized = false;
    /**
     * The last fluid the tank held. Kept after the tank empties, as the 1.12.2 client kept its fluid link, so the
     * level drains to zero and the heat exchanger's flow recedes instead of popping out with the tank.
     */
    private BCFluidStack lastFluid = BCFluidStack.EMPTY;

    public FluidSmoother(BCFluidTank tank) {
        this.tank = tank;
    }

    /**
     * Call every tick (client-side). Moves the display amount toward the
     * tank's actual amount, producing a smooth transition.
     */
    public void tick() {
        int target = tank.getAmountMb(0);
        getRenderFluid(); // records the fluid so a tank that empties between frames still drains visibly

        if (!initialized) {
            displayAmount = target;
            displayAmountPrev = target;
            initialized = true;
            return;
        }

        displayAmountPrev = displayAmount;

        if (displayAmount != target) {
            double delta = target - displayAmount;
            // Move by at least 1 mB, or ~20% of the remaining distance per tick
            // This gives a nice ease-out curve that converges in ~10-15 ticks
            double step = Math.max(1.0, Math.abs(delta) * 0.2);
            if (Math.abs(delta) <= step) {
                displayAmount = target;
            } else {
                displayAmount += Math.signum(delta) * step;
            }
        }
    }

    /**
     * Resets the smoother so the display amount immediately snaps to the
     * current tank level without any interpolation.
     */
    public void resetSmoothing() {
        getRenderFluid();
        displayAmount = tank.getAmountMb(0);
        displayAmountPrev = displayAmount;
        initialized = true;
    }

    /**
     * Returns the interpolated fluid amount for rendering at the given
     * partial tick fraction.
     *
     * @param partialTicks fractional tick progress (0.0 to 1.0)
     * @return smoothed fluid amount
     */
    public double getAmount(double partialTicks) {
        return displayAmountPrev + (displayAmount - displayAmountPrev) * partialTicks;
    }

    /**
     * Returns the tank's fluid stack, or the last fluid it held once empty (type only — use {@link #getAmount} for
     * the smoothed quantity). Empty only if the tank never held anything.
     */
    public BCFluidStack getRenderFluid() {
        BCFluidStack current = tank.getFluidStack(0);
        // Copy on change: the tank may shrink its own stack in place, which would empty a shared reference.
        if (!current.isEmpty() && !BCFluidStack.isSameFluidSameComponents(current, lastFluid)) {
            lastFluid = current.copyWithAmount(1);
        }
        return lastFluid;
    }

    /** Delegate for {@link BCFluidTank#getCapacityMb}. */
    public int getCapacity() {
        return tank.getCapacityMb(0);
    }

    /** Returns the raw (non-smoothed) current display amount. */
    public double getDisplayAmount() {
        return displayAmount;
    }

    public void getDebugInfo(java.util.List<String> left, java.util.List<String> right, net.minecraft.core.Direction side) {
        String contents = (!tank.isTankEmpty(0)) ? "Fluid" : "Empty";
        left.add("smooth = " + String.format("%.1f", displayAmount) + " / " + target() + " (" + contents + ")");
    }

    private int target() {
        return tank.getAmountMb(0);
    }

    /**
     * Result record for convenience rendering methods.
     */
    public record FluidStackInterp(BCFluidStack fluid, double amount) {}

    /**
     * Returns a combined fluid type + smoothed amount for rendering, or {@code null} if the tank never held
     * anything. An emptied tank keeps its last fluid while the amount drains to zero; callers skip a zero amount.
     */
    public FluidStackInterp getFluidForRender(double partialTicks) {
        BCFluidStack res = getRenderFluid();
        if (res.isEmpty()) {
            return null;
        }
        return new FluidStackInterp(res, getAmount(partialTicks));
    }
}
