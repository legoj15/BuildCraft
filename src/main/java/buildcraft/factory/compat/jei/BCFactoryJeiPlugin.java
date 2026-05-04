/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.compat.jei;

import java.util.ArrayList;
import java.util.List;

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

import buildcraft.api.recipes.BuildcraftRecipeRegistry;
import buildcraft.api.recipes.IRefineryRecipeManager.ICoolableRecipe;
import buildcraft.api.recipes.IRefineryRecipeManager.IDistillationRecipe;
import buildcraft.api.recipes.IRefineryRecipeManager.IHeatableRecipe;
import buildcraft.factory.BCFactoryItems;
import buildcraft.factory.BCFactoryMenuTypes;
import buildcraft.factory.client.gui.GuiAutoCraftItems;
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
    private static final Identifier UID = Identifier.parse("buildcraftunofficial:jei_plugin");

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
        registration.addRecipes(HeatExchangerRecipeTypes.PAIR, enumerateHeatExchangerPairs());
        registration.addRecipes(DistillerRecipeTypes.DISTILLER, enumerateDistillationRecipes());
    }

    /**
     * Cross every heatable with every coolable hotter than it. The runtime
     * pairing rule in {@code TileHeatExchange.craft()} is exactly
     * {@code coolable.heatFrom() > heatable.heatFrom()}, so each pair we
     * yield is a real operation the machine can run.
     */
    private static List<HeatExchangerRecipePair> enumerateHeatExchangerPairs() {
        List<HeatExchangerRecipePair> pairs = new ArrayList<>();
        for (IHeatableRecipe h : BuildcraftRecipeRegistry.refineryRecipes.getHeatableRegistry().getAllRecipes()) {
            for (ICoolableRecipe c : BuildcraftRecipeRegistry.refineryRecipes.getCoolableRegistry().getAllRecipes()) {
                if (c.heatFrom() > h.heatFrom()) {
                    pairs.add(new HeatExchangerRecipePair(h, c));
                }
            }
        }
        return pairs;
    }

    /**
     * Every registered distillation recipe with at least one valid input and
     * one valid output. The defensive filter guards against config-disabled
     * fluids slipping into the registry — the runtime would refuse to run
     * such a recipe anyway, so showing it in JEI would only mislead.
     */
    private static List<IDistillationRecipe> enumerateDistillationRecipes() {
        List<IDistillationRecipe> recipes = new ArrayList<>();
        for (IDistillationRecipe r : BuildcraftRecipeRegistry.refineryRecipes.getDistillationRegistry().getAllRecipes()) {
            if (r.in() == null || r.in().isEmpty()) continue;
            boolean hasGas = r.outGas() != null && !r.outGas().isEmpty();
            boolean hasLiquid = r.outLiquid() != null && !r.outLiquid().isEmpty();
            if (!hasGas && !hasLiquid) continue;
            recipes.add(r);
        }
        return recipes;
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
        // Auto Workbench: vanilla crafting catalyst with full-recipe transfer.
        registration.addCraftingStation(RecipeTypes.CRAFTING, BCFactoryItems.AUTOWORKBENCH_ITEM.get());
        // Heat Exchanger: catalyst for the paired-recipe category.
        registration.addCraftingStation(HeatExchangerRecipeTypes.PAIR, BCFactoryItems.HEAT_EXCHANGE.get());
        // Distiller: catalyst for the distillation recipe category.
        registration.addCraftingStation(DistillerRecipeTypes.DISTILLER, BCFactoryItems.DISTILLER.get());
    }
}
