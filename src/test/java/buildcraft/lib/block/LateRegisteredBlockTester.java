/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.FenceGateBlock;

import buildcraft.api.blocks.CustomRotationHelper;
import buildcraft.api.core.BuildCraftAPI;
import buildcraft.lib.test.TestOnlyBlocks;

/**
 * BuildCraft's common setup scans the block registry twice: for the soft blocks the robots path through
 * ({@code BCCore.preInit}) and for the vanilla classes the wrench rotates ({@code VanillaRotationHandlers.fmlInit}).
 * A block registered by a mod that initialises after BuildCraft's own initializer must be found by both, however late
 * that mod registers it: NeoForge runs setup after every register event, and Fabric hands the stragglers over through
 * {@code LifecycleHooks.onLateBlock}. The game-test mod registers its test blocks late on Fabric (it depends on
 * BuildCraft), which is what makes this a test of that route there.
 */
public class LateRegisteredBlockTester {

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) throw new IllegalStateException(msg);
    }

    public static void testSetupScansFindBlocksRegisteredByLaterMods(GameTestHelper helper) {
        assertTrue(BuildCraftAPI.softBlocks.contains(TestOnlyBlocks.scannedPlant()),
                "a plant block registered after BuildCraft's init must be a soft block");

        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, TestOnlyBlocks.scannedFenceGate().defaultBlockState()
                .setValue(FenceGateBlock.FACING, Direction.NORTH));
        InteractionResult result = CustomRotationHelper.INSTANCE.attemptRotateBlock(
                helper.getLevel(), helper.absolutePos(pos), helper.getBlockState(pos), Direction.UP);
        assertTrue(result == InteractionResult.SUCCESS,
                "a fence gate registered after BuildCraft's init must rotate with the wrench, got " + result);
        Direction facing = helper.getBlockState(pos).getValue(FenceGateBlock.FACING);
        assertTrue(facing == Direction.EAST, "the gate should turn NORTH to EAST, got " + facing);
        helper.succeed();
    }
}
