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

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

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
 * <p>The old file is read, not consumed, until the 26.1+ file is seen on disk holding the carried-over data: after
 * each save of the level ({@code LevelEvent.Save}) and once more when the server has stopped, the 26.1+ file is read
 * back and compared with what the storage encoded, and only a match renames the old file to
 * {@code <name>.dat.migrated}. Without the rename, an old file would be re-read whenever the 26.1+ file is empty again
 * (every box removed, every robot gone), resurrecting data the player deleted. The encode alone proves nothing:
 * vanilla clears the dirty flag right after it and writes later, on another thread, only logging a write that fails
 * (a file held open by a virus scanner or a sync client). So a write that never lands leaves the old file in place
 * and the data is marked dirty again at the next save's check, and a session in which no write landed at all leaves
 * the old file to be carried over again at the next launch. A move of the old file that fails is retried after every
 * save. What is left: a crash between a landed write and the next check (up to one autosave interval), or a move
 * that fails all session, leaves the old file beside a 26.1+ file with content; that file wins at every launch, and
 * the old one comes back only if the 26.1+ file is later empty at a launch.
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

    /** Data that came from an old-layout file whose 26.1+ file has not been seen holding it yet, to its hand-over.
     *  Weak keys, by identity: an instance dropped with its storage must not be pinned here. */
    private static final ConcurrentMap<SavedData, CarriedOver> CARRIED_OVER = new MapMaker().weakKeys().makeMap();

    /**
     * Where one carried-over instance is in its hand-over. Touched from the server thread only (the storage encodes
     * there, and both events fire there); volatile for the unit tests' sake as much as anything.
     */
    private static final class CarriedOver {
        final Layout layout;
        /** The last two payloads the storage encoded: an autosave's write of the latest may still be under way when
         *  the check runs, and the one before it landing is just as good a proof. Null until the first encode. */
        volatile @Nullable Tag latest;
        volatile @Nullable Tag previous;
        /** True from an encode until the check after it: a mismatch then may only mean the write has not finished. */
        volatile boolean freshEncode;
        /** The 26.1+ file was seen holding the data: only the move of the old file is left. */
        volatile boolean written;

        CarriedOver(Layout layout) {
            this.layout = layout;
        }

        Path dataFolder() {
            // <data folder>/<namespace>/<name>.dat
            return layout.current().toAbsolutePath().normalize().getParent().getParent();
        }

        void encoded(Tag payload) {
            previous = latest;
            latest = payload;
            freshEncode = true;
        }

        boolean holds(@Nullable Tag onDisk) {
            return onDisk != null && (onDisk.equals(latest) || onDisk.equals(previous));
        }
    }

    /** Hooks the hand-over checks ({@link #checkWrites}) to the server's saves. Called once, from {@code BCLib}. */
    public static void registerListeners() {
        NeoForge.EVENT_BUS.addListener((LevelEvent.Save event) -> {
            if (!CARRIED_OVER.isEmpty() && event.getLevel() instanceof ServerLevel level) {
                checkWrites(dataFolderOf(level), true);
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
            // Every storage was closed, and every write joined, before this fires.
            CARRIED_OVER.forEach((data, carried) -> check(data, carried, false));
            CARRIED_OVER.clear();
        });
    }

    /** The folder the 26.1+ storage of {@code level} keeps its saved data in ({@code ServerChunkCache}). */
    private static Path dataFolderOf(ServerLevel level) {
        return DimensionType.getStorageFolder(level.dimension(), worldRoot.apply(level)).resolve("data");
    }

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
        // Where the 26.1+ storage of this dimension keeps the file (<namespace>/<name>.dat in its data folder), and
        // where the build before it did.
        Path current = dataFolderOf(level).resolve(id.getNamespace()).resolve(id.getPath() + ".dat");
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
            CARRIED_OVER.put(parsed, new CarriedOver(layout));
            BCLog.logger.info("[saved data] Carried {} over from the pre-26.1 location {}: {}", id, layout.legacy(),
                    parsed.describeContent());
        }
        return parsed;
    }

    /**
     * The codec the storage reads and writes the 26.1+ file with. Reading is {@code plain}'s, except that data which
     * decodes to no content is replaced by the old file's when there is one ({@link #recoverOverBlank}); writing is
     * {@code plain}'s, and for carried-over data the payload is kept, so a later {@link #checkWrites} can tell whether
     * the 26.1+ file on disk holds it. Nothing is moved here: the write comes after the encode, on another thread.
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
                CarriedOver carried = CARRIED_OVER.get(input);
                if (carried != null && encoded.result().orElse(null) instanceof Tag payload) {
                    carried.encoded(payload.copy());
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
        CARRIED_OVER.put(recovered, new CarriedOver(layout));
        BCLog.logger.info("[saved data] Recovered {} from the pre-26.1 file {}: the 26.1+ file {} held no content. "
                + "Recovered: {}", id, layout.legacy(), layout.current(), recovered.describeContent());
        return recovered;
    }

    /**
     * Checks, for the data carried over into the storage whose folder is {@code dataFolder}, whether its 26.1+ file
     * now holds what the storage encoded, and retires the old file of each one that does. Called after a level's
     * storage saved ({@code LevelEvent.Save}, {@code storageOpen} true) and once the server stopped and every write
     * was joined ({@code storageOpen} false).
     */
    static void checkWrites(Path dataFolder, boolean storageOpen) {
        Path folder = dataFolder.toAbsolutePath().normalize();
        CARRIED_OVER.forEach((data, carried) -> {
            if (folder.equals(carried.dataFolder())) {
                check(data, carried, storageOpen);
            }
        });
    }

    /**
     * One step of the hand-over of {@code data} from its old file to its 26.1+ file. The old file goes only once the
     * 26.1+ file is seen on disk holding an encoding of {@code data}: vanilla encodes, clears the dirty flag and only
     * then writes, on another thread, logging a failed write and nothing else, so neither the encode nor the dirty
     * flag says whether the data reached the disk.
     */
    private static void check(SavedData data, CarriedOver carried, boolean storageOpen) {
        Layout layout = carried.layout;
        if (!carried.written) {
            if (carried.latest == null) {
                // Never saved: the old file is still the only copy and is read again at the next launch.
                if (!storageOpen) {
                    CARRIED_OVER.remove(data);
                }
                return;
            }
            if (!carried.holds(readPayload(layout.current()))) {
                if (!storageOpen) {
                    BCLog.logger.warn("[saved data] No write of the data carried over from the pre-26.1 file {} "
                            + "reached {} before the server stopped; the pre-26.1 file stays, and the data is "
                            + "carried over from it again at the next launch", layout.legacy(), layout.current());
                    CARRIED_OVER.remove(data);
                } else if (carried.freshEncode) {
                    // Encoded by the save that just ran: an autosave's write may still be under way.
                    carried.freshEncode = false;
                } else {
                    // Encoded by an earlier save whose write never landed: write it again at the next one.
                    BCLog.logger.warn("[saved data] {} does not hold the data carried over from the pre-26.1 file {} "
                            + "(the write failed); writing it again at the next save, and the pre-26.1 file stays "
                            + "until a write lands", layout.current(), layout.legacy());
                    data.setDirty();
                }
                return;
            }
            carried.written = true;
        }
        if (retire(layout.legacy())) {
            CARRIED_OVER.remove(data);
        } else if (!storageOpen) {
            BCLog.logger.warn("[saved data] The pre-26.1 file {} could not be moved aside before the server stopped. "
                    + "Its data is in {} now: move or delete the old file by hand, or it is read again if that file "
                    + "is ever empty at a launch", layout.legacy(), layout.current());
            CARRIED_OVER.remove(data);
        }
    }

    /** The {@code data} payload of the 26.1+ file {@code file} as it is on disk, or null when there is none. */
    private static @Nullable Tag readPayload(Path file) {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            return NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()).get("data");
        } catch (Exception e) {
            return null;
        }
    }

    /** How {@link #retire} moves a file; a test makes it fail. */
    interface Mover {
        void move(Path from, Path to) throws IOException;
    }

    static volatile Mover mover = (from, to) -> Files.move(from, to);

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
            mover.move(legacyFile, target);
            BCLog.logger.info("[saved data] The pre-26.1 file {} was carried over; kept as {}", legacyFile,
                    target.getFileName());
            return true;
        } catch (IOException e) {
            BCLog.logger.warn("[saved data] Could not move the carried-over pre-26.1 file " + legacyFile
                    + " aside; it is tried again after the next save", e);
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
     * save and the old file is retired once that file is seen holding it (it stays on disk, renamed, so the original
     * is never lost).
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
