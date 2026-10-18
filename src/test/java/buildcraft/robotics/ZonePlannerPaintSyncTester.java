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
import buildcraft.robotics.container.PaintRateLimiter;
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
        /** This menu's game clock. By default every {@code broadcastChanges} is a fresh moment, long after the
         *  last, so the push pacing never gets in the way of tests that are not about it; a pacing test turns
         *  {@link #autoAdvance} off and sets the clock itself. */
        long now = 1_000_000L;
        boolean autoAdvance = true;

        RecordingMenu(Player player, TileZonePlanner tile) {
            super(0, player.getInventory(), tile);
            clientLayers = new ZonePlan[tile.layers.length];
        }

        @Override
        protected long gameTime() {
            return now;
        }

        @Override
        public void broadcastChanges() {
            if (autoAdvance) {
                now += 1000;
            }
            super.broadcastChanges();
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

    // ── A viewer that paints continuously ───────────────────────────────────────────────────

    /** Block coordinates of the whole survey square: a stroke across it rewrites the entire layer. */
    private static final int SQ_MIN = ZonePlan.SURVEY_MIN_BLOCK;
    private static final int SQ_MAX = ZonePlan.SURVEY_MAX_BLOCK;

    /** A viewer's own clock, set by hand for the pacing tests (the whole fixture shares one game tick). */
    private static RecordingMenu steadyViewer(GameTestHelper helper, TileZonePlanner tile, long start) {
        RecordingMenu menu = openViewer(helper, tile);
        menu.autoAdvance = false;
        menu.now = start;
        return menu;
    }

    private static void setClock(long now, RecordingMenu... menus) {
        for (RecordingMenu menu : menus) {
            menu.now = now;
        }
    }

    /**
     * Strokes that land together are pushed to the other viewers together: six strokes in one tick are one layer
     * push, not six. A change that follows within the push interval waits for it to end rather than costing the
     * watcher another push at once, and is then sent; the first change after a quiet spell is not delayed.
     */
    public static void pushesToOtherViewersAreCoalesced(GameTestHelper helper) {
        TileZonePlanner tile = placePlanner(helper);
        RecordingMenu painter = openViewer(helper, tile);
        RecordingMenu watcher = openViewer(helper, tile);
        long start = 50_000_000L;
        painter.autoAdvance = false;
        watcher.autoAdvance = false;
        setClock(start, painter, watcher);

        for (int i = 0; i < 6; i++) {
            painter.paintCell(LAYER, i, 0);
        }
        painter.broadcastChanges();
        watcher.broadcastChanges();
        helper.assertTrue(watcher.syncsFor(LAYER) == 1,
                "six strokes in one tick, one push (found " + watcher.syncsFor(LAYER) + "); and after a quiet spell, at once");
        helper.assertTrue(painter.syncsFor(LAYER) == 0, "the painter is not echoed");
        watcher.deliverToClient();
        helper.assertTrue(watcher.clientLayers[LAYER].sameContent(tile.layers[LAYER]), "and it holds all six strokes");

        // Another stroke the very next tick: held back, not lost.
        setClock(start + 1, painter, watcher);
        painter.paintCell(LAYER, 20, 0);
        painter.broadcastChanges();
        watcher.broadcastChanges();
        helper.assertTrue(watcher.syncsFor(LAYER) == 0, "a change inside the push interval is held back");
        setClock(start + ContainerZonePlanner.PUSH_INTERVAL_TICKS - 1, painter, watcher);
        watcher.broadcastChanges();
        helper.assertTrue(watcher.syncsFor(LAYER) == 0, "until the interval has run its course");
        setClock(start + ContainerZonePlanner.PUSH_INTERVAL_TICKS, painter, watcher);
        watcher.broadcastChanges();
        helper.assertTrue(watcher.syncsFor(LAYER) == 1, "then it is sent");
        watcher.deliverToClient();
        helper.assertTrue(watcher.clientLayers[LAYER].sameContent(tile.layers[LAYER]), "complete");

        // A viewer who joins mid-stream is not made to wait: the pacing is per viewer and per layer.
        RecordingMenu late = new RecordingMenu(helper.makeMockPlayer(GameType.SURVIVAL), tile);
        late.autoAdvance = false;
        late.now = start + ContainerZonePlanner.PUSH_INTERVAL_TICKS;
        late.broadcastChanges();
        helper.assertTrue(late.syncsFor(LAYER) == 1, "a viewer's first sync is immediate");
        helper.succeed();
    }

    /**
     * One viewer streaming strokes that each rewrite the whole survey square, a stroke every other tick for 100
     * ticks, used to send every other viewer the whole layer on each of them. They are now sent it at most once
     * per push interval, and still end up holding exactly what the server holds.
     */
    public static void streamedStrokesReachWatchersAtACappedRate(GameTestHelper helper) {
        TileZonePlanner tile = placePlanner(helper);
        RecordingMenu painter = openViewer(helper, tile);
        RecordingMenu watcher = openViewer(helper, tile);
        long start = 60_000_000L;
        painter.autoAdvance = false;
        watcher.autoAdvance = false;

        boolean mark = true;
        int strokes = 0;
        for (int tick = 0; tick < 100; tick++) {
            setClock(start + tick, painter, watcher);
            if (tick % 2 == 0) {
                painter.paint(LAYER, SQ_MIN, SQ_MIN, SQ_MAX, SQ_MAX, mark);
                mark = !mark;
                strokes++;
            }
            painter.broadcastChanges();
            watcher.broadcastChanges();
        }
        long pushes = watcher.syncsFor(LAYER);
        long ceiling = 100 / ContainerZonePlanner.PUSH_INTERVAL_TICKS + 1;
        helper.assertTrue(strokes == 50, "baseline: fifty whole-layer strokes were made");
        helper.assertTrue(pushes <= ceiling,
                "the watcher is sent the layer at most " + ceiling + " times, found " + pushes);
        helper.assertTrue(pushes >= 5, "but it does keep up with the painter (found " + pushes + " pushes)");
        helper.assertTrue(painter.syncsFor(LAYER) == 0, "and the painter is never sent their own strokes back");

        // The stream stops: the watcher's last held change arrives, and it matches the server.
        for (int tick = 100; tick < 100 + ContainerZonePlanner.PUSH_INTERVAL_TICKS; tick++) {
            setClock(start + tick, painter, watcher);
            watcher.broadcastChanges();
        }
        watcher.deliverToClient();
        helper.assertTrue(watcher.clientLayers[LAYER].sameContent(tile.layers[LAYER]),
                "once the stream stops the watcher holds exactly what the server holds");
        painter.deliverToClient();
        helper.assertTrue(painter.pendingStrokes() == 0, "every stroke was acknowledged");
        helper.assertTrue(painter.clientLayers[LAYER].sameContent(tile.layers[LAYER]), "and the painter matches too");
        helper.succeed();
    }

    /**
     * A viewer sending strokes faster than any person can is cut off at a burst: the rest are acknowledged (the
     * painter's list drains) but not applied, the painter is resynced to the layer the server kept, and strokes are
     * taken again once the clock has refilled the allowance.
     */
    public static void strokesPastTheRateLimitAreDropped(GameTestHelper helper) {
        TileZonePlanner tile = placePlanner(helper);
        RecordingMenu menu = openViewer(helper, tile);
        menu.autoAdvance = false;
        long start = 70_000_000L;
        menu.now = start;

        int sent = PaintRateLimiter.BURST + 10;
        for (int i = 0; i < sent; i++) {
            menu.paintCell(LAYER, i, 0);
        }
        int stored = tile.layers[LAYER].getAll().size();
        helper.assertTrue(stored == PaintRateLimiter.BURST,
                "a burst of " + PaintRateLimiter.BURST + " strokes is taken, the rest dropped (stored " + stored + ")");
        helper.assertTrue(menu.pendingStrokes() == sent, "baseline: the painter showed all " + sent);

        menu.broadcastChanges();
        menu.deliverToClient();
        helper.assertTrue(menu.pendingStrokes() == 0, "every stroke, dropped or not, was acknowledged");
        helper.assertTrue(menu.clientLayers[LAYER].sameContent(tile.layers[LAYER]),
                "and the painter was resynced to what the server kept");

        // Time passes: the allowance refills at the sustained rate.
        menu.now = start + PaintRateLimiter.TICKS_PER_STROKE * 3;
        for (int i = 0; i < 6; i++) {
            menu.paintCell(LAYER, 100 + i, 0);
        }
        int accepted = 0;
        for (int i = 0; i < 6; i++) {
            if (tile.layers[LAYER].get(100 + i, 0)) {
                accepted++;
            }
        }
        helper.assertTrue(accepted == 3, "three strokes' worth of ticks buy three strokes (accepted " + accepted + ")");
        helper.succeed();
    }

    /**
     * The stroke allowance belongs to the viewer at that planner, not to the screen they have open: closing the planner
     * and opening it again makes a new menu, and a fresh allowance per menu let a client spend a burst, reopen, and
     * spend another. The same player at the same planner keeps one allowance across menus; another player has their own.
     */
    public static void strokeAllowanceSurvivesReopeningTheScreen(GameTestHelper helper) {
        TileZonePlanner tile = placePlanner(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        long start = 80_000_000L;

        RecordingMenu first = viewerAt(player, tile, start);
        for (int i = 0; i < PaintRateLimiter.BURST + 5; i++) {
            first.paintCell(LAYER, i, 0);
        }
        helper.assertTrue(tile.layers[LAYER].getAll().size() == PaintRateLimiter.BURST,
                "baseline: the first screen spent the whole burst (stored " + tile.layers[LAYER].getAll().size() + ")");

        // The player closes the planner and opens it again one tick later: a new menu for the same player and tile.
        RecordingMenu reopened = viewerAt(player, tile, start + 1);
        reopened.paintCell(LAYER, 100, 0);
        helper.assertFalse(tile.layers[LAYER].get(100, 0),
                "reopening the screen did not refill the allowance: the stroke is dropped");

        // Time still refills it, for the same player through either menu.
        reopened.now = start + 1 + PaintRateLimiter.TICKS_PER_STROKE;
        reopened.paintCell(LAYER, 101, 0);
        helper.assertTrue(tile.layers[LAYER].get(101, 0), "a stroke's worth of ticks buys a stroke");

        // Another player at the same planner has an allowance of their own.
        RecordingMenu other = viewerAt(helper.makeMockPlayer(GameType.SURVIVAL), tile, start + 1);
        other.paintCell(LAYER, 102, 0);
        helper.assertTrue(tile.layers[LAYER].get(102, 0), "another player's allowance is untouched");
        helper.succeed();
    }

    /** A menu for {@code player} on a clock set by hand to {@code now}. */
    private static RecordingMenu viewerAt(Player player, TileZonePlanner tile, long now) {
        RecordingMenu menu = new RecordingMenu(player, tile);
        menu.autoAdvance = false;
        menu.now = now;
        menu.broadcastChanges();
        menu.deliverToClient();
        return menu;
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
