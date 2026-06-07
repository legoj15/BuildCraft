/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.misc;

import java.util.Optional;

import com.mojang.serialization.Codec;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

//? if >=1.21.10 {
import net.minecraft.world.level.storage.ValueInput;
//?}

/**
 * Version-neutral read side of BlockEntity serialization. On MC 1.21.5+ this wraps a
 * {@code net.minecraft.world.level.storage.ValueInput}; on 1.21.1 (which has no ValueInput)
 * it wraps a {@link CompoundTag}. BuildCraft tiles override {@code TileBC_Neptune#readData}
 * and use this uniform API.
 *
 * <p>Method names mirror {@code ValueInput} so a tile's {@code readData} body is identical to
 * its old {@code loadAdditional(ValueInput)} body. On 1.21.1 the {@code getXOr} accessors route
 * through {@link NBTUtilBC} (which papers over the missing Optional/getXOr getters).
 */
public final class BCValueInput {
    //? if >=1.21.10 {
    public final ValueInput raw;

    public BCValueInput(ValueInput raw) {
        this.raw = raw;
    }
    //?} else {
    /*public final CompoundTag raw;

    public BCValueInput(CompoundTag raw) {
        this.raw = raw;
    }*/
    //?}

    public byte getByteOr(String key, byte def) {
        //? if >=1.21.10 {
        return raw.getByteOr(key, def);
        //?} else {
        /*return NBTUtilBC.getByte(raw, key, def);*/
        //?}
    }

    public short getShortOr(String key, short def) {
        //? if >=1.21.10 {
        return (short) raw.getShortOr(key, def); // ValueInput.getShortOr widens to int
        //?} else {
        /*return NBTUtilBC.getShort(raw, key, def);*/
        //?}
    }

    public int getIntOr(String key, int def) {
        //? if >=1.21.10 {
        return raw.getIntOr(key, def);
        //?} else {
        /*return NBTUtilBC.getInt(raw, key, def);*/
        //?}
    }

    public long getLongOr(String key, long def) {
        //? if >=1.21.10 {
        return raw.getLongOr(key, def);
        //?} else {
        /*return NBTUtilBC.getLong(raw, key, def);*/
        //?}
    }

    public float getFloatOr(String key, float def) {
        //? if >=1.21.10 {
        return raw.getFloatOr(key, def);
        //?} else {
        /*return NBTUtilBC.getFloat(raw, key, def);*/
        //?}
    }

    public double getDoubleOr(String key, double def) {
        //? if >=1.21.10 {
        return raw.getDoubleOr(key, def);
        //?} else {
        /*return NBTUtilBC.getDouble(raw, key, def);*/
        //?}
    }

    public boolean getBooleanOr(String key, boolean def) {
        //? if >=1.21.10 {
        return raw.getBooleanOr(key, def);
        //?} else {
        /*return NBTUtilBC.getBoolean(raw, key, def);*/
        //?}
    }

    public String getStringOr(String key, String def) {
        //? if >=1.21.10 {
        return raw.getStringOr(key, def);
        //?} else {
        /*return NBTUtilBC.getString(raw, key, def);*/
        //?}
    }

    /** Mirrors ValueInput.getIntArray(key) -> Optional<int[]>. */
    public Optional<int[]> getIntArray(String key) {
        //? if >=1.21.10 {
        return raw.getIntArray(key);
        //?} else {
        /*return raw.contains(key) ? Optional.of(raw.getIntArray(key)) : Optional.empty();*/
        //?}
    }

    /** Read a value via its codec (e.g. CompoundTag.CODEC, FluidStack.CODEC). Empty if absent/invalid. */
    public <T> Optional<T> read(String name, Codec<T> codec) {
        //? if >=1.21.10 {
        return raw.read(name, codec);
        //?} else {
        /*Tag t = raw.get(name);
        return t == null ? Optional.empty() : codec.parse(NBTUtilBC.registryAwareOps(), t).result();*/
        //?}
    }

    /** A nested compound child for reading (mirrors ValueInput.child). Empty if absent. */
    public Optional<BCValueInput> child(String name) {
        //? if >=1.21.10 {
        return raw.child(name).map(BCValueInput::new);
        //?} else {
        /*return raw.contains(name, Tag.TAG_COMPOUND) ? Optional.of(new BCValueInput(raw.getCompound(name))) : Optional.empty();*/
        //?}
    }
}
