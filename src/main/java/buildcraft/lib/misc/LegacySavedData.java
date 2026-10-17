/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.misc;

import java.nio.file.Path;

//? if >=26.1 {
import java.nio.file.Files;
import java.util.function.Function;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.LevelResource;

import org.jspecify.annotations.Nullable;

import buildcraft.api.core.BCLog;
//?}

/**
 * Carries a mod's {@code SavedData} across the 26.1 world-folder change.
 *
 * <p>Up to 1.21.11 a saved-data file is {@code <dimension folder>/data/<name>.dat}; from 26.1 it is
 * {@code <dimension folder>/data/<namespace>/<name>.dat}, and the vanilla dimensions' folders moved too (the overworld
 * from the world root, the nether and end from {@code DIM-1}/{@code DIM1}, all into {@code dimensions/minecraft/...}).
 * Vanilla's own file fixer moves only the files it knows by name, so a mod's file stays where the old build wrote it
 * and the new storage, looking somewhere else, finds nothing: the first launch after the upgrade starts every one of
 * the mod's saved data from scratch (the robot registry hands out ids robots already carry, volume boxes and markers
 * vanish, wire systems rebuild).
 *
 * <p>{@link #type} builds a {@code SavedDataType} whose factory - run by the storage only when the 26.1+ location has
 * no file - looks in the old location first and starts the data from that file when it exists. The old file is only
 * read, never moved or deleted: it stays as the backup until the new one has been saved, and a crash before the first
 * save just repeats the read.
 *
 * <p>Known gap: the old file is read only while the 26.1+ one is absent, and nothing marks an old file as carried
 * over, so "both exist" cannot tell a world this code migrated from one a 26.x build WITHOUT this migration already
 * saved. The storage marks the data it creates dirty ({@code SavedDataStorage.set}), so such a build wrote a fresh
 * 26.1+ file the first time anything asked for the type, and from then on that file wins: the world keeps that
 * build's data and the 1.21.x data stays unread (the robot registry's id guards in {@code RobotRegistry} keep its
 * ids safe regardless). The old file is still on disk; moving the 26.1+ file aside before a launch re-imports it.
 *
 * <p>Every BuildCraft saved-data type is built this way
 * ({@code LegacySavedDataGuardTester} enforces it). The id of a type must never change: it is the file name under both
 * layouts.
 */
public final class LegacySavedData {
    private LegacySavedData() {}

    /** The folder a build up to 1.21.11 kept the saved data of dimension {@code namespace:path} in, given the world's
     *  root folder: the world root's own {@code data} for the overworld, {@code DIM-1}/{@code DIM1} for the nether and
     *  end, and {@code dimensions/<namespace>/<path>/data} for every other dimension. */
    public static Path legacyDataFolder(Path worldRoot, String dimensionNamespace, String dimensionPath) {
        if ("minecraft".equals(dimensionNamespace)) {
            switch (dimensionPath) {
                case "overworld":
                    return worldRoot.resolve("data");
                case "the_nether":
                    return worldRoot.resolve("DIM-1").resolve("data");
                case "the_end":
                    return worldRoot.resolve("DIM1").resolve("data");
                default:
                    break;
            }
        }
        return worldRoot.resolve("dimensions").resolve(dimensionNamespace).resolve(dimensionPath).resolve("data");
    }

    //? if >=26.1 {
    /**
     * A saved-data type named {@code minecraft:<name>} that starts from the pre-26.1 file when this dimension has one
     * and the 26.1+ location has none. {@code fresh} builds the empty data, {@code codec} reads and writes it (both are
     * given the level the storage belongs to, null only for a storage with no level).
     *
     * <p>The namespace is {@code minecraft} on purpose: it is what the existing 26.1+ worlds' files are already named
     * under, and a different one would orphan them the way the missing migration orphans a 1.21.x world's.
     */
    public static <T extends SavedData> SavedDataType<T> type(String name, Function<@Nullable ServerLevel, T> fresh,
            Function<@Nullable ServerLevel, Codec<T>> codec, DataFixTypes fixType) {
        Identifier id = Identifier.withDefaultNamespace(name);
        return new SavedDataType<>(id, level -> {
            T migrated = readFromLegacyLocation(level, id, codec, fixType);
            return migrated != null ? migrated : fresh.apply(level);
        }, codec::apply, fixType);
    }

    private static <T extends SavedData> @Nullable T readFromLegacyLocation(@Nullable ServerLevel level, Identifier id,
            Function<@Nullable ServerLevel, Codec<T>> codec, DataFixTypes fixType) {
        if (level == null) {
            return null;
        }
        Path worldRoot = level.getServer().getWorldPath(LevelResource.ROOT);
        Identifier dimension = level.dimension().identifier();
        // Where the 26.1+ storage of this dimension keeps the file (ServerChunkCache: <dimension folder>/data, then
        // <namespace>/<name>.dat), and where the build before it did.
        Path current = DimensionType.getStorageFolder(level.dimension(), worldRoot).resolve("data")
                .resolve(id.getNamespace()).resolve(id.getPath() + ".dat");
        Path legacy = legacyDataFolder(worldRoot, dimension.getNamespace(), dimension.getPath())
                .resolve(id.getPath() + ".dat");

        Tag payload = readLegacyFile(current, legacy, fixType);
        if (payload == null) {
            return null;
        }
        RegistryOps<Tag> ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        T parsed = parseLegacyPayload(codec.apply(level), ops, payload, legacy);
        if (parsed != null) {
            BCLog.logger.info("[saved data] Carried {} over from the pre-26.1 location {}", id, legacy);
        }
        return parsed;
    }

    /**
     * Decodes the payload of {@code legacyFile}, or null (logged) when it cannot be: the data then starts fresh. Runs
     * inside the storage's factory call, which - unlike vanilla's own read of a 26.1+ file - has no catch around it,
     * so a decode that throws (a 1.21.x volume box with a malformed id dies in {@code UUID.fromString}) must be
     * stopped here or it fails the level load.
     *
     * <p>A partial result is accepted, as {@code SavedDataStorage.readSavedData} accepts one for vanilla's own files:
     * whatever decoded is more than a fresh start keeps. Either way the outcome becomes the 26.1+ file on the next
     * save and the old file is not read again (it stays on disk untouched, so the original is never lost).
     */
    public static <T> @Nullable T parseLegacyPayload(Codec<T> codec, DynamicOps<Tag> ops, Tag payload,
            Path legacyFile) {
        try {
            return codec.parse(ops, payload)
                    .resultOrPartial(error -> BCLog.logger.error(
                            "[saved data] Could not read the pre-26.1 file {}: {}", legacyFile, error))
                    .orElse(null);
        } catch (RuntimeException e) {
            BCLog.logger.error("[saved data] Could not read the pre-26.1 file " + legacyFile + "; starting fresh", e);
            return null;
        }
    }

    /**
     * The {@code data} payload of {@code legacyFile}, brought up to the current data version, or null when there is
     * nothing to carry over: the 26.1+ file {@code currentFile} already exists (it always wins, including the fresh one
     * a 26.x build without this migration wrote - see the class note on the known gap), the old file does not exist,
     * or it cannot be read (logged; the data then starts fresh, as it does for vanilla's own saved data). Never writes, moves or deletes either file.
     */
    public static @Nullable Tag readLegacyFile(Path currentFile, Path legacyFile, DataFixTypes fixType) {
        if (Files.exists(currentFile) || !Files.isRegularFile(legacyFile)) {
            return null;
        }
        try {
            CompoundTag file = NbtIo.readCompressed(legacyFile, NbtAccounter.unlimitedHeap());
            int fileVersion = NbtUtils.getDataVersion(file, 1343);
            int currentVersion = SharedConstants.getCurrentVersion().dataVersion().version();
            return fixType.update(DataFixers.getDataFixer(), file, fileVersion, currentVersion).get("data");
        } catch (Exception e) {
            BCLog.logger.error("[saved data] Could not read the pre-26.1 file " + legacyFile, e);
            return null;
        }
    }
    //?}
}
