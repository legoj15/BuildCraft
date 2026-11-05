/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric;

import net.minecraft.gametest.framework.GameTestHelper;

//? if fabric {
/*import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.material.Fluids;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.base.SingleFluidStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.base.CombinedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleVariantStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;

import buildcraft.api.fluids.BCFluidStack;
import buildcraft.platform.transfer.BCFluidStorage;
import buildcraft.platform.transfer.BCItemStorage;
import buildcraft.platform.transfer.BCTransaction;
import buildcraft.platform.transfer.TransferAccess;
*///?}

/**
 * The adapters over Fabric's own storage types ({@link FabricFluidStorages}, {@link FabricItemStorages}), run against
 * storages built from Fabric API's own classes and the vanilla blocks that expose one. A NeoForge node has no such
 * adapter, so the body is empty there.
 *
 * <p>A storage that is not slotted (a {@code CombinedStorage}, a composter's top) is read as one virtual tank or slot,
 * so a level gate must get the contents, the capacity and the amounts of ONE view; and a foreign storage may move a
 * count of droplets that is no whole number of millibuckets (a water cauldron level is 27000), which BuildCraft must
 * neither mint nor destroy.
 */
public final class FabricAdapterTester {
    private FabricAdapterTester() {}

    public static void foreignStorageViews(GameTestHelper helper) {
        //? if fabric {
        /*plainFluidViewMeasuresTheTankItShows(helper);
        plainItemViewMeasuresTheSlotItShows(helper);
        compostersTopHasNoSlotToMeasure(helper);
        equipmentStorageIsOnePerEntity(helper);
        foreignFractionsAreNeitherMintedNorDestroyed(helper);
        *///?}
        helper.succeed();
    }

    //? if fabric {
    /*private static SingleFluidStorage tank(long capacityMb, long waterMb) {
        SingleFluidStorage tank =
            SingleFluidStorage.withFixedCapacity(capacityMb * FabricFluids.DROPLETS_PER_MB, () -> {});
        if (waterMb > 0) {
            try (Transaction tx = Transaction.openOuter()) {
                tank.insert(FluidVariant.of(Fluids.WATER), waterMb * FabricFluids.DROPLETS_PER_MB, tx);
                tx.commit();
            }
        }
        return tank;
    }

    // A CombinedStorage is no SlottedStorage: the plain case, which the adapter reduces to one virtual tank.
    private static BCFluidStorage plain(SingleFluidStorage... tanks) {
        return FabricFluidStorages.fromFabric(new CombinedStorage<FluidVariant, SingleFluidStorage>(List.of(tanks)));
    }

    private static SingleVariantStorage<ItemVariant> slot(long capacity, int cobblestone) {
        SingleVariantStorage<ItemVariant> slot = new SingleVariantStorage<>() {
            @Override
            protected ItemVariant getBlankVariant() {
                return ItemVariant.blank();
            }

            @Override
            protected long getCapacity(ItemVariant variant) {
                return capacity;
            }
        };
        if (cobblestone > 0) {
            try (Transaction tx = Transaction.openOuter()) {
                slot.insert(ItemVariant.of(Items.COBBLESTONE), cobblestone, tx);
                tx.commit();
            }
        }
        return slot;
    }

    private static BCItemStorage plainItems(SingleVariantStorage<ItemVariant> first, SingleVariantStorage<ItemVariant> second) {
        return FabricItemStorages.fromFabric(
            new CombinedStorage<ItemVariant, SingleVariantStorage<ItemVariant>>(List.of(first, second)));
    }

    // The stack and the capacity a level gate divides come from the same view, whatever order the views are in.
    private static void plainFluidViewMeasuresTheTankItShows(GameTestHelper helper) {
        BCFluidStorage emptyFirst = plain(tank(1000, 0), tank(4000, 2000));
        BCFluidStack shown = emptyFirst.getStack(0);
        helper.assertTrue(shown.getAmount() == 2000, "the view shows the tank that holds water (" + shown.getAmount() + ")");
        helper.assertTrue(emptyFirst.getCapacity(0, shown) == 4000,
            "its capacity is that tank's, not the empty one in front of it (" + emptyFirst.getCapacity(0, shown) + ")");

        BCFluidStorage bigEmptyFirst = plain(tank(4000, 0), tank(1000, 1000));
        shown = bigEmptyFirst.getStack(0);
        helper.assertTrue(bigEmptyFirst.getCapacity(0, shown) == 1000,
            "a full 1000 mB tank behind an empty 4000 mB one is still 1000 mB (" + bigEmptyFirst.getCapacity(0, shown) + ")");

        BCFluidStorage allEmpty = plain(tank(1000, 0), tank(4000, 0));
        helper.assertTrue(allEmpty.getStack(0).isEmpty(), "all empty shows nothing");
        helper.assertTrue(allEmpty.getCapacity(0, BCFluidStack.EMPTY) == 1000,
            "all empty measures the first tank (" + allEmpty.getCapacity(0, BCFluidStack.EMPTY) + ")");
    }

    // The slot's own capacity, never an invented 64.
    private static void plainItemViewMeasuresTheSlotItShows(GameTestHelper helper) {
        BCItemStorage emptyFirst = plainItems(slot(16, 0), slot(32, 8));
        ItemStack shown = emptyFirst.getStack(0);
        helper.assertTrue(shown.is(Items.COBBLESTONE) && shown.getCount() == 8,
            "the view shows the slot that holds cobblestone (" + shown + ")");
        helper.assertTrue(emptyFirst.getCapacity(0, shown) == 32,
            "its capacity is that slot's (" + emptyFirst.getCapacity(0, shown) + ")");

        BCItemStorage allEmpty = plainItems(slot(16, 0), slot(32, 0));
        helper.assertTrue(allEmpty.getCapacity(0, ItemStack.EMPTY) == 16,
            "all empty measures the first slot (" + allEmpty.getCapacity(0, ItemStack.EMPTY) + ")");
    }

    // The top of a composter takes items in and has no views at all: nothing to measure, so a level gate sees no space.
    private static void compostersTopHasNoSlotToMeasure(GameTestHelper helper) {
        BlockPos rel = new BlockPos(3, 2, 3);
        helper.setBlock(rel, Blocks.COMPOSTER);
        try {
            BCItemStorage top = TransferAccess.findItem(helper.getLevel(), helper.absolutePos(rel), Direction.UP);
            helper.assertTrue(top != null, "a composter's top has an item storage");
            helper.assertTrue(top.getStack(0).isEmpty(), "and it shows nothing");
            helper.assertTrue(top.getCapacity(0, ItemStack.EMPTY) == 0,
                "with no view it has no capacity (" + top.getCapacity(0, ItemStack.EMPTY) + ")");
        } finally {
            helper.setBlock(rel, Blocks.AIR);
        }
    }

    // Fabric's entity lookup caches nothing, so each lookup used to build its own storage over the same slots: two of
    // them in one outer transaction each snapshotted a slot and each reported the equip change (NeoForge keeps one
    // wrapper per entity).
    private static void equipmentStorageIsOnePerEntity(GameTestHelper helper) {
        net.minecraft.world.entity.Mob zombie =
            helper.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, new BlockPos(4, 2, 2));
        try {
            // Inert: nothing here may tick, and a wandering mob could leave its own arena cell.
            zombie.setNoAi(true);
            zombie.setNoGravity(true);
            var first = FabricEntityItems.LOOKUP.find(zombie, null);
            var second = FabricEntityItems.LOOKUP.find(zombie, null);
            helper.assertTrue(first != null && first == second, "two lookups of one entity answer one storage");
        } finally {
            buildcraft.lib.test.EntityArenaUtil.discard(zombie);
        }
    }

    private static int moveWater(GameTestHelper helper, BlockPos pos, int millibuckets, boolean insert) {
        BCFluidStorage cauldron = TransferAccess.findFluid(helper.getLevel(), pos, null);
        helper.assertTrue(cauldron != null, "a cauldron has a fluid storage");
        BCFluidStack water = new BCFluidStack(Fluids.WATER, 1);
        try (BCTransaction tx = BCTransaction.openRoot()) {
            int moved = insert ? cauldron.insert(0, water, millibuckets, tx) : cauldron.extract(0, water, millibuckets, tx);
            tx.commit();
            return moved;
        }
    }

    // A cauldron level is 27000 droplets, 333.33 mB: BuildCraft books whole millibuckets, so a lone level moves
    // nothing (floored it would mint 27 droplets on an insert and destroy them on an extract), while three levels are
    // exactly a bucket.
    private static void foreignFractionsAreNeitherMintedNorDestroyed(GameTestHelper helper) {
        BlockPos rel = new BlockPos(3, 2, 3);
        helper.setBlock(rel, Blocks.CAULDRON);
        BlockPos pos = helper.absolutePos(rel);
        try {
            int moved = moveWater(helper, pos, 400, true);
            helper.assertTrue(moved == 0, "400 mB is one level and a fraction: nothing moves (" + moved + ")");
            helper.assertTrue(helper.getLevel().getBlockState(pos).is(Blocks.CAULDRON), "the cauldron is still empty");

            moved = moveWater(helper, pos, 1000, true);
            helper.assertTrue(moved == 1000, "a bucket is three whole levels (" + moved + ")");
            var filled = helper.getLevel().getBlockState(pos);
            helper.assertTrue(filled.is(Blocks.WATER_CAULDRON) && filled.getValue(LayeredCauldronBlock.LEVEL) == 3,
                "the cauldron is full: " + filled);

            moved = moveWater(helper, pos, 400, false);
            helper.assertTrue(moved == 0, "taking 400 mB out takes one level and a fraction: nothing moves (" + moved + ")");
            filled = helper.getLevel().getBlockState(pos);
            helper.assertTrue(filled.is(Blocks.WATER_CAULDRON) && filled.getValue(LayeredCauldronBlock.LEVEL) == 3,
                "the cauldron is still full: " + filled);

            moved = moveWater(helper, pos, 1000, false);
            helper.assertTrue(moved == 1000, "a bucket comes out as three levels (" + moved + ")");
            helper.assertTrue(helper.getLevel().getBlockState(pos).is(Blocks.CAULDRON), "the cauldron is empty again");
        } finally {
            helper.setBlock(rel, Blocks.AIR);
        }
    }*/
    //?}
}
