/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.core.marker.volume;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.world.entity.player.Player;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedDataType;

import net.neoforged.neoforge.network.PacketDistributor;

public class LevelSavedDataVolumeBoxes extends SavedData {
    private static final String DATA_NAME = "buildcraft_volume_boxes";

    // Codec that wraps the entire state as a CompoundTag for serialization
    // Since VolumeBox needs the Level reference (not serializable in a static Codec),
    // we store only the NBT and load it lazily per-level.
    public Level world;
    public final List<VolumeBox> volumeBoxes = new ArrayList<>();

    // The SavedDataType uses CompoundTag.CODEC to persist the raw tag, and we
    // decode it manually because VolumeBox needs a Level reference.
    public static SavedDataType<LevelSavedDataVolumeBoxes> createType(Level world) {
        return new SavedDataType<>(
                Identifier.withDefaultNamespace(DATA_NAME),
                () -> new LevelSavedDataVolumeBoxes(world),
                buildCodec(world),
                net.minecraft.util.datafix.DataFixTypes.LEVEL
        );
    }

    private static Codec<LevelSavedDataVolumeBoxes> buildCodec(Level world) {
        return CompoundTag.CODEC.xmap(
                nbt -> fromNbt(nbt, world),
                LevelSavedDataVolumeBoxes::toNbt
        );
    }

    private static LevelSavedDataVolumeBoxes fromNbt(CompoundTag nbt, Level world) {
        LevelSavedDataVolumeBoxes instance = new LevelSavedDataVolumeBoxes(world);
        if (nbt.contains("volumeBoxes")) {
            ListTag listTag = nbt.getList("volumeBoxes").orElseGet(ListTag::new);
            for (int i = 0; i < listTag.size(); i++) {
                CompoundTag tag = listTag.getCompound(i).orElseGet(CompoundTag::new);
                instance.volumeBoxes.add(new VolumeBox(world, tag));
            }
        }
        return instance;
    }

    private static CompoundTag toNbt(LevelSavedDataVolumeBoxes data) {
        CompoundTag nbt = new CompoundTag();
        ListTag listTag = new ListTag();
        for (VolumeBox volumeBox : data.volumeBoxes) {
            listTag.add(volumeBox.writeToNBT());
        }
        nbt.put("volumeBoxes", listTag);
        return nbt;
    }

    public LevelSavedDataVolumeBoxes(Level world) {
        this.world = world;
    }

    public VolumeBox getVolumeBoxAt(BlockPos pos) {
        return volumeBoxes.stream().filter(volumeBox -> volumeBox.box.contains(pos)).findFirst().orElse(null);
    }

    public void addVolumeBox(BlockPos pos) {
        volumeBoxes.add(new VolumeBox(world, pos));
        setDirty();
        broadcastToDimension();
    }

    /** Sends the full current set of VolumeBoxes to every player in this dimension. Server-side only. */
    public void broadcastToDimension() {
        if (!(world instanceof ServerLevel sl)) return;
        List<CompoundTag> tags = volumeBoxes.stream().map(VolumeBox::writeToNBT).toList();
        PacketDistributor.sendToPlayersInDimension(sl, new MessageVolumeBoxes(tags));
    }

    /** Sends the full current set of VolumeBoxes to one specific player. Server-side only. */
    public void sendTo(ServerPlayer player) {
        List<CompoundTag> tags = volumeBoxes.stream().map(VolumeBox::writeToNBT).toList();
        PacketDistributor.sendToPlayer(player, new MessageVolumeBoxes(tags));
    }

    /**
     * Mark the saved data dirty AND broadcast the new state to clients in the dimension. Use this from
     * any caller that mutates VolumeBoxes outside of {@link #addVolumeBox} or {@link #tick} (e.g.
     * editing flow in ItemMarkerConnector, lock additions in TileFiller / TileArchitectTable).
     */
    public void markDirtyAndBroadcast() {
        setDirty();
        broadcastToDimension();
    }

    public VolumeBox getVolumeBoxFromId(UUID id) {
        return volumeBoxes.stream().filter(volumeBox -> volumeBox.id.equals(id)).findFirst().orElse(null);
    }

    public VolumeBox getCurrentEditing(Player player) {
        return volumeBoxes.stream().filter(volumeBox -> volumeBox.isEditingBy(player)).findFirst().orElse(null);
    }

    public void tick() {
        boolean dirty = false;
        for (int i = 0; i < volumeBoxes.size(); i++) {
            VolumeBox volumeBox = volumeBoxes.get(i);
            if (volumeBox.isEditing()) {
                Player player = volumeBox.getPlayer(world);
                if (player == null) {
                    volumeBox.pauseEditing();
                    dirty = true;
                } else {
                    AABB oldAabb = volumeBox.box.getBoundingBox();
                    volumeBox.box.reset();
                    volumeBox.box.extendToEncompass(volumeBox.getHeld());
                    BlockPos lookingAt = BlockPos.containing(
                            player.position()
                                    .add(0, player.getEyeHeight(), 0)
                                    .add(player.getLookAngle().scale(volumeBox.getDist())));
                    volumeBox.box.extendToEncompass(lookingAt);
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
    public static LevelSavedDataVolumeBoxes get(Level world) {
        if (world.isClientSide()) {
            throw new IllegalArgumentException("Tried to create a world saved data instance on the client!");
        }
        ServerLevel serverLevel = (ServerLevel) world;
        return serverLevel.getDataStorage().computeIfAbsent(createType(world));
    }
}
