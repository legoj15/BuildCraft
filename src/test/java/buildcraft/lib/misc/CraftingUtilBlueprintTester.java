/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.misc;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;

import buildcraft.lib.tile.item.ItemHandlerSimple;

/**
 * Regression guard for {@link CraftingUtil#placeRecipeInBlueprint} resolving TAG-based ingredients.
 *
 * <p>JEI's "+" button and the vanilla recipe book populate the Advanced Crafting Table / Auto
 * Workbench phantom blueprint through {@code placeRecipeInBlueprint}, which resolves each
 * ingredient's {@code SlotDisplay} to a representative stack. In MC 26.1 a {@code TagSlotDisplay}
 * resolves to an empty stream unless the {@code ContextMap} carries
 * {@code SlotDisplayContext.REGISTRIES}; before the fix the placement context omitted it, so every
 * tag-based ingredient (most recipes — including all the gears) left its phantom slot blank.
 *
 * <p>{@code gear_stone} is an all-tag shaped recipe — pattern {@code " c "/"cGc"/" c "} with
 * {@code c = #minecraft:stone_crafting_materials} and {@code G = #c:gears/wooden}. After placement
 * the centre slot (index 4) must hold the wood gear (the {@code #c:gears/wooden} tag has exactly
 * one member) and an edge slot (index 1) must hold a stone material. Either blank means the
 * registries regression is back.
 */
public final class CraftingUtilBlueprintTester {

    private CraftingUtilBlueprintTester() {}

    public static void testTagIngredientsResolveIntoBlueprint(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        var key = ResourceKey.create(Registries.RECIPE, Identifier.parse("buildcraftunofficial:gear_stone"));
        var holder = level.recipeAccess().byKey(key);
        if (holder.isEmpty() || !(holder.get().value() instanceof CraftingRecipe recipe)) {
            helper.fail("gear_stone crafting recipe not loaded — cannot exercise placeRecipeInBlueprint");
            return;
        }

        ItemHandlerSimple blueprint = new ItemHandlerSimple(9);
        CraftingUtil.placeRecipeInBlueprint(recipe, blueprint, level);

        // Centre slot (index 4) is the G key = #c:gears/wooden tag (exactly one member: gear_wood).
        ItemStack centre = blueprint.getStackInSlot(4);
        if (centre.isEmpty()) {
            helper.fail("Blueprint centre slot is empty: the #c:gears/wooden tag ingredient did not resolve"
                + " — SlotDisplayContext.REGISTRIES is missing from the placement context.");
            return;
        }
        Identifier centreId = BuiltInRegistries.ITEM.getKey(centre.getItem());
        if (!centreId.equals(Identifier.parse("buildcraftunofficial:gear_wood"))) {
            helper.fail("Blueprint centre slot resolved to " + centreId + ", expected buildcraftunofficial:gear_wood");
            return;
        }

        // An edge slot (index 1) is the c key = #minecraft:stone_crafting_materials tag.
        if (blueprint.getStackInSlot(1).isEmpty()) {
            helper.fail("Blueprint edge slot is empty: the #minecraft:stone_crafting_materials tag ingredient"
                + " did not resolve.");
            return;
        }

        helper.succeed();
    }
}
