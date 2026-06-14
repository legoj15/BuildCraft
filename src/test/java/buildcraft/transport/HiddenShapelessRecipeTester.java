/*
 * Copyright (c) 2026 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport;

import java.util.List;
import java.util.Optional;

import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapelessRecipe;

import buildcraft.transport.recipe.HiddenShapelessRecipe;

/**
 * Verifies the recipe-book cleanup wiring end-to-end against the datapack-loaded recipes:
 * <ul>
 *   <li>every pipe "downgrade" conversion — fluid&rarr;item, kinesis&rarr;item, and FE&rarr;kinesis — deserializes
 *       into {@link HiddenShapelessRecipe} and is {@link Recipe#isSpecial() special} (so the vanilla recipe book
 *       never learns them), while one still matches its input pipe and rejects a mismatched one (still craftable);</li>
 *   <li>the three pipe-sealant producers share the {@code buildcraft_pipe_sealant} group (so the recipe book
 *       collapses them into one entry).</li>
 * </ul>
 */
public class HiddenShapelessRecipeTester {

    private static Recipe<?> loaded(GameTestHelper helper, String id) {
        ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, Identifier.parse(id));
        Optional<RecipeHolder<?>> holder = helper.getLevel().recipeAccess().byKey(key);
        return holder.map(RecipeHolder::value).orElse(null);
    }

    public static void testPipeDowngradesHidden(GameTestHelper helper) {
        // One representative from each downgrade family — all must load as a special HiddenShapelessRecipe.
        String[] ids = {
                "buildcraftunofficial:pipe_cobble_item_from_power", // kinesis -> item
                "buildcraftunofficial:pipe_cobble_item_from_fluid", // fluid   -> item
                "buildcraftunofficial:pipe_cobble_power_from_rf",   // FE      -> kinesis
        };
        for (String id : ids) {
            Recipe<?> recipe = loaded(helper, id);
            if (recipe == null) {
                helper.fail(id + " did not load");
                return;
            }
            if (!(recipe instanceof HiddenShapelessRecipe)) {
                helper.fail(id + " should be a HiddenShapelessRecipe, got " + recipe.getClass().getSimpleName());
                return;
            }
            if (!recipe.isSpecial()) {
                helper.fail(id + " must be special so the recipe book never learns it");
                return;
            }
        }
        // The kinesis->item conversion still matches its own power pipe (craftable)...
        ShapelessRecipe shapeless = (ShapelessRecipe) loaded(helper, "buildcraftunofficial:pipe_cobble_item_from_power");
        CraftingInput right = CraftingInput.of(1, 1, List.of(new ItemStack(BCTransportItems.PIPE_COBBLE_POWER.get())));
        if (!shapeless.matches(right, helper.getLevel())) {
            helper.fail("cobble kinesis->item recipe should match a cobble power pipe");
            return;
        }
        // ...but not a different power pipe.
        CraftingInput wrong = CraftingInput.of(1, 1, List.of(new ItemStack(BCTransportItems.PIPE_STONE_POWER.get())));
        if (shapeless.matches(wrong, helper.getLevel())) {
            helper.fail("cobble recipe must NOT match a stone power pipe");
            return;
        }
        helper.succeed();
    }

    public static void testPipeSealantRecipesGrouped(GameTestHelper helper) {
        String[] ids = {
                "buildcraftunofficial:pipe_sealant",
                "buildcraftunofficial:pipe_sealant_from_green_dye",
                "buildcraftunofficial:residue_to_pipe_sealant",
        };
        for (String id : ids) {
            Recipe<?> recipe = loaded(helper, id);
            if (recipe == null) {
                helper.fail(id + " did not load");
                return;
            }
            if (!"buildcraft_pipe_sealant".equals(recipe.group())) {
                helper.fail(id + " should be in group 'buildcraft_pipe_sealant', was '" + recipe.group() + "'");
                return;
            }
        }
        helper.succeed();
    }
}
