/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.marker;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import buildcraft.core.client.MarkerDistanceTracker;
import buildcraft.platform.network.PacketContext;

/**
 * Server -> client: start the distance readout for one volume marker (see {@link MarkerDistanceTracker}).
 * <p>
 * The server sends its own {@code markerMaxDistance} because that option is COMMON config: it backs the server's real
 * connection rule and is never synced, so the client's copy may say anything. The message is sent after the click has
 * been handled, and the marker syncs that click caused ({@code MessageMarker}) go out on the same connection first, so
 * the client has seen any connection the click made by the time this arrives.
 *
 * @param pos the marker clicked
 * @param maxDistance the server's marker connection limit
 */
public record MessageMarkerTrack(BlockPos pos, int maxDistance) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MessageMarkerTrack> TYPE =
            new CustomPacketPayload.Type<>(Identifier.parse("buildcraftunofficial:marker_track"));

    public static final StreamCodec<FriendlyByteBuf, MessageMarkerTrack> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBlockPos(payload.pos);
                buf.writeVarInt(payload.maxDistance);
            },
            buf -> new MessageMarkerTrack(buf.readBlockPos(), buf.readVarInt()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Client-side handler. The tracker is named only inside the queued task, which a dedicated server never runs
     *  (this message goes to clients), so linking this class does not load it. */
    public static void handle(MessageMarkerTrack message, PacketContext ctx) {
        ctx.runOnMain(() -> MarkerDistanceTracker.start(message.pos, message.maxDistance, ctx.player().level()));
    }
}
