/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.client;

import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import buildcraft.core.marker.VolumeCache;
import buildcraft.core.marker.VolumeConnection;
import buildcraft.core.marker.VolumeSubCache;
import buildcraft.core.marker.volume.ClientVolumeBoxes;
import buildcraft.core.marker.volume.VolumeBox;
import buildcraft.lib.misc.BlockUtil;

/**
 * The line under the crosshair that tells how far the player stands from one volume marker, started by right-clicking
 * that marker with a marker wrench ({@code MessageMarkerTrack}). It answers "am I in reach of it, and in line with
 * it?", for that one marker only (other markers of its connection, a box's missing corner and a marker in between
 * are the laser previews' business): the gap shown is what {@code markerMaxDistance} limits, it is coloured by how close that is to the
 * limit, and it says so when the player is not in line with the marker along an axis its connection has left free.
 * <p>
 * The limit comes with the start message, not from the local config: it is the server's (COMMON) value and is never
 * synced to a client. The readout is worked out once a client tick; {@link MarkerLengthIndicator#render} draws it.
 * Tracking ends when the marker is gone, when something connects to it, when the player clicks the air with a marker
 * wrench and nothing marker-related is aimed ({@link #clear}), or when the level changes. A new start replaces any
 * tracking in progress.
 * <p>
 * Everything the server-reachable code touches ({@link #start}, {@link #clear}) uses common types only; the client
 * classes sit in {@link #onClientTick}.
 */
public final class MarkerDistanceTracker {
    /** How a gap sits against the connection limit: markers connect across gaps of 1 to the limit inclusive. */
    public enum Reach {
        /** Short of the limit. */
        OK,
        /** Exactly the limit: the farthest a marker can still connect. */
        AT_LIMIT,
        /** Past the limit. */
        TOO_FAR
    }

    /**
     * @param distance the largest per-axis gap between the marker and the player's block
     * @param reach how that gap sits against the limit
     * @param aligned whether the player stands on a line through the marker along an axis its connection has not
     *            used. A marker strictly between the two on that line still blocks the link; that is not checked
     */
    public record Readout(int distance, Reach reach, boolean aligned) {}

    private static BlockPos trackedPos;
    private static Level trackedLevel;
    private static int maxDistance;
    /** How many markers the tracked marker's connection held when tracking began (lowered if it shrinks). */
    private static int baseline;
    private static Component label;

    private MarkerDistanceTracker() {}

    /**
     * Classifies where {@code player} stands against {@code marker}. The gap is the largest per-axis difference,
     * which is the offset {@code VolumeConnection.canCreateConnection} compares with the limit (1..max connect), not
     * the inclusive block count the laser label shows. The player is aligned when at most one axis differs and that
     * axis is not in {@code taken}; standing in the marker's own block counts as aligned.
     *
     * @param taken the axes the marker's connection already uses (none when the marker is unconnected)
     * @param max the connection limit
     */
    public static Readout classify(BlockPos marker, BlockPos player, Set<Axis> taken, int max) {
        int dx = Math.abs(player.getX() - marker.getX());
        int dy = Math.abs(player.getY() - marker.getY());
        int dz = Math.abs(player.getZ() - marker.getZ());
        int distance = Math.max(dx, Math.max(dy, dz));
        int differing = (dx != 0 ? 1 : 0) + (dy != 0 ? 1 : 0) + (dz != 0 ? 1 : 0);
        boolean aligned;
        if (differing == 0) {
            aligned = true;
        } else if (differing == 1) {
            aligned = !taken.contains(dx != 0 ? Axis.X : dy != 0 ? Axis.Y : Axis.Z);
        } else {
            aligned = false;
        }
        Reach reach = distance > max ? Reach.TOO_FAR : distance == max ? Reach.AT_LIMIT : Reach.OK;
        return new Readout(distance, reach, aligned);
    }

    /** The text for a readout: the gap (yellow at the limit, red past it with "(too far)"), then a red "not aligned"
     *  when a marker there could not connect for lack of a free line. */
    private static Component describe(Readout readout) {
        MutableComponent text = Component.translatable("buildcraft.marker.distance", readout.distance());
        switch (readout.reach()) {
            case AT_LIMIT -> text.withStyle(ChatFormatting.YELLOW);
            case TOO_FAR -> text.append(Component.translatable("buildcraft.marker.distance.too_far"))
                .withStyle(ChatFormatting.RED);
            default -> {
                // short of the limit: the colour the indicator draws with (white)
            }
        }
        if (readout.aligned()) {
            return text;
        }
        return Component.empty().append(text).append(" · ")
            .append(Component.translatable("buildcraft.marker.distance.not_aligned").withStyle(ChatFormatting.RED));
    }

    /**
     * Client: starts tracking {@code pos} in {@code level}, replacing any tracking in progress. Called from the
     * start message's handler, after the marker sync that the same click may have sent (it arrives first on the one
     * connection), so the baseline already holds a connection that click made.
     */
    public static void start(BlockPos pos, int max, Level level) {
        trackedPos = pos.immutable();
        trackedLevel = level;
        maxDistance = max;
        baseline = sizeOf(VolumeCache.INSTANCE.getSubCache(level).getConnection(pos));
        label = null;
    }

    /** Client: ends tracking. Safe to call when nothing is tracked. */
    public static void clear() {
        trackedPos = null;
        trackedLevel = null;
        label = null;
    }

    /** The finished line for this tick, or null when nothing is to be shown. */
    @Nullable
    static Component label() {
        return label;
    }

    /** How many markers the connection holds; a marker with none counts as the 1 it is. */
    private static int sizeOf(@Nullable VolumeConnection connection) {
        return connection == null ? 1 : connection.getMarkerPositions().size();
    }

    /** Called from ClientTickEvent.Post. */
    public static void onClientTick() {
        label = null;
        if (trackedPos == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        // A different level, or none, means a dimension change, a respawn or a disconnect: the tracked marker is gone.
        if (player == null || mc.level == null || mc.level != trackedLevel) {
            clear();
            return;
        }
        VolumeSubCache cache = VolumeCache.INSTANCE.getSubCache(mc.level);
        // The marker is gone once its chunk is here and the cache holds nothing at the spot. An unloaded chunk keeps
        // its entry (the cache stores unloaded markers), so a marker out of range is still tracked.
        if (BlockUtil.isChunkPresent(mc.level, trackedPos) && !cache.hasLoadedOrUnloadedMarker(trackedPos)) {
            clear();
            return;
        }
        VolumeConnection connection = cache.getConnection(trackedPos);
        int size = sizeOf(connection);
        if (size > baseline) {
            // Something was connected to the marker: that is what the readout was helping to do.
            clear();
            return;
        }
        baseline = size;

        // Off first person the crosshair is gone; a volume box under drag has the player's attention.
        if (!mc.options.getCameraType().isFirstPerson() || isDraggingBox(player)) {
            return;
        }
        Set<Axis> taken = connection == null ? Set.of() : connection.getConnectedAxis();
        label = describe(classify(trackedPos, player.blockPosition(), taken, maxDistance));
    }

    private static boolean isDraggingBox(Player player) {
        for (VolumeBox box : ClientVolumeBoxes.INSTANCE.volumeBoxes) {
            if (box.isEditingBy(player)) {
                return true;
            }
        }
        return false;
    }
}
