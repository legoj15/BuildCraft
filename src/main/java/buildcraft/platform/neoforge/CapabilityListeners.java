/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.neoforge;

//? if neoforge {
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * BuildCraft's {@link RegisterCapabilitiesEvent} listeners, in the order its init adds them: one per
 * {@code TransferServices.registerCapabilities} call ({@link NeoForgeTransfer}). The NeoForge entrypoint subscribes
 * {@link #fire} on the mod bus before that init runs, so each registration runs at the point its own mod-bus listener
 * did when the subsystems still held the bus.
 */
public final class CapabilityListeners {
    private static final List<Consumer<RegisterCapabilitiesEvent>> LISTENERS = new ArrayList<>();

    private CapabilityListeners() {}

    /** Adds {@code listener}; called from BuildCraft's init, through the transfer services. */
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
//?}
