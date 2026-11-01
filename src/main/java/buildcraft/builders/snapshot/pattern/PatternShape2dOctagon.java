package buildcraft.builders.snapshot.pattern;

import buildcraft.api.core.render.ISprite;

import buildcraft.builders.BCBuildersSprites;

public class PatternShape2dOctagon extends PatternShape2d {
    private static final double CORNER = 1 / (2 + Math.sqrt(2));

    public PatternShape2dOctagon() {
        super("2d_octagon");
    }

    @Override
    public int minParameters() {
        return 2;
    }

    @Override
    public int maxParameters() {
        return 2;
    }

    @Override
    public ISprite getSprite() {
        return BCBuildersSprites.FILLER_2D_OCTAGON;
    }

    @Override
    protected void genShape(int maxA, int maxB, LineList list) {
        // A regular octagon cuts 1 / (2 + sqrt 2) of the width off each corner. Every diagonal is drawn from its side
        // vertex towards the top or bottom edge, as Hexagon does, so the stepped corners mirror each other.
        int indentA = (int) Math.round(maxA * CORNER);
        int indentB = (int) Math.round(maxB * CORNER);
        list.moveTo(indentA, 0);
        list.lineTo(maxA - indentA, 0);
        list.lineFrom(maxA, indentB);
        list.lineTo(maxA, maxB - indentB);
        list.lineTo(maxA - indentA, maxB);
        list.lineFrom(indentA, maxB);
        list.lineFrom(0, maxB - indentB);
        list.lineTo(0, indentB);
        list.lineTo(indentA, 0);
        list.setFillPoint(maxA / 2, maxB / 2);
    }
}
