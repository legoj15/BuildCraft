/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.snapshot;

import java.io.File;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import buildcraft.lib.misc.HashUtil;

/**
 * The cached list of snapshots stored in one snapshot directory, in display order. Each file is decompressed once;
 * a {@link #rescan} re-reads only files that are new or changed on disk (by size and modification time), so it is
 * cheap enough to repeat. {@link #put}/{@link #remove} keep the index current for the saves and deletes the game
 * makes itself, and for the files a lookup has just read, without touching disk again.
 * <p>
 * Thread-safe: a rescan reads files outside the lock (it is meant to run off the render thread) and merges under
 * it; a put or remove that lands while a scan is reading wins over what that scan saw.
 */
public final class SnapshotIndex {
    /** Name (case-insensitive, then exact), then newest first, then hash — a stable order for the library list.
     *  Keys without a header (none are written today) sort last. */
    public static final Comparator<Snapshot.Key> ORDER = Comparator
        .comparing((Snapshot.Key k) -> k.header == null ? null : k.header.name,
            Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder())))
        .thenComparing((Snapshot.Key k) -> k.header == null ? 0L : k.header.created.getTime(),
            Comparator.reverseOrder())
        .thenComparing(k -> HashUtil.convertHashToString(k.hash));

    /** One file: its key ({@code null} if its bytes are not a snapshot named after its key, so a corrupt file is not
     *  re-read every scan) and the size/time it had when read. A file that could not be read at all (in use
     *  elsewhere, gone) gets no entry, so the next scan or lookup tries it again. */
    private record Entry(@Nullable Snapshot.Key key, long modified, long length) {}

    private Map<String, Entry> entries = new HashMap<>();
    /** File names put or removed while a scan is in flight; the scan leaves them as they are now. */
    private final Set<String> touchedDuringScan = new HashSet<>();
    private int scansInFlight;
    private volatile List<Snapshot.Key> sorted = List.of();
    /** The same keys as {@link #sorted}, for {@link #contains}; replaced whole by {@link #publish}. */
    private volatile Set<Snapshot.Key> keys = Collections.emptySet();

    /** The indexed snapshots in {@link #ORDER}. Never blocks; immutable. */
    public List<Snapshot.Key> list() {
        return sorted;
    }

    /** Whether a file holding exactly {@code key} (header included) is indexed. A set lookup: the library screen asks
     *  this every tick, and it never waits on a save or a scan. */
    public boolean contains(Snapshot.Key key) {
        return keys.contains(key);
    }

    /**
     * Whether the index already knows that {@code fileName} holds nothing that {@code answers}, so a lookup can skip
     * decompressing it: the file is indexed with exactly this size and modification time (so it has not changed since
     * it was read), and its key is either unreadable or not one {@code answers} accepts. A file the index has not seen,
     * or that has changed since, is never ruled out: only reading it can say. Nor is one that could not be read at
     * all, since such a file is never entered.
     */
    public synchronized boolean rulesOut(String fileName, long modified, long length, Predicate<Snapshot.Key> answers) {
        Entry e = entries.get(fileName);
        return e != null && e.modified == modified && e.length == length && (e.key == null || !answers.test(e.key));
    }

    /** Whether the index already knows that {@code fileName}, unchanged (same size and modification time) since it was
     *  read, holds exactly {@code key}: the positive counterpart of {@link #rulesOut}. */
    public synchronized boolean knownToHold(String fileName, long modified, long length, Snapshot.Key key) {
        Entry e = entries.get(fileName);
        return e != null && e.modified == modified && e.length == length && key.equals(e.key);
    }

    /** Record what {@code fileName} holds, as of the given size and time: a snapshot the game just wrote, or what a
     *  lookup just read there ({@code key} null if that was not a snapshot named after its key). */
    public synchronized void put(String fileName, @Nullable Snapshot.Key key, long modified, long length) {
        entries.put(fileName, new Entry(key, modified, length));
        if (scansInFlight > 0) touchedDuringScan.add(fileName);
        publish();
    }

    /**
     * {@link #put} for what a lookup just read from {@code file}, unless the file is no longer there, in which case
     * nothing is recorded. The check is made under the index's lock, which is what makes it hold: a re-key on the scan
     * thread deletes the file it moves and only then calls {@link #remove}, so a lookup that read the file before it
     * went either sees it missing here, or records first and has its entry removed right after. Without it the
     * lookup's entry could land after the removal and list a key whose file is gone until the next scan.
     *
     * @return whether it was recorded
     */
    public synchronized boolean putIfPresent(File file, @Nullable Snapshot.Key key, long modified, long length) {
        if (!file.isFile()) return false;
        put(file.getName(), key, modified, length);
        return true;
    }

    /** Forget {@code fileName} (the game just deleted it). */
    public synchronized void remove(String fileName) {
        entries.remove(fileName);
        if (scansInFlight > 0) touchedDuringScan.add(fileName);
        publish();
    }

    /**
     * Bring the index in line with {@code dir}: drop entries whose file is gone (anything that is not a regular file
     * is never listed), read files that are new or changed
     * with {@code reader}, keep the rest as they are. The reader returns null for a file whose bytes are not a
     * snapshot (remembered, so it is not re-read until it changes), and throws {@link UncheckedIOException} for one it
     * could not read at all (left out, so the next scan tries it again). A directory that cannot be listed leaves the
     * index unchanged.
     */
    public void rescan(File dir, String extension, Function<File, Snapshot.Key> reader) {
        Map<String, Entry> known;
        synchronized (this) {
            scansInFlight++;
            known = new HashMap<>(entries);
        }
        try {
            File[] files = dir.listFiles();
            if (files == null) return;
            Map<String, Entry> fresh = new HashMap<>();
            for (File file : files) {
                String name = file.getName();
                // A directory named like a snapshot can never be read: handing it to the reader would fail, leave no
                // entry, and so be retried (and logged) on every scan.
                if (!name.endsWith(extension) || !file.isFile()) continue;
                long modified = file.lastModified();
                long length = file.length();
                Entry old = known.get(name);
                if (old != null && old.modified == modified && old.length == length) {
                    fresh.put(name, old);
                } else {
                    try {
                        fresh.put(name, new Entry(reader.apply(file), modified, length));
                    } catch (UncheckedIOException e) {
                        // Unreadable right now (another program holds it, or it was just deleted): no entry.
                    }
                }
            }
            synchronized (this) {
                for (String name : touchedDuringScan) {
                    Entry now = entries.get(name);
                    if (now == null) {
                        fresh.remove(name);
                    } else {
                        fresh.put(name, now);
                    }
                }
                entries = fresh;
                publish();
            }
        } finally {
            synchronized (this) {
                if (--scansInFlight == 0) touchedDuringScan.clear();
            }
        }
    }

    private void publish() {
        Set<Snapshot.Key> distinct = new LinkedHashSet<>();
        for (Entry e : entries.values()) {
            if (e.key != null) distinct.add(e.key);
        }
        keys = Collections.unmodifiableSet(distinct);
        sorted = distinct.stream().sorted(ORDER).toList();
    }
}
