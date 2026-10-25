/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team This Source Code Form is subject to the terms of the Mozilla
 * Public License, v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/
 */

package buildcraft.core.marker.volume;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import buildcraft.api.core.BCLog;

import buildcraft.lib.misc.GameProfileUtil;
import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.lib.misc.data.Box;

public class VolumeBox {
    public final Level world;
    public UUID id;
    public Box box;
    /** The player dragging a corner, or null. A drag ends when that player confirms or cancels it, or is no longer in
     *  this box's level (logged out, changed dimension): that cancels it (see LevelSavedDataVolumeBoxes#tick). */
    private UUID player = null;
    private BlockPos held = null;
    private double dist = 0;
    private BlockPos oldMin = null, oldMax = null;
    public final Map<EnumAddonSlot, Addon> addons = new EnumMap<>(EnumAddonSlot.class);
    public final List<Lock> locks = new ArrayList<>();
    /**
     * The machine this box belongs to, or null for a free box (one placed with the Volume Box item). A Filler or
     * Architect Table that takes over survival markers turns them into a box it owns: the box lives exactly as long
     * as the machine (removed with it, never by a wrench) and is the machine's editable working area. Saved under
     * {@code owner}; absent in saves from before ownership existed, which load as free boxes, as they were.
     */
    @Nullable
    private Lock.Cause.CauseBlock owner = null;

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

        if (nbt.contains("addons")) {
            ListTag addonsList = NBTUtilBC.getList(nbt, "addons", Tag.TAG_COMPOUND);
            for (int i = 0; i < addonsList.size(); i++) {
                CompoundTag addonsEntryTag = NBTUtilBC.getCompound(addonsList, i);
                String addonClassName = NBTUtilBC.getString(addonsEntryTag, "addonClass", "");
                try {
                    Class<? extends Addon> addonClass = AddonsRegistry.INSTANCE
                            .getClassByName(Identifier.parse(addonClassName));
                    Addon addon = addonClass.getDeclaredConstructor().newInstance();
                    addon.volumeBox = this;
                    addon.readFromNBT(NBTUtilBC.getCompound(addonsEntryTag, "addonData"));
                    String slotStr = NBTUtilBC.getString(addonsEntryTag, "slot", "");
                    EnumAddonSlot slot = EnumAddonSlot.valueOf(slotStr);
                    addons.put(slot, addon);
                    addon.postReadFromNbt();
                } catch (Exception e) {
                    BCLog.logger.warn("[core.volume] Failed to load a volume box addon from NBT", e);
                }
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
        addons.values().forEach(Addon::onVolumeBoxSizeChange);
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

        ListTag addonsList = new ListTag();
        addons.entrySet().forEach(entry -> {
            CompoundTag addonsEntryTag = new CompoundTag();
            addonsEntryTag.putString("slot", entry.getKey().name());
            addonsEntryTag.putString("addonClass",
                    AddonsRegistry.INSTANCE.getNameByClass(entry.getValue().getClass()).toString());
            addonsEntryTag.put("addonData", entry.getValue().writeToNBT(new CompoundTag()));
            addonsList.add(addonsEntryTag);
        });
        nbt.put("addons", addonsList);

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
