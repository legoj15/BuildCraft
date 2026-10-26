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

import buildcraft.factory.compat.viewer.HeatExchangerRecipePair;
import buildcraft.factory.compat.viewer.HeatExchangerRecipeTransfer;
import buildcraft.factory.container.ContainerHeatExchange;
import buildcraft.lib.compat.jei.AbstractTransferHandler;

/**
 * JEI "+" recipe transfer for the Heat Exchanger: moves a filled bucket of each input fluid into the machine's input
 * container slots. The work is the shared {@link HeatExchangerRecipeTransfer} core.
 */
public class HeatExchangerTransferHandler
        extends AbstractTransferHandler<ContainerHeatExchange, HeatExchangerRecipePair> {

    public HeatExchangerTransferHandler(IRecipeTransferHandlerHelper helper) {
        super(helper, new HeatExchangerRecipeTransfer());
    }

    @Override
    //? if >=1.21.10 {
    public IRecipeType<HeatExchangerRecipePair> getRecipeType() {
    //?} else {
    /*public RecipeType<HeatExchangerRecipePair> getRecipeType() {*/
    //?}
        return HeatExchangerRecipeTypes.PAIR;
    }
}
