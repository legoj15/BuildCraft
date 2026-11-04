/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.misc;

import java.util.function.Consumer;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import buildcraft.lib.block.LocalBlockUpdateNotifier;
import buildcraft.lib.marker.MarkerCacheEvents;
import buildcraft.platform.RecordingHooks;
import buildcraft.platform.event.GameHooks;
import buildcraft.platform.event.HookOrder;

/**
 * The static tables that hold block entities (and through them a whole level) are only cleared if their level-unload
 * listeners are actually registered. The behaviour is proven by the {@code StaticTablesLevelUnloadTester} game test,
 * which posts the event on the real bus; this is the fast guard that runs with every unit-test pass and fails the
 * moment a class's {@code registerHooks} (which BCCore calls with the platform's hooks) stops wiring the unload.
 */
public class LevelUnloadSubscriptionTest {

    @Test
    public void laserNotifierDropsUnloadedLevels() {
        RecordingHooks<GameHooks> recorder = RecordingHooks.of(GameHooks.class);
        LocalBlockUpdateNotifier.registerHooks(recorder.hooks());
        recorder.listener("onLevelUnload", Consumer.class);
    }

    /** Last, and never told about a cancelled placement: a placement a protection mod refused wakes no laser. */
    @Test
    public void laserNotifierHearsPlacementsLast() {
        RecordingHooks<GameHooks> recorder = RecordingHooks.of(GameHooks.class);
        LocalBlockUpdateNotifier.registerHooks(recorder.hooks());
        Object[] args = recorder.only("onBlockPlace").args();
        Assertions.assertEquals(HookOrder.LATE, args[0]);
        Assertions.assertEquals(Boolean.FALSE, args[1]);
    }

    @Test
    public void markerCachesDropUnloadedLevels() {
        RecordingHooks<GameHooks> recorder = RecordingHooks.of(GameHooks.class);
        MarkerCacheEvents.registerHooks(recorder.hooks());
        recorder.listener("onLevelUnload", Consumer.class);
    }
}
