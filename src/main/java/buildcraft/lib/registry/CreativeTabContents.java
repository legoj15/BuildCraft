/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.registry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;

/**
 * The contents of BuildCraft's creative tabs that more than one subsystem fills: an ordered list of contributions per
 * tab, read by that tab's own {@code displayItems} generator ({@link #generator}). A loader-neutral replacement for
 * per-subsystem tab-contents event listeners.
 *
 * <p><b>Order.</b> A tab lists its contributions in the order they were added, which is subsystem init order
 * (transport before silicon): on the plugs tab transport's plugs and wires come first and silicon's gates, lenses and
 * plugs after them, exactly as the old LOW (transport) and LOWEST (silicon) listener pair produced.
 * {@code RoboticsCreativeTabTester} pins it.
 *
 * <p>Contributions are added during mod construction; the tab reads the list each time its contents are built.
 */
public final class CreativeTabContents {
    private CreativeTabContents() {}

    private static final Map<ResourceKey<CreativeModeTab>, List<CreativeModeTab.DisplayItemsGenerator>> CONTRIBUTIONS =
            new HashMap<>();

    /** Appends {@code contribution} to the contents of the tab {@code tab}. */
    public static synchronized void contribute(ResourceKey<CreativeModeTab> tab,
            CreativeModeTab.DisplayItemsGenerator contribution) {
        CONTRIBUTIONS.computeIfAbsent(tab, k -> new ArrayList<>()).add(contribution);
    }

    /** The {@code displayItems} generator of the tab {@code tab}: every contribution, in order. */
    public static CreativeModeTab.DisplayItemsGenerator generator(ResourceKey<CreativeModeTab> tab) {
        return (parameters, output) -> {
            List<CreativeModeTab.DisplayItemsGenerator> contributions;
            synchronized (CreativeTabContents.class) {
                contributions = List.copyOf(CONTRIBUTIONS.getOrDefault(tab, List.of()));
            }
            for (CreativeModeTab.DisplayItemsGenerator contribution : contributions) {
                contribution.accept(parameters, output);
            }
        };
    }
}
