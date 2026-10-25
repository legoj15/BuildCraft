/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.snapshot;

import static buildcraft.builders.BuilderRefloodTester.STONE;
import static buildcraft.builders.BuilderRefloodTester.builderQuiet;
import static buildcraft.builders.BuilderRefloodTester.cell;
import static buildcraft.builders.BuilderRefloodTester.drive;
import static buildcraft.builders.BuilderRefloodTester.expect;
import static buildcraft.builders.BuilderRefloodTester.setUpBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

import buildcraft.api.mj.MjAPI;
import buildcraft.builders.tile.TileBuilder;
import buildcraft.lib.misc.BlockUtil;
import buildcraft.lib.test.DropArenaUtil;
import buildcraft.lib.test.DropArenaUtil.Drops;

/**
 * Covers the Builder's fluid-handling overload on {@link SchematicBlockDefault#build(net.minecraft.world.level.Level, BlockPos, EnumFluidHandlingMode)}.
 * <p>
 * Note: most of these tests call only the terminal {@code build} call that the place queue eventually hits. That's
 * the part of the pipeline that actually decides whether to waterlog, destroy, or overwrite a fluid, and it's the
 * piece most vulnerable to regressions when the surrounding code changes. The one Builder tick-loop test
 * ({@link #testReplaceBuilderBreaksWaterloggedBlockBeforePlacing}) covers the routing in front of it.
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
     * CLEAR diverges from REPLACE at the place-call site (this changed from the original
     * "behaves like replace" assumption — that was correct when CLEAR's only contribution was
     * destroying schematic-air+source positions, but the user's stated intent is "clear all
     * fluids," which extends to *not* opportunistically waterlogging on the place path either).
     * For a dry-fence schematic over a water source, CLEAR now destroys the source and places
     * the fence dry; REPLACE keeps the opportunistic waterlog.
     */
    public static void testClearDestroysSourceInsteadOfWaterlogging(GameTestHelper helper) {
        try {
            BlockPos local = new BlockPos(1, 2, 1);
            BlockPos abs = helper.absolutePos(local);
            helper.getLevel().setBlock(abs, Blocks.WATER.defaultBlockState(), 3);

            SchematicBlockDefault s = schem(Blocks.OAK_FENCE.defaultBlockState());
            boolean placed = s.build(helper.getLevel(), abs, EnumFluidHandlingMode.CLEAR);
            assertTrue(placed, "build() must report success");

            BlockState after = helper.getLevel().getBlockState(abs);
            assertTrue(after.getBlock() == Blocks.OAK_FENCE, "block should be oak fence");
            assertTrue(!after.getValue(BlockStateProperties.WATERLOGGED),
                    "CLEAR must NOT opportunistically waterlog — schematic captured the fence dry, so place it dry");
            assertTrue(helper.getLevel().getFluidState(abs).isEmpty(),
                    "the original water source must be cleared");
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
        // Two volume markers are placed below, each with a water source beside it and stone under it. They must not
        // outlive the test: markers are registered in a dimension-wide cache that the marker connection tests scan
        // across arenas (see MarkerTester), and an unwalled source keeps flowing into the neighbouring arenas.
        BlockPos[] markerCells = { new BlockPos(2, 2, 4), new BlockPos(2, 2, 6) };
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

            // Dry placement next to water. A marker (like a pipe) is non-solid, so the old
            // canBeReplaced(WATER) predicate called it fragile and deferred it forever beside any
            // fluid — but it is a LiquidBlockContainer, so flowing fluid either waterlogs it or is
            // held back; it is never washed away. Both REPLACE and CLEAR must place it.
            BlockState marker = buildcraft.core.BCCoreBlocks.MARKER_VOLUME.get().defaultBlockState();
            assertTrue(!SchematicBlockDefault.isWashedAwayByFluid(marker),
                    "a dry waterloggable marker is never washed away by fluid, so it is not fragile");
            assertTrue(SchematicBlockDefault.isWashedAwayByFluid(Blocks.SNOW.defaultBlockState()),
                    "snow is still fragile");
            assertTrue(!SchematicBlockDefault.isWashedAwayByFluid(Blocks.STONE.defaultBlockState()),
                    "stone is not fragile");
            BlockState dryPipe = buildcraft.transport.BCTransportBlocks.PIPE_HOLDER.get().defaultBlockState();
            assertTrue(!SchematicBlockDefault.isWashedAwayByFluid(dryPipe),
                    "a dry pipe holder is never washed away by fluid, so it is not fragile");

            EnumFluidHandlingMode[] modes = {EnumFluidHandlingMode.REPLACE, EnumFluidHandlingMode.CLEAR};
            for (int i = 0; i < modes.length; i++) {
                BlockPos markerAbs = helper.absolutePos(markerCells[i]);
                helper.getLevel().setBlock(markerAbs.below(), Blocks.STONE.defaultBlockState(), 3);
                helper.getLevel().setBlock(markerAbs.east(), Blocks.WATER.defaultBlockState(), 3);
                boolean markerPlaced = schem(marker).build(helper.getLevel(), markerAbs, modes[i]);
                assertTrue(markerPlaced, "a dry marker next to water must not be deferred under " + modes[i]);
                BlockState markerAt = helper.getLevel().getBlockState(markerAbs);
                assertTrue(markerAt.is(buildcraft.core.BCCoreBlocks.MARKER_VOLUME.get()),
                        "marker should be present under " + modes[i]);
            }
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        } finally {
            // Pass or fail: remove the markers and the fluid that was set up beside them (and the fence's water).
            for (BlockPos cell : markerCells) {
                buildcraft.lib.test.MarkerArenaUtil.clearMarkers(helper, cell);
                helper.setBlock(cell.east(), Blocks.AIR);
                helper.setBlock(cell.below(), Blocks.AIR);
            }
            helper.setBlock(new BlockPos(2, 2, 2), Blocks.AIR);
            helper.setBlock(new BlockPos(3, 2, 2), Blocks.AIR);
        }
    }

    /**
     * Regression test for the user-reported "CLEAR-mode laser fires at water source but the
     * water never disappears" bug. Vanilla {@code Level.destroyBlock} preserves fluid via
     * {@code fluidState.createLegacyBlock()} — for a position whose only "block" is the fluid
     * itself, that's a silent no-op (the fluid block gets re-set to itself). The Builder's
     * break path was "succeeding" against water (returning {@code Optional.of(emptyList)},
     * removing the task from the queue) without actually clearing the water; the position
     * re-queued forever and the user saw a perpetual phantom-laser loop. Fix in
     * {@link BlockUtil#breakBlockAndGetDrops} routes pure-fluid blocks (LiquidBlock instances)
     * through {@code setBlock(AIR)} instead.
     */
    public static void testBreakBlockAndGetDropsClearsWaterSource(GameTestHelper helper) {
        try {
            BlockPos local = new BlockPos(2, 2, 2);
            BlockPos abs = helper.absolutePos(local);
            helper.getLevel().setBlock(abs, Blocks.WATER.defaultBlockState(), 3);
            assertTrue(helper.getLevel().getBlockState(abs).getBlock() instanceof LiquidBlock,
                    "precondition: water block placed");

            Optional<List<ItemStack>> result = BlockUtil.breakBlockAndGetDrops(
                (ServerLevel) helper.getLevel(),
                abs,
                new ItemStack(Items.DIAMOND_PICKAXE),
                (GameProfile) null);

            assertTrue(result.isPresent(),
                    "breakBlockAndGetDrops must report success for a fluid block");
            assertTrue(helper.getLevel().getFluidState(abs).isEmpty(),
                    "fluid state must actually be cleared — vanilla destroyBlock would preserve it via createLegacyBlock");
            assertTrue(helper.getLevel().getBlockState(abs).isAir(),
                    "block state must be air after break");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /**
     * Same fix applies to flowing fluid: the Builder's CLEAR mode now also accepts flowing
     * water for breaking (so that flowing water perpetually refilling from outside the build
     * area can be "mopped up"), and that path needs the fluid to actually disappear.
     */
    public static void testBreakBlockAndGetDropsClearsFlowingWater(GameTestHelper helper) {
        try {
            BlockPos local = new BlockPos(2, 2, 2);
            BlockPos abs = helper.absolutePos(local);
            // Set a flowing-water state (level=5, mid-flow). LiquidBlock.LEVEL is the flow
            // level: 0 = source, 1-7 = falling/flowing levels.
            BlockState flowing = Blocks.WATER.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LiquidBlock.LEVEL, 5);
            helper.getLevel().setBlock(abs, flowing, 3);

            Optional<List<ItemStack>> result = BlockUtil.breakBlockAndGetDrops(
                (ServerLevel) helper.getLevel(),
                abs,
                new ItemStack(Items.DIAMOND_PICKAXE),
                (GameProfile) null);

            assertTrue(result.isPresent(), "breakBlockAndGetDrops must succeed for flowing water");
            assertTrue(helper.getLevel().getFluidState(abs).isEmpty(),
                    "flowing fluid state must be cleared");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /**
     * Verifies that breaking a regular (non-fluid) block still uses {@code destroyBlock} so the
     * sound/particle level-events fire correctly. Stone is a plain solid block; after break, no
     * fluid restore should happen (there was no fluid to begin with). Guards against the fluid
     * carve-out being applied too broadly.
     */
    public static void testBreakBlockAndGetDropsStillBreaksSolidBlocks(GameTestHelper helper) {
        try {
            BlockPos local = new BlockPos(2, 2, 2);
            BlockPos abs = helper.absolutePos(local);
            helper.getLevel().setBlock(abs, Blocks.STONE.defaultBlockState(), 3);

            Optional<List<ItemStack>> result = BlockUtil.breakBlockAndGetDrops(
                (ServerLevel) helper.getLevel(),
                abs,
                new ItemStack(Items.DIAMOND_PICKAXE),
                (GameProfile) null);

            assertTrue(result.isPresent(), "stone break must succeed");
            assertTrue(!result.get().isEmpty(), "stone should drop cobblestone (or itself with silk touch — we use diamond pickaxe so cobblestone)");
            assertTrue(helper.getLevel().getBlockState(abs).isAir(),
                    "stone position must be air after break");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /**
     * Iron pickaxe on iron ore must yield raw_iron. Guards two pieces of the break path:
     * (a) the {@code tool} stack is threaded into the loot context — the 4-arg
     * {@code Block.getDrops} overload silently used {@code ItemStack.EMPTY} pre-fix, which
     * neuters silk_touch / fortune branches on any loot table that reads them; (b) the
     * BlockUtil tier gate recognises an iron pickaxe as a correct tool for iron ore —
     * over-gating would empty the drops list even though the wielded tool is sufficient.
     * <p>
     * Tier gating lives in BlockUtil (not in the loot table — vanilla ore loot tables only
     * carry {@code match_tool} for silk_touch), mirroring vanilla's
     * {@code requiresCorrectToolForDrops}/{@code isCorrectToolForDrops} short-circuit on the
     * player destroy path. The complement — wooden pickaxe under-gates and yields nothing —
     * is covered by {@link #testBreakBlockAndGetDropsRespectsToolTier}.
     */
    public static void testBreakBlockAndGetDropsHonoursToolForLoot(GameTestHelper helper) {
        try {
            BlockPos abs = helper.absolutePos(new BlockPos(2, 2, 2));
            helper.getLevel().setBlock(abs, Blocks.IRON_ORE.defaultBlockState(), 3);

            Optional<List<ItemStack>> result = BlockUtil.breakBlockAndGetDrops(
                (ServerLevel) helper.getLevel(),
                abs,
                new ItemStack(Items.IRON_PICKAXE),
                (GameProfile) null);

            assertTrue(result.isPresent(), "iron ore break must succeed");
            assertTrue(!result.get().isEmpty(),
                    "iron ore must drop raw iron under an iron pickaxe — tool param "
                            + "must be threaded into the loot context, not ignored");
            assertTrue(result.get().stream().anyMatch(s -> s.is(Items.RAW_IRON)),
                    "drops should include raw_iron");
            assertTrue(helper.getLevel().getBlockState(abs).isAir(),
                    "iron ore position must be air after break");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /**
     * Regression for the Builder's "absorb cleared fluid into tanks under CLEAR" feature.
     * {@link BlockUtil#breakBlockAndGetDropsWithXp} must return a non-empty
     * {@code capturedFluid} (1000 mB, matching the broken fluid type) when the target is a
     * {@link LiquidBlock} source. Without this the Builder can't tell that the position used
     * to hold a fluid — the block is already destroyed by the helper.
     */
    public static void testBreakBlockAndGetDropsWithXpCapturesFluidSource(GameTestHelper helper) {
        try {
            BlockPos abs = helper.absolutePos(new BlockPos(2, 2, 2));
            helper.getLevel().setBlock(abs, Blocks.WATER.defaultBlockState(), 3);

            Optional<BlockUtil.BreakResult> result = BlockUtil.breakBlockAndGetDropsWithXp(
                (ServerLevel) helper.getLevel(),
                abs,
                new ItemStack(Items.DIAMOND_PICKAXE),
                (GameProfile) null);

            assertTrue(result.isPresent(), "water source break must succeed");
            assertTrue(!result.get().capturedFluid().isEmpty(),
                    "water source must populate capturedFluid with the fluid we just removed");
            assertTrue(result.get().capturedFluid().getFluid() == Fluids.WATER,
                    "captured fluid must be water (matches the broken block)");
            assertTrue(result.get().capturedFluid().getAmount() == 1000,
                    "captured fluid amount must be one bucket per source");
            assertTrue(helper.getLevel().getBlockState(abs).isAir(),
                    "water position must be air after break");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /**
     * Complement of the above: flowing fluid (level > 0) is transient world-state with no real
     * volume — breaking a flowing-water position must NOT report a captured fluid, otherwise
     * the Builder would tank-fill on every leaky-boundary tick under CLEAR mode and silently
     * generate water from nothing.
     */
    public static void testBreakBlockAndGetDropsWithXpSkipsFlowingFluidCapture(GameTestHelper helper) {
        try {
            BlockPos abs = helper.absolutePos(new BlockPos(2, 2, 2));
            BlockState flowing = Blocks.WATER.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LiquidBlock.LEVEL, 5);
            helper.getLevel().setBlock(abs, flowing, 3);

            Optional<BlockUtil.BreakResult> result = BlockUtil.breakBlockAndGetDropsWithXp(
                (ServerLevel) helper.getLevel(),
                abs,
                new ItemStack(Items.DIAMOND_PICKAXE),
                (GameProfile) null);

            assertTrue(result.isPresent(), "flowing water break must succeed");
            assertTrue(result.get().capturedFluid().isEmpty(),
                    "flowing fluid must NOT be captured — only sources count as a bucket-worth");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /**
     * The XP-aware helper {@link BlockUtil#breakBlockAndGetDropsWithXp} must return a sane
     * non-negative XP value for a tier-positive ore break. Vanilla iron ore gives 0–2 XP
     * (UniformInt) per successful pickaxe break — iron-on-iron-ore is the cleanest tier-
     * positive case to exercise the {@code state.getExpDrop(...)} call shape end-to-end. The
     * tier-negative side is asymmetric: BlockUtil zeroes XP under a tier-gated break (no
     * harvest, no XP, parallel to the empty drops list), so a wooden-pick-on-iron-ore call
     * deterministically returns 0 — see {@link #testBreakBlockAndGetDropsRespectsToolTier}
     * for the drops side of that gate.
     */
    public static void testBreakBlockAndGetDropsWithXpReportsXp(GameTestHelper helper) {
        try {
            BlockPos abs = helper.absolutePos(new BlockPos(2, 2, 2));
            helper.getLevel().setBlock(abs, Blocks.IRON_ORE.defaultBlockState(), 3);

            Optional<BlockUtil.BreakResult> result = BlockUtil.breakBlockAndGetDropsWithXp(
                (ServerLevel) helper.getLevel(),
                abs,
                new ItemStack(Items.IRON_PICKAXE),
                (GameProfile) null);

            assertTrue(result.isPresent(), "iron ore break must succeed");
            assertTrue(!result.get().drops().isEmpty(),
                    "drops must be present under iron pickaxe");
            // Iron ore drops UniformInt(0, 2) XP per vanilla loot; ≥0 is the floor, but we
            // assert non-negative AND that the RNG occasionally rolls non-zero. To keep the
            // test deterministic we accept any value ≥ 0 — the real regression catch is
            // "the call path even reached getExpDrop with our tool." If the loot context
            // dropped the tool the way the old 4-arg getDrops did, this would still return 0
            // every time (no harvest condition met → 0 XP) AND drops would be empty, which
            // the prior test catches. So this test's job is to verify the API call shape
            // works end-to-end and produces a sane non-negative XP value.
            assertTrue(result.get().xp() >= 0, "xp must be non-negative");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /**
     * Complement of {@link #testBreakBlockAndGetDropsHonoursToolForLoot}: a wooden pickaxe
     * fails {@code isCorrectToolForDrops(ironOreState)}, so BlockUtil's tier gate empties the
     * drops list while still destroying the block. Without the gate, {@code Block.getDrops}
     * would happily run iron ore's loot table (which has no tier {@code match_tool} — only
     * silk_touch) and return raw_iron under any pickaxe, silently letting Builder/Filler
     * iron-tier laser-mine obsidian and ancient debris for free.
     */
    public static void testBreakBlockAndGetDropsRespectsToolTier(GameTestHelper helper) {
        try {
            BlockPos abs = helper.absolutePos(new BlockPos(2, 2, 2));
            helper.getLevel().setBlock(abs, Blocks.IRON_ORE.defaultBlockState(), 3);

            Optional<List<ItemStack>> result = BlockUtil.breakBlockAndGetDrops(
                (ServerLevel) helper.getLevel(),
                abs,
                new ItemStack(Items.WOODEN_PICKAXE),
                (GameProfile) null);

            assertTrue(result.isPresent(), "iron ore break must report success (block was removed)");
            assertTrue(result.get().isEmpty(),
                    "wooden pickaxe fails isCorrectToolForDrops on iron ore — BlockUtil tier "
                            + "gate must empty the drops list");
            assertTrue(helper.getLevel().getBlockState(abs).isAir(),
                    "iron ore position must still be air after break (drops absent ≠ break failed)");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /**
     * Regression test for the user-reported "schematic captured a dry fence, world has the same
     * fence waterlogged, CLEAR mode is supposed to clear the water." Without this fix the
     * Builder treated world-wet/schematic-dry as already-built (a workaround for REPLACE-mode
     * opportunistic waterlogging) so the position was never queued for any action and the water
     * stayed in the fence forever. Under CLEAR, the strict comparison kicks in and the place
     * path sets the placed state with WATERLOGGED=false, replacing the wet fence with a dry one
     * (and clearing the water as a side effect — for non-LiquidBlock waterloggable blocks the
     * fluid state IS the WATERLOGGED property).
     */
    public static void testClearModeDriesWaterloggedBlock(GameTestHelper helper) {
        try {
            BlockPos abs = helper.absolutePos(new BlockPos(2, 2, 2));
            BlockState wetFence = Blocks.OAK_FENCE.defaultBlockState()
                    .setValue(BlockStateProperties.WATERLOGGED, true);
            BlockState dryFence = Blocks.OAK_FENCE.defaultBlockState();
            helper.getLevel().setBlock(abs, wetFence, 3);
            assertTrue(!helper.getLevel().getFluidState(abs).isEmpty(),
                    "precondition: waterlogged fence has a water fluid state");

            SchematicBlockDefault s = schem(dryFence);
            s.canBeReplacedWithBlocks.add(Blocks.OAK_FENCE);
            boolean placed = s.build(helper.getLevel(), abs, EnumFluidHandlingMode.CLEAR);
            assertTrue(placed, "build() must succeed for the dry-fence-over-wet-fence case");

            BlockState after = helper.getLevel().getBlockState(abs);
            assertTrue(after.getBlock() == Blocks.OAK_FENCE, "fence should still be there");
            assertTrue(!after.getValue(BlockStateProperties.WATERLOGGED),
                    "CLEAR mode must dry the waterlogged fence to match the schematic");
            assertTrue(helper.getLevel().getFluidState(abs).isEmpty(),
                    "fluid state must be empty after drying");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /**
     * REPLACE mode keeps the existing opportunistic-waterlog behaviour: schematic dry, world has
     * water source, place a waterloggable block — it ends up wet, preserving the water "for
     * free." Regression guard against this round of changes accidentally breaking REPLACE.
     */
    public static void testReplaceModeStillOpportunisticallyWaterlogs(GameTestHelper helper) {
        try {
            BlockPos abs = helper.absolutePos(new BlockPos(2, 2, 2));
            helper.getLevel().setBlock(abs, Blocks.WATER.defaultBlockState(), 3);
            assertTrue(helper.getLevel().getFluidState(abs).getType() == Fluids.WATER,
                    "precondition: water source set");

            SchematicBlockDefault s = schem(Blocks.OAK_FENCE.defaultBlockState());
            boolean placed = s.build(helper.getLevel(), abs, EnumFluidHandlingMode.REPLACE);
            assertTrue(placed, "REPLACE place must succeed");

            BlockState after = helper.getLevel().getBlockState(abs);
            assertTrue(after.getBlock() == Blocks.OAK_FENCE, "fence placed");
            assertTrue(after.getValue(BlockStateProperties.WATERLOGGED),
                    "REPLACE must waterlog opportunistically (preserve water 'for free')");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /**
     * isBuilt's strict mode under CLEAR: schematic captured a dry fence, world has a wet fence,
     * CLEAR fluidMode → must report NOT built (the Builder then mops the cell; only
     * {@code BlueprintBuilder#isBlockCorrect} relaxes this, for water it can never keep out, see
     * {@code BuilderRefloodTester}).
     */
    public static void testIsBuiltStrictInClearMode(GameTestHelper helper) {
        try {
            BlockPos abs = helper.absolutePos(new BlockPos(2, 2, 2));
            BlockState dryFence = Blocks.OAK_FENCE.defaultBlockState();
            BlockState wetFence = dryFence.setValue(BlockStateProperties.WATERLOGGED, true);
            helper.getLevel().setBlock(abs, wetFence, 3);

            SchematicBlockDefault s = schem(dryFence);
            s.canBeReplacedWithBlocks.add(Blocks.OAK_FENCE);
            assertTrue(!s.isBuilt(helper.getLevel(), abs, EnumFluidHandlingMode.CLEAR),
                    "CLEAR mode must report NOT built when world wet ≠ schematic dry");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /**
     * The mirror invariant: under REPLACE/NO_REPLACE, the lenient comparison still kicks in so
     * the Builder doesn't mistakenly re-do break+place on a position that REPLACE itself
     * waterlogged a moment ago. Without the fluidMode check on the leniency, my CLEAR fix would
     * have broken REPLACE.
     */
    public static void testIsBuiltLenientInReplaceMode(GameTestHelper helper) {
        try {
            BlockPos abs = helper.absolutePos(new BlockPos(2, 2, 2));
            BlockState dryFence = Blocks.OAK_FENCE.defaultBlockState();
            BlockState wetFence = dryFence.setValue(BlockStateProperties.WATERLOGGED, true);
            helper.getLevel().setBlock(abs, wetFence, 3);

            SchematicBlockDefault s = schem(dryFence);
            s.canBeReplacedWithBlocks.add(Blocks.OAK_FENCE);
            assertTrue(s.isBuilt(helper.getLevel(), abs, EnumFluidHandlingMode.REPLACE),
                    "REPLACE/NO_REPLACE must continue to treat world wet / schematic dry as built");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /**
     * Vanilla water/lava ship with strength=100 — that's their *explosion resistance*, not break
     * time, but {@code BlockUtil.computeBlockBreakPower}'s {@code (hardness+1)*2 * 16 MJ}
     * formula reads it as time-to-break and produces 3232 MJ per fluid block (40× stone, more
     * than obsidian). At MAX_POWER_PER_TICK = 256 MJ/tick the Builder spent ~13 ticks per single
     * water break in CLEAR mode, breaking it for one tick before vanilla flow refilled and
     * starting another 13-tick cycle — the user-visible "laser fires forever, water never
     * clears" symptom that survived the BlockUtil fluid-clearing fix.
     */
    public static void testWaterBreakCostIsOneMj(GameTestHelper helper) {
        try {
            BlockPos abs = helper.absolutePos(new BlockPos(2, 2, 2));
            helper.getLevel().setBlock(abs, Blocks.WATER.defaultBlockState(), 3);
            long cost = BlockUtil.computeBlockBreakPower(helper.getLevel(), abs);
            assertTrue(cost == MjAPI.MJ,
                    "water break cost must be 1 MJ (vanilla treats fluids as free to break), got " + cost);
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /**
     * Same as above for lava — same hardness-100 trap. Confirms the LiquidBlock check covers all
     * pure-fluid block types, not just water specifically.
     */
    public static void testLavaBreakCostIsOneMj(GameTestHelper helper) {
        try {
            BlockPos abs = helper.absolutePos(new BlockPos(2, 2, 2));
            helper.getLevel().setBlock(abs, Blocks.LAVA.defaultBlockState(), 3);
            long cost = BlockUtil.computeBlockBreakPower(helper.getLevel(), abs);
            assertTrue(cost == MjAPI.MJ,
                    "lava break cost must be 1 MJ, got " + cost);
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /**
     * Solid blocks must keep the original {@code (hardness+1)*2 * 16 MJ} formula — the carve-out
     * is for LiquidBlock instances only, not for everything. Stone (hardness 1.5) should produce
     * cost = 16 * 1MJ * (1.5+1)*2 = 80 MJ.
     */
    public static void testSolidBlockBreakCostUnchanged(GameTestHelper helper) {
        try {
            BlockPos abs = helper.absolutePos(new BlockPos(2, 2, 2));
            helper.getLevel().setBlock(abs, Blocks.STONE.defaultBlockState(), 3);
            long cost = BlockUtil.computeBlockBreakPower(helper.getLevel(), abs);
            assertTrue(cost == 80L * MjAPI.MJ,
                    "stone break cost must be 80 MJ (original formula), got " + (cost / MjAPI.MJ) + " MJ");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /**
     * A placement that fails must leave the world as it found it. The fluid cases: a placement that fails after it has
     * already overwritten a fluid source must put the source back. The source is gone the moment the block goes in,
     * and a block that pops straight away (a torch or lantern with nothing under it, a door whose lower half has no
     * floor) used to be rolled back to air, so the Builder lost the lava or water for good while it refunded the
     * item. Those cases: a torch into lava, a torch into water, a lantern into water (REPLACE waterlogs it, CLEAR
     * places it dry), and a door whose upper cell, or both cells, hold water.
     * <p>
     * The block cases: a cell that holds a waterlogged block keeps it. A refused placement (the cell already holds
     * the very state, a waterlogged fence) must not be "restored" to bare water, which would delete the fence; a
     * torch that pops after replacing a waterlogged slab or stair must give the slab or stair back, where the source
     * alone would leave plain water in its place.
     * <p>
     * And a cell that held anything else goes back as it was, never to air: flowing water (not a source), short grass
     * and a snow layer (replaceable blocks), and both cells of a door (a lower cell, and the upper cell the door's
     * other half overwrote). The door's lower cell holds only what stands with nothing under it (fluid, a
     * waterlogged slab): a plant put back there would be killed by the restore of the cell above it, whatever the
     * code under test did. Every case runs and every broken one is reported.
     */
    public static void testFailedPlacementKeepsFluidSource(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 2, 1);
        BlockPos doorAt = new BlockPos(3, 2, 1);
        try {
            BlockState torch = Blocks.TORCH.defaultBlockState();
            BlockState lantern = Blocks.LANTERN.defaultBlockState();
            BlockState door = Blocks.OAK_DOOR.defaultBlockState();
            BlockState wetFence = Blocks.OAK_FENCE.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true);
            BlockState wetSlab = Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true);
            BlockState wetStairs = Blocks.OAK_STAIRS.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true);
            BlockState flowingWater = Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 3);
            BlockState grass = Blocks.SHORT_GRASS.defaultBlockState();
            BlockState snowLayer = Blocks.SNOW.defaultBlockState();
            List<String> broken = new ArrayList<>();
            for (EnumFluidHandlingMode mode : EnumFluidHandlingMode.values()) {
                check(broken, () -> assertFailedPlaceLeavesCell(helper, "a fence the cell already holds", mode,
                        wetFence, wetFence, at));
                check(broken, () -> assertFailedPlaceLeavesCell(helper, "torch over a waterlogged slab", mode,
                        torch, wetSlab, at));
                check(broken, () -> assertFailedPlaceLeavesCell(helper, "torch over waterlogged stairs", mode,
                        torch, wetStairs, at));
                // Not a source, and not a block that holds one: still what the cell held, never air.
                check(broken, () -> assertFailedPlaceLeavesCell(helper, "torch over flowing water", mode,
                        torch, flowingWater, at));
                check(broken, () -> assertFailedPlaceLeavesCell(helper, "torch over short grass", mode,
                        torch, grass, at));
                check(broken, () -> assertFailedPlaceLeavesCell(helper, "torch over a snow layer", mode,
                        torch, snowLayer, at));
                // Both cells of a two-cell block: each goes back to what it held.
                check(broken, () -> assertFailedPlaceLeavesCells(helper, "door over flowing water under snow", mode,
                        door, doorAt, new BlockPos[] {doorAt, doorAt.above()}, flowingWater, snowLayer));
                check(broken, () -> assertFailedPlaceLeavesCells(helper, "door over a waterlogged slab under grass",
                        mode, door, doorAt, new BlockPos[] {doorAt, doorAt.above()}, wetSlab, grass));
                check(broken, () -> assertFailedPlaceLeavesCells(helper, "door over a water source under flowing "
                        + "water", mode, door, doorAt, new BlockPos[] {doorAt, doorAt.above()},
                        Blocks.WATER.defaultBlockState(), flowingWater));
            }
            for (EnumFluidHandlingMode mode : new EnumFluidHandlingMode[] {
                    EnumFluidHandlingMode.REPLACE, EnumFluidHandlingMode.CLEAR}) {
                check(broken, () -> assertFailedPlaceKeepsSource(helper, "torch into lava", mode, torch, Blocks.LAVA,
                        at, at));
                check(broken, () -> assertFailedPlaceKeepsSource(helper, "torch into water", mode, torch,
                        Blocks.WATER, at, at));
                check(broken, () -> assertFailedPlaceKeepsSource(helper, "lantern into water", mode, lantern,
                        Blocks.WATER, at, at));
                check(broken, () -> assertFailedPlaceKeepsSource(helper, "door under water", mode, door,
                        Blocks.WATER, doorAt, doorAt.above()));
                check(broken, () -> assertFailedPlaceKeepsSource(helper, "door into two water cells", mode, door,
                        Blocks.WATER, doorAt, doorAt, doorAt.above()));
            }
            assertTrue(broken.isEmpty(), broken.size() + " broken: " + String.join(" | ", broken));
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        } finally {
            for (BlockPos cell : new BlockPos[] {at, doorAt, doorAt.above()}) {
                helper.setBlock(cell, Blocks.AIR);
            }
        }
    }

    /** Runs one case, noting its failure instead of ending the test, so a run names every broken case. */
    private static void check(List<String> broken, Runnable one) {
        try {
            one.run();
        } catch (IllegalStateException e) {
            broken.add(e.getMessage());
        }
    }

    /**
     * Puts {@code before} at {@code placeAt} (arena-relative) over an empty cell, builds {@code wanted} there, and
     * asserts nothing was placed (the block pops with nothing under it, or is already there) and the cell still holds
     * exactly {@code before}. Resets the cell afterwards so the next case starts clean.
     */
    private static void assertFailedPlaceLeavesCell(GameTestHelper helper, String what, EnumFluidHandlingMode mode,
            BlockState wanted, BlockState before, BlockPos placeAt) {
        Level level = helper.getLevel();
        BlockPos abs = helper.absolutePos(placeAt);
        level.setBlock(abs.below(), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(abs, before, 3);
        boolean placed = schem(wanted).build(level, abs, mode);
        try {
            assertTrue(!placed, what + ": nothing could be placed, so the " + mode + " build must report false");
            BlockState after = level.getBlockState(abs);
            assertTrue(after == before, what + ": a failed " + mode + " placement must leave " + before
                    + " in the cell, got " + after);
        } finally {
            level.setBlock(abs, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    /**
     * {@link #assertFailedPlaceLeavesCell} for a block that fills several cells: puts {@code before[i]} in
     * {@code cells[i]} (arena-relative), builds {@code wanted} at {@code placeAt} over an empty cell below it, and
     * asserts the build fails and every cell holds exactly what it did. Resets the cells afterwards.
     */
    private static void assertFailedPlaceLeavesCells(GameTestHelper helper, String what, EnumFluidHandlingMode mode,
            BlockState wanted, BlockPos placeAt, BlockPos[] cells, BlockState... before) {
        Level level = helper.getLevel();
        BlockPos abs = helper.absolutePos(placeAt);
        level.setBlock(abs.below(), Blocks.AIR.defaultBlockState(), 3);
        for (int i = 0; i < cells.length; i++) {
            level.setBlock(helper.absolutePos(cells[i]), before[i], 3);
        }
        boolean placed = schem(wanted).build(level, abs, mode);
        try {
            assertTrue(!placed, what + " has nothing to stand on, so the " + mode + " placement must fail");
            for (int i = 0; i < cells.length; i++) {
                BlockState after = level.getBlockState(helper.absolutePos(cells[i]));
                assertTrue(after == before[i], what + ": a failed " + mode + " placement must leave " + before[i]
                        + " at " + cells[i] + ", got " + after);
            }
        } finally {
            for (BlockPos cell : cells) {
                level.setBlock(helper.absolutePos(cell), Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    /** A bottom oak slab, a stairs or a chest, with the water of a waterlogged block in it. */
    private static BlockState waterlogged(Block block) {
        return block.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true);
    }

    /**
     * A placement that overwrites a real block must not void it. The Builder's own break would have dropped it, and
     * a Builder in REPLACE mode places straight over a cell that holds fluid, a waterlogged slab or stair included:
     * the slab was destroyed with no drop, by {@code destroyBlock(pos, false)} when the placed block cannot hold the
     * water, and by the {@code setBlock} itself when it can. The block is dropped once the placement has succeeded.
     * Four cells, each its own case, share one drop scan. The placed blocks are the ones with no collision (a
     * pressure plate, a sign) because a drop popped into a full block that stays is pushed out of it and may leave
     * the cell before the scan; the floor is there so the drop lands in the cell:
     * <ul>
     * <li>a pressure plate over a waterlogged slab (REPLACE destroys the water first): the slab drops;</li>
     * <li>a standing sign over a waterlogged slab (REPLACE waterlogs the sign): the slab drops;</li>
     * <li>a top slab over the waterlogged bottom slab: the same block with another state, so nothing is displaced and
     *     nothing drops;</li>
     * <li>a torch over a waterlogged slab with nothing under it: the torch pops, the slab goes back, and no slab
     *     dropped, or the slab would be in the world twice.</li>
     * </ul>
     */
    public static void testPlacementDropsTheBlockItDisplaces(GameTestHelper helper) {
        BlockPos underPlate = new BlockPos(1, 2, 1);
        BlockPos underSign = new BlockPos(4, 2, 1);
        BlockPos underSlab = new BlockPos(1, 2, 4);
        BlockPos underTorch = new BlockPos(4, 2, 4);
        BlockState wetSlab = waterlogged(Blocks.OAK_SLAB);
        DropArenaUtil.awaitDrops(helper, () -> {
            Level level = helper.getLevel();
            for (BlockPos cell : new BlockPos[] {underPlate, underSign, underSlab, underTorch}) {
                // A floor under every cell but the torch's, so a drop lands in its cell instead of falling out of it.
                level.setBlock(helper.absolutePos(cell.below()),
                        cell.equals(underTorch) ? Blocks.AIR.defaultBlockState() : Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(helper.absolutePos(cell), wetSlab, 3);
            }
            assertTrue(schem(Blocks.OAK_PRESSURE_PLATE.defaultBlockState())
                    .build(level, helper.absolutePos(underPlate), EnumFluidHandlingMode.REPLACE),
                    "the pressure plate must be placed over the waterlogged slab");
            assertTrue(level.getBlockState(helper.absolutePos(underPlate)).is(Blocks.OAK_PRESSURE_PLATE),
                    "the pressure plate must stand");
            assertTrue(schem(Blocks.OAK_SIGN.defaultBlockState())
                    .build(level, helper.absolutePos(underSign), EnumFluidHandlingMode.REPLACE),
                    "the sign must be placed over the waterlogged slab");
            assertTrue(level.getBlockState(helper.absolutePos(underSign)).is(Blocks.OAK_SIGN), "the sign must stand");
            assertTrue(schem(Blocks.OAK_SLAB.defaultBlockState()
                    .setValue(SlabBlock.TYPE, SlabType.TOP))
                    .build(level, helper.absolutePos(underSlab), EnumFluidHandlingMode.REPLACE),
                    "the top slab must be placed over the bottom one");
            assertTrue(!schem(Blocks.TORCH.defaultBlockState())
                    .build(level, helper.absolutePos(underTorch), EnumFluidHandlingMode.REPLACE),
                    "a torch with nothing under it cannot be placed");
            assertTrue(level.getBlockState(helper.absolutePos(underTorch)) == wetSlab,
                    "the slab must be back where the torch popped");
        }, Drops.near(underPlate, 1.5).exactly(Items.OAK_SLAB, 1)
                .alsoNear(underSign, 1.5).exactly(Items.OAK_SLAB, 1)
                .alsoNear(underSlab, 1.5).absent(Items.OAK_SLAB)
                .alsoNear(underTorch, 1.5).absent(Items.OAK_SLAB)
                .andCheck(() -> {
                    for (BlockPos cell : new BlockPos[] {underPlate, underSign, underSlab, underTorch}) {
                        helper.setBlock(cell, Blocks.AIR);
                        helper.setBlock(cell.below(), Blocks.AIR);
                    }
                }));
    }

    /**
     * The contents of a container the placement displaces are handled as the Builder's own break handles them: the
     * chest's contents spill in the world (vanilla does that when the block goes), and the chest item drops with
     * them. A waterlogged chest holding three diamonds, replaced by a pressure plate (which, unlike a full block,
     * does not push the drops out of the cell), leaves one chest and three diamonds, each exactly once: the contents
     * are neither lost with the block nor duplicated by the drop.
     */
    public static void testPlacementOverWaterloggedChestDropsChestAndContentsOnce(GameTestHelper helper) {
        BlockPos chestAt = new BlockPos(2, 2, 2);
        DropArenaUtil.awaitDrops(helper, () -> {
            Level level = helper.getLevel();
            BlockPos abs = helper.absolutePos(chestAt);
            // A floor, so the drops land in the cell instead of falling out of it.
            level.setBlock(abs.below(), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(abs, waterlogged(Blocks.CHEST), 3);
            assertTrue(level.getBlockEntity(abs) instanceof Container,
                    "precondition: the chest has an inventory");
            ((Container) level.getBlockEntity(abs)).setItem(0, new ItemStack(Items.DIAMOND, 3));
            assertTrue(schem(Blocks.OAK_PRESSURE_PLATE.defaultBlockState())
                    .build(level, abs, EnumFluidHandlingMode.REPLACE),
                    "the pressure plate must be placed over the waterlogged chest");
            assertTrue(level.getBlockState(abs).is(Blocks.OAK_PRESSURE_PLATE), "the pressure plate must stand");
        }, Drops.near(chestAt, 1.5).exactly(Items.CHEST, 1).exactly(Items.DIAMOND, 3)
                .andCheck(() -> {
                    helper.setBlock(chestAt, Blocks.AIR);
                    helper.setBlock(chestAt.below(), Blocks.AIR);
                }));
    }

    /**
     * A failed placement over a block that has a block entity drops that block instead of putting it back: the
     * restore could only bring back a fresh, empty block entity, and whatever the old one held only in its drop (a
     * shulker box's contents, a named or configured block) would be gone. A torch with nothing under it, placed over
     * a waterlogged chest holding three diamonds, pops; the cell must be left with the chest's water, and the chest
     * and the diamonds must each drop once. Glass walls keep the water that is left, and the drops floating in it,
     * in the cell.
     */
    public static void testFailedPlacementOverWaterloggedChestDropsIt(GameTestHelper helper) {
        BlockPos chestAt = new BlockPos(2, 3, 2);
        DropArenaUtil.awaitDrops(helper, () -> {
            Level level = helper.getLevel();
            BlockPos abs = helper.absolutePos(chestAt);
            // Nothing under the torch, so it pops; a floor a block lower and walls keep the water and the drops in.
            level.setBlock(abs.below(), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(abs.below(2), Blocks.STONE.defaultBlockState(), 3);
            for (BlockPos wall : failedChestWalls(abs)) {
                level.setBlock(wall, Blocks.GLASS.defaultBlockState(), 3);
            }
            level.setBlock(abs, waterlogged(Blocks.CHEST), 3);
            assertTrue(level.getBlockEntity(abs) instanceof Container,
                    "precondition: the chest has an inventory");
            ((Container) level.getBlockEntity(abs)).setItem(0, new ItemStack(Items.DIAMOND, 3));
            assertTrue(!schem(Blocks.TORCH.defaultBlockState()).build(level, abs, EnumFluidHandlingMode.REPLACE),
                    "a torch with nothing under it must fail to place");
            BlockState after = level.getBlockState(abs);
            assertTrue(after.is(Blocks.WATER) && after.getFluidState().isSource(),
                    "the cell must keep the chest's water, not a fresh empty chest; found " + after);
        }, Drops.near(chestAt, 1.5).exactly(Items.CHEST, 1).exactly(Items.DIAMOND, 3)
                .andCheck(() -> {
                    Level level = helper.getLevel();
                    for (BlockPos wall : failedChestWalls(helper.absolutePos(chestAt))) {
                        level.setBlock(wall, Blocks.AIR.defaultBlockState(), 3);
                    }
                    helper.setBlock(chestAt, Blocks.AIR);
                    helper.setBlock(chestAt.below(), Blocks.AIR);
                    helper.setBlock(chestAt.below(2), Blocks.AIR);
                }));
    }

    /** The four sides of the chest's cell and of the cell below it. */
    private static List<BlockPos> failedChestWalls(BlockPos chest) {
        List<BlockPos> walls = new ArrayList<>();
        for (BlockPos cell : new BlockPos[] {chest, chest.below()}) {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                walls.add(cell.relative(side));
            }
        }
        return walls;
    }

    /**
     * The Builder in REPLACE mode breaks a waterlogged block the blueprint does not want, as it breaks any wrong
     * block, before placing over its water. The place path once took the cell as it stood (a cell holding any fluid
     * passed {@code canPlace}), so a waterlogged slab under a stone in the blueprint was overwritten with no break:
     * no power charged, and the slab lost instead of going into the Builder's resources. The slab must end up in the
     * resources (8 supplied, so 9), the stone must stand, and the Builder must rest.
     */
    public static void testReplaceBuilderBreaksWaterloggedBlockBeforePlacing(GameTestHelper helper) {
        int[] column = {STONE, STONE};
        BlockPos[] walls = {new BlockPos(1, 2, 2), new BlockPos(2, 2, 1)};
        TileBuilder tile = setUpBuilder(helper, column, walls);
        while (tile.getFluidMode() != EnumFluidHandlingMode.REPLACE) {
            tile.cycleFluidMode();
        }
        // The second cell is built already, which also walls the first one in to the south.
        helper.setBlock(cell(1), Blocks.STONE);
        helper.setBlock(cell(0), waterlogged(Blocks.OAK_SLAB));

        boolean[] finished = {false};
        drive(helper, finished, t -> {
            SnapshotBuilder<?> builder = tile.getBuilder();
            expect(builder != null, "the Builder lost its blueprint builder");
            BlockState first = helper.getBlockState(cell(0));
            if (first.is(Blocks.STONE) && builderQuiet(builder)) {
                int slabs = 0;
                for (int slot = 0; slot < tile.invResources.getSlots(); slot++) {
                    ItemStack held = tile.invResources.getStackInSlot(slot);
                    if (held.is(Items.OAK_SLAB)) {
                        slabs += held.getCount();
                    }
                }
                finished[0] = true;
                expect(slabs == 9, "the waterlogged slab the stone replaced must go into the Builder's resources "
                        + "(8 supplied, so 9), found " + slabs);
                helper.succeed();
            } else {
                expect(t < 400, "the REPLACE Builder never placed the stone over the waterlogged slab and rested; "
                        + "cell=" + first + ", leftToBreak=" + builder.leftToBreak + ", leftToPlace="
                        + builder.leftToPlace + ", breakTasks=" + builder.breakTasks.size() + ", placeTasks="
                        + builder.placeTasks.size());
            }
        });
    }

    /**
     * Fills {@code sourceCells} (arena-relative) with source {@code fluid}, builds {@code wanted} at {@code placeAt}
     * over an empty cell below it, and asserts the build fails and every cell still holds that source. Resets the
     * cells afterwards so the next case starts clean.
     */
    private static void assertFailedPlaceKeepsSource(GameTestHelper helper, String what, EnumFluidHandlingMode mode,
            BlockState wanted, Block fluid, BlockPos placeAt, BlockPos... sourceCells) {
        Level level = helper.getLevel();
        BlockPos abs = helper.absolutePos(placeAt);
        level.setBlock(abs.below(), Blocks.AIR.defaultBlockState(), 3);
        for (BlockPos cell : sourceCells) {
            level.setBlock(helper.absolutePos(cell), fluid.defaultBlockState(), 3);
        }
        boolean placed = schem(wanted).build(level, abs, mode);
        try {
            assertTrue(!placed, what + " has nothing to stand on, so the " + mode + " placement must fail");
            for (BlockPos cell : sourceCells) {
                FluidState after = level.getFluidState(helper.absolutePos(cell));
                assertTrue(after.isSource() && after.getType() == fluid.defaultBlockState().getFluidState().getType(),
                        what + ": the source at " + cell + " must survive a failed " + mode + " placement, got "
                                + level.getBlockState(helper.absolutePos(cell)));
            }
        } finally {
            level.setBlock(abs, Blocks.AIR.defaultBlockState(), 3);
            for (BlockPos cell : sourceCells) {
                level.setBlock(helper.absolutePos(cell), Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    /**
     * A Builder in NO_REPLACE defers a fragile block beside water like the other modes: placed, the water would wash
     * it away (its item dropping), and the flooded cell then counts as built in NO_REPLACE, so it would never be
     * placed again. The schematic item placed by hand ({@code build(Level, BlockPos)}) keeps no defer: a player's
     * snow layer goes down beside water as a player's would.
     */
    public static void testNoReplaceModeDefersFragileButHandPlacementDoesNot(GameTestHelper helper) {
        try {
            BlockPos local = new BlockPos(2, 2, 2);
            BlockPos abs = helper.absolutePos(local);
            helper.getLevel().setBlock(abs.below(), Blocks.STONE.defaultBlockState(), 3);
            // Position is air (no fluid). Adjacent water is present.
            helper.getLevel().setBlock(abs.east(), Blocks.WATER.defaultBlockState(), 3);

            SchematicBlockDefault s = schem(Blocks.SNOW.defaultBlockState());
            boolean placed = s.build(helper.getLevel(), abs, EnumFluidHandlingMode.NO_REPLACE);
            assertTrue(!placed, "a NO_REPLACE Builder placement must defer a fragile block beside water");
            assertTrue(helper.getLevel().getBlockState(abs).isAir(),
                    "the deferred NO_REPLACE placement must leave the cell untouched");

            boolean placedByHand = s.build(helper.getLevel(), abs);
            assertTrue(placedByHand, "a placement by hand must not run the fragile defer");
            BlockState atPos = helper.getLevel().getBlockState(abs);
            assertTrue(atPos.getBlock() == Blocks.SNOW, "snow placed by hand should stand despite the adjacent water");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }
}
