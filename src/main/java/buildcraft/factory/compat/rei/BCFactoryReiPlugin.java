/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.compat.rei;

import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.screen.ScreenRegistry;
import me.shedaniel.rei.api.client.registry.transfer.TransferHandlerRegistry;
import me.shedaniel.rei.forge.REIPluginClient;

import buildcraft.factory.BCFactoryItems;
import buildcraft.factory.client.gui.GuiAutoCraftItems;
import buildcraft.factory.container.ContainerAutoCraftItems;
import buildcraft.lib.compat.rei.ReiCraftingTableSupport;

/**
 * REI integration plugin for BuildCraft Factory: registers the Auto Workbench with recipe transfer,
 * click area, catalyst, and ghost drag-and-drop. The shared logic lives in {@link ReiCraftingTableSupport}.
 */
@REIPluginClient
public class BCFactoryReiPlugin implements REIClientPlugin {

    // Click area: the progress arrow, left of the 3x3 blueprint grid, aligned with row 2.
    private static final ReiCraftingTableSupport<GuiAutoCraftItems, ContainerAutoCraftItems> SUPPORT =
            new ReiCraftingTableSupport<>(GuiAutoCraftItems.class, BCFactoryItems.AUTOWORKBENCH_ITEM::get,
                    90, 47, 23, 10);

    @Override
    public void registerTransferHandlers(TransferHandlerRegistry registry) {
        SUPPORT.registerTransferHandlers(registry);
    }

    @Override
    public void registerScreens(ScreenRegistry registry) {
        SUPPORT.registerScreens(registry);
    }

    @Override
    public void registerCategories(CategoryRegistry registry) {
        SUPPORT.registerCategories(registry);
    }
}
