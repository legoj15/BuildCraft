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

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

import buildcraft.lib.block.LocalBlockUpdateNotifier;
import buildcraft.lib.marker.MarkerCacheEvents;

/**
 * The static tables that hold block entities (and through them a whole level) are only cleared if their
 * {@code LevelEvent.Unload} handlers are actually subscribed. The behaviour is proven by the
 * {@code StaticTablesLevelUnloadTester} game test, which posts the event on the real bus; this is the fast guard
 * that runs with every unit-test pass and fails the moment an annotation or modifier that the automatic subscriber
 * needs is lost.
 */
public class LevelUnloadSubscriptionTest {

    @Test
    public void laserNotifierDropsUnloadedLevels() {
        assertUnloadHandlerSubscribed(LocalBlockUpdateNotifier.class);
    }

    @Test
    public void markerCachesDropUnloadedLevels() {
        assertUnloadHandlerSubscribed(MarkerCacheEvents.class);
    }

    private static void assertUnloadHandlerSubscribed(Class<?> owner) {
        Assertions.assertNotNull(owner.getAnnotation(EventBusSubscriber.class),
                owner.getSimpleName() + " must be an @EventBusSubscriber or its unload handler never runs");
        for (Method method : owner.getDeclaredMethods()) {
            Class<?>[] params = method.getParameterTypes();
            if (params.length == 1 && params[0] == LevelEvent.Unload.class) {
                Assertions.assertNotNull(method.getAnnotation(SubscribeEvent.class),
                        method.getName() + " must carry @SubscribeEvent");
                Assertions.assertTrue(Modifier.isStatic(method.getModifiers())
                                && Modifier.isPublic(method.getModifiers()),
                        method.getName() + " must be public static for the automatic subscriber to register it");
                return;
            }
        }
        Assertions.fail(owner.getSimpleName() + " has no LevelEvent.Unload handler");
    }
}
