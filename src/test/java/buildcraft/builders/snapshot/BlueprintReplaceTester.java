/*
 * Copyright (c) 2026 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.snapshot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Game tests for {@link Blueprint#replace} and its count helpers.
 * <p>
 * Registered via {@link buildcraft.BuildCraftGameTests}. Runs in a loaded game environment
 * because plain unit tests can't statically initialize {@link Blocks} — vanilla's feature-flag
 * loader fails with "There is no current FML Loader" when invoked outside a mod environment.
 * <p>
 * The 1.12.2 {@link Blueprint#replace} used {@code Collections.replaceAll}, which relied on
 * whole-object {@code .equals()}. Freshly-captured single-block schematics carry scan-context
 * annotations that palette entries from Architect scans don't share, so matches virtually never
 * succeeded — the symptom the user reported as "schematics consumed, blueprint unchanged".
 * These tests lock in the "match by blockState+placeBlock" semantic of the 26.1 port.
 */
public class BlueprintReplaceTester {

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) throw new IllegalStateException(msg);
    }

    /** Minimal SchematicBlockDefault wrapping a blockState/placeBlock pair. Same-package protected-field access. */
    private static SchematicBlockDefault schem(BlockState state) {
        SchematicBlockDefault s = new SchematicBlockDefault();
        s.blockState = state;
        s.placeBlock = state.getBlock();
        return s;
    }

    /**
     * Same as {@link #schem} but populates {@code requiredBlockOffsets} so the resulting
     * instance is NOT {@code .equals()} to a bare {@link #schem} copy — simulating an Architect
     * palette entry vs. a hand-captured single-block schematic.
     */
    private static SchematicBlockDefault schemWithContext(BlockState state) {
        SchematicBlockDefault s = schem(state);
        s.requiredBlockOffsets.add(new BlockPos(0, -1, 0));
        return s;
    }

    private static Blueprint makeBlueprint(BlockPos size, int[] data, SchematicBlockDefault... palette) {
        Blueprint bp = new Blueprint();
        bp.size = size;
        bp.offset = BlockPos.ZERO;
        bp.facing = Direction.NORTH;
        bp.data = data;
        for (SchematicBlockDefault s : palette) {
            bp.palette.add(s);
        }
        return bp;
    }

    /**
     * Regression test for the 1.12.2 bug: scan-context-different schematics that share the same
     * blockState must still be considered a match by {@link Blueprint#replace}. If this fails,
     * it means {@code Collections.replaceAll} crept back in.
     */
    public static void testScanContextDifferenceDoesNotBlockMatch(GameTestHelper helper) {
        try {
            SchematicBlockDefault oakInPalette = schemWithContext(Blocks.OAK_LOG.defaultBlockState());
            SchematicBlockDefault oakFromItem = schem(Blocks.OAK_LOG.defaultBlockState());
            SchematicBlockDefault stone = schem(Blocks.STONE.defaultBlockState());

            assertTrue(!oakInPalette.equals(oakFromItem),
                    "precondition: context-different schematics should not be .equals()");

            Blueprint bp = makeBlueprint(new BlockPos(2, 1, 1), new int[] { 0, 0 }, oakInPalette);
            bp.replace(oakFromItem, stone);

            assertTrue(bp.palette.get(0) == stone,
                    "palette[0] should now be the stone schematic");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /** The palette-entry swap should not require {@code data[]} to be rewritten. */
    public static void testReplaceLeavesDataArrayUntouched(GameTestHelper helper) {
        try {
            SchematicBlockDefault oak = schem(Blocks.OAK_LOG.defaultBlockState());
            SchematicBlockDefault glass = schem(Blocks.GLASS.defaultBlockState());
            int[] data = new int[] { 0, 0, 0 };
            Blueprint bp = makeBlueprint(new BlockPos(3, 1, 1), data, oak);

            bp.replace(schem(Blocks.OAK_LOG.defaultBlockState()), glass);

            assertTrue(bp.data.length == 3 && bp.data[0] == 0 && bp.data[1] == 0 && bp.data[2] == 0,
                    "data[] must be untouched after an in-place palette swap");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /** Multiple palette entries matching {@code from} should all be swapped — not just the first. */
    public static void testMultipleOccurrencesInPaletteAllReplaced(GameTestHelper helper) {
        try {
            SchematicBlockDefault oakA = schem(Blocks.OAK_LOG.defaultBlockState());
            SchematicBlockDefault oakB = schemWithContext(Blocks.OAK_LOG.defaultBlockState());
            SchematicBlockDefault stone = schem(Blocks.STONE.defaultBlockState());
            SchematicBlockDefault glass = schem(Blocks.GLASS.defaultBlockState());

            Blueprint bp = makeBlueprint(new BlockPos(3, 1, 1), new int[] { 0, 1, 2 }, oakA, stone, oakB);
            bp.replace(schem(Blocks.OAK_LOG.defaultBlockState()), glass);

            assertTrue(bp.palette.get(0) == glass, "palette[0] oak → glass");
            assertTrue(bp.palette.get(1) == stone, "palette[1] stone left alone");
            assertTrue(bp.palette.get(2) == glass, "palette[2] oak → glass");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /** A {@code from} with no palette matches must leave the blueprint untouched. */
    public static void testNoMatchLeavesPaletteUnchanged(GameTestHelper helper) {
        try {
            SchematicBlockDefault oak = schem(Blocks.OAK_LOG.defaultBlockState());
            SchematicBlockDefault stoneReplacement = schem(Blocks.STONE.defaultBlockState());
            Blueprint bp = makeBlueprint(new BlockPos(1, 1, 1), new int[] { 0 }, oak);

            bp.replace(schem(Blocks.BIRCH_LOG.defaultBlockState()), stoneReplacement);

            assertTrue(bp.palette.size() == 1 && bp.palette.get(0) == oak,
                    "palette should be unchanged when from-schematic has no matches");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /**
     * {@code countMatchingCells} should count data[] cells, not palette entries, so a palette
     * with duplicate entries surfaces the correct block total for the summary GUI text.
     */
    public static void testCountMatchingCellsCountsBlocks(GameTestHelper helper) {
        try {
            SchematicBlockDefault oak1 = schem(Blocks.OAK_LOG.defaultBlockState());
            SchematicBlockDefault oak2 = schemWithContext(Blocks.OAK_LOG.defaultBlockState());
            SchematicBlockDefault stone = schem(Blocks.STONE.defaultBlockState());
            Blueprint bp = makeBlueprint(new BlockPos(4, 1, 1), new int[] { 0, 2, 1, 2 }, oak1, stone, oak2);

            int matched = bp.countMatchingCells(schem(Blocks.OAK_LOG.defaultBlockState()));
            assertTrue(matched == 3, "expected 3 matching cells (0→oak, 2→oak, 2→oak), got " + matched);

            int paletteHits = bp.countMatchingPaletteEntries(schem(Blocks.OAK_LOG.defaultBlockState()));
            assertTrue(paletteHits == 2, "expected 2 matching palette entries, got " + paletteHits);

            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /** Defensive: passing null should no-op, not throw. */
    public static void testReplaceNullIsNoOp(GameTestHelper helper) {
        try {
            SchematicBlockDefault oak = schem(Blocks.OAK_LOG.defaultBlockState());
            Blueprint bp = makeBlueprint(new BlockPos(1, 1, 1), new int[] { 0 }, oak);

            bp.replace(null, oak);
            bp.replace(oak, null);
            assertTrue(bp.palette.get(0) == oak, "palette survives null replace calls");

            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }
}
