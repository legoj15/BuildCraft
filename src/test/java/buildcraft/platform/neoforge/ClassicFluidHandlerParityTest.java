/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.neoforge;

//? if <1.21.10 {
/*import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import buildcraft.api.fluids.BCFluidStack;
import buildcraft.builders.BCBuildersBlocks;
import buildcraft.builders.tile.TileBuilder;
import buildcraft.core.statements.TriggerFluidContainerLevel;
import buildcraft.energy.BCEnergyBlocks;
import buildcraft.energy.tile.TileEngineIron_BC8;
import buildcraft.lib.fluid.BCFluidTank;
import buildcraft.lib.transfer.DeferredFluidCalls;
import buildcraft.lib.transfer.FluidHandlerAdvs;
import buildcraft.lib.transfer.FluidStorageUtil;
import buildcraft.platform.transfer.BCFluidStorage;
import buildcraft.platform.transfer.BCTransaction;
*///?}

/**
 * NeoForge 1.21.1 fluid handling through the platform layer, against what it was before the layer: BuildCraft's own
 * tanks seen as classic {@code IFluidHandler}s answer as their classic handlers did, and another mod's classic
 * handler seen as a {@link buildcraft.platform.transfer.BCFluidStorage} moves exactly what one classic call would
 * have. The other nodes have no classic handlers, so the bodies are 1.21.1-only.
 */
public class ClassicFluidHandlerParityTest {
    //? if <1.21.10 {
    /*private static final BCFluidStack WATER = new BCFluidStack(Fluids.WATER, 1);
    private static final BCFluidStack LAVA = new BCFluidStack(Fluids.LAVA, 1);

    /^* Another mod's two-tank classic handler: fill and drain take the first tank that moves anything. ^/
    private static final class TwoTanks implements IFluidHandler {
        final FluidTank[] tanks = { new FluidTank(1000), new FluidTank(1000) };

        TwoTanks(FluidStack first, FluidStack second) {
            tanks[0].setFluid(first);
            tanks[1].setFluid(second);
        }

        int total() {
            return tanks[0].getFluidAmount() + tanks[1].getFluidAmount();
        }

        @Override
        public int getTanks() {
            return tanks.length;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return tanks[tank].getFluid();
        }

        @Override
        public int getTankCapacity(int tank) {
            return tanks[tank].getCapacity();
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return tanks[tank].isFluidValid(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            for (FluidTank tank : tanks) {
                int filled = tank.fill(resource.copy(), action);
                if (filled > 0) {
                    return filled;
                }
            }
            return 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            for (FluidTank tank : tanks) {
                FluidStack drained = tank.drain(resource.copy(), action);
                if (!drained.isEmpty()) {
                    return drained;
                }
            }
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            for (FluidTank tank : tanks) {
                FluidStack drained = tank.drain(maxDrain, action);
                if (!drained.isEmpty()) {
                    return drained;
                }
            }
            return FluidStack.EMPTY;
        }
    }

    private static FluidStack water(int amount) {
        return new FluidStack(Fluids.WATER, amount);
    }

    /^* A wooden pipe pulling more than the first tank holds made one classic drain; the view must too, not drain the
     *  first tank once per index (it gave 50 mB and was credited 100). ^/
    @Test
    public void aClassicHandlerViewDrainsLikeOneClassicDrain() {
        TwoTanks handler = new TwoTanks(water(50), FluidStack.EMPTY);
        BCFluidStorage view = NeoForgeFluidStorages.fromNeo(handler);
        try (BCTransaction tx = BCTransaction.openRoot()) {
            assertEquals(50, view.extract(WATER, 1000, tx), "one drain of what the handler holds");
            assertEquals(0, view.extract(1, WATER, 1000, tx), "another index cannot promise the same 50 again");
            tx.commit();
        }
        assertEquals(0, handler.total(), "the commit drained the 50 for real");
    }

    /^* Moving out of a classic handler never makes fluid: with water in both tanks, the second index's simulation
     *  sees the first tank untouched, which the view must not promise twice. ^/
    @Test
    public void movingOutOfAClassicHandlerConservesFluid() {
        TwoTanks handler = new TwoTanks(water(1000), water(100));
        BCFluidStorage view = NeoForgeFluidStorages.fromNeo(handler);
        BCFluidTank target = new BCFluidTank(1, 5000);
        int moved = FluidStorageUtil.move(view, target, stack -> true, 5000, null);
        assertEquals(moved, target.getAmount(0), "the target holds what move reported");
        assertEquals(1100, target.getAmount(0) + handler.total(), "no fluid made or lost");
        assertEquals(1000, moved, "what one classic drain of the handler gives (its first tank)");
    }

    /^* A gate's empty-tank probe asked the whole classic handler, which cannot fill one tank on its own: an empty
     *  second tank that takes the searched fluid counts. ^/
    @Test
    public void theEmptyTankProbeAsksTheWholeClassicHandler() {
        TwoTanks handler = new TwoTanks(new FluidStack(Fluids.LAVA, 500), FluidStack.EMPTY);
        BCFluidStorage view = NeoForgeFluidStorages.fromNeo(handler);
        assertTrue(TriggerFluidContainerLevel.isBelow(view, WATER, 0.5f),
            "tank 1 is empty and takes water, so water is below 50%");
        assertEquals(0, handler.tanks[1].getFluidAmount(), "the probe never fills");
    }

    /^* A docked robot loading from another mod's classic handler drained the station for real first and filled the
     *  robot with what came out, so a handler whose real drain gives less than its simulation could not create
     *  fluid. The station's input, seen as the robot sees it, is the handler's deferred view, which moves that way. ^/
    @Test
    public void aRobotLoadFromAClassicStationConservesFluid() {
        FluidTank lying = new FluidTank(1000) {
            @Override
            public FluidStack drain(FluidStack resource, FluidAction action) {
                return super.drain(action.execute() ? resource.copyWithAmount(Math.min(resource.getAmount(), 400))
                    : resource, action);
            }
        };
        lying.setFluid(water(1000));
        BCFluidStorage input = FluidHandlerAdvs.storage(FluidHandlerAdvs.of(NeoForgeFluidStorages.fromNeo(lying)));
        assertTrue(input instanceof DeferredFluidCalls.Owner, "the robot sees a view that can only simulate");
        BCFluidTank robot = new BCFluidTank(1, 4000);
        int loaded = ((DeferredFluidCalls.Owner) input).deferredCalls().drainInto(robot, WATER, 1000, true);
        assertEquals(400, loaded, "the robot took what the station really gave");
        assertEquals(1000, robot.getAmount(0) + lying.getFluidAmount(), "no fluid made or lost");
    }

    private static TileBuilder newBuilder() {
        return new TileBuilder(BlockPos.ZERO, BCBuildersBlocks.BUILDER.get().defaultBlockState());
    }

    private static void put(BCFluidStorage storage, int index, BCFluidStack fluid, int amount) {
        try (BCTransaction tx = BCTransaction.openRoot()) {
            assertEquals(amount, storage.insert(index, fluid, amount, tx));
            tx.commit();
        }
    }

    /^* The Builder's classic fill stopped at the first tank that took anything. ^/
    @Test
    public void aBuilderFillsOneTankPerClassicFill() {
        BCFluidStorage tanks = newBuilder().getTankManager();
        int capacity = tanks.getCapacity(0, BCFluidStack.EMPTY);
        IFluidHandler classic = NeoForgeFluidStorages.toNeo(tanks);
        assertEquals(capacity, classic.fill(water(capacity + 500), IFluidHandler.FluidAction.EXECUTE));
        assertEquals(capacity, tanks.getAmount(0));
        assertEquals(0, tanks.getAmount(1), "the second tank is not filled by the same call");
    }

    /^* A blueprint needing 1000 mB of lava against two tanks of 600 was refused: the Builder's drain (its requirement
     *  check) took from the first tank holding the fluid only. ^/
    @Test
    public void aBuilderDrainsOneTankPerCall() {
        BCFluidStorage tanks = newBuilder().getTankManager();
        put(tanks, 0, LAVA, 600);
        put(tanks, 1, LAVA, 600);
        try (BCTransaction tx = BCTransaction.openRoot()) {
            assertEquals(600, tanks.extract(LAVA, 1000, tx), "the requirement check sees the first tank only");
        }
        IFluidHandler classic = NeoForgeFluidStorages.toNeo(tanks);
        assertEquals(600, classic.drain(new FluidStack(Fluids.LAVA, 1000), IFluidHandler.FluidAction.SIMULATE).getAmount());
        assertEquals(600, tanks.getAmount(0), "simulations change nothing");
    }

    /^* The iron engine's classic drain(int) always drained its residue tank, fuel and coolant held or not. ^/
    @Test
    public void aFuelledIronEngineGivesItsResidue() {
        TileEngineIron_BC8 engine = new TileEngineIron_BC8(BlockPos.ZERO, BCEnergyBlocks.ENGINE_IRON.get().defaultBlockState());
        engine.tankFuel.setFluidStack(0, LAVA.copyWithAmount(500));
        engine.tankCoolant.setFluidStack(0, WATER.copyWithAmount(200));
        engine.tankResidue.setFluidStack(0, WATER.copyWithAmount(300));
        IFluidHandler classic = NeoForgeFluidStorages.toNeo(engine.combinedFluidHandler);

        FluidStack simulated = classic.drain(1000, IFluidHandler.FluidAction.SIMULATE);
        assertEquals(300, simulated.getAmount(), "drain(int) reaches the residue past the fuel and coolant");
        assertTrue(classic.drain(new FluidStack(Fluids.LAVA, 100), IFluidHandler.FluidAction.SIMULATE).isEmpty(),
            "fuel is never drained");
        assertEquals(300, classic.drain(1000, IFluidHandler.FluidAction.EXECUTE).getAmount());
        assertEquals(0, engine.tankResidue.getAmountMb(0));
        assertEquals(500, engine.tankFuel.getAmountMb(0));
        assertEquals(200, engine.tankCoolant.getAmountMb(0));
    }
    *///?}
}
