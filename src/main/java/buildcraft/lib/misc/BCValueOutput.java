/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.misc;

import com.mojang.serialization.Codec;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

//? if >=1.21.10 {
import net.minecraft.world.level.storage.ValueOutput;
//?}

/**
 * Version-neutral write side of BlockEntity serialization. On MC 1.21.5+ this wraps a
 * {@code net.minecraft.world.level.storage.ValueOutput}; on 1.21.1 (which has no ValueOutput)
 * it wraps a {@link CompoundTag}. BuildCraft tiles override {@code TileBC_Neptune#writeData}
 * and use this uniform API.
 *
 * <p>The {@code put*} scalar methods exist with identical signatures on both {@code ValueOutput}
 * and {@link CompoundTag}, so they delegate to {@link #raw} without any per-version branching —
 * only {@link #store} (codec-based) genuinely diverges. The {@code raw} field is exposed for the
 * rare call site that needs the underlying object directly.
 */
public final class BCValueOutput {
    //? if >=1.21.10 {
    public final ValueOutput raw;

    public BCValueOutput(ValueOutput raw) {
        this.raw = raw;
    }
    //?} else {
    /*public final CompoundTag raw;

    public BCValueOutput(CompoundTag raw) {
        this.raw = raw;
    }*/
    //?}

    public void putByte(String key, byte value)       { raw.putByte(key, value); }
    public void putShort(String key, short value)     { raw.putShort(key, value); }
    public void putInt(String key, int value)         { raw.putInt(key, value); }
    public void putLong(String key, long value)       { raw.putLong(key, value); }
    public void putFloat(String key, float value)     { raw.putFloat(key, value); }
    public void putDouble(String key, double value)   { raw.putDouble(key, value); }
    public void putBoolean(String key, boolean value) { raw.putBoolean(key, value); }
    public void putString(String key, String value)   { raw.putString(key, value); }
    public void putIntArray(String key, int[] value)  { raw.putIntArray(key, value); }

    /** Store a value via its codec (e.g. CompoundTag.CODEC, FluidStack.CODEC). */
    public <T> void store(String name, Codec<T> codec, T value) {
        //? if >=1.21.10 {
        raw.store(name, codec, value);
        //?} else {
        /*codec.encodeStart(NBTUtilBC.registryAwareOps(), value).result().ifPresent((Tag t) -> raw.put(name, t));*/
        //?}
    }

    /** A nested compound child for writing (mirrors ValueOutput.child). */
    public BCValueOutput child(String name) {
        //? if >=1.21.10 {
        return new BCValueOutput(raw.child(name));
        //?} else {
        /*CompoundTag sub = new CompoundTag();
        raw.put(name, sub);
        return new BCValueOutput(sub);*/
        //?}
    }
}
