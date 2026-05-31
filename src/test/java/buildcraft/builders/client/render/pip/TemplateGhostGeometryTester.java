/*
 * Copyright (c) 2026 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.client.render.pip;

import java.util.BitSet;
import java.util.EnumSet;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import buildcraft.builders.snapshot.Snapshot;
import buildcraft.builders.snapshot.Template;

/**
 * Pins the shell-culling that drives the used-Template ghost preview ({@link TemplateGhostGeometry}).
 * A filled cell's face is drawn only when its neighbour in that direction is not also filled, so a
 * solid template renders as a hollow outer shell instead of a haze of stacked translucent quads.
 * <p>
 * Pure geometry over a {@link Template}'s {@link BitSet} fill data — no client/GL context — so it
 * runs under plain {@code ./gradlew test}, the same way {@code PipeFlowRendererFluidsGeometryTester}
 * tests pipe-flow face geometry and {@code PosIndexTester} tests {@link Snapshot#posToIndex}.
 */
public class TemplateGhostGeometryTester {

    /** Build a template of the given dimensions with the listed cells marked filled. */
    private static Template template(int sx, int sy, int sz, BlockPos... filled) {
        Template t = new Template();
        t.size = new BlockPos(sx, sy, sz);
        t.facing = Direction.NORTH;
        t.offset = BlockPos.ZERO;
        t.data = new BitSet(Snapshot.getDataSize(t.size));
        for (BlockPos p : filled) {
            t.data.set(Snapshot.posToIndex(t.size, p));
        }
        return t;
    }

    private static EnumSet<Direction> faces(Template t, int x, int y, int z) {
        return TemplateGhostGeometry.visibleFaces(t, t.size, x, y, z);
    }

    /** An isolated filled cell (no filled neighbours) exposes all six faces. */
    @Test
    public void lone_cell_shows_all_six_faces() {
        Template t = template(3, 3, 3, new BlockPos(1, 1, 1));
        Assertions.assertEquals(EnumSet.allOf(Direction.class), faces(t, 1, 1, 1));
    }

    /** A 1×1×1 template is exterior on every side — every neighbour is out of bounds. */
    @Test
    public void single_cell_template_shows_all_six_faces() {
        Template t = template(1, 1, 1, new BlockPos(0, 0, 0));
        Assertions.assertEquals(EnumSet.allOf(Direction.class), faces(t, 0, 0, 0));
    }

    /** Two cells touching along X: the shared face is interior on both and must be culled. */
    @Test
    public void adjacent_pair_culls_the_shared_face() {
        Template t = template(2, 1, 1, new BlockPos(0, 0, 0), new BlockPos(1, 0, 0));
        EnumSet<Direction> west = faces(t, 0, 0, 0);
        EnumSet<Direction> east = faces(t, 1, 0, 0);
        Assertions.assertFalse(west.contains(Direction.EAST), "west cell's EAST face should be culled");
        Assertions.assertFalse(east.contains(Direction.WEST), "east cell's WEST face should be culled");
        Assertions.assertEquals(5, west.size(), "west cell should keep its other five faces");
        Assertions.assertEquals(5, east.size(), "east cell should keep its other five faces");
    }

    /** Inside a fully-filled 3×3×3 block: the centre cell is enclosed on all sides → no faces. */
    @Test
    public void interior_cell_of_solid_block_shows_no_faces() {
        Template t = solid3();
        Assertions.assertTrue(faces(t, 1, 1, 1).isEmpty(),
                "a fully-enclosed interior cell must emit no faces");
    }

    /** A face-centre cell of a solid block exposes exactly its one outward face. */
    @Test
    public void face_centre_cell_of_solid_block_shows_one_face() {
        Template t = solid3();
        // (1,1,0) sits on the -Z (NORTH) wall; only that neighbour is out of bounds.
        Assertions.assertEquals(EnumSet.of(Direction.NORTH), faces(t, 1, 1, 0));
    }

    /** A corner cell of a solid block exposes exactly its three outward faces. */
    @Test
    public void corner_cell_of_solid_block_shows_three_faces() {
        Template t = solid3();
        // (0,0,0): out of bounds toward -X (WEST), -Y (DOWN), -Z (NORTH); the other three filled.
        Assertions.assertEquals(EnumSet.of(Direction.WEST, Direction.DOWN, Direction.NORTH),
                faces(t, 0, 0, 0));
    }

    /** Aggregate shell check: a solid n³ block exposes exactly 6·n² faces (its surface area). */
    @Test
    public void solid_block_exposes_only_its_surface() {
        Template t = solid3();
        int total = 0;
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                for (int z = 0; z < 3; z++) {
                    total += faces(t, x, y, z).size();
                }
            }
        }
        Assertions.assertEquals(6 * 3 * 3, total,
                "a solid 3×3×3 template should expose exactly its 54 surface faces");
    }

    /** Null fill data (a fresh, never-populated template) must not NPE; every neighbour reads as
     *  unfilled, so the bounds/null guard reports all faces exposed. */
    @Test
    public void null_data_is_safe() {
        Template t = new Template();
        t.size = new BlockPos(2, 2, 2);
        t.data = null;
        Assertions.assertEquals(EnumSet.allOf(Direction.class), faces(t, 0, 0, 0));
    }

    private static Template solid3() {
        BlockPos[] all = new BlockPos[27];
        int i = 0;
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                for (int z = 0; z < 3; z++) {
                    all[i++] = new BlockPos(x, y, z);
                }
            }
        }
        return template(3, 3, 3, all);
    }
}
