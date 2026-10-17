/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

import io.netty.buffer.Unpooled;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;

import buildcraft.lib.net.IPayloadWriter;
import buildcraft.lib.net.PacketBufferBC;
import buildcraft.robotics.container.ContainerZonePlanner;
import buildcraft.robotics.tile.TileZonePlanner;
import buildcraft.robotics.zone.ZonePlan;

/**
 * Game tests for the Zone Planner's layer sync: the server side of {@code NET_PAINT_LAYER} (a viewer's stroke
 * coming up) and the per-tick {@code NET_SYNC_LAYER} diff (the tile's layers going back down). The menu is
 * driven the way the network drives it &mdash; a captured {@code sendPaint} message fed to {@code readMessage}
 * &mdash; but with a recording subclass, because a mock player is not a {@code ServerPlayer} and the real
 * {@code sendMessage} would silently drop everything. The same menu object also plays its viewer's client: the
 * syncs it recorded can be delivered to its client-side reader, which lands them in {@link
 * RecordingMenu#clientLayers} (never in the shared server tile) and records the revision the next paint is based
 * on. The tests never wait for a tick: both halves of the protocol are plain method calls on the menu, so there
 * is no timing to gate on.
 */
public class ZonePlannerPaintSyncTester {

    private static final int LAYER = 3;

    /** A server-side menu that records every outgoing container message instead of sending it. */
    static final class RecordingMenu extends ContainerZonePlanner {
        record Sent(int id, byte[] bytes) {
            /** The layer index every layer message leads with. */
            int layer() {
                return bytes[0] & 0xFF;
            }
        }

        final List<Sent> sent = new ArrayList<>();
        /** This viewer's client copy of each layer, as the syncs delivered so far left it. */
        final ZonePlan[] clientLayers;

        RecordingMenu(Player player, TileZonePlanner tile) {
            super(0, player.getInventory(), tile);
            clientLayers = new ZonePlan[tile.layers.length];
        }

        @Override
        public void sendMessage(int id, IPayloadWriter writer) {
            PacketBufferBC buf = new PacketBufferBC(Unpooled.buffer());
            writer.write(buf);
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            buf.release();
            sent.add(new Sent(id, bytes));
        }

        @Override
        protected void applySyncedLayer(int index, ZonePlan layer) {
            clientLayers[index] = layer;
        }

        /** The layer-sync messages pushed down for one layer since the last {@code sent.clear()}. */
        long syncsFor(int layer) {
            return sent.stream().filter(m -> m.id() == NET_SYNC_LAYER && m.layer() == layer).count();
        }

        /** Delivers every recorded sync to this viewer's client, in the order they were sent, and drops them. */
        void deliverSyncs() {
            for (Iterator<Sent> it = sent.iterator(); it.hasNext(); ) {
                Sent m = it.next();
                if (m.id() == NET_SYNC_LAYER) {
                    PacketBufferBC buf = new PacketBufferBC(Unpooled.wrappedBuffer(m.bytes()));
                    readMessage(m.id(), buf, true, null);
                    buf.release();
                    it.remove();
                }
            }
        }

        /** Plays the client end of a paint: {@code sendPaint} captured, then handed to the server's reader. */
        void paint(int layer, ZonePlan plan) {
            int before = sent.size();
            sendPaint(layer, plan);
            Sent paint = sent.remove(before);
            deliver(paint.id(), paint.bytes());
        }

        /** Hands raw message bytes to the server-side reader, as the payload handler would. */
        void deliver(int id, byte[] bytes) {
            PacketBufferBC buf = new PacketBufferBC(Unpooled.wrappedBuffer(bytes));
            readMessage(id, buf, false, null);
            buf.release();
        }

        /** A hand-built paint message: the header {@code sendPaint} writes, then whatever {@code body} writes. */
        static byte[] rawPaint(int layer, int baseRevision, Consumer<PacketBufferBC> body) {
            PacketBufferBC buf = new PacketBufferBC(Unpooled.buffer());
            buf.writeByte(layer);
            buf.writeVarInt(baseRevision);
            body.accept(buf);
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            buf.release();
            return bytes;
        }
    }

    private static TileZonePlanner placePlanner(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, BCRoboticsBlocks.ZONE_PLANNER.get());
        //? if >=1.21.10 {
        return helper.getBlockEntity(pos, TileZonePlanner.class);
        //?} else {
        /*return helper.getBlockEntity(pos);*/
        //?}
    }

    /** A viewer whose client has received the initial sync of every layer, as one that just opened the GUI. */
    private static RecordingMenu openViewer(GameTestHelper helper, TileZonePlanner tile) {
        RecordingMenu menu = new RecordingMenu(helper.makeMockPlayer(GameType.SURVIVAL), tile);
        menu.broadcastChanges();
        menu.deliverSyncs();
        menu.sent.clear();
        return menu;
    }

    private static ZonePlan cells(int... xz) {
        ZonePlan plan = new ZonePlan();
        for (int i = 0; i < xz.length; i += 2) {
            plan.set(xz[i], xz[i + 1], true);
        }
        return plan;
    }

    /** A viewer's own stroke must not come straight back down to them: the echo would overwrite any newer
     *  stroke they have already drawn locally while the first was in flight. */
    public static void paintIsNotEchoedToItsOriginator(GameTestHelper helper) {
        TileZonePlanner tile = placePlanner(helper);
        RecordingMenu fresh = new RecordingMenu(helper.makeMockPlayer(GameType.SURVIVAL), tile);
        fresh.broadcastChanges();
        helper.assertTrue(fresh.syncsFor(LAYER) == 1, "a fresh viewer gets the layer once (initial sync)");

        RecordingMenu menu = openViewer(helper, tile);
        menu.paint(LAYER, cells(1, 1));
        helper.assertTrue(tile.layers[LAYER].get(1, 1), "the server stores the viewer's stroke");

        menu.broadcastChanges();
        helper.assertTrue(menu.syncsFor(LAYER) == 0,
                "the painter is not echoed their own stroke (found " + menu.syncsFor(LAYER) + " sync messages)");

        // A second stroke in a row, as quick successive drags produce: still no echo of either.
        menu.paint(LAYER, cells(1, 1, 2, 2));
        menu.broadcastChanges();
        helper.assertTrue(menu.syncsFor(LAYER) == 0, "a follow-up stroke is not echoed either");
        helper.assertTrue(tile.layers[LAYER].get(2, 2), "the follow-up stroke is stored");

        // Other layers keep working: a change the viewer did NOT make still goes down to them.
        tile.layers[LAYER + 1] = cells(5, 5);
        menu.broadcastChanges();
        helper.assertTrue(menu.syncsFor(LAYER + 1) == 1, "a layer changed behind the viewer's back is still pushed");
        helper.succeed();
    }

    /** The echo suppression is per viewer: everyone else with the planner open still receives the stroke. */
    public static void paintStillReachesOtherViewers(GameTestHelper helper) {
        TileZonePlanner tile = placePlanner(helper);
        RecordingMenu painter = openViewer(helper, tile);
        RecordingMenu watcher = openViewer(helper, tile);

        painter.paint(LAYER, cells(4, 4));
        painter.broadcastChanges();
        watcher.broadcastChanges();

        helper.assertTrue(painter.syncsFor(LAYER) == 0, "the painter is not echoed");
        helper.assertTrue(watcher.syncsFor(LAYER) == 1, "the other viewer is sent the new layer");
        watcher.deliverSyncs();
        helper.assertTrue(watcher.clientLayers[LAYER].sameContent(tile.layers[LAYER]),
                "and ends up holding what the server holds");
        helper.succeed();
    }

    /**
     * Two viewers paint the same layer within one round trip. B's stroke lands first and its sync to A is still
     * in flight when A's stroke, drawn on the layer as A knew it, arrives. A's paint was not based on what the
     * server held, so A must be resynced: suppressing that echo would let the late sync of B's stroke overwrite
     * A's view while the server holds A's stroke, and A's stroke would vanish from A's screen for good.
     */
    public static void paintOnAStaleBaseIsResynced(GameTestHelper helper) {
        TileZonePlanner tile = placePlanner(helper);
        RecordingMenu a = openViewer(helper, tile);
        RecordingMenu b = openViewer(helper, tile);

        b.paint(LAYER, cells(1, 1));
        a.broadcastChanges();
        helper.assertTrue(a.syncsFor(LAYER) == 1, "baseline: B's stroke is sent to A (and is now in flight)");

        ZonePlan aStroke = new ZonePlan(a.clientLayers[LAYER]);
        aStroke.set(2, 2, true);
        a.paint(LAYER, aStroke);
        a.broadcastChanges();
        helper.assertTrue(a.syncsFor(LAYER) == 2,
                "A, who painted over a layer the server had moved past, is resynced (found "
                        + a.syncsFor(LAYER) + " syncs)");

        a.deliverSyncs(); // the stale sync of B's stroke lands first, then the resync
        helper.assertTrue(a.clientLayers[LAYER].sameContent(tile.layers[LAYER]),
                "A ends up showing what the server holds");
        helper.assertTrue(a.clientLayers[LAYER].get(2, 2), "which has A's stroke");

        // Back in step, A's next stroke is suppressed again.
        ZonePlan next = new ZonePlan(a.clientLayers[LAYER]);
        next.set(3, 3, true);
        a.paint(LAYER, next);
        a.broadcastChanges();
        helper.assertTrue(a.syncsFor(LAYER) == 0, "a paint on the current revision is not echoed");
        helper.succeed();
    }

    // ── Bounds on what a client may send ────────────────────────────────────────────────────

    /** Tile-relative block x of a chunk (1000) far outside the survey square. */
    private static final int FAR = 16_000;

    /** A client sends only the survey square, so the chunks it holds beyond it (a zone read in from a map
     *  location, handed down with the layer) never travel up and cannot be lost or rewritten by a paint. */
    public static void farChunksStayOnTheServer(GameTestHelper helper) {
        TileZonePlanner tile = placePlanner(helper);
        tile.layers[LAYER] = cells(FAR, 0);
        RecordingMenu menu = openViewer(helper, tile);

        ZonePlan local = new ZonePlan(menu.clientLayers[LAYER]);
        local.set(3, 3, true);
        local.set(-FAR, 40, true); // not something the viewport can draw, but it must not travel either
        menu.paint(LAYER, local);

        ZonePlan stored = tile.layers[LAYER];
        helper.assertTrue(stored.get(3, 3), "the stroke inside the square is stored");
        helper.assertFalse(stored.get(-FAR, 40), "a chunk beyond the square is not sent");
        helper.assertTrue(stored.get(FAR, 0), "the layer's own far chunk is preserved");
        menu.broadcastChanges();
        helper.assertTrue(menu.syncsFor(LAYER) == 0, "an ordinary paint is not echoed to its painter");
        helper.succeed();
    }

    /** A message that does carry far chunks (an old or hostile client) has them dropped, keeps the layer's own,
     *  and resyncs the painter, who may be showing what the server refused. */
    public static void paintBeyondSurveyRangeIsClamped(GameTestHelper helper) {
        TileZonePlanner tile = placePlanner(helper);
        tile.layers[LAYER] = cells(FAR, 0);
        RecordingMenu menu = openViewer(helper, tile);

        ZonePlan sent = cells(3, 3, -FAR, 40);
        menu.deliver(ContainerZonePlanner.NET_PAINT_LAYER,
                RecordingMenu.rawPaint(LAYER, menu.clientRevision(LAYER), sent::writeToByteBuf));

        ZonePlan stored = tile.layers[LAYER];
        helper.assertTrue(stored.get(3, 3), "the stroke inside the square is stored");
        helper.assertFalse(stored.get(-FAR, 40), "a chunk added far outside the square is dropped");
        helper.assertTrue(stored.get(FAR, 0), "the layer's own far chunk is preserved");

        menu.broadcastChanges();
        helper.assertTrue(menu.syncsFor(LAYER) == 1,
                "the painter is sent the clamped layer, which differs from what they sent");
        helper.succeed();
    }

    /**
     * Far chunks accumulate without limit (each map location read in replaces the layer with its whole zone), and
     * they must never count against the paint cap: three full squares' worth beyond the survey range, then an
     * ordinary stroke. Were the client to send them back up, the message would pass the cap and every paint of
     * the layer would be refused.
     */
    public static void farChunksNeverBlockPainting(GameTestHelper helper) {
        TileZonePlanner tile = placePlanner(helper);
        ZonePlan layer = cells(1, 1);
        int far = 3 * ZonePlan.MAX_CHUNKS;
        for (int i = 0; i < far; i++) {
            layer.set(FAR + ((i % 300) << 4), (i / 300) << 4, true);
        }
        tile.layers[LAYER] = layer;
        RecordingMenu menu = openViewer(helper, tile);

        ZonePlan local = new ZonePlan(menu.clientLayers[LAYER]);
        local.set(2, 2, true);
        menu.paint(LAYER, local);

        ZonePlan stored = tile.layers[LAYER];
        helper.assertTrue(stored.get(2, 2), "the stroke is stored");
        helper.assertTrue(stored.getChunkKeys().size() == far + 1, "every far chunk is kept");
        menu.broadcastChanges();
        helper.assertTrue(menu.syncsFor(LAYER) == 0, "the paint was accepted, not refused and resynced");
        helper.succeed();
    }

    /** A message declaring more chunks than the survey square holds is refused outright: nothing is stored, and
     *  the painter, who may be showing the rejected layer, is resynced to the real one. */
    public static void oversizedPaintIsRejected(GameTestHelper helper) {
        TileZonePlanner tile = placePlanner(helper);
        tile.layers[LAYER] = cells(1, 1);
        ZonePlan before = tile.layers[LAYER];
        RecordingMenu menu = openViewer(helper, tile);

        // One more entry than the square holds, each a distinct full chunk.
        int count = ZonePlan.MAX_CHUNKS + 1;
        byte[] bytes = RecordingMenu.rawPaint(LAYER, menu.clientRevision(LAYER), buf -> {
            buf.writeInt(count);
            for (int i = 0; i < count; i++) {
                buf.writeInt(1000 + i % 200);
                buf.writeInt(1000 + i / 200);
                buf.writeByte(2); // fullSet, no bit array
            }
        });
        menu.deliver(ContainerZonePlanner.NET_PAINT_LAYER, bytes);

        helper.assertTrue(tile.layers[LAYER] == before && before.get(1, 1) && !before.get(16_000, 16_000),
                "the layer is unchanged by an oversized message");

        menu.broadcastChanges();
        helper.assertTrue(menu.syncsFor(LAYER) == 1, "the painter is resynced to the layer the server kept");
        helper.succeed();
    }

    /** A message cut short mid-chunk is a rejection too, not a half-stored layer. */
    public static void truncatedPaintIsRejected(GameTestHelper helper) {
        TileZonePlanner tile = placePlanner(helper);
        tile.layers[LAYER] = cells(1, 1);
        ZonePlan before = tile.layers[LAYER];
        RecordingMenu menu = openViewer(helper, tile);

        byte[] bytes = RecordingMenu.rawPaint(LAYER, menu.clientRevision(LAYER), buf -> {
            buf.writeInt(3); // promises three chunks...
            buf.writeInt(0);
            buf.writeInt(0);
            buf.writeByte(2); // ...delivers one
        });
        menu.deliver(ContainerZonePlanner.NET_PAINT_LAYER, bytes);

        helper.assertTrue(tile.layers[LAYER] == before && !before.get(0, 0),
                "the layer is unchanged by a truncated message");
        menu.broadcastChanges();
        helper.assertTrue(menu.syncsFor(LAYER) == 1, "the painter is resynced");
        helper.succeed();
    }

    // ── Cost of the per-tick diff ───────────────────────────────────────────────────────────

    /** A plan that counts how often it is serialised. */
    private static final class CountingPlan extends ZonePlan {
        int writes;

        @Override
        public void writeToByteBuf(net.minecraft.network.FriendlyByteBuf buf) {
            writes++;
            super.writeToByteBuf(buf);
        }
    }

    /** {@code broadcastChanges} runs every tick for every open planner; a layer nobody touched must cost
     *  nothing (no re-serialising), while a change still goes down. */
    public static void unchangedLayersAreNotReserialisedEveryTick(GameTestHelper helper) {
        TileZonePlanner tile = placePlanner(helper);
        CountingPlan counting = new CountingPlan();
        counting.set(1, 1, true);
        tile.layers[LAYER] = counting;
        RecordingMenu menu = new RecordingMenu(helper.makeMockPlayer(GameType.SURVIVAL), tile);

        menu.broadcastChanges(); // the initial sync serialises it
        int afterFirst = counting.writes;
        helper.assertTrue(afterFirst > 0, "baseline: the first broadcast serialised the layer");

        for (int tick = 0; tick < 20; tick++) {
            menu.broadcastChanges();
        }
        helper.assertTrue(counting.writes == afterFirst,
                "20 idle ticks re-serialised the layer " + (counting.writes - afterFirst) + " times");

        menu.sent.clear();
        counting.set(2, 2, true);
        menu.broadcastChanges();
        helper.assertTrue(menu.syncsFor(LAYER) == 1, "a real change is still pushed");
        helper.succeed();
    }
}
