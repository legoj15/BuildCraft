/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.snapshot;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SnapshotRequestPayload(Snapshot.Key key) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SnapshotRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.parse("buildcraftunofficial:snapshot_request"));

    public static final StreamCodec<FriendlyByteBuf, SnapshotRequestPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeByteArray(payload.key().hash);
                buf.writeBoolean(payload.key().header != null);
                if (payload.key().header != null) {
                    buf.writeUUID(payload.key().header.owner);
                    buf.writeLong(payload.key().header.created.getTime());
                    buf.writeUtf(payload.key().header.name);
                }
            },
            buf -> {
                byte[] hash = buf.readByteArray();
                boolean hasHeader = buf.readBoolean();
                Snapshot.Key key = new Snapshot.Key(new Snapshot.Key(), hash);
                if (hasHeader) {
                    Snapshot.Header header = new Snapshot.Header(key, buf.readUUID(), new java.util.Date(buf.readLong()), buf.readUtf());
                    key = new Snapshot.Key(key, header);
                }
                return new SnapshotRequestPayload(key);
            }
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger("BCSnapshotRequest");

    public static void handle(SnapshotRequestPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            String hashHex = payload.key().hash == null ? "null"
                    : buildcraft.lib.misc.HashUtil.convertHashToString(payload.key().hash);
            // The store is a shared pool every player's uploads and architect tables write into, and nothing lists
            // it to clients, so serving any hash to any asking player would hand one player another's build as soon
            // as they know its hash. A client only asks for a hash it read off a used snapshot item it can see (the
            // tooltip overlay, the architect table and the replacer all pass an item's header key), so the server
            // checks exactly that: the asking player holds the item, or has a container open that holds it — the
            // Electronic Library's own rule (it only shows a player their own local files, and its downloads go to
            // the players watching the machine an item was inserted into).
            if (!playerMayRequest(context.player(), payload.key().hash)) {
                LOGGER.warn("Refusing a snapshot request from {} for hash={}: no snapshot item they hold "
                        + "or have open references it", context.player().getName().getString(), hashHex);
                return;
            }
            net.minecraft.world.level.Level level = context.player().level();
            Snapshot snapshot = GlobalSavedDataSnapshots.get(level).getSnapshot(payload.key());
            if (snapshot != null) {
                LOGGER.info("Replying with snapshot: class={} hash={} size={} (player level={})",
                        snapshot.getClass().getSimpleName(), hashHex, snapshot.size,
                        level.dimension());
                context.reply(new SnapshotResponsePayload(snapshot));
            } else {
                LOGGER.warn("Snapshot NOT FOUND on server: hash={} (player level={}). "
                        + "Saved data is per-dimension — blueprint may have been created in a different dimension.",
                        hashHex, level.dimension());
            }
        });
    }

    /** Whether {@code player} may be handed the snapshot named by {@code hash}: a used snapshot item referencing it
     *  is in their own inventory, in the menu they have open (an architect table, a replacer, a chest, a library —
     *  the item-driven windows whose clients ask), or on their cursor. */
    static boolean playerMayRequest(net.minecraft.world.entity.player.Player player, byte[] hash) {
        if (player == null || hash == null || hash.length == 0) return false;
        net.minecraft.world.entity.player.Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (itemReferencesSnapshot(inventory.getItem(i), hash)) return true;
        }
        net.minecraft.world.inventory.AbstractContainerMenu open = player.containerMenu;
        if (open != null) {
            if (itemReferencesSnapshot(open.getCarried(), hash)) return true;
            for (net.minecraft.world.inventory.Slot slot : open.slots) {
                if (itemReferencesSnapshot(slot.getItem(), hash)) return true;
            }
        }
        return false;
    }

    /** Whether {@code stack} is a used snapshot item whose header names the content hash {@code hash}: the only
     *  thing a legitimate client ever asks for, and the player's proof they may see it. Pure, so its test shares
     *  this one definition with the gate. */
    static boolean itemReferencesSnapshot(net.minecraft.world.item.ItemStack stack, byte[] hash) {
        Snapshot.Header header = buildcraft.builders.item.ItemSnapshot.getHeader(stack);
        return header != null && java.util.Arrays.equals(header.key.hash, hash);
    }
}
