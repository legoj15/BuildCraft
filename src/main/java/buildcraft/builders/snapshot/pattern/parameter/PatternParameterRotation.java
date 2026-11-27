/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.snapshot.pattern.parameter;

import javax.annotation.Nonnull;

import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;

import buildcraft.api.core.render.ISprite;
import buildcraft.api.statements.IStatement;
import buildcraft.api.statements.IStatementContainer;
import buildcraft.api.statements.IStatementParameter;
import buildcraft.api.statements.StatementMouseClick;

import buildcraft.lib.misc.LocaleUtil;
import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.lib.misc.StackUtil;

import buildcraft.builders.BCBuildersSprites;

public enum PatternParameterRotation implements IStatementParameter {
    NONE,
    QUARTER,
    HALF,
    THREE_QUARTERS;

    private static final PatternParameterRotation[] POSSIBLE_ORDER =
        { null, null, NONE, null, QUARTER, null, HALF, null, THREE_QUARTERS };

    public final int rotationCount;

    PatternParameterRotation() {
        this.rotationCount = ordinal();
    }

    public static PatternParameterRotation readFromNbt(CompoundTag nbt) {
        int d = NBTUtilBC.getByte(nbt, "d", (byte) 0);
        return values()[d & 3];
    }

    @Override
    public void writeToNbt(CompoundTag nbt) {
        nbt.putByte("d", (byte) rotationCount);
    }

    @Override
    public String getUniqueTag() {
        return "buildcraft:fillerParameterRotation";
    }

    @Override
    public ISprite getSprite() {
        return BCBuildersSprites.PARAM_ROTATION[rotationCount];
    }

    @Nonnull
    @Override
    public ItemStack getItemStack() {
        return StackUtil.EMPTY;
    }

    @Override
    public String getDescription() {
        return LocaleUtil.localize("buildcraft.param.rotation." + rotationCount);
    }

    @Override
    public PatternParameterRotation onClick(IStatementContainer source, IStatement stmt, ItemStack stack,
        StatementMouseClick mouse) {
        return null;
    }

    /** Not turned on its own: a quarter-turn count means a different turn of the world depending on the parameters
     *  beside it (the plane a 2D shape lies in, the face a sphere part opens on), so the pattern that reads it turns it
     *  with them ({@code BCStatement.rotateParameters}, which those patterns override). */
    @Override
    public IStatementParameter rotateLeft() {
        return this;
    }

    @Override
    public IStatementParameter[] getPossible(IStatementContainer source) {
        return POSSIBLE_ORDER;
    }

    @Override
    public boolean isPossibleOrdered() {
        return true;
    }
}
