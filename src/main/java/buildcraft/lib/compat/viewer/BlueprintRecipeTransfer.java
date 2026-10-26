/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.compat.viewer;

import net.minecraft.resources.Identifier;

import buildcraft.lib.gui.BCContainer;
import buildcraft.lib.gui.ContainerBC_Neptune;

/**
 * The "+" recipe transfer of BuildCraft's two phantom-grid crafting tables (Auto Workbench, Advanced Crafting Table),
 * shared by JEI's {@code BlueprintTransferHandler} and REI's {@code ReiCraftingTableSupport}. The blueprint slots
 * accept any item as a template, so there is nothing to check: a press sends the recipe's id, and the server looks the
 * recipe up and calls {@code CraftingUtil.placeRecipeInBlueprint()}. This avoids the vanilla
 * {@code ServerboundPlaceRecipePacket}, which needs a {@code RecipeDisplayId} neither viewer hands over.
 */
public final class BlueprintRecipeTransfer {
    private BlueprintRecipeTransfer() {}

    public static void send(BCContainer container, Identifier recipeId) {
        String recipeIdStr = recipeId.toString();
        container.sendMessage(ContainerBC_Neptune.NET_JEI_RECIPE_TRANSFER, buf -> buf.writeUtf(recipeIdStr));
    }
}
