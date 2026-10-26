/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.robotics.compat.rei;

import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.client.registry.screen.ScreenRegistry;
import me.shedaniel.rei.api.client.registry.transfer.TransferHandlerRegistry;
import me.shedaniel.rei.api.common.util.EntryStacks;
import me.shedaniel.rei.forge.REIPluginClient;

import buildcraft.lib.compat.rei.ReiRecipeTransferHandler;
import buildcraft.lib.compat.rei.ReiScreens;
import buildcraft.robotics.BCRoboticsRecipes;
import buildcraft.robotics.client.gui.GuiProgrammingTable;
import buildcraft.robotics.compat.viewer.IntegrationTableRecipeTransfer;
import buildcraft.robotics.compat.viewer.ProgrammingTableRecipeTransfer;
import buildcraft.robotics.container.ProgrammingTableLayout;
import buildcraft.robotics.recipe.IntegrationRecipeCollector;
import buildcraft.robotics.recipe.IntegrationRecipeJei;
import buildcraft.robotics.recipe.ProgrammingRecipeCollector;
import buildcraft.robotics.recipe.ProgrammingRecipeJei;
import buildcraft.silicon.BCSiliconItems;
import buildcraft.silicon.container.IntegrationTableLayout;
import buildcraft.silicon.gui.GuiIntegrationTable;

/**
 * REI integration for robotics, REI's twin of {@code BCRoboticsJeiPlugin}: the Programming and Integration Table
 * categories, both tables as workstations, click areas on their energy bars, and "+" transfers that move a blank board
 * (or a blank robot and a board) into the table. The board and robot comparators are in
 * {@link BCRoboticsReiCommonPlugin}.
 */
@REIPluginClient
public class BCRoboticsReiPlugin implements REIClientPlugin {

    private static final ProgrammingTableReiCategory PROGRAMMING = new ProgrammingTableReiCategory();
    private static final IntegrationTableReiCategory INTEGRATION = new IntegrationTableReiCategory();

    @Override
    public void registerCategories(CategoryRegistry registry) {
        registry.add(PROGRAMMING);
        registry.add(INTEGRATION);
        registry.addWorkstations(ProgrammingTableReiCategory.ID,
                EntryStacks.of(BCSiliconItems.PROGRAMMING_TABLE.get()));
        registry.addWorkstations(IntegrationTableReiCategory.ID,
                EntryStacks.of(BCSiliconItems.INTEGRATION_TABLE.get()));
    }

    @Override
    public void registerDisplays(DisplayRegistry registry) {
        // The registry is filled at ServerAboutToStart (integrated server) or LoggingIn (multiplayer client); the
        // order of the latter and REI's reload is not ours to rely on, and the call is once-per-JVM.
        BCRoboticsRecipes.ensureInitialized();
        for (ProgrammingRecipeJei recipe : ProgrammingRecipeCollector.collect()) {
            registry.add(PROGRAMMING.display(recipe));
        }
        for (IntegrationRecipeJei recipe : IntegrationRecipeCollector.collect()) {
            registry.add(INTEGRATION.display(recipe));
        }
    }

    @Override
    public void registerTransferHandlers(TransferHandlerRegistry registry) {
        registry.register(new ReiRecipeTransferHandler<>(ProgrammingTableReiCategory.ID,
                new ProgrammingTableRecipeTransfer()));
        registry.register(new ReiRecipeTransferHandler<>(IntegrationTableReiCategory.ID,
                new IntegrationTableRecipeTransfer()));
    }

    @Override
    public void registerScreens(ScreenRegistry registry) {
        // Each table's laser-energy bar, clear of the slots and the option grid the GUI hit-tests itself.
        ReiScreens.clickArea(registry, GuiProgrammingTable.class, ProgrammingTableLayout.BAR_X,
                ProgrammingTableLayout.BAR_Y, ProgrammingTableLayout.BAR_W, ProgrammingTableLayout.BAR_H,
                ProgrammingTableReiCategory.ID);
        ReiScreens.clickArea(registry, GuiIntegrationTable.class, IntegrationTableLayout.BAR_X,
                IntegrationTableLayout.BAR_Y, IntegrationTableLayout.BAR_W, IntegrationTableLayout.BAR_H,
                IntegrationTableReiCategory.ID);
    }
}
