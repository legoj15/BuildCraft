/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.silicon.tile;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import buildcraft.silicon.BCSiliconBlocks;

/**
 * Verifies the two Laser targeting scans behind the "Laser Targeting Behavior" config (issue #22):
 * <ul>
 *   <li>{@code scanBox} (legacy BuildCraft 7.x) reaches a table off to the SIDE at the laser's own
 *       level, and a table behind a WALL — neither of which the cone can reach.</li>
 *   <li>{@code scanCone} (modern 8.0.x) still finds a clear table in front, and the box also covers
 *       that near-front target. (The two shapes overlap but neither strictly contains the other: the
 *       cone reaches 6 blocks straight ahead, the box only 5; the box reaches the sides the cone misses.)</li>
 * </ul>
 * Deterministic: the scans are pure functions of the placed blocks (no ticking, entities, or
 * randomness), so this is flake-free. All placed blocks stay in a tight +octant near the origin so
 * the test never writes outside its own arena (matching the MarkerVolumeTester convention). Membership
 * is checked on ABSOLUTE positions because the scans read the live level; {@code helper} positions are
 * arena-relative. The scan radius/range come from the production constants so the test tracks any change.
 */
public class LaserTargetingTester {

    /** Box mode reaches sideways/own-level and through-wall targets that the cone deliberately misses. */
    public static void testBoxReachesWhereConeCannot(GameTestHelper helper) {
        Level level = helper.getLevel();
        BlockPos origin = new BlockPos(1, 1, 1); // the (hypothetical) laser, facing UP
        Direction face = Direction.UP;

        // A table 3 blocks to the SIDE, at the laser's own level (forward 0) — outside any forward cone.
        BlockPos relSideways = origin.offset(3, 0, 0);
        // A table 2 blocks in FRONT (up) with a solid wall one block in front of the laser, on the path.
        BlockPos relWall = origin.offset(0, 1, 0);
        BlockPos relBehindWall = origin.offset(0, 2, 0);

        helper.setBlock(relSideways, BCSiliconBlocks.ASSEMBLY_TABLE.get());
        helper.setBlock(relWall, Blocks.STONE);
        helper.setBlock(relBehindWall, BCSiliconBlocks.ASSEMBLY_TABLE.get());

        BlockPos absOrigin = helper.absolutePos(origin);
        BlockPos absSideways = helper.absolutePos(relSideways);
        BlockPos absBehindWall = helper.absolutePos(relBehindWall);

        List<BlockPos> box = TileLaser.scanBox(level, absOrigin, face, TileLaser.BOX_RADIUS);
        List<BlockPos> cone = TileLaser.scanCone(level, absOrigin, face, TileLaser.TARGETING_RANGE);

        if (!box.contains(absSideways)) {
            helper.fail("Box scan should reach a table 3 to the side at the laser's own level");
        }
        if (!box.contains(absBehindWall)) {
            helper.fail("Box scan should reach a table behind a wall (no line-of-sight)");
        }
        if (cone.contains(absSideways)) {
            helper.fail("Cone scan must NOT reach a sideways table at the laser's own level");
        }
        if (cone.contains(absBehindWall)) {
            helper.fail("Cone scan must NOT reach a table whose line-of-sight is blocked by a wall");
        }
        helper.succeed();
    }

    /** The cone still finds a clear in-front target, and the box also covers that near-front target. */
    public static void testConeFindsClearFrontTarget(GameTestHelper helper) {
        Level level = helper.getLevel();
        BlockPos origin = new BlockPos(1, 1, 1);
        Direction face = Direction.UP;

        BlockPos relFront = origin.offset(0, 2, 0); // 2 in front, on-axis, clear path
        helper.setBlock(relFront, BCSiliconBlocks.ASSEMBLY_TABLE.get());

        BlockPos absOrigin = helper.absolutePos(origin);
        BlockPos absFront = helper.absolutePos(relFront);

        List<BlockPos> cone = TileLaser.scanCone(level, absOrigin, face, TileLaser.TARGETING_RANGE);
        List<BlockPos> box = TileLaser.scanBox(level, absOrigin, face, TileLaser.BOX_RADIUS);

        if (!cone.contains(absFront)) {
            helper.fail("Cone scan should reach a clear, on-axis table 2 blocks in front");
        }
        if (!box.contains(absFront)) {
            helper.fail("Box scan should also reach the clear near-front table");
        }
        helper.succeed();
    }
}
