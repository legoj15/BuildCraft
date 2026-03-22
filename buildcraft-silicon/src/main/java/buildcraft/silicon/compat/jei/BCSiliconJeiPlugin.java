/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.resources.Identifier;

import buildcraft.lib.compat.jei.BCGhostIngredientHandler;
import buildcraft.lib.compat.jei.BlueprintTransferHandler;
import buildcraft.silicon.BCSiliconItems;
import buildcraft.silicon.BCSiliconMenuTypes;
import buildcraft.silicon.container.ContainerAdvancedCraftingTable;
import buildcraft.silicon.gui.GuiAdvancedCraftingTable;

/**
 * JEI integration plugin for BuildCraft Silicon.
 * Registers the Advanced Crafting Table as a crafting station with recipe
 * transfer support and a clickable progress bar.
 */
@JeiPlugin
public class BCSiliconJeiPlugin implements IModPlugin {
    private static final Identifier UID = Identifier.parse("buildcraftsilicon:jei_plugin");

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        // Register the phantom-slot blueprint transfer handler for the Advanced Crafting Table.
        // Clicking JEI's "+" button will set the 3x3 blueprint grid via handlePlacement().
        registration.addRecipeTransferHandler(
                new BlueprintTransferHandler<>(
                        ContainerAdvancedCraftingTable.class,
                        BCSiliconMenuTypes.ADVANCED_CRAFTING_TABLE.get()
                ),
                RecipeTypes.CRAFTING
        );
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        // Clicking the vertical progress bar in the Advanced Crafting Table opens JEI's
        // crafting recipe view. Coordinates match RECT_PROGRESS at (164, 7) with size 4x70.
        registration.addRecipeClickArea(
                GuiAdvancedCraftingTable.class,
                164, 7, 4, 70,
                RecipeTypes.CRAFTING
        );
        // Allow dragging ingredients from JEI onto phantom blueprint slots
        registration.addGhostIngredientHandler(GuiAdvancedCraftingTable.class, new BCGhostIngredientHandler<>());
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        // Register the Advanced Crafting Table block as a crafting catalyst.
        // Pressing "U" (uses) on it in JEI shows all crafting recipes.
        registration.addCraftingStation(RecipeTypes.CRAFTING, BCSiliconItems.ADVANCED_CRAFTING_TABLE.get());
    }
}
