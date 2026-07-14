/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.core.marker;

import com.mojang.serialization.Codec;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
//? if >=1.21.10 {
import net.minecraft.world.level.saveddata.SavedDataType;
//?}

import buildcraft.lib.marker.MarkerSavedData;

/**
 * Persists marker positions and connections for volume markers. All shared logic
 * (fields, live sub-cache snapshot, CODEC construction, cross-node lookup) lives
 * in {@link MarkerSavedData}; this class only carries the per-type id, CODEC
 * instance and cross-node TYPE descriptor. Serialized format is unchanged.
 */
public class VolumeSavedData extends MarkerSavedData {
    public static final String ID = "buildcraft_marker_volume";

    private VolumeSavedData() {
    }

    private static final Codec<VolumeSavedData> CODEC = makeCodec(VolumeSavedData::new);

    //? if >=26.1 {
    public static final SavedDataType<VolumeSavedData> TYPE = new SavedDataType<>(
            Identifier.withDefaultNamespace(ID),
            VolumeSavedData::new,
            CODEC,
            net.minecraft.util.datafix.DataFixTypes.LEVEL
    );
    //?} elif >=1.21.10 {
    /*public static final SavedDataType<VolumeSavedData> TYPE = new SavedDataType<>(
            ID,
            VolumeSavedData::new,
            CODEC,
            net.minecraft.util.datafix.DataFixTypes.LEVEL
    );*/
    //?} else {
    /*// 1.21.1: SavedData.Factory (ctor, (tag,provider)->T via CODEC, DataFixTypes).
    public static final net.minecraft.world.level.saveddata.SavedData.Factory<VolumeSavedData> TYPE =
            new net.minecraft.world.level.saveddata.SavedData.Factory<>(
            VolumeSavedData::new,
            (tag, provider) -> CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag).result().orElseGet(VolumeSavedData::new),
            net.minecraft.util.datafix.DataFixTypes.LEVEL
    );

    @Override
    public net.minecraft.nbt.CompoundTag save(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        // 1.21.1: serialize via the same CODEC the SavedDataType uses on modern nodes.
        return (net.minecraft.nbt.CompoundTag) CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, this)
                .result().orElseGet(net.minecraft.nbt.CompoundTag::new);
    }*/
    //?}

    public static VolumeSavedData getOrCreate(Level level) {
        return lookupOrCreate(level, VolumeSavedData::new, TYPE, ID);
    }
}
