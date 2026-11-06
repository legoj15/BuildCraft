/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.neoforge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Which NeoForge event each {@code GameHooks} method rides, where the callers' own tests only see that they asked for
 * the method. The fake-player lease sweep ({@code FakePlayerUtilSubscriptionTester}) is correct only at the START of
 * a tick, so the tick-start hook must be {@link ServerTickEvent.Pre} and the tick-end hook must not be.
 */
public class NeoForgeGameHooksBindingTester {

    @Test
    public void tickStartHookRidesTheTickStartEvent() {
        NeoForgeGameHooks hooks = new NeoForgeGameHooks();
        AtomicInteger start = new AtomicInteger();
        AtomicInteger end = new AtomicInteger();
        hooks.onServerTickStart(server -> start.incrementAndGet());
        hooks.onServerTickEnd(server -> end.incrementAndGet());

        NeoForge.EVENT_BUS.post(new ServerTickEvent.Pre(() -> true, null));

        assertEquals(1, start.get(), "the tick-start hook must run when a server tick starts (ServerTickEvent.Pre)");
        assertEquals(0, end.get(), "the tick-end hook must not run when a server tick starts");
    }
}
