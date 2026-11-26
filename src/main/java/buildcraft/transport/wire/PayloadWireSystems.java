/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport.wire;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableList;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import buildcraft.api.transport.pipe.IPipeHolder;
import buildcraft.platform.network.PacketContext;


public class PayloadWireSystems implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PayloadWireSystems> TYPE =
        new CustomPacketPayload.Type<>(Identifier.parse("buildcraftunofficial:wire_systems"));

    public static final StreamCodec<FriendlyByteBuf, PayloadWireSystems> STREAM_CODEC =
        StreamCodec.of(PayloadWireSystems::encode, PayloadWireSystems::decode);

    private final Map<Integer, WireSystem> wireSystems;

    public PayloadWireSystems(Map<Integer, WireSystem> wireSystems) {
        this.wireSystems = wireSystems;
    }

    private static void encode(FriendlyByteBuf buf, PayloadWireSystems msg) {
        buf.writeInt(msg.wireSystems.size());
        msg.wireSystems.forEach((wiresHashCode, wireSystem) -> {
            buf.writeInt(wiresHashCode);
            List<WireSystem.WireElement> elements = wireSystem.elements.stream()
                .filter(element -> element.type == WireSystem.WireElement.Type.WIRE_PART)
                .collect(Collectors.toList());
            buf.writeInt(elements.size());
            elements.forEach(element -> element.toBytes(buf));
        });
    }

    private static PayloadWireSystems decode(FriendlyByteBuf buf) {
        Map<Integer, WireSystem> systems = new HashMap<>();
        int count = buf.readInt();
        for (int i = 0; i < count; i++) {
            int wiresHashCode = buf.readInt();
            int localCount = buf.readInt();

            ImmutableList.Builder<WireSystem.WireElement> elements = ImmutableList.builder();
            for (int j = 0; j < localCount; j++) {
                elements.add(new WireSystem.WireElement(buf));
            }
            WireSystem wireSystem = new WireSystem(elements.build(), null);
            systems.put(wiresHashCode, wireSystem);
        }
        return new PayloadWireSystems(systems);
    }

    @Override
    public Type<PayloadWireSystems> type() {
        return TYPE;
    }

    public static void handle(PayloadWireSystems message, PacketContext ctx) {
        ctx.runOnMain(() -> {
            // No local player when the connection closed before this ran. The systems belong to the level they were
            // sent for, which is gone (its unload cleared them): they must not be put back for the next one.
            Player player = ctx.player();
            if (player == null) {
                return;
            }
            Set<WireSystem.WireElement> incoming = new HashSet<>();
            message.wireSystems.values().forEach(wireSystem -> incoming.addAll(wireSystem.elements));
            // A wire the old list held and the new one does not is in no system this player can see, so it is not
            // lit; the power payload that follows covers the rest.
            Level world = player.level();
            for (WireSystem wireSystem : ClientWireSystems.INSTANCE.wireSystems.values()) {
                for (WireSystem.WireElement element : wireSystem.elements) {
                    if (element.type != WireSystem.WireElement.Type.WIRE_PART || incoming.contains(element)) {
                        continue;
                    }
                    BlockEntity tile = world.getBlockEntity(element.blockPos);
                    if (tile instanceof IPipeHolder holder
                            && holder.getWireManager() instanceof WireManager wireManager) {
                        wireManager.poweredClient.remove(element.wirePart);
                    }
                }
            }
            ClientWireSystems.INSTANCE.wireSystems.clear();
            ClientWireSystems.INSTANCE.wireSystems.putAll(message.wireSystems);
        });
    }
}
