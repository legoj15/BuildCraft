package buildcraft.builders.snapshot.pattern;

import buildcraft.api.core.render.ISprite;

import buildcraft.builders.BCBuildersSprites;

public class PatternShape2dPentagon extends PatternShape2d {
    /** Each top corner's inset as a share of the width: a side's horizontal reach (sin 18) over the width it spans
     *  (one side plus two reaches). 1.12.2 used sin 18 of the whole width, giving a flat side 38% wide, not 62%. */
    private static final double DIST_HORIZONTAL;
    private static final double DIST_VERTICAL;

    static {
        double cos54 = StrictMath.cos(Math.toRadians(108 / 2));
        double cos18 = StrictMath.cos(Math.toRadians(108 - 90));
        double sin18 = StrictMath.sin(Math.toRadians(108 - 90));
        DIST_HORIZONTAL = sin18 / (1 + 2 * sin18);
        DIST_VERTICAL = cos54 / cos18;
    }

    public PatternShape2dPentagon() {
        super("2d_pentagon");
    }

    @Override
    public ISprite getSprite() {
        return BCBuildersSprites.FILLER_2D_PENTAGON;
    }

    @Override
    protected void genShape(int maxA, int maxB, LineList list) {
        int halfA = maxA / 2;
        int indentA = (int) Math.round(maxA * DIST_HORIZONTAL);
        int indentB = (int) Math.round(maxB * DIST_VERTICAL);
        // Point at b = 0 (north with the default axis), like the Triangle's apex; the flat side is b = maxB.
        list.moveTo(indentA, maxB);
        list.lineTo(maxA - indentA, maxB);
        list.lineFrom(maxA, maxB - indentB);
        list.lineTo(maxA - halfA, 0);
        list.moveTo(halfA, 0);
        list.lineFrom(0, maxB - indentB);
        list.lineTo(indentA, maxB);
        list.setFillPoint(halfA, maxB / 2);
    }
}
