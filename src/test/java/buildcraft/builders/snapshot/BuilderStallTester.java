/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.snapshot;

import static buildcraft.lib.test.GameTestUtil.check;

import java.util.List;
import java.util.function.BooleanSupplier;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import buildcraft.builders.BuilderRefloodTester;
import buildcraft.builders.tile.TileBuilder;
import buildcraft.lib.test.EntityArenaUtil;

/**
 * A Builder that has stopped getting on with its build says which cells it is skipping and why, so a stalled build is
 * legible in the world: red for what the player has to change (a block that cannot stand, one that cannot be broken),
 * yellow for supplies. These drive a real {@link TileBuilder} through {@link BlueprintBuilder}'s tick loop and read
 * what the server-side diagnosis ({@link SnapshotBuilder#getStalledCells()}) says once it has gone quiet.
 *
 * <p>Layout: the shared one-wide blueprint of {@link BuilderRefloodTester} (a column along +Z from {@code (2, 2, 2)},
 * as many rows high as the test gives it), with the Builder at {@link BuilderRefloodTester#BUILDER}. Excavation is on
 * (the Builder's default).
 */
public class BuilderStallTester {

    /** Ticks the Builder gets once its cell is entity-ticking: the build itself plus the quiet spell
     *  ({@link #QUIET_TICKS}) the diagnosis is read after. */
    private static final int STALL_WORK_TICKS = 400;
    private static final int STALL_POLL_TICKS = EntityArenaUtil.CELL_READY_TICKS + STALL_WORK_TICKS;

    /** Consecutive ticks the Builder must sit with nothing queued before the diagnosis is read: the stall threshold,
     *  two re-diagnoses (so a diagnosis taken while the first checks were still sweeping is replaced) and a margin. */
    private static final int QUIET_TICKS = SnapshotBuilder.STALL_TICKS + 2 * SnapshotBuilder.STALL_DIAGNOSE_INTERVAL
            + 10;

    private static final int STONE = BuilderRefloodTester.STONE;
    private static final int AIR = BuilderRefloodTester.AIR;
    private static final int TORCH = BuilderRefloodTester.TORCH;

    /** The Builder's blueprint builder (the only one these blueprints load into). */
    private static SnapshotBuilder<?> builderOf(TileBuilder tile) {
        SnapshotBuilder<?> builder = tile.getBuilder();
        check(builder instanceof BlueprintBuilder, "precondition: the Builder runs a blueprint, builder=" + builder);
        return builder;
    }

    /** True once {@code built} has held and the Builder has had nothing queued for {@link #QUIET_TICKS} ticks in a
     *  row: the diagnosis has then had every chance to run, whatever it says. */
    private static BooleanSupplier builtAndQuiet(TileBuilder tile, BooleanSupplier built) {
        int[] quiet = { 0 };
        return () -> {
            SnapshotBuilder<?> builder = builderOf(tile);
            boolean idle = built.getAsBoolean() && builder.breakTasks.isEmpty() && builder.placeTasks.isEmpty();
            quiet[0] = idle ? quiet[0] + 1 : 0;
            return quiet[0] >= QUIET_TICKS;
        };
    }

    private static String describe(SnapshotBuilder<?> builder) {
        return builder.getStalledCells() + " (counts: " + counts(builder) + ")";
    }

    private static String counts(SnapshotBuilder<?> builder) {
        StringBuilder out = new StringBuilder();
        for (EnumStallReason reason : EnumStallReason.values()) {
            out.append(reason).append('=').append(builder.getStalledCount(reason)).append(' ');
        }
        return out.toString().trim();
    }

    /**
     * Stone, air, air, and a torch whose support cell is air in the blueprint too (and in the world): the stone is
     * built, the torch can never stand, so the Builder never queues it. The diagnosis must name exactly the torch's
     * cell, as {@link EnumStallReason#CANT_STAND}, with nothing else red; the stone it built and the air cells it left
     * alone are not stalled. What the client is sent (the synced form of the same list) must read back the same.
     */
    public static void blueprintBuilderReportsAFloatingTorchAsCantStand(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // layers[y][z]: row 0 is stone then air, row 1 is air then the torch: the torch's support, (2,2,3), is air.
        int[][] layers = { { STONE, AIR }, { AIR, TORCH } };
        TileBuilder tile = BuilderRefloodTester.setUpBuilder(helper, layers, new BlockPos[0]);
        tile.setResource(3, new ItemStack(Items.TORCH, 4));
        BlockPos stone = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos torch = helper.absolutePos(new BlockPos(2, 3, 3));

        BooleanSupplier settled = builtAndQuiet(tile, () -> level.getBlockState(stone).is(Blocks.STONE));
        EntityArenaUtil.tickUntil(helper, STALL_POLL_TICKS,
                EntityArenaUtil.whenCellTicking(helper, EntityArenaUtil.CELL_READY_TICKS, STALL_WORK_TICKS, settled,
                        () -> "the stone was never built and left quiet, stone=" + level.getBlockState(stone)
                                + " torch=" + level.getBlockState(torch)),
                () -> {
                    SnapshotBuilder<?> builder = builderOf(tile);
                    check(level.getBlockState(torch).isAir(), "the torch cannot stand, so it must not be placed: "
                            + level.getBlockState(torch));
                    List<StalledCell> cells = builder.getStalledCells();
                    check(cells.equals(List.of(new StalledCell(torch, EnumStallReason.CANT_STAND))),
                            "the diagnosis must name the floating torch's cell as CANT_STAND and nothing else, but "
                                    + "reported " + describe(builder) + " (expected the torch at " + torch + ")");
                    check(builder.getStalledCount(EnumStallReason.CANT_STAND) == 1, "one cell cannot stand: "
                            + counts(builder));
                    for (EnumStallReason reason : EnumStallReason.values()) {
                        if (reason != EnumStallReason.CANT_STAND) {
                            check(builder.getStalledCount(reason) == 0, "nothing else is stalled: " + counts(builder));
                        }
                    }

                    // The synced form: a second builder, given only what the server sends a client, reads it back.
                    CompoundTag sent = builder.serializeClientNBT();
                    BlueprintBuilder client = new BlueprintBuilder(tile);
                    client.loadClientNBT(sent, List.of(), List.of());
                    check(client.getStalledCells().equals(cells), "the client must read back the server's list: "
                            + describe(client));
                    check(client.getStalledCount(EnumStallReason.CANT_STAND) == 1, "the client must read back the "
                            + "server's totals: " + counts(client));
                    // A later sync with nothing stalled empties the client's list.
                    client.loadClientNBT(new CompoundTag(), List.of(), List.of());
                    check(client.getStalledCells().isEmpty() && client.getStalledCount(EnumStallReason.CANT_STAND) == 0,
                            "a sync that carries no stalled cells must clear the client's list: " + describe(client));
                    helper.succeed();
                },
                "the floating torch is diagnosed once the Builder has gone quiet");
    }

    /**
     * Stone, bedrock (the blueprint wants air there), stone, with excavation on. The bedrock can never be broken; it
     * must not hold the whole build up: both stone cells are placed all the same (before the fix a break task for the
     * bedrock was queued, and drained as impossible, on every tick, and a placement waits for an empty break queue,
     * so nothing was ever placed). The bedrock stays, and the diagnosis names its cell as
     * {@link EnumStallReason#UNBREAKABLE}.
     */
    public static void blueprintBuilderPlacesAroundUnbreakableBlockAndReportsIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int[][] layers = { { STONE, AIR, STONE } };
        TileBuilder tile = BuilderRefloodTester.setUpBuilder(helper, layers, new BlockPos[0]);
        helper.setBlock(BuilderRefloodTester.cell(1), Blocks.BEDROCK);
        check(tile.canExcavate(), "precondition: excavation is on");
        BlockPos first = helper.absolutePos(BuilderRefloodTester.cell(0));
        BlockPos bedrock = helper.absolutePos(BuilderRefloodTester.cell(1));
        BlockPos third = helper.absolutePos(BuilderRefloodTester.cell(2));

        BooleanSupplier settled = builtAndQuiet(tile, () -> level.getBlockState(first).is(Blocks.STONE)
                && level.getBlockState(third).is(Blocks.STONE));
        EntityArenaUtil.tickUntil(helper, STALL_POLL_TICKS,
                EntityArenaUtil.whenCellTicking(helper, EntityArenaUtil.CELL_READY_TICKS, STALL_WORK_TICKS, settled,
                        () -> "the stone cells were never built around the bedrock: first=" + level.getBlockState(first)
                                + " bedrock cell=" + level.getBlockState(bedrock) + " third=" + level.getBlockState(third)
                                + ", breakTasks=" + builderOf(tile).breakTasks.size() + " placeTasks="
                                + builderOf(tile).placeTasks.size()),
                () -> {
                    SnapshotBuilder<?> builder = builderOf(tile);
                    check(level.getBlockState(bedrock).is(Blocks.BEDROCK), "the bedrock stays: "
                            + level.getBlockState(bedrock));
                    check(builder.getStalledCells().equals(List.of(new StalledCell(bedrock, EnumStallReason.UNBREAKABLE))),
                            "the diagnosis must name the bedrock cell as UNBREAKABLE and nothing else, but reported "
                                    + describe(builder) + " (expected the bedrock at " + bedrock + ")");
                    check(builder.getStalledCount(EnumStallReason.UNBREAKABLE) == 1, "one cell is unbreakable: "
                            + counts(builder));
                    check(tile.getBuilder().leftToBreak == 1, "the build is not finished, the bedrock is still owed: "
                            + "leftToBreak=" + tile.getBuilder().leftToBreak);
                    helper.succeed();
                },
                "the stone around the bedrock is built and the bedrock is diagnosed once the Builder has gone quiet");
    }

    /**
     * 69 stone cells with no stone to build them from, a floating torch at the very top, and a second torch standing
     * on a stone cell that is itself unbuilt: the list is cut to {@link SnapshotBuilder#STALL_CELL_CAP}, keeps the red
     * torch (it would sit past the cap in the order the cells are walked) ahead of the yellow cells, and the
     * per-reason totals still count every cell. The cap bounds what a big stalled build puts on the wire every five
     * ticks. The second torch cannot stand either (nothing is under it yet), but it only waits on the stone it stands
     * on, which is ordinary and unreported: a count of two red cells would mean a dependency wait was reported.
     */
    public static void stalledListIsCappedKeepsRedFirstAndCountsEverything(GameTestHelper helper) {
        // 5 wide (the arena cell's z runs 2..6), 15 high: rows 0..12 stone, row 13 stone with a gap, row 14 the torch
        // over the gap.
        int[][] layers = new int[15][5];
        for (int y = 0; y < layers.length; y++) {
            for (int z = 0; z < 5; z++) {
                layers[y][z] = y < 14 ? STONE : AIR;
            }
        }
        layers[13][4] = AIR;
        layers[14][4] = TORCH;
        layers[14][0] = TORCH; // on layers[13][0], stone with none to build it from: only waiting
        int stones = 0;
        for (int[] row : layers) {
            for (int kind : row) {
                if (kind == STONE) {
                    stones++;
                }
            }
        }
        check(stones == 69 && stones > SnapshotBuilder.STALL_CELL_CAP, "premise: more stone cells than the cap, got "
                + stones);
        TileBuilder tile = BuilderRefloodTester.setUpBuilder(helper, layers, new BlockPos[0]);
        tile.setResource(1, ItemStack.EMPTY);
        tile.setResource(3, new ItemStack(Items.TORCH, 4));
        BlockPos torch = helper.absolutePos(new BlockPos(2, 2 + 14, 2 + 4));
        int expectedStones = stones;

        BooleanSupplier settled = builtAndQuiet(tile, () -> true);
        EntityArenaUtil.tickUntil(helper, STALL_POLL_TICKS,
                EntityArenaUtil.whenCellTicking(helper, EntityArenaUtil.CELL_READY_TICKS, STALL_WORK_TICKS, settled,
                        () -> "the Builder never sat quiet for " + QUIET_TICKS + " ticks"),
                () -> {
                    SnapshotBuilder<?> builder = builderOf(tile);
                    List<StalledCell> cells = builder.getStalledCells();
                    check(cells.size() == SnapshotBuilder.STALL_CELL_CAP, "the list is cut to the cap of "
                            + SnapshotBuilder.STALL_CELL_CAP + ", got " + cells.size() + ": " + describe(builder));
                    check(cells.get(0).equals(new StalledCell(torch, EnumStallReason.CANT_STAND)),
                            "the red cell comes first, though it is the last the Builder reaches: " + cells.get(0));
                    for (int i = 1; i < cells.size(); i++) {
                        check(cells.get(i).reason() == EnumStallReason.MISSING_ITEMS,
                                "the rest are the cells with no stone to be built from: " + cells.get(i));
                    }
                    check(builder.getStalledCount(EnumStallReason.MISSING_ITEMS) == expectedStones,
                            "the total counts every cell, not only the listed ones: " + counts(builder));
                    check(builder.getStalledCount(EnumStallReason.CANT_STAND) == 1, "one torch cannot stand, the "
                            + "other only waits for its stone: " + counts(builder));
                    helper.succeed();
                },
                "the stalled list is capped, red first, with full totals");
    }
}
