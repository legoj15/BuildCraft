/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.compat.jei;

import java.util.List;

import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;

import buildcraft.factory.BCFactoryMenuTypes;
import buildcraft.factory.container.ContainerHeatExchange;
import buildcraft.lib.compat.jei.AbstractBucketTransferHandler;

/**
 * JEI "+" recipe transfer for the Heat Exchanger: moves a filled bucket of the heatable input into
 * container slot 0 (hot in) and a bucket of the coolable input into slot 1 (cold in), which the
 * tile drains into its tanks. Enabled only when the player holds both buckets. See
 * {@link AbstractBucketTransferHandler} for the shared machinery.
 */
public class HeatExchangerTransferHandler
        extends AbstractBucketTransferHandler<ContainerHeatExchange, HeatExchangerRecipePair> {

    public HeatExchangerTransferHandler(IRecipeTransferHandlerHelper helper) {
        super(helper, ContainerHeatExchange.class, BCFactoryMenuTypes.HEAT_EXCHANGE::get);
    }

    @Override
    public RecipeType<HeatExchangerRecipePair> getRecipeType() {
        return HeatExchangerRecipeTypes.PAIR;
    }

    @Override
    protected List<BucketSlot> getRequiredBuckets(HeatExchangerRecipePair pair) {
        // Slot→tank mapping (see TileHeatExchange's drain logic): container slot 0 → END.tankInput
        // drains the HOT fluid (coolable.in() — the fluid being cooled); slot 1 → START.tankInput
        // drains the COLD fluid (heatable.in() — the fluid being heated). "heatable"/"coolable" name
        // the role of the fluid, which is the opposite of its temperature, so the buckets cross
        // relative to those names.
        return List.of(
            BucketSlot.of(0, pair.coolable().in()), // hot in
            BucketSlot.of(1, pair.heatable().in())  // cold in
        );
    }
}
