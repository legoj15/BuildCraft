/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.client.zone;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.neoforged.bus.api.BusBuilder;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import buildcraft.robotics.client.BCRoboticsClient;

/**
 * Pins that the Zone Planner terrain cache's client-event hooks ({@code BCRoboticsClient.GameBus}) register on an
 * event bus at all &mdash; the bus validates every {@code @SubscribeEvent} signature at registration, which is
 * exactly what would fail at client start-up, and nothing else in the headless runs ever loads this class &mdash;
 * and that posting the tick event runs its hook cleanly. The other hooks (logout, level unload, chunk
 * load/unload) need client objects to construct their events; each is a one-line {@code clear()} or
 * {@code invalidateChunk()} on the facade, which the game tests cover.
 */
public class ZonePlannerCacheEventWiringTest {

    @Test
    public void testGameBusRegistersAndTheTickHookRuns() {
        ZonePlannerMapDataClient.INSTANCE.clear();

        IEventBus bus = BusBuilder.builder().build();
        Assertions.assertDoesNotThrow(() -> bus.register(BCRoboticsClient.GameBus.class),
                "every @SubscribeEvent method on GameBus has a signature the bus accepts");
        Assertions.assertDoesNotThrow(() -> bus.post(new ClientTickEvent.Post()));

        ZonePlannerMapDataClient.INSTANCE.clear();
    }
}
