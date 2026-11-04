/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.registry;

import java.util.function.Supplier;

import buildcraft.platform.Platform;

/**
 * Registers saved entity attachments under one namespace, as {@link BCRegistrar} does registry entries: on NeoForge
 * queued until {@link #subscribe()} attaches the registrar to the constructing mod, on Fabric registered at once.
 *
 * <p>This is a contract (see the package documentation of {@code buildcraft.platform}): JDK and vanilla types only.
 */
public interface BCAttachmentRegistrar {

    /** A new attachment registrar naming its attachments in {@code namespace}. */
    static BCAttachmentRegistrar create(String namespace) {
        return Platform.attachments().create(namespace);
    }

    /**
     * Registers the saved attachment {@code namespace:name}, whose default value {@code factory} builds.
     *
     * @param copyOnDeath whether a player's value survives death and respawn
     */
    <T extends BCAttachmentData> BCAttachment<T> register(String name, Supplier<T> factory, boolean copyOnDeath);

    /** See {@link BCRegistrar#subscribe()}. */
    void subscribe();
}
