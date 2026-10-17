/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import net.minecraft.resources.Identifier;

import buildcraft.lib.compat.jei.JeiSubtypes;

import buildcraft.robotics.BCRoboticsItems;
import buildcraft.robotics.BCRoboticsRecipes;
import buildcraft.robotics.client.gui.GuiProgrammingTable;
import buildcraft.robotics.container.ProgrammingTableLayout;
import buildcraft.robotics.recipe.IntegrationRecipeCollector;
import buildcraft.robotics.recipe.ProgrammingRecipeCollector;
import buildcraft.robotics.recipe.RoboticsJeiSubtypes;
import buildcraft.silicon.BCSiliconItems;
import buildcraft.silicon.container.IntegrationTableLayout;
import buildcraft.silicon.gui.GuiIntegrationTable;

/**
 * JEI integration for robotics: the Programming Table category (every board program, the cost, and the grid cell
 * to click), the Integration Table category (blank robot + board = that robot, so a functional robot has a recipe),
 * both tables as catalysts, click-throughs on their GUIs' power bars, and subtype keys that tell
 * boards and robots apart by program (robots also by charged vs drained) — without those, JEI merges every board into
 * one entry (and every robot into another) because the program rides in {@code CUSTOM_DATA}. See
 * {@link RoboticsJeiSubtypes}.
 */
@JeiPlugin
public class BCRoboticsJeiPlugin implements IModPlugin {
    private static final Identifier UID = Identifier.parse("buildcraftunofficial:robotics_jei_plugin");

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        JeiSubtypes.register(registration, BCRoboticsItems.REDSTONE_BOARD.get(), RoboticsJeiSubtypes::boardKey);
        JeiSubtypes.register(registration, BCRoboticsItems.ROBOT.get(), RoboticsJeiSubtypes::robotKey);
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new ProgrammingTableCategory(registration.getJeiHelpers().getGuiHelper()));
        registration.addRecipeCategories(new IntegrationTableCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        // The registry is filled at ServerAboutToStart (integrated server) or LoggingIn (multiplayer client); the
        // relative order of the latter and JEI's own start is not ours to rely on, and the call is once-per-JVM.
        BCRoboticsRecipes.ensureInitialized();
        registration.addRecipes(ProgrammingRecipeJeiTypes.PROGRAMMING, ProgrammingRecipeCollector.collect());
        registration.addRecipes(IntegrationRecipeJeiTypes.INTEGRATION, IntegrationRecipeCollector.collect());
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addCraftingStation(ProgrammingRecipeJeiTypes.PROGRAMMING, BCSiliconItems.PROGRAMMING_TABLE.get());
        registration.addCraftingStation(IntegrationRecipeJeiTypes.INTEGRATION, BCSiliconItems.INTEGRATION_TABLE.get());
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        // "+" on a Programming Table entry puts a blank board in the table and selects that entry's option;
        // on an Integration Table entry it puts a blank robot in the centre and the entry's board in the ring.
        // Both move real items out of the player's inventory (server-side, see RoboticsTableTransfer).
        registration.addRecipeTransferHandler(
                new ProgrammingTableTransferHandler(registration.getTransferHelper()),
                ProgrammingRecipeJeiTypes.PROGRAMMING);
        registration.addRecipeTransferHandler(
                new IntegrationTableTransferHandler(registration.getTransferHelper()),
                IntegrationRecipeJeiTypes.INTEGRATION);
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        // The table GUI's laser-energy bar opens the category — clear of the option grid, which the GUI hit-tests
        // itself. The bar's rectangle is the layout's, the same one the GUI draws and shows help for.
        registration.addRecipeClickArea(GuiProgrammingTable.class, ProgrammingTableLayout.BAR_X,
                ProgrammingTableLayout.BAR_Y, ProgrammingTableLayout.BAR_W, ProgrammingTableLayout.BAR_H,
                ProgrammingRecipeJeiTypes.PROGRAMMING);
        // Likewise the Integration Table's bar, clear of its slots.
        registration.addRecipeClickArea(GuiIntegrationTable.class, IntegrationTableLayout.BAR_X,
                IntegrationTableLayout.BAR_Y, IntegrationTableLayout.BAR_W, IntegrationTableLayout.BAR_H,
                IntegrationRecipeJeiTypes.INTEGRATION);
    }
}
