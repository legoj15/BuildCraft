/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;

import buildcraft.lib.registry.CreativeTabContents;
import buildcraft.silicon.plug.FacadeBlockStateInfo;
import buildcraft.silicon.plug.FacadeInstance;
import buildcraft.silicon.plug.FacadeStateManager;
import buildcraft.platform.Platform;
import buildcraft.platform.registry.BCRegistrar;
import buildcraft.platform.registry.BCHolder;

public class BCSiliconCreativeTabs {
    public static final BCRegistrar<CreativeModeTab> CREATIVE_MODE_TABS =
            BCRegistrar.create(Registries.CREATIVE_MODE_TAB, BCSilicon.MODID);

    // Reference the transport plugs tab key by identifier (avoids cross-module compile dependency)
    static final ResourceKey<CreativeModeTab> TRANSPORT_PLUGS_TAB_KEY =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB,
                    Identifier.fromNamespaceAndPath("buildcraftunofficial", "plugs"));

    public static final ResourceKey<CreativeModeTab> FACADE_TAB_KEY =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB,
                    Identifier.fromNamespaceAndPath(BCSilicon.MODID, "facades"));

    public static final BCHolder<CreativeModeTab, CreativeModeTab> FACADE_TAB =
            CREATIVE_MODE_TABS.register("facades", () ->
                    Platform.registries().creativeTabBuilder(TRANSPORT_PLUGS_TAB_KEY)
                            .title(Component.translatable("itemGroup.buildcraft.facades"))
                            .icon(() -> {
                                FacadeBlockStateInfo preview = FacadeStateManager.previewState;
                                if (preview != null && preview != FacadeStateManager.defaultState) {
                                    return BCSiliconItems.PLUG_FACADE.get()
                                        .createItemStack(FacadeInstance.createSingle(preview, false));
                                }
                                return BCSiliconItems.PLUG_FACADE.get().getDefaultInstance();
                            })
                            .displayItems(CreativeTabContents.generator(FACADE_TAB_KEY))
                            .build());

    public static void init() {
        CREATIVE_MODE_TABS.subscribe();
    }
}
