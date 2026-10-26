/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.compat.jei;

import java.util.Optional;

import javax.annotation.Nullable;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.entity.player.Player;

import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
//? if >=26.2 {
import mezz.jei.api.recipe.transfer.IRecipeTransferContext;
//?}
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
//? if >=1.21.10 {
import mezz.jei.api.recipe.types.IRecipeType;
//?} else {
/*import mezz.jei.api.recipe.RecipeType;*/
//?}

import buildcraft.lib.compat.viewer.BlueprintRecipeTransfer;
import buildcraft.lib.gui.BCContainer;
import buildcraft.lib.misc.RegistryKeyUtil;

/**
 * A reusable JEI recipe transfer handler for BuildCraft machines that use
 * phantom/blueprint slots. When the user clicks JEI's "+" button, this handler
 * sends the recipe's id to the server through the shared {@link BlueprintRecipeTransfer}
 * (REI's crafting-table support sends the same message).
 *
 * @param <C> the container type
 */
public class BlueprintTransferHandler<C extends AbstractContainerMenu>
        implements IRecipeTransferHandler<C, RecipeHolder<CraftingRecipe>> {

    private final Class<? extends C> containerClass;
    private final MenuType<C> menuType;

    public BlueprintTransferHandler(Class<? extends C> containerClass, MenuType<C> menuType) {
        this.containerClass = containerClass;
        this.menuType = menuType;
    }

    @Override
    public Class<? extends C> getContainerClass() {
        return containerClass;
    }

    @Override
    public Optional<MenuType<C>> getMenuType() {
        return Optional.of(menuType);
    }

    @Override
    //? if >=1.21.10 {
    public IRecipeType<RecipeHolder<CraftingRecipe>> getRecipeType() {
    //?} else {
    /*public RecipeType<RecipeHolder<CraftingRecipe>> getRecipeType() {*/
    //?}
        return RecipeTypes.CRAFTING;
    }

    //? if >=26.2 {
    @Override
    @Nullable
    public IRecipeTransferError transferRecipe(IRecipeTransferContext<RecipeHolder<CraftingRecipe>, C> context,
            boolean doTransfer) {
        return transfer(context.getContainer(), context.getRecipe(), doTransfer);
    }
    //?}

    // JEI 30.30+ (26.2) deprecates this for removal in favour of the context overload above but keeps it
    // abstract, so it must stay; it is also the only entry point on older JEI builds.
    @Override
    @Nullable
    @SuppressWarnings("removal")
    public IRecipeTransferError transferRecipe(
            C container,
            RecipeHolder<CraftingRecipe> recipe,
            IRecipeSlotsView recipeSlots,
            Player player,
            boolean maxTransfer,
            boolean doTransfer) {
        return transfer(container, recipe, doTransfer);
    }

    @Nullable
    private IRecipeTransferError transfer(C container, RecipeHolder<CraftingRecipe> recipe, boolean doTransfer) {
        if (!doTransfer) {
            // Simulation pass — no errors, recipe is always transferable
            // (blueprint slots accept any item as a phantom template)
            return null;
        }

        if (container instanceof BCContainer bcContainer) {
            BlueprintRecipeTransfer.send(bcContainer, RegistryKeyUtil.id(recipe.id()));
        }

        return null;
    }
}
