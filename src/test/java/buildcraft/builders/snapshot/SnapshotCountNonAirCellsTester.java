/*
 * Copyright (c) 2026 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.snapshot;

import java.util.BitSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

import buildcraft.api.schematics.ISchematicBlock;

/**
 * Pins {@link Snapshot#countNonAirCells()} for both subclasses — the count drives the
 * {@code start_of_something_big} advancement threshold. The user spec was "1024 blocks or
 * more (fluid sources count, air doesn't)", so the three things that matter are:
 *   (1) Template counts every set bit (the snapshot doesn't know what gets placed; every
 *       fillable cell is a stamp the Builder will perform).
 *   (2) Blueprint excludes {@link SchematicBlockAir} palette entries but includes everything
 *       else.
 *   (3) Fluid sources route through {@link SchematicBlockFluid}, which does NOT override
 *       {@code isAir()}, so they fall into the "everything else" bucket and count.
 * <p>
 * Game test rather than plain JUnit because the Blueprint cases construct schematics whose
 * underlying BlockStates need vanilla {@link Blocks} statically initialized — unavailable
 * without the game environment, same constraint as {@link BlueprintReplaceTester}.
 */
public class SnapshotCountNonAirCellsTester {

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) throw new IllegalStateException(msg);
    }

    /** Template counts every set bit. 2×2×2 = 8 cells; we mark 3 → expect 3. */
    public static void testTemplateCountsSetBits(GameTestHelper helper) {
        Template t = new Template();
        t.size = new BlockPos(2, 2, 2);
        t.facing = Direction.NORTH;
        t.offset = BlockPos.ZERO;
        t.data = new BitSet(8);
        t.data.set(0);
        t.data.set(3);
        t.data.set(7);
        assertTrue(t.countNonAirCells() == 3,
                "Template with 3 set bits must report 3 non-air cells — got " + t.countNonAirCells());

        // Null-data is fresh-template safety. Should report 0, not crash.
        Template empty = new Template();
        empty.size = new BlockPos(2, 2, 2);
        empty.facing = Direction.NORTH;
        empty.offset = BlockPos.ZERO;
        assertTrue(empty.countNonAirCells() == 0,
                "Template with null data must report 0 non-air cells");
        helper.succeed();
    }

    /**
     * Blueprint counts cells whose palette entry isn't {@link SchematicBlockAir}. 3-cell
     * blueprint, palette [stone, air, dirt], data [0, 1, 2] → 2 non-air (stone + dirt).
     * Pins both the air-exclusion semantic AND the palette-index walk.
     */
    public static void testBlueprintExcludesAirEntries(GameTestHelper helper) {
        Blueprint bp = new Blueprint();
        bp.size = new BlockPos(3, 1, 1);
        bp.facing = Direction.NORTH;
        bp.offset = BlockPos.ZERO;
        bp.data = new int[] { 0, 1, 2 };
        bp.palette.add(schem(Blocks.STONE.defaultBlockState()));
        bp.palette.add(new SchematicBlockAir());
        bp.palette.add(schem(Blocks.DIRT.defaultBlockState()));

        assertTrue(bp.countNonAirCells() == 2,
                "Blueprint with 1 air + 2 non-air cells must report 2 — got " + bp.countNonAirCells());
        helper.succeed();
    }

    /**
     * Fluid sources count as non-air. Pinned because the user explicitly called out
     * "fluid sources count" in the spec — easy to accidentally exclude them by treating
     * "non-solid" as "air-like", which would silently let huge fluid-only structures
     * stop short of the 1024 threshold.
     */
    public static void testBlueprintFluidSourceCountsAsNonAir(GameTestHelper helper) {
        Blueprint bp = new Blueprint();
        bp.size = new BlockPos(2, 1, 1);
        bp.facing = Direction.NORTH;
        bp.offset = BlockPos.ZERO;
        bp.data = new int[] { 0, 1 };
        // A water source is a SchematicBlockFluid, which inherits the default ISchematicBlock.isAir = false.
        ISchematicBlock waterSource = SchematicBlockManager.getSchematicBlock(
                new buildcraft.api.schematics.SchematicBlockContext(
                        helper.getLevel(),
                        helper.absolutePos(BlockPos.ZERO),
                        helper.absolutePos(BlockPos.ZERO),
                        Blocks.WATER.defaultBlockState(),
                        Blocks.WATER));
        bp.palette.add(new SchematicBlockAir());
        bp.palette.add(waterSource);

        assertTrue(bp.countNonAirCells() == 1,
                "Blueprint with [air, water_source] cells must report 1 non-air (the water source) — got "
                        + bp.countNonAirCells());
        helper.succeed();
    }

    /** Null/empty defences: an empty palette or null data array short-circuits to 0. */
    public static void testBlueprintEmptyAndNullSafety(GameTestHelper helper) {
        Blueprint empty = new Blueprint();
        empty.size = new BlockPos(1, 1, 1);
        empty.facing = Direction.NORTH;
        empty.offset = BlockPos.ZERO;
        // data null, palette empty
        assertTrue(empty.countNonAirCells() == 0,
                "Blueprint with null data must report 0 non-air cells");

        Blueprint emptyPalette = new Blueprint();
        emptyPalette.size = new BlockPos(1, 1, 1);
        emptyPalette.facing = Direction.NORTH;
        emptyPalette.offset = BlockPos.ZERO;
        emptyPalette.data = new int[] { 0 };
        assertTrue(emptyPalette.countNonAirCells() == 0,
                "Blueprint with empty palette must report 0 non-air cells");
        helper.succeed();
    }

    private static SchematicBlockDefault schem(net.minecraft.world.level.block.state.BlockState state) {
        SchematicBlockDefault s = new SchematicBlockDefault();
        s.blockState = state;
        s.placeBlock = state.getBlock();
        return s;
    }
}
