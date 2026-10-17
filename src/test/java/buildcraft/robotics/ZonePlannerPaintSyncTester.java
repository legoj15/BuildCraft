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
 * Game tests for the Zone Planner's layer sync: the server side of {@code NET_PAINT_RECT} (a viewer's stroke
 * coming up), its {@code NET_PAINT_ACK}, and the per-tick {@code NET_SYNC_LAYER} diff (the tile's layers going
 * back down). The menu is driven the way the network drives it &mdash; a captured {@code paintRect} message fed
 * to {@code readMessage} &mdash; but with a recording subclass, because a mock player is not a {@code
 * ServerPlayer} and the real {@code sendMessage} would silently drop everything. The same menu object also plays
 * its viewer's client: messages it recorded for the client can be delivered to its client-side reader, which
 * lands layers in {@link RecordingMenu#clientLayers} (never in the shared server tile). The tests never wait for
 * a tick: both halves of the protocol are plain method calls on the menu, so there is no timing to gate on.
 */
public class ZonePlannerPaintSyncTester {

    private static final int LAYER = 3;

    /** A server-side menu that records every outgoing container message instead of sending it. */
    static final class RecordingMenu extends ContainerZonePlanner {
        record Sent(int id, byte[] bytes) {
            /** The layer index a layer sync leads with. */
            int layer() {
                return bytes[0] & 0xFF;
            }
        }

        final List<Sent> sent = new ArrayList<>();
        /** This viewer's client copy of each layer, as its own strokes and the syncs delivered so far left it. */
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
        protected ZonePlan clientLayer(int index) {
            return clientLayers[index] == null ? new ZonePlan() : clientLayers[index];
        }

        @Override
        protected void applySyncedLayer(int index, ZonePlan layer) {
            clientLayers[index] = layer;
        }

        /** The layer-sync messages pushed down for one layer and not yet delivered. */
        long syncsFor(int layer) {
            return sent.stream().filter(m -> m.id() == NET_SYNC_LAYER && m.layer() == layer).count();
        }

        /** Delivers every recorded server-to-client message (syncs and acks) in order, and drops them. */
        void deliverToClient() {
            for (Iterator<Sent> it = sent.iterator(); it.hasNext(); ) {
                Sent m = it.next();
                if (m.id() == NET_SYNC_LAYER || m.id() == NET_PAINT_ACK) {
                    PacketBufferBC buf = new PacketBufferBC(Unpooled.wrappedBuffer(m.bytes()));
                    readMessage(m.id(), buf, true, null);
                    buf.release();
                    it.remove();
                }
            }
        }

        /** The client end of a stroke: applied to this viewer's copy and captured, but not yet delivered. */
        byte[] strokeOnClient(int layer, int x0, int z0, int x1, int z1, boolean val) {
            int before = sent.size();
            paintRect(layer, x0, z0, x1, z1, val);
            return sent.remove(before).bytes();
        }

        /** A whole stroke: made on the client, handed to the server's reader. */
        void paint(int layer, int x0, int z0, int x1, int z1, boolean val) {
            deliverToServer(strokeOnClient(layer, x0, z0, x1, z1, val));
        }

        /** A one-column stroke. */
        void paintCell(int layer, int x, int z) {
            paint(layer, x, z, x, z, true);
        }

        /** Hands raw paint-message bytes to the server-side reader, as the payload handler would. */
        void deliverToServer(byte[] bytes) {
            PacketBufferBC buf = new PacketBufferBC(Unpooled.wrappedBuffer(bytes));
            readMessage(NET_PAINT_RECT, buf, false, null);
            buf.release();
        }

        static byte[] raw(Consumer<PacketBufferBC> body) {
            PacketBufferBC buf = new PacketBufferBC(Unpooled.buffer());
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
        menu.deliverToClient();
        return menu;
    }

    private static ZonePlan cells(int... xz) {
        ZonePlan plan = new ZonePlan();
        for (int i = 0; i < xz.length; i += 2) {
            plan.set(xz[i], xz[i + 1], true);
        }
        return plan;
    }

    /** A viewer's own stroke is not sent straight back to them (it would cost a whole layer per stroke), and
     *  every stroke is acknowledged, so the client's pending list drains. */
    public static void paintIsNotEchoedToItsOriginator(GameTestHelper helper) {
        TileZonePlanner tile = placePlanner(helper);
        RecordingMenu fresh = new RecordingMenu(helper.makeMockPlayer(GameType.SURVIVAL), tile);
        fresh.broadcastChanges();
        helper.assertTrue(fresh.syncsFor(LAYER) == 1, "a fresh viewer gets the layer once (initial sync)");

        RecordingMenu menu = openViewer(helper, tile);
        menu.paintCell(LAYER, 1, 1);
        helper.assertTrue(menu.clientLayers[LAYER].get(1, 1), "the painter shows the stroke at once");
        helper.assertTrue(tile.layers[LAYER].get(1, 1), "the server stores the viewer's stroke");

        menu.broadcastChanges();
        helper.assertTrue(menu.syncsFor(LAYER) == 0,
                "the painter is not echoed their own stroke (found " + menu.syncsFor(LAYER) + " sync messages)");

        // A second stroke in a row, as quick successive drags produce: still no echo of either.
        menu.paintCell(LAYER, 2, 2);
        menu.broadcastChanges();
        helper.assertTrue(menu.syncsFor(LAYER) == 0, "a follow-up stroke is not echoed either");
        helper.assertTrue(tile.layers[LAYER].get(2, 2), "the follow-up stroke is stored");

        helper.assertTrue(menu.pendingStrokes() == 2, "baseline: both strokes await their acks");
        menu.deliverToClient();
        helper.assertTrue(menu.pendingStrokes() == 0, "both strokes were acknowledged");
        helper.assertTrue(menu.clientLayers[LAYER].sameContent(tile.layers[LAYER]), "and the painter matches the server");

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

        painter.paintCell(LAYER, 4, 4);
        painter.broadcastChanges();
        watcher.broadcastChanges();

        helper.assertTrue(painter.syncsFor(LAYER) == 0, "the painter is not echoed");
        helper.assertTrue(watcher.syncsFor(LAYER) == 1, "the other viewer is sent the new layer");
        watcher.deliverToClient();
        helper.assertTrue(watcher.clientLayers[LAYER].sameContent(tile.layers[LAYER]),
                "and ends up holding what the server holds");
        helper.succeed();
    }

    /**
     * Two viewers paint the same layer within one round trip. B's stroke lands first and its sync to A is still
     * in flight when A's stroke, made on the layer as A knew it, arrives. Both strokes must survive, on the
     * server and on both screens: A's stroke is applied to the server's layer (which has B's), and A's client
     * re-applies its unacknowledged stroke on top of the late sync of B's.
     */
    public static void concurrentStrokesAreBothKept(GameTestHelper helper) {
        TileZonePlanner tile = placePlanner(helper);
        RecordingMenu a = openViewer(helper, tile);
        RecordingMenu b = openViewer(helper, tile);

        b.paintCell(LAYER, 1, 1);
        a.broadcastChanges();
        helper.assertTrue(a.syncsFor(LAYER) == 1, "baseline: B's stroke is sent to A (and is now in flight)");

        a.paintCell(LAYER, 2, 2);
        helper.assertTrue(tile.layers[LAYER].get(1, 1), "the server keeps B's stroke");
        helper.assertTrue(tile.layers[LAYER].get(2, 2), "and has A's");

        a.broadcastChanges();
        a.deliverToClient(); // the late sync of B's stroke, then the ack of A's
        helper.assertTrue(a.clientLayers[LAYER].get(1, 1) && a.clientLayers[LAYER].get(2, 2),
                "A shows both strokes");
        helper.assertTrue(a.clientLayers[LAYER].sameContent(tile.layers[LAYER]), "A matches the server");

        b.broadcastChanges();
        b.deliverToClient();
        helper.assertTrue(b.clientLayers[LAYER].sameContent(tile.layers[LAYER]), "B is sent A's stroke and matches too");
        helper.succeed();
    }

    /**
     * A stroke that crosses a sync while the button is held: A presses, B's stroke is synced to A during the
     * hold, A releases. The stroke is applied to the layer as it is at release, so B's stroke stays on A's screen
     * and on the server.
     */
    public static void strokeAcrossASyncKeepsTheSync(GameTestHelper helper) {
        TileZonePlanner tile = placePlanner(helper);
        RecordingMenu a = openViewer(helper, tile);
        RecordingMenu b = openViewer(helper, tile);

        // A presses here (nothing is sent before the release). Meanwhile B paints and the sync reaches A.
        b.paintCell(LAYER, 1, 1);
        a.broadcastChanges();
        a.deliverToClient();
        helper.assertTrue(a.clientLayers[LAYER].get(1, 1), "baseline: A has received B's stroke during the hold");

        a.paint(LAYER, 2, 2, 3, 3, true); // release
        helper.assertTrue(tile.layers[LAYER].get(1, 1) && tile.layers[LAYER].get(3, 3), "the server holds both");
        helper.assertTrue(a.clientLayers[LAYER].sameContent(tile.layers[LAYER]), "A shows exactly what the server holds");
        a.broadcastChanges();
        helper.assertTrue(a.syncsFor(LAYER) == 0, "A, who was in step, is not resynced");
        helper.succeed();
    }

    /** A stroke made after an earlier one was answered by a sync, but before that sync arrives, is not wiped by
     *  it: the client lays its unacknowledged strokes over every sync it receives. */
    public static void newerLocalStrokeSurvivesALateSync(GameTestHelper helper) {
        TileZonePlanner tile = placePlanner(helper);
        RecordingMenu a = openViewer(helper, tile);
        RecordingMenu b = openViewer(helper, tile);

        a.paintCell(LAYER, 1, 1);
        b.paintCell(LAYER, 5, 5);
        a.broadcastChanges();
        helper.assertTrue(a.syncsFor(LAYER) == 1, "baseline: B's stroke makes the server push the layer to A");

        byte[] later = a.strokeOnClient(LAYER, 2, 2, 2, 2, true); // drawn while that push is in flight
        a.deliverToClient();
        helper.assertTrue(a.clientLayers[LAYER].get(2, 2), "A's newer stroke is still on A's screen");
        helper.assertTrue(a.clientLayers[LAYER].get(1, 1) && a.clientLayers[LAYER].get(5, 5),
                "alongside A's first stroke and B's");

        a.deliverToServer(later);
        a.broadcastChanges();
        a.deliverToClient();
        helper.assertTrue(a.clientLayers[LAYER].sameContent(tile.layers[LAYER]), "and A ends up matching the server");
        helper.assertTrue(a.pendingStrokes() == 0, "with nothing left pending");
        helper.succeed();
    }

    // ── What a stroke may reach ─────────────────────────────────────────────────────────────

    /** Tile-relative block x of a chunk (1000) far outside the survey square. */
    private static final int FAR = 16_000;

    /** A zone read in from a map location may sit far outside the survey square; no stroke can reach it, so
     *  even an erase across everything keeps it, and a stroke out there paints nothing. */
    public static void farChunksAreUntouchedByStrokes(GameTestHelper helper) {
        TileZonePlanner tile = placePlanner(helper);
        tile.layers[LAYER] = cells(FAR, 0, 3, 3);
        RecordingMenu menu = openViewer(helper, tile);

        menu.paint(LAYER, -FAR, 40, -FAR + 5, 45, true);
        helper.assertFalse(tile.layers[LAYER].get(-FAR, 40), "a stroke beyond the square paints nothing");

        menu.paint(LAYER, -FAR * 2, -FAR * 2, FAR * 2, FAR * 2, false);
        ZonePlan stored = tile.layers[LAYER];
        helper.assertFalse(stored.get(3, 3), "the erase clears inside the square");
        helper.assertTrue(stored.get(FAR, 0), "the layer's far chunk is preserved");

        menu.paint(LAYER, -FAR * 2, -FAR * 2, FAR * 2, FAR * 2, true);
        helper.assertTrue(tile.layers[LAYER].get(ZonePlan.SURVEY_MAX_BLOCK, ZonePlan.SURVEY_MIN_BLOCK),
                "a stroke across everything fills the square to its edge");
        helper.assertFalse(tile.layers[LAYER].get(ZonePlan.SURVEY_MAX_BLOCK + 1, 0), "and stops there");
        menu.broadcastChanges();
        menu.deliverToClient();
        helper.assertTrue(menu.clientLayers[LAYER].sameContent(tile.layers[LAYER]), "the painter matches the server");
        helper.succeed();
    }

    /** A message cut short is ignored: nothing is stored, the stroke is still acknowledged, and the painter,
     *  who is showing it, is then resynced to the layer the server kept. */
    public static void truncatedPaintIsRejected(GameTestHelper helper) {
        TileZonePlanner tile = placePlanner(helper);
        tile.layers[LAYER] = cells(1, 1);
        RecordingMenu menu = openViewer(helper, tile);

        byte[] full = menu.strokeOnClient(LAYER, 0, 0, 4, 4, true);
        byte[] cut = java.util.Arrays.copyOf(full, full.length - 6);
        menu.deliverToServer(cut);

        helper.assertTrue(tile.layers[LAYER].sameContent(cells(1, 1)), "the layer is unchanged by a truncated message");
        menu.broadcastChanges();
        helper.assertTrue(menu.syncsFor(LAYER) == 1, "the painter is resynced");
        menu.deliverToClient();
        helper.assertTrue(menu.pendingStrokes() == 0, "the stroke was acknowledged");
        helper.assertTrue(menu.clientLayers[LAYER].sameContent(cells(1, 1)), "and the painter shows the real layer");

        menu.deliverToServer(RecordingMenu.raw(buf -> {})); // empty: not even a layer index
        helper.assertTrue(tile.layers[LAYER].sameContent(cells(1, 1)), "an empty message is ignored too");
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
