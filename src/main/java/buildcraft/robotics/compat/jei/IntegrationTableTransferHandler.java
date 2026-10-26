/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.compat.jei;

import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
//? if >=1.21.10 {
import mezz.jei.api.recipe.types.IRecipeType;
//?} else {
/*import mezz.jei.api.recipe.RecipeType;*/
//?}

import buildcraft.lib.compat.jei.AbstractTransferHandler;

import buildcraft.robotics.compat.viewer.IntegrationTableRecipeTransfer;
import buildcraft.robotics.recipe.IntegrationRecipeJei;
import buildcraft.silicon.container.ContainerIntegrationTable;

/**
 * JEI "+" recipe transfer for the Integration Table: moves a blank robot into the centre slot and the entry's board
 * into a ring slot. The work is the shared {@link IntegrationTableRecipeTransfer} core.
 */
public class IntegrationTableTransferHandler
        extends AbstractTransferHandler<ContainerIntegrationTable, IntegrationRecipeJei> {

    public IntegrationTableTransferHandler(IRecipeTransferHandlerHelper helper) {
        super(helper, new IntegrationTableRecipeTransfer());
    }

    @Override
    //? if >=1.21.10 {
    public IRecipeType<IntegrationRecipeJei> getRecipeType() {
    //?} else {
    /*public RecipeType<IntegrationRecipeJei> getRecipeType() {*/
    //?}
        return IntegrationRecipeJeiTypes.INTEGRATION;
    }
}
