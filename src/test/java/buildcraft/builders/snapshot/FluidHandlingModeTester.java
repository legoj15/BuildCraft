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

    /**
     * Fragile-block defer regression test. The user's case: REPLACE-ing a snow_layer over a water
     * source whose horizontal neighbour also holds a water source. Without the defer, the snow
     * gets placed (canSurvive succeeds because there's a stone floor beneath), then the
     * neighbour's water flows back into the snow position on the next fluid tick and destroys
     * it; the Builder consumes one snow item per cycle and never finishes. With the defer,
     * build() returns false and the existing cancelPlaceTask path refunds the item.
     */
    public static void testFragileBlockDeferredWhenNeighbourSourceExists(GameTestHelper helper) {
        try {
            BlockPos local = new BlockPos(2, 2, 2);
            BlockPos abs = helper.absolutePos(local);
            // Sturdy floor so the snow's own canSurvive check passes — we want the test to fail
            // for the *fragile* reason, not the support reason.
            helper.getLevel().setBlock(abs.below(), Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(abs, Blocks.WATER.defaultBlockState(), 3);
            helper.getLevel().setBlock(abs.east(), Blocks.WATER.defaultBlockState(), 3);

            SchematicBlockDefault s = schem(Blocks.SNOW.defaultBlockState());
            boolean placed = s.build(helper.getLevel(), abs, EnumFluidHandlingMode.REPLACE);
            assertTrue(!placed, "build() must defer: snow next to a water source would be flooded out the moment the source at this position is destroyed");

            // The deferred fluid-destroy must NOT have fired — the source should still be there.
            FluidState stillAtPos = helper.getLevel().getFluidState(abs);
            assertTrue(!stillAtPos.isEmpty() && stillAtPos.isSource(),
                    "deferring fragile placement must leave the world untouched: water source must still exist at the position");
            BlockState atPos = helper.getLevel().getBlockState(abs);
            assertTrue(atPos.getBlock() != Blocks.SNOW, "snow must not have been placed");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /**
     * Counterpart: an isolated source (no fluid neighbours) lets fragile placement succeed. The
     * REPLACE path destroys the source and the snow stays because nothing flows back. Guards
     * against the defer being too aggressive.
     */
    public static void testFragileBlockPlacedWhenSourceIsIsolated(GameTestHelper helper) {
        try {
            BlockPos local = new BlockPos(2, 2, 2);
            BlockPos abs = helper.absolutePos(local);
            helper.getLevel().setBlock(abs.below(), Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(abs, Blocks.WATER.defaultBlockState(), 3);
            // No water at any horizontal/up neighbour — clean isolated source.

            SchematicBlockDefault s = schem(Blocks.SNOW.defaultBlockState());
            boolean placed = s.build(helper.getLevel(), abs, EnumFluidHandlingMode.REPLACE);
            assertTrue(placed, "build() must succeed when the source is isolated; no neighbour fluid means nothing flows back to destroy the snow");

            BlockState atPos = helper.getLevel().getBlockState(abs);
            assertTrue(atPos.getBlock() == Blocks.SNOW, "snow should be present at the position");
            assertTrue(helper.getLevel().getFluidState(abs).isEmpty(),
                    "isolated source should have been destroyed before the snow placement");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /**
     * Solid blocks (canBeReplaced returns false because Properties.replaceable is unset) must
     * not be deferred even when neighbour fluid exists — water can't displace stone, so the
     * place→destroy loop the defer is guarding against can't happen. Without this carve-out the
     * defer would over-fire and stall every leaky-pool replacement.
     */
    public static void testSolidBlockPlacedDespiteAdjacentFluid(GameTestHelper helper) {
        try {
            BlockPos local = new BlockPos(2, 2, 2);
            BlockPos abs = helper.absolutePos(local);
            helper.getLevel().setBlock(abs, Blocks.WATER.defaultBlockState(), 3);
            helper.getLevel().setBlock(abs.east(), Blocks.WATER.defaultBlockState(), 3);

            SchematicBlockDefault s = schem(Blocks.STONE.defaultBlockState());
            boolean placed = s.build(helper.getLevel(), abs, EnumFluidHandlingMode.REPLACE);
            assertTrue(placed, "stone is solid (canBeReplaced=false); fragile defer must NOT fire even with neighbour water");

            BlockState atPos = helper.getLevel().getBlockState(abs);
            assertTrue(atPos.getBlock() == Blocks.STONE, "stone should be present");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /**
     * Vertical case: water source above a target position is enough to defer fragile placement,
     * because vanilla's fluid update tick lets water above flow downward. Catches the case the
     * user explicitly called out — they wanted UP included in the neighbour check.
     */
    public static void testFragileBlockDeferredForFluidAbove(GameTestHelper helper) {
        try {
            BlockPos local = new BlockPos(2, 2, 2);
            BlockPos abs = helper.absolutePos(local);
            helper.getLevel().setBlock(abs.below(), Blocks.STONE.defaultBlockState(), 3);
            // Position itself starts empty — no fluid here, so the willDestroyFluidAtPos branch
            // won't fire. Only the fragile check (which inspects neighbours) should trip.
            helper.getLevel().setBlock(abs.above(), Blocks.WATER.defaultBlockState(), 3);

            SchematicBlockDefault s = schem(Blocks.SNOW.defaultBlockState());
            boolean placed = s.build(helper.getLevel(), abs, EnumFluidHandlingMode.REPLACE);
            assertTrue(!placed, "build() must defer when water sits above the target: it'll flow down into the snow on the next fluid tick");

            BlockState atPos = helper.getLevel().getBlockState(abs);
            assertTrue(atPos.isAir(), "position should remain air (no destructive operation should have happened)");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /**
     * Waterloggable blocks (oak_fence, glass_pane, …) coexist with their fluid: the REPLACE path
     * sets WATERLOGGED=true on the placed state instead of destroying the source, and water
     * naturally fills the block's collision void. canBeReplaced returns false for the waterlogged
     * variant, but more importantly: even if it returned true, the defer's "if waterlogged, skip"
     * branch exempts it. Confirms that the carve-out works.
     */
    public static void testWaterloggableBlockNotDeferredNearWater(GameTestHelper helper) {
        try {
            BlockPos local = new BlockPos(2, 2, 2);
            BlockPos abs = helper.absolutePos(local);
            helper.getLevel().setBlock(abs, Blocks.WATER.defaultBlockState(), 3);
            helper.getLevel().setBlock(abs.east(), Blocks.WATER.defaultBlockState(), 3);

            SchematicBlockDefault s = schem(Blocks.OAK_FENCE.defaultBlockState());
            boolean placed = s.build(helper.getLevel(), abs, EnumFluidHandlingMode.REPLACE);
            assertTrue(placed, "oak_fence is waterloggable; the REPLACE path should waterlog it and the fragile defer should not fire");

            BlockState atPos = helper.getLevel().getBlockState(abs);
            assertTrue(atPos.getBlock() == Blocks.OAK_FENCE, "fence should be present");
            assertTrue(atPos.getValue(BlockStateProperties.WATERLOGGED),
                    "fence should be waterlogged (REPLACE preserved the source by setting WATERLOGGED instead of destroying)");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /**
     * NO_REPLACE intentionally doesn't run the fragile defer — the mode's contract is "leave
     * fluids alone", and adding fluid-aware deferrals to the no-fluid-handling mode would be
     * surprising. The Builder's existing fluid-skip filter (in SnapshotBuilder) already keeps
     * fluid positions out of the queue under NO_REPLACE, so the defer would only kick in for
     * empty-position placements with adjacent water — a rarer case the mode's user opted out of
     * fluid handling for.
     */
    public static void testNoReplaceModeSkipsFragileCheck(GameTestHelper helper) {
        try {
            BlockPos local = new BlockPos(2, 2, 2);
            BlockPos abs = helper.absolutePos(local);
            helper.getLevel().setBlock(abs.below(), Blocks.STONE.defaultBlockState(), 3);
            // Position is air (no fluid). Adjacent water is present.
            helper.getLevel().setBlock(abs.east(), Blocks.WATER.defaultBlockState(), 3);

            SchematicBlockDefault s = schem(Blocks.SNOW.defaultBlockState());
            boolean placed = s.build(helper.getLevel(), abs, EnumFluidHandlingMode.NO_REPLACE);
            assertTrue(placed, "NO_REPLACE must skip the fragile defer entirely; the user opted out of fluid handling");

            BlockState atPos = helper.getLevel().getBlockState(abs);
            assertTrue(atPos.getBlock() == Blocks.SNOW, "snow should be placed under NO_REPLACE despite the adjacent water");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }
}
