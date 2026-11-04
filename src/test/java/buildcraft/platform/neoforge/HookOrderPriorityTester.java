/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.neoforge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import net.neoforged.bus.api.EventPriority;

import buildcraft.platform.event.HookOrder;

/**
 * The listeners that moved behind {@link HookOrder} must keep the NeoForge priority they named before: LATE is the
 * {@code LOWEST} of {@code ContainedFluidDetonation}, {@code LocalBlockUpdateNotifier} and the {@code MachineRefusals}
 * recorder; EARLY is the {@code HIGH} of silicon's client login listener.
 */
public class HookOrderPriorityTester {
    @Test
    public void earlyIsHigh() {
        assertEquals(EventPriority.HIGH, NeoForgeGameHooks.priority(HookOrder.EARLY));
    }

    @Test
    public void defaultIsNormal() {
        assertEquals(EventPriority.NORMAL, NeoForgeGameHooks.priority(HookOrder.DEFAULT));
    }

    @Test
    public void lateIsLowest() {
        assertEquals(EventPriority.LOWEST, NeoForgeGameHooks.priority(HookOrder.LATE));
    }
}
