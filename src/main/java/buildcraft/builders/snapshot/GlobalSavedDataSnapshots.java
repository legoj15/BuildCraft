/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.snapshot;

import java.time.Duration;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import org.apache.commons.lang3.tuple.Pair;

import net.minecraft.world.level.Level;

import net.neoforged.fml.loading.FMLPaths;

import buildcraft.api.core.BCLog;
import buildcraft.api.core.InvalidInputDataException;
import buildcraft.lib.nbt.NbtSquisher;

public class GlobalSavedDataSnapshots {
    private static final String SNAPSHOT_FILE_EXTENSION = ".bcnbt";
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
    private final File snapshotsFile;

    private GlobalSavedDataSnapshots(Side side) {
        snapshotsFile = new File(
            FMLPaths.GAMEDIR.get().toFile(),
            "snapshots-" + side.name().toLowerCase(Locale.ROOT)
        );
        if (!snapshotsFile.exists()) {
            if (!snapshotsFile.mkdirs()) {
                throw new RuntimeException("Failed to make the directories required for snapshots: " + snapshotsFile);
            }
        } else if (!snapshotsFile.isDirectory()) {
            throw new IllegalStateException("The snapshots directory was not a directory: " + snapshotsFile);
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

    private Pair<Snapshot, File> readSnapshot(Snapshot.Key key) {
        String targetPrefix = key.toString();
        // addSnapshot names every file exactly <hash>.bcnbt, so try that before scanning the directory.
        File exact = new File(snapshotsFile, targetPrefix + SNAPSHOT_FILE_EXTENSION);
        if (exact.isFile()) {
            Snapshot snapshot = readSnapshotFile(exact);
            if (snapshot != null && Objects.equals(snapshot.key, key)) {
                return Pair.of(snapshot, exact);
            }
        }
        File[] files = snapshotsFile.listFiles();
        if (files == null) {
            LOGGER.warn("readSnapshot: listFiles() returned null for dir={}", snapshotsFile);
            return null;
        }
        for (File snapshotFile : files) {
            if (snapshotFile.equals(exact)) continue;
            if (snapshotFile.getName().startsWith(targetPrefix) &&
                snapshotFile.getName().endsWith(SNAPSHOT_FILE_EXTENSION)) {
                Snapshot snapshot = readSnapshotFile(snapshotFile);
                if (snapshot != null && Objects.equals(snapshot.key, key)) {
                    return Pair.of(snapshot, snapshotFile);
                }
            }
        }
        LOGGER.debug("readSnapshot: no file in {} holds key {}", snapshotsFile, targetPrefix);
        return null;
    }

    @Nullable
    private static Snapshot readSnapshotFile(File snapshotFile) {
        try (FileInputStream fileInputStream = new FileInputStream(snapshotFile)) {
            return Snapshot.readFromNBT(NbtSquisher.expand(fileInputStream));
        } catch (InvalidInputDataException e) {
            LOGGER.warn("Skipping corrupted snapshot file {}: {}", snapshotFile, e.getMessage());
        } catch (IOException e) {
            LOGGER.warn("Failed to read the snapshot {}", snapshotFile, e);
        } catch (RuntimeException e) {
            LOGGER.error("Unexpected error reading the snapshot {}", snapshotFile, e);
        }
        return null;
    }

    /** The index's reader: the key stored in {@code snapshotFile}, or null if the file is unreadable or is not
     *  named after the key it holds. */
    @Nullable
    private static Snapshot.Key readListedKey(File snapshotFile) {
        Snapshot snapshot = readSnapshotFile(snapshotFile);
        if (snapshot == null || snapshot.key == null) return null;
        return snapshotFile.getName().startsWith(snapshot.key.toString()) ? snapshot.key : null;
    }

    public void addSnapshot(Snapshot snapshot) {
        File snapshotFile = new File(
            snapshotsFile,
            snapshot.key.toString() + SNAPSHOT_FILE_EXTENSION
        );
        if (!snapshotFile.exists()) {
            try (FileOutputStream fileOutputStream = new FileOutputStream(snapshotFile)) {
                NbtSquisher.squishVanilla(Snapshot.writeToNBT(snapshot), fileOutputStream);
            } catch (IOException e) {
                BCLog.logger.error("Failed to write the snapshot file: " + snapshotFile, e);
            }
            if (snapshotFile.isFile()) {
                index.put(snapshotFile.getName(), snapshot.key, snapshotFile.lastModified(), snapshotFile.length());
            }
        }
        snapshotsCache.invalidate(snapshot.key);
    }

    public void removeSnapshot(Snapshot.Key key) {
        Optional.ofNullable(readSnapshot(key)).map(Pair::getRight).ifPresent(snapshotFile -> {
            if (!snapshotFile.delete()) {
                BCLog.logger.error("Failed to delete the snapshot file: " + snapshotFile);
            } else {
                index.remove(snapshotFile.getName());
            }
            snapshotsCache.invalidate(key);
        });
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
     */
    public List<Snapshot.Key> getList() {
        if ((!rescannedOnce || System.nanoTime() - lastRescanNanos >= RESCAN_INTERVAL_NANOS)
                && rescanQueued.compareAndSet(false, true)) {
            RESCAN_EXECUTOR.execute(() -> {
                try {
                    index.rescan(snapshotsFile, SNAPSHOT_FILE_EXTENSION, GlobalSavedDataSnapshots::readListedKey);
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

    /** Whether {@code key} is in the {@link #getList() list}. Never touches disk. */
    public boolean isListed(@Nullable Snapshot.Key key) {
        return key != null && index.contains(key);
    }
}
