/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.properties;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;

import buildcraft.api.core.BuildCraftAPI;
import buildcraft.api.core.IWorldProperty;

/** Ph5 game test for the tag-backed world properties, run against a LIVE server because the FML-JUnit
 *  unit-test JVM performs no resource load and therefore can never resolve tag membership (see
 *  {@link WorldPropertySweepTest}). Every pin here goes through the registry —
 *  {@link BuildCraftAPI#getWorldProperty} — so a missing registration fails on the lookup, not on a
 *  silent null. Synchronous: the blocks are placed and the properties read in the same tick, so the water
 *  source has not had a chance to flow. */
public class WorldPropertyTester {

    public static void matchTheArenaBlocks(GameTestHelper helper) {
        Level level = helper.getLevel();

        // One layer, x 0..5 / z 0..2 — inside the arena cell on every node.
        helper.setBlock(rel(0, 0), Blocks.OAK_LOG);
        helper.setBlock(rel(1, 0), Blocks.CRIMSON_STEM);
        helper.setBlock(rel(2, 0), Blocks.STONE);
        helper.setBlock(rel(3, 0), Blocks.DIRT);
        helper.setBlock(rel(4, 0), Blocks.GRASS_BLOCK);
        helper.setBlock(rel(5, 0), Blocks.WATER);
        helper.setBlock(rel(0, 1), Blocks.COAL_ORE);
        helper.setBlock(rel(1, 1), Blocks.IRON_ORE);
        helper.setBlock(rel(2, 1), Blocks.DIAMOND_ORE);
        helper.setBlock(rel(3, 1), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
        helper.setBlock(rel(4, 1), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 3));
        helper.setBlock(rel(5, 1), Blocks.NETHER_WART.defaultBlockState().setValue(NetherWartBlock.AGE, 3));
        helper.setBlock(rel(0, 2), Blocks.SHORT_GRASS);
        // rel(1, 2) is deliberately left air.

        IWorldProperty wood = property(helper, "wood");
        helper.assertTrue(wood.get(level, helper.absolutePos(rel(0, 0))), "oak log is wood");
        helper.assertTrue(wood.get(level, helper.absolutePos(rel(1, 0))), "crimson stem is wood");
        helper.assertFalse(wood.get(level, helper.absolutePos(rel(2, 0))), "stone is not wood");

        IWorldProperty harvestable = property(helper, "harvestable");
        helper.assertTrue(harvestable.get(level, helper.absolutePos(rel(3, 1))), "mature wheat is harvestable");
        helper.assertFalse(harvestable.get(level, helper.absolutePos(rel(4, 1))), "young wheat is not harvestable");
        helper.assertTrue(harvestable.get(level, helper.absolutePos(rel(5, 1))), "mature nether wart is harvestable");
        helper.assertFalse(harvestable.get(level, helper.absolutePos(rel(2, 0))), "stone is not harvestable");

        IWorldProperty ore0 = property(helper, "ore@hardness=0");
        IWorldProperty ore1 = property(helper, "ore@hardness=1");
        IWorldProperty ore3 = property(helper, "ore@hardness=3");
        helper.assertTrue(ore0.get(level, helper.absolutePos(rel(0, 1))), "a tier-0 robot mines coal ore");
        helper.assertFalse(ore0.get(level, helper.absolutePos(rel(1, 1))), "a tier-0 robot cannot mine iron ore");
        helper.assertTrue(ore1.get(level, helper.absolutePos(rel(1, 1))), "a tier-1 robot mines iron ore");
        helper.assertFalse(ore1.get(level, helper.absolutePos(rel(2, 1))), "a tier-1 robot cannot mine diamond ore");
        helper.assertTrue(ore3.get(level, helper.absolutePos(rel(2, 1))), "a tier-3 robot mines diamond ore");
        helper.assertTrue(ore3.get(level, helper.absolutePos(rel(0, 1))), "a tier-3 robot still mines coal ore");
        helper.assertFalse(ore3.get(level, helper.absolutePos(rel(2, 0))), "stone is not an ore at any tier");

        IWorldProperty dirt = property(helper, "dirt");
        helper.assertTrue(dirt.get(level, helper.absolutePos(rel(3, 0))), "dirt is dirt");
        helper.assertTrue(dirt.get(level, helper.absolutePos(rel(4, 0))), "grass block is tillable ground");
        helper.assertFalse(dirt.get(level, helper.absolutePos(rel(2, 0))), "stone is not dirt");

        IWorldProperty replaceable = property(helper, "replaceable");
        helper.assertTrue(replaceable.get(level, helper.absolutePos(rel(0, 2))), "short grass is replaceable");
        helper.assertTrue(replaceable.get(level, helper.absolutePos(rel(5, 0))), "water is replaceable");
        helper.assertTrue(replaceable.get(level, helper.absolutePos(rel(1, 2))), "air is replaceable");
        helper.assertFalse(replaceable.get(level, helper.absolutePos(rel(2, 0))), "stone is not replaceable");
        helper.assertFalse(replaceable.get(level, helper.absolutePos(rel(4, 0))), "grass block is not replaceable");

        IWorldProperty fluidSource = property(helper, "fluidSource");
        helper.assertTrue(fluidSource.get(level, helper.absolutePos(rel(5, 0))), "standing water is a source");
        helper.assertFalse(fluidSource.get(level, helper.absolutePos(rel(1, 2))), "air is not a fluid source");

        helper.succeed();
    }

    /** The registry is the seam the boards use — a missing key must fail the test, not the robot. */
    private static IWorldProperty property(GameTestHelper helper, String name) {
        IWorldProperty property = BuildCraftAPI.getWorldProperty(name);
        helper.assertTrue(property != null, "the world property '" + name + "' must be registered");
        return property;
    }

    private static BlockPos rel(int x, int z) {
        return new BlockPos(x, 0, z);
    }
}
