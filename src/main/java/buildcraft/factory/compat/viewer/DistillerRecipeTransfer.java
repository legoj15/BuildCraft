/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory.compat.viewer;

import java.util.List;

import buildcraft.api.recipes.IRefineryRecipeManager.IDistillationRecipe;
import buildcraft.factory.BCFactoryMenuTypes;
import buildcraft.factory.container.ContainerDistiller;
import buildcraft.lib.compat.viewer.BucketRecipeTransfer;

/**
 * "+" recipe transfer for the Distiller, shared by JEI and REI: moves a filled bucket of the recipe's input fluid from
 * the player's inventory into the machine's input container slot (slot 0), which the tile then drains into its input
 * tank. See {@link BucketRecipeTransfer} for the shared machinery.
 */
public final class DistillerRecipeTransfer extends BucketRecipeTransfer<ContainerDistiller, IDistillationRecipe> {

    public DistillerRecipeTransfer() {
        super(ContainerDistiller.class, BCFactoryMenuTypes.DISTILLER::get);
    }

    @Override
    protected List<BucketSlot> getRequiredBuckets(IDistillationRecipe recipe) {
        return List.of(BucketSlot.of(0, recipe.in()));
    }
}
