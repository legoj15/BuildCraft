/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.fluid;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.world.level.material.Fluids;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.api.fluids.BCFluidStack;

/**
 * An emptied tank must keep rendering its last fluid while the smoothed level drains to zero, as the 1.12.2 smoother
 * did (its client kept the last fluid link). Without that the last of the fluid pops out of the tank in one frame and
 * the heat exchanger's flow, which outlives its input tank by the whole STOPPING ramp, vanishes the moment the input
 * runs dry.
 */
public class FluidSmootherTest extends VanillaSetupBaseTester {

    @Test
    void emptiedTankDrainsItsLastFluidSmoothly() {
        BCFluidTank tank = new BCFluidTank(1, 2000);
        FluidSmoother smoother = new FluidSmoother(tank);
        tank.setFluidStack(0, new BCFluidStack(Fluids.LAVA, 1000));
        smoother.tick();

        // Drain rather than replace: on 1.21.1 the tank shrinks its live stack in place, which would also empty a
        // remembered fluid that shared the reference.
        tank.drain(0, 1000, false);
        Assertions.assertTrue(tank.isTankEmpty(0), "the drain left fluid behind");
        smoother.tick();

        FluidSmoother.FluidStackInterp interp = smoother.getFluidForRender(1);
        Assertions.assertNotNull(interp, "the draining tank stopped rendering the moment it emptied");
        Assertions.assertTrue(interp.fluid().is(Fluids.LAVA), "the draining tank lost its fluid type");
        Assertions.assertTrue(interp.amount() > 0, "the displayed level snapped to zero");
        Assertions.assertTrue(smoother.getRenderFluid().is(Fluids.LAVA), "the flow's fluid vanished with the tank");

        for (int i = 0; i < 100; i++) {
            smoother.tick();
        }
        Assertions.assertEquals(0, smoother.getFluidForRender(1).amount(), "the displayed level never reached zero");

        tank.setFluidStack(0, new BCFluidStack(Fluids.WATER, 500));
        smoother.tick();
        Assertions.assertTrue(smoother.getFluidForRender(1).fluid().is(Fluids.WATER),
                "a refill with a new fluid still shows the old one");
    }
}
