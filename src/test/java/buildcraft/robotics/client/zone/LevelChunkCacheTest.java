/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.client.zone;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Pure-JUnit pins for {@link LevelChunkCache}, the cache behind the Zone Planner's terrain colours. The cache is
 * generic over the level type so it can be exercised with plain objects: what is under test is its keying and
 * lifetime rules, not Minecraft chunks.
 */
public class LevelChunkCacheTest {

    /** Builds a distinct {@code "<level>@<x>,<z>#<n>"} value per call and logs each build. */
    private static final class Source implements LevelChunkCache.Builder<String, String> {
        final List<String> builds = new ArrayList<>();
        /** Chunks that are "not loaded": the builder reports them as absent. */
        final List<String> unloaded = new ArrayList<>();

        @Override
        public String build(String level, int chunkX, int chunkZ) {
            String where = level + "@" + chunkX + "," + chunkZ;
            if (unloaded.contains(where)) {
                return null;
            }
            String value = where + "#" + builds.size();
            builds.add(value);
            return value;
        }
    }

    private final Source source = new Source();
    private final LevelChunkCache<String, String> cache = new LevelChunkCache<>(source);

    // The cache holds levels weakly, so the tests keep their own strong references.
    private final String overworld = new String("overworld");
    private final String nether = new String("nether");

    @Test
    public void testBuildsOnceAndServesTheCachedValue() {
        String first = cache.get(overworld, 3, -4);
        Assertions.assertEquals("overworld@3,-4#0", first);
        Assertions.assertSame(first, cache.get(overworld, 3, -4), "the second look is the cached instance");
        Assertions.assertEquals(1, source.builds.size());

        cache.get(overworld, -4, 3);
        Assertions.assertEquals(2, source.builds.size(), "x and z are not interchangeable");
    }

    @Test
    public void testAnotherLevelNeverSeesThisLevelsChunks() {
        String over = cache.get(overworld, 5, 5);
        String under = cache.get(nether, 5, 5);
        Assertions.assertNotSame(over, under);
        Assertions.assertEquals("nether@5,5#1", under, "the nether's chunk is built from the nether");

        // The cache follows the level it is asked about; going back rebuilds rather than serving stale terrain.
        String again = cache.get(overworld, 5, 5);
        Assertions.assertNotSame(over, again);
        Assertions.assertEquals(3, source.builds.size());
    }

    @Test
    public void testClearDropsEverything() {
        cache.get(overworld, 0, 0);
        cache.get(overworld, 1, 0);
        Assertions.assertEquals(2, cache.size());
        cache.clear();
        Assertions.assertEquals(0, cache.size());
        cache.get(overworld, 0, 0);
        Assertions.assertEquals(3, source.builds.size(), "rebuilt after a clear");
    }

    @Test
    public void testInvalidateRebuildsOnlyThatChunk() {
        String a = cache.get(overworld, 0, 0);
        String b = cache.get(overworld, 1, 0);
        cache.invalidate(overworld, 0, 0);
        Assertions.assertNotSame(a, cache.get(overworld, 0, 0), "the invalidated chunk is rebuilt");
        Assertions.assertSame(b, cache.get(overworld, 1, 0), "its neighbour is untouched");
    }

    @Test
    public void testInvalidatingAnotherLevelsChunkChangesNothing() {
        String a = cache.get(overworld, 0, 0);
        cache.invalidate(nether, 0, 0);
        Assertions.assertSame(a, cache.get(overworld, 0, 0));
    }

    @Test
    public void testAChunkThatIsNotLoadedIsNotCachedAsEmpty() {
        source.unloaded.add("overworld@9,9");
        Assertions.assertNull(cache.get(overworld, 9, 9));
        Assertions.assertEquals(0, cache.size(), "absence is not stored");
        source.unloaded.clear();
        Assertions.assertNotNull(cache.get(overworld, 9, 9), "it is built as soon as it loads");
    }

    // ── Time-based refresh ──────────────────────────────────────────────────────────────

    private void age(int ticks) {
        for (int i = 0; i < ticks; i++) {
            cache.tick();
        }
    }

    @Test
    public void testAFreshEntryIsNotRebuilt() {
        String a = cache.get(overworld, 0, 0);
        age(LevelChunkCache.REFRESH_AFTER_TICKS - 1);
        Assertions.assertSame(a, cache.get(overworld, 0, 0));
    }

    @Test
    public void testAStaleEntryIsRebuiltOnItsNextLook() {
        String a = cache.get(overworld, 0, 0);
        age(LevelChunkCache.REFRESH_AFTER_TICKS);
        String b = cache.get(overworld, 0, 0);
        Assertions.assertNotSame(a, b, "block changes made since the build show up");
        Assertions.assertSame(b, cache.get(overworld, 0, 0), "and the rebuilt one is fresh again");
    }

    @Test
    public void testRefreshesAreBudgetedPerTick() {
        int chunks = LevelChunkCache.REFRESHES_PER_TICK + 3;
        List<String> before = new ArrayList<>();
        for (int x = 0; x < chunks; x++) {
            before.add(cache.get(overworld, x, 0));
        }
        age(LevelChunkCache.REFRESH_AFTER_TICKS);

        int rebuilt = 0;
        for (int x = 0; x < chunks; x++) {
            if (cache.get(overworld, x, 0) != before.get(x)) {
                rebuilt++;
            }
        }
        Assertions.assertEquals(LevelChunkCache.REFRESHES_PER_TICK, rebuilt,
                "only the per-tick budget is rebuilt; the rest are served stale, not stalled");

        cache.tick();
        for (int x = 0; x < chunks; x++) {
            cache.get(overworld, x, 0);
        }
        int stillOld = 0;
        for (int x = 0; x < chunks; x++) {
            if (cache.get(overworld, x, 0) == before.get(x)) {
                stillOld++;
            }
        }
        Assertions.assertEquals(0, stillOld, "the next tick's budget finishes the job");
    }

    @Test
    public void testARefreshOfAChunkThatHasUnloadedDropsIt() {
        cache.get(overworld, 2, 2);
        source.unloaded.add("overworld@2,2");
        age(LevelChunkCache.REFRESH_AFTER_TICKS);
        Assertions.assertNull(cache.get(overworld, 2, 2));
        Assertions.assertEquals(0, cache.size());
    }
}
