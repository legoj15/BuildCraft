/*
 * Copyright (c) 2026 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.snapshot;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/**
 * Covers the Builder's fluid-handling overload on {@link SchematicBlockDefault#build(net.minecraft.world.level.Level, BlockPos, EnumFluidHandlingMode)}.
 * <p>
 * Note: the full Builder tick loop (snapshot load, check queue, break/place budgeting) isn't
 * exercised here — only the terminal {@code build} call that the place queue eventually hits.
 * That's the part of the pipeline that actually decides whether to waterlog, destroy, or
 * overwrite a fluid, and it's the piece most vulnerable to regressions when the surrounding
 * code changes.
 */
public class FluidHandlingModeTester {

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) throw new IllegalStateException(msg);
    }

    /** Constructs a bare {@link SchematicBlockDefault} for {@code state}. Same pattern as
     *  BlueprintReplaceTester — same-package protected-field access. */
    private static SchematicBlockDefault schem(BlockState state) {
        SchematicBlockDefault s = new SchematicBlockDefault();
        s.blockState = state;
        s.placeBlock = state.getBlock();
        return s;
    }

    /**
     * REPLACE over a water source with a waterloggable block must waterlog, not destroy. The
     * user's intent here is "keep the water if possible, place the block anyway" — the most
     * aesthetically expected outcome.
     */
    public static void testReplaceWaterSourceWithWaterloggableBlock(GameTestHelper helper) {
        try {
            BlockPos local = new BlockPos(1, 2, 1);
            BlockPos abs = helper.absolutePos(local);
            helper.getLevel().setBlock(abs, Blocks.WATER.defaultBlockState(), 3);
            assertTrue(helper.getLevel().getFluidState(abs).getType() == Fluids.WATER,
                    "precondition: water source set");

            SchematicBlockDefault s = schem(Blocks.OAK_FENCE.defaultBlockState());
            boolean placed = s.build(helper.getLevel(), abs, EnumFluidHandlingMode.REPLACE);
            assertTrue(placed, "build() must report success");

            BlockState after = helper.getLevel().getBlockState(abs);
            assertTrue(after.getBlock() == Blocks.OAK_FENCE, "block should be oak fence");
            assertTrue(after.hasProperty(BlockStateProperties.WATERLOGGED)
                    && after.getValue(BlockStateProperties.WATERLOGGED),
                    "oak fence should be waterlogged");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /**
     * REPLACE over a water source with a non-waterloggable block destroys the fluid before
     * placing. Stone has no WATERLOGGED property, so we can't preserve the water — that's
     * acceptable because the user asked for REPLACE, not CLEAR-water-only.
     */
    public static void testReplaceWaterSourceWithSolidBlock(GameTestHelper helper) {
        try {
            BlockPos local = new BlockPos(1, 2, 1);
            BlockPos abs = helper.absolutePos(local);
            helper.getLevel().setBlock(abs, Blocks.WATER.defaultBlockState(), 3);

            SchematicBlockDefault s = schem(Blocks.STONE.defaultBlockState());
            boolean placed = s.build(helper.getLevel(), abs, EnumFluidHandlingMode.REPLACE);
            assertTrue(placed, "build() must report success");

            BlockState after = helper.getLevel().getBlockState(abs);
            assertTrue(after.getBlock() == Blocks.STONE, "block should be stone");
            FluidState fluidAfter = helper.getLevel().getFluidState(abs);
            assertTrue(fluidAfter.isEmpty(), "fluid state should be empty");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /**
     * REPLACE over a lava source with a waterloggable block still destroys the lava (there's
     * no "lava-logged" vanilla state). Prevents a regression where waterlog logic runs for any
     * fluid instead of water specifically.
     */
    public static void testReplaceLavaSourceWithWaterloggableBlock(GameTestHelper helper) {
        try {
            BlockPos local = new BlockPos(1, 2, 1);
            BlockPos abs = helper.absolutePos(local);
            helper.getLevel().setBlock(abs, Blocks.LAVA.defaultBlockState(), 3);

            SchematicBlockDefault s = schem(Blocks.OAK_FENCE.defaultBlockState());
            boolean placed = s.build(helper.getLevel(), abs, EnumFluidHandlingMode.REPLACE);
            assertTrue(placed, "build() must report success");

            BlockState after = helper.getLevel().getBlockState(abs);
            assertTrue(after.getBlock() == Blocks.OAK_FENCE, "block should be oak fence");
            assertTrue(!after.getValue(BlockStateProperties.WATERLOGGED),
                    "oak fence should NOT be waterlogged over lava");
            FluidState fluidAfter = helper.getLevel().getFluidState(abs);
            assertTrue(fluidAfter.isEmpty(), "lava should be destroyed");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /**
     * CLEAR behaves exactly like REPLACE at the place-call site — the schematic-air+source
     * destruction happens in {@code SnapshotBuilder}'s break-queue filter, not in build(). So
     * calling build(..., CLEAR) on a water+waterloggable pair must still waterlog.
     */
    public static void testClearAtPlaceSiteBehavesLikeReplace(GameTestHelper helper) {
        try {
            BlockPos local = new BlockPos(1, 2, 1);
            BlockPos abs = helper.absolutePos(local);
            helper.getLevel().setBlock(abs, Blocks.WATER.defaultBlockState(), 3);

            SchematicBlockDefault s = schem(Blocks.OAK_FENCE.defaultBlockState());
            boolean placed = s.build(helper.getLevel(), abs, EnumFluidHandlingMode.CLEAR);
            assertTrue(placed, "build() must report success");

            BlockState after = helper.getLevel().getBlockState(abs);
            assertTrue(after.getBlock() == Blocks.OAK_FENCE, "block should be oak fence");
            assertTrue(after.getValue(BlockStateProperties.WATERLOGGED),
                    "CLEAR should still waterlog at place sites (only schematic-air+source goes through break queue)");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /**
     * NO_REPLACE must not waterlog. In the real builder flow this path would never be reached
     * (the break filter skips the position), but calling build directly with NO_REPLACE still
     * must not add a WATERLOGGED property to the placed state — otherwise the invariant that
     * the mode controls fluid preservation is broken.
     */
    public static void testNoReplaceModeDoesNotWaterlog(GameTestHelper helper) {
        try {
            BlockPos local = new BlockPos(1, 2, 1);
            BlockPos abs = helper.absolutePos(local);
            helper.getLevel().setBlock(abs, Blocks.WATER.defaultBlockState(), 3);

            SchematicBlockDefault s = schem(Blocks.OAK_FENCE.defaultBlockState());
            boolean placed = s.build(helper.getLevel(), abs, EnumFluidHandlingMode.NO_REPLACE);
            assertTrue(placed, "build() must report success");

            BlockState after = helper.getLevel().getBlockState(abs);
            assertTrue(after.getBlock() == Blocks.OAK_FENCE, "block should be oak fence");
            assertTrue(!after.getValue(BlockStateProperties.WATERLOGGED),
                    "NO_REPLACE must not opportunistically waterlog");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /**
     * Flowing (non-source) fluid must not trigger destroyBlock — the branch only fires on
     * sources. {@code setBlock} will overwrite the flowing water anyway, but destroyBlock would
     * produce an unwanted block-break sound/particle cluster in the user's face.
     */
    public static void testFlowingFluidIsNotSpeciallyDestroyed(GameTestHelper helper) {
        try {
            BlockPos local = new BlockPos(1, 2, 1);
            BlockPos abs = helper.absolutePos(local);
            helper.getLevel().setBlock(abs, Blocks.WATER.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LiquidBlock.LEVEL, 5), 3);
            FluidState pre = helper.getLevel().getFluidState(abs);
            assertTrue(!pre.isEmpty() && !pre.isSource(),
                    "precondition: flowing water (non-source) set");

            SchematicBlockDefault s = schem(Blocks.STONE.defaultBlockState());
            boolean placed = s.build(helper.getLevel(), abs, EnumFluidHandlingMode.REPLACE);
            assertTrue(placed, "build() must report success");

            BlockState after = helper.getLevel().getBlockState(abs);
            assertTrue(after.getBlock() == Blocks.STONE, "block should be stone");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /**
     * {@link EnumFluidHandlingMode#next()} must cycle through all three states and wrap around.
     * Guards against an off-by-one or missing state if someone reorders the enum later.
     */
    public static void testFluidModeCyclesThroughAllStates(GameTestHelper helper) {
        try {
            EnumFluidHandlingMode a = EnumFluidHandlingMode.NO_REPLACE;
            EnumFluidHandlingMode b = a.next();
            EnumFluidHandlingMode c = b.next();
            EnumFluidHandlingMode d = c.next();
            assertTrue(b == EnumFluidHandlingMode.REPLACE, "NO_REPLACE.next() should be REPLACE");
            assertTrue(c == EnumFluidHandlingMode.CLEAR, "REPLACE.next() should be CLEAR");
            assertTrue(d == EnumFluidHandlingMode.NO_REPLACE, "CLEAR.next() should wrap to NO_REPLACE");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /**
     * Regression: after REPLACE waterlogs a placed block, {@code isBuilt} must report it as built.
     * Otherwise the builder would mark the position TO_BREAK every check tick (schematic dry,
     * world wet) and loop break+place forever, wasting items and power.
     */
    public static void testIsBuiltAcceptsWorldWaterloggedWhenSchematicDry(GameTestHelper helper) {
        try {
            BlockPos local = new BlockPos(1, 2, 1);
            BlockPos abs = helper.absolutePos(local);
            // Simulate the post-REPLACE state: schematic captured the dry fence, the world has
            // a waterlogged fence (because REPLACE waterlogged it on top of a water source).
            BlockState dryFence = Blocks.OAK_FENCE.defaultBlockState();
            BlockState wetFence = dryFence.setValue(BlockStateProperties.WATERLOGGED, true);
            helper.getLevel().setBlock(abs, wetFence, 3);

            SchematicBlockDefault s = schem(dryFence);
            s.canBeReplacedWithBlocks.add(Blocks.OAK_FENCE);
            assertTrue(s.isBuilt(helper.getLevel(), abs),
                    "isBuilt must accept world=waterlogged when schematic=dry to avoid break/place loop");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /**
     * The reverse direction: schematic asks for waterlogged, world is dry — that's a real
     * unfinished build, isBuilt must return false. Guards against the regression fix being
     * over-broad.
     */
    public static void testIsBuiltRejectsWorldDryWhenSchematicWaterlogged(GameTestHelper helper) {
        try {
            BlockPos local = new BlockPos(1, 2, 1);
            BlockPos abs = helper.absolutePos(local);
            BlockState dryFence = Blocks.OAK_FENCE.defaultBlockState();
            BlockState wetFence = dryFence.setValue(BlockStateProperties.WATERLOGGED, true);
            helper.getLevel().setBlock(abs, dryFence, 3);

            SchematicBlockDefault s = schem(wetFence);
            s.canBeReplacedWithBlocks.add(Blocks.OAK_FENCE);
            assertTrue(!s.isBuilt(helper.getLevel(), abs),
                    "isBuilt must reject world=dry when schematic asked for waterlogged");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /**
     * {@link EnumFluidHandlingMode#fromOrdinal(int)} must clamp bad indices to the safe default
     * rather than throwing — the NBT loader and ContainerData path both rely on this.
     */
    public static void testFluidModeFromOrdinalClampsOutOfRange(GameTestHelper helper) {
        try {
            assertTrue(EnumFluidHandlingMode.fromOrdinal(-1) == EnumFluidHandlingMode.NO_REPLACE,
                    "negative ordinal should clamp to NO_REPLACE");
            assertTrue(EnumFluidHandlingMode.fromOrdinal(999) == EnumFluidHandlingMode.NO_REPLACE,
                    "out-of-range ordinal should clamp to NO_REPLACE");
            assertTrue(EnumFluidHandlingMode.fromOrdinal(1) == EnumFluidHandlingMode.REPLACE,
                    "ordinal 1 should be REPLACE");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }
}
