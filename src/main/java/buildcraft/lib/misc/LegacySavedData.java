/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.misc;

import java.nio.file.Path;

import net.minecraft.world.level.saveddata.SavedData;

//? if >=26.1 {
import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.function.Supplier;

import com.google.common.collect.MapMaker;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Encoder;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.dimension.DimensionType;
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
 * <p>{@link #type} builds a {@code SavedDataType} that looks in the old location in two situations:
 * <ul>
 * <li><b>The 26.1+ file is absent</b> (the world was never opened by a 26.x build): the storage's factory starts the
 * data from the old file when it exists.</li>
 * <li><b>The 26.1+ file exists but holds no content</b> ({@link Recoverable#hasNoContent}: no robots, no boxes, no
 * markers, no wire systems): a 26.x build from before this migration opened the world, found nothing and saved a fresh
 * empty file, which then won for good. The decode of that file is replaced by the old file's data, with the empty
 * file's counters kept ({@link Recoverable#absorbBlank}). A 26.1+ file with real content is never overridden.</li>
 * </ul>
 *
 * <p>The old file is read, not consumed, until the recovered data is about to be written: when the storage encodes
 * the instance for its first save, the old file is renamed to {@code <name>.dat.migrated}. Without that, an old file
 * would be re-read whenever the 26.1+ file is empty again (every box removed, every robot gone), resurrecting data the
 * player deleted. A crash before the first save leaves the old file where it was, so the next launch repeats the read.
 *
 * <p>Every BuildCraft saved-data type is built this way
 * ({@code LegacySavedDataGuardTester} enforces it). The id of a type must never change: it is the file name under both
 * layouts.
 */
public final class LegacySavedData {
    private LegacySavedData() {}

    /** Suffix an old-layout file is renamed to once the data read from it has been handed to the 26.1+ storage. */
    public static final String MIGRATED_SUFFIX = ".migrated";

    /**
     * What a saved-data type tells the migration about its own content. Defined on every node (the types exist on every
     * node); only 26.1+ calls it.
     */
    public interface Recoverable {
        /**
         * True when this data holds nothing a player could have lost: a 26.1+ file that decodes to such data is the
         * empty file a build without the migration wrote, and the old file is read in its place. Be conservative: any
         * real entry (a box, a marker, a wire system, a docking station, a reservation) makes this false, because a
         * false answer only declines to recover while a wrong true overrides a file with content.
         */
        boolean hasNoContent();

        /** A short line for the log: what this data holds. */
        String describeContent();

        /**
         * Called on data recovered from the old file with the empty data it replaces, so state that lives outside the
         * "content" (a counter the empty file had already advanced) is not rolled back. Default: nothing to keep.
         */
        default void absorbBlank(SavedData blank) {}
    }

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
    /** Where one dimension's file lives under each layout, and the ops its payload is decoded with. */
    record Layout(Path current, Path legacy, DynamicOps<Tag> ops) {}

    /**
     * Where the world's root folder is, for the layout of a level. Tests point it at a temporary world so the real
     * types can be exercised against a layout they control; nothing else assigns it. Only touched from the server
     * thread, in the middle of a synchronous test.
     */
    static volatile Function<ServerLevel, Path> worldRoot = level -> level.getServer().getWorldPath(LevelResource.ROOT);

    /** Data that came from an old-layout file and has not been saved yet, to that file. Weak keys, by identity: an
     *  instance dropped with its storage must not be pinned here. */
    private static final ConcurrentMap<SavedData, Path> CARRIED_OVER = new MapMaker().weakKeys().makeMap();

    /**
     * A saved-data type named {@code minecraft:<name>} that starts from the pre-26.1 file when this dimension has one
     * and the 26.1+ location has none, and replaces a 26.1+ file that holds no content with it. {@code fresh} builds
     * the empty data, {@code codec} reads and writes it (both are given the level the storage belongs to, null only
     * for a storage with no level).
     *
     * <p>The namespace is {@code minecraft} on purpose: it is what the existing 26.1+ worlds' files are already named
     * under, and a different one would orphan them the way the missing migration orphans a 1.21.x world's.
     */
    public static <T extends SavedData & Recoverable> SavedDataType<T> type(String name,
            Function<@Nullable ServerLevel, T> fresh, Function<@Nullable ServerLevel, Codec<T>> codec,
            DataFixTypes fixType) {
        Identifier id = Identifier.withDefaultNamespace(name);
        return typeAt(id, fresh, codec, fixType, level -> layoutOf(level, id));
    }

    /** {@link #type} with the file locations supplied, so a test can use a temporary layout and no level. */
    static <T extends SavedData & Recoverable> SavedDataType<T> typeAt(Identifier id,
            Function<@Nullable ServerLevel, T> fresh, Function<@Nullable ServerLevel, Codec<T>> codec,
            DataFixTypes fixType, Function<@Nullable ServerLevel, @Nullable Layout> layoutOf) {
        return new SavedDataType<>(id, level -> {
            T migrated = readFromLegacyLocation(layoutOf.apply(level), codec.apply(level), id, fixType);
            return migrated != null ? migrated : fresh.apply(level);
        }, level -> recovering(id, codec.apply(level), fixType, () -> layoutOf.apply(level)), fixType);
    }

    private static @Nullable Layout layoutOf(@Nullable ServerLevel level, Identifier id) {
        if (level == null) {
            return null;
        }
        Path root = worldRoot.apply(level);
        Identifier dimension = level.dimension().identifier();
        // Where the 26.1+ storage of this dimension keeps the file (ServerChunkCache: <dimension folder>/data, then
        // <namespace>/<name>.dat), and where the build before it did.
        Path current = DimensionType.getStorageFolder(level.dimension(), root).resolve("data")
                .resolve(id.getNamespace()).resolve(id.getPath() + ".dat");
        Path legacy = legacyDataFolder(root, dimension.getNamespace(), dimension.getPath())
                .resolve(id.getPath() + ".dat");
        return new Layout(current, legacy, level.registryAccess().createSerializationContext(NbtOps.INSTANCE));
    }

    /** The factory path: the storage found no 26.1+ file, so the old one, when there is one, is the data. */
    private static <T extends SavedData & Recoverable> @Nullable T readFromLegacyLocation(@Nullable Layout layout,
            Codec<T> codec, Identifier id, DataFixTypes fixType) {
        if (layout == null) {
            return null;
        }
        Tag payload = readLegacyFile(layout.current(), layout.legacy(), fixType);
        if (payload == null) {
            return null;
        }
        T parsed = parseLegacyPayload(codec, layout.ops(), payload, layout.legacy());
        if (parsed != null) {
            CARRIED_OVER.put(parsed, layout.legacy());
            BCLog.logger.info("[saved data] Carried {} over from the pre-26.1 location {}: {}", id, layout.legacy(),
                    parsed.describeContent());
        }
        return parsed;
    }

    /**
     * The codec the storage reads and writes the 26.1+ file with. Reading is {@code plain}'s, except that data which
     * decodes to no content is replaced by the old file's when there is one ({@link #recoverOverBlank}); writing is
     * {@code plain}'s, followed by retiring the old file that data was carried over from.
     */
    private static <T extends SavedData & Recoverable> Codec<T> recovering(Identifier id, Codec<T> plain,
            DataFixTypes fixType, Supplier<@Nullable Layout> layout) {
        Decoder<T> decoder = new Decoder<>() {
            @Override
            public <O> DataResult<Pair<T, O>> decode(DynamicOps<O> ops, O input) {
                DataResult<Pair<T, O>> result = plain.decode(ops, input);
                Optional<Pair<T, O>> decoded = result.result();
                if (decoded.isEmpty() || !decoded.get().getFirst().hasNoContent()) {
                    return result;
                }
                T recovered = recoverOverBlank(id, plain, fixType, layout.get(), decoded.get().getFirst());
                return recovered == null ? result : DataResult.success(Pair.of(recovered, decoded.get().getSecond()));
            }
        };
        Encoder<T> encoder = new Encoder<>() {
            @Override
            public <O> DataResult<O> encode(T input, DynamicOps<O> ops, O prefix) {
                DataResult<O> encoded = plain.encode(input, ops, prefix);
                if (encoded.result().isPresent()) {
                    retireCarriedOverFile(input);
                }
                return encoded;
            }
        };
        return Codec.of(encoder, decoder);
    }

    /**
     * The old file's data in place of {@code blank}, or null when there is nothing to put there: no old file, an old
     * file that cannot be read or holds no content either.
     */
    private static <T extends SavedData & Recoverable> @Nullable T recoverOverBlank(Identifier id, Codec<T> plain,
            DataFixTypes fixType, @Nullable Layout layout, T blank) {
        if (layout == null) {
            return null;
        }
        Tag payload = readLegacyPayload(layout.legacy(), fixType);
        if (payload == null) {
            return null;
        }
        T recovered = parseLegacyPayload(plain, layout.ops(), payload, layout.legacy());
        if (recovered == null || recovered.hasNoContent()) {
            return null;
        }
        recovered.absorbBlank(blank);
        // The storage did not create this instance, so it does not know to save it: do it here, or the recovery
        // would be repeated (and could be undone by a later save of an empty file) until something else dirties it.
        recovered.setDirty();
        CARRIED_OVER.put(recovered, layout.legacy());
        BCLog.logger.info("[saved data] Recovered {} from the pre-26.1 file {}: the 26.1+ file {} held no content. "
                + "Recovered: {}", id, layout.legacy(), layout.current(), recovered.describeContent());
        return recovered;
    }

    /**
     * Renames the old file {@code data} was carried over from to {@code <name>.dat.migrated} (numbered if that name is
     * taken), now that the storage is writing {@code data} out; a no-op for data that was not carried over. The
     * storage encodes data for its file write immediately before the write, so a crash can only fall between the two
     * for the few milliseconds the write takes; the renamed file is still beside the world either way.
     */
    private static void retireCarriedOverFile(SavedData data) {
        Path legacy = CARRIED_OVER.get(data);
        if (legacy != null && retire(legacy)) {
            CARRIED_OVER.remove(data);
        }
    }

    /** Moves {@code legacyFile} aside, never over an earlier backup. True when it is gone from its place. */
    static boolean retire(Path legacyFile) {
        try {
            if (!Files.exists(legacyFile)) {
                return true;
            }
            Path name = legacyFile.getFileName();
            Path target = legacyFile.resolveSibling(name + MIGRATED_SUFFIX);
            for (int attempt = 1; Files.exists(target); attempt++) {
                target = legacyFile.resolveSibling(name + MIGRATED_SUFFIX + "." + attempt);
            }
            Files.move(legacyFile, target);
            BCLog.logger.info("[saved data] The pre-26.1 file {} was carried over; kept as {}", legacyFile,
                    target.getFileName());
            return true;
        } catch (IOException e) {
            BCLog.logger.warn("[saved data] Could not move the carried-over pre-26.1 file " + legacyFile
                    + " aside; it will be tried again at the next save", e);
            return false;
        }
    }

    /**
     * Decodes the payload of {@code legacyFile}, or null (logged) when it cannot be: the data then starts fresh. Runs
     * inside the storage's factory call, which - unlike vanilla's own read of a 26.1+ file - has no catch around it,
     * so a decode that throws (a 1.21.x volume box with a malformed id dies in {@code UUID.fromString}) must be
     * stopped here or it fails the level load.
     *
     * <p>A partial result is accepted, as {@code SavedDataStorage.readSavedData} accepts one for vanilla's own files:
     * whatever decoded is more than a fresh start keeps. Either way the outcome becomes the 26.1+ file on the next
     * save and the old file is retired then (it stays on disk, renamed, so the original is never lost).
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
     * nothing to carry over: the 26.1+ file {@code currentFile} already exists (the factory path - the storage found
     * no usable data in it, so it is a file it could not parse, which is never replaced with older data), the old file
     * does not exist, or it cannot be read (logged; the data then starts fresh, as it does for vanilla's own saved
     * data). Never writes, moves or deletes either file.
     */
    public static @Nullable Tag readLegacyFile(Path currentFile, Path legacyFile, DataFixTypes fixType) {
        if (Files.exists(currentFile)) {
            return null;
        }
        return readLegacyPayload(legacyFile, fixType);
    }

    /** {@link #readLegacyFile} without the check for a 26.1+ file: the payload of the old file, or null. */
    static @Nullable Tag readLegacyPayload(Path legacyFile, DataFixTypes fixType) {
        if (!Files.isRegularFile(legacyFile)) {
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
