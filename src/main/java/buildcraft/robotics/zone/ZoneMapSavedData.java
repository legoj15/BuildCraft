/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.zone;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

//? if <1.21.10 {
/*import net.minecraft.core.HolderLookup;*/
//?}
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
//? if >=1.21.10 {
import net.minecraft.world.level.saveddata.SavedDataType;
//?}

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import org.jspecify.annotations.Nullable;

import buildcraft.lib.misc.LegacySavedData;
import buildcraft.lib.misc.NBTUtilBC;

/**
 * The zones written onto Zone Planner map locations, keyed by map id, the way vanilla keeps filled maps: the item
 * carries only a {@link ZoneMapRef} ({@code BCCore.ZONE_MAP_ID}), so a stack's data does not grow with its zone and
 * copying a stack (a creative clone of 64 maps) shares one zone instead of duplicating it. Each store has a random
 * token, written into every ref it hands out; a ref carrying another store's token (a map from another world) names
 * no zone here.
 *
 * <p>One store per server, on the overworld's data storage, since a map location names world coordinates and works
 * from any dimension's gate. Entries are immutable: {@link #intern} adds a zone and never changes one, so a map that
 * is written again gets a different id (vanilla's re-write fork) and every other copy keeps the zone it had. Interning
 * reuses the id of an entry with the same columns, which keeps the store from growing when the same layer is written
 * out again, and makes the legacy-item migration idempotent: a legacy stack whose rewrite is lost (read through a
 * copy, or not saved before a crash) migrates to the same id the next time. Content-equal writes sharing one id is a
 * deliberate departure from vanilla's always-new-id re-write: no entry ever changes, so the copies sharing it cannot
 * observe each other, and the only visible effect is that two maps of the same zone stack. Like vanilla maps, entries
 * are never collected.
 *
 * <p>An entry added since the store was last encoded for a save is not yet {@linkplain #isDurable durable}; the
 * legacy migration keeps a stack's own copy of its plan until it is, so a crash between the player-data save and the
 * level-data save cannot leave a map whose only copy of its zone was dropped.
 *
 * <p>Zones are handed out as copies, so no caller can edit the stored one.
 */
public class ZoneMapSavedData extends SavedData implements LegacySavedData.Recoverable {
    /** Declared before {@link #TYPE}, which reads it during class init. */
    static final Codec<ZoneMapSavedData> CODEC = CompoundTag.CODEC.flatXmap(
        tag -> {
            ZoneMapSavedData data = new ZoneMapSavedData();
            data.readFromTag(tag);
            return DataResult.success(data);
        },
        data -> DataResult.success(data.encodeForSave())
    );

    //? if >=26.1 {
    public static final SavedDataType<ZoneMapSavedData> TYPE = LegacySavedData.type(
        "buildcraft_zone_maps",
        level -> new ZoneMapSavedData(),
        level -> CODEC,
        DataFixTypes.SAVED_DATA_MAP_DATA
    );
    //?} elif >=1.21.10 {
    /*public static final SavedDataType<ZoneMapSavedData> TYPE = new SavedDataType<>(
        "buildcraft_zone_maps",
        ZoneMapSavedData::new,
        CODEC,
        DataFixTypes.SAVED_DATA_MAP_DATA
    );*/
    //?} else {
    /*// 1.21.1: no SavedDataType — use SavedData.Factory (ctor, (tag,provider)->T deserializer, DataFixTypes).
    public static final SavedData.Factory<ZoneMapSavedData> TYPE = new SavedData.Factory<>(
        ZoneMapSavedData::new,
        (tag, provider) -> {
            ZoneMapSavedData data = new ZoneMapSavedData();
            data.readFromTag(tag);
            return data;
        },
        DataFixTypes.SAVED_DATA_MAP_DATA
    );*/
    //?}

    /** The store used when no server is running (the unit-test JVM, which has none): never saved. */
    private static final ZoneMapSavedData DETACHED = new ZoneMapSavedData();

    private final Map<Integer, ZonePlan> maps = new HashMap<>();
    private int nextId;
    /** Written into every ref this store hands out; a ref with another token is from another store (another world). */
    private long token = ThreadLocalRandom.current().nextLong();
    /** Ids added since the store was last encoded for a save. */
    private final Set<Integer> unsaved = new HashSet<>();
    /** {@link ZonePlan#contentHash} to the ids holding a zone with that hash; built on the first {@link #intern}. */
    @Nullable
    private Map<Integer, List<Integer>> idsByContent;

    public ZoneMapSavedData() {}

    /**
     * The store the calling thread may use: the running server's, on the server thread; null on any other thread (a
     * client thread beside an integrated server) and on a client with no server of its own (multiplayer), which must
     * not touch or invent the server's data; the detached store on a server-side JVM with no server running (the
     * unit-test JVM).
     */
    @Nullable
    public static ZoneMapSavedData forThisThread() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return FMLEnvironment.getDist() == Dist.CLIENT ? null : DETACHED;
        }
        if (!server.isSameThread()) {
            return null;
        }
        ServerLevel overworld = server.overworld();
        //? if >=1.21.10 {
        return overworld.getDataStorage().computeIfAbsent(TYPE);
        //?} else {
        /*return overworld.getDataStorage().computeIfAbsent(TYPE, "buildcraft_zone_maps");*/
        //?}
    }

    /** A copy of the zone {@code ref} names, or null when this store holds none under it (or the ref is another
     *  store's). */
    @Nullable
    public ZonePlan get(ZoneMapRef ref) {
        ZonePlan stored = ref.store() == token ? maps.get(ref.id()) : null;
        return stored == null ? null : new ZonePlan(stored);
    }

    /** Whether this store holds a zone under {@code ref} (false for another store's ref). */
    public boolean holds(ZoneMapRef ref) {
        return ref.store() == token && maps.containsKey(ref.id());
    }

    /** Whether {@code ref} names a zone in this store that has been encoded for a save since it was added (always, for
     *  the detached store, which is never saved). False for a ref this store does not hold. */
    public boolean isDurable(ZoneMapRef ref) {
        return holds(ref) && (this == DETACHED || !unsaved.contains(ref.id()));
    }

    /** A ref to an entry marking exactly {@code plan}'s columns: an existing one, or a new one holding a copy. */
    public ZoneMapRef intern(ZonePlan plan) {
        Map<Integer, List<Integer>> index = idsByContent;
        if (index == null) {
            index = new HashMap<>();
            for (Map.Entry<Integer, ZonePlan> e : maps.entrySet()) {
                index.computeIfAbsent(e.getValue().contentHash(), k -> new ArrayList<>()).add(e.getKey());
            }
            idsByContent = index;
        }
        List<Integer> sameHash = index.computeIfAbsent(plan.contentHash(), k -> new ArrayList<>());
        for (int id : sameHash) {
            if (maps.get(id).sameContent(plan)) {
                return new ZoneMapRef(token, id);
            }
        }
        int id = nextId++;
        maps.put(id, new ZonePlan(plan));
        sameHash.add(id);
        unsaved.add(id);
        setDirty();
        return new ZoneMapRef(token, id);
    }

    /** How many zones the store holds. */
    public int size() {
        return maps.size();
    }

    @Override
    public boolean hasNoContent() {
        return maps.isEmpty();
    }

    @Override
    public String describeContent() {
        return maps.size() + " zone maps";
    }

    //? if <1.21.10 {
    /*// 1.21.1: SavedData.save(CompoundTag, Provider) is abstract (no Codec-driven SavedDataType).
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        return encodeForSave();
    }*/
    //?}

    /** {@link #writeToTag} for the level save: every entry it writes counts as {@linkplain #isDurable durable}. */
    public CompoundTag encodeForSave() {
        CompoundTag tag = writeToTag();
        unsaved.clear();
        return tag;
    }

    public CompoundTag writeToTag() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("token", token);
        tag.putInt("nextId", nextId);
        ListTag list = new ListTag();
        for (Map.Entry<Integer, ZonePlan> e : maps.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("id", e.getKey());
            e.getValue().writeToNBT(entry);
            list.add(entry);
        }
        tag.put("maps", list);
        return tag;
    }

    public void readFromTag(CompoundTag tag) {
        maps.clear();
        unsaved.clear();
        idsByContent = null;
        if (tag.contains("token")) {
            token = NBTUtilBC.getLong(tag, "token", token);
        } else {
            // Written before stores had a token: keep the fresh one, and save it.
            setDirty();
        }
        int next = NBTUtilBC.getInt(tag, "nextId", 0);
        ListTag list = NBTUtilBC.getList(tag, "maps", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = NBTUtilBC.getCompound(list, i);
            int id = NBTUtilBC.getInt(entry, "id", -1);
            if (id < 0) {
                continue;
            }
            ZonePlan plan = new ZonePlan();
            plan.readFromNBT(entry);
            maps.put(id, plan);
            // A hand-edited or truncated counter must never hand out an id already in use.
            next = Math.max(next, id + 1);
        }
        nextId = next;
    }
}
