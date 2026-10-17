/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.robotics.container;

import java.util.Arrays;

import io.netty.buffer.Unpooled;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;

import net.neoforged.neoforge.network.handling.IPayloadContext;

import buildcraft.api.core.BCLog;
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
        this(containerId, playerInv, resolveTile(playerInv, buf, TileZonePlanner.class));
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

    // Shift-click transfer is inherited from ContainerBC_Neptune.quickMoveStack — the generic
    // container<->player-inventory move that respects each slot's mayPlace() filter. (The old
    // override here returned ItemStack.EMPTY, which silently disabled shift-click entirely.)

    // ── Zone-layer sync ──────────────────────────────────────────────────────────────────
    // The viewport edits zone layers client-side; the server tile owns them (the slot I/O reads
    // them) and persists them. NET_PAINT_LAYER carries an edit up; the server's per-tick diff
    // (broadcastChanges) pushes any changed layer back down via NET_SYNC_LAYER — covering both
    // viewport paints and the paintbrush/map-location slot transfer.
    public static final int NET_PAINT_LAYER = 10;
    public static final int NET_SYNC_LAYER = 11;

    /** What this viewer's menu knows about one layer: the instance and modification count it last examined
     *  (so an untouched layer costs two compares per tick, not a serialisation) and the bytes last pushed to
     *  (or known to be already held by) this viewer's client. */
    private static final class LayerView {
        ZonePlan seen;
        int seenMod;
        byte[] sent;

        void markSent(ZonePlan layer, byte[] bytes) {
            seen = layer;
            seenMod = layer.modCount();
            sent = bytes;
        }

        /** Forces the next diff to push the layer, whatever it holds. */
        void forget() {
            seen = null;
            sent = null;
        }
    }

    /** Per-viewer, per-layer sync state; created on first use. */
    private LayerView[] views;

    private LayerView view(int index) {
        if (views == null) {
            views = new LayerView[tile.layers.length];
        }
        if (views[index] == null) {
            views[index] = new LayerView();
        }
        return views[index];
    }

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
        for (int i = 0; i < layers.length; i++) {
            ZonePlan layer = layers[i];
            LayerView view = view(i);
            // This runs every tick for every open planner: only a layer that was replaced or edited since the
            // last look is serialised and compared.
            if (view.seen == layer && view.seenMod == layer.modCount()) {
                continue;
            }
            view.seen = layer;
            view.seenMod = layer.modCount();
            byte[] current = serialize(layer);
            if (!Arrays.equals(current, view.sent)) {
                view.sent = current;
                final int idx = i;
                sendMessage(NET_SYNC_LAYER, buf -> {
                    buf.writeByte(idx);
                    buf.writeBytes(current);
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
            if (tile != null && idx < tile.layers.length) {
                receivePaint(idx, buffer);
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

    /**
     * Server side of a paint: the client sent its whole copy of one layer. That is untrusted input, so it is
     * read with a size cap, and only its chunks inside the survey square are taken (the layer's own chunks
     * beyond it, e.g. a zone read in from a far-away map location, are kept as they were).
     *
     * <p>The painter's client already shows what it sent, so when nothing had to be altered the stored bytes are
     * recorded as already sent to this viewer: the per-tick diff then does not push the layer back at them. An
     * echo would land a round trip late, wiping any newer stroke they have drawn meanwhile until the next sync.
     * Only this menu's view is touched; every other open planner still receives the change. When the server
     * stored something other than what was sent (clamped, or the message was refused), the painter IS resynced
     * to the real layer.
     */
    private void receivePaint(int idx, PacketBufferBC buffer) {
        LayerView view = view(idx);
        ZonePlan received = new ZonePlan();
        if (!received.readFromByteBufBounded(buffer)) {
            BCLog.logger.warn("[robotics.zone] Refused a malformed or oversized layer from a Zone Planner viewer");
            view.forget();
            return;
        }
        ZonePlan stored = ZonePlan.clampToSurvey(received, tile.layers[idx]);
        tile.layers[idx] = stored;
        tile.setChanged();
        if (stored.sameContent(received)) {
            view.markSent(stored, serialize(stored));
        } else {
            view.forget();
        }
    }
}
