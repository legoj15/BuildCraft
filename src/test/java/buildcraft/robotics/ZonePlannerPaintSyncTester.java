/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics;

import java.util.ArrayList;
import java.util.List;

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
 * {@code sendMessage} would silently drop everything. The tests never wait for a tick: both halves of the
 * protocol are plain method calls on the menu, so there is no timing to gate on.
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

        RecordingMenu(Player player, TileZonePlanner tile) {
            super(0, player.getInventory(), tile);
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

        /** The layer-sync messages pushed down for one layer since the last {@code sent.clear()}. */
        long syncsFor(int layer) {
            return sent.stream().filter(m -> m.id() == NET_SYNC_LAYER && m.layer() == layer).count();
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

    private static Player viewer(GameTestHelper helper) {
        return helper.makeMockPlayer(GameType.SURVIVAL);
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
        RecordingMenu menu = new RecordingMenu(viewer(helper), tile);

        menu.broadcastChanges(); // a fresh viewer is sent every layer once
        helper.assertTrue(menu.syncsFor(LAYER) == 1, "a fresh viewer gets the layer once (initial sync)");
        menu.sent.clear();

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
        RecordingMenu painter = new RecordingMenu(viewer(helper), tile);
        RecordingMenu watcher = new RecordingMenu(viewer(helper), tile);
        painter.broadcastChanges();
        watcher.broadcastChanges();
        painter.sent.clear();
        watcher.sent.clear();

        painter.paint(LAYER, cells(4, 4));
        painter.broadcastChanges();
        watcher.broadcastChanges();

        helper.assertTrue(painter.syncsFor(LAYER) == 0, "the painter is not echoed");
        helper.assertTrue(watcher.syncsFor(LAYER) == 1, "the other viewer is sent the new layer");
        helper.succeed();
    }

    // ── Bounds on what a client may send ────────────────────────────────────────────────────

    /** Tile-relative block x of a chunk (1000) far outside the survey square. */
    private static final int FAR = 16_000;

    /** A client may paint inside the survey square only: a chunk it tries to add beyond that is dropped, and a
     *  zone the layer already has out there (read in from a map location) survives the paint untouched. The
     *  painter is told what the server really holds, since it differs from what they sent. */
    public static void paintBeyondSurveyRangeIsClamped(GameTestHelper helper) {
        TileZonePlanner tile = placePlanner(helper);
        tile.layers[LAYER] = cells(FAR, 0);
        RecordingMenu menu = new RecordingMenu(viewer(helper), tile);
        menu.broadcastChanges();
        menu.sent.clear();

        // The client's copy: the far chunk it was given, a legitimate new stroke, and a far chunk it made up.
        menu.paint(LAYER, cells(FAR, 0, 3, 3, -FAR, 40));

        ZonePlan stored = tile.layers[LAYER];
        helper.assertTrue(stored.get(3, 3), "the stroke inside the square is stored");
        helper.assertFalse(stored.get(-FAR, 40), "a chunk added far outside the square is dropped");
        helper.assertTrue(stored.get(FAR, 0), "the layer's own far chunk is preserved");

        menu.broadcastChanges();
        helper.assertTrue(menu.syncsFor(LAYER) == 1,
                "the painter is sent the clamped layer, which differs from what they sent");
        helper.succeed();
    }

    /** The common case with a far chunk present: the client hands back exactly what it was given plus its
     *  stroke, nothing is altered, so nothing is echoed. */
    public static void paintKeepingFarChunksIntactIsNotEchoed(GameTestHelper helper) {
        TileZonePlanner tile = placePlanner(helper);
        tile.layers[LAYER] = cells(FAR, 0);
        RecordingMenu menu = new RecordingMenu(viewer(helper), tile);
        menu.broadcastChanges();
        menu.sent.clear();

        menu.paint(LAYER, cells(FAR, 0, 3, 3));

        helper.assertTrue(tile.layers[LAYER].get(FAR, 0) && tile.layers[LAYER].get(3, 3),
                "both the far chunk and the new stroke are stored");
        menu.broadcastChanges();
        helper.assertTrue(menu.syncsFor(LAYER) == 0, "an unaltered paint is not echoed to its painter");
        helper.succeed();
    }

    /** A message declaring more chunks than any layer can hold is refused outright: nothing is stored, and the
     *  painter, who may be showing the rejected layer, is resynced to the real one. */
    public static void oversizedPaintIsRejected(GameTestHelper helper) {
        TileZonePlanner tile = placePlanner(helper);
        tile.layers[LAYER] = cells(1, 1);
        ZonePlan before = tile.layers[LAYER];
        RecordingMenu menu = new RecordingMenu(viewer(helper), tile);
        menu.broadcastChanges();
        menu.sent.clear();

        // One more entry than the wire cap, each a distinct full chunk far from the planner.
        int count = ZonePlan.MAX_CHUNKS_ON_WIRE + 1;
        PacketBufferBC buf = new PacketBufferBC(Unpooled.buffer());
        buf.writeByte(LAYER);
        buf.writeInt(count);
        for (int i = 0; i < count; i++) {
            buf.writeInt(1000 + i % 200);
            buf.writeInt(1000 + i / 200);
            buf.writeByte(2); // fullSet, no bit array
        }
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        buf.release();
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
        RecordingMenu menu = new RecordingMenu(viewer(helper), tile);
        menu.broadcastChanges();
        menu.sent.clear();

        PacketBufferBC buf = new PacketBufferBC(Unpooled.buffer());
        buf.writeByte(LAYER);
        buf.writeInt(3); // promises three chunks...
        buf.writeInt(0);
        buf.writeInt(0);
        buf.writeByte(2); // ...delivers one
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        buf.release();
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
        RecordingMenu menu = new RecordingMenu(viewer(helper), tile);

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
