/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon.compat.rei;

import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.screen.ScreenRegistry;
import me.shedaniel.rei.api.client.registry.transfer.TransferHandlerRegistry;
import me.shedaniel.rei.forge.REIPluginClient;

import buildcraft.lib.compat.rei.ReiCraftingTableSupport;
import buildcraft.silicon.BCSiliconItems;
import buildcraft.silicon.container.ContainerAdvancedCraftingTable;
import buildcraft.silicon.gui.GuiAdvancedCraftingTable;

/**
 * REI integration plugin for BuildCraft Silicon: registers the Advanced Crafting Table with recipe
 * transfer, click area, catalyst, and ghost drag-and-drop. The shared logic lives in
 * {@link ReiCraftingTableSupport}.
 */
@REIPluginClient
public class BCSiliconReiPlugin implements REIClientPlugin {

    // Click area: the progress arrow between the blueprint grid and the output slot.
    private static final ReiCraftingTableSupport<GuiAdvancedCraftingTable, ContainerAdvancedCraftingTable> SUPPORT =
            new ReiCraftingTableSupport<>(GuiAdvancedCraftingTable.class, BCSiliconItems.ADVANCED_CRAFTING_TABLE::get,
                    93, 32, 23, 16);

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
