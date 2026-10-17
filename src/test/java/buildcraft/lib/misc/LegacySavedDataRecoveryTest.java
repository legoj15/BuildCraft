/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.misc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

//? if >=26.1 {
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.SavedDataStorage;
//?}

/**
 * The recovery half of {@link LegacySavedData}: a world already opened by a 26.x build from before the migration holds
 * BOTH a fresh, empty 26.1+ file (that build wrote it, finding nothing) and the original 1.21.x file, which the old
 * rule never read. Run against a real {@code SavedDataStorage} over a temporary folder, with the two file locations
 * pinned by the test (there is no level in a unit test). The real types - what "empty" means for each - are
 * {@link LegacySavedDataContentTest} and {@link LegacySavedDataTester}.
 */
public class LegacySavedDataRecoveryTest {

    //? if >=26.1 {
    private static final int DATA_VERSION_1_21_11 = 4671;
    private static final String NAME = "buildcraft_test_recovery";

    /** A throwaway saved-data type: a value (0 means "no content") and a counter that lives outside the content. */
    private static final class Probe extends SavedData implements LegacySavedData.Recoverable {
        static final Codec<Probe> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("value").forGetter(p -> p.value),
                Codec.INT.optionalFieldOf("counter", 0).forGetter(p -> p.counter)).apply(i, Probe::new));

        int value;
        int counter;

        Probe(int value, int counter) {
            this.value = value;
            this.counter = counter;
        }

        @Override
        public boolean hasNoContent() {
            return value == 0;
        }

        @Override
        public String describeContent() {
            return "value " + value + ", counter " + counter;
        }

        @Override
        public void absorbBlank(SavedData blank) {
            if (blank instanceof Probe other && other.counter > counter) {
                counter = other.counter;
            }
        }
    }

    /** One probe type over a pinned pair of file locations, and storages over its 26.1+ data folder. */
    private static final class World {
        final Path dataFolder;
        final Path currentFile;
        final Path legacyFile;
        final Path migratedFile;
        final SavedDataType<Probe> type;

        World(Path tmp, boolean withLayout) {
            dataFolder = tmp.resolve("current");
            currentFile = dataFolder.resolve("minecraft").resolve(NAME + ".dat");
            legacyFile = tmp.resolve("legacy").resolve(NAME + ".dat");
            migratedFile = legacyFile.resolveSibling(NAME + ".dat" + LegacySavedData.MIGRATED_SUFFIX);
            LegacySavedData.Layout layout = new LegacySavedData.Layout(currentFile, legacyFile, NbtOps.INSTANCE);
            type = LegacySavedData.typeAt(Identifier.withDefaultNamespace(NAME), level -> new Probe(0, 0),
                    level -> Probe.CODEC, DataFixTypes.SAVED_DATA_MAP_DATA, level -> withLayout ? layout : null);
        }

        SavedDataStorage storage() {
            return new SavedDataStorage(null, dataFolder, DataFixers.getDataFixer(), RegistryAccess.EMPTY);
        }
    }

    private static int currentDataVersion() {
        return SharedConstants.getCurrentVersion().dataVersion().version();
    }

    private static void writeRaw(Path file, Tag payload, int dataVersion) throws IOException {
        Files.createDirectories(file.getParent());
        CompoundTag wrapper = new CompoundTag();
        wrapper.put("data", payload);
        wrapper.putInt("DataVersion", dataVersion);
        NbtIo.writeCompressed(wrapper, file);
    }

    private static void writeProbe(Path file, int value, int counter, int dataVersion) throws IOException {
        CompoundTag payload = new CompoundTag();
        payload.putInt("value", value);
        payload.putInt("counter", counter);
        writeRaw(file, payload, dataVersion);
    }

    private static int valueIn(Path file) throws IOException {
        CompoundTag wrapper = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        return NBTUtilBC.getInt(NBTUtilBC.getCompound(wrapper, "data"), "value", -1);
    }

    // ---- both files present, the 26.1+ one holds no content: the old data is recovered ----

    @Test
    public void aCurrentFileWithoutContentGivesWayToTheLegacyData(@TempDir Path tmp) throws IOException {
        World world = new World(tmp, true);
        writeProbe(world.legacyFile, 7, 2, DATA_VERSION_1_21_11);
        writeProbe(world.currentFile, 0, 0, currentDataVersion());

        try (SavedDataStorage storage = world.storage()) {
            Probe probe = storage.computeIfAbsent(world.type);

            Assertions.assertEquals(7, probe.value, "the empty 26.1+ file must not hide the 1.21.x data");
            Assertions.assertEquals(2, probe.counter);
            Assertions.assertTrue(probe.isDirty(), "the recovered data has to be written to the 26.1+ file");
        }
    }

    @Test
    public void theRecoveredDataKeepsWhatTheEmptyFileHadAlreadyAdvanced(@TempDir Path tmp) throws IOException {
        World world = new World(tmp, true);
        writeProbe(world.legacyFile, 7, 2, DATA_VERSION_1_21_11);
        writeProbe(world.currentFile, 0, 9, currentDataVersion());

        try (SavedDataStorage storage = world.storage()) {
            Probe probe = storage.computeIfAbsent(world.type);

            Assertions.assertEquals(7, probe.value);
            Assertions.assertEquals(9, probe.counter,
                    "state that lives outside the content must not be rolled back to the old file's");
        }
    }

    @Test
    public void theLegacyFileIsRetiredOnceTheRecoveredDataIsWritten(@TempDir Path tmp) throws IOException {
        World world = new World(tmp, true);
        writeProbe(world.legacyFile, 7, 0, DATA_VERSION_1_21_11);
        writeProbe(world.currentFile, 0, 0, currentDataVersion());
        byte[] legacyBytes = Files.readAllBytes(world.legacyFile);

        try (SavedDataStorage storage = world.storage()) {
            storage.computeIfAbsent(world.type);
            Assertions.assertTrue(Files.isRegularFile(world.legacyFile),
                    "until the data is written the old file is only read - a crash must repeat the read");

            storage.saveAndJoin();
        }

        Assertions.assertFalse(Files.exists(world.legacyFile), "the old file must not be read again");
        Assertions.assertArrayEquals(legacyBytes, Files.readAllBytes(world.migratedFile),
                "...but it is kept, byte for byte, next to where it was");
        Assertions.assertEquals(7, valueIn(world.currentFile), "the 26.1+ file now holds the recovered data");
    }

    @Test
    public void aCrashBeforeTheFirstSaveRepeatsTheRecovery(@TempDir Path tmp) throws IOException {
        World world = new World(tmp, true);
        writeProbe(world.legacyFile, 7, 0, DATA_VERSION_1_21_11);
        writeProbe(world.currentFile, 0, 0, currentDataVersion());

        // The first storage is dropped without a save: the launch that died.
        world.storage().computeIfAbsent(world.type);

        try (SavedDataStorage relaunch = world.storage()) {
            Assertions.assertEquals(7, relaunch.computeIfAbsent(world.type).value,
                    "nothing was written, so the old file is still the source");
        }
    }

    @Test
    public void recoveredDataThatIsLaterEmptiedIsNotResurrectedFromTheLegacyFile(@TempDir Path tmp)
            throws IOException {
        World world = new World(tmp, true);
        writeProbe(world.legacyFile, 7, 0, DATA_VERSION_1_21_11);
        writeProbe(world.currentFile, 0, 0, currentDataVersion());
        try (SavedDataStorage storage = world.storage()) {
            storage.computeIfAbsent(world.type);
            storage.saveAndJoin();
        }

        // The player removes everything and the world saves: the 26.1+ file holds no content again.
        try (SavedDataStorage storage = world.storage()) {
            Probe probe = storage.computeIfAbsent(world.type);
            Assertions.assertEquals(7, probe.value, "the saved 26.1+ file now carries the data");
            probe.value = 0;
            probe.setDirty();
            storage.saveAndJoin();
        }

        try (SavedDataStorage storage = world.storage()) {
            Assertions.assertEquals(0, storage.computeIfAbsent(world.type).value,
                    "data the player deleted must stay deleted, not return from the retired 1.21.x file");
        }
    }

    @Test
    public void aSecondRetirementDoesNotOverwriteAnEarlierBackup(@TempDir Path tmp) throws IOException {
        World world = new World(tmp, true);
        writeProbe(world.legacyFile, 7, 0, DATA_VERSION_1_21_11);
        writeProbe(world.currentFile, 0, 0, currentDataVersion());
        Files.writeString(world.migratedFile, "an earlier backup");

        try (SavedDataStorage storage = world.storage()) {
            storage.computeIfAbsent(world.type);
            storage.saveAndJoin();
        }

        Assertions.assertEquals("an earlier backup", Files.readString(world.migratedFile), "the earlier backup stays");
        Assertions.assertTrue(Files.isRegularFile(
                world.migratedFile.resolveSibling(world.migratedFile.getFileName() + ".1")),
                "the new one gets a numbered name instead");
        Assertions.assertFalse(Files.exists(world.legacyFile));
    }

    // ---- both files present, the 26.1+ one has content: it always wins ----

    @Test
    public void aCurrentFileWithContentIsNeverReplacedAndTheLegacyFileIsLeftAlone(@TempDir Path tmp)
            throws IOException {
        World world = new World(tmp, true);
        writeProbe(world.legacyFile, 7, 0, DATA_VERSION_1_21_11);
        writeProbe(world.currentFile, 3, 0, currentDataVersion());
        byte[] legacyBytes = Files.readAllBytes(world.legacyFile);

        try (SavedDataStorage storage = world.storage()) {
            Probe probe = storage.computeIfAbsent(world.type);
            Assertions.assertEquals(3, probe.value, "real content in the 26.1+ file is never overridden");
            Assertions.assertFalse(probe.isDirty());

            probe.setDirty();
            storage.saveAndJoin();
        }

        Assertions.assertArrayEquals(legacyBytes, Files.readAllBytes(world.legacyFile),
                "a file nothing was carried over from is not touched, even when the data is saved");
        Assertions.assertEquals(3, valueIn(world.currentFile));
    }

    @Test
    public void whenNeitherFileHoldsContentNothingChanges(@TempDir Path tmp) throws IOException {
        World world = new World(tmp, true);
        writeProbe(world.legacyFile, 0, 0, DATA_VERSION_1_21_11);
        writeProbe(world.currentFile, 0, 4, currentDataVersion());
        byte[] legacyBytes = Files.readAllBytes(world.legacyFile);

        try (SavedDataStorage storage = world.storage()) {
            Probe probe = storage.computeIfAbsent(world.type);

            Assertions.assertEquals(4, probe.counter, "the 26.1+ file's own data is what loads");
            Assertions.assertFalse(probe.isDirty(), "nothing was recovered, so nothing needs writing");
        }
        Assertions.assertArrayEquals(legacyBytes, Files.readAllBytes(world.legacyFile));
    }

    @Test
    public void anUnreadableLegacyFileLeavesTheEmptyCurrentFileInPlace(@TempDir Path tmp) throws IOException {
        World world = new World(tmp, true);
        Files.createDirectories(world.legacyFile.getParent());
        Files.writeString(world.legacyFile, "this is not nbt");
        writeProbe(world.currentFile, 0, 4, currentDataVersion());

        try (SavedDataStorage storage = world.storage()) {
            Probe probe = Assertions.assertDoesNotThrow(() -> storage.computeIfAbsent(world.type));

            Assertions.assertEquals(4, probe.counter, "a corrupt old file is skipped, the level still loads");
        }
        Assertions.assertEquals("this is not nbt", Files.readString(world.legacyFile), "...and is left as it was");
    }

    @Test
    public void aLegacyPayloadOfTheWrongShapeLeavesTheEmptyCurrentFileInPlace(@TempDir Path tmp)
            throws IOException {
        World world = new World(tmp, true);
        writeRaw(world.legacyFile, IntTag.valueOf(3), DATA_VERSION_1_21_11);
        writeProbe(world.currentFile, 0, 4, currentDataVersion());

        try (SavedDataStorage storage = world.storage()) {
            Assertions.assertEquals(4, storage.computeIfAbsent(world.type).counter);
        }
        Assertions.assertTrue(Files.isRegularFile(world.legacyFile));
    }

    @Test
    public void aCurrentFileThatCannotBeReadIsNotReplacedByTheLegacyData(@TempDir Path tmp) throws IOException {
        World world = new World(tmp, true);
        writeProbe(world.legacyFile, 7, 0, DATA_VERSION_1_21_11);
        Files.createDirectories(world.currentFile.getParent());
        Files.writeString(world.currentFile, "this is not nbt");

        try (SavedDataStorage storage = world.storage()) {
            Assertions.assertEquals(0, storage.computeIfAbsent(world.type).value,
                    "a 26.1+ file that exists but cannot be parsed starts fresh, as it always did");
        }
    }

    @Test
    public void withoutALayoutNothingIsRecovered(@TempDir Path tmp) throws IOException {
        // A storage with no level has no way to find the old file.
        World world = new World(tmp, false);
        writeProbe(world.legacyFile, 7, 0, DATA_VERSION_1_21_11);
        writeProbe(world.currentFile, 0, 0, currentDataVersion());

        try (SavedDataStorage storage = world.storage()) {
            Assertions.assertEquals(0, storage.computeIfAbsent(world.type).value);
        }
        Assertions.assertTrue(Files.isRegularFile(world.legacyFile));
    }

    // ---- only the legacy file: still migrates, and is retired once written ----

    @Test
    public void aLegacyFileAloneStillMigratesAndIsRetiredOnTheFirstSave(@TempDir Path tmp) throws IOException {
        World world = new World(tmp, true);
        writeProbe(world.legacyFile, 7, 2, DATA_VERSION_1_21_11);
        byte[] legacyBytes = Files.readAllBytes(world.legacyFile);

        try (SavedDataStorage storage = world.storage()) {
            Probe probe = storage.computeIfAbsent(world.type);
            Assertions.assertEquals(7, probe.value, "the pre-26.1 file is picked up when there is no 26.1+ file");
            Assertions.assertEquals(2, probe.counter);
            Assertions.assertTrue(Files.isRegularFile(world.legacyFile), "read, not consumed, until written");

            storage.saveAndJoin();
        }

        Assertions.assertFalse(Files.exists(world.legacyFile));
        Assertions.assertArrayEquals(legacyBytes, Files.readAllBytes(world.migratedFile));
        Assertions.assertEquals(7, valueIn(world.currentFile));
    }

    @Test
    public void migratedDataThatIsLaterEmptiedIsNotResurrected(@TempDir Path tmp) throws IOException {
        World world = new World(tmp, true);
        writeProbe(world.legacyFile, 7, 0, DATA_VERSION_1_21_11);
        try (SavedDataStorage storage = world.storage()) {
            storage.computeIfAbsent(world.type);
            storage.saveAndJoin();
        }
        try (SavedDataStorage storage = world.storage()) {
            Probe probe = storage.computeIfAbsent(world.type);
            probe.value = 0;
            probe.setDirty();
            storage.saveAndJoin();
        }

        try (SavedDataStorage storage = world.storage()) {
            Assertions.assertEquals(0, storage.computeIfAbsent(world.type).value);
        }
    }

    @Test
    public void noFilesAtAllStartsFresh(@TempDir Path tmp) {
        World world = new World(tmp, true);

        try (SavedDataStorage storage = world.storage()) {
            Probe probe = storage.computeIfAbsent(world.type);
            Assertions.assertEquals(0, probe.value);
        }
        Assertions.assertFalse(Files.exists(world.legacyFile));
    }
    //?}
}
