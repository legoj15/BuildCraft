/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon.compat.rei;

import java.util.ArrayList;
import java.util.Collection;

import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.client.registry.screen.ExclusionZones;
import me.shedaniel.rei.api.client.registry.screen.ScreenRegistry;
import me.shedaniel.rei.api.client.registry.transfer.TransferHandlerRegistry;
import me.shedaniel.rei.api.common.util.EntryStacks;
import me.shedaniel.rei.forge.REIPluginClient;

import buildcraft.lib.compat.rei.ReiCraftingTableSupport;
import buildcraft.lib.compat.rei.ReiRecipeTransferHandler;
import buildcraft.lib.compat.rei.ReiScreens;
import buildcraft.lib.gui.IGuiElement;
import buildcraft.lib.gui.statement.GuiElementStatementSource;
import buildcraft.silicon.BCSiliconItems;
import buildcraft.silicon.compat.viewer.AssemblyRecipeCollector;
import buildcraft.silicon.compat.viewer.AssemblyRecipeEntry;
import buildcraft.silicon.compat.viewer.AssemblyTableRecipeTransfer;
import buildcraft.silicon.container.AssemblyTableLayout;
import buildcraft.silicon.container.ContainerAdvancedCraftingTable;
import buildcraft.silicon.gui.GuiAdvancedCraftingTable;
import buildcraft.silicon.gui.GuiAssemblyTable;
import buildcraft.silicon.gui.GuiGate;

/**
 * REI integration plugin for BuildCraft Silicon, REI's twin of {@code BCSiliconJeiPlugin}: the Advanced Crafting
 * Table as a crafting workstation (recipe transfer, click area, ghost drag-and-drop; the shared logic lives in
 * {@link ReiCraftingTableSupport}), the Assembly Table category with its workstation, click area and "+" transfer, and
 * the gate GUI's statement panels as exclusion zones. The item comparators are in {@link BCSiliconReiCommonPlugin}.
 */
@REIPluginClient
public class BCSiliconReiPlugin implements REIClientPlugin {

    // Click area: the progress arrow between the blueprint grid and the output slot.
    private static final ReiCraftingTableSupport<GuiAdvancedCraftingTable, ContainerAdvancedCraftingTable> SUPPORT =
            new ReiCraftingTableSupport<>(GuiAdvancedCraftingTable.class, BCSiliconItems.ADVANCED_CRAFTING_TABLE::get,
                    93, 32, 23, 16);

    private static final AssemblyTableReiCategory ASSEMBLY = new AssemblyTableReiCategory();

    @Override
    public void registerTransferHandlers(TransferHandlerRegistry registry) {
        SUPPORT.registerTransferHandlers(registry);
        // Assembly Table: move the recipe's real ingredients from the player inventory into the input slots.
        registry.register(new ReiRecipeTransferHandler<>(AssemblyTableReiCategory.ID,
                new AssemblyTableRecipeTransfer()));
    }

    @Override
    public void registerScreens(ScreenRegistry registry) {
        SUPPORT.registerScreens(registry);
        // The Assembly Table's energy bar, clear of the recipe grid the GUI handles on click.
        ReiScreens.clickArea(registry, GuiAssemblyTable.class, AssemblyTableLayout.BAR_X, AssemblyTableLayout.BAR_Y,
                AssemblyTableLayout.BAR_W, AssemblyTableLayout.BAR_H, AssemblyTableReiCategory.ID);
    }

    @Override
    public void registerCategories(CategoryRegistry registry) {
        SUPPORT.registerCategories(registry);
        registry.add(ASSEMBLY);
        registry.addWorkstations(AssemblyTableReiCategory.ID, EntryStacks.of(BCSiliconItems.ASSEMBLY_TABLE.get()));
    }

    @Override
    public void registerDisplays(DisplayRegistry registry) {
        for (AssemblyRecipeEntry entry : AssemblyRecipeCollector.collect()) {
            registry.add(ASSEMBLY.display(entry));
        }
    }

    @Override
    public void registerExclusionZones(ExclusionZones zones) {
        // The gate GUI's trigger/action source panels stick out of its window.
        zones.register(GuiGate.class, screen -> {
            Collection<Rectangle> areas = new ArrayList<>();
            for (IGuiElement element : screen.mainGui.shownElements) {
                if (element instanceof GuiElementStatementSource) {
                    areas.add(new Rectangle((int) element.getX(), (int) element.getY(), (int) element.getWidth(),
                            (int) element.getHeight()));
                }
            }
            return areas;
        });
    }
}
