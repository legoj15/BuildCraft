/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.silicon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import buildcraft.silicon.block.BlockLaser;

/**
 * Verifies the Laser's collision/outline shape ({@link BlockLaser#getShape}) hugs its rendered
 * model — a full-footprint 4px base plate plus a centred emitter tower — instead of the old full
 * cube, so players can click and walk between closely-packed lasers (issue #22).
 * <p>
 * Collision shapes are a pure function of the blockstate (no ticking, entities, or randomness), so
 * this is deterministic and flake-free. It also pins the per-facing orientation against future
 * Minecraft-line bumps that could quietly change how block-model rotation maps onto VoxelShapes.
 */
public class LaserShapeTester {
    private static final double EPS = 1.0e-6;
    private static final double TIP_POS = 13.0 / 16.0; // tower tip when the laser points along a +axis (0.8125)
    private static final double TIP_NEG = 3.0 / 16.0;  // tower tip when the laser points along a -axis (0.1875)

    public static void testCollisionShapeMatchesModel(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);

        // Every facing: the shape exists, isn't the old full cube, and its envelope sits where the
        // model puts it — base plate on the face opposite FACING, emitter tower reaching toward it.
        for (Direction facing : Direction.values()) {
            BlockState state = BCSiliconBlocks.LASER.get().defaultBlockState().setValue(BlockLaser.FACING, facing);
            helper.setBlock(pos, state);

            VoxelShape shape = helper.getBlockState(pos).getCollisionShape(helper.getLevel(), helper.absolutePos(pos));
            if (shape.isEmpty()) {
                helper.fail("Laser collision shape was empty for facing=" + facing);
            }
            AABB bounds = shape.bounds();
            AABB expected = expectedBounds(facing);
            if (!aabbMatches(bounds, expected)) {
                helper.fail("Laser collision bounds for facing=" + facing + " were " + bounds
                        + " but expected " + expected + " (base plate opposite facing, tower toward it)");
            }
        }

        // The headline of issue #22: there must be a genuine HOLE beside the tower, not merely a
        // shrunk cube (which would pass the bounds check above). With facing=UP the base fills y<0.25
        // across the whole footprint and the tower is the centred 6x6 column (x/z in 0.3125..0.6875)
        // rising to y=0.8125. Probe one point in each region.
        helper.setBlock(pos, BCSiliconBlocks.LASER.get().defaultBlockState().setValue(BlockLaser.FACING, Direction.UP));
        VoxelShape up = helper.getBlockState(pos).getCollisionShape(helper.getLevel(), helper.absolutePos(pos));
        assertSolid(helper, up, 0.5, 0.1, 0.5, "base plate");
        assertSolid(helper, up, 0.5, 0.6, 0.5, "emitter tower");
        assertHollow(helper, up, 0.1, 0.6, 0.5, "side gap"); // beside the tower, above the plate — must be clickable-through

        helper.succeed();
    }

    private static AABB expectedBounds(Direction facing) {
        return switch (facing) {
            case UP -> new AABB(0, 0, 0, 1, TIP_POS, 1);
            case DOWN -> new AABB(0, TIP_NEG, 0, 1, 1, 1);
            case SOUTH -> new AABB(0, 0, 0, 1, 1, TIP_POS);
            case NORTH -> new AABB(0, 0, TIP_NEG, 1, 1, 1);
            case EAST -> new AABB(0, 0, 0, TIP_POS, 1, 1);
            case WEST -> new AABB(TIP_NEG, 0, 0, 1, 1, 1);
        };
    }

    private static boolean aabbMatches(AABB a, AABB b) {
        return Math.abs(a.minX - b.minX) < EPS && Math.abs(a.minY - b.minY) < EPS && Math.abs(a.minZ - b.minZ) < EPS
                && Math.abs(a.maxX - b.maxX) < EPS && Math.abs(a.maxY - b.maxY) < EPS && Math.abs(a.maxZ - b.maxZ) < EPS;
    }

    private static void assertSolid(GameTestHelper helper, VoxelShape shape, double x, double y, double z, String where) {
        if (!contains(shape, x, y, z)) {
            helper.fail("Laser collision should be solid at the " + where + " (" + x + ", " + y + ", " + z + ")");
        }
    }

    private static void assertHollow(GameTestHelper helper, VoxelShape shape, double x, double y, double z, String where) {
        if (contains(shape, x, y, z)) {
            helper.fail("Laser collision should be hollow at the " + where + " (" + x + ", " + y + ", " + z
                    + ") so closely-packed lasers stay clickable-between");
        }
    }

    private static boolean contains(VoxelShape shape, double x, double y, double z) {
        for (AABB box : shape.toAabbs()) {
            if (box.contains(x, y, z)) {
                return true;
            }
        }
        return false;
    }
}
