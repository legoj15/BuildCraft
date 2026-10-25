/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.filler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import buildcraft.builders.snapshot.Template;

/**
 * What a client's Filler keeps between frames so its preview does not walk its box every frame: the cells still to
 * fill, and the order to draw them in.
 * <p>
 * The cell list is rebuilt when the Filler builds a new {@code BuildingInfo} (a changed pattern, parameter, invert or
 * box always does — the same object identity is what makes a stale preview impossible), and every
 * {@link #RESCAN_TICKS} ticks otherwise, because a block placed or broken inside the box (by a player, a pipe, a
 * robot) announces itself to no client event. The draw order — far to near, for the translucent blend — is redone only
 * when the cells change or the camera has moved a few blocks.
 */
public final class FillPreviewCache {
    /** How long a scan of the box is trusted, in game ticks. */
    public static final int RESCAN_TICKS = 10;
    /** How long the Filler must have had nothing to place or break before its preview shows, so the gaps between
     *  tasks of a running build do not flicker it. */
    public static final int IDLE_GRACE_TICKS = 20;
    /** Most cubes drawn. A cube is 24 immediate-mode vertices; past this the nearest are kept and the far ones left
     *  undrawn rather than let a huge box cost every frame. */
    public static final int MAX_CUBES = 4096;

    private static final double RESORT_DISTANCE_SQR = 2.0 * 2.0;

    private final int maxCubes;
    @Nullable
    private Template.BuildingInfo scannedFor;
    private long scannedAt;
    private List<BlockPos> cells = List.of();
    @Nullable
    private Vec3 sortedFrom;
    private List<BlockPos> drawOrder = List.of();
    private long lastBusy = Long.MIN_VALUE / 2;

    public FillPreviewCache() {
        this(MAX_CUBES);
    }

    /** A cache drawing at most {@code maxCubes} cubes instead of {@link #MAX_CUBES}. */
    public FillPreviewCache(int maxCubes) {
        this.maxCubes = maxCubes;
    }

    /**
     * Notes whether the Filler has blocks in flight this frame.
     *
     * @return true once it has been quiet for {@link #IDLE_GRACE_TICKS}
     */
    public boolean isIdle(long gameTime, boolean workInFlight) {
        if (workInFlight || gameTime < lastBusy) {
            lastBusy = gameTime;
        }
        return gameTime - lastBusy >= IDLE_GRACE_TICKS;
    }

    /** The cubes to draw this frame, farthest first, at most {@link #MAX_CUBES} (or the constructor's) of the nearest. */
    public List<BlockPos> cellsToDraw(Level level, Template.BuildingInfo info, Vec3 camera) {
        long now = level.getGameTime();
        if (info != scannedFor || now < scannedAt || now - scannedAt >= RESCAN_TICKS) {
            List<BlockPos> scanned = FillPreview.collectFillable(level, info);
            scannedFor = info;
            scannedAt = now;
            if (!scanned.equals(cells)) {
                cells = scanned;
                sortedFrom = null;
            }
        }
        if (sortedFrom == null || sortedFrom.distanceToSqr(camera) > RESORT_DISTANCE_SQR) {
            drawOrder = farToNear(cells, camera, maxCubes);
            sortedFrom = camera;
        }
        return drawOrder;
    }

    /** Drops what was scanned, so the next frame starts from a fresh scan and nothing stays held meanwhile. */
    public void clear() {
        scannedFor = null;
        cells = List.of();
        sortedFrom = null;
        drawOrder = List.of();
    }

    /** The nearest {@code maxCubes} cells, farthest first. Sorted on packed (distance bits, index) keys: positive
     *  floats order like their bit patterns, and a primitive sort allocates nothing per comparison. */
    private static List<BlockPos> farToNear(List<BlockPos> cells, Vec3 from, int maxCubes) {
        int count = cells.size();
        long[] keys = new long[count];
        for (int i = 0; i < count; i++) {
            BlockPos p = cells.get(i);
            double dx = p.getX() + 0.5 - from.x;
            double dy = p.getY() + 0.5 - from.y;
            double dz = p.getZ() + 0.5 - from.z;
            keys[i] = ((long) Float.floatToIntBits((float) (dx * dx + dy * dy + dz * dz)) << 32) | i;
        }
        Arrays.sort(keys);
        int keep = Math.min(count, maxCubes);
        List<BlockPos> ordered = new ArrayList<>(keep);
        for (int k = keep - 1; k >= 0; k--) {
            ordered.add(cells.get((int) keys[k]));
        }
        return ordered;
    }
}
