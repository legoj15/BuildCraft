/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.crops;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import buildcraft.VanillaSetupBaseTester;

/** {@link CropHandlerPlantable#isMature} — what the Harvester robot is willing to cut down. Pins the
 *  7.1.x rule set:
 *
 *  <ul>
 *  <li>a {@code CropBlock} at max age, nether wart at age 3, and the always-ripe blocks (tall grass,
 *      melon, pumpkin, mushrooms, double plants);</li>
 *  <li>a STACKING plant — cactus, sugar cane — is mature once it stands on its own kind, which is how
 *      7.1.x's final {@code IPlantable} rule read;</li>
 *  <li>and nothing else. In particular a lone flower is NOT mature: 7.1.x had no flower rule, so a poppy
 *      on grass failed the stacked-{@code IPlantable} test and the Harvester left flower gardens alone.</li>
 *  </ul> */
public class CropHandlerPlantableTest extends VanillaSetupBaseTester {

    private static final BlockPos POS = new BlockPos(0, 64, 0);

    private static boolean mature(BlockState here, BlockState below) {
        return CropHandlerPlantable.INSTANCE.isMature(new StackedBlockGetter(here, below), here, POS);
    }

    @Test
    public void aCactusStandingOnCactusIsMature() {
        Assertions.assertTrue(
                mature(Blocks.CACTUS.defaultBlockState(), Blocks.CACTUS.defaultBlockState()),
                "a cactus segment on another cactus is the harvestable part of the stack");
    }

    @Test
    public void aCactusStandingOnSandIsNotMature() {
        Assertions.assertFalse(
                mature(Blocks.CACTUS.defaultBlockState(), Blocks.SAND.defaultBlockState()),
                "the base segment of a cactus stays — cutting it would uproot the plant");
    }

    @Test
    public void stackedSugarCaneIsMature() {
        Assertions.assertTrue(
                mature(Blocks.SUGAR_CANE.defaultBlockState(), Blocks.SUGAR_CANE.defaultBlockState()),
                "sugar cane standing on sugar cane is harvestable");
        Assertions.assertFalse(
                mature(Blocks.SUGAR_CANE.defaultBlockState(), Blocks.GRASS_BLOCK.defaultBlockState()),
                "the base cane stays");
    }

    @Test
    public void aLoneFlowerIsNotMature() {
        Assertions.assertFalse(
                mature(Blocks.POPPY.defaultBlockState(), Blocks.GRASS_BLOCK.defaultBlockState()),
                "7.1.x had no flower rule — a poppy on grass is not a crop, and the Harvester must not "
                        + "strip flower gardens");
    }

    @Test
    public void fullyGrownWheatIsMature() {
        Assertions.assertTrue(
                mature(Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7),
                        Blocks.FARMLAND.defaultBlockState()),
                "age-7 wheat is ripe");
        Assertions.assertFalse(
                mature(Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 3),
                        Blocks.FARMLAND.defaultBlockState()),
                "age-3 wheat is not");
    }

    @Test
    public void theAlwaysRipeBlocksStayMature() {
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        Assertions.assertTrue(mature(Blocks.PUMPKIN.defaultBlockState(), dirt), "pumpkin");
        Assertions.assertTrue(mature(Blocks.MELON.defaultBlockState(), dirt), "melon");
        Assertions.assertTrue(mature(Blocks.RED_MUSHROOM.defaultBlockState(), dirt), "mushroom");
        Assertions.assertFalse(mature(Blocks.STONE.defaultBlockState(), dirt), "stone is not a crop");
    }

    /** A two-cell {@link BlockGetter}: the crop and whatever stands directly under it, everything else air.
     *
     *  <p>The {@code LevelHeightAccessor} getter name cliffed at 1.21.10 ({@code getMinBuildHeight} →
     *  {@code getMinY}), so BOTH are declared without {@code @Override}: on each node the abstract one is
     *  implemented and the other is just an unused extra method. That keeps this shared test free of
     *  Stonecutter directives. */
    private static final class StackedBlockGetter implements BlockGetter {
        private final BlockState here;
        private final BlockState below;

        StackedBlockGetter(BlockState here, BlockState below) {
            this.here = here;
            this.below = below;
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            if (POS.equals(pos)) {
                return here;
            }
            if (POS.below().equals(pos)) {
                return below;
            }
            return Blocks.AIR.defaultBlockState();
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        public int getHeight() {
            return 384;
        }

        public int getMinBuildHeight() {
            return -64;
        }

        public int getMinY() {
            return -64;
        }
    }
}
