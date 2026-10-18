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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.SavedDataStorage;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;

import buildcraft.api.robots.ResourceIdBlock;
import buildcraft.api.transport.EnumWirePart;
import buildcraft.core.marker.PathSavedData;
import buildcraft.core.marker.VolumeSavedData;
import buildcraft.core.marker.volume.LevelSavedDataVolumeBoxes;
import buildcraft.core.marker.volume.VolumeBox;
import buildcraft.robotics.RobotRegistry;
import buildcraft.transport.wire.SavedDataWireSystems;
import buildcraft.transport.wire.WireSystem;
//?}

/**
 * Game test for {@link LegacySavedData} against a REAL level and its real saved-data storage: a file laid down where a
 * 1.21.x build kept the overworld's saved data (the world's root {@code data} folder) is what the 26.1+ storage hands
 * back, even though the storage itself looks somewhere else entirely. The pure path mapping and the file read are the
 * unit tests' ({@link LegacySavedDataTest}); this one is what proves they agree with where vanilla really looks.
 *
 * <p>The three scenarios at the bottom run every real BuildCraft type (robot registry, wire systems, both marker kinds,
 * volume boxes) through a real {@code SavedDataStorage} over a temporary world layout: the world root is pointed at a
 * temporary folder for the length of the test ({@link LegacySavedData#worldRoot}), so the real types, with their real
 * codecs and rules for "holds no content", meet files the test controls and never the game test world's own.
 */
public class LegacySavedDataTester {

    //? if >=26.1 {
    /** A throwaway saved-data payload: one int (0 means no content). */
    private static final class Probe extends SavedData implements LegacySavedData.Recoverable {
        static final Codec<Probe> CODEC = Codec.INT.fieldOf("value").xmap(Probe::new, p -> p.value).codec();

        final int value;

        Probe(int value) {
            this.value = value;
        }

        @Override
        public boolean hasNoContent() {
            return value == 0;
        }

        @Override
        public String describeContent() {
            return "value " + value;
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

    /**
     * The volume boxes' 26.1+ type builds its data (fresh and decoded) for the level the storage hands it, not for the
     * level its builder was given. {@code createType(world)} used to close over its argument, so a type built for one
     * level and handed to another level's storage would have put its boxes in the wrong world; the argument is only
     * meaningful before 26.1 (the older factories get no level). A type built for NO level shows it: the data it makes
     * for the storage's level must still carry that level.
     */
    public static void volumeBoxesTypeBuildsForTheStoragesLevel(GameTestHelper helper) {
        //? if >=26.1 {
        ServerLevel level = helper.getLevel();
        SavedDataType<LevelSavedDataVolumeBoxes> type = LevelSavedDataVolumeBoxes.createType(null);

        LevelSavedDataVolumeBoxes fresh = type.factory().create(level);
        helper.assertTrue(fresh.world == level,
                "fresh data must belong to the level the storage gave the factory, got " + fresh.world);

        CompoundTag stored = new CompoundTag();
        net.minecraft.nbt.ListTag boxes = new net.minecraft.nbt.ListTag();
        boxes.add(new VolumeBox(level, new BlockPos(1, 64, 1)).writeToNBT());
        stored.put("volumeBoxes", boxes);
        LevelSavedDataVolumeBoxes decoded = type.codecFactory().create(level).parse(NbtOps.INSTANCE, stored)
                .result().orElseThrow();
        helper.assertTrue(decoded.world == level,
                "decoded data must belong to the level the storage gave the codec factory, got " + decoded.world);
        helper.assertTrue(decoded.volumeBoxes.size() == 1 && decoded.volumeBoxes.get(0).world == level,
                "...and so must each box in it");
        helper.succeed();
        //?} else {
        /*// Before 26.1 the factories are handed no level: nothing to test.
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
     * With BOTH files on disk and real content in the 26.1+ one, the real storage hands back the 26.1+ file and never
     * asks for the pre-26.1 one, and the pre-26.1 file is left byte-for-byte intact. (When the 26.1+ file holds no
     * content the old data replaces it instead - {@link #everyTypeRecoversTheLegacyDataOverAnEmptyCurrentFile}.)
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

    // ---- every real type, in a temporary world layout ----

    /**
     * A world opened by a 26.x build from before the migration: its fresh, empty 26.1+ file and the original 1.21.x
     * file side by side. Every type must take the 1.21.x data (the empty file's state kept), write it out, retire the
     * old file, and - once the player empties the data again - never bring it back.
     */
    public static void everyTypeRecoversTheLegacyDataOverAnEmptyCurrentFile(GameTestHelper helper) {
        //? if >=26.1 {
        runScenario(helper, Case::emptyCurrentIsRecovered);
        //?} else {
        /*// Before 26.1 the layout never moved: there is only one file.
        helper.succeed();*/
        //?}
    }

    /** ...but a 26.1+ file with real content is never overridden, whatever the 1.21.x file holds. */
    public static void everyTypeKeepsACurrentFileWithContent(GameTestHelper helper) {
        //? if >=26.1 {
        runScenario(helper, Case::currentWithContentWins);
        //?} else {
        /*// Before 26.1 the layout never moved: there is only one file.
        helper.succeed();*/
        //?}
    }

    /** A world no 26.x build has opened: the 1.21.x file alone still migrates, and is retired once written. */
    public static void everyTypeMigratesALegacyFileAlone(GameTestHelper helper) {
        //? if >=26.1 {
        runScenario(helper, Case::legacyAloneMigrates);
        //?} else {
        /*// Before 26.1 the layout never moved: there is only one file.
        helper.succeed();*/
        //?}
    }

    //? if >=26.1 {
    /** The data version of the last release that used the old layout. */
    private static final int DATA_VERSION_1_21_11 = 4671;

    private interface Scenario {
        void run(Case<?> type, Env env) throws Exception;
    }

    /** The temporary world one scenario runs in: a folder standing in for the world root, and the failures so far. */
    private static final class Env {
        final ServerLevel level;
        final Path root;
        final RegistryOps<Tag> ops;
        final List<String> failures = new ArrayList<>();

        Env(ServerLevel level, Path root) {
            this.level = level;
            this.root = root;
            this.ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        }

        /** Where a 1.21.x build kept the overworld's data: the world root's own data folder. */
        Path legacyFolder() {
            return root.resolve("data");
        }

        /** Where the 26.1+ storage keeps it: dimensions/minecraft/overworld/data. */
        Path currentFolder() {
            return DimensionType.getStorageFolder(level.dimension(), root).resolve("data");
        }

        SavedDataStorage storage() {
            return new SavedDataStorage(level, currentFolder(), DataFixers.getDataFixer(), level.registryAccess());
        }

        /** Saves {@code storage} the way a level does: the storage first, then {@code LevelEvent.Save}, which is what
         *  the migration's real listener answers by checking the write landed (it finds this temporary world through
         *  the redirected world root) - so this also proves the listener is registered. */
        void saveLikeALevel(SavedDataStorage storage) {
            storage.saveAndJoin();
            NeoForge.EVENT_BUS.post(new LevelEvent.Save(level));
        }
    }

    private static void runScenario(GameTestHelper helper, Scenario scenario) {
        ServerLevel level = helper.getLevel();
        helper.assertTrue(level.dimension() == Level.OVERWORLD, "the layouts asserted here are the overworld's");
        Path root;
        try {
            root = Files.createTempDirectory("buildcraft-legacy-saved-data");
        } catch (IOException e) {
            helper.fail("could not create a temporary world: " + e);
            return;
        }
        Function<ServerLevel, Path> previousRoot = LegacySavedData.worldRoot;
        Env env = new Env(level, root);
        LegacySavedData.worldRoot = lvl -> root;
        try {
            for (Case<?> type : cases()) {
                try {
                    scenario.run(type, env);
                } catch (Exception | AssertionError e) {
                    env.failures.add("[" + type.label + "] threw " + e);
                }
            }
        } finally {
            LegacySavedData.worldRoot = previousRoot;
            deleteTree(root);
        }
        helper.assertTrue(env.failures.isEmpty(), String.join("; ", env.failures));
        helper.succeed();
    }

    private static void deleteTree(Path root) {
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // a leftover file in the temp folder is harmless
                }
            });
        } catch (IOException ignored) {
            // nothing to clean up
        }
    }

    /** One real saved-data type: how to make it hold {@code n} entries, and the three scenarios over it. */
    private abstract static class Case<T extends SavedData & LegacySavedData.Recoverable> {
        final String label;

        Case(String label) {
            this.label = label;
        }

        abstract SavedDataType<T> type(ServerLevel level);

        /** Adds {@code entries} distinct entries to {@code data}. */
        abstract void fill(ServerLevel level, T data, int entries);

        /** A type's data with {@code entries} entries, built while no file for it exists in the temporary world. */
        final T instance(Env env, int entries) {
            T data = type(env.level).factory().create(env.level);
            fill(env.level, data, entries);
            return data;
        }

        /** What {@code data} reads as after a save and a load: loading can move state that is not content (the robot
         *  registry raises its id counter past every robot id it names), so the in-memory instance is not what a
         *  loaded one is compared with. Call it while no file for the type exists in the temporary world. */
        final String savedDescription(Env env, T data) {
            SavedDataType<T> type = type(env.level);
            Tag tag = type.codecFactory().create(env.level).encodeStart(env.ops, data).getOrThrow();
            return type.codecFactory().create(env.level).parse(env.ops, tag).getOrThrow().describeContent();
        }

        final void check(Env env, boolean condition, String message) {
            if (!condition) {
                env.failures.add("[" + label + "] " + message);
            }
        }

        final void writeData(Env env, Path file, T data, int dataVersion) throws IOException {
            Tag payload = type(env.level).codecFactory().create(env.level).encodeStart(env.ops, data).getOrThrow();
            Files.createDirectories(file.getParent());
            CompoundTag wrapper = new CompoundTag();
            wrapper.put("data", payload);
            wrapper.putInt("DataVersion", dataVersion);
            NbtIo.writeCompressed(wrapper, file);
        }

        private static int currentDataVersion() {
            return SharedConstants.getCurrentVersion().dataVersion().version();
        }

        private String fileName(Env env) {
            return type(env.level).id().getPath() + ".dat";
        }

        final void emptyCurrentIsRecovered(Env env) throws IOException {
            SavedDataType<T> type = type(env.level);
            // Built before any file exists: once the old file is down, a new instance would start from it.
            T empty = instance(env, 0);
            T three = instance(env, 3);
            String expected = savedDescription(env, three);
            Path legacy = env.legacyFolder().resolve(fileName(env));
            Path current = env.currentFolder().resolve("minecraft").resolve(fileName(env));
            Path migrated = legacy.resolveSibling(legacy.getFileName() + LegacySavedData.MIGRATED_SUFFIX);
            writeData(env, legacy, three, DATA_VERSION_1_21_11);
            writeData(env, current, empty, currentDataVersion());
            byte[] legacyBytes = Files.readAllBytes(legacy);

            try (SavedDataStorage storage = env.storage()) {
                T got = storage.computeIfAbsent(type);
                check(env, expected.equals(got.describeContent()), "an empty 26.1+ file must give way to the 1.21.x "
                        + "data: expected [" + expected + "], got [" + got.describeContent() + "]");
                check(env, got.isDirty(), "the recovered data must be marked for saving");
                check(env, Files.isRegularFile(legacy), "the 1.21.x file must stay until the data is written");

                env.saveLikeALevel(storage);
            }
            check(env, !Files.exists(legacy), "the 1.21.x file must be retired once its data is written");
            check(env, Files.isRegularFile(migrated) && Arrays.equals(legacyBytes, Files.readAllBytes(migrated)),
                    "...and kept, byte for byte, as " + migrated.getFileName());

            try (SavedDataStorage storage = env.storage()) {
                T got = storage.computeIfAbsent(type);
                check(env, expected.equals(got.describeContent()),
                        "the 26.1+ file must now hold the recovered data: got [" + got.describeContent() + "]");
            }

            // The player empties everything and the world saves: the 26.1+ file holds no content again.
            writeData(env, current, empty, currentDataVersion());
            try (SavedDataStorage storage = env.storage()) {
                T got = storage.computeIfAbsent(type);
                check(env, got.hasNoContent(), "deleted data must stay deleted, not come back from the retired "
                        + "1.21.x file: got [" + got.describeContent() + "]");
            }
        }

        final void currentWithContentWins(Env env) throws IOException {
            SavedDataType<T> type = type(env.level);
            // Both built before any file exists: once the old file is down, a new instance would start from it.
            T one = instance(env, 1);
            T three = instance(env, 3);
            String expected = savedDescription(env, one);
            Path legacy = env.legacyFolder().resolve(fileName(env));
            Path current = env.currentFolder().resolve("minecraft").resolve(fileName(env));
            writeData(env, legacy, three, DATA_VERSION_1_21_11);
            writeData(env, current, one, currentDataVersion());
            byte[] legacyBytes = Files.readAllBytes(legacy);

            try (SavedDataStorage storage = env.storage()) {
                T got = storage.computeIfAbsent(type);
                check(env, expected.equals(got.describeContent()), "a 26.1+ file with content must be what loads: "
                        + "expected [" + expected + "], got [" + got.describeContent() + "]");

                got.setDirty();
                env.saveLikeALevel(storage);
            }
            check(env, Files.isRegularFile(legacy) && Arrays.equals(legacyBytes, Files.readAllBytes(legacy)),
                    "the 1.21.x file must be left exactly as it was");
            try (SavedDataStorage storage = env.storage()) {
                T got = storage.computeIfAbsent(type);
                check(env, expected.equals(got.describeContent()), "...and the 26.1+ file keeps its own data: got ["
                        + got.describeContent() + "]");
            }
        }

        final void legacyAloneMigrates(Env env) throws IOException {
            SavedDataType<T> type = type(env.level);
            T three = instance(env, 3);
            String expected = savedDescription(env, three);
            Path legacy = env.legacyFolder().resolve(fileName(env));
            Path migrated = legacy.resolveSibling(legacy.getFileName() + LegacySavedData.MIGRATED_SUFFIX);
            writeData(env, legacy, three, DATA_VERSION_1_21_11);
            byte[] legacyBytes = Files.readAllBytes(legacy);

            try (SavedDataStorage storage = env.storage()) {
                T got = storage.computeIfAbsent(type);
                check(env, expected.equals(got.describeContent()), "the 1.21.x file alone must migrate: expected ["
                        + expected + "], got [" + got.describeContent() + "]");
                check(env, Files.isRegularFile(legacy), "the 1.21.x file must stay until the data is written");

                env.saveLikeALevel(storage);
            }
            check(env, !Files.exists(legacy) && Files.isRegularFile(migrated)
                    && Arrays.equals(legacyBytes, Files.readAllBytes(migrated)),
                    "the 1.21.x file must be retired, intact, once its data is written");
            try (SavedDataStorage storage = env.storage()) {
                T got = storage.computeIfAbsent(type);
                check(env, expected.equals(got.describeContent()),
                        "the 26.1+ file must now hold the migrated data: got [" + got.describeContent() + "]");
            }
        }
    }

    private static List<Case<?>> cases() {
        return List.of(
                new Case<RobotRegistry>("robot registry") {
                    @Override
                    SavedDataType<RobotRegistry> type(ServerLevel level) {
                        return RobotRegistry.TYPE;
                    }

                    @Override
                    void fill(ServerLevel level, RobotRegistry data, int entries) {
                        // Ids from the registry's own counter, as real robots get theirs: a registry whose counter is
                        // behind the ids it names is raised (and marked for saving) on load.
                        for (int i = 0; i < entries; i++) {
                            data.take(new ResourceIdBlock(new BlockPos(i, 64, 0)), data.getNextRobotId());
                        }
                    }
                },
                new Case<SavedDataWireSystems>("wire systems") {
                    @Override
                    SavedDataType<SavedDataWireSystems> type(ServerLevel level) {
                        return SavedDataWireSystems.TYPE;
                    }

                    @Override
                    void fill(ServerLevel level, SavedDataWireSystems data, int entries) {
                        for (int i = 0; i < entries; i++) {
                            data.addWireSystem(new WireSystem(ImmutableList.of(new WireSystem.WireElement(
                                    new BlockPos(i, 64, 0), EnumWirePart.EAST_UP_SOUTH)), DyeColor.RED), false);
                        }
                    }
                },
                new Case<VolumeSavedData>("volume markers") {
                    @Override
                    SavedDataType<VolumeSavedData> type(ServerLevel level) {
                        return VolumeSavedData.TYPE;
                    }

                    @Override
                    void fill(ServerLevel level, VolumeSavedData data, int entries) {
                        for (int i = 0; i < entries; i++) {
                            data.markerPositions.add(new BlockPos(i, 64, 0));
                        }
                    }
                },
                new Case<PathSavedData>("path markers") {
                    @Override
                    SavedDataType<PathSavedData> type(ServerLevel level) {
                        return PathSavedData.TYPE;
                    }

                    @Override
                    void fill(ServerLevel level, PathSavedData data, int entries) {
                        for (int i = 0; i < entries; i++) {
                            data.markerPositions.add(new BlockPos(i, 64, 0));
                        }
                    }
                },
                new Case<LevelSavedDataVolumeBoxes>("volume boxes") {
                    @Override
                    SavedDataType<LevelSavedDataVolumeBoxes> type(ServerLevel level) {
                        return LevelSavedDataVolumeBoxes.createType(level);
                    }

                    @Override
                    void fill(ServerLevel level, LevelSavedDataVolumeBoxes data, int entries) {
                        for (int i = 0; i < entries; i++) {
                            data.volumeBoxes.add(new VolumeBox(level, new BlockPos(i, 64, 0)));
                        }
                    }
                });
    }
    //?}
}
