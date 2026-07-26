/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.snapshot;

import java.io.IOException;

import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;


import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SnapshotResponsePayload(Snapshot snapshot) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SnapshotResponsePayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.parse("buildcraftunofficial:snapshot_response"));

    public static final StreamCodec<FriendlyByteBuf, SnapshotResponsePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                try {
                    CompoundTag nbt = Snapshot.writeToNBT(payload.snapshot());
                    net.minecraft.nbt.NbtIo.writeCompressed(nbt, new ByteBufOutputStream(buf));
                } catch (IOException e) {
                    throw new RuntimeException("Failed to compress snapshot", e);
                }
            },
            buf -> {
                try {
                    CompoundTag nbt = net.minecraft.nbt.NbtIo.readCompressed(new ByteBufInputStream(buf), net.minecraft.nbt.NbtAccounter.unlimitedHeap());
                    return new SnapshotResponsePayload(Snapshot.readFromNBT(nbt));
                } catch (Exception e) {
                    throw new RuntimeException("Failed to decompress snapshot", e);
                }
            }
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SnapshotResponsePayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientSnapshots.INSTANCE.onSnapshotReceived(payload.snapshot());
        });
    }
}
