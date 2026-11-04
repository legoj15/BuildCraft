/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.neoforge;

//? if neoforge {
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;

/**
 * BuildCraft's mod bus and mod container, handed over by {@link BCNeoForgeEntrypoint} before it runs BuildCraft's
 * init. Every NeoForge implementation that subscribes on the mod bus, or registers on the container, reads them here
 * (the entrypoint is the only class FML gives them to).
 */
public final class NeoForgeModBus {
    private static IEventBus bus;
    private static ModContainer container;

    private NeoForgeModBus() {}

    /** Set once, by the entrypoint, before anything subscribes. */
    static synchronized void set(IEventBus modBus, ModContainer modContainer) {
        if (bus != null) {
            throw new IllegalStateException("BuildCraft's mod bus was already set");
        }
        bus = modBus;
        container = modContainer;
    }

    /** BuildCraft's mod event bus. */
    public static IEventBus get() {
        IEventBus modBus = bus;
        if (modBus == null) {
            throw new IllegalStateException("BuildCraft's mod bus is read before BCNeoForgeEntrypoint ran");
        }
        return modBus;
    }

    /** BuildCraft's mod container. */
    public static ModContainer container() {
        ModContainer modContainer = container;
        if (modContainer == null) {
            throw new IllegalStateException("BuildCraft's mod container is read before BCNeoForgeEntrypoint ran");
        }
        return modContainer;
    }
}
//?}
