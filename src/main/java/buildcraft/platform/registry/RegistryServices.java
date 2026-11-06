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

    /**
     * Hands {@code contribution} to the loader's own late-addition hook for the tab {@code tab}, returning true when
     * the loader delivers it; false when it has no such hook, and the caller puts the contribution in the tab's own
     * {@code displayItems} generator instead. Contributions the loader accepts reach the tab in call order.
     *
     * <p>NeoForge: a {@code BuildCreativeModeTabContentsEvent} listener on BuildCraft's mod bus at {@code LOW}
     * priority, so BuildCraft's items still follow the additions of any normal or high priority listener, as they did
     * before the platform layer (the tab's own generator runs before that event is posted). Fabric: false; its tabs
     * list BuildCraft's contribution from the generator (see {@link #creativeTabBuilder(ResourceKey)}).
     */
    boolean contributeToTabLate(ResourceKey<CreativeModeTab> tab, CreativeModeTab.DisplayItemsGenerator contribution);
}
