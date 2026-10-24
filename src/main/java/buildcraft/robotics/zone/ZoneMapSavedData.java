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

import com.google.common.collect.MapMaker;
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
 * <p>An entry added to the store in this session is not yet {@linkplain #isDurable durable}; the legacy migration
 * keeps a stack's own copy of its plan until it is, so neither a crash between the player-data save and the level-data
 * save nor a level-data write that fails (vanilla only logs it) can leave a map whose only copy of its zone was
 * dropped. An entry becomes durable when the store's file on disk is read back holding it ({@link #onLevelSave}); the
 * encode for a save proves nothing, since vanilla clears the dirty flag right after it and writes later, on another
 * thread.
 *
 * <p>Zones are handed out as copies, so no caller can edit the stored one.
 */
public class ZoneMapSavedData extends SavedData implements LegacySavedData.Recoverable {
    /** The saved-data id, so the file is {@code <name>.dat} under every layout. Never changes. */
    private static final String NAME = "buildcraft_zone_maps";

    /** Declared before {@link #TYPE}, which reads it during class init. Public so a test in another package can encode
     *  a store the way a level save does. */
    public static final Codec<ZoneMapSavedData> CODEC = CompoundTag.CODEC.flatXmap(
        tag -> {
            ZoneMapSavedData data = new ZoneMapSavedData();
            data.readFromTag(tag);
            return DataResult.success(data);
        },
        data -> DataResult.success(data.writeToTag())
    );

    //? if >=26.1 {
    public static final SavedDataType<ZoneMapSavedData> TYPE = LegacySavedData.type(
        NAME,
        level -> new ZoneMapSavedData(),
        level -> CODEC,
        DataFixTypes.SAVED_DATA_MAP_DATA
    );
    //?} elif >=1.21.10 {
    /*public static final SavedDataType<ZoneMapSavedData> TYPE = new SavedDataType<>(
        NAME,
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
    /** Ids added in this session whose entry has not been seen in the store's file on disk yet. */
    private final Set<Integer> unsaved = new HashSet<>();
    /** {@link ZonePlan#contentHash} to the ids holding a zone with that hash; built on the first {@link #intern}. */
    @Nullable
    private Map<Integer, List<Integer>> idsByContent;
    /** Carried plans (by identity) already found to mark what the entry of the ref beside them marks; see
     *  {@link #isCheckedCarrier}. Weak keys, compared by identity: a carrier nothing holds any more drops out. */
    private final Map<Object, ZoneMapRef> checkedCarriers = new MapMaker().weakKeys().makeMap();

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
        /*return overworld.getDataStorage().computeIfAbsent(TYPE, NAME);*/
        //?}
    }

    /**
     * The level-save hook ({@code LevelEvent.Save}, server thread): the overworld's store reads its own file back and
     * {@linkplain #confirmOnDisk confirms} the entries in it. The event fires after the level's data storage encoded
     * and started writing. From 1.21.10 a flushed save (autosave aside) has also joined its write by then, while an
     * autosave's may still be under way; on 1.21.1 no save has, because the event is posted before NeoForge waits for
     * its IO worker, so even a flushed save's write may be pending. What the file shows is therefore that save's write
     * or an earlier one, and an entry is durable a save or two after it was added (on 1.21.1 a shutdown save's entries
     * may confirm only at the next launch, which loads them from the file). A write that failed is made again. Does
     * nothing, with no file read, while nothing is unconfirmed. The file is read, not asked of the storage, because
     * no save API on any node says whether a write landed (vanilla logs a failed one and completes normally).
     */
    public static void onLevelSave(ServerLevel level) {
        MinecraftServer server = level.getServer();
        if (level != server.overworld()) {
            return;
        }
        // get, not computeIfAbsent: a store nothing has used this session has nothing to confirm, and must not be made.
        //? if >=1.21.10 {
        ZoneMapSavedData store = level.getDataStorage().get(TYPE);
        //?} else {
        /*ZoneMapSavedData store = level.getDataStorage().get(TYPE, NAME);*/
        //?}
        if (store == null || store.unsaved.isEmpty()) {
            return;
        }
        store.confirmOnDisk(LegacySavedData.readPayload(LegacySavedData.overworldDataFile(server, NAME)));
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

    /** Whether the zone this store holds under {@code ref} marks exactly {@code plan}'s columns (false when it holds
     *  none there). Compared in place, without the copy {@link #get} hands out. */
    public boolean holdsSame(ZoneMapRef ref, ZonePlan plan) {
        ZonePlan stored = ref.store() == token ? maps.get(ref.id()) : null;
        return stored != null && stored.sameContent(plan);
    }

    /** Whether {@code ref} names a zone in this store that is on disk: loaded from the store's file, or seen in it
     *  since ({@link #confirmOnDisk}). Not merely encoded for a save, which does not say whether the write landed.
     *  Always, for the detached store, which is never saved. False for a ref this store does not hold. */
    public boolean isDurable(ZoneMapRef ref) {
        return holds(ref) && (this == DETACHED || !unsaved.contains(ref.id()));
    }

    /**
     * Whether {@code carrier} (an immutable value holding a plan, such as a stack's custom data, compared by identity)
     * was {@linkplain #markCheckedCarrier marked} as marking exactly what this store holds under {@code ref}. Entries
     * never change while the store lives, so a match found once holds until the store is reloaded; a caller can then
     * skip parsing and comparing the plan again.
     */
    public boolean isCheckedCarrier(Object carrier, ZoneMapRef ref) {
        return ref.equals(checkedCarriers.get(carrier)) && holds(ref);
    }

    /** Records that {@code carrier}'s plan marks exactly what this store holds under {@code ref}. */
    public void markCheckedCarrier(Object carrier, ZoneMapRef ref) {
        checkedCarriers.put(carrier, ref);
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
        return writeToTag();
    }*/
    //?}

    /**
     * Called with the store's file as it is on disk (its {@code data} payload; null when there is none or it cannot be
     * read): every entry of {@code onDisk} that is this store's own (same token) becomes
     * {@linkplain #isDurable durable}. An entry still missing from the file is marked dirty again, so the next save
     * writes it again.
     */
    public void confirmOnDisk(@Nullable CompoundTag onDisk) {
        if (onDisk != null && onDisk.contains("token") && NBTUtilBC.getLong(onDisk, "token", 0L) == token) {
            // Entries never change and an id is never reused under one token, so an id in the file is the entry.
            ListTag list = NBTUtilBC.getList(onDisk, "maps", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                unsaved.remove(NBTUtilBC.getInt(NBTUtilBC.getCompound(list, i), "id", -1));
            }
        }
        if (!unsaved.isEmpty()) {
            setDirty();
        }
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
        checkedCarriers.clear();
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
