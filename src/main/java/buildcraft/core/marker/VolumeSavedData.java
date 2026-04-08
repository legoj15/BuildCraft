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
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Persists both marker positions and connections for volume markers.
 * Mirrors the 1.12.2 MarkerSavedData behavior: on save, the codec
 * dynamically reads the current state from the subCache so that
 * the persisted data is always up-to-date.
 */
public class VolumeSavedData extends SavedData {
    public static final String ID = "buildcraft_marker_volume";

    public List<BlockPos> markerPositions = new ArrayList<>();
    public List<List<BlockPos>> markerConnections = new ArrayList<>();

    /** Set after VolumeSubCache is created so the codec can pull live state. */
    private VolumeSubCache subCache;

    private VolumeSavedData() {
    }

    public void setSubCache(VolumeSubCache subCache) {
        this.subCache = subCache;
    }

    /**
     * Snapshot the live subCache state into our fields.
     * Called by the codec just before serialization.
     */
    private void syncFromSubCache() {
        if (subCache == null) return;
        markerPositions = new ArrayList<>(subCache.getAllMarkers());
        markerConnections = new ArrayList<>();
        for (VolumeConnection connection : subCache.getConnections()) {
            markerConnections.add(new ArrayList<>(connection.getMarkerPositions()));
        }
    }

    // -------------------------------------------------------------------------
    // Codec and SavedDataType
    // -------------------------------------------------------------------------

    private static final Codec<VolumeSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.listOf().optionalFieldOf("markers", List.of())
                    .forGetter(d -> {
                        d.syncFromSubCache();
                        return d.markerPositions;
                    }),
            BlockPos.CODEC.listOf().listOf().optionalFieldOf("connections", List.of())
                    .forGetter(d -> d.markerConnections)
    ).apply(instance, (positions, connections) -> {
        VolumeSavedData data = new VolumeSavedData();
        data.markerPositions = new ArrayList<>(positions);
        data.markerConnections = new ArrayList<>();
        for (List<BlockPos> conn : connections) {
            data.markerConnections.add(new ArrayList<>(conn));
        }
        return data;
    }));

    public static final SavedDataType<VolumeSavedData> TYPE = new SavedDataType<>(
            Identifier.withDefaultNamespace(ID),
            VolumeSavedData::new,
            CODEC,
            net.minecraft.util.datafix.DataFixTypes.LEVEL
    );

    // -------------------------------------------------------------------------
    // Access
    // -------------------------------------------------------------------------

    public static VolumeSavedData getOrCreate(Level level) {
        if (level.isClientSide()) return new VolumeSavedData();
        return ((net.minecraft.server.level.ServerLevel) level)
                .getDataStorage().computeIfAbsent(TYPE);
    }
}
