/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.snapshot;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Covers {@link SnapshotIndex}, the Electronic Library's cached list of stored snapshots. The list used to be rebuilt
 * by decompressing every snapshot file on the render thread once a second, and came out in directory order (random,
 * since files are named by hash). The index reads each file once, re-reads only files that changed on disk, and sorts
 * by name, then newest first.
 */
public class SnapshotIndexTester {

    private static final String EXT = ".bcnbt";

    @TempDir
    Path dir;

    private static Snapshot.Key key(int hash, String name, long created) {
        Snapshot.Key bare = new Snapshot.Key(new Snapshot.Key(), new byte[] {(byte) hash, 1, 2, 3});
        return new Snapshot.Key(bare, new Snapshot.Header(bare, new UUID(0, 0), new Date(created), name));
    }

    /** A fake disk: file name -> key; reading a file counts calls. */
    private final Map<String, Snapshot.Key> disk = new HashMap<>();
    private final AtomicInteger reads = new AtomicInteger();

    private final Function<File, Snapshot.Key> reader = file -> {
        reads.incrementAndGet();
        return disk.get(file.getName());
    };

    private String write(Snapshot.Key key) throws IOException {
        String name = key.toString() + EXT;
        Files.writeString(dir.resolve(name), "x");
        disk.put(name, key);
        return name;
    }

    private static List<String> names(List<Snapshot.Key> keys) {
        List<String> names = new ArrayList<>();
        for (Snapshot.Key k : keys) {
            names.add(k.header == null ? "<" + k + ">" : k.header.name + "@" + k.header.created.getTime());
        }
        return names;
    }

    @Test
    public void theListIsSortedByNameThenNewestFirst() throws IOException {
        write(key(1, "house", 100));
        write(key(2, "Bridge", 50));
        write(key(3, "house", 300));
        write(key(4, "apple", 999));
        write(key(5, "house", 200));
        SnapshotIndex index = new SnapshotIndex();
        index.rescan(dir.toFile(), EXT, reader);
        Assertions.assertEquals(
            List.of("apple@999", "Bridge@50", "house@300", "house@200", "house@100"),
            names(index.list()),
            "names sort case-insensitively; one name's copies list newest first");
    }

    @Test
    public void aRescanReadsOnlyNewOrChangedFiles() throws IOException {
        write(key(1, "a", 1));
        write(key(2, "b", 2));
        SnapshotIndex index = new SnapshotIndex();
        index.rescan(dir.toFile(), EXT, reader);
        Assertions.assertEquals(2, reads.get());

        index.rescan(dir.toFile(), EXT, reader);
        index.rescan(dir.toFile(), EXT, reader);
        Assertions.assertEquals(2, reads.get(), "unchanged files are never decompressed again");

        write(key(3, "c", 3));
        index.rescan(dir.toFile(), EXT, reader);
        Assertions.assertEquals(3, reads.get(), "only the new file is read");
        Assertions.assertEquals(List.of("a@1", "b@2", "c@3"), names(index.list()));
    }

    @Test
    public void aDeletedFileLeavesTheList() throws IOException {
        String a = write(key(1, "a", 1));
        write(key(2, "b", 2));
        SnapshotIndex index = new SnapshotIndex();
        index.rescan(dir.toFile(), EXT, reader);
        Files.delete(dir.resolve(a));
        index.rescan(dir.toFile(), EXT, reader);
        Assertions.assertEquals(List.of("b@2"), names(index.list()));
    }

    @Test
    public void anUnreadableFileIsSkippedAndNotRetriedUntilItChanges() throws IOException {
        Files.writeString(dir.resolve("corrupt" + EXT), "x"); // the fake reader returns null for it
        Files.writeString(dir.resolve("notes.txt"), "x");     // wrong extension: ignored entirely
        Files.createDirectory(dir.resolve("folder" + EXT));   // not a file: never read, so never retried every scan
        SnapshotIndex index = new SnapshotIndex();
        index.rescan(dir.toFile(), EXT, reader);
        index.rescan(dir.toFile(), EXT, reader);
        Assertions.assertEquals(1, reads.get(), "a corrupt file is read once, not once per scan; a directory never");
        Assertions.assertTrue(index.list().isEmpty());
    }

    @Test
    public void addAndRemoveUpdateTheListWithoutTouchingDisk() {
        SnapshotIndex index = new SnapshotIndex();
        Snapshot.Key b = key(2, "b", 2);
        Snapshot.Key a = key(1, "a", 1);
        index.put(b.toString() + EXT, b, 0, 0);
        index.put(a.toString() + EXT, a, 0, 0);
        Assertions.assertEquals(List.of("a@1", "b@2"), names(index.list()));
        Assertions.assertTrue(index.contains(a));
        Assertions.assertFalse(index.contains(new Snapshot.Key(a, (Snapshot.Header) null)),
            "the same build without its name is another entry: only an equal key is contained");
        Assertions.assertFalse(index.contains(key(1, "a", 2)), "so is the same name saved at another time");
        index.remove(a.toString() + EXT);
        Assertions.assertEquals(List.of("b@2"), names(index.list()));
        Assertions.assertFalse(index.contains(a));
        Assertions.assertEquals(0, reads.get());
    }

    @Test
    public void aFileIsRuledOutOnlyWhileItsSizeAndTimeAreWhatTheIndexRead() throws IOException {
        Snapshot.Key held = key(1, "a", 1);
        String name = write(held);
        Files.writeString(dir.resolve("corrupt" + EXT), "x");
        SnapshotIndex index = new SnapshotIndex();
        index.rescan(dir.toFile(), EXT, reader);
        long modified = Files.getLastModifiedTime(dir.resolve(name)).toMillis();
        long length = Files.size(dir.resolve(name));
        long corruptModified = Files.getLastModifiedTime(dir.resolve("corrupt" + EXT)).toMillis();

        Assertions.assertTrue(index.rulesOut(name, modified, length, k -> false), "known to hold nothing that answers");
        Assertions.assertFalse(index.rulesOut(name, modified, length, held::equals), "it holds the answer");
        Assertions.assertFalse(index.rulesOut(name, modified + 1, length, k -> false), "touched since: must be read");
        Assertions.assertFalse(index.rulesOut(name, modified, length + 1, k -> false), "resized since: must be read");
        Assertions.assertFalse(index.rulesOut("never-seen" + EXT, modified, length, k -> false), "unknown: must be read");
        Assertions.assertTrue(index.rulesOut("corrupt" + EXT, corruptModified, 1, k -> true),
            "a file the index could not read holds no key to answer with");
    }

    @Test
    public void aFileThatCannotBeReadRightNowIsRetriedNotRuledOut() throws IOException {
        // A file held open by another program (antivirus, a sync tool) fails to read for a moment. Caching that as
        // "unreadable" would hide it from the list and let lookups skip it until something changed its size or time.
        String name = write(key(1, "a", 1));
        write(key(2, "b", 2));
        AtomicBoolean inUse = new AtomicBoolean(true);
        Function<File, Snapshot.Key> busyReader = file -> {
            if (inUse.get() && file.getName().equals(name)) {
                throw new UncheckedIOException(new IOException("in use by another process"));
            }
            return reader.apply(file);
        };
        SnapshotIndex index = new SnapshotIndex();
        index.rescan(dir.toFile(), EXT, busyReader);
        Assertions.assertEquals(List.of("b@2"), names(index.list()), "the rest of the scan still lands");
        long modified = Files.getLastModifiedTime(dir.resolve(name)).toMillis();
        long length = Files.size(dir.resolve(name));
        Assertions.assertFalse(index.rulesOut(name, modified, length, k -> false),
            "a file that was never read is not known to hold nothing");

        inUse.set(false);
        index.rescan(dir.toFile(), EXT, busyReader);
        Assertions.assertEquals(List.of("a@1", "b@2"), names(index.list()),
            "the next scan reads it again although it is unchanged on disk");
    }

    @Test
    public void anAddDuringAScanSurvivesTheScan() throws IOException {
        write(key(1, "a", 1));
        SnapshotIndex index = new SnapshotIndex();
        Snapshot.Key late = key(9, "late", 9);
        // The scan listed the directory before "late" was saved; it is added while the scan is reading.
        index.rescan(dir.toFile(), EXT, file -> {
            index.put(late.toString() + EXT, late, 0, 0);
            return reader.apply(file);
        });
        Assertions.assertEquals(List.of("a@1", "late@9"), names(index.list()));
    }

    @Test
    public void aRemoveDuringAScanIsNotUndoneByTheScan() throws IOException {
        String a = write(key(1, "a", 1));
        write(key(2, "b", 2));
        SnapshotIndex index = new SnapshotIndex();
        index.rescan(dir.toFile(), EXT, reader);
        Files.writeString(dir.resolve(a), "changed!"); // forces a re-read of a
        index.rescan(dir.toFile(), EXT, file -> {
            if (file.getName().equals(a)) {
                index.remove(a); // deleted by the player while the scan was reading it
            }
            return reader.apply(file);
        });
        Assertions.assertEquals(List.of("b@2"), names(index.list()));
    }

    @Test
    public void aMissingDirectoryLeavesTheListAlone() throws IOException {
        write(key(1, "a", 1));
        SnapshotIndex index = new SnapshotIndex();
        index.rescan(dir.toFile(), EXT, reader);
        index.rescan(dir.resolve("gone").toFile(), EXT, reader);
        Assertions.assertEquals(List.of("a@1"), names(index.list()));
    }
}
