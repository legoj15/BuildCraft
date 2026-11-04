/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;

import buildcraft.core.BCCoreCreativeTabs;
import buildcraft.lib.registry.CreativeTabContents;
import buildcraft.platform.Platform;
import buildcraft.platform.registry.BCRegistrar;
import buildcraft.platform.registry.BCHolder;

public class BCTransportCreativeTabs {
    public static final BCRegistrar<CreativeModeTab> CREATIVE_MODE_TABS =
            BCRegistrar.create(Registries.CREATIVE_MODE_TAB, BCTransport.MODID);

    public static final ResourceKey<CreativeModeTab> PIPES_TAB_KEY =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB,
                    Identifier.fromNamespaceAndPath(BCTransport.MODID, "pipes"));

    public static final BCHolder<CreativeModeTab, CreativeModeTab> PIPES_TAB =
            CREATIVE_MODE_TABS.register("pipes", () ->
                    Platform.registries().creativeTabBuilder(BCCoreCreativeTabs.MAIN_TAB_KEY)
                            .title(Component.translatable("itemGroup.buildcraft.pipes"))
                            .icon(() -> BCTransportItems.PIPE_DIAMOND_ITEM.get().getDefaultInstance())
                            .displayItems(CreativeTabContents.generator(PIPES_TAB_KEY))
                            .build());

    public static final ResourceKey<CreativeModeTab> PLUGS_TAB_KEY =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB,
                    Identifier.fromNamespaceAndPath(BCTransport.MODID, "plugs"));

    public static final BCHolder<CreativeModeTab, CreativeModeTab> PLUGS_TAB =
            CREATIVE_MODE_TABS.register("plugs", () ->
                    Platform.registries().creativeTabBuilder(PIPES_TAB_KEY)
                            .title(Component.translatable("itemGroup.buildcraft.plugs"))
                            .icon(() -> BCTransportItems.PLUG_BLOCKER.get().getDefaultInstance())
                            .displayItems(CreativeTabContents.generator(PLUGS_TAB_KEY))
                            .build());

    public static void init() {
        CREATIVE_MODE_TABS.subscribe();
    }
}
