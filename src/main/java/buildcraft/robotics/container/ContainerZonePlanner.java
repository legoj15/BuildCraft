/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.robotics.container;

import java.util.Arrays;

import io.netty.buffer.Unpooled;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;

import net.neoforged.neoforge.network.handling.IPayloadContext;

import buildcraft.robotics.BCRoboticsMenuTypes;
import buildcraft.robotics.tile.TileZonePlanner;
import buildcraft.robotics.zone.ZonePlan;
import buildcraft.lib.gui.ContainerBCTile;
import buildcraft.lib.gui.slot.SlotBase;
import buildcraft.lib.gui.slot.SlotOutput;
import buildcraft.lib.net.PacketBufferBC;

@SuppressWarnings("this-escape")
public class ContainerZonePlanner extends ContainerBCTile<TileZonePlanner> {

    /** Two synced ints (input, output transfer progress, raw −1..PROGRESS) driving the GUI bars. */
    private final ContainerData progressData;

    // Client-side constructor (from network)
    public ContainerZonePlanner(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv, getTile(playerInv, buf));
    }

    // Server-side constructor
    public ContainerZonePlanner(int containerId, Inventory playerInv, TileZonePlanner tile) {
        super(BCRoboticsMenuTypes.ZONE_PLANNER.get(), containerId, playerInv.player, tile);

        // Player inventory at y=146, matching 1.12.2 addFullPlayerInventory(88, 146)
        addFullPlayerInventory(88, 146);

        // 16 paintbrush slots in a 4×4 grid
        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 4; y++) {
                addSlot(new SlotBase(tile.invPaintbrushes, x * 4 + y, 8 + x * 18, 146 + y * 18));
            }
        }

        // Input section slots
        addSlot(new SlotBase(tile.invInputPaintbrush, 0, 8, 125));
        addSlot(new SlotBase(tile.invInputMapLocation, 0, 26, 125));
        addSlot(new SlotOutput(tile.invInputResult, 0, 74, 125));

        // Output section slots
        addSlot(new SlotBase(tile.invOutputPaintbrush, 0, 233, 9));
        addSlot(new SlotBase(tile.invOutputMapLocation, 0, 233, 27));
        addSlot(new SlotOutput(tile.invOutputResult, 0, 233, 75));

        // Sync the two transfer-progress counters to the client for the furnace-style GUI bars.
        if (tile != null && tile.getLevel() != null && !tile.getLevel().isClientSide()) {
            this.progressData = new ContainerData() {
                @Override
                public int get(int index) {
                    return index == 0 ? tile.getProgressInput() : tile.getProgressOutput();
                }

                @Override
                public void set(int index, int value) {
                    // read-only on the client
                }

                @Override
                public int getCount() {
                    return 2;
                }
            };
        } else {
            this.progressData = new SimpleContainerData(2);
        }
        addDataSlots(this.progressData);
    }

    /** Input transfer progress as a 0..1 fraction (0 when idle), for the GUI's horizontal bar. */
    public float getProgressInputFraction() {
        return fraction(progressData.get(0));
    }

    /** Output transfer progress as a 0..1 fraction (0 when idle), for the GUI's vertical bar. */
    public float getProgressOutputFraction() {
        return fraction(progressData.get(1));
    }

    private static float fraction(int raw) {
        return raw < 0 ? 0f : Math.min(1f, raw / (float) TileZonePlanner.getProgressMax());
    }

    private static TileZonePlanner getTile(Inventory playerInv, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        if (playerInv.player.level() != null) {
            var be = playerInv.player.level().getBlockEntity(pos);
            if (be instanceof TileZonePlanner planner) {
                return planner;
            }
        }
        return null;
    }

    // Shift-click transfer is inherited from ContainerBC_Neptune.quickMoveStack — the generic
    // container<->player-inventory move that respects each slot's mayPlace() filter. (The old
    // override here returned ItemStack.EMPTY, which silently disabled shift-click entirely.)

    // ── Zone-layer sync ──────────────────────────────────────────────────────────────────
    // The viewport edits zone layers client-side; the server tile owns them (the slot I/O reads
    // them) and persists them. NET_PAINT_LAYER carries an edit up; the server's per-tick diff
    // (broadcastChanges) pushes any changed layer back down via NET_SYNC_LAYER — covering both
    // viewport paints and the paintbrush/map-location slot transfer.
    private static final int NET_PAINT_LAYER = 10;
    private static final int NET_SYNC_LAYER = 11;

    /** Per-viewer snapshot of the last layer bytes pushed to this menu's client, for change detection. */
    private byte[][] lastSentLayers;

    /** Client → server: a painted/erased layer. */
    public void sendPaint(int index, ZonePlan layer) {
        sendMessage(NET_PAINT_LAYER, buf -> {
            buf.writeByte(index);
            layer.writeToByteBuf(buf);
        });
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (tile == null || tile.getLevel() == null || tile.getLevel().isClientSide()) {
            return;
        }
        ZonePlan[] layers = tile.layers;
        if (lastSentLayers == null) {
            lastSentLayers = new byte[layers.length][];
        }
        for (int i = 0; i < layers.length; i++) {
            byte[] current = serialize(layers[i]);
            if (!Arrays.equals(current, lastSentLayers[i])) {
                lastSentLayers[i] = current;
                final int idx = i;
                final ZonePlan layer = layers[i];
                sendMessage(NET_SYNC_LAYER, buf -> {
                    buf.writeByte(idx);
                    layer.writeToByteBuf(buf);
                });
            }
        }
    }

    private static byte[] serialize(ZonePlan layer) {
        PacketBufferBC tmp = new PacketBufferBC(Unpooled.buffer());
        layer.writeToByteBuf(tmp);
        byte[] out = new byte[tmp.readableBytes()];
        tmp.readBytes(out);
        tmp.release();
        return out;
    }

    @Override
    public void readMessage(int id, PacketBufferBC buffer, boolean isClient, IPayloadContext ctx) {
        if (id == NET_PAINT_LAYER && !isClient) {
            int idx = buffer.readUnsignedByte();
            if (tile != null && idx >= 0 && idx < tile.layers.length) {
                ZonePlan plan = new ZonePlan();
                plan.readFromByteBuf(buffer);
                tile.layers[idx] = plan;
                tile.setChanged();
            }
            return;
        }
        if (id == NET_SYNC_LAYER && isClient) {
            int idx = buffer.readUnsignedByte();
            if (tile != null && idx >= 0 && idx < tile.layers.length) {
                ZonePlan plan = new ZonePlan();
                plan.readFromByteBuf(buffer);
                tile.layers[idx] = plan;
            }
            return;
        }
        super.readMessage(id, buffer, isClient, ctx);
    }
}
