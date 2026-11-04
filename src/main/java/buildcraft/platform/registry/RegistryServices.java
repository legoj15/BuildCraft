/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.registry;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

/** The loader half of {@link BCRegistrar#create}: one registrar per (registry, namespace) pair. */
public interface RegistryServices {
    /** A new, empty registrar for {@code registry} whose entries are named in {@code namespace}. */
    <T> BCRegistrar<T> create(ResourceKey<? extends Registry<T>> registry, String namespace);
}
