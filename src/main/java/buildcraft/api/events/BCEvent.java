/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.api.events;

/**
 * The base of every BuildCraft API event. Listeners subscribe through each event's {@link BCEventListeners}
 * {@code LISTENERS}, which works on every loader.
 *
 * <p>On NeoForge this is also a NeoForge {@code Event}, and {@link BCEventListeners#post} posts it on
 * {@code NeoForge.EVENT_BUS} after the BuildCraft listeners, so a NeoForge addon's {@code @SubscribeEvent} or
 * {@code addListener} on these classes keeps working exactly as it did before the platform layer. On Fabric it is a
 * plain class and the lists are the only listeners.
 */
//? if neoforge {
public abstract class BCEvent extends net.neoforged.bus.api.Event {
//?} else {
/*public abstract class BCEvent {
*///?}

    protected BCEvent() {}

    /** Posts {@code event} on the running loader's own event bus, where it has one (NeoForge); a no-op elsewhere. */
    static void postOnLoaderBus(BCEvent event) {
        //? if neoforge {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
        //?}
    }
}
