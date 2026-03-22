/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.resources.Identifier;

import buildcraft.factory.BCFactoryItems;
import buildcraft.factory.BCFactoryMenuTypes;
import buildcraft.factory.client.gui.GuiAutoCraftItems;
import buildcraft.factory.container.ContainerAutoCraftItems;
import buildcraft.lib.compat.jei.BCGhostIngredientHandler;
import buildcraft.lib.compat.jei.BlueprintTransferHandler;

/**
 * JEI integration plugin for BuildCraft Factory.
 * Registers the Auto Workbench as a crafting station with recipe transfer
 * support and a clickable progress bar.
 */
@JeiPlugin
public class BCFactoryJeiPlugin implements IModPlugin {
    private static final Identifier UID = Identifier.parse("buildcraftfactory:jei_plugin");

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        // Register the phantom-slot blueprint transfer handler for the Auto Workbench.
        // Clicking JEI's "+" button will set the 3x3 blueprint grid via handlePlacement().
        registration.addRecipeTransferHandler(
                new BlueprintTransferHandler<>(
                        ContainerAutoCraftItems.class,
                        BCFactoryMenuTypes.AUTO_WORKBENCH_ITEMS.get()
                ),
                RecipeTypes.CRAFTING
        );
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        // Clicking the progress arrow in the Auto Workbench opens JEI's crafting recipe view.
        // Coordinates match the ICON_PROGRESS drawn at (90, 47) with size 23x10.
        registration.addRecipeClickArea(
                GuiAutoCraftItems.class,
                90, 47, 23, 10,
                RecipeTypes.CRAFTING
        );
        // Allow dragging ingredients from JEI onto phantom blueprint slots
        registration.addGhostIngredientHandler(GuiAutoCraftItems.class, new BCGhostIngredientHandler<>());
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        // Register the Auto Workbench block as a crafting catalyst.
        // Pressing "U" (uses) on it in JEI shows all crafting recipes.
        registration.addCraftingStation(RecipeTypes.CRAFTING, BCFactoryItems.AUTOWORKBENCH_ITEM.get());
    }
}
