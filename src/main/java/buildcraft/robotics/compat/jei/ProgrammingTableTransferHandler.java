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

import buildcraft.robotics.compat.viewer.ProgrammingTableRecipeTransfer;
import buildcraft.robotics.container.ContainerProgrammingTable;
import buildcraft.robotics.compat.viewer.ProgrammingRecipeEntry;

/**
 * JEI "+" recipe transfer for the Programming Table: moves a blank board from the player's inventory into the table
 * and selects the option the JEI entry shows. The work is the shared {@link ProgrammingTableRecipeTransfer} core.
 */
public class ProgrammingTableTransferHandler
        extends AbstractTransferHandler<ContainerProgrammingTable, ProgrammingRecipeEntry> {

    public ProgrammingTableTransferHandler(IRecipeTransferHandlerHelper helper) {
        super(helper, new ProgrammingTableRecipeTransfer());
    }

    @Override
    //? if >=1.21.10 {
    public IRecipeType<ProgrammingRecipeEntry> getRecipeType() {
    //?} else {
    /*public RecipeType<ProgrammingRecipeEntry> getRecipeType() {*/
    //?}
        return ProgrammingRecipeJeiTypes.PROGRAMMING;
    }
}
