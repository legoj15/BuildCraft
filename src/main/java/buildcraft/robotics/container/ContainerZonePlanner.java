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
    //
    // Every NET_SYNC_LAYER carries a per-viewer revision of that layer; the client sends the revision
    // its paint was drawn on with NET_PAINT_LAYER. That lets the server tell "this paint was made on
    // exactly what I hold" (no echo needed) from "this paint crossed a sync still in flight" (resync).
    public static final int NET_PAINT_LAYER = 10;
    public static final int NET_SYNC_LAYER = 11;

    /** Server side: what this viewer's menu knows about one layer: the instance and modification count it last
     *  examined (so an untouched layer costs two compares per tick, not a serialisation), the bytes last pushed
     *  to (or known to be already held by) this viewer's client, and the revision of the last push. */
    private static final class LayerView {
        ZonePlan seen;
        int seenMod;
        byte[] sent;
        int revision;

        /** Records that the client holds {@code layer} (as {@code bytes}) without a message being sent. */
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

        /** True when a paint drawn on {@code baseRevision} was drawn on exactly what the server holds now: the
         *  viewer had received the last push, and the layer has not changed since this view last looked. */
        boolean isCurrent(ZonePlan layer, int baseRevision) {
            return baseRevision == revision && seen == layer && seenMod == layer.modCount();
        }
    }

    /** Per-viewer, per-layer sync state (server side); created on first use. */
    private LayerView[] views;
    /** Client side: the revision of each layer's last NET_SYNC_LAYER, sent back with a paint as its base. */
    private int[] clientRevisions;

    private LayerView view(int index) {
        if (views == null) {
            views = new LayerView[tile.layers.length];
        }
        if (views[index] == null) {
            views[index] = new LayerView();
        }
        return views[index];
    }

    /** Client side: the revision of the last sync received for a layer (0 before the first). */
    public int clientRevision(int index) {
        return clientRevisions == null ? 0 : clientRevisions[index];
    }

    /** Client → server: a painted/erased layer. Only its chunks inside the survey square travel: the viewport
     *  cannot paint beyond it (see {@link ZonePlan#withRect}), and the server keeps its own copy of whatever the
     *  layer holds further out, so sending those back would only cost bandwidth and count against the cap. */
    public void sendPaint(int index, ZonePlan layer) {
        int base = clientRevision(index);
        sendMessage(NET_PAINT_LAYER, buf -> {
            buf.writeByte(index);
            buf.writeVarInt(base);
            layer.writeSurveyToByteBuf(buf);
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
                int revision = ++view.revision;
                final int idx = i;
                sendMessage(NET_SYNC_LAYER, buf -> {
                    buf.writeByte(idx);
                    buf.writeVarInt(revision);
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
            if (tile != null && idx < tile.layers.length) {
                int revision = buffer.readVarInt();
                ZonePlan plan = new ZonePlan();
                plan.readFromByteBuf(buffer);
                if (clientRevisions == null) {
                    clientRevisions = new int[tile.layers.length];
                }
                clientRevisions[idx] = revision;
                applySyncedLayer(idx, plan);
            }
            return;
        }
        super.readMessage(id, buffer, isClient, ctx);
    }

    /** Client side: installs a layer the server pushed. Overridable so a test can play a viewer's client without
     *  writing into the server's tile. */
    protected void applySyncedLayer(int index, ZonePlan layer) {
        tile.layers[index] = layer;
    }

    /**
     * Server side of a paint: the client sent the survey-square part of its copy of one layer, and the revision
     * that copy was drawn on. That is untrusted input, so it is read with a size cap (a full square), and only
     * its chunks inside the square are taken; the layer's own chunks beyond it (e.g. a zone read in from a
     * far-away map location) are kept as they were.
     *
     * <p>The painter's client already shows what it painted, so the layer is not pushed back at them when the
     * paint was drawn on exactly what the server held ({@link LayerView#isCurrent}) and nothing had to be
     * dropped: the stored bytes are recorded as already sent. An echo would land a round trip late, wiping any
     * newer stroke they have drawn meanwhile until the next sync. Only this menu's view is touched; every other
     * open planner still receives the change. In every other case the painter IS resynced: a paint drawn on an
     * older revision (another viewer's stroke, or a slot transfer, landed first and its sync is still on the
     * way, and would otherwise overwrite this stroke on the painter's screen), a message carrying chunks beyond
     * the square, or one that was refused.
     */
    private void receivePaint(int idx, PacketBufferBC buffer) {
        LayerView view = view(idx);
        ZonePlan received = new ZonePlan();
        int base;
        try {
            base = buffer.readVarInt();
        } catch (RuntimeException malformed) {
            base = -1;
        }
        if (base < 0 || !received.readFromByteBufBounded(buffer)) {
            BCLog.logger.warn("[robotics.zone] Refused a malformed or oversized layer from a Zone Planner viewer");
            view.forget();
            return;
        }
        ZonePlan existing = tile.layers[idx];
        boolean current = view.isCurrent(existing, base);
        ZonePlan stored = ZonePlan.clampToSurvey(received, existing);
        tile.layers[idx] = stored;
        tile.setChanged();
        if (current && received.isWithinSurvey()) {
            view.markSent(stored, serialize(stored));
        } else {
            view.forget();
        }
    }
}
