/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.neoforge;

//? if >=1.21.10 {
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import buildcraft.factory.BCFactoryBlocks;
import buildcraft.factory.tile.TankColumnResourceHandler;
import buildcraft.factory.tile.TileTank;
import buildcraft.lib.fluid.BCFluidTank;
import buildcraft.lib.transfer.FluidStorageUtil;
//?}

import buildcraft.VanillaSetupBaseTester;

/**
 * Before the platform layer a BuildCraft tank on NeoForge 1.21.10+ was a {@code FluidStacksResourceHandler} and the
 * Tank's column handler checked its offer itself, so an empty resource or a negative amount at an index threw
 * {@code IllegalArgumentException}, as NeoForge's {@code ResourceHandler} contract says. A face that refuses a
 * direction outright (the Pump's, the Distiller's) answered 0 without looking at the offer, and still does.
 */
public class NeoForgeFluidOfferPreconditionTest extends VanillaSetupBaseTester {
    //? if >=1.21.10 {
    private static final FluidResource WATER = FluidResource.of(new FluidStack(Fluids.WATER, 1));

    @Test
    public void aTankRefusesAnEmptyOrNegativeOfferAtAnIndex() {
        ResourceHandler<FluidResource> handler = NeoForgeFluidStorages.toNeo(new BCFluidTank(1, 1000));
        try (Transaction tx = Transaction.openRoot()) {
            assertThrows(IllegalArgumentException.class, () -> handler.insert(0, FluidResource.EMPTY, 5, tx),
                "an empty insert throws");
            assertThrows(IllegalArgumentException.class, () -> handler.extract(0, FluidResource.EMPTY, 5, tx),
                "an empty extract throws");
            assertThrows(IllegalArgumentException.class, () -> handler.insert(0, WATER, -1, tx),
                "a negative insert throws");
            assertThrows(IllegalArgumentException.class, () -> handler.extract(0, WATER, -1, tx),
                "a negative extract throws");
            assertEquals(0, handler.insert(0, WATER, 0, tx), "a zero offer moves nothing");
        }
    }

    @Test
    public void theTankColumnRefusesAnEmptyOfferAtAnIndex() {
        TileTank tile = new TileTank(BlockPos.ZERO, BCFactoryBlocks.TANK.get().defaultBlockState());
        ResourceHandler<FluidResource> handler = NeoForgeFluidStorages.toNeo(new TankColumnResourceHandler(tile));
        try (Transaction tx = Transaction.openRoot()) {
            assertThrows(IllegalArgumentException.class, () -> handler.insert(0, FluidResource.EMPTY, 5, tx),
                "an empty insert throws");
            assertThrows(IllegalArgumentException.class, () -> handler.extract(0, WATER, -1, tx),
                "a negative extract throws");
        }
    }

    @Test
    public void aRefusingFaceStillAnswersZero() {
        ResourceHandler<FluidResource> pumpFace =
            NeoForgeFluidStorages.toNeo(FluidStorageUtil.extractOnly(new BCFluidTank(1, 1000)));
        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(0, pumpFace.insert(0, FluidResource.EMPTY, 5, tx), "a drain-only face refuses any insert");
        }
    }
    //?}
}
