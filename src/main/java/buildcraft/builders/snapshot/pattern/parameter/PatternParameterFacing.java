/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.snapshot.pattern.parameter;

import java.util.EnumMap;
import java.util.Map;

import javax.annotation.Nonnull;

import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.Direction;

import buildcraft.api.core.render.ISprite;
import buildcraft.api.statements.IStatement;
import buildcraft.api.statements.IStatementContainer;
import buildcraft.api.statements.IStatementParameter;
import buildcraft.api.statements.StatementMouseClick;

import buildcraft.lib.misc.LocaleUtil;
import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.lib.misc.StackUtil;

import buildcraft.builders.BCBuildersSprites;

public enum PatternParameterFacing implements IStatementParameter {
    DOWN(Direction.DOWN),
    UP(Direction.UP),
    NORTH(Direction.NORTH),
    SOUTH(Direction.SOUTH),
    WEST(Direction.WEST),
    EAST(Direction.EAST);

    public final Direction face;

    private static final Map<Direction, PatternParameterFacing> faceToParam;

    static {
        faceToParam = new EnumMap<>(Direction.class);
        for (PatternParameterFacing param : values()) {
            faceToParam.put(param.face, param);
        }
    }

    PatternParameterFacing(Direction face) {
        this.face = face;
    }

    public static PatternParameterFacing readFromNbt(CompoundTag nbt) {
        // An index that is no constant (a hand-edited or damaged tag) reads as DOWN, the default a missing value gives
        // and the sphere part's own; PatternParameterCenter falls back to its default the same way.
        int index = NBTUtilBC.getByte(nbt, "v", (byte) 0);
        return index >= 0 && index < values().length ? values()[index] : DOWN;
    }

    public static PatternParameterFacing get(Direction face) {
        return faceToParam.get(face);
    }

    @Override
    public void writeToNbt(CompoundTag nbt) {
        nbt.putByte("v", (byte) ordinal());
    }

    @Override
    public String getUniqueTag() {
        return "buildcraft:fillerParameterFacing";
    }

    @Override
    public ISprite getSprite() {
        return BCBuildersSprites.PARAM_FACE.get(face);
    }

    @Nonnull
    @Override
    public ItemStack getItemStack() {
        return StackUtil.EMPTY;
    }

    @Override
    public String getDescription() {
        return LocaleUtil.localize("buildcraft.param.facing." + face.getSerializedName());
    }

    @Override
    public PatternParameterFacing onClick(IStatementContainer source, IStatement stmt, ItemStack stack,
        StatementMouseClick mouse) {
        return null;
    }

    /** The sphere part's open face is a world direction (the shape is laid out in world axes), so a quarter turn
     *  clockwise about Y moves a horizontal face to the next one (NORTH becomes EAST) and leaves UP and DOWN. */
    @Override
    public IStatementParameter rotateLeft() {
        return face.getAxis().isHorizontal() ? get(face.getClockWise()) : this;
    }

    @Override
    public IStatementParameter[] getPossible(IStatementContainer source) {
        return values();
    }

    @Override
    public boolean isPossibleOrdered() {
        return false;
    }
}
