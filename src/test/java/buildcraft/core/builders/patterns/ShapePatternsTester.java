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
import buildcraft.builders.snapshot.pattern.parameter.PatternParameterFacing;
import buildcraft.builders.snapshot.pattern.parameter.PatternParameterHollow;

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
                }
            }
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage());
        }
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
