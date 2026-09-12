/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.client.render;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import buildcraft.lib.client.model.ModelUtil;
import net.minecraft.core.Direction;

/**
 * Pins {@link RenderRobot}'s entity skin net to the 7.1.x-anchored orientation, texel for texel —
 * a wrong number here parses, compiles and renders, just wrong (that is exactly how the robot shipped
 * with every side face upside down relative to a 7.1.x build: 8.0.x's model-JSON numbers were used
 * verbatim, but 7.1.x's renderer drew the raw 1.7.10 ModelBox mapping, and the vanilla JSON pipeline's
 * face conventions 180&deg;-rotate every side face relative to it).
 *
 * <p>The expected values are the raw ModelBox mapping expressed through {@code ModelUtil.createFace}'s
 * fixed corner conventions: up and down run U&rarr;+X / V&rarr;&minus;Z as {@code createFace} does, so
 * their rectangles are used ascending; every side runs V upward in 7.1.x (minV at the cube's bottom
 * edge — the side art's transparent band hugs the cube's TOP), so all four carry reversed V; south and
 * west are additionally U-mirrored. West/east also swap 8.0.x's rectangles: 7.1.x drew region
 * (0,8)-(8,16) on &minus;X and (16,8)-(24,16) on +X. Two authentic oddities fall out and are pinned
 * on purpose: the eye decal rect (8,0)-(16,8) is the DOWN face (7.1.x really did draw the eye on the
 * robot's underside), and south's maxU sits exactly on the texture's u=1.0 edge (7.1.x sampled texel
 * column 32 of its 64-wide sheet, which the 32-wide re-cut does not have).
 *
 * <p>The item model shows the same art through the vanilla model-JSON pipeline, whose face conventions
 * differ from {@code createFace}'s, so {@code robot_chassis_base.json} needs different numbers for the
 * same look — those are pinned by {@code RoboticsItemIconCoverageTester} instead.
 */
public class RobotEntityUvNetTester {

    private static final float TEXEL = 1.0F / 32.0F;

    @Test
    public void entityNetMatchesTheSevenOneXBoxMapping() throws Exception {
        Field field = RenderRobot.class.getDeclaredField("UVS");
        field.setAccessible(true);
        ModelUtil.UvFaceData[] uvs = (ModelUtil.UvFaceData[]) field.get(null);
        Assertions.assertEquals(Direction.values().length, uvs.length, "one net entry per direction");

        assertFace(uvs, Direction.UP, 16, 0, 24, 8);
        assertFace(uvs, Direction.DOWN, 8, 0, 16, 8);
        assertFace(uvs, Direction.NORTH, 8, 16, 16, 8);
        assertFace(uvs, Direction.SOUTH, 32, 16, 24, 8);
        assertFace(uvs, Direction.WEST, 8, 16, 0, 8);
        assertFace(uvs, Direction.EAST, 16, 16, 24, 8);
    }

    private static void assertFace(ModelUtil.UvFaceData[] uvs, Direction face,
            int minU, int minV, int maxU, int maxV) {
        ModelUtil.UvFaceData uv = uvs[face.ordinal()];
        Assertions.assertNotNull(uv, face + " must have a net entry");
        String message = face + " drifted off the 7.1.x box mapping";
        Assertions.assertEquals(minU * TEXEL, uv.minU, 1e-6F, message + " (minU)");
        Assertions.assertEquals(minV * TEXEL, uv.minV, 1e-6F, message + " (minV)");
        Assertions.assertEquals(maxU * TEXEL, uv.maxU, 1e-6F, message + " (maxU)");
        Assertions.assertEquals(maxV * TEXEL, uv.maxV, 1e-6F, message + " (maxV)");
    }
}
