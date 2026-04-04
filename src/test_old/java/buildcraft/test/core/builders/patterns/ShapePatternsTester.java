package buildcraft.test.core.builders.patterns;

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;

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
import buildcraft.test.VanillaSetupBaseTester;

public class ShapePatternsTester extends VanillaSetupBaseTester {
    public static List<IFillerPatternShape> patterns;

    public static Stream<Arguments> sizeAndPattern() {
        if (patterns == null) setupRegistries();
        BlockPos[] sizes = {
            new BlockPos(1, 1, 1), new BlockPos(2, 1, 1), new BlockPos(3, 1, 1),
            new BlockPos(2, 2, 2), new BlockPos(3, 2, 2), new BlockPos(4, 2, 2),
            new BlockPos(2, 3, 2), new BlockPos(2, 2, 3), new BlockPos(2, 8, 2),
            new BlockPos(3, 3, 3), new BlockPos(4, 4, 4), new BlockPos(5, 5, 5),
            new BlockPos(6, 6, 6), new BlockPos(7, 7, 7), new BlockPos(11, 13, 12)
        };
        Stream.Builder<Arguments> builder = Stream.builder();
        for (IFillerPatternShape pattern : patterns) {
            for (BlockPos size : sizes) {
                builder.add(Arguments.of(pattern, size));
            }
        }
        return builder.build();
    }

    public static Stream<BlockPos> sizes() {
        return Stream.of(
            new BlockPos(1, 1, 1), new BlockPos(2, 1, 1), new BlockPos(3, 1, 1),
            new BlockPos(2, 2, 2), new BlockPos(3, 2, 2), new BlockPos(4, 2, 2),
            new BlockPos(2, 3, 2), new BlockPos(2, 2, 3), new BlockPos(2, 8, 2),
            new BlockPos(3, 3, 3), new BlockPos(4, 4, 4), new BlockPos(5, 5, 5),
            new BlockPos(6, 6, 6), new BlockPos(7, 7, 7), new BlockPos(11, 13, 12)
        );
    }

    @BeforeAll
    public static void setupRegistries() {
        VanillaSetupBaseTester.init();
        FillerManager.registry = FillerRegistry.INSTANCE;
        patterns = Arrays.stream(BCBuildersStatements.PATTERNS)
            .filter(IFillerPatternShape.class::isInstance)
            .map(IFillerPatternShape.class::cast)
            .collect(Collectors.toList());
    }

    @ParameterizedTest
    @MethodSource("sizeAndPattern")
    public void testTinyTemplate(IFillerPatternShape pattern, BlockPos size) {
        System.out.print("Testing pattern " + pattern.getUniqueTag() + " in " + size.toShortString());

        try {
            IStatementParameter[] params = new IStatementParameter[pattern.maxParameters()];
            for (int i = 0; i < params.length; i++) {
                params[i] = pattern.createParameter(i);
            }

            IFilledTemplate filledTemplate = createFilledTemplate(size);
            boolean b = pattern.fillTemplate(filledTemplate, params);
            if (pattern == BCBuildersStatements.PATTERN_NONE) {
                Assertions.assertFalse(b);
            } else {
                Assertions.assertTrue(b);
            }
            System.out.println(" -> success");
        } catch (Throwable t) {
            System.out.println(" -> fail");
            throw t;
        }
    }

    private IFilledTemplate createFilledTemplate(BlockPos size) {
        Template template = new Template();
        template.size = size;
        template.offset = BlockPos.ZERO;
        template.data = new BitSet(Snapshot.getDataSize(size));
        return template.getFilledTemplate();
    }

    @ParameterizedTest
    @MethodSource("sizes")
    public void testSphereEquality(BlockPos size) {
        BlockPos fullSize = new BlockPos(size.getX() * 2, size.getY() * 2, size.getZ() * 2);

        System.out.println("Testing spheres for equality in " + fullSize.toShortString());

        IStatementParameter[] fullParams = new IStatementParameter[] {
            PatternParameterHollow.HOLLOW,
        };
        IFilledTemplate filledTemplateFull = createFilledTemplate(fullSize);
        Assertions.assertTrue(BCBuildersStatements.PATTERN_SPHERE.fillTemplate(filledTemplateFull, fullParams));
        System.out.println("Full:\n" + filledTemplateFull);

        // Test halfs
        for (Direction face : Direction.values()) {
            BlockPos halfSize = VecUtil.replaceValue(fullSize, face.getAxis(), VecUtil.getValue(size, face.getAxis()));
            IStatementParameter[] params = new IStatementParameter[] {
                PatternParameterHollow.HOLLOW,
                PatternParameterFacing.get(face)
            };
            IFilledTemplate filledTemplateHalf = createFilledTemplate(halfSize);
            Assertions.assertTrue(BCBuildersStatements.PATTERN_HEMI_SPHERE.fillTemplate(filledTemplateHalf, params));
            System.out.println("Half:\n" + filledTemplateHalf);
            int dx = face == Direction.WEST ? filledTemplateHalf.getSize().getX() : 0;
            int dy = face == Direction.DOWN ? filledTemplateHalf.getSize().getY() : 0;
            int dz = face == Direction.NORTH ? filledTemplateHalf.getSize().getZ() : 0;
            for (int z = 0; z <= filledTemplateHalf.getMax().getZ(); z++) {
                for (int y = 0; y <= filledTemplateHalf.getMax().getY(); y++) {
                    for (int x = 0; x <= filledTemplateHalf.getMax().getX(); x++) {
                        if (filledTemplateFull.get(x + dx, y + dy, z + dz) != filledTemplateHalf.get(x, y, z)) {
                            Assertions.fail(
                                String.format(
                                    "Half sphere[%s] didn't match full sphere at (%s, %s, %s)",
                                    face,
                                    x,
                                    y,
                                    z
                                )
                            );
                        }
                    }
                }
            }
        }
    }
}
