/*
 * Copyright (c) 2026 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

import buildcraft.factory.tile.TilePump;

/**
 * Regression coverage for {@link TilePump#isInfiniteSourceAt} — the corner-case
 * refinement to the pump's "infinite" detection that landed in 26.1.x Beta 7.
 * <p>
 * Pre-fix, the BFS in {@code buildQueue0} flipped its single {@code isInfiniteWaterSource}
 * flag the moment <em>any</em> block in the explored volume had ≥2 source neighbours +
 * water/solid below, then short-circuited out. A 1×3 strip with the pump's tube
 * over either edge therefore got the same "infinite" verdict as one with the tube
 * over the regenerable centre — wrong, since a player bucket-scooping the edge of
 * a 1×3 strip in vanilla loses the source whereas scooping the centre regenerates.
 * <p>
 * The new helper applies vanilla's own water-source rule per position: ≥2 horizontally
 * adjacent source neighbours and a non-air supporting block below. The pump's
 * anchor-block (tube end) is what's checked, not "any block somewhere in the BFS."
 */
public class PumpInfiniteDetectionTester {

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) throw new IllegalStateException(msg);
    }

    /**
     * 1×3 strip [S1][S2][S3] on a solid floor: vanilla's water-source rule says only
     * the centre would regenerate after being scooped (S2 has S1 and S3 as horizontal
     * source neighbours), so only S2 reports as infinite. Both edges have a single
     * horizontal source neighbour each and therefore fail the {@code sources >= 2} gate.
     */
    public static void testStrip1x3CentreInfiniteEdgesFinite(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        BlockPos s1 = new BlockPos(1, 2, 1);
        BlockPos s2 = new BlockPos(2, 2, 1);
        BlockPos s3 = new BlockPos(3, 2, 1);

        helper.setBlock(s1.below(), Blocks.STONE);
        helper.setBlock(s2.below(), Blocks.STONE);
        helper.setBlock(s3.below(), Blocks.STONE);
        helper.setBlock(s1, Blocks.WATER);
        helper.setBlock(s2, Blocks.WATER);
        helper.setBlock(s3, Blocks.WATER);

        assertTrue(!TilePump.isInfiniteSourceAt(level, helper.absolutePos(s1)),
                "Edge S1 of 1x3 strip should NOT be infinite (only 1 horizontal source neighbour)");
        assertTrue(TilePump.isInfiniteSourceAt(level, helper.absolutePos(s2)),
                "Centre S2 of 1x3 strip SHOULD be infinite (S1+S3 as neighbours, stone below)");
        assertTrue(!TilePump.isInfiniteSourceAt(level, helper.absolutePos(s3)),
                "Edge S3 of 1x3 strip should NOT be infinite (only 1 horizontal source neighbour)");

        helper.succeed();
    }

    /**
     * Isolated 1×1 water source is never infinite — zero horizontal neighbours.
     */
    public static void testIsolatedSourceFinite(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = new BlockPos(1, 2, 1);

        helper.setBlock(pos.below(), Blocks.STONE);
        helper.setBlock(pos, Blocks.WATER);

        assertTrue(!TilePump.isInfiniteSourceAt(level, helper.absolutePos(pos)),
                "Isolated water source must not be flagged infinite");

        helper.succeed();
    }

    /**
     * 2×2 pond: every corner has two horizontal source neighbours, so all four
     * positions report as infinite. This is the classic "infinite water pool" players
     * carve into stone — the fix must not regress it.
     */
    public static void testPond2x2AllCornersInfinite(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        BlockPos[] corners = {
                new BlockPos(1, 2, 1),
                new BlockPos(2, 2, 1),
                new BlockPos(1, 2, 2),
                new BlockPos(2, 2, 2)
        };
        for (BlockPos c : corners) {
            helper.setBlock(c.below(), Blocks.STONE);
            helper.setBlock(c, Blocks.WATER);
        }

        for (BlockPos c : corners) {
            assertTrue(TilePump.isInfiniteSourceAt(level, helper.absolutePos(c)),
                    "Every corner of a 2x2 pond should be infinite, " + c + " was not");
        }

        helper.succeed();
    }

    /**
     * 1×5 strip [S1..S5]: edges (S1, S5) are finite, the three middle blocks
     * (S2, S3, S4) each have 2 horizontal neighbours and report infinite. The point
     * of this case is that "centre" extends to every interior block of a long strip,
     * not just the literal midpoint of a 1×3.
     */
    public static void testStrip1x5InteriorInfiniteEdgesFinite(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        BlockPos[] positions = new BlockPos[5];
        for (int i = 0; i < 5; i++) {
            positions[i] = new BlockPos(1 + i, 2, 1);
            helper.setBlock(positions[i].below(), Blocks.STONE);
            helper.setBlock(positions[i], Blocks.WATER);
        }

        assertTrue(!TilePump.isInfiniteSourceAt(level, helper.absolutePos(positions[0])),
                "S1 (left edge) must be finite");
        assertTrue(TilePump.isInfiniteSourceAt(level, helper.absolutePos(positions[1])),
                "S2 (interior) must be infinite");
        assertTrue(TilePump.isInfiniteSourceAt(level, helper.absolutePos(positions[2])),
                "S3 (interior) must be infinite");
        assertTrue(TilePump.isInfiniteSourceAt(level, helper.absolutePos(positions[3])),
                "S4 (interior) must be infinite");
        assertTrue(!TilePump.isInfiniteSourceAt(level, helper.absolutePos(positions[4])),
                "S5 (right edge) must be finite");

        helper.succeed();
    }

    /**
     * A water source perched over air fails the support check even if its horizontal
     * neighbour count would otherwise qualify — vanilla regenerated water flows
     * straight down through air, so the position can't sustain a new source. Without
     * this guard the pump would preserve floating water that the player can't even
     * exploit by bucket scoop.
     */
    public static void testNoSupportBelowIsFinite(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        BlockPos s1 = new BlockPos(1, 3, 1);
        BlockPos s2 = new BlockPos(2, 3, 1);
        BlockPos s3 = new BlockPos(3, 3, 1);

        helper.setBlock(s1, Blocks.WATER);
        helper.setBlock(s2, Blocks.WATER);
        helper.setBlock(s3, Blocks.WATER);

        assertTrue(!TilePump.isInfiniteSourceAt(level, helper.absolutePos(s2)),
                "Centre of a 1x3 strip with no support below must not be flagged infinite");

        helper.succeed();
    }

    /**
     * Diagonal neighbours do NOT count toward the horizontal-source tally — vanilla's
     * regen rule is strictly orthogonal in the horizontal plane. Two diagonally
     * adjacent sources sharing one common corner are both isolated.
     */
    public static void testDiagonalNeighboursDoNotCount(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        BlockPos a = new BlockPos(1, 2, 1);
        BlockPos b = new BlockPos(2, 2, 2);

        helper.setBlock(a.below(), Blocks.STONE);
        helper.setBlock(b.below(), Blocks.STONE);
        helper.setBlock(a, Blocks.WATER);
        helper.setBlock(b, Blocks.WATER);

        assertTrue(!TilePump.isInfiniteSourceAt(level, helper.absolutePos(a)),
                "Diagonal-only neighbour must not be counted, A should be finite");
        assertTrue(!TilePump.isInfiniteSourceAt(level, helper.absolutePos(b)),
                "Diagonal-only neighbour must not be counted, B should be finite");

        helper.succeed();
    }

    /**
     * Water below counts as support even when not a source — matches the historical
     * pump check that used {@code getFluidWithFlowing} for the below-block test.
     * Lets the rule recognise "1×3 strip floating on top of a deeper pool" as
     * infinite at its centre.
     */
    public static void testWaterBelowProvidesSupport(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        BlockPos s1 = new BlockPos(1, 3, 1);
        BlockPos s2 = new BlockPos(2, 3, 1);
        BlockPos s3 = new BlockPos(3, 3, 1);

        helper.setBlock(s1.below(), Blocks.WATER);
        helper.setBlock(s2.below(), Blocks.WATER);
        helper.setBlock(s3.below(), Blocks.WATER);
        helper.setBlock(s1.below().below(), Blocks.STONE);
        helper.setBlock(s2.below().below(), Blocks.STONE);
        helper.setBlock(s3.below().below(), Blocks.STONE);
        helper.setBlock(s1, Blocks.WATER);
        helper.setBlock(s2, Blocks.WATER);
        helper.setBlock(s3, Blocks.WATER);

        assertTrue(TilePump.isInfiniteSourceAt(level, helper.absolutePos(s2)),
                "Centre block with water below should be infinite (water counts as support)");

        helper.succeed();
    }

    /**
     * Null defensiveness — the helper sits on the {@code mine()} hot path and is
     * called per drain; a null level or pos during teardown must short-circuit to
     * "not infinite" rather than NPE.
     */
    public static void testNullSafetyShortCircuits(GameTestHelper helper) {
        assertTrue(!TilePump.isInfiniteSourceAt(null, BlockPos.ZERO),
                "Null level must return false");
        assertTrue(!TilePump.isInfiniteSourceAt(helper.getLevel(), null),
                "Null pos must return false");
        helper.succeed();
    }
}
