/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.compat.jei;

import java.util.List;

import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;

import buildcraft.api.recipes.IRefineryRecipeManager.IDistillationRecipe;
import buildcraft.factory.BCFactoryMenuTypes;
import buildcraft.factory.container.ContainerDistiller;
import buildcraft.lib.compat.jei.AbstractBucketTransferHandler;

/**
 * JEI "+" recipe transfer for the Distiller: moves a filled bucket of the recipe's input fluid from
 * the player's inventory into the machine's input container slot (slot 0), which the tile then
 * drains into its input tank. See {@link AbstractBucketTransferHandler} for the shared machinery.
 */
public class DistillerTransferHandler extends AbstractBucketTransferHandler<ContainerDistiller, IDistillationRecipe> {

    public DistillerTransferHandler(IRecipeTransferHandlerHelper helper) {
        super(helper, ContainerDistiller.class, BCFactoryMenuTypes.DISTILLER::get);
    }

    @Override
    public RecipeType<IDistillationRecipe> getRecipeType() {
        return DistillerRecipeTypes.DISTILLER;
    }

    @Override
    protected List<BucketSlot> getRequiredBuckets(IDistillationRecipe recipe) {
        return List.of(BucketSlot.of(0, recipe.in()));
    }
}
