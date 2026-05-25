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
 * Server → client full-replace sync of all VolumeBoxes in a dimension. Each VolumeBox is encoded as its
 * {@link VolumeBox#writeToNBT()} CompoundTag and reconstructed on the client via the
 * {@link VolumeBox#VolumeBox(Level, CompoundTag)} constructor.
 */
public record MessageVolumeBoxes(List<CompoundTag> tags) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MessageVolumeBoxes> TYPE =
            new CustomPacketPayload.Type<>(Identifier.parse("buildcraftunofficial:volume_boxes"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MessageVolumeBoxes> STREAM_CODEC =
            StreamCodec.of(MessageVolumeBoxes::encode, MessageVolumeBoxes::decode);

    private static void encode(RegistryFriendlyByteBuf buf, MessageVolumeBoxes msg) {
        buf.writeShort(msg.tags.size());
        for (CompoundTag tag : msg.tags) {
            buf.writeNbt(tag);
        }
    }

    private static MessageVolumeBoxes decode(RegistryFriendlyByteBuf buf) {
        int count = buf.readShort();
        List<CompoundTag> tags = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            tags.add(buf.readNbt());
        }
        return new MessageVolumeBoxes(tags);
    }

    @Override
    public Type<MessageVolumeBoxes> type() {
        return TYPE;
    }

    /** Client-side handler: full-replace ClientVolumeBoxes with what the server sent. */
    public static void handle(MessageVolumeBoxes message, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Level world = ctx.player().level();

            Set<UUID> previousIds = new HashSet<>();
            for (VolumeBox vb : ClientVolumeBoxes.INSTANCE.volumeBoxes) {
                previousIds.add(vb.id);
            }

            List<VolumeBox> rebuilt = new ArrayList<>(message.tags.size());
            for (CompoundTag tag : message.tags) {
                rebuilt.add(new VolumeBox(world, tag));
            }

            ClientVolumeBoxes.INSTANCE.volumeBoxes.clear();
            ClientVolumeBoxes.INSTANCE.volumeBoxes.addAll(rebuilt);

            for (VolumeBox vb : rebuilt) {
                if (!previousIds.contains(vb.id)) {
                    for (Addon addon : vb.addons.values()) {
                        if (addon != null) {
                            addon.onAdded();
                        }
                    }
                }
            }
        });
    }
}
