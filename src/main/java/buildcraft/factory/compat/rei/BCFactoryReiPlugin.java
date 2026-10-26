/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.compat.rei;

import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.client.registry.screen.ScreenRegistry;
import me.shedaniel.rei.api.client.registry.transfer.TransferHandlerRegistry;
import me.shedaniel.rei.api.common.util.EntryStacks;
import me.shedaniel.rei.forge.REIPluginClient;

import buildcraft.api.recipes.IRefineryRecipeManager.IDistillationRecipe;
import buildcraft.energy.BCEnergyRecipes;
import buildcraft.factory.BCFactoryItems;
import buildcraft.factory.client.gui.GuiAutoCraftItems;
import buildcraft.factory.client.gui.GuiDistiller;
import buildcraft.factory.client.gui.GuiHeatExchange;
import buildcraft.factory.compat.viewer.DistillerRecipeTransfer;
import buildcraft.factory.compat.viewer.HeatExchangerRecipePair;
import buildcraft.factory.compat.viewer.HeatExchangerRecipeTransfer;
import buildcraft.factory.compat.viewer.RefineryRecipeCollector;
import buildcraft.factory.container.ContainerAutoCraftItems;
import buildcraft.lib.compat.rei.ReiCraftingTableSupport;
import buildcraft.lib.compat.rei.ReiRecipeTransferHandler;
import buildcraft.lib.compat.rei.ReiScreens;

/**
 * REI integration plugin for BuildCraft Factory, REI's twin of {@code BCFactoryJeiPlugin}: the Auto Workbench as a
 * crafting workstation (recipe transfer, click area, ghost drag-and-drop; shared with the Advanced Crafting Table in
 * {@link ReiCraftingTableSupport}), and the Heat Exchanger and Distiller categories with their workstations, click
 * areas, and "+" transfers that move the input buckets into the machine.
 */
@REIPluginClient
public class BCFactoryReiPlugin implements REIClientPlugin {

    // Click area: the progress arrow, left of the 3x3 blueprint grid, aligned with row 2.
    private static final ReiCraftingTableSupport<GuiAutoCraftItems, ContainerAutoCraftItems> SUPPORT =
            new ReiCraftingTableSupport<>(GuiAutoCraftItems.class, BCFactoryItems.AUTOWORKBENCH_ITEM::get,
                    90, 47, 23, 10);

    private static final HeatExchangerReiCategory HEAT_EXCHANGER = new HeatExchangerReiCategory();
    private static final DistillerReiCategory DISTILLER = new DistillerReiCategory();

    @Override
    public void registerTransferHandlers(TransferHandlerRegistry registry) {
        SUPPORT.registerTransferHandlers(registry);
        registry.register(new ReiRecipeTransferHandler<>(DistillerReiCategory.ID, new DistillerRecipeTransfer()));
        registry.register(new ReiRecipeTransferHandler<>(HeatExchangerReiCategory.ID,
                new HeatExchangerRecipeTransfer()));
    }

    @Override
    public void registerScreens(ScreenRegistry registry) {
        SUPPORT.registerScreens(registry);
        // The Distiller's centre process schematic, between the input and the output tanks.
        ReiScreens.clickArea(registry, GuiDistiller.class, 61, 12, 36, 57, DistillerReiCategory.ID);
        // The Heat Exchanger's centre progress region, clear of all four tank gauges.
        ReiScreens.clickArea(registry, GuiHeatExchange.class, 73, 36, 30, 21, HeatExchangerReiCategory.ID);
    }

    @Override
    public void registerCategories(CategoryRegistry registry) {
        SUPPORT.registerCategories(registry);
        registry.add(HEAT_EXCHANGER);
        registry.add(DISTILLER);
        registry.addWorkstations(HeatExchangerReiCategory.ID, EntryStacks.of(BCFactoryItems.HEAT_EXCHANGE.get()));
        registry.addWorkstations(DistillerReiCategory.ID, EntryStacks.of(BCFactoryItems.DISTILLER.get()));
    }

    @Override
    public void registerDisplays(DisplayRegistry registry) {
        // A multiplayer client fills the refinery registries at LoggingIn; REI's reload may come first.
        BCEnergyRecipes.ensureInitialized();
        for (HeatExchangerRecipePair pair : RefineryRecipeCollector.heatExchangerPairs()) {
            registry.add(HEAT_EXCHANGER.display(pair));
        }
        for (IDistillationRecipe recipe : RefineryRecipeCollector.distillationRecipes()) {
            registry.add(DISTILLER.display(recipe));
        }
    }
}
