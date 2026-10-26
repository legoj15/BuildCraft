/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.core.marker.volume;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.world.entity.player.Player;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
//? if >=1.21.10 {
import net.minecraft.world.level.saveddata.SavedDataType;
//?}

import net.neoforged.neoforge.network.PacketDistributor;

import buildcraft.api.core.IAreaProvider;

import buildcraft.lib.misc.LegacySavedData;
import buildcraft.lib.misc.NBTUtilBC;

import buildcraft.core.BCCoreConfig;
import buildcraft.core.item.ItemMarkerWrench;
import buildcraft.core.item.ItemVolumeWrench;

public class LevelSavedDataVolumeBoxes extends SavedData implements LegacySavedData.Recoverable {
    private static final String DATA_NAME = "buildcraft_volume_boxes";

    // Codec that wraps the entire state as a CompoundTag for serialization
    // Since VolumeBox needs the Level reference (not serializable in a static Codec),
    // we store only the NBT and load it lazily per-level.
    public Level world;
    public final List<VolumeBox> volumeBoxes = new ArrayList<>();

    /**
     * Snapshot (box id → serialized tag) of what dimension clients were last told, so
     * {@link #broadcastToDimension} can send only what changed. Server-side baseline; not persisted.
     */
    private final Map<UUID, CompoundTag> lastBroadcast = new HashMap<>();

    // The SavedDataType uses CompoundTag.CODEC to persist the raw tag, and we
    // decode it manually because VolumeBox needs a Level reference.
    //? if >=26.1 {
    // 26.1+: `world` is unused. The storage hands both factories the level it belongs to, and the data is built for
    // that one (as RobotRegistry, SavedDataWireSystems and the marker types do). The parameter stays so the call
    // sites and tests read the same on every node; before 26.1 the factories get no level and close over it.
    public static SavedDataType<LevelSavedDataVolumeBoxes> createType(Level world) {
        return buildcraft.lib.misc.LegacySavedData.type(
                DATA_NAME,
                level -> new LevelSavedDataVolumeBoxes(level),
                level -> buildCodec(level),
                net.minecraft.util.datafix.DataFixTypes.LEVEL
        );
    }
    //?} elif >=1.21.10 {
    /*public static SavedDataType<LevelSavedDataVolumeBoxes> createType(Level world) {
        return new SavedDataType<>(
                DATA_NAME,
                () -> new LevelSavedDataVolumeBoxes(world),
                buildCodec(world),
                net.minecraft.util.datafix.DataFixTypes.LEVEL
        );
    }*/
    //?} else {
    /*// 1.21.1: SavedData.Factory (ctor, (tag,provider)->T via fromNbt(world), DataFixTypes).
    public static SavedData.Factory<LevelSavedDataVolumeBoxes> createType(Level world) {
        return new SavedData.Factory<>(
                () -> new LevelSavedDataVolumeBoxes(world),
                (tag, provider) -> fromNbt(tag, world),
                net.minecraft.util.datafix.DataFixTypes.LEVEL
        );
    }

    @Override
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        // 1.21.1: SavedData.save is abstract; reuse the existing toNbt serializer.
        return toNbt(this);
    }*/
    //?}

    private static Codec<LevelSavedDataVolumeBoxes> buildCodec(Level world) {
        return CompoundTag.CODEC.xmap(
                nbt -> fromNbt(nbt, world),
                LevelSavedDataVolumeBoxes::toNbt
        );
    }

    /** The boxes as the world save wrote them. */
    public static LevelSavedDataVolumeBoxes fromNbt(CompoundTag nbt, Level world) {
        LevelSavedDataVolumeBoxes instance = new LevelSavedDataVolumeBoxes(world);
        if (nbt.contains("volumeBoxes")) {
            ListTag listTag = NBTUtilBC.getList(nbt, "volumeBoxes", Tag.TAG_COMPOUND);
            for (int i = 0; i < listTag.size(); i++) {
                CompoundTag tag = NBTUtilBC.getCompound(listTag, i);
                VolumeBox volumeBox = new VolumeBox(world, tag);
                instance.volumeBoxes.add(volumeBox);
                // Every client that joins is sent the whole loaded set, so it is the baseline too: left empty, the
                // removal of a box that was there at load diffed to nothing and the client kept a ghost box.
                instance.lastBroadcast.put(volumeBox.id, volumeBox.writeToNBT());
            }
        }
        return instance;
    }

    /** The boxes as the world save writes them. */
    public static CompoundTag toNbt(LevelSavedDataVolumeBoxes data) {
        CompoundTag nbt = new CompoundTag();
        ListTag listTag = new ListTag();
        for (VolumeBox volumeBox : data.volumeBoxes) {
            listTag.add(volumeBox.writeToDiskNBT());
        }
        nbt.put("volumeBoxes", listTag);
        return nbt;
    }

    public LevelSavedDataVolumeBoxes(Level world) {
        this.world = world;
    }

    @Override
    public boolean hasNoContent() {
        return volumeBoxes.isEmpty();
    }

    @Override
    public String describeContent() {
        return volumeBoxes.size() + " volume boxes";
    }

    public VolumeBox getVolumeBoxAt(BlockPos pos) {
        return volumeBoxes.stream().filter(volumeBox -> volumeBox.box.contains(pos)).findFirst().orElse(null);
    }

    /**
     * The box at {@code pos} a machine may lock and share (never a one-shot box, nor one another machine owns), or
     * null. Boxes that machines already lock are included: Fillers backed onto one box from an old save share it.
     */
    @Nullable
    public VolumeBox getLockableVolumeBoxAt(BlockPos pos) {
        return volumeBoxes.stream()
            .filter(volumeBox -> !volumeBox.isOneShot() && volumeBox.getOwner() == null && volumeBox.box.contains(pos))
            .findFirst().orElse(null);
    }

    /**
     * A free box containing {@code pos}: what a machine placed against it may adopt. A box another machine owns or
     * has already adopted (a machine lock) is that machine's working area, never a second machine's, and a one-shot
     * box is not locked at all (see {@link #getOneShotAreaAt}).
     */
    @Nullable
    public VolumeBox getFreeVolumeBoxAt(BlockPos pos) {
        return volumeBoxes.stream().filter(volumeBox -> !volumeBox.isOneShot() && volumeBox.getOwner() == null
                && volumeBox.box.contains(pos)
                && volumeBox.getLockTargetsStream().noneMatch(Lock.Target.TargetUsedByMachine.class::isInstance))
            .findFirst().orElse(null);
    }

    /**
     * The confirmed one-shot box at {@code pos} as an area a machine takes the way it takes markers: removing it from
     * the world removes the box. Null when there is none, or when it is still being edited or still backed by markers.
     * A Filler or Architect Table that takes it makes it the box it owns ({@link #addOwnedBox}); a Quarry just
     * consumes it.
     */
    @Nullable
    public IAreaProvider getOneShotAreaAt(BlockPos pos) {
        VolumeBox found = volumeBoxes.stream()
            .filter(volumeBox -> volumeBox.isOneShot() && !volumeBox.isEditing() && !volumeBox.hasSourceMarkers()
                && volumeBox.box.contains(pos))
            .findFirst().orElse(null);
        if (found == null) {
            return null;
        }
        BlockPos min = found.box.min();
        BlockPos max = found.box.max();
        return new IAreaProvider() {
            @Override
            public BlockPos min() {
                return min;
            }

            @Override
            public BlockPos max() {
                return max;
            }

            @Override
            public void removeFromWorld() {
                volumeBoxes.remove(found);
                markDirtyAndBroadcast();
            }
        };
    }

    /** The box the machine at {@code ownerPos} owns, or null. */
    @Nullable
    public VolumeBox getOwnedBy(BlockPos ownerPos) {
        for (VolumeBox volumeBox : volumeBoxes) {
            if (volumeBox.isOwnedBy(ownerPos)) {
                return volumeBox;
            }
        }
        return null;
    }

    public void addVolumeBox(BlockPos pos) {
        volumeBoxes.add(new VolumeBox(world, pos));
        setDirty();
        broadcastToDimension();
    }

    /**
     * Creates the box a machine owns from the area it took over (survival markers), replacing any box already owned
     * from that position. Locked against removal and marked as in use by the machine; not against resizing, so the
     * Volume Wrench can drag its corners and the machine follows (see {@link VolumeBox#getOwner()}).
     */
    public VolumeBox addOwnedBox(BlockPos min, BlockPos max, BlockPos ownerPos, Block ownerBlock,
                                 Lock.Target.TargetUsedByMachine.EnumType use) {
        removeOwnedBy(ownerPos);
        VolumeBox volumeBox = new VolumeBox(world, min);
        volumeBox.box.extendToEncompass(max);
        volumeBox.setOwner(ownerPos, ownerBlock);
        volumeBox.locks.add(new Lock(
            new Lock.Cause.CauseBlock(ownerPos.immutable(), ownerBlock),
            new Lock.Target.TargetRemove(),
            new Lock.Target.TargetUsedByMachine(use)));
        volumeBoxes.add(volumeBox);
        markDirtyAndBroadcast();
        return volumeBox;
    }

    /** Removes the box the machine at {@code ownerPos} owns, if any: the machine is gone. */
    public void removeOwnedBy(BlockPos ownerPos) {
        boolean removed = false;
        for (Iterator<VolumeBox> it = volumeBoxes.iterator(); it.hasNext(); ) {
            VolumeBox volumeBox = it.next();
            if (volumeBox.isOwnedBy(ownerPos)) {
                it.remove();
                removed = true;
            }
        }
        if (removed) {
            markDirtyAndBroadcast();
        }
    }

    /**
     * Diffs the current VolumeBox set against {@link #lastBroadcast} and sends only the changed boxes
     * (add-or-update) plus the ids of any that vanished, instead of re-broadcasting the whole set on
     * every mutation. No packet is sent when nothing changed. Server-side only.
     */
    public void broadcastToDimension() {
        if (!(world instanceof ServerLevel sl)) return;

        MessageVolumeBoxes delta = takeDelta();
        if (delta != null) {
            PacketDistributor.sendToPlayersInDimension(sl, delta);
        }
    }

    /** What changed since dimension clients were last told (null when nothing did); moves the baseline to now. */
    MessageVolumeBoxes takeDelta() {
        Map<UUID, CompoundTag> current = new HashMap<>();
        List<CompoundTag> upserts = new ArrayList<>();
        for (VolumeBox volumeBox : volumeBoxes) {
            CompoundTag tag = volumeBox.writeToNBT();
            current.put(volumeBox.id, tag);
            CompoundTag previous = lastBroadcast.get(volumeBox.id);
            if (previous == null || !previous.equals(tag)) {
                upserts.add(tag);
            }
        }
        List<UUID> removed = new ArrayList<>();
        for (UUID id : lastBroadcast.keySet()) {
            if (!current.containsKey(id)) {
                removed.add(id);
            }
        }

        lastBroadcast.clear();
        lastBroadcast.putAll(current);

        if (upserts.isEmpty() && removed.isEmpty()) return null;
        return MessageVolumeBoxes.delta(upserts, removed);
    }

    /** Sends the full current set of VolumeBoxes to one specific player (initial tracking). Server-side only. */
    public void sendTo(ServerPlayer player) {
        List<CompoundTag> tags = volumeBoxes.stream().map(VolumeBox::writeToNBT).toList();
        PacketDistributor.sendToPlayer(player, MessageVolumeBoxes.fullReplace(tags));
    }

    /**
     * Mark the saved data dirty AND broadcast the new state to clients in the dimension. Use this from
     * any caller that mutates VolumeBoxes outside of {@link #addVolumeBox} or {@link #tick} (e.g.
     * editing flow in ItemMarkerWrench, lock additions in TileFiller / TileArchitectTable).
     */
    public void markDirtyAndBroadcast() {
        setDirty();
        broadcastToDimension();
    }

    public VolumeBox getVolumeBoxFromId(UUID id) {
        return volumeBoxes.stream().filter(volumeBox -> volumeBox.id.equals(id)).findFirst().orElse(null);
    }

    public void tick() {
        boolean dirty = false;
        for (int i = 0; i < volumeBoxes.size(); i++) {
            VolumeBox volumeBox = volumeBoxes.get(i);
            // The fallback for an owner removed without its removal hook (a >=1.21.10 command replace skips block
            // side effects): a box whose machine is no longer there goes too. An unloaded machine keeps its box
            // (stillWorks holds while the chunk is not loaded).
            Lock.Cause.CauseBlock owner = volumeBox.getOwner();
            if (owner != null && !owner.stillWorks(world)) {
                volumeBoxes.remove(i--);
                dirty = true;
                continue;
            }
            if (volumeBox.isEditing()) {
                Player player = volumeBox.getPlayer(world);
                if (player == null || player.isSpectator() || !holdsDragTool(player, volumeBox)
                        || volumeBox.getHeld() == null || volumeBox.getOldMin() == null || volumeBox.getOldMax() == null) {
                    // The drag is cancelled and the old size comes back when its player can no longer finish it: not
                    // in this level any more (logged out, changed dimension), a spectator (who cannot use items), or
                    // no longer holding the tool that drags this box; or when the drag lost its anchor. Nothing waits
                    // for them, so a machine that owns the box is never left frozen on an edit nobody can finish.
                    if (volumeBox.hasSourceMarkers()) {
                        // A box lifted off markers is cancelled by dropping it: the markers keep working.
                        volumeBoxes.remove(i--);
                        dirty = true;
                        continue;
                    }
                    volumeBox.cancelEditing();
                    dirty = true;
                } else {
                    AABB oldAabb = volumeBox.box.getBoundingBox();
                    volumeBox.box.reset();
                    volumeBox.box.extendToEncompass(volumeBox.getHeld());
                    BlockPos lookingAt = BlockPos.containing(
                            player.position()
                                    .add(0, player.getEyeHeight(), 0)
                                    .add(player.getLookAngle().scale(volumeBox.getDist())));
                    // No drag makes a box larger than markers could: each axis stops at the marker reach.
                    volumeBox.box.extendToEncompass(VolumeBox.clampDragTarget(volumeBox.getHeld(), lookingAt,
                            BCCoreConfig.markerMaxDistance.get(), volumeBox.getOldMin(), volumeBox.getOldMax()));
                    if (!volumeBox.box.getBoundingBox().equals(oldAabb)) {
                        dirty = true;
                    }
                }
            }

            // Handle locks removal without streams
            if (!volumeBox.locks.isEmpty()) {
                boolean removed = volumeBox.locks.removeIf(lock -> !lock.cause.stillWorks(world));
                if (removed) {
                    dirty = true;
                }
            }
        }

        if (dirty) {
            setDirty();
            broadcastToDimension();
        }
    }

    /**
     * Whether {@code player} holds the tool that drags {@code volumeBox}: a Marker Wrench (a Volume Wrench is one too)
     * for a box lifted off markers, the Volume Wrench for any other.
     */
    private static boolean holdsDragTool(Player player, VolumeBox volumeBox) {
        Class<?> tool = volumeBox.hasSourceMarkers() ? ItemMarkerWrench.class : ItemVolumeWrench.class;
        return tool.isInstance(player.getMainHandItem().getItem()) || tool.isInstance(player.getOffhandItem().getItem());
    }

    public static LevelSavedDataVolumeBoxes get(Level world) {
        if (world.isClientSide()) {
            throw new IllegalArgumentException("Tried to create a world saved data instance on the client!");
        }
        ServerLevel serverLevel = (ServerLevel) world;
        //? if >=1.21.10 {
        return serverLevel.getDataStorage().computeIfAbsent(createType(world));
        //?} else {
        /*return serverLevel.getDataStorage().computeIfAbsent(createType(world), DATA_NAME);*/
        //?}
    }
}
