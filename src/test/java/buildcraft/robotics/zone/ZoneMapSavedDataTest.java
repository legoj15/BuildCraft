/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.zone;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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

    /** The legacy migration drops a stack's own copy of its plan only once the entry is in a level save. */
    @Test
    public void anEntryIsDurableOnlyOnceEncodedForASave() {
        ZoneMapSavedData store = new ZoneMapSavedData();
        ZoneMapRef ref = store.intern(plan(7, 7));
        Assertions.assertFalse(store.isDurable(ref), "a new entry is in no save yet");
        store.encodeForSave();
        Assertions.assertTrue(store.isDurable(ref), "encoded for a save, it is");
        ZoneMapRef next = store.intern(plan(8, 8));
        Assertions.assertFalse(store.isDurable(next), "an entry added after that save is not");
        Assertions.assertTrue(store.isDurable(ref), "and the saved one stays durable");
    }
}
