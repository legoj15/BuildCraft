package buildcraft.silicon.gate;

import java.util.List;

import buildcraft.api.statements.IStatement;

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
        public final List<T> statements;

        public GateGroup(List<T> statements) {
            this.statements = statements;
        }

        @Override
        public List<T> getValues() {
            return statements;
        }

        @Override
        public ISimpleDrawable getSourceIcon() {
            return null;
        }
    }
}

