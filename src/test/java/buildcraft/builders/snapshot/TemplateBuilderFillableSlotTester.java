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

/**
 * Pins {@link TemplateBuilder#isFillableSlot(net.minecraft.world.level.Level, BlockPos)} —
 * the helper that decides whether a Filler/Builder template position is considered "empty
 * enough to fill" by the place-task path.
 * <p>
 * The user-visible bug was a Filler with Excavate enabled leaving tall_grass tufts standing
 * inside a filled box. Before the fix, {@code isBlockCorrect} returned {@code true} for any
 * non-air block (including replaceable plants), so the check classified grass-tuft positions
 * as CORRECT and never queued a break OR place — and excavate doesn't help once a position is
 * already labelled CORRECT. The fix routes replaceable, non-fluid blocks through the place
 * path so vanilla {@code stack.useOn} overwrites them naturally.
 * <p>
 * Fluids (water/lava sources, flowing fluid) are deliberately excluded from the helper so the
 * existing fluid-mode logic continues to own that decision — NO_REPLACE Fillers must keep
 * leaving water sources alone.
 */
public class TemplateBuilderFillableSlotTester {

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) throw new IllegalStateException(msg);
    }

    private static void assertFalse(boolean cond, String msg) {
        if (cond) throw new IllegalStateException("Expected false: " + msg);
    }

    /**
     * Setting a block at a helper-relative position, then asserting the fillable-slot helper
     * against the absolute world coords. Wraps the boilerplate of every case below.
     */
    private static boolean fillableAt(GameTestHelper helper, BlockPos local, BlockState state) {
        BlockPos abs = helper.absolutePos(local);
        helper.getLevel().setBlock(abs, state, 3);
        return TemplateBuilder.isFillableSlot(helper.getLevel(), abs);
    }

    /** Air is the trivial baseline — filling over air must always work. */
    public static void testAirIsFillable(GameTestHelper helper) {
        try {
            BlockPos local = new BlockPos(1, 2, 1);
            assertTrue(fillableAt(helper, local, Blocks.AIR.defaultBlockState()),
                    "air must be fillable");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /**
     * The user-reported case — tall_grass tufts (and the short grass block) sit on the
     * canBeReplaced side of vanilla's placement check, so the Filler should treat them as
     * fillable. Before the fix this returned false (grass was classified as CORRECT and the
     * box was never finished cleanly).
     */
    public static void testTallGrassIsFillable(GameTestHelper helper) {
        try {
            BlockPos local = new BlockPos(1, 2, 1);
            assertTrue(fillableAt(helper, local, Blocks.TALL_GRASS.defaultBlockState()),
                    "tall_grass must be fillable — vanilla canBeReplaced=true");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    public static void testShortGrassIsFillable(GameTestHelper helper) {
        try {
            BlockPos local = new BlockPos(1, 2, 1);
            // Vanilla's single-block "grass tuft" is the SHORT_GRASS block (was named "GRASS"
            // pre-1.20.3). The user's screenshot showed exactly these tufts under the Filler.
            assertTrue(fillableAt(helper, local, Blocks.SHORT_GRASS.defaultBlockState()),
                    "short_grass must be fillable");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /** Single-layer snow is replaceable per vanilla; multi-layer is also canBeReplaced=true
     *  (the no-arg flag), so the Filler treats either as fillable. Snow at the build site is
     *  one of the common cases the old behaviour mishandled. */
    public static void testSnowLayerIsFillable(GameTestHelper helper) {
        try {
            BlockPos local = new BlockPos(1, 2, 1);
            assertTrue(fillableAt(helper, local, Blocks.SNOW.defaultBlockState()),
                    "snow_layer must be fillable");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /**
     * Water sources are deliberately excluded from {@code isFillableSlot} — the existing
     * fluid-mode logic owns "what to do with fluids" (NO_REPLACE leaves them, CLEAR mops,
     * REPLACE waterlogs). Without this guard a NO_REPLACE Filler would start overwriting water
     * sources as plain blocks, which would silently regress fluid-mode behaviour.
     */
    public static void testWaterSourceIsNotFillable(GameTestHelper helper) {
        try {
            BlockPos local = new BlockPos(1, 2, 1);
            assertFalse(fillableAt(helper, local, Blocks.WATER.defaultBlockState()),
                    "water source must NOT be fillable — fluid mode owns that decision");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    public static void testLavaSourceIsNotFillable(GameTestHelper helper) {
        try {
            BlockPos local = new BlockPos(1, 2, 1);
            assertFalse(fillableAt(helper, local, Blocks.LAVA.defaultBlockState()),
                    "lava source must NOT be fillable — fluid mode owns that decision");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /** Solid existing blocks must stay CORRECT — the Filler must NOT start tearing up the
     *  player's existing build to swap in the inventory block. Stone is the canonical case. */
    public static void testSolidBlockIsNotFillable(GameTestHelper helper) {
        try {
            BlockPos local = new BlockPos(1, 2, 1);
            assertFalse(fillableAt(helper, local, Blocks.STONE.defaultBlockState()),
                    "stone must NOT be fillable");
            assertFalse(fillableAt(helper, local, Blocks.COBBLESTONE.defaultBlockState()),
                    "cobblestone must NOT be fillable");
            assertFalse(fillableAt(helper, local, Blocks.DIRT.defaultBlockState()),
                    "dirt must NOT be fillable");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /**
     * Waterlogged fence is a tricky case — the FENCE block itself is solid and not
     * canBeReplaced=true, so it should fall on the not-fillable side even though it holds
     * water. (If the fence were treated as fillable just because of the water, the Filler
     * would destroy the fence to set a plain block there — the opposite of the user's intent
     * when they built a fenced area inside the Filler box.)
     */
    public static void testWaterloggedFenceIsNotFillable(GameTestHelper helper) {
        try {
            BlockPos local = new BlockPos(1, 2, 1);
            BlockState waterloggedFence = Blocks.OAK_FENCE.defaultBlockState()
                    .setValue(BlockStateProperties.WATERLOGGED, true);
            assertFalse(fillableAt(helper, local, waterloggedFence),
                    "waterlogged fence must NOT be fillable — the fence itself is solid");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }
}
