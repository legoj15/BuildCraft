package buildcraft.lib.gui.statement;

import net.minecraft.resources.Identifier;

import buildcraft.api.statements.IStatementParameter;

import buildcraft.lib.gui.ISimpleDrawable;

/** An {@link IStatementParameter} that provides methods to draw itself. */
public interface IDrawingParameter extends IStatementParameter {
    ISimpleDrawable getDrawable();
}
