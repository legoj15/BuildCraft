/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.config;

/**
 * Listeners for a spec's file being read. The spec is an {@code Object} for the same reason as in
 * {@link ConfigRegistrar}; a listener fires only for the spec it was registered against. Register while the mod is
 * being constructed.
 */
public interface ConfigHooks {

    /**
     * Runs {@code action} each time {@code spec}'s file is first loaded; at once, from this call, when a loader has
     * read the file already (Fabric reads it when the spec is registered, NeoForge in a later loading phase).
     */
    void onLoad(Object spec, Runnable action);

    /** Runs {@code action} each time {@code spec}'s file is reloaded after a change on disk. */
    void onReload(Object spec, Runnable action);
}
