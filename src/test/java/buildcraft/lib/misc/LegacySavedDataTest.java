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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.DataFixTypes;
//?}

/**
 * Where a build from before 26.1 kept a dimension's saved data, and the read that picks such a file up when the
 * 26.1+ location has none. 26.1 moved every dimension's saved data from {@code <dim>/data/<name>.dat} to
 * {@code <dim>/data/<namespace>/<name>.dat} (and the vanilla dimensions' folders from {@code DIM-1}/{@code DIM1} to
 * {@code dimensions/minecraft/...}), and vanilla's own file fixer moves only the files it knows - so a mod's file
 * stayed behind, unread, and its world state reset on the first launch after the upgrade.
 */
public class LegacySavedDataTest {

    private static final Path ROOT = Path.of("world");

    @Test
    public void theOverworldKeptItsDataInTheWorldRoot() {
        Assertions.assertEquals(ROOT.resolve("data"), LegacySavedData.legacyDataFolder(ROOT, "minecraft", "overworld"));
    }

    @Test
    public void theNetherAndTheEndKeptTheirsInTheOldDimensionFolders() {
        Assertions.assertEquals(ROOT.resolve("DIM-1").resolve("data"),
                LegacySavedData.legacyDataFolder(ROOT, "minecraft", "the_nether"));
        Assertions.assertEquals(ROOT.resolve("DIM1").resolve("data"),
                LegacySavedData.legacyDataFolder(ROOT, "minecraft", "the_end"));
    }

    @Test
    public void aModdedDimensionKeptItsDataInItsOwnFolderWithoutTheNamespaceSubfolder() {
        Assertions.assertEquals(ROOT.resolve("dimensions").resolve("othermod").resolve("mining").resolve("data"),
                LegacySavedData.legacyDataFolder(ROOT, "othermod", "mining"));
        Assertions.assertEquals(ROOT.resolve("dimensions").resolve("othermod").resolve("deep").resolve("pit").resolve("data"),
                LegacySavedData.legacyDataFolder(ROOT, "othermod", "deep/pit"),
                "a dimension id with a path separator nests its folders");
    }

    @Test
    public void aVanillaNamedDimensionFromAnotherNamespaceIsNotTheOverworld() {
        Assertions.assertEquals(ROOT.resolve("dimensions").resolve("othermod").resolve("overworld").resolve("data"),
                LegacySavedData.legacyDataFolder(ROOT, "othermod", "overworld"));
    }

    //? if >=26.1 {
    private static final int DATA_VERSION_1_21_11 = 4671;

    private static void writeFile(Path file, Tag payload) throws IOException {
        Files.createDirectories(file.getParent());
        CompoundTag wrapper = new CompoundTag();
        wrapper.put("data", payload);
        wrapper.putInt("DataVersion", DATA_VERSION_1_21_11);
        NbtIo.writeCompressed(wrapper, file);
    }

    private static CompoundTag payload(int value) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("value", value);
        return tag;
    }

    @Test
    public void theLegacyFileIsReadWhenTheCurrentOneIsAbsent(@TempDir Path tmp) throws IOException {
        Path legacy = tmp.resolve("data").resolve("probe.dat");
        Path current = tmp.resolve("current").resolve("minecraft").resolve("probe.dat");
        writeFile(legacy, payload(77));

        Tag read = LegacySavedData.readLegacyFile(current, legacy, DataFixTypes.SAVED_DATA_MAP_DATA);

        Assertions.assertNotNull(read, "the pre-26.1 file must be picked up, not orphaned");
        Assertions.assertEquals(77, NBTUtilBC.getInt((CompoundTag) read, "value", -1),
                "...and its payload comes back intact");
    }

    @Test
    public void theCurrentFileAlwaysWins(@TempDir Path tmp) throws IOException {
        Path legacy = tmp.resolve("data").resolve("probe.dat");
        Path current = tmp.resolve("current").resolve("minecraft").resolve("probe.dat");
        writeFile(legacy, payload(1));
        writeFile(current, payload(2));

        Assertions.assertNull(LegacySavedData.readLegacyFile(current, legacy, DataFixTypes.SAVED_DATA_MAP_DATA),
                "once the 26.1+ file exists the stale pre-26.1 one must never override it");
    }

    @Test
    public void noLegacyFileMeansNothingToRead(@TempDir Path tmp) {
        Path legacy = tmp.resolve("data").resolve("probe.dat");
        Path current = tmp.resolve("current").resolve("minecraft").resolve("probe.dat");

        Assertions.assertNull(LegacySavedData.readLegacyFile(current, legacy, DataFixTypes.SAVED_DATA_MAP_DATA));
    }

    @Test
    public void anUnreadableLegacyFileIsSkippedNotFatal(@TempDir Path tmp) throws IOException {
        Path legacy = tmp.resolve("data").resolve("probe.dat");
        Path current = tmp.resolve("current").resolve("minecraft").resolve("probe.dat");
        Files.createDirectories(legacy.getParent());
        Files.writeString(legacy, "this is not nbt");

        Assertions.assertNull(LegacySavedData.readLegacyFile(current, legacy, DataFixTypes.SAVED_DATA_MAP_DATA),
                "a corrupt file starts the data fresh, as vanilla does for its own saved data");
    }

    @Test
    public void readingNeverTouchesTheLegacyFile(@TempDir Path tmp) throws IOException {
        Path legacy = tmp.resolve("data").resolve("probe.dat");
        Path current = tmp.resolve("current").resolve("minecraft").resolve("probe.dat");
        writeFile(legacy, payload(5));
        byte[] before = Files.readAllBytes(legacy);

        LegacySavedData.readLegacyFile(current, legacy, DataFixTypes.SAVED_DATA_MAP_DATA);

        Assertions.assertArrayEquals(before, Files.readAllBytes(legacy),
                "the old file stays as the backup until the new one has been saved at least once");
    }
    //?}
}
