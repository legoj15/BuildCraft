/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team This Source Code Form is subject to the terms of the Mozilla
 * Public License, v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/
 */

package buildcraft.core.marker.volume;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import buildcraft.lib.misc.GameProfileUtil;
import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.lib.misc.data.Box;

import buildcraft.core.tile.TileMarkerVolume;

public class VolumeBox {
    /** The registered name of the Filler Planner addon in the {@code addonClass} of an old save's {@code addons} entry. */
    private static final String RETIRED_FILLER_PLANNER_ADDON = "buildcraftunofficial:filler_planner";

    public final Level world;
    public UUID id;
    public Box box;
    /** The player dragging a corner, or null. A drag ends when that player confirms or cancels it, or is no longer in
     *  this box's level (logged out, changed dimension): that cancels it (see LevelSavedDataVolumeBoxes#tick). */
    private UUID player = null;
    private BlockPos held = null;
    private double dist = 0;
    private BlockPos oldMin = null, oldMax = null;
    public final List<Lock> locks = new ArrayList<>();
    @Nullable
    private RetiredPlanner retiredPlanner = null;
    /**
     * True for a box lifted off volume markers with the Marker Wrench: a machine takes it the way it takes markers
     * (copies the area and removes the box) instead of locking it, so it is used once. A Quarry consumes it; a Filler
     * or Architect Table turns it into the box it owns (see {@link #owner}), as it does the markers themselves.
     */
    private boolean oneShot = false;
    /** The markers still backing a one-shot box that has not been confirmed yet; empty once they broke. */
    private final List<BlockPos> sourceMarkers = new ArrayList<>();
    /**
     * The machine this box belongs to, or null for a free box (one placed with the Volume Box item). A Filler or
     * Architect Table that takes over survival markers, or a box lifted off them, turns them into a box it owns: the
     * box lives exactly as long as the machine (removed with it, never by a wrench) and is the machine's editable
     * working area. Saved under {@code owner}; absent in saves from before ownership existed, which load as free
     * boxes, as they were.
     */
    @Nullable
    private Lock.Cause.CauseBlock owner = null;

    /**
     * What the retired Filler Planner addon kept for the Filler beside its box: the pattern statement (as
     * {@code FullStatement.writeToNbt} wrote it) and the invert flag, and the addon slot it sat in (carried only so the
     * save can write the entry back exactly as it read it).
     */
    public record RetiredPlanner(String slot, CompoundTag pattern, boolean inverted) {}

    public VolumeBox(Level world, BlockPos at) {
        if (world == null)
            throw new NullPointerException("world");
        this.world = world;
        id = UUID.randomUUID();
        box = new Box(at, at);
    }

    public VolumeBox(Level world, CompoundTag nbt) {
        if (world == null)
            throw new NullPointerException("world");
        this.world = world;

        id = UUID.fromString(NBTUtilBC.getString(nbt, "id", ""));
        box = new Box();
        box.initialize(NBTUtilBC.getCompound(nbt, "box"));

        player = nbt.contains("player") ? UUID.fromString(NBTUtilBC.getString(nbt, "player", "")) : null;

        if (nbt.contains("held")) {
            held = NBTUtilBC.readBlockPos(NBTUtilBC.getCompound(nbt, "held"));
        }
        dist = NBTUtilBC.getDouble(nbt, "dist", 0.0);

        if (nbt.contains("oldMin")) {
            oldMin = NBTUtilBC.readBlockPos(NBTUtilBC.getCompound(nbt, "oldMin"));
        }
        if (nbt.contains("oldMax")) {
            oldMax = NBTUtilBC.readBlockPos(NBTUtilBC.getCompound(nbt, "oldMax"));
        }
        // Saves from before a drag ended with its player's absence kept such a drag "paused" under oldPlayer. It is
        // a cancelled drag now: the old size comes back.
        if (nbt.contains("oldPlayer") && player == null) {
            cancelEditing();
        }

        // An "addons" list in an old save holds the Filler Planner, the only addon there ever was. The addon layer is
        // gone, but every Filler that was driven by it needs its pattern and invert flag: they stay with the box, for
        // each such Filler to copy when it loads (TileFiller.migrateOrphanedBoxOnce), and the world save
        // (writeToDiskNBT) keeps carrying them, so a save before a Filler's chunk loads does not lose them.
        if (nbt.contains("addons")) {
            ListTag addonsList = NBTUtilBC.getList(nbt, "addons", Tag.TAG_COMPOUND);
            for (int i = 0; i < addonsList.size(); i++) {
                CompoundTag addonTag = NBTUtilBC.getCompound(addonsList, i);
                if (RETIRED_FILLER_PLANNER_ADDON.equals(NBTUtilBC.getString(addonTag, "addonClass", ""))) {
                    CompoundTag data = NBTUtilBC.getCompound(addonTag, "addonData");
                    retiredPlanner = new RetiredPlanner(
                            NBTUtilBC.getString(addonTag, "slot", ""),
                            NBTUtilBC.getCompound(data, "patternStatement"),
                            NBTUtilBC.getBoolean(data, "inverted", false));
                }
            }
        }

        oneShot = NBTUtilBC.getBoolean(nbt, "oneShot", false);
        if (nbt.contains("sourceMarkers")) {
            ListTag markerList = NBTUtilBC.getList(nbt, "sourceMarkers", Tag.TAG_COMPOUND);
            for (int i = 0; i < markerList.size(); i++) {
                sourceMarkers.add(NBTUtilBC.readBlockPos(NBTUtilBC.getCompound(markerList, i)));
            }
        }

        if (nbt.contains("owner")) {
            owner = new Lock.Cause.CauseBlock();
            owner.readFromNBT(NBTUtilBC.getCompound(nbt, "owner"));
        }

        if (nbt.contains("locks")) {
            ListTag locksList = NBTUtilBC.getList(nbt, "locks", Tag.TAG_COMPOUND);
            for (int i = 0; i < locksList.size(); i++) {
                CompoundTag lockTag = NBTUtilBC.getCompound(locksList, i);
                Lock lock = new Lock();
                lock.readFromNBT(lockTag);
                locks.add(lock);
            }
        }
    }

    /**
     * The pattern and invert flag the Filler Planner of an old save held, or null when there were none. They belong to
     * the box, not to one Filler: in the old build every Filler backed onto the box built the planner's pattern, so
     * every one of them reads this and none consumes it.
     */
    @Nullable
    public RetiredPlanner retiredPlanner() {
        return retiredPlanner;
    }

    /** A box lifted off the markers at {@code markers}, spanning {@code min}..{@code max}. */
    public static VolumeBox fromMarkers(Level world, BlockPos min, BlockPos max, Collection<BlockPos> markers) {
        VolumeBox volumeBox = new VolumeBox(world, min);
        volumeBox.box.extendToEncompass(max);
        volumeBox.oneShot = true;
        volumeBox.sourceMarkers.addAll(markers);
        return volumeBox;
    }

    public boolean isOneShot() {
        return oneShot;
    }

    /** True while markers still back this box: it has not been confirmed with a new size yet. */
    public boolean hasSourceMarkers() {
        return !sourceMarkers.isEmpty();
    }

    /** True while editing, when the box no longer spans what it spanned when editing began. */
    public boolean sizeChangedWhileEditing() {
        return oldMin != null && oldMax != null && !(box.min().equals(oldMin) && box.max().equals(oldMax));
    }

    /** True when the volume marker at {@code pos} still backs this box. */
    public boolean isBackedBy(BlockPos pos) {
        return sourceMarkers.contains(pos);
    }

    /**
     * Breaks the volume markers that still back this box, dropping each as an item, as a machine taking them does.
     * Returns how many were still there to break.
     */
    public int breakSourceMarkers() {
        // A marker placed or connected on the lifted area while the box was dragged joined the same connection: it goes
        // too. Markers of another volume the connection merged with lie outside that area and are left alone.
        Set<BlockPos> toBreak = new LinkedHashSet<>(sourceMarkers);
        Box lifted = new Box();
        if (oldMin != null && oldMax != null) {
            lifted.extendToEncompass(oldMin);
            lifted.extendToEncompass(oldMax);
        }
        for (BlockPos p : sourceMarkers) {
            if (lifted.isInitialized() && world.getBlockEntity(p) instanceof TileMarkerVolume marker
                && marker.getCurrentConnection() != null) {
                for (BlockPos joined : marker.getCurrentConnection().getMarkerPositions()) {
                    if (lifted.contains(joined)) {
                        toBreak.add(joined);
                    }
                }
            }
        }
        int broken = 0;
        for (BlockPos p : toBreak) {
            if (world.getBlockEntity(p) instanceof TileMarkerVolume) {
                Block.popResource(world, p, new ItemStack(world.getBlockState(p).getBlock()));
                world.destroyBlock(p, false);
                broken++;
            }
        }
        sourceMarkers.clear();
        return broken;
    }

    /** Whether a corner is being dragged: the box's size is not final. */
    public boolean isEditing() {
        return player != null;
    }

    /** Makes this box belong to the machine block at {@code pos} (see {@link #owner}). */
    public void setOwner(BlockPos pos, Block block) {
        owner = new Lock.Cause.CauseBlock(pos.immutable(), block);
    }

    /** The machine this box belongs to, or null for a free box. */
    @Nullable
    public Lock.Cause.CauseBlock getOwner() {
        return owner;
    }

    public boolean isOwnedBy(BlockPos pos) {
        return owner != null && owner.pos.equals(pos);
    }

    /**
     * Where the dragged corner goes when the player aims at {@code target} while {@code held} stays put: each axis is
     * kept within {@code cap} blocks of the held corner, the same reach a marker connection has, so a box can never be
     * dragged larger than markers could make it. An axis the box already spanned further than that when the drag
     * started ({@code startMin}..{@code startMax}) keeps that span as its limit instead, so grabbing a corner never
     * shrinks a box by itself. Axes are clamped on their own.
     */
    public static BlockPos clampDragTarget(BlockPos held, BlockPos target, int cap, BlockPos startMin,
                                           BlockPos startMax) {
        return new BlockPos(
            clampAxis(held.getX(), target.getX(), Math.max(cap, startMax.getX() - startMin.getX())),
            clampAxis(held.getY(), target.getY(), Math.max(cap, startMax.getY() - startMin.getY())),
            clampAxis(held.getZ(), target.getZ(), Math.max(cap, startMax.getZ() - startMin.getZ())));
    }

    private static int clampAxis(int held, int target, int maxSpan) {
        return Math.max(held - maxSpan, Math.min(held + maxSpan, target));
    }

    private void resetEditing() {
        oldMin = oldMax = null;
        held = null;
        dist = 0;
    }

    /** Ends the drag, putting the size from before it back (when it is known). */
    public void cancelEditing() {
        player = null;
        if (oldMin != null && oldMax != null) {
            box.reset();
            box.extendToEncompass(oldMin);
            box.extendToEncompass(oldMax);
        }
        resetEditing();
    }

    public void confirmEditing() {
        player = null;
        resetEditing();
    }

    public void setPlayer(Player player) {
        this.player = GameProfileUtil.getId(player.getGameProfile());
    }

    public boolean isEditingBy(Player player) {
        return player != null && Objects.equals(this.player, GameProfileUtil.getId(player.getGameProfile()));
    }

    @SuppressWarnings("WeakerAccess")
    public Player getPlayer(Level world) {
        return world.getPlayerByUUID(player);
    }

    public void setHeldDistOldMinOldMax(BlockPos held, double dist, BlockPos oldMin, BlockPos oldMax) {
        this.held = held;
        this.dist = dist;
        this.oldMin = oldMin;
        this.oldMax = oldMax;
    }

    @SuppressWarnings("WeakerAccess")
    public BlockPos getHeld() {
        return held;
    }

    @SuppressWarnings("WeakerAccess")
    public double getDist() {
        return dist;
    }

    /** The box's min corner when the drag started, or null when there is no drag. */
    @Nullable
    public BlockPos getOldMin() {
        return oldMin;
    }

    /** The box's max corner when the drag started, or null when there is no drag. */
    @Nullable
    public BlockPos getOldMax() {
        return oldMax;
    }

    public Stream<Lock.Target> getLockTargetsStream() {
        return locks.stream().flatMap(lock -> lock.targets.stream());
    }

    /**
     * This box as the world save writes it: {@link #writeToNBT()} plus, if the box came from a save with a Filler
     * Planner on it, that planner's state in the {@code addons} list in the shape the addon layer wrote it (so the one
     * reader above handles both a save from then and a save of this build). It is written for as long as the box
     * exists, since any number of Fillers back onto it and one may load long after the rest. Never sent to clients and
     * never part of the broadcast diff: they have no use for it.
     */
    public CompoundTag writeToDiskNBT() {
        CompoundTag nbt = writeToNBT();
        if (retiredPlanner != null) {
            CompoundTag data = new CompoundTag();
            data.put("patternStatement", retiredPlanner.pattern().copy());
            data.putBoolean("inverted", retiredPlanner.inverted());
            CompoundTag entry = new CompoundTag();
            entry.putString("slot", retiredPlanner.slot());
            entry.putString("addonClass", RETIRED_FILLER_PLANNER_ADDON);
            entry.put("addonData", data);
            ListTag addons = new ListTag();
            addons.add(entry);
            nbt.put("addons", addons);
        }
        return nbt;
    }

    /** This box as it is sent to clients and compared for the broadcast diff: everything but the retired planner. */
    public CompoundTag writeToNBT() {
        CompoundTag nbt = new CompoundTag();
        nbt.putString("id", id.toString());
        nbt.put("box", this.box.writeToNBT());

        if (player != null) {
            nbt.putString("player", player.toString());
        }
        if (held != null) {
            nbt.put("held", NBTUtilBC.writeBlockPos(held));
        }
        nbt.putDouble("dist", dist);
        if (oldMin != null) {
            nbt.put("oldMin", NBTUtilBC.writeBlockPos(oldMin));
        }
        if (oldMax != null) {
            nbt.put("oldMax", NBTUtilBC.writeBlockPos(oldMax));
        }

        if (oneShot) {
            nbt.putBoolean("oneShot", true);
        }
        if (!sourceMarkers.isEmpty()) {
            ListTag markerList = new ListTag();
            for (BlockPos p : sourceMarkers) {
                markerList.add(NBTUtilBC.writeBlockPos(p));
            }
            nbt.put("sourceMarkers", markerList);
        }

        ListTag locksList = new ListTag();
        for (Lock lock : locks) {
            locksList.add(lock.writeToNBT());
        }
        nbt.put("locks", locksList);
        if (owner != null) {
            nbt.put("owner", owner.writeToNBT(new CompoundTag()));
        }
        return nbt;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || !(o == null || getClass() != o.getClass()) && id.equals(((VolumeBox) o).id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
