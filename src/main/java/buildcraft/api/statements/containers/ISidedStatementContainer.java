package buildcraft.api.statements.containers;

import net.minecraft.core.Direction;

import buildcraft.api.statements.IStatementContainer;

/** Created by asie on 3/14/15. */
public interface ISidedStatementContainer extends IStatementContainer {
    Direction getSide();
}

