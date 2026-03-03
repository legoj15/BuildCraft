/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.core.marker;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public class PathSavedData extends SavedData {
    public static final String ID = "buildcraft_marker_path";

    public List<BlockPos> markerPositions = new ArrayList<>();

    private PathSavedData() {
    }

    // -------------------------------------------------------------------------
    // Codec and SavedDataType
    // -------------------------------------------------------------------------

    private static final Codec<PathSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.listOf().optionalFieldOf("markers", List.of())
                    .forGetter(d -> d.markerPositions)
    ).apply(instance, positions -> {
        PathSavedData data = new PathSavedData();
        data.markerPositions = new ArrayList<>(positions);
        return data;
    }));

    public static final SavedDataType<PathSavedData> TYPE = new SavedDataType<>(
            ID,
            PathSavedData::new,
            CODEC
    );

    // -------------------------------------------------------------------------
    // Access
    // -------------------------------------------------------------------------

    public static PathSavedData getOrCreate(Level level) {
        if (level.isClientSide()) return new PathSavedData();
        return ((net.minecraft.server.level.ServerLevel) level)
                .getDataStorage().computeIfAbsent(TYPE);
    }
}
