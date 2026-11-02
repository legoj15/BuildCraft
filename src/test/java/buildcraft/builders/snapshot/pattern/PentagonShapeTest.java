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
import buildcraft.api.statements.IStatementParameter;

import buildcraft.builders.BCBuildersStatements;
import buildcraft.builders.snapshot.Snapshot;
import buildcraft.builders.snapshot.Template;
import buildcraft.builders.snapshot.pattern.parameter.PatternParameterAxis;
import buildcraft.builders.snapshot.pattern.parameter.PatternParameterHollow;
import buildcraft.builders.snapshot.pattern.parameter.PatternParameterRotation;

/** The Pentagon builds a regular pentagon, point north like the Triangle's apex: its flat side spans the golden
 *  ratio's share of the width (about 62%), not the 38% the 1.12.2 inset gave. */
public class PentagonShapeTest {
    private static String row(IFilledTemplate t, int width, int z) {
        StringBuilder sb = new StringBuilder();
        for (int x = 0; x < width; x++) {
            sb.append(t.get(x, 0, z) ? '#' : '.');
        }
        return sb.toString();
    }

    @Test
    public void defaultPentagonIsRegularAndPointsNorth() {
        BlockPos size = new BlockPos(14, 1, 14);
        Template template = new Template();
        template.size = size;
        template.offset = BlockPos.ZERO;
        template.data = new BitSet(Snapshot.getDataSize(size));
        IFilledTemplate filled = template.getFilledTemplate();
        IStatementParameter[] params = { PatternParameterAxis.Y, PatternParameterHollow.FILLED_INNER,
            PatternParameterRotation.NONE };
        Assertions.assertTrue(BCBuildersStatements.PATTERN_PENTAGON.fillTemplate(filled, params));

        Assertions.assertEquals("......##......", row(filled, 14, 0), "the point should be the north row");
        Assertions.assertEquals("..##########..", row(filled, 14, 13), "the flat south side should span 10 of 14");
    }
}
