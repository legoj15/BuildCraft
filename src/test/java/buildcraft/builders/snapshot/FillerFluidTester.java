/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.snapshot;

import static buildcraft.lib.test.GameTestUtil.check;

import java.util.BitSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;

import buildcraft.lib.block.BlockWaterloggingTester;
import buildcraft.lib.test.EntityArenaUtil;

/**
 * What a Filler does with fluid standing in a cell its template leaves unset. The Filler never replaces or removes
 * fluid (its fluid mode is No-Replace), so a cell holding nothing but fluid is as clear as the Filler ever makes it:
 * the build must finish with the fluid still there. A real block holding fluid (a waterlogged fence) is still a block
 * the template does not want, so it is excavated and the water it leaves stays.
 *
 * <p>Driven through the same {@link BuilderClaimTester.TestFiller} stand-in as the claim tests, with excavation on
 * (the real Filler's default): the Filler marks itself finished on the first pass {@link SnapshotBuilder#tick()}
 * answers true, and that answer is what is read here.
 */
public class FillerFluidTester {

    /** Real ticks the fillers get to finish once the arena cell is ticking: a few place tasks and a break, far more
     *  than they need. The poll waits {@link EntityArenaUtil#CELL_READY_TICKS} for the cell first (the pockets' water
     *  sources tick, and the pockets' blocks update, only once their chunks do), so its budget is the sum of the two,
     *  read as such by {@code GameTestPollBudgetTester}; the manifest's {@code max_ticks} stays above it. */
    private static final int BUDGET = 100;

    /** A Filler over a two-cell column: the unset cell at {@code pocket} (the fluid), the set cell above it. */
    private static BuilderClaimTester.TestFiller columnFiller(ServerLevel level, BlockPos pocket) {
        Template template = new Template();
        template.size = new BlockPos(1, 2, 1);
        template.facing = Direction.NORTH;
        template.offset = BlockPos.ZERO;
        template.data = new BitSet();
        template.data.set(template.posToIndex(0, 1, 0));
        BuilderClaimTester.TestFiller filler = new BuilderClaimTester.TestFiller(level, pocket.south(3),
                template.new BuildingInfo(pocket, Rotation.NONE));
        filler.excavate = true;
        filler.inv.setStackInSlot(0, new ItemStack(Items.STONE, 8));
        filler.builder.updateSnapshot();
        return filler;
    }

    /**
     * Two walled-in pockets on a stone floor, each under a template cell that is to receive stone: one holds a water
     * source, the other a waterlogged oak fence. Before the fix the Filler left the water cell "to break" for ever
     * (its break queue refuses fluid-only cells in No-Replace, the check still classed it as one to break), so it
     * never finished and a LOOP Filler never looped. Both fillers must finish, put their stone in the cell above,
     * leave the water in both pockets, and have broken the fence.
     */
    public static void fillerRestsWithFluidInAnUnsetCell(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        EntityArenaUtil.forceLoadCell(helper);
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        BlockPos water = new BlockPos(1, 2, 1);
        BlockPos fence = new BlockPos(3, 2, 3);
        for (BlockPos pocket : new BlockPos[] { water, fence }) {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                helper.setBlock(pocket.relative(side), Blocks.STONE);
            }
        }
        BlockWaterloggingTester.placeSource(helper, water, Blocks.WATER, Fluids.WATER);
        helper.setBlock(fence, Blocks.OAK_FENCE.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true));
        BlockPos waterAbs = helper.absolutePos(water);
        BlockPos fenceAbs = helper.absolutePos(fence);

        BuilderClaimTester.TestFiller waterFiller = columnFiller(level, waterAbs);
        BuilderClaimTester.TestFiller fenceFiller = columnFiller(level, fenceAbs);
        boolean[] done = { false, false };
        EntityArenaUtil.tickUntil(helper, EntityArenaUtil.CELL_READY_TICKS + BUDGET,
                EntityArenaUtil.whenCellTicking(helper, EntityArenaUtil.CELL_READY_TICKS, BUDGET, () -> {
                    done[0] = waterFiller.run(1);
                    done[1] = fenceFiller.run(1);
                    return done[0] && done[1];
                }, () -> "a Filler never finished: water pocket done=" + done[0] + " (leftToBreak="
                        + waterFiller.builder.leftToBreak + ", leftToPlace=" + waterFiller.builder.leftToPlace
                        + "), fence pocket done=" + done[1] + " (leftToBreak=" + fenceFiller.builder.leftToBreak
                        + ", leftToPlace=" + fenceFiller.builder.leftToPlace + "), fence cell="
                        + level.getBlockState(fenceAbs)),
                () -> {
                    check(level.getBlockState(waterAbs.above()).is(Blocks.STONE),
                            "the water pocket's template cell is filled");
                    check(level.getFluidState(waterAbs).isSource(), "the Filler must leave the water source in the "
                            + "unset cell alone, it is " + level.getBlockState(waterAbs));
                    check(level.getBlockState(fenceAbs.above()).is(Blocks.STONE),
                            "the fence pocket's template cell is filled");
                    check(!level.getBlockState(fenceAbs).is(Blocks.OAK_FENCE), "the waterlogged fence in an unset "
                            + "cell is excavated");
                    check(level.getFluidState(fenceAbs).isSource(), "the fence's water stays once the fence is gone");
                    helper.succeed();
                },
                "a Filler with fluid in an unset template cell never finished");
    }
}
