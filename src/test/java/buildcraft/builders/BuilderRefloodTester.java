/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.builders;

import java.util.Arrays;
import java.util.Date;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;

import buildcraft.api.mj.MjAPI;
import buildcraft.api.schematics.ISchematicBlock;
import buildcraft.api.schematics.SchematicBlockContext;
import buildcraft.builders.snapshot.Blueprint;
import buildcraft.builders.snapshot.EnumFluidHandlingMode;
import buildcraft.builders.snapshot.GlobalSavedDataSnapshots;
import buildcraft.builders.snapshot.SchematicBlockManager;
import buildcraft.builders.snapshot.Snapshot;
import buildcraft.builders.snapshot.SnapshotBuilder;
import buildcraft.builders.tile.TileBuilder;
import buildcraft.lib.block.BlockWaterloggingTester;
import buildcraft.lib.test.EntityArenaUtil;

/**
 * Drives a real {@link TileBuilder} through {@link buildcraft.builders.snapshot.BlueprintBuilder}'s live tick loop
 * in CLEAR fluid mode, against the geometry the single-call {@code FluidHandlingModeTester} cases cannot reach:
 * dry waterloggable blocks on the edge of the build area with water sources just OUTSIDE it.
 *
 * <p>Vanilla only waterlogs a block through the infinite-water rule (see {@code BlockWaterloggingTester}): a single
 * source, or flowing water, is held back, but a cell flanked by TWO water sources over a solid floor becomes a
 * source itself and so waterlogs a waterloggable block there. CLEAR's {@code isBuilt} is strict about waterlogging
 * ("the user wants the water gone"), so before the fix the Builder classed the flooded block as to-break, tore it
 * (and the water) out, found the cell flooding again from the same two sources, and cycled for ever, draining MJ
 * while the whole base position stayed unfinished. The rule under test: water the Builder cannot mop (two or more
 * water-source neighbours that lie outside the build box, or are themselves accepted cells) is tolerated on a block
 * that is otherwise correct, and the tolerated water must not hold the CLEAR "mop first" placement gate shut.
 *
 * <p>Layout (relative to the arena, all inside the 6x7 cell). A one-block-wide column runs along +Z from
 * {@code (2, 2, 2)}: dry bottom slabs first, then stone. The Builder stands east of the first cell, facing EAST, so
 * the build box is that one-wide column. Every outside source is walled in on all sides but the one facing the
 * column, so its water can only ever go into the column and never leaks into a neighbouring arena.
 */
public class BuilderRefloodTester {

    public static final int SLAB = 0;
    public static final int STONE = 1;
    /** Schematic air: the Builder must keep the cell empty, so it is where it mops. */
    public static final int AIR = 2;
    /** A fragile block (any fluid washes it away), to check tolerated water does not hold every one back. */
    public static final int SNOW = 3;
    private static final BlockState[] KIND_STATES = {
        Blocks.OAK_SLAB.defaultBlockState(), Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState(),
        Blocks.SNOW.defaultBlockState() };

    public static final BlockPos BUILDER = new BlockPos(3, 2, 2);

    /** Consecutive quiet ticks that count as "the Builder stopped re-queuing". The sources re-flood a dry cell
     *  within ~5 ticks, so any live cycle breaks the streak many times over in this window. */
    public static final int QUIET_TICKS = 40;

    /** How long the lava test watches the Builder not rest: several lava flow cycles (lava spreads every 30 ticks in
     *  the overworld) and five times {@link #QUIET_TICKS}. */
    private static final int LAVA_WATCH_TICKS = 200;

    /** How many ticks {@link #drive} polls: the {@code max_ticks} of every {@code builder_clear_*} manifest, so the
     *  watchdog, not an exhausted poll budget, ends a test whose phase windows run long (an un-polled test would
     *  time out without saying which phase it was stuck in). Polls past the watchdog are harmless. */
    private static final int DRIVE_TICKS = 1050;

    public static void expect(boolean cond, String msg) {
        if (!cond) throw new IllegalStateException(msg);
    }

    public static BlockPos cell(int i) {
        return new BlockPos(2, 2, 2 + i);
    }

    /** One-wide blueprint along +Z; {@code column[i]} is {@link #SLAB} (dry bottom oak slab), {@link #STONE},
     *  {@link #AIR} or {@link #SNOW} (one snow layer). */
    private static ItemStack makeBlueprint(ServerLevel level, BlockPos samplePos, int[] column) {
        Blueprint blueprint = new Blueprint();
        blueprint.size = new BlockPos(1, 1, column.length);
        blueprint.facing = Direction.EAST;
        blueprint.offset = BlockPos.ZERO;
        for (int kind = 0; kind < KIND_STATES.length; kind++) {
            BlockState state = KIND_STATES[kind];
            ISchematicBlock schematic = SchematicBlockManager.getSchematicBlock(
                    new SchematicBlockContext(level, samplePos, samplePos, state, state.getBlock()));
            expect(schematic != null && schematic.isAir() == state.isAir(),
                    "the schematic must resolve (and be air exactly for air) for " + state);
            blueprint.palette.add(schematic); // palette index == kind
        }
        blueprint.data = new int[column.length];
        for (int z = 0; z < column.length; z++) {
            blueprint.data[blueprint.posToIndex(0, 0, z)] = column[z];
        }
        blueprint.computeKey();
        GlobalSavedDataSnapshots.get(level).addSnapshot(blueprint);

        return BCBuildersItems.BLUEPRINT_USED.get().createUsedStack(
                new Snapshot.Header(blueprint.key, new UUID(0, 0), new Date(0), "reflood-test"));
    }

    public static boolean isWetSlab(BlockState state) {
        return state.is(Blocks.OAK_SLAB) && state.getValue(BlockStateProperties.WATERLOGGED);
    }

    private static boolean isDrySlab(BlockState state) {
        return state.is(Blocks.OAK_SLAB) && !state.getValue(BlockStateProperties.WATERLOGGED);
    }

    private static boolean isWaterSource(BlockState state) {
        return state.is(Blocks.WATER) && state.getFluidState().isSource();
    }

    /** Is {@code state} the dry, finished block the blueprint wants for {@code kind}? */
    public static boolean isBuilt(int kind, BlockState state) {
        return switch (kind) {
            case SLAB -> isDrySlab(state);
            case STONE -> state.is(Blocks.STONE);
            case AIR -> state.isAir();
            case SNOW -> state.is(Blocks.SNOW);
            default -> throw new IllegalArgumentException("bad kind " + kind);
        };
    }

    /** True while the Builder has nothing queued and nothing left to queue. */
    public static boolean builderQuiet(SnapshotBuilder<?> builder) {
        return builder != null && builder.breakTasks.isEmpty() && builder.placeTasks.isEmpty()
                && builder.leftToBreak == 0 && builder.leftToPlace == 0;
    }

    /** The shared arena: a stone floor, the given walls, and a CLEAR-mode Builder with enough MJ and items to build
     *  {@code column}, loaded with its blueprint. The column itself is NOT placed; the Builder builds it. */
    public static TileBuilder setUpBuilder(GameTestHelper helper, int[] column, BlockPos[] walls) {
        EntityArenaUtil.forceLoadEntityArena(helper);
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 5; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        for (BlockPos wall : walls) {
            helper.setBlock(wall, Blocks.STONE);
        }
        ItemStack blueprintStack = makeBlueprint(helper.getLevel(), helper.absolutePos(new BlockPos(5, 2, 5)), column);

        helper.setBlock(BUILDER, BCBuildersBlocks.BUILDER.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
        //? if >=1.21.10 {
        TileBuilder tile = helper.getBlockEntity(BUILDER, TileBuilder.class);
        //?} else {
        /*TileBuilder tile = helper.getBlockEntity(BUILDER);*/
        //?}
        tile.getBattery().addPower(16000L * MjAPI.MJ, false);
        tile.setResource(0, new ItemStack(Items.OAK_SLAB, 8));
        tile.setResource(1, new ItemStack(Items.STONE, 8));
        tile.setResource(2, new ItemStack(Items.SNOW, 8));
        tile.cycleFluidMode(); // NO_REPLACE -> REPLACE
        tile.cycleFluidMode(); // REPLACE -> CLEAR
        expect(tile.getFluidMode() == EnumFluidHandlingMode.CLEAR, "sanity: the Builder is in CLEAR mode");
        tile.setSnapshot(blueprintStack);
        return tile;
    }

    /** Schedules {@code step} once per tick up front (see {@link EntityArenaUtil#tickUntil} for why) and turns a
     *  thrown {@link IllegalStateException} into a test failure. {@code step} calls {@code finish} to succeed. */
    public static void drive(GameTestHelper helper, boolean[] finished, IntConsumer step) {
        drive(helper, finished, step, () -> {});
    }

    /** As {@link #drive(GameTestHelper, boolean[], IntConsumer)}, running {@code cleanup} before a thrown failure
     *  is reported (a step that succeeds runs its own cleanup first). */
    private static void drive(GameTestHelper helper, boolean[] finished, IntConsumer step, Runnable cleanup) {
        for (int t = 1; t <= DRIVE_TICKS; t++) {
            final int tick = t;
            helper.runAfterDelay(tick, () -> {
                if (finished[0]) return;
                try {
                    step.accept(tick);
                } catch (RuntimeException e) {
                    finished[0] = true;
                    cleanup.run();
                    helper.fail(String.valueOf(e.getMessage()));
                }
            });
        }
    }

    /**
     * The flood scenario shared by the single-slab and cascade tests. Phases, all gated on observed state because
     * fluid ticks and chunk promotion lag by a variable number of ticks:
     * <ol start="0">
     * <li>build the dry column with no water anywhere;</li>
     * <li>add the outside sources and wait until every slab cell has been flooded - if they never are, the premise
     *     failed and so does the test;</li>
     * <li>the Builder must go quiet for {@link #QUIET_TICKS} straight ticks with every slab still wet;</li>
     * <li>knock out the LAST cell - a placement the wet slabs, which are inside the box, must not block - and
     *     require it to be rebuilt and the Builder quiet again.</li>
     * </ol>
     */
    private static void runFloodScenario(GameTestHelper helper, int[] column, BlockPos[] walls, BlockPos[] sources) {
        ServerLevel level = helper.getLevel();
        TileBuilder tile = setUpBuilder(helper, column, walls);
        int last = column.length - 1;

        int[] phase = { 0 };
        int[] phaseStart = { 0 };
        int[] quiet = { 0 };
        int[] busyTicks = { 0 };
        int[] slabChanges = { 0 };
        BlockState[] lastFirstSlab = { null };
        boolean[] flooded = new boolean[column.length];
        boolean[] finished = { false };

        drive(helper, finished, t -> {
            SnapshotBuilder<?> builder = tile.getBuilder();
            BlockState[] cells = new BlockState[column.length];
            for (int i = 0; i < column.length; i++) {
                cells[i] = helper.getBlockState(cell(i));
            }
            int inPhase = t - phaseStart[0];
            boolean allBuilt = true;
            boolean allSlabsWet = true;
            for (int i = 0; i < column.length; i++) {
                allBuilt &= isBuilt(column[i], cells[i]);
                if (column[i] == SLAB) {
                    allSlabsWet &= isWetSlab(cells[i]);
                }
            }

            switch (phase[0]) {
                case 0 -> {
                    if (allBuilt && builderQuiet(builder)) {
                        for (BlockPos source : sources) {
                            BlockWaterloggingTester.placeSource(helper, source, Blocks.WATER, Fluids.WATER);
                        }
                        phase[0] = 1;
                        phaseStart[0] = t;
                    } else {
                        expect(inPhase < 300, "phase 0: the Builder never finished the dry build, cells="
                                + Arrays.toString(cells));
                    }
                }
                case 1 -> {
                    // Any fluid at a cell proves it flooded: a pre-fix Builder reacts inside the very game tick the
                    // fluid lands (fluid tick, then block-entity tick), so the wet slab can be gone - torn out,
                    // leaving a legacy water block - before this poll ever sees it.
                    boolean all = true;
                    for (int i = 0; i < column.length; i++) {
                        if (column[i] == SLAB) {
                            flooded[i] |= !level.getFluidState(helper.absolutePos(cell(i))).isEmpty();
                            all &= flooded[i];
                        }
                    }
                    if (all) {
                        phase[0] = 2;
                        phaseStart[0] = t;
                        lastFirstSlab[0] = cells[0];
                    } else {
                        expect(inPhase < 200, "phase 1 (test premise): the outside water sources never flooded "
                                + "every slab, so the re-flood loop cannot be exercised; flooded="
                                + Arrays.toString(flooded) + ", cells=" + Arrays.toString(cells));
                    }
                }
                case 2 -> {
                    if (!cells[0].equals(lastFirstSlab[0])) {
                        slabChanges[0]++;
                        lastFirstSlab[0] = cells[0];
                    }
                    boolean settled = allSlabsWet && builderQuiet(builder);
                    if (builder != null && !builderQuiet(builder)) {
                        busyTicks[0]++;
                    }
                    quiet[0] = settled ? quiet[0] + 1 : 0;
                    if (quiet[0] >= QUIET_TICKS) {
                        // The loop is dead. Now check the wet slabs do not stall the rest of the area.
                        helper.setBlock(cell(last), Blocks.AIR);
                        phase[0] = 3;
                        phaseStart[0] = t;
                        quiet[0] = 0;
                    } else {
                        expect(inPhase < 400, "phase 2: the Builder never settled after the outside water "
                                + "re-flooded the slabs - it kept re-queuing (busy on " + busyTicks[0] + " of "
                                + inPhase + " ticks, first slab changed state " + slabChanges[0] + " times, cells="
                                + Arrays.toString(cells) + ", breakTasks="
                                + (builder == null ? -1 : builder.breakTasks.size()) + ", placeTasks="
                                + (builder == null ? -1 : builder.placeTasks.size()) + ")");
                    }
                }
                case 3 -> {
                    boolean rebuilt = isBuilt(column[last], cells[last]) && allSlabsWet && builderQuiet(builder);
                    quiet[0] = rebuilt ? quiet[0] + 1 : 0;
                    if (quiet[0] >= QUIET_TICKS / 2) {
                        finished[0] = true;
                        helper.succeed();
                    } else {
                        expect(inPhase < 300, "phase 3: the wet slabs inside the build area blocked the CLEAR "
                                + "placement gate - the last cell was never rebuilt (cells="
                                + Arrays.toString(cells) + ", leftToPlace="
                                + (builder == null ? -1 : builder.leftToPlace) + ")");
                    }
                }
                default -> throw new IllegalStateException("bad phase " + phase[0]);
            }
        });
    }

    /**
     * CLEAR mode, a dry slab flanked by two OUTSIDE water sources: the Builder must settle (no endless break/place
     * churn) with the slab left waterlogged, and the rest of the area must still build afterwards (the wet slab
     * must not hold the CLEAR "mop the fluid first" gate shut for every other block).
     *
     * <p>Column (z = 2..4): slab, stone, stone. Sources: {@code (1,2,2)} west of the slab and {@code (2,2,1)} north
     * of it, both outside the one-wide box.
     */
    public static void clearModeToleratesOutsideFedWaterlogging(GameTestHelper helper) {
        runFloodScenario(helper,
                new int[] { SLAB, STONE, STONE },
                new BlockPos[] {
                    new BlockPos(0, 2, 2), new BlockPos(1, 2, 1), new BlockPos(1, 2, 3), // around the west source
                    new BlockPos(2, 2, 0), new BlockPos(3, 2, 1) },                      // around the north source
                new BlockPos[] { new BlockPos(1, 2, 2), new BlockPos(2, 2, 1) });
    }

    /**
     * The cascade: a second slab touches the first INSIDE the box and has just ONE outside source of its own. On
     * its own that source cannot flood it, but together with the first slab's tolerated (permanent) water it does,
     * so the second slab must be tolerated too, or a row of blocks along a shore would settle one block and loop on
     * the next.
     *
     * <p>Column (z = 2..5): slab, slab, stone, stone. Sources: {@code (1,2,2)} and {@code (2,2,1)} flood the first
     * slab; {@code (1,2,3)} plus the first slab's water flood the second.
     */
    public static void clearModeToleratesCascadingWaterlogging(GameTestHelper helper) {
        runFloodScenario(helper,
                new int[] { SLAB, SLAB, STONE, STONE },
                new BlockPos[] {
                    new BlockPos(0, 2, 2), new BlockPos(1, 2, 1), new BlockPos(2, 2, 0), new BlockPos(3, 2, 1),
                    new BlockPos(0, 2, 3), new BlockPos(1, 2, 4), new BlockPos(3, 2, 3) },
                new BlockPos[] { new BlockPos(1, 2, 2), new BlockPos(2, 2, 1), new BlockPos(1, 2, 3) });
    }

    /**
     * A tolerated wet slab is itself a water source, so it spreads flowing water into an in-box schematic-AIR cell
     * beside it. Mopping that water is futile (the slab feeds it again within a fluid tick, and the mop's neighbour
     * update reschedules the slab's water), so before the follow-up fix the Builder mopped that cell for ever.
     * Flowing water fed only by water the Builder cannot remove must be tolerated like the slab.
     *
     * <p>Column (z = 2..4): slab, air, stone. Sources as in the single-slab test; the air cell is walled in at
     * {@code (1,2,3)} and {@code (3,2,3)}, so the slab is its only feeder.
     */
    public static void clearModeToleratesWaterSpreadIntoAirFromToleratedBlock(GameTestHelper helper) {
        runFloodScenario(helper,
                new int[] { SLAB, AIR, STONE },
                new BlockPos[] {
                    new BlockPos(0, 2, 2), new BlockPos(1, 2, 1), new BlockPos(1, 2, 3),
                    new BlockPos(2, 2, 0), new BlockPos(3, 2, 1), new BlockPos(3, 2, 3) },
                new BlockPos[] { new BlockPos(1, 2, 2), new BlockPos(2, 2, 1) });
    }

    /**
     * The air cell between a tolerated slab and an outside source has two water-source neighbours over a stone
     * floor, so vanilla's infinite-water rule turns it into a SOURCE. Mopping it is futile for the same reason as
     * drying the slab; it must be tolerated too.
     *
     * <p>Column (z = 2..5): slab, air, stone, stone. Sources {@code (1,2,2)} and {@code (2,2,1)} flood the slab;
     * {@code (1,2,3)} plus the slab's water make the air cell a source.
     */
    public static void clearModeToleratesInfiniteSourceInAirCell(GameTestHelper helper) {
        runFloodScenario(helper,
                new int[] { SLAB, AIR, STONE, STONE },
                new BlockPos[] {
                    new BlockPos(0, 2, 2), new BlockPos(1, 2, 1), new BlockPos(2, 2, 0), new BlockPos(3, 2, 1),
                    new BlockPos(0, 2, 3), new BlockPos(1, 2, 4), new BlockPos(3, 2, 3) },
                new BlockPos[] { new BlockPos(1, 2, 2), new BlockPos(2, 2, 1), new BlockPos(1, 2, 3) });
    }

    /**
     * Tolerated water must not hold back a fragile block that no fluid touches. The snow layer at the end of the
     * column has no fluid beside or above it, yet before the follow-up fix any tolerated water anywhere in the box
     * kept every fragile placement deferred, so once knocked out it was never rebuilt and the build never finished.
     *
     * <p>Column (z = 2..4): slab, stone, snow layer, on the single-slab geometry.
     */
    public static void clearModeToleratedWaterDoesNotBlockDistantFragileBlocks(GameTestHelper helper) {
        runFloodScenario(helper,
                new int[] { SLAB, STONE, SNOW },
                new BlockPos[] {
                    new BlockPos(0, 2, 2), new BlockPos(1, 2, 1), new BlockPos(1, 2, 3),
                    new BlockPos(2, 2, 0), new BlockPos(3, 2, 1) },
                new BlockPos[] { new BlockPos(1, 2, 2), new BlockPos(2, 2, 1) });
    }

    /**
     * The fragile-acceptance scenario: build {@code column} dry, add the outside water {@code sources}, then require
     * the Builder to rest for {@link #QUIET_TICKS} straight ticks with {@code settled} holding for the cells. The
     * fragile block at {@code fragile} can never stand once the sources are in. Every window is phase-relative and
     * gated on observed state.
     */
    private static void runFragileAcceptScenario(GameTestHelper helper, int[] column, BlockPos[] walls,
            BlockPos[] sources, int fragile, Predicate<BlockState[]> settled, String what) {
        TileBuilder tile = setUpBuilder(helper, column, walls);
        int[] phase = { 0 };
        int[] phaseStart = { 0 };
        int[] quiet = { 0 };
        int[] busyTicks = { 0 };
        boolean[] finished = { false };
        Runnable discardDrops = () -> {
            for (var drop : EntityArenaUtil.droppedItems(helper, cell(fragile), 3.0)) {
                drop.discard();
            }
        };
        drive(helper, finished, t -> {
            SnapshotBuilder<?> builder = tile.getBuilder();
            BlockState[] cells = new BlockState[column.length];
            boolean allBuilt = true;
            for (int i = 0; i < column.length; i++) {
                cells[i] = helper.getBlockState(cell(i));
                allBuilt &= isBuilt(column[i], cells[i]);
            }
            int inPhase = t - phaseStart[0];
            switch (phase[0]) {
                case 0 -> {
                    if (allBuilt && builderQuiet(builder)) {
                        for (BlockPos source : sources) {
                            BlockWaterloggingTester.placeSource(helper, source, Blocks.WATER, Fluids.WATER);
                        }
                        phase[0] = 1;
                        phaseStart[0] = t;
                    } else {
                        expect(inPhase < 300, "phase 0: the Builder never finished the dry build, cells="
                                + Arrays.toString(cells));
                    }
                }
                case 1 -> {
                    if (builder != null && !builderQuiet(builder)) {
                        busyTicks[0]++;
                    }
                    quiet[0] = settled.test(cells) && builderQuiet(builder) ? quiet[0] + 1 : 0;
                    if (quiet[0] >= QUIET_TICKS) {
                        discardDrops.run();
                        finished[0] = true;
                        helper.succeed();
                    } else {
                        expect(inPhase < 400, "phase 1: the Builder never rested with " + what + " (busy on "
                                + busyTicks[0] + " of " + inPhase + " ticks, cells=" + Arrays.toString(cells)
                                + ", leftToPlace=" + (builder == null ? -1 : builder.leftToPlace) + ", breakTasks="
                                + (builder == null ? -1 : builder.breakTasks.size()) + ", placeTasks="
                                + (builder == null ? -1 : builder.placeTasks.size()) + ")");
                    }
                }
                default -> throw new IllegalStateException("bad phase " + phase[0]);
            }
        }, discardDrops);
    }

    /**
     * A fragile block whose own cell touches water the Builder can never remove can never be built: the tolerated
     * wet slab beside it spreads water into the cell and washes the snow layer away. Before this was handled the
     * Builder never queued the placement (the fragile gate saw the water) yet never counted the cell as done, so
     * the build reported busy for ever. Such a cell must be accepted as unbuildable and the Builder must rest.
     *
     * <p>Column (z = 2..3): slab, snow layer, on the single-slab geometry, with the snow cell walled in at
     * {@code (1,2,3)}, {@code (3,2,3)} and {@code (2,2,4)} so the slab's water stays in it.
     */
    public static void clearModeAcceptsFragileBlockBesideToleratedWater(GameTestHelper helper) {
        runFragileAcceptScenario(helper,
                new int[] { SLAB, SNOW },
                new BlockPos[] {
                    new BlockPos(0, 2, 2), new BlockPos(1, 2, 1), new BlockPos(1, 2, 3),
                    new BlockPos(2, 2, 0), new BlockPos(3, 2, 1), new BlockPos(3, 2, 3), new BlockPos(2, 2, 4) },
                new BlockPos[] { new BlockPos(1, 2, 2), new BlockPos(2, 2, 1) },
                1, cells -> isWetSlab(cells[0]) && !cells[1].is(Blocks.SNOW),
                "a fragile block beside permanent water");
    }

    /**
     * A fragile cell flanked by two OUTSIDE water sources over a stone floor does not end up holding flowing water:
     * vanilla's infinite-water rule makes it a water SOURCE. Mopping that source is futile (the same two sources
     * re-make it within a fluid tick) and the snow layer can never stand there, so the cell must be accepted like a
     * fragile cell holding flowing water. Before the fix only air or flowing water counted as "nothing to break",
     * so the Builder mopped the re-made source for ever.
     *
     * <p>Column (z = 2..4): slab, snow layer, slab. The slabs keep the water in the column (a dry slab cannot take
     * flowing water, and with one source beside it it is never waterlogged, so both stay dry). Sources
     * {@code (1,2,3)} and {@code (3,2,3)} flank the snow cell, each walled in on every side but the column's (the
     * Builder itself closes the east source's north side).
     */
    public static void clearModeAcceptsFragileCellResourcedByOutsideWater(GameTestHelper helper) {
        runFragileAcceptScenario(helper,
                new int[] { SLAB, SNOW, SLAB },
                new BlockPos[] {
                    new BlockPos(0, 2, 3), new BlockPos(1, 2, 2), new BlockPos(1, 2, 4), // around the west source
                    new BlockPos(4, 2, 3), new BlockPos(3, 2, 4) },                      // around the east source
                new BlockPos[] { new BlockPos(1, 2, 3), new BlockPos(3, 2, 3) },
                1, cells -> isDrySlab(cells[0]) && isDrySlab(cells[2]) && isWaterSource(cells[1]),
                "a fragile cell re-made into a water source by outside water");
    }

    /**
     * The fragile-cell acceptance is water-only. Outside-fed flowing LAVA in a fragile cell is not water the
     * Builder may leave alone: it keeps the plain CLEAR behaviour (the lava in the cell is mopped and the area is
     * never reported built). Before the fix any fluid outside the box counted as permanent, so the cell was silently
     * accepted with lava in it.
     *
     * <p>Column (z = 2..3): snow layer, stone. A lava source at {@code (1,2,2)}, walled in on every side but the
     * column's, and {@code (2,2,1)} walled so the lava it feeds into the snow cell stays there.
     *
     * <p>This is a negative check, so it needs a bounded window: once the lava is seen to have washed the snow layer
     * away (observed state, not a fixed tick), the Builder must not rest for {@link #QUIET_TICKS} straight ticks
     * within the next {@link #LAVA_WATCH_TICKS}, several lava flow cycles. A pre-fix Builder rests within a few
     * dozen ticks with the lava in the cell. The lava itself cannot be the gate: post-fix the Builder mops it before
     * any poll sees it, and the cell then stays empty with the snow layer still to place (it cannot stand beside the
     * lava source), so the Builder reports the area unfinished.
     */
    public static void clearModeKeepsMoppingOutsideLavaInFragileCell(GameTestHelper helper) {
        int[] column = { SNOW, STONE };
        BlockPos lavaSource = new BlockPos(1, 2, 2);
        TileBuilder tile = setUpBuilder(helper, column, new BlockPos[] {
            new BlockPos(0, 2, 2), new BlockPos(1, 2, 1), new BlockPos(1, 2, 3), new BlockPos(2, 2, 1) });
        ServerLevel level = helper.getLevel();

        int[] phase = { 0 };
        int[] phaseStart = { 0 };
        int[] quiet = { 0 };
        int[] lavaSeen = { 0 };
        int[] busyTicks = { 0 };
        boolean[] finished = { false };
        // Never leave lava behind: its random ticks can start fires, and the next test in this cell expects a void.
        Runnable clearLava = () -> {
            helper.setBlock(lavaSource, Blocks.STONE);
            helper.setBlock(cell(0), Blocks.AIR);
        };
        drive(helper, finished, t -> {
            SnapshotBuilder<?> builder = tile.getBuilder();
            BlockState snowCell = helper.getBlockState(cell(0));
            BlockState stoneCell = helper.getBlockState(cell(1));
            boolean lava = level.getFluidState(helper.absolutePos(cell(0))).is(FluidTags.LAVA);
            int inPhase = t - phaseStart[0];
            switch (phase[0]) {
                case 0 -> {
                    if (snowCell.is(Blocks.SNOW) && stoneCell.is(Blocks.STONE) && builderQuiet(builder)) {
                        BlockWaterloggingTester.placeSource(helper, lavaSource, Blocks.LAVA, Fluids.LAVA);
                        phase[0] = 1;
                        phaseStart[0] = t;
                    } else {
                        expect(inPhase < 300, "phase 0: the Builder never finished the dry build, snow cell="
                                + snowCell + ", stone cell=" + stoneCell);
                    }
                }
                case 1 -> {
                    if (!snowCell.is(Blocks.SNOW)) {
                        phase[0] = 2;
                        phaseStart[0] = t;
                    } else {
                        expect(inPhase < 300, "phase 1 (test premise): the outside lava never washed the snow "
                                + "layer away");
                    }
                }
                case 2 -> {
                    if (lava) {
                        lavaSeen[0]++;
                    }
                    boolean quietNow = builderQuiet(builder);
                    if (!quietNow) {
                        busyTicks[0]++;
                    }
                    quiet[0] = quietNow ? quiet[0] + 1 : 0;
                    expect(quiet[0] < QUIET_TICKS, "the Builder rested with outside-fed lava washing a fragile "
                            + "cell, accepting the cell as built (cell=" + snowCell + ", lava seen in the cell on "
                            + lavaSeen[0] + " and the Builder busy on " + busyTicks[0] + " of " + inPhase + " ticks)");
                    expect(!snowCell.is(Blocks.SNOW), "sanity: a snow layer cannot stand beside the lava, cell="
                            + snowCell);
                    if (inPhase >= LAVA_WATCH_TICKS) {
                        clearLava.run();
                        finished[0] = true;
                        helper.succeed();
                    }
                }
                default -> throw new IllegalStateException("bad phase " + phase[0]);
            }
        }, clearLava);
    }

    /**
     * Drives a single-condition test in two phase-relative windows: up to 300 ticks for {@code rested} to first
     * hold, then up to 200 more for it to hold {@code quietNeeded} ticks in a row (a broken streak restarts the
     * count but not the window).
     */
    private static void driveUntilRested(GameTestHelper helper, BooleanSupplier rested, int quietNeeded,
            Supplier<String> failure) {
        int[] phase = { 0 };
        int[] phaseStart = { 0 };
        int[] quiet = { 0 };
        boolean[] finished = { false };
        drive(helper, finished, t -> {
            int inPhase = t - phaseStart[0];
            boolean now = rested.getAsBoolean();
            switch (phase[0]) {
                case 0 -> {
                    if (now) {
                        phase[0] = 1;
                        phaseStart[0] = t;
                        quiet[0] = 1;
                    } else {
                        expect(inPhase < 300, "phase 0: " + failure.get());
                    }
                }
                case 1 -> quiet[0] = now ? quiet[0] + 1 : 0;
                default -> throw new IllegalStateException("bad phase " + phase[0]);
            }
            if (phase[0] == 1) {
                if (quiet[0] >= quietNeeded) {
                    finished[0] = true;
                    helper.succeed();
                } else {
                    expect(t - phaseStart[0] < 200, "phase 1 (never stayed rested): " + failure.get());
                }
            }
        });
    }

    /**
     * Vanilla re-sources a cell between two water sources only over a solid or water-source floor (the
     * infinite-water rule in {@code FlowingFluid#getNewLiquid}). A waterlogged slab with AIR below it is therefore
     * never re-flooded once dried, however many outside sources flank it, and CLEAR must dry it rather than
     * tolerate the water.
     *
     * <p>Column (z = 2..4): slab, stone, stone, on the single-slab geometry with the stone already standing, the
     * floor under the slab removed, and the slab waterlogged before the Builder starts.
     */
    public static void clearModeDriesFlankedBlockWithNoFloor(GameTestHelper helper) {
        int[] column = { SLAB, STONE, STONE };
        TileBuilder tile = setUpBuilder(helper, column, new BlockPos[] {
            new BlockPos(0, 2, 2), new BlockPos(1, 2, 1), new BlockPos(1, 2, 3),
            new BlockPos(2, 2, 0), new BlockPos(3, 2, 1), cell(1), cell(2) });
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.AIR);
        helper.setBlock(cell(0), Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true));
        BlockWaterloggingTester.placeSource(helper, new BlockPos(1, 2, 2), Blocks.WATER, Fluids.WATER);
        BlockWaterloggingTester.placeSource(helper, new BlockPos(2, 2, 1), Blocks.WATER, Fluids.WATER);

        driveUntilRested(helper,
                () -> isDrySlab(helper.getBlockState(cell(0))) && builderQuiet(tile.getBuilder()),
                QUIET_TICKS,
                () -> "a flanked waterlogged slab with no floor is never re-flooded, so CLEAR must dry it (slab="
                        + helper.getBlockState(cell(0)) + ", quiet=" + builderQuiet(tile.getBuilder()) + ")");
    }

    /**
     * The control: with NO outside water the strict CLEAR rule must still apply. A slab someone waterlogged with a
     * bucket, where the blueprint wants it dry, has to be dried (torn out and re-placed dry), and the Builder must
     * then rest. Guards against the tolerance above being too broad; passes with and without the fix.
     *
     * <p>Column (z = 2..4): slab, stone, stone, with the stone already standing and stone walls where the other
     * tests have their sources.
     */
    public static void clearModeStillDriesBucketWaterloggedBlock(GameTestHelper helper) {
        int[] column = { SLAB, STONE, STONE };
        TileBuilder tile = setUpBuilder(helper, column, new BlockPos[] {
            new BlockPos(0, 2, 2), new BlockPos(1, 2, 1), new BlockPos(1, 2, 2), new BlockPos(1, 2, 3),
            new BlockPos(2, 2, 0), new BlockPos(2, 2, 1), new BlockPos(3, 2, 1),
            cell(1), cell(2) });
        helper.setBlock(cell(0), Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true));

        driveUntilRested(helper,
                () -> isDrySlab(helper.getBlockState(cell(0))) && helper.getBlockState(cell(1)).is(Blocks.STONE)
                        && helper.getBlockState(cell(2)).is(Blocks.STONE) && builderQuiet(tile.getBuilder()),
                QUIET_TICKS / 2,
                () -> "a bucket-waterlogged slab with no outside water must still be dried under CLEAR (slab="
                        + helper.getBlockState(cell(0)) + ", quiet=" + builderQuiet(tile.getBuilder()) + ")");
    }
}
