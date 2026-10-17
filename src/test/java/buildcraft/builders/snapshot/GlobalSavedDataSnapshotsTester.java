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
import java.util.BitSet;
import java.util.Date;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

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
}
