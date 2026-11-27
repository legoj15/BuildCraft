package buildcraft.builders.snapshot.pattern;

import java.util.BitSet;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.core.render.ISprite;
import buildcraft.api.filler.IFilledTemplate;
import buildcraft.api.filler.IFillerPatternShape;
import buildcraft.api.statements.IStatementParameter;

import buildcraft.lib.misc.VecUtil;

import buildcraft.builders.BCBuildersSprites;
import buildcraft.builders.snapshot.Snapshot;
import buildcraft.builders.snapshot.pattern.parameter.PatternParameterFacing;
import buildcraft.builders.snapshot.pattern.parameter.PatternParameterHollow;
import buildcraft.builders.snapshot.pattern.parameter.PatternParameterRotation;

public final class PatternSpherePart extends Pattern implements IFillerPatternShape {
    public enum SpherePartType {
        EIGHTH(3),
        QUARTER(2),
        HALF(1);

        public final String lowerCaseName = name().toLowerCase(Locale.ROOT);
        final int openFaces;

        SpherePartType(int numOpenFaces) {
            this.openFaces = numOpenFaces;
        }
    }

    private final SpherePartType type;

    public PatternSpherePart(SpherePartType type) {
        super("sphere_" + type.lowerCaseName);
        this.type = type;
    }

    @Override
    public int minParameters() {
        return type.openFaces == 1 ? 2 : 3;
    }

    @Override
    public int maxParameters() {
        return minParameters();
    }

    @Override
    public IStatementParameter createParameter(int index) {
        if (index >= minParameters()) {
            return null;
        }
        switch (index) {
            case 0:
                return PatternParameterHollow.FILLED_INNER;
            case 1:
                return PatternParameterFacing.DOWN;
            case 2:
                return PatternParameterRotation.NONE;
            default:
                return null;
        }
    }

    @Override
    public ISprite getSprite() {
        return BCBuildersSprites.FILLER_SPHERE_PART.get(type);
    }

    /**
     * The face a sphere part opens beyond the one it faces, for a quarter-turn count of {@code rotationCount}: the
     * second open face for the count itself, the third (the eighth sphere's) for the next count. The axis of the face
     * alternates with the count and the sign comes from the upper half of the counts, so the counts 0 to 3 go round the
     * faces of the two other axes: WEST, NORTH, EAST, SOUTH about a vertical facing.
     */
    private static Direction openFace(Direction facing, int rotationCount) {
        Axis axis = facing.getAxis();
        Axis openAxis;
        if (rotationCount % 2 == 1) {
            openAxis = axis == Axis.X ? Axis.Y : axis == Axis.Y ? Axis.Z : Axis.X;
        } else {
            openAxis = axis == Axis.X ? Axis.Z : axis == Axis.Y ? Axis.X : Axis.Y;
        }
        return VecUtil.getFacing(openAxis, rotationCount >= 2);
    }

    /** A quarter turn clockwise about Y: a horizontal face moves to the next one, UP and DOWN stay. */
    private static Direction turnedFace(Direction face) {
        return face.getAxis().isHorizontal() ? face.getClockWise() : face;
    }

    /**
     * The facing turns by itself ({@link PatternParameterFacing#rotateLeft}); the quarter-turn count has to follow it,
     * because the second and third open faces are chosen by the count together with the facing. Every open face moves
     * to its turned face, and the count is the one that picks exactly those faces round the turned facing: the second
     * face of a quarter sphere, the second and third of an eighth in either order (the shape is the set of its open
     * faces, and a turn does not always keep which of the three is the facing). It is not always one more: a face on
     * the X axis and one on the Z axis are picked by different counts. A count the tag lacks reads as NONE, so a
     * quarter or eighth sphere with none saved gets the turned count written when it is not NONE.
     */
    @Override
    public IStatementParameter[] rotateParameters(IStatementParameter[] params) {
        IStatementParameter[] turned = super.rotateParameters(params);
        if (type.openFaces > 1 && params.length > 2) {
            Direction facing = getParam(1, params, PatternParameterFacing.DOWN).face;
            int count = getParam(2, params, PatternParameterRotation.NONE).rotationCount;
            Direction turnedFacing = turnedFace(facing);
            Direction secondary = turnedFace(openFace(facing, count));
            Direction tertiary = turnedFace(openFace(facing, (count + 1) & 3));
            for (int turnedCount = 0; turnedCount < 4; turnedCount++) {
                Direction second = openFace(turnedFacing, turnedCount);
                Direction third = openFace(turnedFacing, (turnedCount + 1) & 3);
                boolean picks = type.openFaces < 3
                    ? second == secondary
                    : (second == secondary && third == tertiary) || (second == tertiary && third == secondary);
                if (picks) {
                    if (turnedCount != count) {
                        turned[2] = PatternParameterRotation.values()[turnedCount];
                    }
                    break;
                }
            }
        }
        return turned;
    }

    @Override
    public boolean fillTemplate(IFilledTemplate filledTemplate, IStatementParameter[] params) {
        PatternParameterFacing facing = getParam(1, params, PatternParameterFacing.DOWN);
        PatternParameterRotation rotation = getParam(2, params, PatternParameterRotation.NONE);
        PatternParameterHollow hollow = getParam(0, params, PatternParameterHollow.FILLED_INNER);

        Vec3 center;
        Vec3 radius;

        Set<Direction> innerSides = EnumSet.noneOf(Direction.class);

        Vec3 max = new Vec3(filledTemplate.getMax().getX(), filledTemplate.getMax().getY(), filledTemplate.getMax().getZ());
        center = VecUtil.scale(max, 0.5);
        radius = center.add(0.5, 0.5, 0.5);

        innerSides.add(facing.face);

        Axis axis = facing.face.getAxis();
        Vec3 offset = VecUtil.offset(Vec3.ZERO, facing.face, VecUtil.getValue(radius, axis));
        center = center.add(offset);
        radius = VecUtil.replaceValue(radius, axis, VecUtil.getValue(radius, axis) * 2);

        if (type.openFaces > 1) {
            Direction secondaryFace = openFace(facing.face, rotation.rotationCount);
            Axis secondaryAxis = secondaryFace.getAxis();
            innerSides.add(secondaryFace);

            offset = VecUtil.offset(Vec3.ZERO, secondaryFace, VecUtil.getValue(radius, secondaryAxis));
            center = center.add(offset);
            radius = VecUtil.replaceValue(radius, secondaryAxis, VecUtil.getValue(radius, secondaryAxis) * 2);

            if (type.openFaces > 2) {
                Direction tertiaryFace = openFace(facing.face, (rotation.rotationCount + 1) & 3);
                Axis tertiaryAxis = tertiaryFace.getAxis();
                innerSides.add(tertiaryFace);

                offset = VecUtil.offset(Vec3.ZERO, tertiaryFace, VecUtil.getValue(radius, tertiaryAxis));
                center = center.add(offset);
                radius = VecUtil.replaceValue(radius, tertiaryAxis, VecUtil.getValue(radius, tertiaryAxis) * 2);
            }
        }

        double cx = center.x;
        double cy = center.y;
        double cz = center.z;

        double rx = radius.x;
        double ry = radius.y;
        double rz = radius.z;

        BitSet data = null;
        if (hollow != PatternParameterHollow.FILLED_INNER) {
            data = new BitSet(Snapshot.getDataSize(filledTemplate.getSize()));
        }
        for (int x = 0; x <= filledTemplate.getMax().getX(); x++) {
            double dx = Math.abs(x - cx) / rx;
            double dxx = dx * dx;
            for (int y = 0; y <= filledTemplate.getMax().getY(); y++) {
                double dy = Math.abs(y - cy) / ry;
                double dyy = dy * dy;
                for (int z = 0; z <= filledTemplate.getMax().getZ(); z++) {
                    double dz = Math.abs(z - cz) / rz;
                    double dzz = dz * dz;
                    if (dxx + dyy + dzz < 1) {
                        if (hollow != PatternParameterHollow.FILLED_INNER) {
                            // noinspection ConstantConditions
                            data.set(Snapshot.posToIndex(filledTemplate.getSize(), x, y, z), true);
                        } else {
                            filledTemplate.set(x, y, z, true);
                        }
                    }
                }
            }
        }

        boolean outerFilled = hollow.outerFilled;

        if (hollow != PatternParameterHollow.FILLED_INNER) {
            // Z iteration
            for (int x = 0; x <= filledTemplate.getMax().getX(); x++) {
                for (int y = 0; y <= filledTemplate.getMax().getY(); y++) {
                    if (!innerSides.contains(Direction.NORTH)) {
                        for (int z = 0; z <= filledTemplate.getMax().getZ(); z++) {
                            if (data.get(Snapshot.posToIndex(filledTemplate.getSize(), x, y, z))) {
                                filledTemplate.set(x, y, z, true);
                                break;
                            }
                            if (outerFilled) {
                                filledTemplate.set(x, y, z, true);
                            }
                        }
                    }

                    if (!innerSides.contains(Direction.SOUTH)) {
                        for (int z = filledTemplate.getMax().getZ(); z >= 0; z--) {
                            if (data.get(Snapshot.posToIndex(filledTemplate.getSize(), x, y, z))) {
                                filledTemplate.set(x, y, z, true);
                                break;
                            }
                            if (outerFilled) {
                                filledTemplate.set(x, y, z, true);
                            }
                        }
                    }
                }
            }

            // Y iteration
            for (int x = 0; x <= filledTemplate.getMax().getX(); x++) {
                for (int z = 0; z <= filledTemplate.getMax().getZ(); z++) {
                    if (!innerSides.contains(Direction.DOWN)) {
                        for (int y = 0; y <= filledTemplate.getMax().getY(); y++) {
                            if (data.get(Snapshot.posToIndex(filledTemplate.getSize(), x, y, z))) {
                                filledTemplate.set(x, y, z, true);
                                break;
                            }
                            if (outerFilled) {
                                filledTemplate.set(x, y, z, true);
                            }
                        }
                    }

                    if (!innerSides.contains(Direction.UP)) {
                        for (int y = filledTemplate.getMax().getY(); y >= 0; y--) {
                            if (data.get(Snapshot.posToIndex(filledTemplate.getSize(), x, y, z))) {
                                filledTemplate.set(x, y, z, true);
                                break;
                            }
                            if (outerFilled) {
                                filledTemplate.set(x, y, z, true);
                            }
                        }
                    }
                }
            }

            // X iteration
            for (int y = 0; y <= filledTemplate.getMax().getY(); y++) {
                for (int z = 0; z <= filledTemplate.getMax().getZ(); z++) {
                    if (!innerSides.contains(Direction.WEST)) {
                        for (int x = 0; x <= filledTemplate.getMax().getX(); x++) {
                            if (data.get(Snapshot.posToIndex(filledTemplate.getSize(), x, y, z))) {
                                filledTemplate.set(x, y, z, true);
                                break;
                            }
                            if (outerFilled) {
                                filledTemplate.set(x, y, z, true);
                            }
                        }
                    }

                    if (!innerSides.contains(Direction.EAST)) {
                        for (int x = filledTemplate.getMax().getX(); x >= 0; x--) {
                            if (data.get(Snapshot.posToIndex(filledTemplate.getSize(), x, y, z))) {
                                filledTemplate.set(x, y, z, true);
                                break;
                            }
                            if (outerFilled) {
                                filledTemplate.set(x, y, z, true);
                            }
                        }
                    }
                }
            }
        }
        return true;
    }
}
