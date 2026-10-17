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
}
