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
import static buildcraft.builders.BuilderRefloodTester.WET_SLAB;
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
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
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
import buildcraft.lib.test.EntityArenaUtil;
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
     *     {@link SnapshotBuilder#FLUID_SCAN_INTERVAL} ticks, not every tick. The snow layer cannot stand, so it is
     *     refused before the last gate and only the reused-result gate sweeps here; the last gate's own sweep is
     *     bounded by {@link #freshGateSweepsOnceForSeveralFragilePlacements};</li>
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
        return setUpBuilderIn(helper, column, walls, EnumFluidHandlingMode.REPLACE);
    }

    /** A Builder in {@code mode}, set up on {@code column} inside {@code walls}. */
    private static TileBuilder setUpBuilderIn(GameTestHelper helper, int[] column, BlockPos[] walls,
            EnumFluidHandlingMode mode) {
        TileBuilder tile = setUpBuilder(helper, column, walls);
        for (int i = 0; i < EnumFluidHandlingMode.values().length && tile.getFluidMode() != mode; i++) {
            tile.cycleFluidMode();
        }
        expect(tile.getFluidMode() == mode, "sanity: cycling the fluid mode never reached " + mode);
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
     * NO_REPLACE holds a fragile placement back from water that can still reach it, as REPLACE does. It once had no
     * fragile defer at all: a snow layer placed a few cells ahead of flowing water was washed away (its item dropped),
     * and the cell then held flowing water, which NO_REPLACE counts as built, so the block was never placed again.
     *
     * <p>Phases, all gated on observed state:
     * <ol start="0">
     * <li>build the dry race column in NO_REPLACE mode;</li>
     * <li>knock out the snow layer and its floor, and wait for the Builder to see it as a waiting placement (it cannot
     *     stand, so it is never queued);</li>
     * <li>put a water source in the box's first air cell, free to flow down the column to the snow, and give the snow
     *     its floor back in the same tick: for {@link #RACE_WATCH_TICKS} the snow must be neither queued nor placed
     *     while that water stands;</li>
     * <li>control: take the water away, and the snow must now be placed (so it was the water that held it).</li>
     * </ol>
     */
    public static void noReplaceFragilePlacementWaitsForWaterThatCanReachIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TileBuilder tile = setUpBuilderIn(helper, RACE_COLUMN, RACE_WALLS, EnumFluidHandlingMode.NO_REPLACE);
        BlockPos water = cell(0);
        BlockPos snow = cell(3);
        BlockPos snowAbs = helper.absolutePos(snow);

        int[] phase = { 0 };
        int[] phaseStart = { 0 };
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
                        expect(inPhase < 300, "phase 0: the NO_REPLACE Builder never finished the dry build, cells="
                                + Arrays.toString(cells) + ", " + describe(builder));
                    }
                }
                case 1 -> {
                    if (builder.leftToPlace > 0 && builder.placeTasks.isEmpty()) {
                        BlockWaterloggingTester.placeSource(helper, water, Blocks.WATER, Fluids.WATER);
                        helper.setBlock(snow.below(), Blocks.STONE);
                        phase[0] = 2;
                        phaseStart[0] = t;
                    } else {
                        expect(inPhase < 100, "phase 1 (test premise): the Builder never saw the snow layer as a "
                                + "waiting placement, cells=" + Arrays.toString(cells) + ", " + describe(builder));
                    }
                }
                case 2 -> {
                    expect(level.getFluidState(helper.absolutePos(water)).isSource(), "phase 2 (test premise): the "
                            + "water source in the box went away, cells=" + Arrays.toString(cells));
                    expect(!snowQueued && !cells[3].is(Blocks.SNOW), "a fragile block was "
                            + (cells[3].is(Blocks.SNOW) ? "placed" : "queued") + " " + inPhase + " ticks after water "
                            + "that can still flow entered the NO_REPLACE build box; " + describe(builder));
                    if (inPhase >= RACE_WATCH_TICKS) {
                        helper.setBlock(water, Blocks.AIR);
                        phase[0] = 3;
                        phaseStart[0] = t;
                    }
                }
                case 3 -> {
                    if (cells[3].is(Blocks.SNOW)) {
                        finished[0] = true;
                        helper.succeed();
                    } else {
                        expect(inPhase < 200, "phase 3 (control): with the water gone the snow layer was still "
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
     */
    public static void settledWaterDoesNotHoldBackADistantFragileBlock(GameTestHelper helper) {
        runSettledFragileScenario(helper, EnumFluidHandlingMode.REPLACE, SETTLED_COLUMN, SETTLED_WALLS,
                "a walled water source three cells from it");
    }

    /** {@link #settledWaterDoesNotHoldBackADistantFragileBlock} in NO_REPLACE mode, whose fragile placements wait on
     *  the same gate as REPLACE's: a still pond in the box must not hold them back for ever there either. */
    public static void noReplaceSettledWaterDoesNotHoldBackADistantFragileBlock(GameTestHelper helper) {
        runSettledFragileScenario(helper, EnumFluidHandlingMode.NO_REPLACE, SETTLED_COLUMN, SETTLED_WALLS,
                "a walled water source three cells from it");
    }

    /** The flowing settled column: the walled source in the first cell spills into the walled air cell after it and
     *  stops there (flowing water fed by the source, for good), then a stone and the snow layer two cells off. */
    private static final int[] FLOWING_SETTLED_COLUMN = { AIR, AIR, STONE, SNOW };
    private static final BlockPos[] FLOWING_SETTLED_WALLS = {
        new BlockPos(1, 2, 2), new BlockPos(2, 2, 1), new BlockPos(1, 2, 3), new BlockPos(3, 2, 3) };

    /**
     * As {@link #settledWaterDoesNotHoldBackADistantFragileBlock}, with FLOWING water in the box that has stopped
     * spreading: a source that spills into a walled cell beside it (as a waterlogged block beside a schematic-air
     * cell does). Flowing water is not always moving: once it has filled every cell it can reach it stays as it is
     * for as long as its source does. A gate that held every fragile placement back on any flowing fluid in the box
     * held this snow layer back for ever.
     */
    public static void settledFlowingWaterDoesNotHoldBackADistantFragileBlock(GameTestHelper helper) {
        runSettledFragileScenario(helper, EnumFluidHandlingMode.REPLACE, FLOWING_SETTLED_COLUMN,
                FLOWING_SETTLED_WALLS, "settled flowing water two cells from it");
    }

    /**
     * Phases, all gated on observed state:
     * <ol start="0">
     * <li>build the dry {@code column} in {@code mode};</li>
     * <li>put a water source in the first cell (walled in by {@code walls}) and knock the snow layer (last cell)
     *     out in the same tick;</li>
     * <li>the snow layer must be placed again and the Builder must rest for {@link BuilderRefloodTester#QUIET_TICKS}
     *     straight ticks with the water source still standing.</li>
     * </ol>
     */
    private static void runSettledFragileScenario(GameTestHelper helper, EnumFluidHandlingMode mode, int[] column,
            BlockPos[] walls, String what) {
        ServerLevel level = helper.getLevel();
        TileBuilder tile = setUpBuilderIn(helper, column, walls, mode);
        BlockPos water = cell(0);
        int snowIndex = column.length - 1;
        BlockPos snow = cell(snowIndex);

        int[] phase = { 0 };
        int[] phaseStart = { 0 };
        int[] quiet = { 0 };
        boolean[] finished = { false };

        drive(helper, finished, t -> {
            SnapshotBuilder<?> builder = tile.getBuilder();
            expect(builder != null, "the Builder lost its blueprint builder");
            BlockState[] cells = new BlockState[column.length];
            boolean allBuilt = true;
            for (int i = 0; i < cells.length; i++) {
                cells[i] = helper.getBlockState(cell(i));
                allBuilt &= isBuilt(column[i], cells[i]);
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
                    boolean rested = cells[snowIndex].is(Blocks.SNOW) && builderQuiet(builder);
                    quiet[0] = rested ? quiet[0] + 1 : 0;
                    if (quiet[0] >= QUIET_TICKS) {
                        for (int i = 0; i < snowIndex; i++) {
                            if (column[i] == AIR) helper.setBlock(cell(i), Blocks.AIR);
                        }
                        finished[0] = true;
                        helper.succeed();
                    } else {
                        expect(inPhase < 300, "phase 1: the " + mode + " Builder never "
                                + (cells[snowIndex].is(Blocks.SNOW) ? "rested" : "placed the snow layer")
                                + " with " + what + "; cells=" + Arrays.toString(cells) + ", " + describe(builder));
                    }
                }
                default -> throw new IllegalStateException("bad phase " + phase[0]);
            }
        });
    }

    /** The washed column: a water source walled in the first cell (schematic air) spills over the two snow cells
     *  after it and stops at the south wall, so both snow cells hold flowing water fed by it for good. */
    private static final int[] WASHED_COLUMN = { AIR, SNOW, SNOW };
    private static final BlockPos[] WASHED_WALLS = {
        new BlockPos(2, 2, 1), new BlockPos(1, 2, 2), new BlockPos(1, 2, 3), new BlockPos(1, 2, 4),
        new BlockPos(3, 2, 3), new BlockPos(3, 2, 4), new BlockPos(2, 2, 5) };

    /**
     * REPLACE never removes fluid, so a fragile block whose cell settled fluid keeps washing can never stand (the
     * gate and {@code SchematicBlockDefault#build} both refuse it). Such a cell must count as done, as CLEAR accepts a
     * fragile cell beside fluid it will never mop; it was once left waiting for ever, so a REPLACE build with a torch
     * in a pond never finished. Two snow cells wash each other as well as being washed by the source, so neither may
     * wait for the other to be accepted first.
     *
     * <p>Phases, all gated on observed state:
     * <ol start="0">
     * <li>build the dry column in REPLACE mode;</li>
     * <li>put a water source in the first cell and knock both snow layers out in the same tick;</li>
     * <li>the Builder must rest for {@link BuilderRefloodTester#QUIET_TICKS} straight ticks with water over both snow
     *     cells;</li>
     * <li>control: take the source away, and both snow layers must be placed once the water drains (the acceptance
     *     lasts only while the water does).</li>
     * </ol>
     */
    public static void replaceRestsWithFragileCellsInSettledWater(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TileBuilder tile = setUpReplaceBuilder(helper, WASHED_COLUMN, WASHED_WALLS);
        BlockPos water = cell(0);

        int[] phase = { 0 };
        int[] phaseStart = { 0 };
        int[] quiet = { 0 };
        boolean[] finished = { false };

        drive(helper, finished, t -> {
            SnapshotBuilder<?> builder = tile.getBuilder();
            expect(builder != null, "the Builder lost its blueprint builder");
            BlockState[] cells = new BlockState[WASHED_COLUMN.length];
            boolean allBuilt = true;
            for (int i = 0; i < cells.length; i++) {
                cells[i] = helper.getBlockState(cell(i));
                allBuilt &= isBuilt(WASHED_COLUMN[i], cells[i]);
            }
            int inPhase = t - phaseStart[0];
            switch (phase[0]) {
                case 0 -> {
                    if (allBuilt && builderQuiet(builder)) {
                        BlockWaterloggingTester.placeSource(helper, water, Blocks.WATER, Fluids.WATER);
                        helper.setBlock(cell(1), Blocks.AIR);
                        helper.setBlock(cell(2), Blocks.AIR);
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
                    expect(!cells[1].is(Blocks.SNOW) && !cells[2].is(Blocks.SNOW), "phase 1: the REPLACE Builder "
                            + "placed a snow layer into water that washes it away; cells=" + Arrays.toString(cells));
                    boolean washed = !level.getFluidState(helper.absolutePos(cell(1))).isEmpty()
                            && !level.getFluidState(helper.absolutePos(cell(2))).isEmpty();
                    quiet[0] = washed && builderQuiet(builder) ? quiet[0] + 1 : 0;
                    if (quiet[0] >= QUIET_TICKS) {
                        helper.setBlock(water, Blocks.AIR);
                        phase[0] = 2;
                        phaseStart[0] = t;
                    } else {
                        expect(inPhase < 300, "phase 1: the REPLACE Builder never rested with settled water "
                                + "washing both snow cells; cells=" + Arrays.toString(cells) + ", "
                                + describe(builder));
                    }
                }
                case 2 -> {
                    if (cells[1].is(Blocks.SNOW) && cells[2].is(Blocks.SNOW)) {
                        finished[0] = true;
                        helper.succeed();
                    } else {
                        expect(inPhase < 300, "phase 2 (control): with the water gone the snow layers were not "
                                + "placed again; cells=" + Arrays.toString(cells) + ", " + describe(builder));
                    }
                }
                default -> throw new IllegalStateException("bad phase " + phase[0]);
            }
        });
    }

    /** One schematic stone cell, walled in on every side but the top (the Builder closes the east, the next stone
     *  the south), so a water source put there stays where it is. */
    private static final int[] SOLID_COLUMN = { STONE, STONE };

    /**
     * NO_REPLACE never replaces fluid, so a schematic block whose cell holds nothing but fluid cannot be built. The
     * check once sent such a cell to "to break", where the break queue (which refuses fluid outside CLEAR) left it
     * for ever: the build never finished. Like a schematic-air cell holding fluid, it is as built as that mode makes
     * it, and counts as done (and is built if the fluid ever goes).
     *
     * <p>Phases, all gated on observed state:
     * <ol start="0">
     * <li>build the dry column in NO_REPLACE mode;</li>
     * <li>swap the first stone for a water source;</li>
     * <li>the Builder must rest for {@link BuilderRefloodTester#QUIET_TICKS} straight ticks with the source standing
     *     (NO_REPLACE must not have replaced it).</li>
     * </ol>
     */
    public static void noReplaceRestsWithFluidInASolidCell(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TileBuilder tile = setUpBuilder(helper, SOLID_COLUMN, SETTLED_WALLS);
        tile.cycleFluidMode(); // CLEAR -> NO_REPLACE
        expect(tile.getFluidMode() == EnumFluidHandlingMode.NO_REPLACE, "sanity: the Builder is in NO_REPLACE mode");
        BlockPos water = cell(0);

        int[] phase = { 0 };
        int[] phaseStart = { 0 };
        int[] quiet = { 0 };
        boolean[] finished = { false };

        drive(helper, finished, t -> {
            SnapshotBuilder<?> builder = tile.getBuilder();
            expect(builder != null, "the Builder lost its blueprint builder");
            BlockState[] cells = new BlockState[SOLID_COLUMN.length];
            boolean allBuilt = true;
            for (int i = 0; i < cells.length; i++) {
                cells[i] = helper.getBlockState(cell(i));
                allBuilt &= isBuilt(SOLID_COLUMN[i], cells[i]);
            }
            int inPhase = t - phaseStart[0];
            switch (phase[0]) {
                case 0 -> {
                    if (allBuilt && builderQuiet(builder)) {
                        BlockWaterloggingTester.placeSource(helper, water, Blocks.WATER, Fluids.WATER);
                        phase[0] = 1;
                        phaseStart[0] = t;
                    } else {
                        expect(inPhase < 300, "phase 0: the NO_REPLACE Builder never finished the dry build, cells="
                                + Arrays.toString(cells) + ", " + describe(builder));
                    }
                }
                case 1 -> {
                    expect(level.getFluidState(helper.absolutePos(water)).isSource(), "NO_REPLACE replaced the "
                            + "water source in a schematic stone cell; cells=" + Arrays.toString(cells));
                    quiet[0] = builderQuiet(builder) ? quiet[0] + 1 : 0;
                    if (quiet[0] >= QUIET_TICKS) {
                        helper.setBlock(water, Blocks.AIR);
                        finished[0] = true;
                        helper.succeed();
                    } else {
                        expect(inPhase < 300, "the NO_REPLACE Builder never rested with a water source in a "
                                + "schematic stone cell; cells=" + Arrays.toString(cells) + ", " + describe(builder));
                    }
                }
                default -> throw new IllegalStateException("bad phase " + phase[0]);
            }
        });
    }

    /** One schematic waterlogged slab, walled in on every side but the top (the Builder closes the east). */
    private static final int[] WET_SLAB_COLUMN = { WET_SLAB };
    private static final BlockPos[] WET_SLAB_WALLS = {
        new BlockPos(1, 2, 2), new BlockPos(2, 2, 1), new BlockPos(2, 2, 3) };

    /**
     * NO_REPLACE keeps the fluid it finds, so it skips a block over fluid
     * ({@link #noReplaceRestsWithFluidInASolidCell}), but a block the blueprint wants waterlogged, going into a source
     * of its own fluid, keeps that fluid: placing it replaces nothing. It was once skipped like a dry block (the cell
     * counted as built), so a blueprint of a flooded build rebuilt under water came out without its waterlogged
     * blocks.
     *
     * <p>Phases, all gated on observed state:
     * <ol start="0">
     * <li>put a water source in the walled cell before the NO_REPLACE Builder's first tick;</li>
     * <li>the Builder must place the waterlogged slab there (the water kept, as the slab's own) and then rest for
     *     {@link BuilderRefloodTester#QUIET_TICKS} straight ticks.</li>
     * </ol>
     */
    public static void noReplacePlacesWaterloggedBlockIntoItsOwnWater(GameTestHelper helper) {
        TileBuilder tile = setUpBuilderIn(helper, WET_SLAB_COLUMN, WET_SLAB_WALLS, EnumFluidHandlingMode.NO_REPLACE);
        BlockPos wet = cell(0);
        BlockWaterloggingTester.placeSource(helper, wet, Blocks.WATER, Fluids.WATER);

        int[] quiet = { 0 };
        int[] quietUnbuilt = { 0 };
        boolean[] finished = { false };

        drive(helper, finished, t -> {
            SnapshotBuilder<?> builder = tile.getBuilder();
            expect(builder != null, "the Builder lost its blueprint builder");
            BlockState state = helper.getBlockState(wet);
            boolean built = isWetSlab(state);
            expect(built || state.is(Blocks.WATER), "the waterlogged slab's cell lost its water, cell=" + state);
            quiet[0] = built && builderQuiet(builder) ? quiet[0] + 1 : 0;
            quietUnbuilt[0] = !built && builderQuiet(builder) ? quietUnbuilt[0] + 1 : 0;
            expect(quietUnbuilt[0] < QUIET_TICKS, "the NO_REPLACE Builder rested without placing the waterlogged slab "
                    + "into the water source in its cell; cell=" + state + ", " + describe(builder));
            if (quiet[0] >= QUIET_TICKS) {
                helper.setBlock(wet, Blocks.AIR);
                finished[0] = true;
                helper.succeed();
            } else {
                expect(t < 300, "the NO_REPLACE Builder never placed the waterlogged slab and rested; cell=" + state
                        + ", " + describe(builder));
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
                        // The Builder stores what it breaks, but a full inventory drops the fence in the world.
                        for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class,
                                EntityArenaUtil.cellBounds(helper))) {
                            drop.discard();
                        }
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

    /** Four fragile blocks side by side on the dry arena floor, none with fluid anywhere near. */
    private static final int[] SNOW_ROW = { SNOW, SNOW, SNOW, SNOW };

    /**
     * A fragile placement is let through only on a sweep taken this tick ({@code passesFreshFluidGate}), but however
     * many fragile placements queue in one tick, that is ONE sweep: the first one re-sweeps and the rest ride on its
     * answer. Without that, every queued torch or carpet costs a whole-box sweep of its own, and a build laying
     * hundreds of them sweeps a big box hundreds of times a tick.
     *
     * <p>The Builder is ticked by hand, so each pass is one Builder tick and nothing depends on when the game ticks
     * run. A sweep result is "this tick's" only on the pass that took it; the first pass has none to reuse, so its one
     * sweep comes from the reused-result gate, and the second round is set up so the result is a few ticks old and the
     * sweep can only come from the last gate:
     * <ol start="0">
     * <li>four snow layers are all to be placed in the first pass: it must sweep once;</li>
     * <li>passes go on until all four stand, none sweeping more than once;</li>
     * <li>three of them are knocked out, with the last sweep a few passes old (younger than
     *     {@link SnapshotBuilder#FLUID_SCAN_INTERVAL}, so the reused-result gate does not sweep): all three are to be
     *     placed in the next pass, which must sweep exactly once, for the first of them.</li>
     * </ol>
     */
    public static void freshGateSweepsOnceForSeveralFragilePlacements(GameTestHelper helper) {
        TileBuilder tile = setUpBuilder(helper, SNOW_ROW, new BlockPos[0]);
        SnapshotBuilder<?> builder = tile.getBuilder();
        expect(builder != null, "the Builder lost its blueprint builder");
        expect(SnapshotBuilder.FLUID_SCAN_INTERVAL > 4, "premise: a sweep result must outlive the passes this test "
                + "takes between the rounds");
        int[] sinceSweep = { 0 };

        IntSupplier pass = () -> {
            tile.getBattery().addPower(tile.getBattery().getCapacity() - tile.getBattery().getStored(), false);
            int before = builder.getFluidSweepCount();
            builder.tick();
            int sweeps = builder.getFluidSweepCount() - before;
            sinceSweep[0] = sweeps > 0 ? 0 : sinceSweep[0] + 1;
            return sweeps;
        };
        Supplier<String> state = () -> "cells=" + describeSnowRow(helper) + ", " + describe(builder);

        int first = pass.getAsInt();
        expect(builder.leftToPlace == SNOW_ROW.length, "phase 0 (test premise): all four snow layers should be to "
                + "place in the first pass; " + state.get());
        expect(first == 1, "the first pass queued " + SNOW_ROW.length + " fragile placements and swept the whole box "
                + first + " times (once expected); " + state.get());
        for (int i = 0; i < 10 && !snowRowStands(helper, 0, SNOW_ROW.length); i++) {
            int sweeps = pass.getAsInt();
            expect(sweeps <= 1, "a pass swept the whole box " + sweeps + " times (at most once expected) while the "
                    + "snow row was placed; " + state.get());
        }
        expect(snowRowStands(helper, 0, SNOW_ROW.length), "phase 1 (test premise): the snow row was never built; "
                + state.get());
        expect(sinceSweep[0] <= 3, "phase 1 (test premise): the last sweep is too old for the second round, "
                + sinceSweep[0] + " passes; " + state.get());

        for (int i = 1; i < SNOW_ROW.length; i++) {
            helper.setBlock(cell(i), Blocks.AIR);
        }
        int second = pass.getAsInt();
        expect(builder.leftToPlace == SNOW_ROW.length - 1, "phase 2 (test premise): the three knocked-out layers "
                + "should all be to place in the next pass; " + state.get());
        expect(second == 1, "three fragile placements queued in one pass swept the whole box " + second + " times "
                + "(once, by the first of them, expected); " + state.get());
        helper.succeed();
    }

    private static boolean snowRowStands(GameTestHelper helper, int from, int to) {
        for (int i = from; i < to; i++) {
            if (!helper.getBlockState(cell(i)).is(Blocks.SNOW)) {
                return false;
            }
        }
        return true;
    }

    private static String describeSnowRow(GameTestHelper helper) {
        BlockState[] cells = new BlockState[SNOW_ROW.length];
        for (int i = 0; i < cells.length; i++) {
            cells[i] = helper.getBlockState(cell(i));
        }
        return Arrays.toString(cells);
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
