/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team This Source Code Form is subject to the terms of the Mozilla
 * Public License, v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/
 */

package buildcraft.core.marker.volume;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import buildcraft.lib.misc.GameProfileUtil;
import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.lib.misc.data.Box;

public class VolumeBox {
    /** The registered name of the Filler Planner addon in the {@code addonClass} of an old save's {@code addons} entry. */
    private static final String RETIRED_FILLER_PLANNER_ADDON = "buildcraftunofficial:filler_planner";

    public final Level world;
    public UUID id;
    public Box box;
    private UUID player = null;
    private UUID oldPlayer = null;
    private BlockPos held = null;
    private double dist = 0;
    private BlockPos oldMin = null, oldMax = null;
    public final List<Lock> locks = new ArrayList<>();
    @Nullable
    private RetiredPlanner retiredPlanner = null;

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
        oldPlayer = nbt.contains("oldPlayer") ? UUID.fromString(NBTUtilBC.getString(nbt, "oldPlayer", "")) : null;

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

        // An "addons" list in an old save holds the Filler Planner, the only addon there ever was. The addon layer is
        // gone, but a Filler that was driven by one needs its pattern and invert flag: they are kept here until that
        // Filler takes them over (TileFiller.migrateOrphanedBoxOnce), and the world save (writeToDiskNBT) carries them
        // meanwhile, so a save before that Filler's chunk loads does not lose them.
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
     * The pattern and invert flag the Filler Planner of an old save held, or null when there were none or a Filler has
     * already taken them. Handing them out clears them, so one planner migrates one Filler, once, and the world save
     * stops carrying them.
     */
    @Nullable
    public RetiredPlanner takeRetiredPlanner() {
        RetiredPlanner taken = retiredPlanner;
        retiredPlanner = null;
        return taken;
    }

    @SuppressWarnings("WeakerAccess")
    public boolean isEditing() {
        return player != null;
    }

    private void resetEditing() {
        oldMin = oldMax = null;
        held = null;
        dist = 0;
    }

    public void cancelEditing() {
        player = null;
        box.reset();
        box.extendToEncompass(oldMin);
        box.extendToEncompass(oldMax);
        resetEditing();
    }

    public void confirmEditing() {
        player = null;
        resetEditing();
    }

    @SuppressWarnings("WeakerAccess")
    public void pauseEditing() {
        oldPlayer = player;
        player = null;
    }

    public void resumeEditing() {
        player = oldPlayer;
        oldPlayer = null;
    }

    public void setPlayer(Player player) {
        this.player = GameProfileUtil.getId(player.getGameProfile());
    }

    public boolean isEditingBy(Player player) {
        return player != null && Objects.equals(this.player, GameProfileUtil.getId(player.getGameProfile()));
    }

    public boolean isPausedEditingBy(Player player) {
        return oldPlayer != null && Objects.equals(oldPlayer, GameProfileUtil.getId(player.getGameProfile()));
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

    public Stream<Lock.Target> getLockTargetsStream() {
        return locks.stream().flatMap(lock -> lock.targets.stream());
    }

    /**
     * This box as the world save writes it: {@link #writeToNBT()} plus, while no Filler has taken it yet, the retired
     * planner's state in the {@code addons} list in the shape the addon layer wrote it (so the one reader above
     * handles both a save from then and a save of this build). Never sent to clients and never part of the broadcast
     * diff: they have no use for it.
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
        if (oldPlayer != null) {
            nbt.putString("oldPlayer", oldPlayer.toString());
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

        ListTag locksList = new ListTag();
        for (Lock lock : locks) {
            locksList.add(lock.writeToNBT());
        }
        nbt.put("locks", locksList);
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
