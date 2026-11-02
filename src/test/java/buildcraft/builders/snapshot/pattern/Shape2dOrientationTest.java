/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.snapshot.pattern;

import java.util.BitSet;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

import buildcraft.api.filler.IFilledTemplate;
import buildcraft.api.filler.IFillerPatternShape;
import buildcraft.api.statements.IStatementParameter;

import buildcraft.builders.BCBuildersStatements;
import buildcraft.builders.snapshot.Snapshot;
import buildcraft.builders.snapshot.Template;
import buildcraft.builders.snapshot.pattern.parameter.PatternParameterAxis;
import buildcraft.builders.snapshot.pattern.parameter.PatternParameterHollow;
import buildcraft.builders.snapshot.pattern.parameter.PatternParameterRotation;

/** How a 2D shape lands in the world. The icon's top is north on Axis Y and up on the upright axes X and Z, so the
 *  Pentagon (regular, point at the icon's top like the Triangle's apex) and the Triangle stand on their bases there.
 *  1.12.2 laid Axis X on its side and Axis Z upside down. */
public class Shape2dOrientationTest {
    @FunctionalInterface
    private interface Cell {
        boolean at(IFilledTemplate t, int across, int depth);
    }

    private static IFilledTemplate fill(IFillerPatternShape pattern, BlockPos size, PatternParameterAxis axis) {
        Template template = new Template();
        template.size = size;
        template.offset = BlockPos.ZERO;
        template.data = new BitSet(Snapshot.getDataSize(size));
        IFilledTemplate filled = template.getFilledTemplate();
        IStatementParameter[] params = { axis, PatternParameterHollow.FILLED_INNER, PatternParameterRotation.NONE };
        Assertions.assertTrue(pattern.fillTemplate(filled, params));
        return filled;
    }

    private static String row(IFilledTemplate t, Cell cell, int depth) {
        StringBuilder sb = new StringBuilder();
        for (int a = 0; a < 14; a++) {
            sb.append(cell.at(t, a, depth) ? '#' : '.');
        }
        return sb.toString();
    }

    /** Checks a 14x14 face: {@code cell} reads the face with depth 0 at the icon's top. */
    private static void assertPointedTop(IFillerPatternShape pattern, BlockPos size, PatternParameterAxis axis,
        Cell cell, String bottom) {
        IFilledTemplate t = fill(pattern, size, axis);
        Assertions.assertEquals("......##......", row(t, cell, 0), axis + ": the point should be the icon's top row");
        Assertions.assertEquals(bottom, row(t, cell, 13), axis + ": wrong base row");
    }

    @Test
    public void pentagonAndTriangleStandOnTheirBasesOnEveryAxis() {
        Cell flat = (t, a, d) -> t.get(a, 0, d);
        Cell eastWest = (t, a, d) -> t.get(a, 13 - d, 0);
        Cell northSouth = (t, a, d) -> t.get(0, 13 - d, a);
        BlockPos flatBox = new BlockPos(14, 1, 14);
        BlockPos zBox = new BlockPos(14, 14, 1);
        BlockPos xBox = new BlockPos(1, 14, 14);
        IFillerPatternShape pentagon = BCBuildersStatements.PATTERN_PENTAGON;
        IFillerPatternShape triangle = BCBuildersStatements.PATTERN_TRIANGLE;
        // The Pentagon's flat side spans 10 of 14, the golden ratio's share (1.12.2's inset gave 6).
        String pentagonBase = "..##########..";
        String triangleBase = "##############";

        assertPointedTop(pentagon, flatBox, PatternParameterAxis.Y, flat, pentagonBase);
        assertPointedTop(pentagon, zBox, PatternParameterAxis.Z, eastWest, pentagonBase);
        assertPointedTop(pentagon, xBox, PatternParameterAxis.X, northSouth, pentagonBase);
        assertPointedTop(triangle, flatBox, PatternParameterAxis.Y, flat, triangleBase);
        assertPointedTop(triangle, zBox, PatternParameterAxis.Z, eastWest, triangleBase);
        assertPointedTop(triangle, xBox, PatternParameterAxis.X, northSouth, triangleBase);
    }
}
