/*
 * Copyright (c) 2026 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import buildcraft.core.BCCoreItems;
import buildcraft.factory.block.BlockDistiller;
import buildcraft.factory.tile.TileDistiller_BC8;

/**
 * Regression coverage for the distiller's wrench-rotation behaviour.
 * <p>
 * In 1.12.2 the distiller used {@code IBlockWithFacing} (which extended
 * {@code ICustomRotationHandler}), so a wrench right-click rotated the block
 * via {@code EnumFacing.rotateY()}. The 26.1.1 port dropped that interface,
 * so the wrench fell through to {@code useItemOn} which always opened the GUI.
 */
public class DistillerTester {

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) throw new IllegalStateException(msg);
    }

    /**
     * {@link BlockDistiller#attemptRotation} — the {@link buildcraft.api.blocks.ICustomRotationHandler}
     * entry point that {@code ItemWrench_Neptune.useOn} dispatches to — must rotate the
     * horizontal facing clockwise. Stepping through all four cardinal directions covers the
     * full cycle and the wrap-around back to the starting facing.
     */
    public static void testWrenchRotatesClockwise(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, BCFactoryBlocks.DISTILLER.get().defaultBlockState()
                .setValue(BlockDistiller.FACING, Direction.NORTH));

        BlockDistiller block = (BlockDistiller) BCFactoryBlocks.DISTILLER.get();
        Direction[] expected = { Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.NORTH };

        for (Direction next : expected) {
            BlockState before = helper.getBlockState(pos);
            InteractionResult result = block.attemptRotation(
                    helper.getLevel(), helper.absolutePos(pos), before, Direction.UP);
            assertTrue(result == InteractionResult.SUCCESS,
                    "attemptRotation should return SUCCESS, got " + result);
            Direction after = helper.getBlockState(pos).getValue(BlockDistiller.FACING);
            assertTrue(after == next,
                    "Distiller should rotate " + before.getValue(BlockDistiller.FACING)
                            + " -> " + next + ", got " + after);
        }

        helper.succeed();
    }

    /**
     * {@code BlockDistiller.useItemOn} must return {@link InteractionResult#PASS} for a
     * held wrench so the wrench's {@code useOn} path runs and dispatches to
     * {@link BlockDistiller#attemptRotation}. Returning SUCCESS here would consume the
     * action before the wrench fires, which is exactly the regression that re-routed
     * wrench clicks into the GUI.
     */
    public static void testWrenchPassesThroughUseItemOn(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, BCFactoryBlocks.DISTILLER.get().defaultBlockState()
                .setValue(BlockDistiller.FACING, Direction.NORTH));

        BlockDistiller block = (BlockDistiller) BCFactoryBlocks.DISTILLER.get();
        BlockState state = helper.getBlockState(pos);
        ItemStack wrench = new ItemStack(BCCoreItems.WRENCH.get());
        BlockPos absPos = helper.absolutePos(pos);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absPos), Direction.UP, absPos, false);

        InteractionResult result = invokeUseItemOn(block, wrench, state, helper, absPos,
                helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL), hit);

        assertTrue(result == InteractionResult.PASS,
                "useItemOn with a wrench must return PASS so the wrench's useOn handles rotation, got " + result);
        assertTrue(helper.getBlockState(pos).getValue(BlockDistiller.FACING) == Direction.NORTH,
                "useItemOn should not have rotated the block on its own");

        helper.succeed();
    }

    /**
     * {@code useItemOn} is {@code protected} on {@code BlockBehaviour}. Reflection here
     * is the simplest way to drive it from test code without standing up a real player
     * interaction sequence.
     */
    private static InteractionResult invokeUseItemOn(BlockDistiller block, ItemStack stack,
            BlockState state, GameTestHelper helper, BlockPos absPos,
            net.minecraft.world.entity.player.Player player, BlockHitResult hit) {
        try {
            java.lang.reflect.Method m = net.minecraft.world.level.block.state.BlockBehaviour.class
                    .getDeclaredMethod("useItemOn",
                            ItemStack.class, BlockState.class, net.minecraft.world.level.Level.class,
                            BlockPos.class, net.minecraft.world.entity.player.Player.class,
                            InteractionHand.class, BlockHitResult.class);
            m.setAccessible(true);
            return (InteractionResult) m.invoke(block, stack, state, helper.getLevel(), absPos, player,
                    InteractionHand.MAIN_HAND, hit);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to invoke useItemOn reflectively", e);
        }
    }

    // --- Tank gating regression tests ---
    //
    // 1.12.2 set:
    //   tankIn.setFilter(isDistillableFluid)  // only distillable fluids accept fills
    //   tankIn.setCanDrain(false)             // never drain to external (pipes / buckets)
    //   tankGasOut.setCanFill(false)          // never accept external fills
    //   tankLiquidOut.setCanFill(false)       // never accept external fills
    //
    // The 26.1.1 port lost all four restrictions, so right-clicking with any filled
    // bucket fed it into tankIn, an empty bucket drained tankIn (instead of an output),
    // and pipes could shove arbitrary fluids straight through the multi-block.

    /**
     * Lava is not a registered distillation input. The InputTank's {@code isValid}
     * filter must reject it on external insert so a player right-clicking the distiller
     * with a lava bucket can't bypass the recipe registry.
     */
    public static void testInputTankRejectsNonDistillableInsert(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, BCFactoryBlocks.DISTILLER.get());
        TileDistiller_BC8 distiller = helper.getBlockEntity(pos, TileDistiller_BC8.class);
        FluidResource lava = FluidResource.of(new FluidStack(Fluids.LAVA, 1000));
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = distiller.getTankIn().insert(0, lava, 1000, tx);
            assertTrue(inserted == 0,
                    "InputTank must reject a non-distillable insert via isValid, got " + inserted);
            tx.commit();
        }
        assertTrue(distiller.getTankIn().getAmountAsLong(0) == 0,
                "InputTank should still be empty after a rejected insert");
        helper.succeed();
    }

    /**
     * Even after the recipe loop has filled tankIn (via {@code set()} during NBT load
     * or {@code insertInternal} during craft), external {@code extract} must return 0 so
     * a right-click with an empty bucket doesn't drain the input tank — 1.12.2's
     * {@code tankIn.setCanDrain(false)} was the load-bearing piece that made empty-bucket
     * right-clicks fall through to the output tanks.
     */
    public static void testInputTankBlocksExternalExtract(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, BCFactoryBlocks.DISTILLER.get());
        TileDistiller_BC8 distiller = helper.getBlockEntity(pos, TileDistiller_BC8.class);

        // Bypass the isValid filter the same way NBT load does: write directly with set().
        FluidResource water = FluidResource.of(new FluidStack(Fluids.WATER, 1000));
        distiller.getTankIn().set(0, water, 1000);

        try (Transaction tx = Transaction.openRoot()) {
            int extracted = distiller.getTankIn().extract(0, water, 1000, tx);
            assertTrue(extracted == 0,
                    "InputTank must reject external extract, got " + extracted);
            tx.commit();
        }
        assertTrue(distiller.getTankIn().getAmountAsLong(0) == 1000,
                "InputTank should still be full after a rejected extract, got " + distiller.getTankIn().getAmountAsLong(0));

        // The recipe loop must still be able to drain it. Without this, distillation halts.
        try (Transaction tx = Transaction.openRoot()) {
            int extracted = distiller.getTankIn().extractInternal(0, water, 1000, tx);
            assertTrue(extracted == 1000,
                    "InputTank.extractInternal must drain the full amount, got " + extracted);
            tx.commit();
        }
        assertTrue(distiller.getTankIn().getAmountAsLong(0) == 0,
                "InputTank should be empty after extractInternal");

        helper.succeed();
    }

    /**
     * Both output tanks must reject external inserts so a right-click with a filled
     * bucket can't dump arbitrary fluids into the gas/liquid outputs (and pipes can't
     * either). The craft loop's {@code insertInternal} bypasses the gate so recipe
     * results still land — same pattern the heat exchanger's OutputTank uses.
     */
    public static void testOutputTanksRejectExternalInsertButAcceptInternal(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, BCFactoryBlocks.DISTILLER.get());
        TileDistiller_BC8 distiller = helper.getBlockEntity(pos, TileDistiller_BC8.class);
        FluidResource water = FluidResource.of(new FluidStack(Fluids.WATER, 500));

        for (TileDistiller_BC8.OutputTank tank : new TileDistiller_BC8.OutputTank[] {
                distiller.getTankGasOut(), distiller.getTankLiquidOut() }) {
            try (Transaction tx = Transaction.openRoot()) {
                int inserted = tank.insert(0, water, 500, tx);
                assertTrue(inserted == 0,
                        "OutputTank external insert must return 0, got " + inserted);
                tx.commit();
            }
            assertTrue(tank.getAmountAsLong(0) == 0,
                    "OutputTank should still be empty after rejected external insert");

            try (Transaction tx = Transaction.openRoot()) {
                int inserted = tank.insertInternal(0, water, 500, tx);
                assertTrue(inserted == 500,
                        "OutputTank.insertInternal must accept full amount, got " + inserted);
                tx.commit();
            }
            assertTrue(tank.getAmountAsLong(0) == 500,
                    "OutputTank should hold 500mb after internal insert, got " + tank.getAmountAsLong(0));

            // Internal flag must reset so a follow-up external insert is still rejected.
            try (Transaction tx = Transaction.openRoot()) {
                int inserted = tank.insert(0, water, 500, tx);
                assertTrue(inserted == 0,
                        "OutputTank external insert after insertInternal must still return 0, got " + inserted);
                tx.commit();
            }
        }
        helper.succeed();
    }
}
