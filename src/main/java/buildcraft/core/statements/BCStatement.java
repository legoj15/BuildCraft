/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.core.statements;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

import buildcraft.api.statements.IStatement;
import buildcraft.api.statements.IStatementParameter;
import buildcraft.api.statements.StatementManager;

import buildcraft.lib.statement.FullStatement;

@SuppressWarnings({"this-escape", "unchecked"})
public abstract class BCStatement implements IStatement {

    protected final String uniqueTag;

    /** UniqueTag accepts multiple possible tags, use this feature to migrate to more standardised tags if needed,
     * otherwise just pass a single string. The first passed string will be the one used when saved to disk.
     *
     * @param uniqueTag */
    public BCStatement(String... uniqueTag) {
        this.uniqueTag = uniqueTag[0];
        for (String tag : uniqueTag) {
            StatementManager.statements.put(tag, this);
        }
    }

    @Override
    public String getUniqueTag() {
        return uniqueTag;
    }

    @Override
    public int maxParameters() {
        return 0;
    }

    @Override
    public int minParameters() {
        return 0;
    }

    @Override
    public IStatement rotateLeft() {
        return this;
    }

    /**
     * The parameters this statement holds once what it belongs to (a gate, a Filler) is turned one clockwise quarter
     * turn about the Y axis ({@code Rotation.CLOCKWISE_90}: NORTH becomes EAST), in the same slots. The statement owns
     * the turn of its whole list because a parameter can mean something only beside another (a sphere part's
     * quarter-turn count depends on its facing). By default each parameter turns by its own
     * {@link IStatementParameter#rotateLeft()}, which is all a statement needs whose parameters name a direction or an
     * axis on their own; a statement with a parameter that depends on another overrides this. A slot that holds nothing
     * stays empty, except where an override gives the slot the value its rule calls for (a count the tag lacks reads
     * as the default, and turning it can make it a quarter turn); the array handed in is not changed.
     */
    public IStatementParameter[] rotateParameters(IStatementParameter[] params) {
        return FullStatement.rotateEach(params);
    }

    /**
     * Turns, in place, the parameters of {@code statement}, a {@link FullStatement} tag, by the statement that owns
     * them: {@code owner}'s own {@link #rotateParameters} when it is a BuildCraft statement (a filler pattern held as a
     * gate's action turns its parameters by its own rule, as it does in a Filler), each parameter by its own
     * {@code rotateLeft} for any other statement or for a kind that did not resolve ({@code owner} null).
     */
    public static void rotateParametersOf(CompoundTag statement, @Nullable IStatement owner, int quarterTurns) {
        if (owner instanceof BCStatement bcStatement) {
            FullStatement.rotateParameters(statement, bcStatement.maxParameters(), quarterTurns,
                bcStatement::rotateParameters);
        } else {
            FullStatement.rotateParameters(statement, quarterTurns);
        }
    }

    @Override
    public IStatement[] getPossible() {
        return new IStatement[] { this };
    }

    @Override
    public IStatementParameter createParameter(int index) {
        return null;
    }

    protected static <P extends IStatementParameter> P getParam(int index, IStatementParameter[] params, P _default) {
        if (params == null || params.length <= index) {
            return _default;
        }
        IStatementParameter atIndex = params[index];
        if (atIndex != null && atIndex.getClass() == _default.getClass()) {
            return (P) atIndex;
        }
        return _default;
    }
}
