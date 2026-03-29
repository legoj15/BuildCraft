/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.net;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import buildcraft.lib.debug.ClientDebuggables;

/**
 * Sent from server to client in response to a {@link MessageDebugRequest}.
 * Contains the server-side debug info strings.
 */
public record MessageDebugResponse(
        List<String> left,
        List<String> right
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MessageDebugResponse> TYPE =
            new CustomPacketPayload.Type<>(Identifier.parse("buildcraftunofficial:debug_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MessageDebugResponse> STREAM_CODEC =
            StreamCodec.of(MessageDebugResponse::encode, MessageDebugResponse::decode);

    private static void encode(RegistryFriendlyByteBuf buf, MessageDebugResponse msg) {
        buf.writeVarInt(msg.left.size());
        for (String s : msg.left) {
            buf.writeUtf(s);
        }
        buf.writeVarInt(msg.right.size());
        for (String s : msg.right) {
            buf.writeUtf(s);
        }
    }

    private static MessageDebugResponse decode(RegistryFriendlyByteBuf buf) {
        int leftCount = buf.readVarInt();
        List<String> left = new ArrayList<>(leftCount);
        for (int i = 0; i < leftCount; i++) {
            left.add(buf.readUtf());
        }
        int rightCount = buf.readVarInt();
        List<String> right = new ArrayList<>(rightCount);
        for (int i = 0; i < rightCount; i++) {
            right.add(buf.readUtf());
        }
        return new MessageDebugResponse(left, right);
    }

    @Override
    public Type<MessageDebugResponse> type() {
        return TYPE;
    }

    /** Client-side handler. Stores the server debug info for display on the F3 overlay. */
    public static void handle(MessageDebugResponse message, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ClientDebuggables.SERVER_LEFT.clear();
        ClientDebuggables.SERVER_LEFT.addAll(message.left);
        ClientDebuggables.SERVER_RIGHT.clear();
        ClientDebuggables.SERVER_RIGHT.addAll(message.right);
    }
}
