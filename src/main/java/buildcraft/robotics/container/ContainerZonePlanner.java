/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.robotics.container;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
    // The viewport edits zone layers client-side; the server tile owns them (the slot I/O reads them) and
    // persists them. A stroke goes up as the edit itself, NET_PAINT_RECT (a rectangle and whether it marks or
    // clears), which the server applies to ITS copy of the layer, so strokes from several viewers all land
    // whatever order they arrive in. The server's per-tick diff (broadcastChanges) pushes any changed layer down
    // via NET_SYNC_LAYER, covering viewport paints and the paintbrush/map-location slot transfers alike.
    //
    // The painter shows its stroke at once, before the server has seen it. Every NET_PAINT_RECT is answered with
    // NET_PAINT_ACK (its sequence number); until then the stroke is pending, and the client re-applies its pending
    // strokes on top of each NET_SYNC_LAYER it receives. A sync sent before the server applied the stroke would
    // otherwise wipe it off the painter's screen, and one sent after it already contains it. The channel is
    // ordered, so a sync that arrives after the ack always contains the stroke.
    //
    // A layer push is the whole layer, so one viewer painting continuously must not make the server push it to every
    // other viewer every tick. Two limits bound that: a viewer's stroke messages go through a PaintRateLimiter (the
    // surplus is acknowledged, dropped, and the painter resynced, as for a malformed message), and a layer is pushed
    // to a given viewer at most once per PUSH_INTERVAL_TICKS, the strokes that land in between going down together.
    public static final int NET_PAINT_RECT = 10;
    public static final int NET_SYNC_LAYER = 11;
    public static final int NET_PAINT_ACK = 12;

    /** Server side: the fewest ticks between two pushes of the same layer to one viewer (twice a second). A change
     *  that lands sooner than that after the last push waits for the interval to end, and everything that changed
     *  meanwhile goes down in that one push; so a viewer painting continuously costs every other viewer one layer
     *  push per interval, not one per tick. The first change after a quiet spell is pushed at once. */
    public static final int PUSH_INTERVAL_TICKS = 10;

    /** Server side: what this viewer's menu knows about one layer: the instance and modification count it last
     *  examined (so an untouched layer costs two compares per tick, not a serialisation), and the bytes last
     *  pushed to this viewer's client ({@code null} when unknown, which makes the next change push). */
    private static final class LayerView {
        /** {@link #lastPush} before anything has been pushed: far enough back never to count as recent. */
        static final long NEVER = Long.MIN_VALUE / 2;

        ZonePlan seen;
        int seenMod;
        byte[] sent;
        /** The game time of the last push of this layer to this viewer. */
        long lastPush = NEVER;

        /** True when this viewer has been pushed (or already holds) the layer exactly as the server has it. */
        boolean isUpToDate(ZonePlan layer) {
            return seen == layer && seenMod == layer.modCount();
        }

        /** True when the layer was pushed to this viewer less than {@link #PUSH_INTERVAL_TICKS} ticks before {@code now}. */
        boolean pushedRecently(long now) {
            long since = now - lastPush;
            return since >= 0 && since < PUSH_INTERVAL_TICKS;
        }

        /** Forces the next diff to push the layer, whatever it holds. */
        void forget() {
            seen = null;
            sent = null;
        }
    }

    /** Client side: a stroke shown locally but not yet acknowledged by the server. */
    private record PendingStroke(int seq, int layer, int x0, int z0, int x1, int z1, boolean val) {
        void applyTo(ZonePlan plan) {
            plan.paintRect(x0, z0, x1, z1, val);
        }
    }

    /** Per-viewer, per-layer sync state (server side); created on first use. */
    private LayerView[] views;
    /** Server side: this viewer's allowance of stroke messages (see {@link PaintRateLimiter}). */
    private final PaintRateLimiter paintBudget = new PaintRateLimiter();
    /** Client side: strokes sent but not yet acknowledged, oldest first. */
    private final List<PendingStroke> pending = new ArrayList<>();
    /** Client side: the sequence number of the last stroke sent. */
    private int lastSeq;

    private LayerView view(int index) {
        if (views == null) {
            views = new LayerView[tile.layers.length];
        }
        if (views[index] == null) {
            views[index] = new LayerView();
        }
        return views[index];
    }

    /**
     * Client side: paints (or, with {@code val == false}, erases) the tile-relative block rectangle between two
     * corners on one layer: on this client's copy at once, and on the server's by message. Both clip it to the
     * survey square ({@link ZonePlan#paintRect}). It is applied to the layer as it is NOW, not as it was when the
     * stroke began, so a sync that landed while the button was held is kept.
     */
    public void paintRect(int index, int x0, int z0, int x1, int z1, boolean val) {
        PendingStroke stroke = new PendingStroke(++lastSeq, index, x0, z0, x1, z1, val);
        pending.add(stroke);
        ZonePlan local = new ZonePlan(clientLayer(index));
        stroke.applyTo(local);
        applySyncedLayer(index, local);
        sendMessage(NET_PAINT_RECT, buf -> {
            buf.writeByte(index);
            buf.writeVarInt(stroke.seq());
            buf.writeInt(x0);
            buf.writeInt(z0);
            buf.writeInt(x1);
            buf.writeInt(z1);
            buf.writeBoolean(val);
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
            if (view.isUpToDate(layer)) {
                continue;
            }
            // A push is a whole layer, so a layer that keeps changing (one viewer painting continuously) is pushed at
            // most once per interval. The change is left unexamined, not dropped: a later tick finds the layer still
            // out of date and sends it as it then stands, with every stroke that landed in between.
            long now = gameTime();
            if (view.pushedRecently(now)) {
                continue;
            }
            view.seen = layer;
            view.seenMod = layer.modCount();
            byte[] current = serialize(layer);
            if (!Arrays.equals(current, view.sent)) {
                view.sent = current;
                view.lastPush = now;
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
        if (id == NET_PAINT_RECT && !isClient) {
            receivePaint(buffer);
            return;
        }
        if (id == NET_SYNC_LAYER && isClient) {
            int idx = buffer.readUnsignedByte();
            if (tile != null && idx < tile.layers.length) {
                ZonePlan plan = new ZonePlan();
                plan.readFromByteBuf(buffer);
                for (PendingStroke stroke : pending) {
                    if (stroke.layer() == idx) {
                        stroke.applyTo(plan);
                    }
                }
                applySyncedLayer(idx, plan);
            }
            return;
        }
        if (id == NET_PAINT_ACK && isClient) {
            int seq = buffer.readVarInt();
            pending.removeIf(stroke -> stroke.seq() <= seq);
            return;
        }
        super.readMessage(id, buffer, isClient, ctx);
    }

    /** The server's game clock, in ticks. Overridable so a test can play a viewer's clock without ticking a level. */
    protected long gameTime() {
        return tile == null || tile.getLevel() == null ? 0L : tile.getLevel().getGameTime();
    }

    /** Client side: this viewer's copy of a layer. Overridable (with {@link #applySyncedLayer}) so a test can
     *  play a viewer's client without touching the server's tile. */
    protected ZonePlan clientLayer(int index) {
        ZonePlan layer = tile.layers[index];
        return layer == null ? new ZonePlan() : layer;
    }

    /** Client side: installs a layer the server pushed, or a local stroke. */
    protected void applySyncedLayer(int index, ZonePlan layer) {
        tile.layers[index] = layer;
    }

    /** Client side: strokes still awaiting the server's ack (for tests). */
    public int pendingStrokes() {
        return pending.size();
    }

    /**
     * Server side of a stroke: applies it to the server's copy of the layer, whatever has changed there since
     * the painter last heard (another viewer's stroke, a slot transfer), and acknowledges it. A malformed
     * message changes nothing and resyncs the painter, who is showing the stroke; the ack still goes first, so
     * the resync is not overlaid with it.
     *
     * <p>The painter is not sent the result when the layer was, before this stroke, exactly what was last pushed
     * to them: their copy (that push plus their pending strokes, re-applied on arrival) already equals the
     * server's. Every other open planner still receives the change.
     */
    private void receivePaint(PacketBufferBC buffer) {
        int idx;
        int seq;
        try {
            idx = buffer.readUnsignedByte();
            seq = buffer.readVarInt();
        } catch (RuntimeException malformed) {
            BCLog.logger.warn("[robotics.zone] Ignored a malformed paint message from a Zone Planner viewer");
            return;
        }
        sendMessage(NET_PAINT_ACK, buf -> buf.writeVarInt(seq));
        if (tile == null || idx >= tile.layers.length) {
            return;
        }
        LayerView view = view(idx);
        if (!paintBudget.tryAcquire(gameTime())) {
            // Faster than a person can stroke: acknowledged (above) so the painter's pending list drains, but not
            // applied, and the painter, who is showing it, is resynced to the layer the server kept.
            BCLog.logger.debug("[robotics.zone] Dropped a paint message from a Zone Planner viewer over the rate limit");
            view.forget();
            return;
        }
        int x0;
        int z0;
        int x1;
        int z1;
        boolean val;
        try {
            x0 = buffer.readInt();
            z0 = buffer.readInt();
            x1 = buffer.readInt();
            z1 = buffer.readInt();
            val = buffer.readBoolean();
        } catch (RuntimeException malformed) {
            BCLog.logger.warn("[robotics.zone] Ignored a malformed paint message from a Zone Planner viewer");
            view.forget();
            return;
        }
        ZonePlan layer = tile.layers[idx];
        boolean painterHadIt = view.isUpToDate(layer);
        if (layer.paintRect(x0, z0, x1, z1, val)) {
            tile.setChanged();
            if (painterHadIt) {
                view.seenMod = layer.modCount();
                view.sent = null;
            }
        }
    }
}
