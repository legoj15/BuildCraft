/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.snapshot;

import java.io.File;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import javax.annotation.Nullable;

import buildcraft.lib.misc.HashUtil;

/**
 * The cached list of snapshots stored in one snapshot directory, in display order. Each file is decompressed once;
 * a {@link #rescan} re-reads only files that are new or changed on disk (by size and modification time), so it is
 * cheap enough to repeat. {@link #put}/{@link #remove} keep the index current for the saves and deletes the game
 * makes itself, without touching disk.
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

    /** One file: its key ({@code null} if it could not be read, so a corrupt file is not re-read every scan) and
     *  the size/time it had when read. */
    private record Entry(@Nullable Snapshot.Key key, long modified, long length) {}

    private Map<String, Entry> entries = new HashMap<>();
    /** File names put or removed while a scan is in flight; the scan leaves them as they are now. */
    private final Set<String> touchedDuringScan = new HashSet<>();
    private int scansInFlight;
    private volatile List<Snapshot.Key> sorted = List.of();

    /** The indexed snapshots in {@link #ORDER}. Never blocks; immutable. */
    public List<Snapshot.Key> list() {
        return sorted;
    }

    public synchronized boolean contains(Snapshot.Key key) {
        for (Entry e : entries.values()) {
            if (key.equals(e.key)) return true;
        }
        return false;
    }

    /** Record a snapshot the game just wrote to {@code fileName}. */
    public synchronized void put(String fileName, Snapshot.Key key, long modified, long length) {
        entries.put(fileName, new Entry(key, modified, length));
        if (scansInFlight > 0) touchedDuringScan.add(fileName);
        publish();
    }

    /** Forget {@code fileName} (the game just deleted it). */
    public synchronized void remove(String fileName) {
        entries.remove(fileName);
        if (scansInFlight > 0) touchedDuringScan.add(fileName);
        publish();
    }

    /**
     * Bring the index in line with {@code dir}: drop entries whose file is gone, read files that are new or changed
     * with {@code reader} (which returns null for an unreadable file), keep the rest as they are. A directory that
     * cannot be listed leaves the index unchanged.
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
                if (!name.endsWith(extension)) continue;
                long modified = file.lastModified();
                long length = file.length();
                Entry old = known.get(name);
                if (old != null && old.modified == modified && old.length == length) {
                    fresh.put(name, old);
                } else {
                    fresh.put(name, new Entry(reader.apply(file), modified, length));
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
        sorted = entries.values().stream()
            .map(Entry::key)
            .filter(Objects::nonNull)
            .distinct()
            .sorted(ORDER)
            .toList();
    }
}
