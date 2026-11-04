/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric;

//? if fabric {
/*import io.netty.buffer.Unpooled;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;

import buildcraft.platform.Platform;
import buildcraft.platform.hook.SpawnDataEntity;
import buildcraft.platform.network.PacketContext;

/^*
 * NeoForge's complex-spawn data on Fabric ({@link SpawnDataEntity}): vanilla's add-entity packet carries no extra
 * bytes, so when a player starts tracking such an entity the server sends this payload, holding the entity's id and
 * the bytes its {@code writeExtraSpawnData} wrote, and the client hands them to the same entity's
 * {@code readExtraSpawnData}. Fabric's tracking-start event fires after the spawn packet is sent on the same
 * connection, so the client has the entity by the time the payload is handled.
 *
 * <p>Gap against NeoForge: the data arrives one packet after the spawn instead of inside it, so for that moment the
 * client entity has its constructor's defaults.
 *^/
public final class FabricSpawnData {
    private FabricSpawnData() {}

    /^* The payload: the entity's network id and its extra spawn data, as written. *^/
    public record Message(int entityId, byte[] data) implements CustomPacketPayload {
        public static final Type<Message> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath("buildcraftunofficial", "entity_spawn_data"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Message> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Message::entityId,
                ByteBufCodecs.BYTE_ARRAY, Message::data,
                Message::new);

        @Override
        public Type<Message> type() {
            return TYPE;
        }
    }

    /^* Registers the payload and the tracking listener; the Fabric entrypoint calls it once. *^/
    public static void register() {
        Platform.network().clientbound(Message.TYPE, Message.STREAM_CODEC, FabricSpawnData::handle);
        EntityTrackingEvents.START_TRACKING.register(FabricSpawnData::startedTracking);
    }

    private static void startedTracking(Entity entity, ServerPlayer player) {
        if (!(entity instanceof SpawnDataEntity spawnData)) {
            return;
        }
        RegistryFriendlyByteBuf buffer =
                new RegistryFriendlyByteBuf(Unpooled.buffer(), entity.level().registryAccess());
        try {
            spawnData.writeExtraSpawnData(buffer);
            byte[] data = new byte[buffer.readableBytes()];
            buffer.readBytes(data);
            Platform.network().sendToPlayer(player, new Message(entity.getId(), data));
        } finally {
            buffer.release();
        }
    }

    private static void handle(Message message, PacketContext context) {
        Player player = context.player();
        if (player == null) {
            return;
        }
        // An entity that is gone again, or was never spawned here, has nothing to read the data into.
        if (!(player.level().getEntity(message.entityId()) instanceof SpawnDataEntity spawnData)) {
            return;
        }
        FriendlyByteBuf bytes = new FriendlyByteBuf(Unpooled.wrappedBuffer(message.data()));
        spawnData.readExtraSpawnData(new RegistryFriendlyByteBuf(bytes, player.level().registryAccess()));
    }
}
*///?}
