/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.compat.jei;

import java.util.Optional;

import javax.annotation.Nullable;

import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.entity.player.Player;

import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
//? if >=1.21.10 {
import mezz.jei.api.recipe.types.IRecipeType;
//?} else {
/*import mezz.jei.api.recipe.RecipeType;*/
//?}

import buildcraft.lib.gui.BCContainer;
import buildcraft.lib.gui.ContainerBC_Neptune;
import buildcraft.lib.misc.RegistryKeyUtil;

/**
 * A reusable JEI recipe transfer handler for BuildCraft machines that use
 * phantom/blueprint slots. When the user clicks JEI's "+" button, this handler
 * sends a custom container message with the recipe's resource location to the
 * server, which looks up the recipe and calls
 * {@code CraftingUtil.placeRecipeInBlueprint()} to set the phantom blueprint slots.
 *
 * <p>This avoids the vanilla {@code ServerboundPlaceRecipePacket} which requires
 * a {@code RecipeDisplayId} that we don't have from JEI's API.
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

    @Override
    @Nullable
    public IRecipeTransferError transferRecipe(
            C container,
            RecipeHolder<CraftingRecipe> recipe,
            IRecipeSlotsView recipeSlots,
            Player player,
            boolean maxTransfer,
            boolean doTransfer) {

        if (!doTransfer) {
            // Simulation pass — no errors, recipe is always transferable
            // (blueprint slots accept any item as a phantom template)
            return null;
        }

        if (container instanceof BCContainer bcContainer) {
            // Send a custom container message with the recipe's resource location.
            // The server-side handler in ContainerBCCrafting will look up the recipe
            // and call CraftingUtil.placeRecipeInBlueprint().
            String recipeIdStr = RegistryKeyUtil.id(recipe.id()).toString();
            bcContainer.sendMessage(ContainerBC_Neptune.NET_JEI_RECIPE_TRANSFER, buf -> {
                buf.writeUtf(recipeIdStr);
            });
        }

        return null;
    }
}
