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

import buildcraft.builders.container.LibraryUploadBuffer;

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
                    // Bounded by the read ceiling (LibraryUploadBuffer.Limits.readCeiling — what may arrive is the
                    // sender's to decide, not this side's upload setting): the payload is compressed, so its size on
                    // the wire says nothing about its size in memory, and what lands here goes into a permanent
                    // client cache. Oversized data fails the decode like unreadable data already does.
                    LibraryUploadBuffer.Limits limits = LibraryUploadBuffer.Limits.readCeiling();
                    CompoundTag nbt = buildcraft.lib.nbt.NbtSquisher.expand(
                        new ByteBufInputStream(buf), limits.maxExpandedBytes());
                    return new SnapshotResponsePayload(Snapshot.readFromNBT(nbt, limits.maxCells()));
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
