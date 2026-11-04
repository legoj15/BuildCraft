/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.registry;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;

/** The loader half of {@link BCRegistrar#create}: one registrar per (registry, namespace) pair. */
public interface RegistryServices {
    /** A new, empty registrar for {@code registry} whose entries are named in {@code namespace}. */
    <T> BCRegistrar<T> create(ResourceKey<? extends Registry<T>> registry, String namespace);

    /**
     * A builder for a mod creative tab (vanilla's own needs a row and column; NeoForge patches in a no-argument one
     * and places the tab itself, Fabric API's pages it).
     */
    CreativeModeTab.Builder creativeTabBuilder();

    /**
     * {@link #creativeTabBuilder()} for a tab listed right after {@code after} (NeoForge: {@code withTabsBefore}).
     * Fabric has no tab ordering: its tabs follow registration order, which BuildCraft's init already keeps.
     */
    CreativeModeTab.Builder creativeTabBuilder(ResourceKey<CreativeModeTab> after);
}
