/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.builders;

import java.util.Arrays;
import java.util.Date;
import java.util.UUID;
import java.util.function.IntConsumer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
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

    private static final int SLAB = 0;
    private static final int STONE = 1;

    private static final BlockPos BUILDER = new BlockPos(3, 2, 2);

    /** Consecutive quiet ticks that count as "the Builder stopped re-queuing". The sources re-flood a dry cell
     *  within ~5 ticks, so any live cycle breaks the streak many times over in this window. */
    private static final int QUIET_TICKS = 40;

    private static void expect(boolean cond, String msg) {
        if (!cond) throw new IllegalStateException(msg);
    }

    private static BlockPos cell(int i) {
        return new BlockPos(2, 2, 2 + i);
    }

    /** One-wide blueprint along +Z; {@code column[i]} is {@link #SLAB} (dry bottom oak slab) or {@link #STONE}. */
    private static ItemStack makeBlueprint(ServerLevel level, BlockPos samplePos, int[] column) {
        BlockState slab = Blocks.OAK_SLAB.defaultBlockState();
        BlockState stone = Blocks.STONE.defaultBlockState();
        ISchematicBlock slabSchematic = SchematicBlockManager.getSchematicBlock(
                new SchematicBlockContext(level, samplePos, samplePos, slab, slab.getBlock()));
        ISchematicBlock stoneSchematic = SchematicBlockManager.getSchematicBlock(
                new SchematicBlockContext(level, samplePos, samplePos, stone, stone.getBlock()));
        expect(slabSchematic != null && stoneSchematic != null, "the default schematic must resolve for slab+stone");

        Blueprint blueprint = new Blueprint();
        blueprint.size = new BlockPos(1, 1, column.length);
        blueprint.facing = Direction.EAST;
        blueprint.offset = BlockPos.ZERO;
        blueprint.palette.add(slabSchematic);   // index SLAB
        blueprint.palette.add(stoneSchematic);  // index STONE
        blueprint.data = new int[column.length];
        for (int z = 0; z < column.length; z++) {
            blueprint.data[blueprint.posToIndex(0, 0, z)] = column[z];
        }
        blueprint.computeKey();
        GlobalSavedDataSnapshots.get(level).addSnapshot(blueprint);

        return BCBuildersItems.BLUEPRINT_USED.get().createUsedStack(
                new Snapshot.Header(blueprint.key, new UUID(0, 0), new Date(0), "reflood-test"));
    }

    private static boolean isWetSlab(BlockState state) {
        return state.is(Blocks.OAK_SLAB) && state.getValue(BlockStateProperties.WATERLOGGED);
    }

    private static boolean isDrySlab(BlockState state) {
        return state.is(Blocks.OAK_SLAB) && !state.getValue(BlockStateProperties.WATERLOGGED);
    }

    /** True while the Builder has nothing queued and nothing left to queue. */
    private static boolean builderQuiet(SnapshotBuilder<?> builder) {
        return builder != null && builder.breakTasks.isEmpty() && builder.placeTasks.isEmpty()
                && builder.leftToBreak == 0 && builder.leftToPlace == 0;
    }

    /** The shared arena: a stone floor, the given walls, and a CLEAR-mode Builder with enough MJ and items to build
     *  {@code column}, loaded with its blueprint. The column itself is NOT placed; the Builder builds it. */
    private static TileBuilder setUpBuilder(GameTestHelper helper, int[] column, BlockPos[] walls) {
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
        tile.cycleFluidMode(); // NO_REPLACE -> REPLACE
        tile.cycleFluidMode(); // REPLACE -> CLEAR
        expect(tile.getFluidMode() == EnumFluidHandlingMode.CLEAR, "sanity: the Builder is in CLEAR mode");
        tile.setSnapshot(blueprintStack);
        return tile;
    }

    /** Schedules {@code step} once per tick up front (see {@link EntityArenaUtil#tickUntil} for why) and turns a
     *  thrown {@link IllegalStateException} into a test failure. {@code step} calls {@code finish} to succeed. */
    private static void drive(GameTestHelper helper, boolean[] finished, IntConsumer step) {
        for (int t = 1; t <= 1000; t++) {
            final int tick = t;
            helper.runAfterDelay(tick, () -> {
                if (finished[0]) return;
                try {
                    step.accept(tick);
                } catch (RuntimeException e) {
                    finished[0] = true;
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
                if (column[i] == SLAB) {
                    allBuilt &= isDrySlab(cells[i]);
                    allSlabsWet &= isWetSlab(cells[i]);
                } else {
                    allBuilt &= cells[i].is(Blocks.STONE);
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
                    boolean rebuilt = cells[last].is(Blocks.STONE) && allSlabsWet && builderQuiet(builder);
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

        int[] quiet = { 0 };
        boolean[] finished = { false };
        drive(helper, finished, t -> {
            BlockState a = helper.getBlockState(cell(0));
            boolean rested = isDrySlab(a) && helper.getBlockState(cell(1)).is(Blocks.STONE)
                    && helper.getBlockState(cell(2)).is(Blocks.STONE) && builderQuiet(tile.getBuilder());
            quiet[0] = rested ? quiet[0] + 1 : 0;
            if (quiet[0] >= QUIET_TICKS / 2) {
                finished[0] = true;
                helper.succeed();
            } else {
                expect(t < 300, "a bucket-waterlogged slab with no outside water must still be dried under "
                        + "CLEAR (slab=" + a + ", quiet=" + builderQuiet(tile.getBuilder()) + ")");
            }
        });
    }
}
