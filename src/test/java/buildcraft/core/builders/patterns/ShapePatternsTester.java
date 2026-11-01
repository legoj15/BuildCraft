package buildcraft.core.builders.patterns;

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.stream.Collectors;



import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;

import net.minecraft.gametest.framework.GameTestHelper;

import buildcraft.api.filler.FillerManager;
import buildcraft.api.filler.IFilledTemplate;
import buildcraft.api.filler.IFillerPatternShape;
import buildcraft.api.statements.IStatementParameter;

import buildcraft.lib.misc.VecUtil;

import buildcraft.builders.BCBuildersStatements;
import buildcraft.builders.registry.FillerRegistry;
import buildcraft.builders.snapshot.Snapshot;
import buildcraft.builders.snapshot.Template;
import buildcraft.builders.snapshot.pattern.PatternShape2d;
import buildcraft.builders.snapshot.pattern.parameter.PatternParameterAxis;
import buildcraft.builders.snapshot.pattern.parameter.PatternParameterFacing;
import buildcraft.builders.snapshot.pattern.parameter.PatternParameterHollow;
import buildcraft.builders.snapshot.pattern.parameter.PatternParameterRotation;

public class ShapePatternsTester {
    private static void assertTrue(boolean val) {
        if (!val) throw new IllegalStateException("Assertion failed!");
    }

    private static void assertFalse(boolean val) {
        if (val) throw new IllegalStateException("Assertion failed!");
    }
    public static List<IFillerPatternShape> patterns;
    public static final BlockPos[] SIZES = {
        new BlockPos(1, 1, 1), new BlockPos(2, 1, 1), new BlockPos(3, 1, 1),
        new BlockPos(2, 2, 2), new BlockPos(3, 2, 2), new BlockPos(4, 2, 2),
        new BlockPos(2, 3, 2), new BlockPos(2, 2, 3), new BlockPos(2, 8, 2),
        new BlockPos(3, 3, 3), new BlockPos(4, 4, 4), new BlockPos(5, 5, 5),
        new BlockPos(6, 6, 6), new BlockPos(7, 7, 7), new BlockPos(11, 13, 12)
    };

    public static void setupRegistries() {
        FillerManager.registry = FillerRegistry.INSTANCE;
        patterns = Arrays.stream(BCBuildersStatements.PATTERNS)
            .filter(IFillerPatternShape.class::isInstance)
            .map(IFillerPatternShape.class::cast)
            .collect(Collectors.toList());
    }

    private static IFilledTemplate createFilledTemplate(BlockPos size) {
        Template template = new Template();
        template.size = size;
        template.offset = BlockPos.ZERO;
        template.data = new BitSet(Snapshot.getDataSize(size));
        return template.getFilledTemplate();
    }

    public static void testTinyTemplate(GameTestHelper helper) {
        if (patterns == null) setupRegistries();
        
        try {
            for (IFillerPatternShape pattern : patterns) {
                for (BlockPos size : SIZES) {
                    System.out.println("Testing pattern " + pattern.getUniqueTag() + " in " + size.toShortString());

                    IStatementParameter[] params = new IStatementParameter[pattern.maxParameters()];
                    for (int i = 0; i < params.length; i++) {
                        params[i] = pattern.createParameter(i);
                    }

                    IFilledTemplate filledTemplate = createFilledTemplate(size);
                    boolean b = pattern.fillTemplate(filledTemplate, params);
                    if (pattern == BCBuildersStatements.PATTERN_NONE) {
                        assertFalse(b);
                    } else {
                        assertTrue(b);
                    }
                    // Every pattern the popup offers, bar None and Clear, has to place something (#85: Octagon placed nothing).
                    if (pattern != BCBuildersStatements.PATTERN_NONE && pattern != BCBuildersStatements.PATTERN_CLEAR
                        && countSet(filledTemplate) == 0) {
                        throw new IllegalStateException(pattern.getUniqueTag() + " placed nothing in " + size.toShortString());
                    }
                    // Flatten lays exactly the box's bottom layer and leaves everything above it to be cleared.
                    if (pattern == BCBuildersStatements.PATTERN_FLATTEN) {
                        for (int z = 0; z <= filledTemplate.getMax().getZ(); z++) {
                            for (int y = 0; y <= filledTemplate.getMax().getY(); y++) {
                                for (int x = 0; x <= filledTemplate.getMax().getX(); x++) {
                                    if (filledTemplate.get(x, y, z) != (y == 0)) {
                                        throw new IllegalStateException("flatten in " + size.toShortString() + " is wrong at (" + x + ", " + y + ", " + z + ")");
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // A closed 2D outline (Arc's is closed by the box edge) must place more when Filled than when Hollow, and
            // Surrounded must leave its inside empty (#85: Hexagon set no fill point, so Filled did nothing).
            BlockPos flat = new BlockPos(5, 1, 9);
            int cells = flat.getX() * flat.getY() * flat.getZ();
            for (IFillerPatternShape pattern : patterns) {
                if (!(pattern instanceof PatternShape2d) || pattern == BCBuildersStatements.PATTERN_ARC) continue;
                String where = pattern.getUniqueTag() + " in " + flat.toShortString();
                int hollow = countSet(fill2d(pattern, flat, PatternParameterHollow.HOLLOW, PatternParameterRotation.NONE));
                int filled = countSet(fill2d(pattern, flat, PatternParameterHollow.FILLED_INNER, PatternParameterRotation.NONE));
                int outer = countSet(fill2d(pattern, flat, PatternParameterHollow.FILLED_OUTER, PatternParameterRotation.NONE));
                if (filled <= hollow) {
                    throw new IllegalStateException(where + ": filled (" + filled + ") is not more than hollow (" + hollow + ")");
                }
                if (outer >= cells) {
                    throw new IllegalStateException(where + ": surrounded filled every cell, inside included");
                }
            }
            // Turning a 2D shape turns its fill with it: every rotation, in every fill mode, is the unturned shape
            // in the transposed (quarter turns) or same box, rotated cell for cell. A quarter turn used to seed the
            // fill outside a non-square box, so Filled did nothing and Surrounded filled everything. Shapes with no
            // rotation slot (Square, Octagon) never turn, though a Filler keeps a rotation from the previous pattern.
            for (BlockPos box : new BlockPos[] { new BlockPos(5, 1, 9), new BlockPos(4, 1, 7) }) {
                BlockPos transposed = new BlockPos(box.getZ(), 1, box.getX());
                int w = box.getX(), l = box.getZ();
                for (IFillerPatternShape pattern : patterns) {
                    if (!(pattern instanceof PatternShape2d)) continue;
                    boolean turns = pattern.maxParameters() > 2;
                    for (PatternParameterHollow mode : PatternParameterHollow.values()) {
                        IFilledTemplate same = fill2d(pattern, box, mode, PatternParameterRotation.NONE);
                        IFilledTemplate across = fill2d(pattern, transposed, mode, PatternParameterRotation.NONE);
                        for (int r = 1; r < 4; r++) {
                            IFilledTemplate turned = fill2d(pattern, box, mode, PatternParameterRotation.values()[r]);
                            for (int z = 0; z < l; z++) {
                                for (int x = 0; x < w; x++) {
                                    boolean expected;
                                    if (!turns) expected = same.get(x, 0, z);
                                    else if (r == 1) expected = across.get(z, 0, w - 1 - x);
                                    else if (r == 2) expected = same.get(w - 1 - x, 0, l - 1 - z);
                                    else expected = across.get(l - 1 - z, 0, x);
                                    if (turned.get(x, 0, z) != expected) {
                                        throw new IllegalStateException(pattern.getUniqueTag() + " " + mode + " rotated " + r + " in "
                                            + box.toShortString() + " is not the unturned shape turned, at (" + x + ", " + z + ")");
                                    }
                                }
                            }
                        }
                    }
                }
            }
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage());
        }
    }

    private static int countSet(IFilledTemplate template) {
        int count = 0;
        for (int z = 0; z <= template.getMax().getZ(); z++) {
            for (int y = 0; y <= template.getMax().getY(); y++) {
                for (int x = 0; x <= template.getMax().getX(); x++) {
                    if (template.get(x, y, z)) count++;
                }
            }
        }
        return count;
    }

    private static IFilledTemplate fill2d(IFillerPatternShape pattern, BlockPos size, PatternParameterHollow hollow,
        PatternParameterRotation rotation) {
        IStatementParameter[] params = new IStatementParameter[Math.max(3, pattern.maxParameters())];
        for (int i = 0; i < params.length; i++) {
            params[i] = i == 0 ? PatternParameterAxis.Y : i == 1 ? hollow : i == 2 ? rotation : pattern.createParameter(i);
        }
        IFilledTemplate template = createFilledTemplate(size);
        assertTrue(pattern.fillTemplate(template, params));
        return template;
    }

    public static void testSphereEquality(GameTestHelper helper) {
        if (patterns == null) setupRegistries();
        
        try {
            for (BlockPos size : SIZES) {
                BlockPos fullSize = new BlockPos(size.getX() * 2, size.getY() * 2, size.getZ() * 2);
                System.out.println("Testing spheres for equality in " + fullSize.toShortString());

                IStatementParameter[] fullParams = new IStatementParameter[] { PatternParameterHollow.HOLLOW };
                IFilledTemplate filledTemplateFull = createFilledTemplate(fullSize);
                assertTrue(BCBuildersStatements.PATTERN_SPHERE.fillTemplate(filledTemplateFull, fullParams));

                // Test halfs
                for (Direction face : Direction.values()) {
                    BlockPos halfSize = VecUtil.replaceValue(fullSize, face.getAxis(), VecUtil.getValue(size, face.getAxis()));
                    IStatementParameter[] params = new IStatementParameter[] {
                        PatternParameterHollow.HOLLOW,
                        PatternParameterFacing.get(face)
                    };
                    IFilledTemplate filledTemplateHalf = createFilledTemplate(halfSize);
                    assertTrue(BCBuildersStatements.PATTERN_HEMI_SPHERE.fillTemplate(filledTemplateHalf, params));
                    
                    int dx = face == Direction.WEST ? filledTemplateHalf.getSize().getX() : 0;
                    int dy = face == Direction.DOWN ? filledTemplateHalf.getSize().getY() : 0;
                    int dz = face == Direction.NORTH ? filledTemplateHalf.getSize().getZ() : 0;
                    for (int z = 0; z <= filledTemplateHalf.getMax().getZ(); z++) {
                        for (int y = 0; y <= filledTemplateHalf.getMax().getY(); y++) {
                            for (int x = 0; x <= filledTemplateHalf.getMax().getX(); x++) {
                                if (filledTemplateFull.get(x + dx, y + dy, z + dz) != filledTemplateHalf.get(x, y, z)) {
                                    throw new IllegalStateException(String.format("Half sphere[%s] didn't match full sphere at (%s, %s, %s)", face, x, y, z));
                                }
                            }
                        }
                    }
                }
            }
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage());
        }
    }
}
