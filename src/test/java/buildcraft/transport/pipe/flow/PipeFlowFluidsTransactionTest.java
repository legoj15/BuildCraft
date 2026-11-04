/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.pipe.flow;

import java.lang.reflect.Proxy;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.material.Fluids;


import buildcraft.VanillaSetupBaseTester;

import buildcraft.api.fluids.BCFluidStack;
import buildcraft.platform.transfer.BCFluidStorage;
import buildcraft.lib.transfer.FluidStorageUtil;
import buildcraft.platform.transfer.BCTransaction;
import buildcraft.api.transport.pipe.IPipeHolder;

import buildcraft.lib.fluid.BCFluidTank;
import buildcraft.lib.misc.CapUtil;

import buildcraft.transport.BCTransportPipes;
import buildcraft.transport.pipe.Pipe;

/**
 * A fluid pipe's per-face section must take part in the caller's transaction: fluid inserted inside a
 * transaction that is then aborted must leave the pipe exactly as it was. Before the fix the section
 * committed in place regardless, so the source's extraction rolled back while the pipe kept the fluid —
 * every simulated {@code ResourceHandlerUtil.move} into a pipe (and every move it rolled back because the
 * source could not deliver) minted fluid out of nothing.
 *
 * <p>The pipe is built on a stub holder with a TILE connection on {@link #FACE}; nothing here needs a world.
 * Extends {@link VanillaSetupBaseTester} because a 26.x {@code BCFluidStack} needs bound default components.
 */
public class PipeFlowFluidsTransactionTest extends VanillaSetupBaseTester {

    private static final Direction FACE = Direction.WEST;

    private static PipeFlowFluids newConnectedFluidPipe() {
        IPipeHolder holder = (IPipeHolder) Proxy.newProxyInstance(IPipeHolder.class.getClassLoader(),
                new Class<?>[] { IPipeHolder.class }, (proxy, method, args) -> switch (method.getName()) {
                    case "fireEvent" -> false; // no pluggables, no behaviour listeners: nothing cancels
                    case "getPluggable", "getNeighbourPipe", "getNeighbourTile" -> null;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "StubPipeHolder";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        Pipe pipe = new Pipe(holder, BCTransportPipes.cobbleFluid);
        CompoundTag connections = new CompoundTag();
        connections.putInt("con", 0b10 << (FACE.ordinal() * 2)); // TILE on FACE
        pipe.readFromNbt(connections);
        Assertions.assertTrue(pipe.isConnected(FACE), "precondition: the stub pipe is connected on " + FACE);
        return (PipeFlowFluids) pipe.getFlow();
    }

    private static BCFluidTank waterTank(int amount) {
        BCFluidTank tank = new BCFluidTank(1, 4000);
        Assertions.assertEquals(amount, tank.fill(0, new BCFluidStack(Fluids.WATER, amount), false));
        return tank;
    }

    private static BCFluidStorage section(PipeFlowFluids flow, Direction face) {
        return flow.getCapability(CapUtil.CAP_FLUIDS, face);
    }

    /** The mB held across all seven sections. */
    private static long pipeTotal(PipeFlowFluids flow) {
        long total = section(flow, null).getAmount(0);
        for (Direction face : Direction.values()) {
            total += section(flow, face).getAmount(0);
        }
        return total;
    }

    @Test
    public void simulatedMoveIntoPipeMintsNoFluid() {
        PipeFlowFluids flow = newConnectedFluidPipe();
        BCFluidTank tank = waterTank(1000);

        int simulated;
        try (BCTransaction tx = BCTransaction.openRoot()) {
            simulated = FluidStorageUtil.move(tank, section(flow, FACE), r -> true, 100, tx);
            // not committed: the canonical Transfer-API dry run
        }
        Assertions.assertTrue(simulated > 0, "precondition: the dry run must report an accepted amount");
        Assertions.assertEquals(1000, tank.getAmountMb(0), "the source rolls back its extraction");
        Assertions.assertEquals(0, pipeTotal(flow),
                "an aborted insert must leave the pipe empty, or the dry run minted " + pipeTotal(flow) + " mB");
        Assertions.assertNull(flow.getFluidStackForRender(),
                "an aborted first insert must also roll back the pipe's fluid type");
    }

    @Test
    public void nestedAbortRestoresExactStateAndLaterCommitStillLands() {
        PipeFlowFluids flow = newConnectedFluidPipe();
        BCFluidTank tank = waterTank(1000);

        int moved;
        try (BCTransaction root = BCTransaction.openRoot()) {
            try (BCTransaction nested = root.openNested()) {
                FluidStorageUtil.move(tank, section(flow, FACE), r -> true, 100, nested);
                // nested aborted: what ResourceHandlerUtil.move itself does when the source under-delivers
            }
            Assertions.assertEquals(0, pipeTotal(flow), "the nested abort must already have restored the pipe");
            moved = FluidStorageUtil.move(tank, section(flow, FACE), r -> true, 100, root);
            root.commit();
        }
        Assertions.assertTrue(moved > 0, "precondition: the committed move must transfer something");
        Assertions.assertEquals(1000 - moved, tank.getAmountMb(0));
        Assertions.assertEquals(moved, pipeTotal(flow), "a committed move lands exactly what left the tank");
        Assertions.assertEquals(1000, tank.getAmountMb(0) + pipeTotal(flow), "fluid is conserved");
    }
}
