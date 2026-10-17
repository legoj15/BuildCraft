/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.marker;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import buildcraft.lib.test.EntityArenaUtil;
import buildcraft.lib.test.StubMarkerCache;

/**
 * The server half of the marker join resync: a player entering a level is handed to every registered
 * {@link MarkerCache}, which is what makes the client's cache (dropped whenever its level unloads) fill up again.
 * The wiring is exercised through the REAL event bus with a stand-in cache that records who it was asked to sync,
 * because a game-test server has no client to receive a packet; what the sync CARRIES is checked, against real
 * markers and a replay into a client-side cache, by {@code MarkerTester}'s join-sync test.
 */
public class MarkerJoinResyncTester {

    /** Upper bound on the wait for the resync, which the handler defers by one server tick. */
    private static final int RESYNC_TICKS = 20;

    public static void aPlayerJoiningALevelIsResynced(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StubMarkerCache cache = StubMarkerCache.register();
        // Whatever the outcome, this cache must not stay on the event paths of the tests after this one.
        helper.runAfterDelay(RESYNC_TICKS + 1, cache::unregister);
        StubMarkerCache.SubCache sub = cache.getSubCache(level);

        // A player the world never added (so no connection, and nothing for the real caches to send) and an
        // item: only the player may reach the caches.
        ServerPlayer player = new ServerPlayer(level.getServer(), level,
                new GameProfile(UUID.randomUUID(), "marker-join-test"), ClientInformation.createDefault());
        ItemEntity item = new ItemEntity(level, 0.5, 0.5, 0.5, new ItemStack(Items.STONE));
        NeoForge.EVENT_BUS.post(new EntityJoinLevelEvent(item, level));
        NeoForge.EVENT_BUS.post(new EntityJoinLevelEvent(player, level));

        EntityArenaUtil.tickUntil(helper, RESYNC_TICKS, () -> !sub.joined.isEmpty(), () -> {
            cache.unregister();
            if (sub.joined.size() != 1 || sub.joined.get(0) != player) {
                helper.fail("expected the cache to be asked to sync exactly the joining player, got " + sub.joined);
                return;
            }
            helper.succeed();
        }, "a player joining the level was never handed to the marker caches for a resync");
    }
}
