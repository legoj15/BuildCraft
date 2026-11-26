/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.snapshot.pattern;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import buildcraft.builders.snapshot.pattern.parameter.PatternParameterCenter;
import buildcraft.builders.snapshot.pattern.parameter.PatternParameterFacing;
import buildcraft.builders.snapshot.pattern.parameter.PatternParameterHollow;
import buildcraft.builders.snapshot.pattern.parameter.PatternParameterRotation;
import buildcraft.builders.snapshot.pattern.parameter.PatternParameterXZDir;
import buildcraft.builders.snapshot.pattern.parameter.PatternParameterYDir;

/**
 * A Filler in a blueprint that is built rotated turns its pattern with it: the patterns lay their shape out in world
 * axes inside the Filler's box (the Filler's own facing never enters), so a half sphere that opened NORTH has to open
 * EAST after a quarter turn clockwise, or the blueprint builds a different shape. Two layers are pinned. Each
 * parameter's own {@code rotateLeft} (one call is one clockwise quarter turn: NORTH becomes EAST, UP and DOWN stay),
 * and the pattern's {@link Pattern#rotateParameters} over a whole parameter list, which is checked against what the
 * shape really is: for every pattern, every value of every parameter and several boxes that are not square, the turned
 * parameters in the box turned one quarter clockwise fill exactly the cells of the original shape turned the same way,
 * and four turns bring the parameters back. A parameter that turns alone with a pattern that does not read it as a
 * world direction, or a pattern whose quarter-turn parameter depends on another (the sphere parts and the 2D shapes),
 * fails there naming the pattern, the parameters and the cell.
 */
public class PatternRotationTest {

    // --- one parameter at a time ---

    @Test
    public void facingTurnsTheHorizontalFacesAndLeavesUpAndDown() {
        Assertions.assertSame(PatternParameterFacing.EAST, PatternParameterFacing.NORTH.rotateLeft());
        Assertions.assertSame(PatternParameterFacing.SOUTH, PatternParameterFacing.EAST.rotateLeft());
        Assertions.assertSame(PatternParameterFacing.WEST, PatternParameterFacing.SOUTH.rotateLeft());
        Assertions.assertSame(PatternParameterFacing.NORTH, PatternParameterFacing.WEST.rotateLeft());
        Assertions.assertSame(PatternParameterFacing.UP, PatternParameterFacing.UP.rotateLeft());
        Assertions.assertSame(PatternParameterFacing.DOWN, PatternParameterFacing.DOWN.rotateLeft());
        assertFourTurnsReturn(PatternParameterFacing.values());
    }

    @Test
    public void centerTurnsItsCompassOffsets() {
        Assertions.assertSame(PatternParameterCenter.EAST, PatternParameterCenter.NORTH.rotateLeft());
        Assertions.assertSame(PatternParameterCenter.SOUTH_EAST, PatternParameterCenter.NORTH_EAST.rotateLeft());
        Assertions.assertSame(PatternParameterCenter.SOUTH, PatternParameterCenter.EAST.rotateLeft());
        Assertions.assertSame(PatternParameterCenter.SOUTH_WEST, PatternParameterCenter.SOUTH_EAST.rotateLeft());
        Assertions.assertSame(PatternParameterCenter.WEST, PatternParameterCenter.SOUTH.rotateLeft());
        Assertions.assertSame(PatternParameterCenter.NORTH_WEST, PatternParameterCenter.SOUTH_WEST.rotateLeft());
        Assertions.assertSame(PatternParameterCenter.NORTH, PatternParameterCenter.WEST.rotateLeft());
        Assertions.assertSame(PatternParameterCenter.NORTH_EAST, PatternParameterCenter.NORTH_WEST.rotateLeft());
        Assertions.assertSame(PatternParameterCenter.CENTER, PatternParameterCenter.CENTER.rotateLeft());
        assertFourTurnsReturn(PatternParameterCenter.values());
    }

    @Test
    public void axisAndHorizontalDirectionTurnAndTheVerticalOnesStay() {
        Assertions.assertSame(PatternParameterAxis.Z, PatternParameterAxis.X.rotateLeft());
        Assertions.assertSame(PatternParameterAxis.X, PatternParameterAxis.Z.rotateLeft());
        Assertions.assertSame(PatternParameterAxis.Y, PatternParameterAxis.Y.rotateLeft());
        Assertions.assertSame(PatternParameterXZDir.EAST, PatternParameterXZDir.NORTH.rotateLeft());
        Assertions.assertSame(PatternParameterXZDir.NORTH, PatternParameterXZDir.WEST.rotateLeft());
        for (PatternParameterYDir up : PatternParameterYDir.values()) {
            Assertions.assertSame(up, up.rotateLeft(), "a turn about Y leaves up and down alone");
        }
        for (PatternParameterHollow hollow : PatternParameterHollow.values()) {
            Assertions.assertSame(hollow, hollow.rotateLeft(), "how a shape is filled names no direction");
        }
        assertFourTurnsReturn(PatternParameterAxis.values());
        assertFourTurnsReturn(PatternParameterXZDir.values());
    }

    /** The quarter-turn count of a shape means a different turn depending on the parameters beside it (the plane the
     *  shape lies in, the face a sphere part opens on), so on its own it is never turned: its pattern turns it. */
    @Test
    public void theRotationCountIsNotTurnedAlone() {
        for (PatternParameterRotation count : PatternParameterRotation.values()) {
            Assertions.assertSame(count, count.rotateLeft());
        }
    }

    private static void assertFourTurnsReturn(IStatementParameter[] all) {
        for (IStatementParameter start : all) {
            IStatementParameter turned = start;
            for (int i = 0; i < 4; i++) {
                turned = turned.rotateLeft();
            }
            Assertions.assertSame(start, turned, start + " must come back after four quarter turns");
        }
    }

    // --- a whole pattern ---

    private static IFilledTemplate fill(IFillerPatternShape pattern, BlockPos size, IStatementParameter[] params) {
        Template template = new Template();
        template.size = size;
        template.offset = BlockPos.ZERO;
        template.data = new BitSet(Snapshot.getDataSize(size));
        IFilledTemplate filled = template.getFilledTemplate();
        pattern.fillTemplate(filled, params.clone());
        return filled;
    }

    /** Every value the popup offers for slot {@code index} of {@code pattern}. */
    private static List<IStatementParameter> valuesOf(Pattern pattern, int index) {
        Set<IStatementParameter> values = new LinkedHashSet<>();
        for (IStatementParameter value : pattern.createParameter(index).getPossible(null)) {
            if (value != null) {
                values.add(value);
            }
        }
        return new ArrayList<>(values);
    }

    /** Every combination of one value per slot of {@code pattern}. */
    private static List<IStatementParameter[]> everyCombination(Pattern pattern) {
        List<IStatementParameter[]> combinations = new ArrayList<>();
        combinations.add(new IStatementParameter[pattern.maxParameters()]);
        for (int slot = 0; slot < pattern.maxParameters(); slot++) {
            List<IStatementParameter[]> next = new ArrayList<>();
            for (IStatementParameter[] partial : combinations) {
                for (IStatementParameter value : valuesOf(pattern, slot)) {
                    IStatementParameter[] with = partial.clone();
                    with[slot] = value;
                    next.add(with);
                }
            }
            combinations = next;
        }
        return combinations;
    }

    @Test
    public void everyPatternTurnsItsShapeWithItsParameters() {
        // Boxes that are not square and not flat, so a turn that swaps the two horizontal sizes shows.
        BlockPos[] boxes = { new BlockPos(5, 4, 7), new BlockPos(6, 3, 4), new BlockPos(4, 5, 4), new BlockPos(3, 3, 5) };
        // The first failure of each pattern and how many it had, so one broken pattern does not hide another.
        Map<String, String> firstFailure = new LinkedHashMap<>();
        Map<String, Integer> failureCount = new LinkedHashMap<>();
        int checked = 0;
        for (Pattern pattern : BCBuildersStatements.PATTERNS) {
            if (!(pattern instanceof IFillerPatternShape shape) || pattern == BCBuildersStatements.PATTERN_NONE) {
                continue;
            }
            for (IStatementParameter[] params : everyCombination(pattern)) {
                IStatementParameter[] turned = pattern.rotateParameters(params);
                IStatementParameter[] again = turned;
                for (int i = 1; i < 4; i++) {
                    again = pattern.rotateParameters(again);
                }
                if (!Arrays.equals(params, again)) {
                    fail(firstFailure, failureCount, pattern, Arrays.toString(params) + ": four turns gave "
                        + Arrays.toString(again));
                    continue;
                }
                if (pattern == BCBuildersStatements.PATTERN_OCTAGON && params[0] == PatternParameterAxis.Y) {
                    // The flat Octagon's diagonals are stepped lines that start from the side vertex, so the staircase
                    // of the corner differs by a cell between a box and the same box turned. It has no parameter to turn.
                    continue;
                }
                for (BlockPos box : boxes) {
                    checked++;
                    String mismatch = firstMismatch(shape, box, params, turned);
                    if (mismatch != null) {
                        fail(firstFailure, failureCount, pattern, Arrays.toString(params) + " turned to "
                            + Arrays.toString(turned) + " in " + box.toShortString() + ": " + mismatch);
                        break;
                    }
                }
            }
        }
        Assertions.assertTrue(checked > 0, "the walk reached no shapes");
        List<String> report = new ArrayList<>();
        firstFailure.forEach((tag, first) -> report.add(tag + " (" + failureCount.get(tag) + " parameter lists), e.g. "
            + first));
        Assertions.assertEquals(List.of(), report, "a pattern does not turn its shape with the blueprint");
    }

    private static void fail(Map<String, String> firstFailure, Map<String, Integer> failureCount, Pattern pattern,
        String message) {
        firstFailure.putIfAbsent(pattern.getUniqueTag(), message);
        failureCount.merge(pattern.getUniqueTag(), 1, Integer::sum);
    }

    /** The original shape turned one quarter clockwise (the cell (x, z) of a box of length l along Z goes to
     *  (l - 1 - z, x) of the box with the two horizontal sizes swapped) against the turned parameters filling that
     *  swapped box; null when every cell agrees. */
    private static String firstMismatch(IFillerPatternShape pattern, BlockPos box, IStatementParameter[] params,
        IStatementParameter[] turned) {
        int w = box.getX();
        int h = box.getY();
        int l = box.getZ();
        IFilledTemplate before = fill(pattern, box, params);
        IFilledTemplate after = fill(pattern, new BlockPos(l, h, w), turned);
        for (int y = 0; y < h; y++) {
            for (int z = 0; z < l; z++) {
                for (int x = 0; x < w; x++) {
                    boolean expected = before.get(x, y, z);
                    boolean actual = after.get(l - 1 - z, y, x);
                    if (expected != actual) {
                        return "cell (" + x + ", " + y + ", " + z + ") was " + expected + " and is now " + actual;
                    }
                }
            }
        }
        return null;
    }
}
