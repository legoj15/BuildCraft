/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.snapshot;

import java.time.Duration;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import org.apache.commons.lang3.tuple.Pair;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

import net.neoforged.fml.loading.FMLPaths;

import buildcraft.api.core.BCLog;
import buildcraft.lib.misc.HashUtil;
import buildcraft.lib.nbt.NbtSquisher;

public class GlobalSavedDataSnapshots {
    private static final String SNAPSHOT_FILE_EXTENSION = ".bcnbt";
    /** Ends the in-progress copy of a save ({@code <file name>.<random>.tmp}); never listed or read as a snapshot. */
    private static final String TEMP_FILE_EXTENSION = ".tmp";
    /** A temp file younger than this is never swept: a save writes one for seconds, and a second game instance
     *  sharing the directory (two servers, a server and a client over one game dir) must not lose its live one. */
    static final Duration ORPHANED_TEMP_MIN_AGE = Duration.ofMinutes(5);
    /** Bytes of the header hash kept in a named snapshot's file name; enough to tell one build's names apart. */
    private static final int HEADER_DIGEST_BYTES = 8;
    /** How often the background scan looks for files added or removed outside the game. */
    private static final long RESCAN_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(5);
    /** One daemon thread shared by both sides: after the first, scans only stat files that are already indexed. */
    private static final Executor RESCAN_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "BuildCraft snapshot index");
        t.setDaemon(true);
        return t;
    });

    public enum Side {
        CLIENT, SERVER;
    }

    private static final Map<Side, GlobalSavedDataSnapshots> INSTANCES = new EnumMap<>(Side.class);
    private final LoadingCache<Snapshot.Key, Optional<Snapshot>> snapshotsCache = CacheBuilder.newBuilder()
        .expireAfterAccess(Duration.ofMinutes(10))
        .build(CacheLoader.from(key -> Optional.ofNullable(readSnapshot(key)).map(Pair::getLeft)));
    /** The library list: built and refreshed on the scan thread (see {@link #getList()}), updated directly by the
     *  saves and deletes made through this class. */
    private final SnapshotIndex index = new SnapshotIndex();
    private final AtomicBoolean rescanQueued = new AtomicBoolean();
    private volatile long lastRescanNanos;
    private volatile boolean rescannedOnce;
    /** What a file looked like (size and modification time) when this store wrote it, or when a save last
     *  decompressed it and found it readable. A repeat save of a file whose stats still match is a stat check
     *  only; any other file (left by an older version, a crash, a hand copy, or changed outside the game since
     *  it was trusted) is decompressed once before it is trusted again. Reads through {@link #getSnapshot} do
     *  not add to this: only a save's own check does. */
    private final Map<String, FileStamp> intactFiles = new ConcurrentHashMap<>();
    /** Files whose bytes could not be read and that have already logged a warning for it (see
     *  {@link #readSnapshotFile}); a file leaves once it reads. Written by the scan thread and lookups alike. */
    private final Set<String> unreadableWarned = ConcurrentHashMap.newKeySet();

    /** A file's size and modification time: the cheap fingerprint the trust in {@link #intactFiles} is keyed on,
     *  the same one {@link SnapshotIndex} uses to notice a changed file. */
    private record FileStamp(long length, long modified) {
        static FileStamp of(File file) {
            return new FileStamp(file.length(), file.lastModified());
        }
    }
    private final File snapshotsFile;
    /** Whether the scan re-keys a file whose stored key no longer matches its content (see {@link #rekeyed}). */
    private final boolean healKeys;

    private GlobalSavedDataSnapshots(Side side) {
        this(new File(FMLPaths.GAMEDIR.get().toFile(), "snapshots-" + side.name().toLowerCase(Locale.ROOT)),
            side == Side.CLIENT);
    }

    /** A store over {@code directory} that leaves every file under the key it holds. */
    GlobalSavedDataSnapshots(File directory) {
        this(directory, false);
    }

    /**
     * A store over {@code directory}; the game uses one per side under the game directory.
     *
     * @param healKeys whether the scan that builds the list re-keys the files it reads (the client's library does:
     *                 the server re-keys every upload from its content and refuses one whose key differs from the
     *                 key it asked for; the server's own files are what blueprint items point at, so they never move)
     */
    GlobalSavedDataSnapshots(File directory, boolean healKeys) {
        this.healKeys = healKeys;
        snapshotsFile = directory;
        if (!snapshotsFile.exists()) {
            if (!snapshotsFile.mkdirs()) {
                throw new RuntimeException("Failed to make the directories required for snapshots: " + snapshotsFile);
            }
        } else if (!snapshotsFile.isDirectory()) {
            throw new IllegalStateException("The snapshots directory was not a directory: " + snapshotsFile);
        }
        deleteOrphanedTempFiles();
    }

    /** Delete the in-progress copies a crash mid-save left behind (a live save always removes its own). Only those
     *  older than {@link #ORPHANED_TEMP_MIN_AGE}: a younger one may belong to a save running in another instance
     *  that shares this directory. */
    private void deleteOrphanedTempFiles() {
        File[] files = snapshotsFile.listFiles();
        if (files == null) return;
        long oldestKept = System.currentTimeMillis() - ORPHANED_TEMP_MIN_AGE.toMillis();
        for (File file : files) {
            String name = file.getName();
            if (name.endsWith(TEMP_FILE_EXTENSION) && name.contains(SNAPSHOT_FILE_EXTENSION + ".")
                    && file.isFile() && file.lastModified() <= oldestKept && !file.delete()) {
                LOGGER.warn("Failed to delete the leftover snapshot temp file {}", file);
            }
        }
    }

    public static void reInit(Side side) {
        INSTANCES.put(side, new GlobalSavedDataSnapshots(side));
    }

    public static GlobalSavedDataSnapshots get(Side side) {
        if (!INSTANCES.containsKey(side)) {
            INSTANCES.put(side, new GlobalSavedDataSnapshots(side));
        }
        return INSTANCES.get(side);
    }

    public static GlobalSavedDataSnapshots get(Level world) {
        return get(world.isClientSide() ? Side.CLIENT : Side.SERVER);
    }

    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger("BCSavedSnapshots");

    /**
     * The file a snapshot is saved to: {@code <hash>.bcnbt} for bare content, {@code <hash>-<name digest>.bcnbt} when
     * the key carries a header, so one build kept under several names (the Electronic Library) gets one file per
     * name. Every name starts with the content hash, which is what {@link #readSnapshot} and the index rely on.
     */
    static String fileName(Snapshot.Key key) {
        String name = key.toString();
        if (key.header != null) {
            byte[] digest = HashUtil.computeHash(key.header.serializeNBT());
            name += "-" + HashUtil.convertHashToString(Arrays.copyOf(digest, HEADER_DIGEST_BYTES));
        }
        return name + SNAPSHOT_FILE_EXTENSION;
    }

    /** Whether a file holding {@code stored} answers a lookup for {@code wanted}: the same key, or (unless
     *  {@code exact}) the same content when the lookup names no header (blueprint items look content up by hash). */
    private static boolean answers(Snapshot.Key stored, Snapshot.Key wanted, boolean exact) {
        return Objects.equals(stored, wanted)
            || !exact && stored != null && wanted.header == null && Arrays.equals(stored.hash, wanted.hash);
    }

    private Pair<Snapshot, File> readSnapshot(Snapshot.Key key) {
        return readSnapshot(key, false);
    }

    /**
     * The first file answering {@code key}; with {@code exact}, only a file holding that very key (deletes).
     *
     * <p>Decompressing a file is the cost here, and one build kept under several names puts several files under one
     * hash, so a file the {@link SnapshotIndex} already knows holds something else (same size and time as when it
     * was read) is skipped rather than read again to be rejected. A file the index has not seen, or that changed
     * since, is read, and what it held goes into the index: the server never runs the library scan that fills it,
     * so after a restart the first miss reads a build's other names once, and every later miss (and the sweep that
     * ends a delete) reads none of them again while they stay unchanged.
     */
    private Pair<Snapshot, File> readSnapshot(Snapshot.Key key, boolean exact) {
        String targetPrefix = key.toString();
        Predicate<Snapshot.Key> answering = stored -> answers(stored, key, exact);
        // Try the file addSnapshot would write before scanning for older or hand-copied ones (<hash>*.bcnbt).
        File exactFile = new File(snapshotsFile, fileName(key));
        if (exactFile.isFile() && !knownNotToAnswer(exactFile, answering)) {
            Pair<Snapshot, File> found = matching(exactFile, key, exact);
            if (found != null) return found;
        }
        File[] files = snapshotsFile.listFiles();
        if (files == null) {
            LOGGER.warn("readSnapshot: listFiles() returned null for dir={}", snapshotsFile);
            return null;
        }
        for (File snapshotFile : files) {
            if (snapshotFile.equals(exactFile)) continue;
            if (snapshotFile.getName().startsWith(targetPrefix) &&
                snapshotFile.getName().endsWith(SNAPSHOT_FILE_EXTENSION) &&
                snapshotFile.isFile() &&
                !knownNotToAnswer(snapshotFile, answering)) {
                Pair<Snapshot, File> found = matching(snapshotFile, key, exact);
                if (found != null) return found;
            }
        }
        LOGGER.debug("readSnapshot: no file in {} holds key {}", snapshotsFile, targetPrefix);
        return null;
    }

    /** Whether the index already knows {@code snapshotFile}, as it is on disk now, holds nothing {@code answering}
     *  accepts, so it need not be decompressed to be turned down. */
    private boolean knownNotToAnswer(File snapshotFile, Predicate<Snapshot.Key> answering) {
        return index.rulesOut(snapshotFile.getName(), snapshotFile.lastModified(), snapshotFile.length(), answering);
    }

    /** The snapshot in {@code snapshotFile} if it answers {@code key}; a content-only lookup gets it without its
     *  header, so anything derived from it (a Replacer copy) carries no stale name. What the file held is recorded in
     *  the index either way (unless it could not be read at all), so the next lookup that it cannot answer skips it. */
    @Nullable
    private Pair<Snapshot, File> matching(File snapshotFile, Snapshot.Key key, boolean exact) {
        // Stat before reading: a file replaced between the two is recorded with a stamp it no longer has, so the next
        // lookup reads it again instead of trusting what this read saw.
        FileStamp stamp = FileStamp.of(snapshotFile);
        FileRead read = readSnapshotFile(snapshotFile);
        if (read.unreadable() == null) {
            index.put(snapshotFile.getName(), listedKey(snapshotFile, read.snapshot(), false), stamp.modified(), stamp.length());
        }
        Snapshot snapshot = read.snapshot();
        if (snapshot == null || !answers(snapshot.key, key, exact)) return null;
        if (key.header == null && snapshot.key.header != null) {
            snapshot.key = new Snapshot.Key(snapshot.key, (Snapshot.Header) null);
        }
        return Pair.of(snapshot, snapshotFile);
    }

    /** What reading one file found: its snapshot, or null when its bytes are not one (corrupt: reading them again
     *  gives the same answer until the file changes); or, in {@code unreadable}, why its bytes could not be read at
     *  all (another program holds the file, or it was just deleted), which may pass, so nothing is concluded. */
    private record FileRead(@Nullable Snapshot snapshot, @Nullable IOException unreadable) {}

    private FileRead readSnapshotFile(File snapshotFile) {
        // Read the bytes first, then decode them, so a failure says which it was: vanilla's NBT reader reports an I/O
        // error inside a tag as an unchecked exception, indistinguishable from bad data once the two are mixed.
        byte[] bytes;
        String name = snapshotFile.getName();
        try {
            bytes = Files.readAllBytes(snapshotFile.toPath());
        } catch (IOException e) {
            // Retried on every scan (about every 5 s while the library is open) and every lookup, so a file another
            // program keeps open warns once, not each time; it warns again only after it has once been read.
            if (unreadableWarned.add(name)) {
                LOGGER.warn("Failed to read the snapshot {}; retrying quietly until it can be read", snapshotFile, e);
            } else {
                LOGGER.debug("Still cannot read the snapshot {}: {}", snapshotFile, e.toString());
            }
            return new FileRead(null, e);
        }
        unreadableWarned.remove(name);
        try {
            // Bounded by the read ceiling (LibraryUploadBuffer.Limits.readCeiling), NOT this server's upload config:
            // the operator lowering the cap later must not orphan every blueprint file already stored. The file is
            // compressed, so its size on disk says nothing about its size in memory, and a file planted outside the
            // game could otherwise name gigabytes. Oversized or corrupt bytes are both just an unreadable file.
            buildcraft.builders.container.LibraryUploadBuffer.Limits limits =
                buildcraft.builders.container.LibraryUploadBuffer.Limits.readCeiling();
            return new FileRead(Snapshot.readFromNBT(
                NbtSquisher.expand(new ByteArrayInputStream(bytes), limits.maxExpandedBytes()),
                limits.maxCells()), null);
        } catch (IOException e) { // InvalidInputDataException included: the bytes decode, but not to a snapshot
            LOGGER.warn("Skipping corrupted snapshot file {}: {}", snapshotFile, e.getMessage());
        } catch (RuntimeException e) {
            LOGGER.error("Unexpected error reading the snapshot {}", snapshotFile, e);
        }
        return new FileRead(null, null);
    }

    /** The key {@code snapshot} (read from {@code snapshotFile}) is listed under, or null if there is none or the file
     *  is not named after it. */
    @Nullable
    private Snapshot.Key listedKey(File snapshotFile, @Nullable Snapshot snapshot, boolean heal) {
        if (snapshot == null || snapshot.key == null) return null;
        if (!snapshotFile.getName().startsWith(snapshot.key.toString())) return null;
        return heal ? rekeyed(snapshotFile, snapshot) : snapshot.key;
    }

    /**
     * The key {@code snapshotFile} is listed under, after moving it to the key its content now hashes to if that is
     * not the key it holds. A key is the hash of the snapshot as it serialises, and the server keys every upload
     * from the content it unpacks, refusing one whose hash differs from the key it asked for. A file can hold a key
     * the content no longer has for two reasons: an older build hashed a schematic's replaceable blocks in the order
     * of one JVM's identity hashes, and loading a schematic migrates it to the current rules (ignored properties,
     * required offsets), so a file saved before a rule changed deserialises to different NBT. Either way the server
     * would refuse it for as long as the stored key stayed.
     * <p>
     * A file is moved only when it {@linkplain Snapshot#readFully() read fully}: one that names a block (or a property, or
     * value) this game lacks loads with a default in its place, and its content hashes differently for that reason
     * alone, so it is left as it was rather than overwritten with the poorer copy.
     * <p>
     * The name and the rest of the header are kept. A failed write or delete leaves the file as it was, listed under
     * the key it holds: when the old file cannot be deleted (another program holds it open), the copy just written
     * under the new key is removed again, since a second file whose content answers the new key would keep that key
     * listed after the player deleted it, and bring it back at the next start. The next start tries again.
     */
    private Snapshot.Key rekeyed(File snapshotFile, Snapshot snapshot) {
        Snapshot.Key stored = snapshot.key;
        try {
            snapshot.computeKey();
        } catch (RuntimeException e) {
            LOGGER.warn("Could not recompute the key of the snapshot {}", snapshotFile, e);
            snapshot.key = stored;
            return stored;
        }
        Snapshot.Key current = snapshot.key;
        if (Arrays.equals(stored.hash, current.hash)) return stored;
        if (!snapshot.readFully()) {
            // The copy in memory has a default (air, mostly) where the file names something this game lacks, such as
            // a block of a mod that is not installed: writing it back would destroy the original. It keeps its key;
            // a start with that mod present re-keys it.
            LOGGER.debug("Leaving the snapshot {} under its key {}: it names content this game lacks",
                snapshotFile.getName(), stored);
            snapshot.key = stored;
            return stored;
        }

        // A file already holding the new key (the same item downloaded again) is merged into, never rolled back.
        boolean heldAlready = alreadyStored(current);
        addSnapshot(snapshot);
        if (!alreadyStored(current)) {
            LOGGER.warn("Could not re-key the snapshot {} to {}", snapshotFile, current);
            return stored;
        }
        if (!snapshotFile.delete()) {
            // Left listed under its old key; a later scan leaves the unchanged file alone rather than trying to move
            // it again every few seconds.
            LOGGER.warn("Failed to delete the snapshot file {}, so it keeps the key {}", snapshotFile, stored);
            if (!heldAlready) {
                File copy = new File(snapshotsFile, fileName(current));
                if (copy.delete()) {
                    intactFiles.remove(copy.getName());
                    index.remove(copy.getName());
                } else {
                    LOGGER.warn("Failed to delete the re-keyed copy {}", copy);
                }
                invalidate(current);
            }
            return stored;
        }
        LOGGER.info("Re-keyed the snapshot {} from {} to {}", snapshotFile.getName(), stored, current);
        intactFiles.remove(snapshotFile.getName());
        index.remove(snapshotFile.getName());
        invalidate(stored);
        return current;
    }

    /** The index's reader: {@link #listedKey} of what {@code snapshotFile} holds; a file that cannot be read right now
     *  throws, so the index leaves it out and the next scan tries again. Any other failure on this one file (a
     *  re-key that cannot serialise it, say) is logged and the file is left unlisted, like a corrupt one: it is not
     *  tried again until it changes, and it never stops the scan from listing the rest. Scan path only: saves made
     *  through {@link #addSnapshot} still throw to their caller. */
    @Nullable
    private Snapshot.Key readListedKey(File snapshotFile) {
        FileRead read = readSnapshotFile(snapshotFile);
        if (read.unreadable() != null) throw new UncheckedIOException(read.unreadable());
        try {
            return listedKey(snapshotFile, read.snapshot(), healKeys);
        } catch (UncheckedIOException e) {
            throw e;
        } catch (RuntimeException e) {
            LOGGER.error("Unexpected error listing the snapshot {}; leaving it out of the library", snapshotFile, e);
            return null;
        }
    }

    public void addSnapshot(Snapshot snapshot) {
        if (!alreadyStored(snapshot.key)) {
            File snapshotFile = new File(snapshotsFile, fileName(snapshot.key));
            // Serialise before touching the disk: a snapshot that cannot be written leaves nothing behind.
            CompoundTag nbt = Snapshot.writeToNBT(snapshot);
            if (writeAtomically(nbt, snapshotFile)) {
                FileStamp stamp = FileStamp.of(snapshotFile);
                intactFiles.put(snapshotFile.getName(), stamp);
                index.put(snapshotFile.getName(), snapshot.key, stamp.modified(), stamp.length());
            }
        }
        invalidate(snapshot.key);
    }

    /**
     * Write {@code nbt} to a temp file beside {@code target}, then move it over {@code target} in one step, so a
     * crash or a full disk mid-write can never leave a half-written {@code .bcnbt} for later saves to trust. A
     * failed write deletes its temp file and leaves {@code target} as it was.
     *
     * @return whether {@code target} now holds the snapshot
     */
    private boolean writeAtomically(CompoundTag nbt, File target) {
        Path temp = null;
        try {
            temp = Files.createTempFile(snapshotsFile.toPath(), target.getName() + ".", TEMP_FILE_EXTENSION);
            try (var out = Files.newOutputStream(temp)) {
                NbtSquisher.squishVanilla(nbt, out);
            }
            try {
                Files.move(temp, target.toPath(), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            BCLog.logger.error("Failed to write the snapshot file: " + target, e);
            return false;
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException e) {
                    LOGGER.warn("Failed to delete the snapshot temp file {}", temp, e);
                }
            }
        }
    }

    /**
     * Whether saving {@code key} would add nothing: the file {@link #fileName} gives it exists and holds a readable
     * snapshot answering {@code key} (not another build copied in under its name), or (for a named key) an older {@code <hash>.bcnbt} from before names split files holds it. A
     * {@code <hash>.bcnbt} holding a name also answers a content-only save, since a content-only lookup finds it.
     * <p>
     * A file this store wrote (or an earlier save already decompressed) is trusted while its size and modification
     * time are unchanged, so a save never decompresses a large snapshot twice. Any other existing file is
     * decompressed once first: an empty or truncated one (left by a crash under the old in-place write, or by
     * something outside the game replacing a file this store had trusted) is not "stored" and is overwritten by
     * the save.
     * <p>
     * The old {@code <hash>.bcnbt} is trusted through the {@link SnapshotIndex} (which records exactly which key a
     * read found there), not through {@link #intactFiles}: that name is also the content-only file name, so a stamp on
     * it vouches only that it answers the content, not which name it holds.
     */
    private boolean alreadyStored(Snapshot.Key key) {
        File stored = new File(snapshotsFile, fileName(key));
        if (stored.isFile() && isIntact(stored, key)) return true;
        if (key.header == null) return false;
        File legacy = new File(snapshotsFile, key + SNAPSHOT_FILE_EXTENSION);
        if (!legacy.isFile()) return false;
        FileStamp stamp = FileStamp.of(legacy);
        if (index.knownToHold(legacy.getName(), stamp.modified(), stamp.length(), key)) return true;
        if (knownNotToAnswer(legacy, held -> answers(held, key, true))) return false;
        return matching(legacy, key, true) != null;
    }

    /** Whether {@code snapshotFile} (the file {@link #fileName} gives {@code key}) holds a readable snapshot that
     *  answers {@code key}: readable is not enough, since a valid file of another build under this name (a hand copy,
     *  a sync tool) would otherwise be trusted and the save would write nothing. */
    private boolean isIntact(File snapshotFile, Snapshot.Key key) {
        // Stat before reading: a file replaced between the two leaves a stamp that no longer matches, so the next
        // save verifies it again instead of trusting content it never saw. The stamp is keyed by name, and only a
        // file that answered this name's key is ever stamped, so a matching stamp vouches for the key too.
        FileStamp stamp = FileStamp.of(snapshotFile);
        if (stamp.equals(intactFiles.get(snapshotFile.getName()))) return true;
        Snapshot held = stamp.length() == 0 ? null : readSnapshotFile(snapshotFile).snapshot();
        if (held == null || !answers(held.key, key, false)) {
            intactFiles.remove(snapshotFile.getName());
            return false;
        }
        intactFiles.put(snapshotFile.getName(), stamp);
        return true;
    }

    /** Delete every file holding exactly {@code key} (an old-style and a new-style file can both hold one entry);
     *  other names of the same build stay, even when {@code key} is content-only. */
    public void removeSnapshot(Snapshot.Key key) {
        Pair<Snapshot, File> found;
        while ((found = readSnapshot(key, true)) != null) {
            File snapshotFile = found.getRight();
            if (!snapshotFile.delete()) {
                BCLog.logger.error("Failed to delete the snapshot file: " + snapshotFile);
                break;
            }
            intactFiles.remove(snapshotFile.getName());
            index.remove(snapshotFile.getName());
        }
        invalidate(key);
    }

    /** Drop {@code key} from the cache, and the content-only lookup that a file holding it also answers. */
    private void invalidate(Snapshot.Key key) {
        snapshotsCache.invalidate(key);
        snapshotsCache.invalidate(new Snapshot.Key(key, (Snapshot.Header) null));
    }

    @Nullable
    public Snapshot getSnapshot(@Nullable Snapshot.Key key) {
        if (key == null) return null;
        return snapshotsCache.getUnchecked(key).orElse(null);
    }

    /**
     * The stored snapshots, sorted by name then newest first. Never touches disk, so it is safe to call every frame:
     * the list comes from a cached index that saves and deletes update directly, and that a background thread
     * re-checks against the directory at most every {@link #RESCAN_INTERVAL_NANOS} (to notice files copied in or
     * deleted outside the game). The very first call returns an empty list; the first scan lands a frame or so later.
     * On the client's store that scan also moves a file whose key no longer matches its content to the key it now
     * hashes to (see {@link #rekeyed}), so a key listed before a scan can be gone after it.
     */
    public List<Snapshot.Key> getList() {
        if ((!rescannedOnce || System.nanoTime() - lastRescanNanos >= RESCAN_INTERVAL_NANOS)
                && rescanQueued.compareAndSet(false, true)) {
            RESCAN_EXECUTOR.execute(() -> {
                try {
                    scanNow();
                } catch (RuntimeException e) {
                    LOGGER.error("Failed to scan the snapshot directory {}", snapshotsFile, e);
                } finally {
                    lastRescanNanos = System.nanoTime();
                    rescannedOnce = true;
                    rescanQueued.set(false);
                }
            });
        }
        return index.list();
    }

    /** Bring the index in line with the directory on this thread: what the background scan does. */
    void scanNow() {
        index.rescan(snapshotsFile, SNAPSHOT_FILE_EXTENSION, this::readListedKey);
    }

    /** Whether {@code key} is in the {@link #getList() list}. Never touches disk. */
    public boolean isListed(@Nullable Snapshot.Key key) {
        return key != null && index.contains(key);
    }
}
