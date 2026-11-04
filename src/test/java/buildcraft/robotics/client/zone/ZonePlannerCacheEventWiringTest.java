/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.client.zone;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import buildcraft.robotics.client.BCRoboticsClient;

/**
 * Pins that the Zone Planner terrain cache's client hooks ({@code BCRoboticsClient.GameBus}) load and run cleanly
 * &mdash; nothing else in the headless runs ever loads this class. The hooks reach the loader through
 * {@code ClientHooks}, which only a client registers; the tick, logout and level-unload hooks run here, and the
 * chunk hooks are one-line {@code invalidateChunk()} calls on the facade, which the game tests cover.
 */
public class ZonePlannerCacheEventWiringTest {

    @Test
    public void testTheTickAndSessionHooksRun() {
        ZonePlannerMapDataClient.INSTANCE.clear();

        Assertions.assertDoesNotThrow(BCRoboticsClient.GameBus::onClientTick);
        Assertions.assertDoesNotThrow(BCRoboticsClient.GameBus::onClientLoggingOut);
        Assertions.assertDoesNotThrow(BCRoboticsClient.GameBus::onLevelUnload);

        ZonePlannerMapDataClient.INSTANCE.clear();
    }
}
