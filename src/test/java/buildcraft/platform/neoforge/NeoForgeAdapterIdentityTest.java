/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.neoforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

//? if >=1.21.10 {
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;

import buildcraft.api.fluids.BCFluidStack;
import buildcraft.core.statements.TriggerFluidContainerLevel;
//?} else {
/*import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import buildcraft.platform.transfer.BCEnergyStorage;
import buildcraft.platform.transfer.BCTransaction;
*///?}

import buildcraft.VanillaSetupBaseTester;
import buildcraft.lib.fluid.BCFluidTank;
import buildcraft.platform.transfer.BCFluidStorage;

/**
 * The adapters every BuildCraft fluid or energy capability, and every lookup of another mod's handler, goes through
 * hand out one object per wrapped storage or handler while that object is held, as the storage itself was before the
 * platform layer: two capability queries of a BuildCraft tank answer the same handler, and a second lookup of another
 * mod's handler, made while the first view is still held, gives that same view.
 */
public class NeoForgeAdapterIdentityTest extends VanillaSetupBaseTester {

    @Test
    public void aBuildCraftTankIsOneHandlerOnEveryQuery() {
        BCFluidTank tank = new BCFluidTank(1, 1000);
        var first = NeoForgeFluidStorages.toNeo(tank);
        assertSame(first, NeoForgeFluidStorages.toNeo(tank), "two capability queries answer the same handler");
        assertSame(tank, NeoForgeFluidStorages.fromNeo(first), "and it unwraps to the tank itself");
    }

    @Test
    public void anotherModsHandlerIsOneViewOnEveryLookup() {
        //? if >=1.21.10 {
        ResourceHandler<FluidResource> fluids = new FluidStacksResourceHandler(1, 1000);
        EnergyHandler energy = new SimpleEnergyHandler(1000);
        //?} else {
        /*IFluidHandler fluids = new FluidTank(1000);
        IEnergyStorage energy = new EnergyStorage(1000);
        *///?}
        BCFluidStorage view = NeoForgeFluidStorages.fromNeo(fluids);
        assertSame(view, NeoForgeFluidStorages.fromNeo(fluids), "two fluid lookups give the same view");
        assertSame(fluids, NeoForgeFluidStorages.toNeo(view), "and it unwraps to the handler itself");

        var energyView = NeoForgeEnergyStorages.fromNeo(energy);
        assertSame(energyView, NeoForgeEnergyStorages.fromNeo(energy), "two energy lookups give the same view");
    }

    //? if >=1.21.10 {
    /**
     * Before the platform layer the Fluid Level trigger read a tank's capacity as {@code (int) getCapacityAsLong}, so
     * a tank of {@code Long.MAX_VALUE} mB came out as -1 and was passed over; a saturated read made it a nearly empty
     * tank of {@code Integer.MAX_VALUE} that fired every "below" trigger.
     */
    @Test
    public void theLevelTriggerPassesOverATankTooLargeForAnInt() {
        FluidStacksResourceHandler infinite = new FluidStacksResourceHandler(1, 1000) {
            @Override
            public long getCapacityAsLong(int index, FluidResource resource) {
                return Long.MAX_VALUE;
            }
        };
        infinite.set(0, FluidResource.of(new FluidStack(Fluids.WATER, 1)), 1000);
        assertFalse(TriggerFluidContainerLevel.isBelow(NeoForgeFluidStorages.fromNeo(infinite), BCFluidStack.EMPTY,
            0.5f), "a tank whose capacity narrows to -1 is skipped, as before the platform layer");
    }
    //?} else {
    /*/^* A classic FE buffer can only simulate, and each simulation sees the buffer untouched: without the owed
     *  headroom two receives in one transaction were both promised the same free space and the replay lost one. The
     *  buffer moves at most 30 FE per call, so a second call is owed its own 30, not what one call of the total
     *  would move. ^/
    @Test
    public void aClassicEnergyBufferIsNeverPromisedTwiceInOneTransaction() {
        EnergyStorage classic = new EnergyStorage(100, 30, 30, 60);
        BCEnergyStorage first = NeoForgeEnergyStorages.fromNeo(classic);
        BCEnergyStorage second = NeoForgeEnergyStorages.fromNeo(classic);
        try (BCTransaction tx = BCTransaction.openRoot()) {
            assertEquals(30, first.insert(30, tx));
            assertEquals(10, second.insert(30, tx), "40 FE of room, 30 already promised");
            tx.commit();
        }
        assertEquals(100, classic.getEnergyStored());
        try (BCTransaction tx = BCTransaction.openRoot()) {
            assertEquals(30, first.extract(70, tx));
            assertEquals(30, second.extract(70, tx), "each call moves at most 30, and 70 is still unpromised");
            tx.commit();
        }
        assertEquals(40, classic.getEnergyStored(), "every promise replayed in full");
    }
    *///?}
}
