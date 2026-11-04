/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.energy;

import java.util.List;
import java.util.function.BooleanSupplier;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import buildcraft.api.fluids.BCFluidStack;
import buildcraft.energy.BCEnergyConfig.ContainedDetonation;
import buildcraft.factory.BCFactoryBlocks;
import buildcraft.factory.tile.TileTank;
import buildcraft.lib.test.EntityArenaUtil;

/**
 * Game test for gas stored in a tank exploding ({@link ContainedFluidDetonation}, issue #61): lava beside a tank of
 * gaseous fuel sets it off only once {@code containedFluidDetonation} is on and, in {@code PER_BLOCK}, only from a
 * bucket up; a blast that destroys a tank of gas sets that tank off too.
 *
 * <p>Runs alone (its own {@code test_environment}): it changes the fire configs for a while.
 */
public class ContainedFluidDetonationTester {

    /** The work budget of the poll, counted from the cell ticking; the manifest's {@code max_ticks} stays above
     *  {@code CELL_READY_TICKS} plus this. */
    private static final int WORK_TICKS = 200;

    /** How long a tank that must not go off is watched: two and a half ignition checks. */
    private static final int HOLD_TICKS = 25;

    /** The power of the outside blast that breaks the second tank: enough to break a tank one block away, too weak to
     *  reach {@link #GLASS}. */
    private static final float SPARK_POWER = 1.5F;

    // Layout, in the test's own 6x7 cell: solid obsidian (x 0..5, y 1..3, z 0..6) with two sealed pockets at y=2.
    //   - pocket 1: lava beside tank A (the fire trigger);
    //   - pocket 2 (x 1..4, z 3..5, obsidian on every side, the cell's edge row z=6 included): tank B, the spot of an outside blast beside it, and glass that the outside blast
    //     cannot reach but B's own blast does, which is how B going off is seen.
    private static final BlockPos LAVA = new BlockPos(1, 2, 1);
    private static final BlockPos TANK_A = new BlockPos(2, 2, 1);
    private static final BlockPos SPARK = new BlockPos(1, 2, 3);
    private static final BlockPos TANK_B = new BlockPos(2, 2, 3);
    private static final BlockPos GLASS = new BlockPos(4, 2, 5);

    public static void testTankOfGasDetonates(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BCFluidStack gas = new BCFluidStack(entry("fuel_gaseous").source().get(), 2_000);
        boolean canBurnBefore = BCEnergyConfig.canBurnEnabled();
        boolean gasExplodesBefore = BCEnergyConfig.gasExplodesEnabled();
        ContainedDetonation modeBefore = BCEnergyConfig.containedFluidDetonation.get();

        BCEnergyConfig.canBurn.set(true);
        BCEnergyConfig.gasExplodes.set(true);
        BCEnergyConfig.containedFluidDetonation.set(ContainedDetonation.OFF);
        EntityArenaUtil.forceLoadCell(helper);
        int[] stage = { 0 };
        int[] stageStart = { 0 };
        int[] polls = { 0 };
        BooleanSupplier machine = () -> {
            polls[0]++;
            switch (stage[0]) {
                case 0 -> {
                    build(helper);
                    tank(helper, TANK_A).tank.setFluidStack(0, gas.copy());
                    tank(helper, TANK_B).tank.setFluidStack(0, gas.copy());
                    helper.setBlock(LAVA, Blocks.LAVA);
                    advance(stage, stageStart, polls);
                }
                case 1 -> {
                    if (polls[0] - stageStart[0] >= HOLD_TICKS) {
                        require(amountIn(helper, TANK_A) == 2_000, "a tank of gas beside lava went off while OFF");
                        BCEnergyConfig.containedFluidDetonation.set(ContainedDetonation.PER_BLOCK);
                        tank(helper, TANK_A).tank.setFluidStack(0, gas.copyWithAmount(999));
                        advance(stage, stageStart, polls);
                    }
                }
                case 2 -> {
                    if (polls[0] - stageStart[0] >= HOLD_TICKS) {
                        require(amountIn(helper, TANK_A) == 999,
                            "PER_BLOCK set off a tank holding less than a bucket of gas");
                        tank(helper, TANK_A).tank.setFluidStack(0, gas.copy());
                        advance(stage, stageStart, polls);
                    }
                }
                case 3 -> {
                    if (!helper.getBlockState(TANK_A).is(BCFactoryBlocks.TANK.get())) {
                        require(helper.getBlockState(GLASS).is(Blocks.GLASS), "tank A's blast reached pocket 2");
                        BlockPos spark = helper.absolutePos(SPARK);
                        // A blast that breaks nothing (a wind charge's) must leave tank B alone ...
                        level.explode(null, spark.getX() + 0.5, spark.getY() + 0.5, spark.getZ() + 0.5, SPARK_POWER,
                            false, Level.ExplosionInteraction.TRIGGER);
                        require(amountIn(helper, TANK_B) == 2_000, "a blast that breaks no blocks set off tank B");
                        // ... and one that breaks it sets it off
                        level.explode(null, spark.getX() + 0.5, spark.getY() + 0.5, spark.getZ() + 0.5, SPARK_POWER,
                            false, Level.ExplosionInteraction.BLOCK);
                        require(!helper.getBlockState(TANK_B).is(BCFactoryBlocks.TANK.get()),
                            "the outside blast did not break tank B");
                        require(helper.getBlockState(GLASS).is(Blocks.GLASS),
                            "the outside blast reached the glass by itself");
                        advance(stage, stageStart, polls);
                    }
                }
                default -> {
                    // Only tank B's own blast, a few ticks later, reaches the glass
                    return !helper.getBlockState(GLASS).is(Blocks.GLASS);
                }
            }
            return false;
        };
        EntityArenaUtil.tickUntil(helper, EntityArenaUtil.CELL_READY_TICKS + WORK_TICKS,
            EntityArenaUtil.whenCellTicking(helper, EntityArenaUtil.CELL_READY_TICKS, WORK_TICKS, machine,
                () -> "stuck in stage " + stage[0] + " (0 build, 1 OFF holds, 2 under a bucket holds,"
                    + " 3 lava sets tank A off, 4 the blast that broke tank B sets it off)"),
            helper::succeed,
            "the tanks never finished detonating",
            () -> {
                BCEnergyConfig.canBurn.set(canBurnBefore);
                BCEnergyConfig.gasExplodes.set(gasExplodesBefore);
                BCEnergyConfig.containedFluidDetonation.set(modeBefore);
                // Leave no lava, gas, fire or obsidian behind in the shared world, and no gas to spill doing it
                if (stage[0] > 0) {
                    for (BlockPos rel : List.of(TANK_A, TANK_B)) {
                        if (level.getBlockEntity(helper.absolutePos(rel)) instanceof TileTank tank) {
                            tank.clearContent();
                        }
                    }
                    fill(helper, 0, 1, 0, 5, 3, 6, Blocks.AIR);
                }
                // The blasts drop the tanks they break as items
                for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, EntityArenaUtil.cellBounds(helper))) {
                    item.discard();
                }
            });
    }

    private static void build(GameTestHelper helper) {
        fill(helper, 0, 1, 0, 5, 3, 6, Blocks.OBSIDIAN);
        helper.setBlock(LAVA, Blocks.AIR);
        fill(helper, 1, 2, 3, 4, 2, 5, Blocks.AIR);
        helper.setBlock(GLASS, Blocks.GLASS);
        helper.setBlock(TANK_A, BCFactoryBlocks.TANK.get());
        helper.setBlock(TANK_B, BCFactoryBlocks.TANK.get());
    }

    private static void fill(GameTestHelper helper, int x0, int y0, int z0, int x1, int y1, int z1,
                             net.minecraft.world.level.block.Block block) {
        for (BlockPos rel : BlockPos.betweenClosed(x0, y0, z0, x1, y1, z1)) {
            helper.setBlock(rel.immutable(), block);
        }
    }

    private static TileTank tank(GameTestHelper helper, BlockPos rel) {
        if (helper.getLevel().getBlockEntity(helper.absolutePos(rel)) instanceof TileTank tank) {
            return tank;
        }
        throw new IllegalStateException("no tank at " + rel);
    }

    private static int amountIn(GameTestHelper helper, BlockPos rel) {
        return helper.getLevel().getBlockEntity(helper.absolutePos(rel)) instanceof TileTank tank
            ? tank.tank.getAmountMb(0) : -1;
    }

    private static void advance(int[] stage, int[] stageStart, int[] polls) {
        stage[0]++;
        stageStart[0] = polls[0];
    }

    private static BCEnergyFluids.FluidEntry entry(String name) {
        return BCEnergyFluids.ALL.stream().filter(e -> e.name().equals(name)).findFirst()
            .orElseThrow(() -> new IllegalStateException("no BuildCraft energy fluid named " + name));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
