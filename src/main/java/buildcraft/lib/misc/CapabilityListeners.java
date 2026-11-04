/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.misc;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * BuildCraft's {@link RegisterCapabilitiesEvent} listeners, in the order its init adds them. The NeoForge entrypoint
 * subscribes {@link #fire} on the mod bus before that init runs, so each listener runs at the point its own mod-bus
 * listener did when the subsystems still held the bus. A stopgap: the transfer slice replaces it with a
 * loader-neutral capability registrar.
 */
public final class CapabilityListeners {
    private static final List<Consumer<RegisterCapabilitiesEvent>> LISTENERS = new ArrayList<>();

    private CapabilityListeners() {}

    /** Adds {@code listener}; call it from BuildCraft's init. */
    public static synchronized void add(Consumer<RegisterCapabilitiesEvent> listener) {
        LISTENERS.add(listener);
    }

    /** Runs every listener, in the order they were added. */
    public static synchronized void fire(RegisterCapabilitiesEvent event) {
        for (Consumer<RegisterCapabilitiesEvent> listener : LISTENERS) {
            listener.accept(event);
        }
    }
}
