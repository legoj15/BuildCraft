/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;

import buildcraft.transport.recipe.DyedPipeRecipe;

/** Guards against silent drift between the base pipe crafting recipes (the JSON {@code [material,
 *  colourless-glass, material]} recipes under {@code data/buildcraftunofficial/recipe/}) and
 *  {@link DyedPipeRecipe}'s hand-maintained material table. The base recipe shares the pipe item's id
 *  (its JSON is named after the pipe item), so for every dyed-table row this test looks up the base recipe
 *  and asserts its two non-glass material ingredients accept exactly the same item set the dyed row does
 *  (in either left/right orientation). When a base recipe's material ingredient is retagged without
 *  updating the dyed table — as happened in commit 57eec3c3d for stone/cobble/quartz — this fails loudly
 *  instead of letting the dyed and plain craft paths silently diverge. */
public class DyedPipeTableDriftTester {

    public static void testTableMatchesBaseRecipes(GameTestHelper helper) {
        ItemStack colourlessGlass = new ItemStack(Items.GLASS);
        StringBuilder problems = new StringBuilder();

        for (DyedPipeRecipe.TableRow row : DyedPipeRecipe.tableForTest()) {
            Identifier pipeId = BuiltInRegistries.ITEM.getKey(row.pipe());
            RecipeHolder<?> base = baseRecipe(helper, pipeId);
            if (base == null) {
                problems.append("\n- ").append(pipeId).append(": no base recipe registered at that id");
                continue;
            }
            if (!(base.value() instanceof ShapedRecipe shaped)) {
                problems.append("\n- ").append(pipeId).append(": base recipe is not a shaped recipe");
                continue;
            }
            List<Ingredient> materials = materialIngredients(shaped, colourlessGlass);
            if (materials.size() != 2) {
                problems.append("\n- ").append(pipeId)
                        .append(": expected 2 non-glass material ingredients, found ").append(materials.size());
                continue;
            }
            Set<Item> baseLeft = itemsOf(materials.get(0));
            Set<Item> baseRight = itemsOf(materials.get(1));
            Set<Item> dyedLeft = itemsOf(row.left());
            Set<Item> dyedRight = itemsOf(row.right());
            boolean aligned = (baseLeft.equals(dyedLeft) && baseRight.equals(dyedRight))
                    || (baseLeft.equals(dyedRight) && baseRight.equals(dyedLeft));
            if (!aligned) {
                problems.append("\n- ").append(pipeId)
                        .append(": dyed-pipe table material ingredient(s) differ from the base recipe")
                        .append("\n    base: ").append(ids(baseLeft)).append("  |  ").append(ids(baseRight))
                        .append("\n    dyed: ").append(ids(dyedLeft)).append("  |  ").append(ids(dyedRight));
            }
        }

        if (!problems.isEmpty()) {
            helper.fail("DyedPipeRecipe.entries() has drifted from the base pipe recipes — update the table to match:"
                    + problems);
            return;
        }
        helper.succeed();
    }

    /** The base pipe recipe shares the pipe item's id (the recipe JSON is named after the pipe item). */
    private static RecipeHolder<?> baseRecipe(GameTestHelper helper, Identifier pipeId) {
        //? if >=1.21.10 {
        return helper.getLevel().recipeAccess()
                .byKey(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, pipeId))
                .orElse(null);
        //?} else {
        /*return helper.getLevel().getServer().getRecipeManager().byKey(pipeId).orElse(null);*/
        //?}
    }

    /** The non-glass material ingredients of the {@code [material, glass, material]} pattern. */
    private static List<Ingredient> materialIngredients(ShapedRecipe shaped, ItemStack colourlessGlass) {
        //? if >=1.21.10 {
        return shaped.getIngredients().stream().flatMap(Optional::stream)
                .filter(ing -> !ing.test(colourlessGlass)).toList();
        //?} else {
        /*return shaped.getIngredients().stream()
                .filter(ing -> !ing.isEmpty() && !ing.test(colourlessGlass)).toList();*/
        //?}
    }

    private static Set<Item> itemsOf(Ingredient ingredient) {
        //? if >=1.21.10 {
        return ingredient.items().map(net.minecraft.core.Holder::value).collect(Collectors.toSet());
        //?} else {
        /*return java.util.Arrays.stream(ingredient.getItems())
                .map(ItemStack::getItem).collect(Collectors.toSet());*/
        //?}
    }

    private static String ids(Set<Item> items) {
        return items.stream().map(i -> BuiltInRegistries.ITEM.getKey(i).toString()).sorted()
                .collect(Collectors.joining(", "));
    }
}
