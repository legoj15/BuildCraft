/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * The fake-player cache is only safe if its invalidation handlers actually run: level unload (so no cached player
 * keeps a level alive) and owner login/logout (so no cached player keeps a discarded advancement tracker). The game
 * tests call the handlers directly — posting real unload/login events on the shared game-test server would run every
 * mod's listeners against a level or player that is not really going away — so this pins the subscription wiring.
 */
public class FakePlayerUtilSubscriptionTester {

    @Test
    public void classIsAnEventBusSubscriber() {
        Assertions.assertNotNull(FakePlayerUtil.class.getAnnotation(EventBusSubscriber.class),
                "FakePlayerUtil must stay an @EventBusSubscriber or its cache is never invalidated");
    }

    @Test
    public void levelUnloadIsSubscribed() {
        assertSubscribed(LevelEvent.Unload.class);
    }

    @Test
    public void ownerLoginIsSubscribed() {
        assertSubscribed(PlayerEvent.PlayerLoggedInEvent.class);
    }

    @Test
    public void ownerLogoutIsSubscribed() {
        assertSubscribed(PlayerEvent.PlayerLoggedOutEvent.class);
    }

    private static void assertSubscribed(Class<? extends Event> eventType) {
        for (Method method : FakePlayerUtil.class.getDeclaredMethods()) {
            Class<?>[] params = method.getParameterTypes();
            if (params.length == 1 && params[0] == eventType) {
                Assertions.assertNotNull(method.getAnnotation(SubscribeEvent.class),
                        method.getName() + " must carry @SubscribeEvent");
                Assertions.assertTrue(Modifier.isStatic(method.getModifiers()) && Modifier.isPublic(method.getModifiers()),
                        method.getName() + " must be public static for the automatic subscriber to register it");
                return;
            }
        }
        Assertions.fail("FakePlayerUtil has no handler for " + eventType.getName());
    }
}
