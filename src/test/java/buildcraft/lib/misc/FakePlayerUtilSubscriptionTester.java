/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import buildcraft.platform.RecordingHooks;
import buildcraft.platform.event.GameHooks;

/**
 * The fake-player cache is only safe if its invalidation handlers actually run: level unload (so no cached player
 * keeps a level alive) and owner login/logout (so no cached player keeps a discarded advancement tracker). The game
 * tests call the handlers directly — posting real unload/login events on the shared game-test server would run every
 * mod's listeners against a level or player that is not really going away — so this pins the hook wiring
 * ({@link FakePlayerUtil#registerHooks}, which BCCore calls with the platform's hooks).
 */
public class FakePlayerUtilSubscriptionTester {

    private static RecordingHooks<GameHooks> registered() {
        RecordingHooks<GameHooks> recorder = RecordingHooks.of(GameHooks.class);
        FakePlayerUtil.registerHooks(recorder.hooks());
        return recorder;
    }

    @Test
    public void levelUnloadIsSubscribed() {
        registered().only("onLevelUnload");
    }

    @Test
    public void ownerLoginIsSubscribed() {
        registered().only("onPlayerLoggedIn");
    }

    @Test
    public void ownerLogoutIsSubscribed() {
        registered().only("onPlayerLoggedOut");
    }

    /**
     * The leaked-lease sweep runs when a tick STARTS: by then every lease that is still open was dropped without
     * close() during an earlier tick. Run at the end of a tick it would force-release a lease another listener
     * opened earlier in that same Post phase and is still using.
     */
    @Test
    public void leakedLeaseSweepRunsAtTickStart() {
        registered().only("onServerTickStart");
    }

    @Test
    public void leakedLeaseSweepIsNotAtTickEnd() {
        Assertions.assertEquals(0, registered().count("onServerTickEnd"),
                "a sweep at the end of a tick would release a lease another tick-end listener opened earlier in the"
                        + " same phase and is still using");
    }
}
