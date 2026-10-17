/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.misc;

import net.minecraft.gametest.framework.GameTestHelper;

//? if >=26.1 {
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import com.mojang.serialization.Codec;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.LevelResource;
//?}

/**
 * Game test for {@link LegacySavedData} against a REAL level and its real saved-data storage: a file laid down where a
 * 1.21.x build kept the overworld's saved data (the world's root {@code data} folder) is what the 26.1+ storage hands
 * back, even though the storage itself looks somewhere else entirely. The pure path mapping and the file read are the
 * unit tests' ({@link LegacySavedDataTest}); this one is what proves they agree with where vanilla really looks.
 */
public class LegacySavedDataTester {

    //? if >=26.1 {
    /** A throwaway saved-data payload: one int. */
    private static final class Probe extends SavedData {
        static final Codec<Probe> CODEC = Codec.INT.fieldOf("value").xmap(Probe::new, p -> p.value).codec();

        final int value;

        Probe(int value) {
            this.value = value;
        }
    }
    //?}

    /** The overworld's pre-26.1 saved-data file is picked up by the real storage the first time the type is asked for. */
    public static void aPre26OverworldFileIsPickedUpByTheRealStorage(GameTestHelper helper) {
        //? if >=26.1 {
        ServerLevel level = helper.getLevel();
        helper.assertTrue(level.dimension() == Level.OVERWORLD,
                "the legacy layout asserted here is the overworld's (world root data folder)");
        // Where a 1.21.x build wrote it: <world>/data/<name>.dat. Spelled out, not derived from LegacySavedData, so
        // the test fails if the helper and the real layout ever disagree.
        Path legacy = level.getServer().getWorldPath(LevelResource.ROOT).resolve("data")
                .resolve("buildcraft_test_legacy_" + Long.toHexString(System.nanoTime()) + ".dat");
        String name = legacy.getFileName().toString().replace(".dat", "");
        try {
            Files.createDirectories(legacy.getParent());
            CompoundTag payload = new CompoundTag();
            payload.putInt("value", 4242);
            CompoundTag file = new CompoundTag();
            file.put("data", payload);
            file.putInt("DataVersion", 4671);
            NbtIo.writeCompressed(file, legacy);

            SavedDataType<Probe> type = LegacySavedData.type(name, lvl -> new Probe(-1), lvl -> Probe.CODEC,
                    DataFixTypes.SAVED_DATA_MAP_DATA);
            Probe probe = level.getDataStorage().computeIfAbsent(type);
            // Keep the throwaway out of the world save.
            probe.setDirty(false);

            helper.assertTrue(probe.value == 4242,
                    "the pre-26.1 file must be what the storage hands back (got " + probe.value
                            + ", -1 means the legacy file was ignored and the data started fresh)");
        } catch (IOException e) {
            helper.fail("could not lay down the legacy file: " + e);
            return;
        } finally {
            try {
                Files.deleteIfExists(legacy);
            } catch (IOException ignored) {
                // a leftover file in a throwaway test world is harmless
            }
        }
        helper.succeed();
        //?} else {
        /*// Before 26.1 the layout never moved: nothing to migrate, nothing to test.
        helper.succeed();*/
        //?}
    }

    //? if >=26.1 {
    private static void writeSavedDataFile(Path file, int value, int dataVersion) throws IOException {
        Files.createDirectories(file.getParent());
        CompoundTag payload = new CompoundTag();
        payload.putInt("value", value);
        CompoundTag wrapper = new CompoundTag();
        wrapper.put("data", payload);
        wrapper.putInt("DataVersion", dataVersion);
        NbtIo.writeCompressed(wrapper, file);
    }
    //?}

    /**
     * With BOTH files on disk the real storage hands back the 26.1+ file and never asks for the pre-26.1 one, and the
     * pre-26.1 file is left byte-for-byte intact. This pins today's behaviour for the open gap in
     * {@link LegacySavedData} (a 1.21.x world already saved once by a 26.x build without the migration keeps that
     * build's fresh file): the old data is still on disk to recover. A merge of the two would flip the first assert.
     */
    public static void bothFilesPresentTheCurrentWinsAndTheLegacyIsKept(GameTestHelper helper) {
        //? if >=26.1 {
        ServerLevel level = helper.getLevel();
        helper.assertTrue(level.dimension() == Level.OVERWORLD,
                "the layouts asserted here are the overworld's");
        Path worldRoot = level.getServer().getWorldPath(LevelResource.ROOT);
        String name = "buildcraft_test_legacy_both_" + Long.toHexString(System.nanoTime());
        Path legacy = worldRoot.resolve("data").resolve(name + ".dat");
        // Where the 26.1+ storage of the overworld keeps it: <dimension folder>/data/minecraft/<name>.dat.
        Path current = DimensionType.getStorageFolder(level.dimension(), worldRoot).resolve("data")
                .resolve("minecraft").resolve(name + ".dat");
        try {
            writeSavedDataFile(legacy, 1111, 4671);
            writeSavedDataFile(current, 2222, SharedConstants.getCurrentVersion().dataVersion().version());
            byte[] legacyBefore = Files.readAllBytes(legacy);

            SavedDataType<Probe> type = LegacySavedData.type(name, lvl -> new Probe(-1), lvl -> Probe.CODEC,
                    DataFixTypes.SAVED_DATA_MAP_DATA);
            Probe probe = level.getDataStorage().computeIfAbsent(type);
            probe.setDirty(false);

            helper.assertTrue(probe.value == 2222,
                    "with both files present the 26.1+ file must be what the storage hands back (got " + probe.value
                            + "; 1111 is the pre-26.1 file, -1 a fresh start)");
            helper.assertTrue(Arrays.equals(legacyBefore, Files.readAllBytes(legacy)),
                    "the pre-26.1 file must be left untouched, so its data stays recoverable");
        } catch (IOException e) {
            helper.fail("could not lay down the saved-data files: " + e);
            return;
        } finally {
            try {
                Files.deleteIfExists(legacy);
                Files.deleteIfExists(current);
            } catch (IOException ignored) {
                // a leftover file in a throwaway test world is harmless
            }
        }
        helper.succeed();
        //?} else {
        /*// Before 26.1 the layout never moved: there is only one file.
        helper.succeed();*/
        //?}
    }
}
