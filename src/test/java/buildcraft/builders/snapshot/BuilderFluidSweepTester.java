/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.builders.snapshot;

import static buildcraft.builders.BuilderRefloodTester.AIR;
import static buildcraft.builders.BuilderRefloodTester.BUILDER;
import static buildcraft.builders.BuilderRefloodTester.QUIET_TICKS;
import static buildcraft.builders.BuilderRefloodTester.SLAB;
import static buildcraft.builders.BuilderRefloodTester.SNOW;
import static buildcraft.builders.BuilderRefloodTester.STONE;
import static buildcraft.builders.BuilderRefloodTester.builderQuiet;
import static buildcraft.builders.BuilderRefloodTester.cell;
import static buildcraft.builders.BuilderRefloodTester.drive;
import static buildcraft.builders.BuilderRefloodTester.expect;
import static buildcraft.builders.BuilderRefloodTester.isBuilt;
import static buildcraft.builders.BuilderRefloodTester.isWetSlab;
import static buildcraft.builders.BuilderRefloodTester.setUpBuilder;

import java.util.Arrays;
import java.util.Date;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;

import buildcraft.api.schematics.ISchematicBlock;
import buildcraft.api.schematics.SchematicBlockContext;
import buildcraft.builders.BCBuildersBlocks;
import buildcraft.builders.BCBuildersItems;
import buildcraft.builders.tile.TileBuilder;
import buildcraft.lib.block.BlockWaterloggingTester;
import buildcraft.lib.test.ThreadAllocationMeter;

/**
 * The cost of {@link SnapshotBuilder#scanAreaFluid()}, the whole-box fluid sweep behind the CLEAR/REPLACE place-task
 * gate. The sweep once ran on every tick for the lifetime of the build: it listed the box into one {@link BlockPos}
 * per cell, and (once the area held nothing but tolerated water, so there was no uncleared fluid to stop it early)
 * walked the entire box each time, re-deriving the tolerance of every wet air cell. Nothing needs the answer while
 * there is nothing to place, and a stuck placement needs it only about as often as water can move.
 */
public class BuilderFluidSweepTester {

    /** Ticks each watch window runs. Long enough to span many {@link SnapshotBuilder#FLUID_SCAN_INTERVAL}s. */
    private static final int WATCH_TICKS = 60;

    /** The pond: a dry column of slab, stone, stone and a snow layer, with two water sources outside the box
     *  flanking the slab, which therefore ends up waterlogged for good (tolerated water). */
    private static final int[] POND_COLUMN = { SLAB, STONE, STONE, SNOW };
    private static final BlockPos[] POND_WALLS = {
        new BlockPos(0, 2, 2), new BlockPos(1, 2, 1), new BlockPos(1, 2, 3), new BlockPos(2, 2, 0),
        new BlockPos(3, 2, 1) };
    private static final BlockPos[] POND_SOURCES = { new BlockPos(1, 2, 2), new BlockPos(2, 2, 1) };
    /** The snow layer's cell, and the floor under it. */
    private static final BlockPos SNOW_CELL = cell(3);
    private static final BlockPos SNOW_FLOOR = SNOW_CELL.below();

    private static String describe(SnapshotBuilder<?> builder) {
        return builder == null ? "no builder" : "leftToPlace=" + builder.leftToPlace + ", leftToBreak="
                + builder.leftToBreak + ", placeTasks=" + builder.placeTasks.size() + ", breakTasks="
                + builder.breakTasks.size();
    }

    /**
     * Phases, all gated on observed state (fluid ticks and chunk promotion lag by a variable number of ticks):
     * <ol start="0">
     * <li>build the dry column, then add the outside water sources;</li>
     * <li>wait for the slab to flood and the Builder to rest for {@link BuilderRefloodTester#QUIET_TICKS} straight
     *     ticks with the wet slab standing: the build is done, and tolerated water stays in the box;</li>
     * <li>watch {@link #WATCH_TICKS} ticks of that idle Builder: it has nothing to place, so no sweep may run;</li>
     * <li>knock the snow layer out AND the floor under it, so it can never be placed again (nothing for it to stand
     *     on) but stays a placement candidate, and wait for the Builder to notice;</li>
     * <li>watch {@link #WATCH_TICKS} ticks of that stuck Builder: the sweep may run, but only about once per
     *     {@link SnapshotBuilder#FLUID_SCAN_INTERVAL} ticks, not every tick;</li>
     * <li>right after a sweep, change the fluid mode: the next sweep must follow at once, not an interval later.</li>
     * </ol>
     */
    public static void toleratedWaterDoesNotMakeTheBuilderSweepEveryTick(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TileBuilder tile = setUpBuilder(helper, POND_COLUMN, POND_WALLS);
        int maxStuckSweeps = (WATCH_TICKS + SnapshotBuilder.FLUID_SCAN_INTERVAL - 1)
                / SnapshotBuilder.FLUID_SCAN_INTERVAL + 1;

        int[] phase = { 0 };
        int[] phaseStart = { 0 };
        int[] quiet = { 0 };
        int[] sweepsAtStart = { 0 };
        boolean[] finished = { false };

        drive(helper, finished, t -> {
            SnapshotBuilder<?> builder = tile.getBuilder();
            expect(builder != null, "the Builder lost its blueprint builder");
            BlockState[] cells = new BlockState[POND_COLUMN.length];
            boolean allBuilt = true;
            for (int i = 0; i < cells.length; i++) {
                cells[i] = helper.getBlockState(cell(i));
                allBuilt &= isBuilt(POND_COLUMN[i], cells[i]);
            }
            int inPhase = t - phaseStart[0];
            switch (phase[0]) {
                case 0 -> {
                    if (allBuilt && builderQuiet(builder)) {
                        for (BlockPos source : POND_SOURCES) {
                            BlockWaterloggingTester.placeSource(helper, source, Blocks.WATER, Fluids.WATER);
                        }
                        phase[0] = 1;
                        phaseStart[0] = t;
                    } else {
                        expect(inPhase < 300, "phase 0: the Builder never finished the dry build, cells="
                                + Arrays.toString(cells) + ", " + describe(builder));
                    }
                }
                case 1 -> {
                    boolean settled = !level.getFluidState(helper.absolutePos(cell(0))).isEmpty()
                            && isWetSlab(cells[0]) && builderQuiet(builder);
                    quiet[0] = settled ? quiet[0] + 1 : 0;
                    if (quiet[0] >= QUIET_TICKS) {
                        phase[0] = 2;
                        phaseStart[0] = t;
                        sweepsAtStart[0] = builder.getFluidSweepCount();
                    } else {
                        expect(inPhase < 600, "phase 1 (test premise): the Builder never rested with the slab "
                                + "waterlogged by outside water, cells=" + Arrays.toString(cells) + ", "
                                + describe(builder));
                    }
                }
                case 2 -> {
                    expect(isWetSlab(cells[0]) && builderQuiet(builder), "phase 2 (test premise): the idle Builder "
                            + "stopped resting with tolerated water standing, cells=" + Arrays.toString(cells)
                            + ", " + describe(builder));
                    if (inPhase >= WATCH_TICKS) {
                        int sweeps = builder.getFluidSweepCount() - sweepsAtStart[0];
                        expect(sweeps == 0, "a Builder with nothing to place swept the whole build box for fluid "
                                + sweeps + " times in " + WATCH_TICKS + " ticks (tolerated water stands in the box)");
                        helper.setBlock(SNOW_CELL, Blocks.AIR);
                        helper.setBlock(SNOW_FLOOR, Blocks.AIR);
                        phase[0] = 3;
                        phaseStart[0] = t;
                    }
                }
                case 3 -> {
                    if (builder.leftToPlace > 0 && builder.placeTasks.isEmpty()) {
                        phase[0] = 4;
                        phaseStart[0] = t;
                        sweepsAtStart[0] = builder.getFluidSweepCount();
                    } else {
                        expect(inPhase < 100, "phase 3 (test premise): the Builder never saw the snow layer as a "
                                + "waiting placement, cells=" + Arrays.toString(cells) + ", " + describe(builder));
                    }
                }
                case 4 -> {
                    expect(builder.leftToPlace > 0 && !cells[3].is(Blocks.SNOW) && isWetSlab(cells[0]),
                            "phase 4 (test premise): the snow layer was placed or the pond dried, cells="
                                    + Arrays.toString(cells) + ", " + describe(builder));
                    if (inPhase >= WATCH_TICKS) {
                        int sweeps = builder.getFluidSweepCount() - sweepsAtStart[0];
                        expect(sweeps >= 1, "phase 4 (test premise): the waiting placement never reached the fluid "
                                + "gate, so this proves nothing");
                        expect(sweeps <= maxStuckSweeps, "a Builder with a stuck placement swept the whole build box "
                                + "for fluid " + sweeps + " times in " + WATCH_TICKS + " ticks (at most "
                                + maxStuckSweeps + " expected)");
                        phase[0] = 5;
                        phaseStart[0] = t;
                        sweepsAtStart[0] = builder.getFluidSweepCount();
                    }
                }
                case 5 -> {
                    // Wait for a sweep to just have happened, so the next one is a full interval away unless
                    // something discards the result.
                    if (builder.getFluidSweepCount() != sweepsAtStart[0]) {
                        sweepsAtStart[0] = builder.getFluidSweepCount();
                        tile.cycleFluidMode(); // CLEAR -> NO_REPLACE
                        tile.cycleFluidMode(); // NO_REPLACE -> REPLACE
                        expect(tile.getFluidMode() == EnumFluidHandlingMode.REPLACE, "sanity: now in REPLACE mode");
                        phase[0] = 6;
                        phaseStart[0] = t;
                    } else {
                        expect(inPhase < 20, "phase 5 (test premise): the stuck placement stopped sweeping, "
                                + describe(builder));
                    }
                }
                case 6 -> {
                    // The sweep is a full interval away; a changed fluid mode (tolerance, and so the answer,
                    // differs per mode) must not be answered from the previous mode's result.
                    if (inPhase >= 2) {
                        expect(builder.getFluidSweepCount() > sweepsAtStart[0], "a change of fluid mode was "
                                + "answered from the previous mode's sweep result; " + describe(builder));
                        finished[0] = true;
                        helper.succeed();
                    }
                }
                default -> throw new IllegalStateException("bad phase " + phase[0]);
            }
        });
    }

    /** The race column: a water source goes in the schematic-air cell at the Builder's end, and two more air cells
     *  carry its flow to the snow layer at the far end, so the water reaches the snow a few flow steps after it is
     *  placed. */
    private static final int[] RACE_COLUMN = { AIR, AIR, AIR, SNOW };
    /** Walls along both sides and both ends of the race column, so the water stays in it (the Builder closes the
     *  east side of the first cell). */
    private static final BlockPos[] RACE_WALLS = {
        new BlockPos(2, 2, 1), new BlockPos(2, 2, 6),
        new BlockPos(1, 2, 2), new BlockPos(1, 2, 3), new BlockPos(1, 2, 4), new BlockPos(1, 2, 5),
        new BlockPos(3, 2, 3), new BlockPos(3, 2, 4), new BlockPos(3, 2, 5) };
    /** The settled column: a water source walled in on every side in the first cell (the Builder closes the east,
     *  the stone the south), then two stone and a snow layer three cells from the water, which never moves. */
    private static final int[] SETTLED_COLUMN = { AIR, STONE, STONE, SNOW };
    private static final BlockPos[] SETTLED_WALLS = { new BlockPos(1, 2, 2), new BlockPos(2, 2, 1) };
    /** Ticks the race test watches after water enters the box: several {@link SnapshotBuilder#FLUID_SCAN_INTERVAL}s,
     *  so a placement let through on a stale sweep result has time to be queued and placed. */
    private static final int RACE_WATCH_TICKS = 3 * SnapshotBuilder.FLUID_SCAN_INTERVAL;

    /** A Builder in REPLACE mode, set up on {@code column} inside {@code walls}. */
    private static TileBuilder setUpReplaceBuilder(GameTestHelper helper, int[] column, BlockPos[] walls) {
        TileBuilder tile = setUpBuilder(helper, column, walls);
        tile.cycleFluidMode(); // CLEAR -> NO_REPLACE
        tile.cycleFluidMode(); // NO_REPLACE -> REPLACE
        expect(tile.getFluidMode() == EnumFluidHandlingMode.REPLACE, "sanity: the Builder is in REPLACE mode");
        return tile;
    }

    /**
     * The sweep result is reused for {@link SnapshotBuilder#FLUID_SCAN_INTERVAL} ticks, but a fragile block must not
     * be let through on an old "no fluid" answer. In REPLACE mode fluid that can still move holds every fragile
     * placement back, because {@code SchematicBlockDefault#build} only sees fluid already beside the block, and water
     * a few cells off reaches it a few flow steps later and washes it away (its item drops). The water here is a
     * fresh SOURCE with open air beside it: on the tick it is placed nothing flows yet, so a gate that took every
     * source for settled water would let the snow through, and the water would wash it away a few ticks later.
     *
     * <p>Phases, all gated on observed state:
     * <ol start="0">
     * <li>build the dry column in REPLACE mode;</li>
     * <li>knock out the snow layer and its floor, so it is a waiting placement that reaches the fluid gate every
     *     tick (swept about once an interval) but cannot stand;</li>
     * <li>right after a sweep (so its "no fluid" answer would be reused for a whole interval), put a water source
     *     in the box's first air cell, free to flow down the column to the snow, and give the snow its floor back
     *     in the same tick;</li>
     * <li>for {@link #RACE_WATCH_TICKS} the snow must be neither queued nor placed while that water stands;</li>
     * <li>control: take the water away, and the snow must now be placed (so it was the water that held it).</li>
     * </ol>
     */
    public static void fragilePlacementIsNotLetThroughOnAStaleSweep(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TileBuilder tile = setUpReplaceBuilder(helper, RACE_COLUMN, RACE_WALLS);
        BlockPos water = cell(0);
        BlockPos snow = cell(3);
        BlockPos snowAbs = helper.absolutePos(snow);

        int[] phase = { 0 };
        int[] phaseStart = { 0 };
        int[] sweepsAtStart = { 0 };
        boolean[] finished = { false };

        drive(helper, finished, t -> {
            SnapshotBuilder<?> builder = tile.getBuilder();
            expect(builder != null, "the Builder lost its blueprint builder");
            BlockState[] cells = new BlockState[RACE_COLUMN.length];
            boolean allBuilt = true;
            for (int i = 0; i < cells.length; i++) {
                cells[i] = helper.getBlockState(cell(i));
                allBuilt &= isBuilt(RACE_COLUMN[i], cells[i]);
            }
            boolean snowQueued = builder.placeTasks.stream().anyMatch(task -> task.pos.equals(snowAbs));
            int inPhase = t - phaseStart[0];
            switch (phase[0]) {
                case 0 -> {
                    if (allBuilt && builderQuiet(builder)) {
                        helper.setBlock(snow, Blocks.AIR);
                        helper.setBlock(snow.below(), Blocks.AIR);
                        phase[0] = 1;
                        phaseStart[0] = t;
                    } else {
                        expect(inPhase < 300, "phase 0: the Builder never finished the dry build, cells="
                                + Arrays.toString(cells) + ", " + describe(builder));
                    }
                }
                case 1 -> {
                    if (builder.leftToPlace > 0 && builder.placeTasks.isEmpty()) {
                        phase[0] = 2;
                        phaseStart[0] = t;
                        sweepsAtStart[0] = builder.getFluidSweepCount();
                    } else {
                        expect(inPhase < 100, "phase 1 (test premise): the Builder never saw the snow layer as a "
                                + "waiting placement, cells=" + Arrays.toString(cells) + ", " + describe(builder));
                    }
                }
                case 2 -> {
                    expect(!cells[3].is(Blocks.SNOW), "phase 2 (test premise): the snow layer stood with no floor");
                    if (builder.getFluidSweepCount() != sweepsAtStart[0]) {
                        BlockWaterloggingTester.placeSource(helper, water, Blocks.WATER, Fluids.WATER);
                        helper.setBlock(snow.below(), Blocks.STONE);
                        phase[0] = 3;
                        phaseStart[0] = t;
                    } else {
                        expect(inPhase < 20, "phase 2 (test premise): the waiting placement never reached the "
                                + "fluid gate, " + describe(builder));
                    }
                }
                case 3 -> {
                    expect(level.getFluidState(helper.absolutePos(water)).isSource(), "phase 3 (test premise): the "
                            + "water source in the box went away, cells=" + Arrays.toString(cells));
                    expect(!snowQueued && !cells[3].is(Blocks.SNOW), "a fragile block was "
                            + (cells[3].is(Blocks.SNOW) ? "placed" : "queued") + " " + inPhase + " ticks after water "
                            + "that can still flow entered the REPLACE build box; " + describe(builder));
                    if (inPhase >= RACE_WATCH_TICKS) {
                        helper.setBlock(water, Blocks.AIR);
                        phase[0] = 4;
                        phaseStart[0] = t;
                    }
                }
                case 4 -> {
                    if (cells[3].is(Blocks.SNOW)) {
                        finished[0] = true;
                        helper.succeed();
                    } else {
                        expect(inPhase < 100, "phase 4 (control): with the water gone the snow layer was still "
                                + "not placed, so the water was not what held it; cells=" + Arrays.toString(cells)
                                + ", " + describe(builder));
                    }
                }
                default -> throw new IllegalStateException("bad phase " + phase[0]);
            }
        });
    }

    /**
     * The other half of the REPLACE fragile gate: settled water must not hold back a fragile block it can never reach.
     * A water source walled in on every side cannot flow anywhere, so a snow layer three cells off is safe to place.
     * Before the fix any fluid anywhere in the box held every fragile placement back, so a REPLACE build with one
     * still pond in it never finished. The schematic-air cell holding the water must also count as done: REPLACE never
     * removes fluid, so the cell was once left "to break" for ever and the Builder never rested.
     *
     * <p>Phases, all gated on observed state:
     * <ol start="0">
     * <li>build the dry column in REPLACE mode;</li>
     * <li>put the walled water source in the first cell and knock the snow layer out in the same tick;</li>
     * <li>the snow layer must be placed again and the Builder must rest for {@link BuilderRefloodTester#QUIET_TICKS}
     *     straight ticks with the water source still standing.</li>
     * </ol>
     */
    public static void settledWaterDoesNotHoldBackADistantFragileBlock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TileBuilder tile = setUpReplaceBuilder(helper, SETTLED_COLUMN, SETTLED_WALLS);
        BlockPos water = cell(0);
        BlockPos snow = cell(3);

        int[] phase = { 0 };
        int[] phaseStart = { 0 };
        int[] quiet = { 0 };
        boolean[] finished = { false };

        drive(helper, finished, t -> {
            SnapshotBuilder<?> builder = tile.getBuilder();
            expect(builder != null, "the Builder lost its blueprint builder");
            BlockState[] cells = new BlockState[SETTLED_COLUMN.length];
            boolean allBuilt = true;
            for (int i = 0; i < cells.length; i++) {
                cells[i] = helper.getBlockState(cell(i));
                allBuilt &= isBuilt(SETTLED_COLUMN[i], cells[i]);
            }
            int inPhase = t - phaseStart[0];
            switch (phase[0]) {
                case 0 -> {
                    if (allBuilt && builderQuiet(builder)) {
                        BlockWaterloggingTester.placeSource(helper, water, Blocks.WATER, Fluids.WATER);
                        helper.setBlock(snow, Blocks.AIR);
                        phase[0] = 1;
                        phaseStart[0] = t;
                    } else {
                        expect(inPhase < 300, "phase 0: the Builder never finished the dry build, cells="
                                + Arrays.toString(cells) + ", " + describe(builder));
                    }
                }
                case 1 -> {
                    expect(level.getFluidState(helper.absolutePos(water)).isSource(), "phase 1 (test premise): the "
                            + "walled water source went away, cells=" + Arrays.toString(cells));
                    boolean rested = cells[3].is(Blocks.SNOW) && builderQuiet(builder);
                    quiet[0] = rested ? quiet[0] + 1 : 0;
                    if (quiet[0] >= QUIET_TICKS) {
                        helper.setBlock(water, Blocks.AIR);
                        finished[0] = true;
                        helper.succeed();
                    } else {
                        expect(inPhase < 300, "phase 1: the REPLACE Builder never "
                                + (cells[3].is(Blocks.SNOW) ? "rested" : "placed the snow layer")
                                + " with a walled water source three cells from it; cells=" + Arrays.toString(cells)
                                + ", " + describe(builder));
                    }
                }
                default -> throw new IllegalStateException("bad phase " + phase[0]);
            }
        });
    }

    /** {@link #waterloggedBlockInAirCellIsBrokenOutsideClear} in REPLACE mode. */
    public static void replaceBreaksWaterloggedBlockInAirCell(GameTestHelper helper) {
        waterloggedBlockInAirCellIsBrokenOutsideClear(helper, EnumFluidHandlingMode.REPLACE);
    }

    /** {@link #waterloggedBlockInAirCellIsBrokenOutsideClear} in NO_REPLACE mode. */
    public static void noReplaceBreaksWaterloggedBlockInAirCell(GameTestHelper helper) {
        waterloggedBlockInAirCellIsBrokenOutsideClear(helper, EnumFluidHandlingMode.NO_REPLACE);
    }

    /**
     * REPLACE and NO_REPLACE never remove fluid, but a real block holding fluid in a schematic-air cell (a
     * waterlogged fence, seagrass, kelp) is still a block the schematic does not want. The break queue once refused
     * every fluid-holding cell outside CLEAR, so such a cell stayed "to break" for ever and the Builder never rested.
     * Breaking the block leaves its water behind, which those modes then leave alone.
     *
     * <p>Phases, all gated on observed state:
     * <ol start="0">
     * <li>build the dry column in {@code mode};</li>
     * <li>put a waterlogged oak fence in the walled first cell (schematic air);</li>
     * <li>the fence must go, its water source must stay, and the Builder must rest for
     *     {@link BuilderRefloodTester#QUIET_TICKS} straight ticks.</li>
     * </ol>
     */
    private static void waterloggedBlockInAirCellIsBrokenOutsideClear(GameTestHelper helper,
            EnumFluidHandlingMode mode) {
        ServerLevel level = helper.getLevel();
        TileBuilder tile = setUpBuilder(helper, SETTLED_COLUMN, SETTLED_WALLS);
        while (tile.getFluidMode() != mode) {
            tile.cycleFluidMode();
        }
        BlockPos wet = cell(0);

        int[] phase = { 0 };
        int[] phaseStart = { 0 };
        int[] quiet = { 0 };
        boolean[] finished = { false };

        drive(helper, finished, t -> {
            SnapshotBuilder<?> builder = tile.getBuilder();
            expect(builder != null, "the Builder lost its blueprint builder");
            BlockState[] cells = new BlockState[SETTLED_COLUMN.length];
            boolean allBuilt = true;
            for (int i = 0; i < cells.length; i++) {
                cells[i] = helper.getBlockState(cell(i));
                allBuilt &= isBuilt(SETTLED_COLUMN[i], cells[i]);
            }
            int inPhase = t - phaseStart[0];
            switch (phase[0]) {
                case 0 -> {
                    if (allBuilt && builderQuiet(builder)) {
                        helper.setBlock(wet, Blocks.OAK_FENCE.defaultBlockState()
                                .setValue(BlockStateProperties.WATERLOGGED, true));
                        phase[0] = 1;
                        phaseStart[0] = t;
                    } else {
                        expect(inPhase < 300, "phase 0: the " + mode + " Builder never finished the dry build, "
                                + "cells=" + Arrays.toString(cells) + ", " + describe(builder));
                    }
                }
                case 1 -> {
                    boolean water = cells[0].is(Blocks.WATER)
                            && level.getFluidState(helper.absolutePos(wet)).isSource();
                    boolean rested = water && builderQuiet(builder);
                    quiet[0] = rested ? quiet[0] + 1 : 0;
                    if (quiet[0] >= QUIET_TICKS) {
                        helper.setBlock(wet, Blocks.AIR);
                        finished[0] = true;
                        helper.succeed();
                    } else {
                        expect(inPhase < 300, "phase 1: the " + mode + " Builder never "
                                + (water ? "rested" : "broke the waterlogged fence") + " in a schematic-air cell; "
                                + "cells=" + Arrays.toString(cells) + ", " + describe(builder));
                    }
                }
                default -> throw new IllegalStateException("bad phase " + phase[0]);
            }
        });
    }

    /** The edge length (blocks) of the tall air column {@link #sweepAllocatesNothingPerCell} sweeps. */
    private static final int TALL = 40;

    /**
     * One sweep of a build box holding no fluid at all must not allocate per cell: it once listed the box into one
     * fresh {@link BlockPos} per cell (plus the list), on every tick. The box here is a blueprint of {@link #TALL}
     * x 4 schematic-air cells standing in the arena (it reaches far above the arena, into empty sky), which is
     * 160 cells: the old sweep allocated several KB, the cursor sweep a few dozen bytes.
     */
    public static void sweepAllocatesNothingPerCell(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos sample = helper.absolutePos(new BlockPos(5, 2, 5));
        Blueprint blueprint = new Blueprint();
        blueprint.size = new BlockPos(1, TALL, 4);
        blueprint.facing = Direction.EAST;
        blueprint.offset = BlockPos.ZERO;
        BlockState air = Blocks.AIR.defaultBlockState();
        ISchematicBlock schematic = SchematicBlockManager.getSchematicBlock(
                new SchematicBlockContext(level, sample, sample, air, air.getBlock()));
        expect(schematic != null && schematic.isAir(), "the air schematic must resolve");
        blueprint.palette.add(schematic);
        blueprint.data = new int[TALL * 4]; // every cell is palette entry 0: air
        blueprint.computeKey();
        GlobalSavedDataSnapshots.get(level).addSnapshot(blueprint);

        helper.setBlock(BUILDER, BCBuildersBlocks.BUILDER.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
        //? if >=1.21.10 {
        TileBuilder tile = helper.getBlockEntity(BUILDER, TileBuilder.class);
        //?} else {
        /*TileBuilder tile = helper.getBlockEntity(BUILDER);*/
        //?}
        tile.cycleFluidMode(); // NO_REPLACE -> REPLACE
        tile.cycleFluidMode(); // REPLACE -> CLEAR
        tile.setSnapshot(BCBuildersItems.BLUEPRINT_USED.get().createUsedStack(
                new Snapshot.Header(blueprint.key, new UUID(0, 0), new Date(0), "sweep-test")));

        SnapshotBuilder<?> builder = tile.getBuilder();
        expect(builder != null && builder.getBuildingInfo() != null, "the Builder must hold the blueprint");
        BlockPos size = builder.getBuildingInfo().box.size();
        int cells = size.getX() * size.getY() * size.getZ();
        expect(cells == TALL * 4, "the build box must cover the whole blueprint, got " + size);

        for (int i = 0; i < 20; i++) { // warm the sweep up so the measured one is the steady state
            builder.scanAreaFluid();
        }
        // The meter throws when the JVM cannot count per-thread allocation (it switches tracking on when it can):
        // a counter that reads -1 would measure every sweep as zero bytes, and this test would pass unmeasured.
        // The check on the answer stays out of the measured action, whose message building would be counted.
        SnapshotBuilder.AreaFluid[] last = { null };
        boolean[] everyAnswerNone = { true };
        long allocated = ThreadAllocationMeter.leastAllocatedBytes(5, () -> {
            last[0] = builder.scanAreaFluid();
            everyAnswerNone[0] &= last[0] == SnapshotBuilder.AreaFluid.NONE;
        });
        expect(everyAnswerNone[0], "the box holds no fluid, got " + last[0]);
        long budget = cells * 8L; // under one reference per cell; the old sweep spent 30+ bytes on each
        expect(allocated < budget, "one sweep over " + cells + " fluid-free cells allocated " + allocated
                + " bytes (budget " + budget + "): it allocates per cell");
        helper.succeed();
    }
}
