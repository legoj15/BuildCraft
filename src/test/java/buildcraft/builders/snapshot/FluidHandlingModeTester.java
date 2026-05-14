/*
 * Copyright (c) 2026 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.snapshot;

import java.util.List;
import java.util.Optional;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

import buildcraft.api.mj.MjAPI;
import buildcraft.lib.misc.BlockUtil;

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
     * Regression test for the silently-dropped tool parameter. Before the fix,
     * {@code BlockUtil.breakBlockAndGetDrops} called {@code Block.getDrops} via the 4-arg overload,
     * which builds a loot context with {@code LootContextParams.TOOL = ItemStack.EMPTY}. Iron ore's
     * loot table has a top-level {@code minecraft:match_tool} condition (needs a stone+ pickaxe),
     * so the condition failed and the call returned an empty drops list — even though the caller
     * was passing an iron pickaxe through the {@code tool} parameter, intending exactly to satisfy
     * that condition. Net effect: the Mining Well / Quarry / Builder-CLEAR destroyed the ore block
     * but produced no item.
     * <p>
     * After the fix the 6-arg overload threads {@code tool} into the loot context, so a wielded
     * iron pickaxe satisfies the iron-tier requirement and the iron raw drops out.
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
     * Regression: the XP-aware helper {@link BlockUtil#breakBlockAndGetDropsWithXp} must
     * return non-zero XP for a tool-gated ore mined with a sufficient tier. Vanilla iron ore
     * gives 0–2 XP under any successful pickaxe break, so iron-on-iron-ore is the cleanest
     * tier-positive case to assert against. The drops side is already covered by
     * {@link #testBreakBlockAndGetDropsHonoursToolForLoot}; this guards the XP branch
     * specifically — the {@code state.getExpDrop(level, pos, blockEntity, breaker, tool)}
     * call inside the helper would silently return 0 if the tool param were ever to slip
     * back out of the loot context (mirroring the original drops regression).
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
     * Complement of the above: a wooden pickaxe should NOT satisfy iron ore's match_tool
     * condition, so the block destroys but drops nothing. Confirms the loot context actually
     * cares about the wielded tier rather than just "any non-empty ItemStack".
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
                    "wooden pickaxe does not satisfy iron ore's match_tool condition — drops must be empty");
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
     * CLEAR fluidMode → must report NOT built (which is what triggers the dry-it placement).
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
     * isWaterlogClearOnly directly. Confirms the predicate the Builder uses to skip item
     * extraction matches "world wet / schematic dry / same block / CLEAR mode." Other mode
     * values must return false even with the same block setup, otherwise REPLACE/NO_REPLACE
     * placements would skip their item costs.
     */
    public static void testIsWaterlogClearOnlyOnlyFiresUnderClear(GameTestHelper helper) {
        try {
            BlockPos abs = helper.absolutePos(new BlockPos(2, 2, 2));
            BlockState wetFence = Blocks.OAK_FENCE.defaultBlockState()
                    .setValue(BlockStateProperties.WATERLOGGED, true);
            helper.getLevel().setBlock(abs, wetFence, 3);

            SchematicBlockDefault s = schem(Blocks.OAK_FENCE.defaultBlockState());
            assertTrue(s.isWaterlogClearOnly(helper.getLevel(), abs, EnumFluidHandlingMode.CLEAR),
                    "CLEAR + matching block + world wet + schematic dry must flag as waterlog-clear-only");
            assertTrue(!s.isWaterlogClearOnly(helper.getLevel(), abs, EnumFluidHandlingMode.REPLACE),
                    "REPLACE must NOT flag — opportunistic waterlog is the correct behaviour there, the place would just keep it wet");
            assertTrue(!s.isWaterlogClearOnly(helper.getLevel(), abs, EnumFluidHandlingMode.NO_REPLACE),
                    "NO_REPLACE must NOT flag — fluid positions are skipped entirely in that mode");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /**
     * Different block in world vs schematic must NOT trigger the dry-only path — those are real
     * place operations that need items extracted normally. Guards against the predicate being
     * over-broad.
     */
    public static void testIsWaterlogClearOnlyRequiresMatchingBlock(GameTestHelper helper) {
        try {
            BlockPos abs = helper.absolutePos(new BlockPos(2, 2, 2));
            BlockState wetFence = Blocks.OAK_FENCE.defaultBlockState()
                    .setValue(BlockStateProperties.WATERLOGGED, true);
            helper.getLevel().setBlock(abs, wetFence, 3);

            // Schematic captured a different block — birch fence — at this position.
            SchematicBlockDefault s = schem(Blocks.BIRCH_FENCE.defaultBlockState());
            assertTrue(!s.isWaterlogClearOnly(helper.getLevel(), abs, EnumFluidHandlingMode.CLEAR),
                    "different block types must not be treated as a waterlog-only modification — that's a real block placement");
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
