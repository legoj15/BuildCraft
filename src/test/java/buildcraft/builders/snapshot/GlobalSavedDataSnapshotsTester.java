/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.snapshot;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.BitSet;
import java.util.Date;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

import buildcraft.builders.tile.TileElectronicLibrary;
import buildcraft.lib.nbt.NbtSquisher;

/**
 * Covers the snapshot store's handling of one build saved under several names. Files used to be named by the content
 * hash alone, so a second save of the same build under another name was skipped: the Electronic Library showed only
 * the first, and an upload that hit an existing file handed out a blueprint item whose lookup failed.
 */
public class GlobalSavedDataSnapshotsTester {

    @TempDir
    Path dir;

    private static Template build() {
        Template template = new Template();
        template.size = new BlockPos(2, 1, 1);
        template.facing = Direction.NORTH;
        template.offset = BlockPos.ZERO;
        template.data = new BitSet();
        template.data.set(0);
        template.computeKey();
        return template;
    }

    /** {@code content} saved under {@code name}, as a library download stores it. */
    private static Template named(Template content, String name, long created) {
        Template copy = content.copy();
        Snapshot.Key bare = new Snapshot.Key(copy.key, (Snapshot.Header) null);
        copy.key = new Snapshot.Key(bare, new Snapshot.Header(bare, new UUID(0, 7), new Date(created), name));
        return copy;
    }

    private GlobalSavedDataSnapshots store() {
        return new GlobalSavedDataSnapshots(dir.toFile());
    }

    private long fileCount() throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.count();
        }
    }

    @Test
    public void oneBuildSavedUnderTwoNamesKeepsBoth() throws IOException {
        Template content = build();
        Template house = named(content, "house", 100);
        Template cabin = named(content, "cabin", 200);
        GlobalSavedDataSnapshots store = store();
        store.addSnapshot(house);
        store.addSnapshot(cabin);

        Assertions.assertEquals(2, fileCount(), "each name gets its own file");
        Assertions.assertTrue(store.isListed(house.key));
        Assertions.assertTrue(store.isListed(cabin.key));
        Assertions.assertEquals(house.key, store.getSnapshot(house.key).key);
        Assertions.assertEquals(cabin.key, store.getSnapshot(cabin.key).key);
    }

    @Test
    public void deletingOneNameLeavesTheOther() throws IOException {
        Template content = build();
        Template house = named(content, "house", 100);
        Template cabin = named(content, "cabin", 200);
        GlobalSavedDataSnapshots store = store();
        store.addSnapshot(house);
        store.addSnapshot(cabin);

        store.removeSnapshot(house.key);
        Assertions.assertNull(store.getSnapshot(house.key));
        Assertions.assertNotNull(store.getSnapshot(cabin.key), "the other name's file survives");
        Assertions.assertEquals(1, fileCount());
    }

    @Test
    public void savingTheSameNamedBuildTwiceKeepsOneFile() throws IOException {
        Template content = build();
        GlobalSavedDataSnapshots store = store();
        store.addSnapshot(named(content, "house", 100));
        store.addSnapshot(named(content, "house", 100));
        Assertions.assertEquals(1, fileCount());
    }

    @Test
    public void anOldFileNamedByHashAloneStillLoadsAndIsNotDuplicated() throws IOException {
        Template house = named(build(), "house", 100);
        File legacy = new File(dir.toFile(), house.key + ".bcnbt");
        try (FileOutputStream out = new FileOutputStream(legacy)) {
            NbtSquisher.squishVanilla(Snapshot.writeToNBT(house), out);
        }
        GlobalSavedDataSnapshots store = store();
        Assertions.assertEquals(house.key, store.getSnapshot(house.key).key);

        store.addSnapshot(named(build(), "house", 100));
        Assertions.assertEquals(1, fileCount(), "re-saving an entry an old file already holds writes nothing");

        store.removeSnapshot(house.key);
        Assertions.assertEquals(0, fileCount());
    }

    @Test
    public void aLookupByContentAloneFindsANamedFile() {
        // Uploads used to be stored with their name, but items look them up by content alone.
        Template house = named(build(), "house", 100);
        GlobalSavedDataSnapshots store = store();
        store.addSnapshot(house);
        Snapshot.Key contentOnly = new Snapshot.Key(house.key, (Snapshot.Header) null);
        Snapshot found = store.getSnapshot(contentOnly);
        Assertions.assertNotNull(found);
        Assertions.assertEquals(contentOnly, found.key, "the result carries no name, so a copy of it carries none");
    }

    @Test
    public void deletingTheContentOnlyEntryLeavesTheNamedOnes() throws IOException {
        Template content = build();
        Template house = named(content, "house", 100);
        GlobalSavedDataSnapshots store = store();
        store.addSnapshot(content);
        store.addSnapshot(house);
        Assertions.assertEquals(2, fileCount());

        store.removeSnapshot(content.key);
        Assertions.assertEquals(1, fileCount(), "a delete removes only the exact entry");
        Assertions.assertEquals(house.key, store.getSnapshot(house.key).key);
    }

    @Test
    public void savingContentAnOldNamedFileAlreadyHoldsWritesNothing() throws IOException {
        // An upload stored before names split files: <hash>.bcnbt holding a header. The Architect Table then scans
        // the same build; its content-only save must not add a second file, and its lookup must find the old one.
        Template content = build();
        Template house = named(content, "house", 100);
        try (FileOutputStream out = new FileOutputStream(new File(dir.toFile(), house.key + ".bcnbt"))) {
            NbtSquisher.squishVanilla(Snapshot.writeToNBT(house), out);
        }
        GlobalSavedDataSnapshots store = store();
        store.addSnapshot(content);
        Assertions.assertEquals(1, fileCount());
        Assertions.assertEquals(content.key, store.getSnapshot(content.key).key);
    }

    @Test
    public void uploadingOneBuildUnderTwoNamesGivesTwoWorkingItems() throws IOException {
        Template content = build();
        GlobalSavedDataSnapshots server = store();
        Snapshot.Header house = TileElectronicLibrary.storeUpload(server, named(content, "house", 100));
        Snapshot.Header cabin = TileElectronicLibrary.storeUpload(server, named(content, "cabin", 200));

        Assertions.assertEquals("house", house.name);
        Assertions.assertEquals("cabin", cabin.name);
        Assertions.assertNotNull(server.getSnapshot(house.key), "the first item resolves");
        Assertions.assertNotNull(server.getSnapshot(cabin.key), "the second item resolves");
        Assertions.assertNull(house.key.header, "the server stores content only");
        Assertions.assertEquals(1, fileCount(), "the server keeps one copy of the content");
    }

    /** A template that cannot be serialised, so a save of it must fail without touching the disk. */
    private static final class UnserialisableTemplate extends Template {
        UnserialisableTemplate(Template good) {
            size = good.size;
            facing = good.facing;
            offset = good.offset;
            data = good.data;
            key = good.key;
        }

        @Override
        public CompoundTag serializeNBT() {
            throw new IllegalStateException("cannot serialise");
        }
    }

    private long countOf(String suffix) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(f -> f.getFileName().toString().endsWith(suffix)).count();
        }
    }

    @Test
    public void aSaveThatFailsToSerialiseLeavesNothingBehindAndALaterSaveWorks() throws IOException {
        Template good = build();
        GlobalSavedDataSnapshots store = store();

        Assertions.assertThrows(IllegalStateException.class, () -> store.addSnapshot(new UnserialisableTemplate(good)));
        Assertions.assertEquals(0, fileCount(), "a failed save leaves neither a partial file nor a temp file");
        Assertions.assertFalse(store.isListed(good.key), "a failed save is not listed");
        Assertions.assertNull(store.getSnapshot(good.key));

        store.addSnapshot(good);
        Assertions.assertEquals(1, fileCount());
        Assertions.assertNotNull(store.getSnapshot(good.key), "the retry is not blocked by the failed attempt");
    }

    @Test
    public void aSaveThatCannotReachItsTargetNameLeavesNoTempFile() throws IOException {
        Template good = build();
        // A non-empty directory squatting on the target name: the temp file is written fine, the move onto it fails.
        Path target = dir.resolve(GlobalSavedDataSnapshots.fileName(good.key));
        Files.createDirectory(target);
        Files.write(target.resolve("keep"), new byte[] {1});
        GlobalSavedDataSnapshots store = store();

        store.addSnapshot(good);
        Assertions.assertEquals(0, countOf(".tmp"), "the failed move cleans up its temp file");
        Assertions.assertEquals(1, fileCount(), "only the squatting directory remains");
        Assertions.assertFalse(store.isListed(good.key));
    }

    @Test
    public void anEmptyFileAtTheTargetNameIsReplacedNotTrusted() throws IOException {
        Template good = build();
        Files.write(dir.resolve(GlobalSavedDataSnapshots.fileName(good.key)), new byte[0]);
        GlobalSavedDataSnapshots store = store();
        Assertions.assertNull(store.getSnapshot(good.key), "an empty file holds nothing");

        store.addSnapshot(good);
        Assertions.assertEquals(1, fileCount());
        Assertions.assertNotNull(store.getSnapshot(good.key), "the empty file was overwritten with the snapshot");
    }

    @Test
    public void aTruncatedFileAtTheTargetNameIsReplacedNotTrusted() throws IOException {
        Template good = build();
        Path target = dir.resolve(GlobalSavedDataSnapshots.fileName(good.key));
        // Write the real file once, then cut it in half: what a crash part-way through an in-place write leaves.
        try (FileOutputStream out = new FileOutputStream(target.toFile())) {
            NbtSquisher.squishVanilla(Snapshot.writeToNBT(good), out);
        }
        byte[] whole = Files.readAllBytes(target);
        Files.write(target, java.util.Arrays.copyOf(whole, whole.length / 2));
        GlobalSavedDataSnapshots store = store();
        Assertions.assertNull(store.getSnapshot(good.key), "half a file holds nothing");

        store.addSnapshot(good);
        Assertions.assertEquals(1, fileCount());
        Assertions.assertNotNull(store.getSnapshot(good.key));
    }

    @Test
    public void aSavedFileLeavesNoTempFileAndIsTrustedOnTheNextSave() throws IOException {
        Template good = build();
        GlobalSavedDataSnapshots store = store();
        store.addSnapshot(good);
        Assertions.assertEquals(1, countOf(".bcnbt"));
        Assertions.assertEquals(1, fileCount(), "no temp file survives a successful save");

        long modified = Files.getLastModifiedTime(dir.resolve(GlobalSavedDataSnapshots.fileName(good.key))).toMillis();
        store.addSnapshot(good);
        Assertions.assertEquals(modified,
            Files.getLastModifiedTime(dir.resolve(GlobalSavedDataSnapshots.fileName(good.key))).toMillis(),
            "an intact file is left alone");
    }

    /** Backdate {@code file} by {@code age}, as if it had been sitting there that long. */
    private static void age(Path file, Duration age) throws IOException {
        Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() - age.toMillis()));
    }

    @Test
    public void tempFilesLeftByACrashAreSweptWhenTheStoreOpens() throws IOException {
        Template good = build();
        GlobalSavedDataSnapshots first = store();
        first.addSnapshot(good);
        Path orphan = dir.resolve(GlobalSavedDataSnapshots.fileName(good.key) + ".1234.tmp");
        Files.write(orphan, new byte[] {1, 2, 3});
        age(orphan, GlobalSavedDataSnapshots.ORPHANED_TEMP_MIN_AGE.plusMinutes(1));

        GlobalSavedDataSnapshots reopened = store();
        Assertions.assertFalse(Files.exists(orphan), "the orphaned temp file is deleted");
        Assertions.assertEquals(1, fileCount(), "the real snapshot survives the sweep");
        Assertions.assertNotNull(reopened.getSnapshot(good.key));
    }

    @Test
    public void aTempFileAnotherInstanceIsStillWritingSurvivesTheSweep() throws IOException {
        // A second server pointed at the same directory opens its store while the first is mid-save: the first's
        // temp file is seconds from done, not left by a crash, and deleting it would fail that save.
        Template good = build();
        Path inFlight = dir.resolve(GlobalSavedDataSnapshots.fileName(good.key) + ".5678.tmp");
        Files.write(inFlight, new byte[] {1, 2, 3});

        store();
        Assertions.assertTrue(Files.exists(inFlight), "a just-written temp file is not an orphan");

        // Still young just inside the threshold; only past it is the file treated as a crash leftover.
        age(inFlight, GlobalSavedDataSnapshots.ORPHANED_TEMP_MIN_AGE.minusSeconds(30));
        store();
        Assertions.assertTrue(Files.exists(inFlight), "a temp file below the age threshold is kept");

        age(inFlight, GlobalSavedDataSnapshots.ORPHANED_TEMP_MIN_AGE.plusSeconds(30));
        store();
        Assertions.assertFalse(Files.exists(inFlight), "past the threshold it is swept");
    }

    @Test
    public void aFileTruncatedOutsideTheGameAfterTheStoreSavedItIsNotTrusted() throws IOException {
        Template good = build();
        GlobalSavedDataSnapshots store = store();
        store.addSnapshot(good);
        Path target = dir.resolve(GlobalSavedDataSnapshots.fileName(good.key));
        byte[] whole = Files.readAllBytes(target);
        // What an external tool (a sync client, a hand edit, another instance) leaves behind.
        Files.write(target, java.util.Arrays.copyOf(whole, whole.length / 2));
        Assertions.assertNull(store.getSnapshot(good.key), "the cut file holds nothing");

        store.addSnapshot(good);
        Assertions.assertNotNull(store.getSnapshot(good.key), "the save noticed the file changed and rewrote it");
        Assertions.assertArrayEquals(whole, Files.readAllBytes(target));
    }

    @Test
    public void aFileReplacedByGarbageOfTheSameLengthIsNotTrusted() throws IOException {
        Template good = build();
        GlobalSavedDataSnapshots store = store();
        store.addSnapshot(good);
        Path target = dir.resolve(GlobalSavedDataSnapshots.fileName(good.key));
        byte[] whole = Files.readAllBytes(target);
        long before = Files.getLastModifiedTime(target).toMillis();
        // Same length, so only the modification time can tell it changed.
        Files.write(target, new byte[whole.length]);
        Files.setLastModifiedTime(target, FileTime.fromMillis(before + 60_000));
        Assertions.assertNull(store.getSnapshot(good.key));

        store.addSnapshot(good);
        Assertions.assertNotNull(store.getSnapshot(good.key), "the save noticed the file changed and rewrote it");
    }

    @Test
    public void aRewrittenFileIsTrustedAgainOnTheNextSave() throws IOException {
        // The trust follows what the file looks like now: after the rewrite it is verified, and left alone.
        Template good = build();
        GlobalSavedDataSnapshots store = store();
        store.addSnapshot(good);
        Path target = dir.resolve(GlobalSavedDataSnapshots.fileName(good.key));
        byte[] whole = Files.readAllBytes(target);
        Files.write(target, new byte[0]);
        store.addSnapshot(good);
        Assertions.assertArrayEquals(whole, Files.readAllBytes(target));

        long modified = Files.getLastModifiedTime(target).toMillis();
        store.addSnapshot(good);
        Assertions.assertEquals(modified, Files.getLastModifiedTime(target).toMillis(), "the rewritten file is trusted");
    }
}
