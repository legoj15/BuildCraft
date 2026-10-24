/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.snapshot;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import buildcraft.api.enums.EnumSnapshotType;
import buildcraft.lib.net.NetCodecSanity;

/**
 * Server -> client: a batch of block positions the Architect Table scanned this tick. Used to
 * drive the "digitizing" cube effect on the client. Batched per tick so a full scan sends roughly
 * one packet per server tick rather than one per block.
 */
public record ArchitectScanPayload(List<BlockPos> positions) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ArchitectScanPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.parse("buildcraftunofficial:architect_scan"));

    /** The most positions a legitimate batch holds: the Architect Table scans at most one snapshot type's
     *  {@link EnumSnapshotType#maxPerTick} blocks a tick (a template's 900 is the larger), and drains them into one
     *  batch. Clamped before the list is sized so a small packet cannot name a huge allocation. */
    public static final int MAX_POSITIONS = EnumSnapshotType.TEMPLATE.maxPerTick;

    public static final StreamCodec<FriendlyByteBuf, ArchitectScanPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.positions().size());
                for (BlockPos pos : payload.positions()) {
                    buf.writeBlockPos(pos);
                }
            },
            buf -> {
                int count = NetCodecSanity.clampNonNegative(buf.readVarInt(), MAX_POSITIONS);
                List<BlockPos> list = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    list.add(buf.readBlockPos());
                }
                return new ArchitectScanPayload(list);
            }
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ArchitectScanPayload payload,
                              net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> ClientArchitectScans.INSTANCE.onReceived(payload.positions()));
    }
}
