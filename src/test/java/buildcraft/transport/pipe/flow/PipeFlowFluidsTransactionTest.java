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

import net.neoforged.neoforge.fluids.FluidStack;
//? if >=1.21.10 {
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
//?} else {
/*import net.neoforged.neoforge.fluids.capability.IFluidHandler;*/
//?}

import buildcraft.VanillaSetupBaseTester;

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
 * Extends {@link VanillaSetupBaseTester} because a 26.x {@code FluidStack} needs bound default components.
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
        Assertions.assertEquals(amount, tank.fill(0, new FluidStack(Fluids.WATER, amount), false));
        return tank;
    }

    //? if >=1.21.10 {
    private static ResourceHandler<FluidResource> section(PipeFlowFluids flow, Direction face) {
        return flow.getCapability(CapUtil.CAP_FLUIDS, face);
    }

    /** The mB held across all seven sections. */
    private static long pipeTotal(PipeFlowFluids flow) {
        long total = section(flow, null).getAmountAsLong(0);
        for (Direction face : Direction.values()) {
            total += section(flow, face).getAmountAsLong(0);
        }
        return total;
    }

    @Test
    public void simulatedMoveIntoPipeMintsNoFluid() {
        PipeFlowFluids flow = newConnectedFluidPipe();
        BCFluidTank tank = waterTank(1000);

        int simulated;
        try (Transaction tx = Transaction.openRoot()) {
            simulated = ResourceHandlerUtil.move(tank, section(flow, FACE), r -> true, 100, tx);
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
        try (Transaction root = Transaction.openRoot()) {
            try (Transaction nested = Transaction.open(root)) {
                ResourceHandlerUtil.move(tank, section(flow, FACE), r -> true, 100, nested);
                // nested aborted: what ResourceHandlerUtil.move itself does when the source under-delivers
            }
            Assertions.assertEquals(0, pipeTotal(flow), "the nested abort must already have restored the pipe");
            moved = ResourceHandlerUtil.move(tank, section(flow, FACE), r -> true, 100, root);
            root.commit();
        }
        Assertions.assertTrue(moved > 0, "precondition: the committed move must transfer something");
        Assertions.assertEquals(1000 - moved, tank.getAmountMb(0));
        Assertions.assertEquals(moved, pipeTotal(flow), "a committed move lands exactly what left the tank");
        Assertions.assertEquals(1000, tank.getAmountMb(0) + pipeTotal(flow), "fluid is conserved");
    }
    //?} else {
    /*private static IFluidHandler section(PipeFlowFluids flow, Direction face) {
        return flow.getCapability(CapUtil.CAP_FLUIDS, face);
    }

    private static long pipeTotal(PipeFlowFluids flow) {
        long total = section(flow, null).getFluidInTank(0).getAmount();
        for (Direction face : Direction.values()) {
            total += section(flow, face).getFluidInTank(0).getAmount();
        }
        return total;
    }

    @Test
    public void simulatedFillLeavesNoTrace() {
        PipeFlowFluids flow = newConnectedFluidPipe();
        int simulated = section(flow, FACE).fill(new FluidStack(Fluids.WATER, 100), IFluidHandler.FluidAction.SIMULATE);
        Assertions.assertTrue(simulated > 0, "precondition: the dry run must report an accepted amount");
        Assertions.assertEquals(0, pipeTotal(flow), "a simulated fill must not add fluid");
        Assertions.assertNull(flow.getFluidStackForRender(), "a simulated fill must not set the pipe's fluid type");
    }

    @Test
    public void executedFillIsConserved() {
        PipeFlowFluids flow = newConnectedFluidPipe();
        BCFluidTank tank = waterTank(1000);
        // BCFluidTank.drain's third argument is `simulate`: offer 100 mB without taking it yet
        int filled = section(flow, FACE).fill(tank.drain(0, 100, true), IFluidHandler.FluidAction.EXECUTE);
        Assertions.assertTrue(filled > 0, "precondition: the fill must transfer something");
        tank.drain(0, filled, false); // simulate=false: take from the tank exactly what landed in the pipe
        Assertions.assertEquals(1000, tank.getAmountMb(0) + pipeTotal(flow), "fluid is conserved");
    }*/
    //?}
}
