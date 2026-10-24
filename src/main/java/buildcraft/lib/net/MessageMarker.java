/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.net;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import buildcraft.api.core.BCLog;
import buildcraft.lib.marker.MarkerCache;

/**
 * Syncs marker positions and connections from server to client.
 * <p>
 * Fields:
 * <ul>
 * <li>{@code add} - true to add markers/connections, false to remove</li>
 * <li>{@code connection} - true if this packet describes a connection, false for individual marker positions</li>
 * <li>{@code cacheId} - the index of the MarkerCache type (Volume, Path, etc.)</li>
 * <li>{@code positions} - the list of BlockPos involved</li>
 * </ul>
 */
public record MessageMarker(
        boolean add,
        boolean connection,
        int cacheId,
        List<BlockPos> positions
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MessageMarker> TYPE =
            new CustomPacketPayload.Type<>(Identifier.parse("buildcraftunofficial:marker"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MessageMarker> STREAM_CODEC =
            StreamCodec.of(MessageMarker::encode, MessageMarker::decode);

    /**
     * The most positions one message carries (a message of this size is a quarter of a megabyte, well inside a
     * packet). It is the largest count the old signed-short field could hold, so nothing that used to fit is now
     * refused; a longer list is cut by {@link #split} and a longer count off the wire is refused as a bad packet.
     */
    public static final int MAX_POSITIONS = Short.MAX_VALUE;

    /**
     * The messages that carry {@code positions}: one when they fit, otherwise several of at most
     * {@link #MAX_POSITIONS} (the join sync sends every marker of a cache type in the dimension in one list). The
     * positions of a marker add or remove, and of a connection removal, are handled one at a time on arrival, so
     * the pieces may arrive as separate messages. A connection <em>add</em> names one connection through all its
     * positions and cannot be cut (the receiver would build one connection per piece, each tearing down the last):
     * one too long to send is dropped with a warning rather than left to fail the encode, which disconnects the
     * player.
     */
    public static List<MessageMarker> split(boolean add, boolean connection, int cacheId, List<BlockPos> positions) {
        int total = positions.size();
        if (total <= MAX_POSITIONS) {
            return List.of(new MessageMarker(add, connection, cacheId, positions));
        }
        if (add && connection) {
            BCLog.logger.warn("[lib.messages][marker] A connection of {} markers is too long to send (the limit is {}); "
                + "clients will not show it", total, MAX_POSITIONS);
            return List.of();
        }
        List<MessageMarker> messages = new ArrayList<>((total + MAX_POSITIONS - 1) / MAX_POSITIONS);
        for (int from = 0; from < total; from += MAX_POSITIONS) {
            int to = Math.min(total, from + MAX_POSITIONS);
            messages.add(new MessageMarker(add, connection, cacheId, new ArrayList<>(positions.subList(from, to))));
        }
        return messages;
    }

    private static void encode(RegistryFriendlyByteBuf buf, MessageMarker msg) {
        int count = msg.positions.size();
        if (count > MAX_POSITIONS) {
            // A sender that skipped split(): refuse here, where the cause is named, instead of writing a count the
            // receiver refuses as a bad packet.
            throw new IllegalStateException("Marker message of " + count + " positions exceeds " + MAX_POSITIONS
                + "; build it with MessageMarker.split");
        }
        buf.writeBoolean(msg.add);
        buf.writeBoolean(msg.connection);
        buf.writeShort(msg.cacheId);
        buf.writeVarInt(count);
        for (BlockPos pos : msg.positions) {
            buf.writeBlockPos(pos);
        }
    }

    private static MessageMarker decode(RegistryFriendlyByteBuf buf) {
        boolean add = buf.readBoolean();
        boolean connection = buf.readBoolean();
        int cacheId = buf.readShort();
        int count = NetCodecSanity.checkedCount(buf.readVarInt(), MAX_POSITIONS, "Marker position");
        List<BlockPos> positions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            positions.add(buf.readBlockPos());
        }
        return new MessageMarker(add, connection, cacheId, positions);
    }

    @Override
    public Type<MessageMarker> type() {
        return TYPE;
    }

    /**
     * Client-side handler. Dispatches to the appropriate MarkerSubCache.
     */
    public static void handle(MessageMarker message, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        if (message.cacheId < 0 || message.cacheId >= MarkerCache.CACHES.size()) {
            BCLog.logger.warn("[lib.messages][marker] Invalid cache ID: " + message.cacheId);
            return;
        }
        net.minecraft.world.level.Level world = ctx.player().level();
        MarkerCache<?> cache = MarkerCache.CACHES.get(message.cacheId);
        cache.getSubCache(world).handleMessageMain(message);
    }
}
