/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.gen;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import buildcraft.api.enums.EnumSpring;
import buildcraft.core.BCCoreBlocks;

/**
 * Custom Feature that generates water spring blocks by replacing bedrock.
 * Replaces the 1.12 event-based SpringPopulate with the modern data-driven worldgen system.
 *
 * Placement logic:
 * - Scans Y 0–4 for bedrock at the placed position
 * - Replaces one bedrock with a water spring block
 * - Fills blocks upward with water until air is reached
 */
public class SpringFeature extends Feature<NoneFeatureConfiguration> {

    public SpringFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();

        if (!EnumSpring.WATER.canGen) {
            return false;
        }

        int posX = origin.getX();
        int posZ = origin.getZ();

        // Scan Y 0–4 for bedrock to replace
        for (int y = 0; y < 5; y++) {
            BlockPos pos = new BlockPos(posX, y, posZ);
            BlockState existing = level.getBlockState(pos);

            if (existing.getBlock() != Blocks.BEDROCK) {
                continue;
            }

            // Handle flat bedrock: place at y or y-1
            int placeY = y > 0 ? y : y - 1;
            if (placeY < level.getMinY()) {
                continue;
            }

            BlockPos springPos = new BlockPos(posX, placeY, posZ);

            // Place the water spring block
            BlockState springState = BCCoreBlocks.SPRING_WATER.get().defaultBlockState();
            level.setBlock(springPos, springState, 3);

            // Fill upward with water until we hit air
            for (int j = placeY + 1; j < level.getMaxY(); j++) {
                BlockPos waterPos = new BlockPos(posX, j, posZ);
                if (level.isEmptyBlock(waterPos)) {
                    break;
                }
                level.setBlock(waterPos, Blocks.WATER.defaultBlockState(), 3);
            }

            return true;
        }

        return false;
    }
}
