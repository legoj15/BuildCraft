/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.zone;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;

import buildcraft.lib.misc.NBTUtilBC;

/** The zone-map store: every written Zone Planner map names its zone here by id, so losing an entry or reusing an id
 *  across a save silently changes or empties the zone a robot works in. */
public class ZoneMapSavedDataTest {

    private static ZonePlan plan(int... xz) {
        ZonePlan plan = new ZonePlan();
        for (int i = 0; i < xz.length; i += 2) {
            plan.set(xz[i], xz[i + 1], true);
        }
        return plan;
    }

    @Test
    public void zonesSurviveASaveAndNewIdsDoNotReuseSavedOnes() {
        ZoneMapSavedData store = new ZoneMapSavedData();
        ZoneMapRef a = store.intern(plan(1, 2));
        ZoneMapRef b = store.intern(plan(-300, 77, 5, 5));

        ZoneMapSavedData loaded = new ZoneMapSavedData();
        loaded.readFromTag(store.writeToTag());

        Assertions.assertTrue(loaded.get(a).sameContent(plan(1, 2)), "the first zone is back under its ref");
        Assertions.assertTrue(loaded.get(b).sameContent(plan(-300, 77, 5, 5)), "the second zone is back under its ref");
        ZoneMapRef c = loaded.intern(plan(9, 9));
        Assertions.assertTrue(c.id() != a.id() && c.id() != b.id(), "a zone written after the load gets an id no saved"
                + " map holds");
        Assertions.assertTrue(loaded.get(a).sameContent(plan(1, 2)), "and the saved zone is untouched");
    }

    /** The first zone's reservation is written before its entry, so a crash between the two leaves a file holding a
     *  mark and no zone: no content, so a world with a pre-26.1 file recovers that file over it. The recovered store
     *  must keep the mark, or it hands out again the id the lost map already holds. */
    @Test
    public void aStoreRecoveredOverAReservationOnlyFileKeepsTheMark() {
        ZoneMapSavedData first = new ZoneMapSavedData();
        CompoundTag[] written = {null};
        first.usePersister(() -> written[0] = encode(first));
        ZoneMapRef lost = first.intern(plan(1, 2));

        ZoneMapSavedData blank = new ZoneMapSavedData();
        blank.readFromTag(written[0]);
        Assertions.assertTrue(blank.hasNoContent(), "precondition: the file the crash left holds no zone");

        ZoneMapSavedData recovered = new ZoneMapSavedData(); // the pre-26.1 file: no zones, no mark
        recovered.absorbBlank(blank);
        ZoneMapRef after = recovered.intern(plan(5, 5));
        Assertions.assertNotEquals(lost.id(), after.id(), "an id the lost map holds is not handed to another zone");
    }

    /** A crash restarts the store from the last saved file, so an id handed out since that save is handed out again
     *  there. A plain zone map holding it (no carried plan to fall back on) would then name another zone. */
    @Test
    public void anIdHandedOutAfterTheLastSaveIsNotReusedAfterACrash() {
        ZoneMapSavedData store = new ZoneMapSavedData();
        ZoneMapRef saved = store.intern(plan(1, 2));
        CompoundTag lastSave = encode(store);

        ZoneMapRef lost = store.intern(plan(40, 40)); // handed out after the last save; the crash loses its entry

        ZoneMapSavedData restarted = new ZoneMapSavedData();
        restarted.readFromTag(lastSave);
        ZoneMapRef after = restarted.intern(plan(-9, -9));
        Assertions.assertNotEquals(lost.id(), after.id(), "an id some map may still hold is not handed to another zone");
        Assertions.assertNotEquals(saved.id(), after.id(), "and neither is a saved one");
        Assertions.assertNull(restarted.get(lost), "the lost map names no zone, rather than somebody else's");
    }

    /** The reservation is written, and read back, before the first id from it is handed out: a crash with no level save
     *  at all since the store began (the file is only what that write left) still skips every id the session used. */
    @Test
    public void anIdIsHandedOutOnlyOnceTheFileHoldsItsReservation() {
        ZoneMapSavedData store = new ZoneMapSavedData();
        CompoundTag[] file = {null};
        int[] writes = {0};
        store.usePersister(() -> {
            writes[0]++;
            return file[0] = encode(store);
        });

        Assertions.assertEquals(0, writes[0], "nothing is written until a zone needs an id");
        int previous = -1;
        for (int i = 0; i < 2 * ZoneMapSavedData.RESERVE_BLOCK + 3; i++) {
            ZoneMapRef ref = store.intern(plan(i, i));
            Assertions.assertTrue(ref.id() > previous, "ids still count up");
            previous = ref.id();
            Assertions.assertTrue(NBTUtilBC.getInt(file[0], "reserved", 0) > ref.id(),
                    "when zone " + i + " got id " + ref.id() + " the file already reserved past it");
        }
        Assertions.assertEquals(3, writes[0], "one write per block of ids, not one per zone");

        ZoneMapSavedData restarted = new ZoneMapSavedData();
        restarted.readFromTag(file[0]);
        Assertions.assertTrue(restarted.intern(plan(-1, -1)).id() > previous,
                "a store restarted from that file starts above every id handed out");
    }

    /** A write that cannot be confirmed (vanilla only logs a failed one) must not stop a player writing a zone, and is
     *  tried again after the next level save rather than trusted, and not once per zone until then. */
    @Test
    public void anUnconfirmedReservationStillHandsOutIdsAndIsWrittenAgain() {
        ZoneMapSavedData store = new ZoneMapSavedData();
        int[] writes = {0};
        boolean[] lands = {false};
        store.usePersister(() -> {
            writes[0]++;
            return lands[0] ? encode(store) : null;
        });

        ZoneMapRef first = store.intern(plan(1, 1));
        Assertions.assertTrue(store.holds(first), "the zone is written although the file could not be confirmed");
        Assertions.assertFalse(store.isDurable(first), "and its entry is not claimed durable");
        store.intern(plan(2, 2));
        Assertions.assertEquals(1, writes[0], "a file that stays unreadable is not rewritten for every zone");

        store.confirmOnDisk(null);
        lands[0] = true;
        store.intern(plan(3, 3));
        Assertions.assertEquals(2, writes[0], "after a level save the next id tries the write again");
        store.intern(plan(4, 4));
        Assertions.assertEquals(2, writes[0], "then it holds: no more writes inside the block");
        Assertions.assertTrue(store.isDurable(first), "and the write that landed made the earlier entries durable too");
    }

    /** A file written before ids were reserved has only the counter; it must keep loading, as it always did. */
    @Test
    public void aFileFromBeforeTheReservationStillLoads() {
        CompoundTag old = new CompoundTag();
        old.putLong("token", 42L);
        old.putInt("nextId", 5);
        ListTag maps = new ListTag();
        CompoundTag entry = new CompoundTag();
        entry.putInt("id", 2);
        plan(3, 3).writeToNBT(entry);
        maps.add(entry);
        old.put("maps", maps);

        ZoneMapSavedData loaded = new ZoneMapSavedData();
        loaded.readFromTag(old);

        Assertions.assertEquals(1, loaded.size(), "the saved zone is back");
        Assertions.assertTrue(loaded.intern(plan(8, 8)).id() >= 5, "and new ids count on from the saved counter");
    }

    @Test
    public void writingTheSameColumnsAgainReusesTheirId() {
        ZoneMapSavedData store = new ZoneMapSavedData();
        ZoneMapRef first = store.intern(plan(4, 4, 100, -100));
        ZoneMapSavedData loaded = new ZoneMapSavedData();
        loaded.readFromTag(store.writeToTag());

        // Built the other way round, and after a reload (the content index is rebuilt from the saved entries).
        ZoneMapRef again = loaded.intern(plan(100, -100, 4, 4));
        Assertions.assertEquals(first, again, "the same columns map to the same entry");
        Assertions.assertEquals(1, loaded.size(), "so re-writing a layer (or re-migrating a legacy map) adds nothing");
        Assertions.assertNotEquals(first, loaded.intern(plan(4, 4)), "different columns get their own entry");
    }

    /** Ids are a per-world counter: a map carried into another world (a structure or blueprint captured with its chest)
     *  must not read that world's zone with the same number, which would put a robot to work in another area. */
    @Test
    public void aMapFromAnotherWorldNamesNoZoneThere() {
        ZoneMapSavedData home = new ZoneMapSavedData();
        ZoneMapRef farm = home.intern(plan(1, 1));
        ZoneMapSavedData elsewhere = new ZoneMapSavedData();
        ZoneMapRef mine = elsewhere.intern(plan(-50, 60));
        Assertions.assertEquals(farm.id(), mine.id(), "precondition: both worlds hand out the same first id");

        Assertions.assertNull(elsewhere.get(farm), "the other world's map names no zone here");
        Assertions.assertFalse(elsewhere.holds(farm), "and is not held here");
        Assertions.assertTrue(elsewhere.get(mine).sameContent(plan(-50, 60)), "while this world's own map still reads");
    }

    /** The legacy migration drops a stack's own copy of its plan only once the store's file on disk is seen holding the
     *  entry. The encode proves nothing: vanilla clears the dirty flag right after it and writes later, on another
     *  thread, only logging a write that fails. */
    @Test
    public void anEntryIsDurableOnlyOnceTheWrittenFileIsSeenHoldingIt() {
        ZoneMapSavedData store = new ZoneMapSavedData();
        ZoneMapRef ref = store.intern(plan(7, 7));
        Assertions.assertFalse(store.isDurable(ref), "a new entry is in no file yet");

        CompoundTag written = encode(store);
        store.setDirty(false); // what vanilla does right after an encode
        Assertions.assertFalse(store.isDurable(ref), "encoded for a save it still is not: the write may fail");

        store.confirmOnDisk(null);
        Assertions.assertFalse(store.isDurable(ref), "no file on disk (the write failed) confirms nothing");
        Assertions.assertTrue(store.isDirty(), "and the entry is written again at the next save");

        // Another world's file, with an entry under the very same id.
        ZoneMapSavedData elsewhere = new ZoneMapSavedData();
        Assertions.assertEquals(ref.id(), elsewhere.intern(plan(7, 7)).id(), "precondition: the same first id");
        store.setDirty(false);
        store.confirmOnDisk(encode(elsewhere));
        Assertions.assertFalse(store.isDurable(ref), "another store's file confirms nothing here");
        Assertions.assertTrue(store.isDirty(), "so this entry is written again");

        store.setDirty(false);
        store.confirmOnDisk(written);
        Assertions.assertTrue(store.isDurable(ref), "the file this store wrote, read back, holds it");
        Assertions.assertFalse(store.isDirty(), "and nothing is left to write again");

        ZoneMapRef next = store.intern(plan(8, 8));
        Assertions.assertFalse(store.isDurable(next), "an entry added after that file was written is not durable");
        Assertions.assertTrue(store.isDurable(ref), "and the confirmed one stays durable");
        store.setDirty(false);
        store.confirmOnDisk(written);
        Assertions.assertFalse(store.isDurable(next), "an older file does not hold the newer entry");
        Assertions.assertTrue(store.isDirty(), "which is written again");
    }

    /** What the level save writes: the storage encodes the store through its codec. */
    private static CompoundTag encode(ZoneMapSavedData store) {
        return Assertions.assertInstanceOf(CompoundTag.class,
                ZoneMapSavedData.CODEC.encodeStart(NbtOps.INSTANCE, store).getOrThrow());
    }
}
