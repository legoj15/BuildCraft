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
 * BuildCraft's {@link RegisterCapabilitiesEvent} registrations, queued in the order its init adds them: one per
 * {@code TransferServices.registerCapabilities} call ({@link NeoForgeTransfer}). They are not mod-bus listeners of their
 * own: the NeoForge entrypoint subscribes the one listener, {@link #fire}, on the mod bus before that init runs, and it
 * runs the queue in order. That keeps them at the point their per-subsystem mod-bus listeners ran when the subsystems
 * still held the bus. The event fires once, so a registration queued after it would never run:
 * {@link #add} refuses it.
 */
public final class CapabilityListeners {
    private static final List<Consumer<RegisterCapabilitiesEvent>> LISTENERS = new ArrayList<>();
    private static boolean fired;

    private CapabilityListeners() {}

    /**
     * Adds {@code listener}; called from BuildCraft's init, through the transfer services.
     *
     * @throws IllegalStateException if the event has already fired, when the listener could never run
     */
    public static synchronized void add(Consumer<RegisterCapabilitiesEvent> listener) {
        if (fired) {
            throw new IllegalStateException("registerCapabilities was called after RegisterCapabilitiesEvent fired; "
                + "BuildCraft's capabilities must be registered from its init");
        }
        LISTENERS.add(listener);
    }

    /** Runs every listener, in the order they were added. */
    public static synchronized void fire(RegisterCapabilitiesEvent event) {
        fired = true;
        for (Consumer<RegisterCapabilitiesEvent> listener : LISTENERS) {
            listener.accept(event);
        }
    }
}
//?}
