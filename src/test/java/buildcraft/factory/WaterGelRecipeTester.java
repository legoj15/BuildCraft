/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory;

import java.util.List;
import java.util.Optional;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * Pins the restored {@code water_gel_to_bucket} crafting recipe: Gelled Water stacked on an empty
 * bucket yields a vanilla Water Bucket.
 *
 * <p>This is the <em>only</em> consumer of Gelled Water. 1.12.2 shipped the recipe, but the modern
 * ports silently dropped it — it was carried through only in the legacy {@code assets/<ns>/recipes/}
 * path that current Minecraft never reads, then deleted with the legacy-namespace sweep — which left
 * Gelled Water a dead-end drop with no purpose. This test guards against that regression returning:
 * a malformed recipe JSON, a wrong item id, or a future resource sweep would make
 * {@link net.minecraft.world.item.crafting.RecipeManager#getRecipeFor} come back empty here.
 */
public final class WaterGelRecipeTester {

    private WaterGelRecipeTester() {}

    public static void water_gel_to_bucket_recipe(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // Recipe pattern is 1 wide x 2 tall: Gelled Water on top, empty Bucket below.
        CraftingInput input = CraftingInput.of(1, 2, List.of(
            new ItemStack(BCFactoryItems.GELLED_WATER.get()),
            new ItemStack(Items.BUCKET)));
        Optional<RecipeHolder<CraftingRecipe>> match = level.getServer().getRecipeManager()
            .getRecipeFor(RecipeType.CRAFTING, input, level);
        if (match.isEmpty()) {
            helper.fail("water_gel_to_bucket didn't match Gelled Water + Bucket — the recipe JSON is "
                + "missing, malformed, or uses a wrong item id. Gelled Water has no other use, so this "
                + "regressing means it is a dead-end drop again.");
            return;
        }
        ItemStack out = match.get().value().assemble(input);
        if (!out.is(Items.WATER_BUCKET)) {
            helper.fail("water_gel_to_bucket produced " + out + " instead of a Water Bucket.");
            return;
        }
        helper.succeed();
    }
}
