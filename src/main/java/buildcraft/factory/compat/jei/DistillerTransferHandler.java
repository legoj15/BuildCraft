/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.compat.jei;

import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
//? if >=1.21.10 {
import mezz.jei.api.recipe.types.IRecipeType;
//?} else {
/*import mezz.jei.api.recipe.RecipeType;*/
//?}

import buildcraft.api.recipes.IRefineryRecipeManager.IDistillationRecipe;
import buildcraft.factory.compat.viewer.DistillerRecipeTransfer;
import buildcraft.factory.container.ContainerDistiller;
import buildcraft.lib.compat.jei.AbstractTransferHandler;

/**
 * JEI "+" recipe transfer for the Distiller: moves a filled bucket of the recipe's input fluid from the player's
 * inventory into the machine's input container slot. The work is the shared {@link DistillerRecipeTransfer} core.
 */
public class DistillerTransferHandler extends AbstractTransferHandler<ContainerDistiller, IDistillationRecipe> {

    public DistillerTransferHandler(IRecipeTransferHandlerHelper helper) {
        super(helper, new DistillerRecipeTransfer());
    }

    @Override
    //? if >=1.21.10 {
    public IRecipeType<IDistillationRecipe> getRecipeType() {
    //?} else {
    /*public RecipeType<IDistillationRecipe> getRecipeType() {*/
    //?}
        return DistillerRecipeTypes.DISTILLER;
    }
}
