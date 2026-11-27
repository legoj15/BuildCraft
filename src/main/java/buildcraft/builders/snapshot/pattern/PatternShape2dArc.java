package buildcraft.builders.snapshot.pattern;

import buildcraft.api.core.render.ISprite;

import buildcraft.builders.BCBuildersSprites;

public class PatternShape2dArc extends PatternShape2d {
    public PatternShape2dArc() {
        super("2d_arc");
    }

    @Override
    public ISprite getSprite() {
        return BCBuildersSprites.FILLER_2D_ARC;
    }

    /** The arc is a quarter of an ellipse centred on the corner at the largest across and down (the bottom right of the
     *  icon): reversing the across axis centres it on the bottom left, which is one quarter turn on, and so on round
     *  the corners. */
    @Override
    protected int mirroredCount(int count) {
        return (1 - count) & 3;
    }

    @Override
    protected void genShape(int maxA, int maxB, LineList list) {
        if (maxA == 0 || maxB == 0) {
            list.moveTo(0, 0);
            list.lineTo(maxA, maxB);
            return;
        }
        list.setFillPoint(maxA, maxB);
        list.arc(maxA, maxB, maxA, maxB);
    }
}
