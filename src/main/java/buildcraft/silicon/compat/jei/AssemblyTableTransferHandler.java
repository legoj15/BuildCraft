/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon.compat.jei;

import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
//? if >=1.21.10 {
import mezz.jei.api.recipe.types.IRecipeType;
//?} else {
/*import mezz.jei.api.recipe.RecipeType;*/
//?}

import buildcraft.lib.compat.jei.AbstractTransferHandler;

import buildcraft.silicon.compat.viewer.AssemblyRecipeEntry;
import buildcraft.silicon.compat.viewer.AssemblyTableRecipeTransfer;
import buildcraft.silicon.container.ContainerAssemblyTable;

/**
 * JEI "+" recipe transfer for the Assembly Table: moves the recipe's real ingredients from the player's inventory into
 * the table's input slots (shift-click: everything available). The work is the shared
 * {@link AssemblyTableRecipeTransfer} core.
 */
public class AssemblyTableTransferHandler
        extends AbstractTransferHandler<ContainerAssemblyTable, AssemblyRecipeEntry> {

    public AssemblyTableTransferHandler(IRecipeTransferHandlerHelper helper) {
        super(helper, new AssemblyTableRecipeTransfer());
    }

    @Override
    //? if >=1.21.10 {
    public IRecipeType<AssemblyRecipeEntry> getRecipeType() {
    //?} else {
    /*public RecipeType<AssemblyRecipeEntry> getRecipeType() {*/
    //?}
        return AssemblyRecipeJeiTypes.ASSEMBLY;
    }
}
