/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.snapshot;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.BitSet;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;

import buildcraft.builders.tile.TileElectronicLibrary;
import buildcraft.lib.misc.HashUtil;
import buildcraft.lib.misc.NBTUtilBC;
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

    /** Overwrite {@code file} with bytes that are not a snapshot, keeping its size and modification time: a store
     *  that skips the file without reading it cannot tell, and one that reads it records it as holding nothing. */
    private static void spoilKeepingStamp(Path file) throws IOException {
        FileTime modified = Files.getLastModifiedTime(file);
        byte[] junk = new byte[(int) Files.size(file)];
        java.util.Arrays.fill(junk, (byte) 0xFF);
        Files.write(file, junk);
        Files.setLastModifiedTime(file, modified);
    }

    private Path fileOf(Template snapshot) {
        return dir.resolve(GlobalSavedDataSnapshots.fileName(snapshot.key));
    }

    @Test
    public void aMissDoesNotReadTheBuildsOtherNames() throws IOException {
        // One build kept under several names shares a hash prefix, so a lookup for one name used to decompress every
        // sibling to find out it holds another name. A file the store itself wrote is already known to hold its key.
        Template content = build();
        Template cabin = named(content, "cabin", 200);
        GlobalSavedDataSnapshots store = store();
        store.addSnapshot(named(content, "house", 100));
        store.addSnapshot(cabin);
        spoilKeepingStamp(fileOf(cabin));

        Assertions.assertNull(store.getSnapshot(named(content, "barn", 300).key), "no file holds that name");
        Assertions.assertTrue(store.isListed(cabin.key), "the miss never read the other name's file");
    }

    @Test
    public void afterARestartAMissRemembersWhatItReadForTheNextMiss() throws IOException {
        // A fresh store (a restart) knows nothing about the files, and the server never runs the library scan that
        // would tell it, so the first miss has to read the build's other names. What it read is remembered, so the
        // next miss for yet another name of the same build does not read them again.
        Template content = build();
        Template cabin = named(content, "cabin", 200);
        GlobalSavedDataSnapshots before = store();
        before.addSnapshot(named(content, "house", 100));
        before.addSnapshot(cabin);
        GlobalSavedDataSnapshots store = store();

        Assertions.assertNull(store.getSnapshot(named(content, "barn", 300).key));
        Assertions.assertTrue(store.isListed(cabin.key), "the first miss read the other name and recorded it");
        spoilKeepingStamp(fileOf(cabin));
        Assertions.assertNull(store.getSnapshot(named(content, "shed", 400).key));
        Assertions.assertTrue(store.isListed(cabin.key), "the second miss did not read it again");
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
    public void aRepeatSaveOfAnEntryAnOldFileHoldsDoesNotReadItAgain() throws IOException {
        // A named save first checks the old <hash>.bcnbt; once it has read that file, a later save of the same entry
        // trusts what it found while the file is unchanged, rather than decompressing a large snapshot every save.
        Template house = named(build(), "house", 100);
        Path legacy = dir.resolve(house.key + ".bcnbt");
        try (FileOutputStream out = new FileOutputStream(legacy.toFile())) {
            NbtSquisher.squishVanilla(Snapshot.writeToNBT(house), out);
        }
        GlobalSavedDataSnapshots store = store();
        store.addSnapshot(named(build(), "house", 100));
        Assertions.assertEquals(1, fileCount(), "the old file already holds the entry");

        spoilKeepingStamp(legacy);
        store.addSnapshot(named(build(), "house", 100));
        Assertions.assertEquals(1, fileCount(), "the repeat save did not read the old file again");
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
        Template houseUpload = named(content, "house", 100);
        Template cabinUpload = named(content, "cabin", 200);
        Snapshot.Header house = TileElectronicLibrary.storeUpload(server, houseUpload, houseUpload.key.header);
        Snapshot.Header cabin = TileElectronicLibrary.storeUpload(server, cabinUpload, cabinUpload.key.header);

        Assertions.assertEquals("house", house.name);
        Assertions.assertEquals("cabin", cabin.name);
        Assertions.assertNotNull(server.getSnapshot(house.key), "the first item resolves");
        Assertions.assertNotNull(server.getSnapshot(cabin.key), "the second item resolves");
        Assertions.assertNull(house.key.header, "the server stores content only");
        Assertions.assertEquals(1, fileCount(), "the server keeps one copy of the content");
    }

    @Test
    public void anUploadedItemIsNamedByTheRequestNotByThePayload() throws IOException {
        // The request's header was checked when the client selected it (name length capped); the payload's was not,
        // so an answer carrying a huge name or another player's id must not reach the item.
        Template content = build();
        Snapshot.Header asked = named(content, "house", 100).key.header;
        Template payload = content.copy();
        Snapshot.Key bare = new Snapshot.Key(payload.key, (Snapshot.Header) null);
        payload.key = new Snapshot.Key(bare, new Snapshot.Header(bare, new UUID(9, 9), new Date(999), "x".repeat(4096)));

        Snapshot.Header made = TileElectronicLibrary.storeUpload(store(), payload, asked);
        Assertions.assertEquals("house", made.name);
        Assertions.assertEquals(new UUID(0, 7), made.owner);
        Assertions.assertEquals(new Date(100), made.created);
        Assertions.assertArrayEquals(content.key.hash, made.key.hash, "the item points at the uploaded content");
        Assertions.assertNull(made.key.header);

        Snapshot.Header unnamed = TileElectronicLibrary.storeUpload(store(), payload, null);
        Assertions.assertEquals("Snapshot", unnamed.name, "a request with no header gets the default name");
        Assertions.assertEquals(new UUID(0, 0), unnamed.owner);
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

    /** A build other than {@link #build()}, so its file is a valid snapshot holding a different key. */
    private static Template otherBuild() {
        Template template = build();
        template.data.set(1);
        template.computeKey();
        return template;
    }

    @Test
    public void aValidFileHoldingAnotherBuildUnderThisBuildsNameIsNotTrusted() throws IOException {
        // Readable is not enough: the file must hold the key it is named for, or the save writes nothing and every
        // lookup of this build fails from then on.
        Template good = build();
        Template other = otherBuild();
        Assertions.assertNotEquals(good.key, other.key);
        GlobalSavedDataSnapshots store = store();
        store.addSnapshot(good);
        store.addSnapshot(other);
        Path target = dir.resolve(GlobalSavedDataSnapshots.fileName(good.key));
        byte[] whole = Files.readAllBytes(target);
        long before = Files.getLastModifiedTime(target).toMillis();
        Files.copy(dir.resolve(GlobalSavedDataSnapshots.fileName(other.key)), target,
            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Files.setLastModifiedTime(target, FileTime.fromMillis(before + 60_000));
        Assertions.assertNull(store.getSnapshot(good.key), "the file answers for the other build, not this one");

        store.addSnapshot(good);
        Assertions.assertNotNull(store.getSnapshot(good.key), "the save noticed the wrong build and rewrote it");
        Assertions.assertArrayEquals(whole, Files.readAllBytes(target));
    }

    @Test
    public void aFreshStoreDoesNotTrustAFileHoldingAnotherBuild() throws IOException {
        // The same, for a file this store never saw: left by a hand copy under the wrong name.
        Template good = build();
        Template other = otherBuild();
        Path target = dir.resolve(GlobalSavedDataSnapshots.fileName(good.key));
        try (FileOutputStream out = new FileOutputStream(target.toFile())) {
            NbtSquisher.squishVanilla(Snapshot.writeToNBT(other), out);
        }
        GlobalSavedDataSnapshots store = store();
        store.addSnapshot(good);
        Assertions.assertNotNull(store.getSnapshot(good.key));
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

    // --- Re-keying the client's library files ---

    /** A blueprint whose one stair schematic has no ignored properties, the way a schematic saved before stairs
     *  auto-ignored their {@code shape} is: loading it adds the property, so its NBT changes on every load. */
    private static Blueprint stairsBlueprint() {
        Blueprint blueprint = new Blueprint();
        blueprint.size = new BlockPos(2, 1, 1);
        blueprint.facing = Direction.NORTH;
        blueprint.offset = BlockPos.ZERO;
        blueprint.data = new int[] {0, 0};
        SchematicBlockDefault stairs = new SchematicBlockDefault();
        stairs.blockState = Blocks.OAK_STAIRS.defaultBlockState();
        stairs.placeBlock = Blocks.OAK_STAIRS;
        blueprint.palette.add(stairs);
        return blueprint;
    }

    /** A blueprint whose one piston schematic lists eight replaceable blocks. */
    private static Blueprint pistonBlueprint() {
        Blueprint blueprint = new Blueprint();
        blueprint.size = new BlockPos(2, 1, 1);
        blueprint.facing = Direction.NORTH;
        blueprint.offset = BlockPos.ZERO;
        blueprint.data = new int[] {0, 0};
        SchematicBlockDefault piston = new SchematicBlockDefault();
        piston.blockState = Blocks.PISTON.defaultBlockState();
        piston.placeBlock = Blocks.PISTON;
        piston.canBeReplacedWithBlocks.addAll(List.of(Blocks.STONE, Blocks.PISTON, Blocks.STICKY_PISTON,
            Blocks.MOVING_PISTON, Blocks.PISTON_HEAD, Blocks.AIR, Blocks.DIRT, Blocks.OAK_LOG));
        blueprint.palette.add(piston);
        return blueprint;
    }

    /** The older form of {@link #pistonBlueprint()}: the replaceable blocks in descending order, as a JVM whose
     *  identity hashes ran that way wrote them, where the current write sorts them ascending. */
    private static void replaceableBlocksDescending(CompoundTag nbt) {
        NBTUtilBC.readCompoundList(nbt.get("palette")).forEach(entry -> {
            CompoundTag data = NBTUtilBC.getCompound(entry, "data");
            data.put("canBeReplacedWithBlocks", NBTUtilBC.writeStringList(
                NBTUtilBC.readStringList(data.get("canBeReplacedWithBlocks")).sorted(Comparator.reverseOrder())));
        });
    }

    /** Write the file an older build left in the library under {@code name}: the NBT as {@code olderForm} leaves it,
     *  keyed by the hash of that very NBT, which is what the older build stored. Returns the key it stored. */
    private Snapshot.Key writeOlderFile(Blueprint blueprint, String name, Consumer<CompoundTag> olderForm)
            throws IOException {
        CompoundTag nbt = Snapshot.writeToNBT(blueprint);
        olderForm.accept(nbt);
        CompoundTag unkeyed = nbt.copy();
        unkeyed.remove("key");
        Snapshot.Key bare = new Snapshot.Key(new Snapshot.Key(), HashUtil.computeHash(unkeyed));
        Snapshot.Key key = new Snapshot.Key(bare, new Snapshot.Header(bare, new UUID(0, 7), new Date(100), name));
        nbt.put("key", key.serializeNBT());
        try (FileOutputStream out = new FileOutputStream(fileOf(key))) {
            NbtSquisher.squishVanilla(nbt, out);
        }
        return key;
    }

    private File fileOf(Snapshot.Key key) {
        return new File(dir.toFile(), GlobalSavedDataSnapshots.fileName(key));
    }

    /** {@code snapshot} as the server's library receives it: sent over the wire, unpacked, and keyed from its content
     *  ({@code LibraryUploadBuffer#decode}). The server refuses an upload whose key is not the one it asked for. */
    private static Snapshot receivedByTheServer(Snapshot snapshot) throws IOException {
        ByteArrayOutputStream wire = new ByteArrayOutputStream();
        NbtSquisher.squishVanilla(Snapshot.writeToNBT(snapshot), wire);
        Snapshot received = Snapshot.readFromNBT(NbtSquisher.expand(new ByteArrayInputStream(wire.toByteArray())));
        received.computeKey();
        return received;
    }

    /** The one key in the store's list after a scan. */
    private static Snapshot.Key listedKey(GlobalSavedDataSnapshots store) {
        store.scanNow();
        List<Snapshot.Key> list = store.getList();
        Assertions.assertEquals(1, list.size(), list.toString());
        return list.get(0);
    }

    /** Whether an upload of what the store holds under {@code key} would be answered with that same key. */
    private static boolean serverAgreesOn(GlobalSavedDataSnapshots store, Snapshot.Key key) throws IOException {
        return receivedByTheServer(store.getSnapshot(key)).key.toString().equals(key.toString());
    }

    /** The snapshot files in the directory, by name. */
    private List<String> snapshotFileNames() throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(f -> f.getFileName().toString()).filter(n -> n.endsWith(".bcnbt")).sorted().toList();
        }
    }

    @Test
    public void aClientFileKeyedBeforeALoadTimeMigrationIsRekeyedToWhatTheServerComputes() throws IOException {
        Snapshot.Key stale = writeOlderFile(stairsBlueprint(), "stairs", nbt -> {});
        GlobalSavedDataSnapshots asStored = new GlobalSavedDataSnapshots(dir.toFile());
        Assertions.assertFalse(serverAgreesOn(asStored, listedKey(asStored)),
            "precondition: loading the file adds the stair's ignored property, so the server's key for it differs");

        GlobalSavedDataSnapshots client = new GlobalSavedDataSnapshots(dir.toFile(), true);
        Snapshot.Key offered = listedKey(client);
        Assertions.assertTrue(serverAgreesOn(client, offered), "an upload of the offered key is not refused");
        Assertions.assertEquals("stairs", offered.header.name, "the name travels with the file");
        Assertions.assertFalse(fileOf(stale).exists(), "the file under the stale key is gone");
        Assertions.assertEquals(List.of(GlobalSavedDataSnapshots.fileName(offered)), snapshotFileNames(),
            "renamed for the key it now holds, not copied");
    }

    @Test
    public void aClientFileKeyedInAnotherJvmsReplaceableBlockOrderIsRekeyedToWhatTheServerComputes() throws IOException {
        Snapshot.Key stale = writeOlderFile(pistonBlueprint(), "piston",
            GlobalSavedDataSnapshotsTester::replaceableBlocksDescending);
        GlobalSavedDataSnapshots asStored = new GlobalSavedDataSnapshots(dir.toFile());
        Assertions.assertFalse(serverAgreesOn(asStored, listedKey(asStored)),
            "precondition: the stored key hashes the descending order, the server hashes the sorted one");

        GlobalSavedDataSnapshots client = new GlobalSavedDataSnapshots(dir.toFile(), true);
        Snapshot.Key offered = listedKey(client);
        Assertions.assertTrue(serverAgreesOn(client, offered));
        Assertions.assertFalse(fileOf(stale).exists());
        Assertions.assertEquals(List.of(GlobalSavedDataSnapshots.fileName(offered)), snapshotFileNames());
    }

    @Test
    public void aServerStoreNeverMovesAFileBecauseItemsPointAtItsKey() throws IOException {
        Snapshot.Key stale = writeOlderFile(stairsBlueprint(), "stairs", nbt -> {});

        Assertions.assertEquals(stale, listedKey(new GlobalSavedDataSnapshots(dir.toFile())));
        Assertions.assertEquals(List.of(GlobalSavedDataSnapshots.fileName(stale)), snapshotFileNames());
    }

    @Test
    public void aStaleFileMergesIntoTheCopyTheSameItemDownloadedUnderTheRightKey() throws IOException {
        // The Library refused the stale file's upload; the player downloads the same item again, which keys the
        // content correctly under the same header. Re-keying the old file must not leave a second entry.
        Snapshot.Key stale = writeOlderFile(pistonBlueprint(), "piston",
            GlobalSavedDataSnapshotsTester::replaceableBlocksDescending);
        GlobalSavedDataSnapshots client = new GlobalSavedDataSnapshots(dir.toFile(), true);
        Blueprint downloaded = (Blueprint) receivedByTheServer(pistonBlueprint());
        downloaded.key = new Snapshot.Key(downloaded.key, stale.header);
        client.addSnapshot(downloaded);
        long modified = Files.getLastModifiedTime(fileOf(downloaded.key).toPath()).toMillis();

        Assertions.assertEquals(downloaded.key, listedKey(client));
        Assertions.assertFalse(fileOf(stale).exists(), "the stale file is removed");
        Assertions.assertEquals(List.of(GlobalSavedDataSnapshots.fileName(downloaded.key)), snapshotFileNames());
        Assertions.assertEquals(modified, Files.getLastModifiedTime(fileOf(downloaded.key).toPath()).toMillis(),
            "the file that was right all along is not rewritten");
    }

    @Test
    public void aStaleFileThatCannotBeDeletedStaysAsItWasSoDeletingItDoesNotLeaveItListed() throws IOException {
        // Windows refuses to delete a file another handle holds open; elsewhere the delete goes through.
        Assumptions.assumeTrue(System.getProperty("os.name").startsWith("Windows"), "needs a delete-blocking lock");
        Snapshot.Key stale = writeOlderFile(stairsBlueprint(), "stairs", nbt -> {});
        GlobalSavedDataSnapshots client = new GlobalSavedDataSnapshots(dir.toFile(), true);
        try (FileInputStream lock = new FileInputStream(fileOf(stale))) {
            Assertions.assertEquals(stale, listedKey(client), "listed under the key the file holds");
            Assertions.assertTrue(fileOf(stale).exists(), "precondition: the lock blocked the delete");
            Assertions.assertEquals(List.of(GlobalSavedDataSnapshots.fileName(stale)), snapshotFileNames(),
                "no second copy under the new key");
        }

        client.removeSnapshot(stale);
        client.scanNow();
        Assertions.assertEquals(List.of(), client.getList(), "deleting the one entry empties the list");
        Assertions.assertEquals(List.of(), snapshotFileNames(), "and leaves nothing for a later scan to bring back");
    }

    /** Apply {@code edit} to the {@code data} of every palette entry: the piston of {@link #pistonBlueprint()}. */
    private static Consumer<CompoundTag> pistonData(Consumer<CompoundTag> edit) {
        return nbt -> NBTUtilBC.readCompoundList(nbt.get("palette"))
            .forEach(entry -> edit.accept(NBTUtilBC.getCompound(entry, "data")));
    }

    /** A file that names something this game cannot resolve reads back with a default in its place (a block this
     *  game lacks reads as air), so writing the loaded copy under its new key would destroy the original: it is left
     *  under the key it holds, byte for byte. */
    private void assertLeftAsItWas(Consumer<CompoundTag> olderForm) throws IOException {
        Snapshot.Key stale = writeOlderFile(pistonBlueprint(), "piston", olderForm);
        byte[] before = Files.readAllBytes(fileOf(stale).toPath());
        GlobalSavedDataSnapshots asStored = new GlobalSavedDataSnapshots(dir.toFile());
        Assertions.assertFalse(serverAgreesOn(asStored, listedKey(asStored)),
            "precondition: the loaded copy differs from the file, so its key is stale and a re-key would rewrite it");

        GlobalSavedDataSnapshots client = new GlobalSavedDataSnapshots(dir.toFile(), true);
        Assertions.assertEquals(stale, listedKey(client), "listed under the key the file holds");
        Assertions.assertEquals(List.of(GlobalSavedDataSnapshots.fileName(stale)), snapshotFileNames());
        Assertions.assertArrayEquals(before, Files.readAllBytes(fileOf(stale).toPath()), "the file is untouched");
    }

    @Test
    public void aClientFileWhoseBlockThisGameLacksIsNotRekeyed() throws IOException {
        assertLeftAsItWas(pistonData(data -> {
            NBTUtilBC.getCompound(data, "blockState").putString("Name", "missingmod:gizmo");
            data.putString("placeBlock", "missingmod:gizmo");
        }));
    }

    @Test
    public void aClientFileWhoseReplaceableBlockThisGameLacksIsNotRekeyed() throws IOException {
        assertLeftAsItWas(pistonData(data -> data.put("canBeReplacedWithBlocks",
            NBTUtilBC.writeStringList(Stream.of("minecraft:stone", "missingmod:gizmo")))));
    }

    @Test
    public void aClientFileWhosePropertyValueThisGameLacksIsNotRekeyed() throws IOException {
        // The piston's facing is among its saved properties; a value the block does not have reads as the default.
        assertLeftAsItWas(pistonData(data -> NBTUtilBC.getCompound(NBTUtilBC.getCompound(data, "blockState"),
            "Properties").putString("facing", "sideways")));
    }
}
