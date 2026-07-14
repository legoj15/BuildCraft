/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.marker.volume;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server → client VolumeBox sync. Two shapes:
 * <ul>
 *   <li><b>full replace</b> ({@code fullReplace == true}) — the client discards its set and rebuilds
 *       from {@code upserts}. Used for initial tracking (player login / dimension change).</li>
 *   <li><b>delta</b> ({@code fullReplace == false}) — the client applies {@code removed} (by id) then
 *       {@code upserts} (add-or-replace by id). Sent on every mutation so only changed boxes travel,
 *       instead of re-broadcasting the whole dimension's set as the pre-delta protocol did.</li>
 * </ul>
 * Each VolumeBox is encoded as its {@link VolumeBox#writeToNBT()} CompoundTag (id included) and
 * reconstructed via the {@link VolumeBox#VolumeBox(Level, CompoundTag)} constructor. Both shapes are
 * idempotent and order-independent between the two lists (a box is never in both).
 */
public record MessageVolumeBoxes(boolean fullReplace, List<CompoundTag> upserts, List<UUID> removed)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MessageVolumeBoxes> TYPE =
            new CustomPacketPayload.Type<>(Identifier.parse("buildcraftunofficial:volume_boxes"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MessageVolumeBoxes> STREAM_CODEC =
            StreamCodec.of(MessageVolumeBoxes::encode, MessageVolumeBoxes::decode);

    /** Builds a full-replace snapshot from a set of box tags. */
    public static MessageVolumeBoxes fullReplace(List<CompoundTag> tags) {
        return new MessageVolumeBoxes(true, tags, List.of());
    }

    /** Builds an incremental delta from changed-box tags and removed-box ids. */
    public static MessageVolumeBoxes delta(List<CompoundTag> upserts, List<UUID> removed) {
        return new MessageVolumeBoxes(false, upserts, removed);
    }

    private static void encode(RegistryFriendlyByteBuf buf, MessageVolumeBoxes msg) {
        buf.writeBoolean(msg.fullReplace);
        buf.writeShort(msg.upserts.size());
        for (CompoundTag tag : msg.upserts) {
            buf.writeNbt(tag);
        }
        buf.writeShort(msg.removed.size());
        for (UUID id : msg.removed) {
            buf.writeUUID(id);
        }
    }

    private static MessageVolumeBoxes decode(RegistryFriendlyByteBuf buf) {
        boolean fullReplace = buf.readBoolean();
        int upsertCount = buf.readShort();
        List<CompoundTag> upserts = new ArrayList<>(upsertCount);
        for (int i = 0; i < upsertCount; i++) {
            upserts.add(buf.readNbt());
        }
        int removedCount = buf.readShort();
        List<UUID> removed = new ArrayList<>(removedCount);
        for (int i = 0; i < removedCount; i++) {
            removed.add(buf.readUUID());
        }
        return new MessageVolumeBoxes(fullReplace, upserts, removed);
    }

    @Override
    public Type<MessageVolumeBoxes> type() {
        return TYPE;
    }

    /** Client-side handler: apply the full snapshot or the incremental delta to ClientVolumeBoxes. */
    public static void handle(MessageVolumeBoxes message, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Level world = ctx.player().level();
            List<VolumeBox> boxes = ClientVolumeBoxes.INSTANCE.volumeBoxes;

            if (message.fullReplace) {
                Set<UUID> previousIds = new HashSet<>();
                for (VolumeBox vb : boxes) {
                    previousIds.add(vb.id);
                }

                List<VolumeBox> rebuilt = new ArrayList<>(message.upserts.size());
                for (CompoundTag tag : message.upserts) {
                    rebuilt.add(new VolumeBox(world, tag));
                }

                boxes.clear();
                boxes.addAll(rebuilt);

                for (VolumeBox vb : rebuilt) {
                    if (!previousIds.contains(vb.id)) {
                        fireOnAdded(vb);
                    }
                }
            } else {
                // Removals first, then add-or-replace. onAdded fires only for genuinely-new ids,
                // matching the pre-delta full-replace: persisting/updated ids never re-fired it, and
                // a replaced box's addons still refresh via VolumeBox's ctor postReadFromNbt().
                if (!message.removed.isEmpty()) {
                    Set<UUID> toRemove = new HashSet<>(message.removed);
                    boxes.removeIf(vb -> toRemove.contains(vb.id));
                }
                for (CompoundTag tag : message.upserts) {
                    VolumeBox rebuilt = new VolumeBox(world, tag);
                    int idx = indexOfId(boxes, rebuilt.id);
                    if (idx >= 0) {
                        boxes.set(idx, rebuilt);
                    } else {
                        boxes.add(rebuilt);
                        fireOnAdded(rebuilt);
                    }
                }
            }
        });
    }

    private static int indexOfId(List<VolumeBox> boxes, UUID id) {
        for (int i = 0; i < boxes.size(); i++) {
            if (boxes.get(i).id.equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private static void fireOnAdded(VolumeBox vb) {
        for (Addon addon : vb.addons.values()) {
            if (addon != null) {
                addon.onAdded();
            }
        }
    }
}
