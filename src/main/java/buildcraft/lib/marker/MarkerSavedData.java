/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.marker;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
//? if >=1.21.10 {
import net.minecraft.world.level.saveddata.SavedDataType;
//?}

/**
 * Shared persistence base for the marker-connection SavedData twins
 * ({@code PathSavedData} / {@code VolumeSavedData}). It holds the identical
 * field set, the live sub-cache snapshot logic, the record CODEC builder, and
 * the cross-node {@code computeIfAbsent} lookup that both used verbatim.
 *
 * <p>Each concrete subclass keeps only its own {@code ID}, its {@code CODEC}
 * instance and its cross-node {@code TYPE} descriptor (Java cannot host
 * per-subclass statics on a shared base), plus a one-line {@code getOrCreate}
 * wrapper delegating to {@link #lookupOrCreate}.
 *
 * <p>The serialized format ({@code "markers"} + {@code "connections"} lists) is
 * byte-identical to the pre-split twins — this is a pure code-dedup, not a
 * save-format change.
 */
public abstract class MarkerSavedData extends SavedData {

    public List<BlockPos> markerPositions = new ArrayList<>();
    public List<List<BlockPos>> markerConnections = new ArrayList<>();

    /** Set after the owning sub-cache is created so the CODEC can pull live state on save. */
    private MarkerSubCache<?> subCache;

    public void setSubCache(MarkerSubCache<?> subCache) {
        this.subCache = subCache;
    }

    /**
     * Snapshot the live sub-cache state into our fields. Invoked by the CODEC
     * (see {@link #makeCodec}) just before serialization so the persisted data
     * is always up to date, mirroring the 1.12.2 MarkerSavedData behaviour.
     */
    protected void syncFromSubCache() {
        if (subCache == null) return;
        markerPositions = new ArrayList<>(subCache.getAllMarkers());
        markerConnections = new ArrayList<>();
        for (MarkerConnection<?> connection : subCache.getConnections()) {
            markerConnections.add(new ArrayList<>(connection.getMarkerPositions()));
        }
    }

    /**
     * Builds the shared record CODEC. The {@code markers} getter triggers the
     * live sub-cache snapshot, exactly as the original per-class CODECs did, so
     * field order and names ("markers", then "connections") stay byte-identical.
     */
    protected static <T extends MarkerSavedData> Codec<T> makeCodec(Supplier<T> factory) {
        return RecordCodecBuilder.create(instance -> instance.group(
                BlockPos.CODEC.listOf().optionalFieldOf("markers", List.of())
                        .forGetter((T d) -> {
                            d.syncFromSubCache();
                            return d.markerPositions;
                        }),
                BlockPos.CODEC.listOf().listOf().optionalFieldOf("connections", List.of())
                        .forGetter((T d) -> d.markerConnections)
        ).apply(instance, (positions, connections) -> {
            T data = factory.get();
            data.markerPositions = new ArrayList<>(positions);
            data.markerConnections = new ArrayList<>();
            for (List<BlockPos> conn : connections) {
                data.markerConnections.add(new ArrayList<>(conn));
            }
            return data;
        }));
    }

    // -------------------------------------------------------------------------
    // Cross-node computeIfAbsent — the one //? SavedData seam the twins shared
    // (beyond their per-type TYPE descriptor), kept here ONCE. Client side always
    // returns a fresh detached instance. On modern nodes the id is embedded in the
    // SavedDataType, so the trailing id argument is only consumed on 1.21.1; passing
    // it unconditionally keeps every subclass's getOrCreate directive-free.
    // -------------------------------------------------------------------------

    //? if >=1.21.10 {
    protected static <T extends MarkerSavedData> T lookupOrCreate(Level level, Supplier<T> factory,
            SavedDataType<T> type, String id) {
        if (level.isClientSide()) return factory.get();
        return ((ServerLevel) level).getDataStorage().computeIfAbsent(type);
    }
    //?} else {
    /*protected static <T extends MarkerSavedData> T lookupOrCreate(Level level, Supplier<T> factory,
            net.minecraft.world.level.saveddata.SavedData.Factory<T> type, String id) {
        if (level.isClientSide()) return factory.get();
        return ((ServerLevel) level).getDataStorage().computeIfAbsent(type, id);
    }*/
    //?}
}
