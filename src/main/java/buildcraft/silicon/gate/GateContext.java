package buildcraft.silicon.gate;

import java.util.List;

import buildcraft.api.core.EnumPipePart;
import buildcraft.api.statements.IStatement;
import net.minecraft.core.Direction;

import buildcraft.lib.gui.ISimpleDrawable;
import buildcraft.lib.statement.StatementContext;

public class GateContext<T extends IStatement> implements StatementContext<T> {

    public final List<GateGroup<T>> groups;

    public GateContext(List<GateGroup<T>> groups) {
        this.groups = groups;
    }

    @Override
    public List<? extends StatementGroup<T>> getAllPossible() {
        return groups;
    }

    public static class GateGroup<T extends IStatement> implements StatementGroup<T> {
        public final EnumPipePart part;
        public final List<T> statements;

        public GateGroup(EnumPipePart part, List<T> statements) {
            this.part = part;
            this.statements = statements;
        }

        @Override
        public List<T> getValues() {
            return statements;
        }

        @Override
        public ISimpleDrawable getSourceIcon() {
            if (part == buildcraft.api.core.EnumPipePart.CENTER) return null; return buildcraft.lib.gui.statement.GuiElementStatement.SLOT_COLOUR.offset(0, (1 + part.getIndex()) * 18);
        }

        @Override
        public int getLedgerColour() {
            if (part == EnumPipePart.CENTER) {
                return 0;
            }
            return getColourForSide(part.face);
        }
    }

    private static final int[] FACE_TO_COLOUR = new int[6];

    static {
        FACE_TO_COLOUR[Direction.DOWN.ordinal()] = 0xFF_33_33_33;
        FACE_TO_COLOUR[Direction.UP.ordinal()] = 0xFF_CC_CC_CC;
    }

    private static int getColourForSide(Direction face) {
        return FACE_TO_COLOUR[face.ordinal()];
    }
}
