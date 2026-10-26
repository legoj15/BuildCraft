/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.resources.Identifier;

import buildcraft.factory.BCFactoryItems;
import buildcraft.factory.BCFactoryMenuTypes;
import buildcraft.factory.client.gui.GuiAutoCraftItems;
import buildcraft.factory.client.gui.GuiDistiller;
import buildcraft.factory.client.gui.GuiHeatExchange;
import buildcraft.factory.compat.viewer.RefineryRecipeCollector;
import buildcraft.factory.container.ContainerAutoCraftItems;
import buildcraft.lib.compat.jei.BCGhostIngredientHandler;
import buildcraft.lib.compat.jei.BlueprintTransferHandler;

/**
 * JEI integration plugin for BuildCraft Factory.
 * Registers the Auto Workbench as a crafting station with recipe transfer
 * support and a clickable progress bar, surfaces every valid heat exchanger
 * pairing as its own recipe entry, and surfaces every distillation recipe
 * (including its MJ cost). Both refinery categories use a layout that mirrors
 * the in-world GUI of the corresponding machine.
 */
@JeiPlugin
public class BCFactoryJeiPlugin implements IModPlugin {
    private static final Identifier UID = Identifier.parse("buildcraftunofficial:factory_jei_plugin");

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper guiHelper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new HeatExchangerCategory(guiHelper));
        registration.addRecipeCategories(new DistillerCategory(guiHelper));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(HeatExchangerRecipeTypes.PAIR, RefineryRecipeCollector.heatExchangerPairs());
        registration.addRecipes(DistillerRecipeTypes.DISTILLER, RefineryRecipeCollector.distillationRecipes());
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
        // Distiller: move a bucket of the input fluid into its input container slot.
        registration.addRecipeTransferHandler(
                new DistillerTransferHandler(registration.getTransferHelper()),
                DistillerRecipeTypes.DISTILLER
        );
        // Heat Exchanger: move the hot + cold input buckets into its input container slots.
        registration.addRecipeTransferHandler(
                new HeatExchangerTransferHandler(registration.getTransferHelper()),
                HeatExchangerRecipeTypes.PAIR
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

        // Clicking the centre process schematic in the Distiller opens its JEI recipe view.
        // Region matches the running/stuck pipe overlay drawn at (61, 12) size 36x57, between
        // the input tank (ends x=60) and the output tanks (start x=98).
        registration.addRecipeClickArea(
                GuiDistiller.class,
                61, 12, 36, 57,
                DistillerRecipeTypes.DISTILLER
        );
        // Clicking the centre progress region in the Heat Exchanger opens its JEI recipe view.
        // Region matches the progress help rect at (73, 36) size 30x21, hand-tuned clear of all
        // four tank gauges.
        registration.addRecipeClickArea(
                GuiHeatExchange.class,
                73, 36, 30, 21,
                HeatExchangerRecipeTypes.PAIR
        );
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        // Auto Workbench: vanilla crafting catalyst with full-recipe transfer.
        registration.addCraftingStation(RecipeTypes.CRAFTING, BCFactoryItems.AUTOWORKBENCH_ITEM.get());
        // Heat Exchanger: catalyst for the paired-recipe category.
        registration.addCraftingStation(HeatExchangerRecipeTypes.PAIR, BCFactoryItems.HEAT_EXCHANGE.get());
        // Distiller: catalyst for the distillation recipe category.
        registration.addCraftingStation(DistillerRecipeTypes.DISTILLER, BCFactoryItems.DISTILLER.get());
    }
}
