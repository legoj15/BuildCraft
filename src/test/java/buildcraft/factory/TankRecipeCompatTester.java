/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory;

import java.util.List;
import java.util.Optional;

import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

import buildcraft.core.BCCoreItems;

import buildcraft.lib.recipe.AssemblyRecipeRegistry;

/**
 * Pins the IronTanks-compatibility recipe fixes for the BuildCraft tank (GitHub issue #20).
 *
 * <p>IronTanks' glass tank shares the BuildCraft tank's 8-cheap-glass crafting-grid recipe. Rather
 * than drop one side, BuildCraft keeps both and offers several routes: the four machines that need a
 * tank accept either via the {@code #buildcraftunofficial:tanks} tag; the BuildCraft tank has a
 * 6-cheap-glass Assembly Table recipe (a cheaper, conflict-free native path); and a recipe-priority
 * override ({@code data/neoforge/recipe_priorities.json}) lets IronTanks win the shared crafting grid
 * so its own tiers stay craftable. The Auto Workbench / Advanced Crafting Table cycle-output button
 * (see {@code buildcraft.lib.CraftingOutputCycleTester}) then lets players pick either tank from the
 * shared 8-glass grid.
 *
 * <p>These run in the no-IronTanks dev/CI environment, so they pin the BuildCraft side: the tag
 * resolves the BuildCraft tank into the Pump, the 8-glass crafting recipe produces a BuildCraft tank,
 * and the Assembly Table recipe converts exactly 6 cheap glass into a tank.
 */
public final class TankRecipeCompatTester {

    private TankRecipeCompatTester() {}

    /** The Pump's tank slot is the {@code #buildcraftunofficial:tanks} tag — a BuildCraft tank must satisfy it. */
    public static void testPumpAcceptsBuildcraftTank(GameTestHelper helper) {
        ItemStack iron = new ItemStack(Items.IRON_INGOT);
        ItemStack tank = new ItemStack(BCFactoryBlocks.TANK.get());
        // Pump pattern: iri / igi / tbt
        CraftingInput input = CraftingInput.of(3, 3, List.of(
            iron, new ItemStack(Items.REDSTONE), iron,
            iron, new ItemStack(BCCoreItems.GEAR_IRON.get()), iron,
            tank, new ItemStack(Items.BUCKET), tank));
        assertCraftResult(helper, input, new ItemStack(BCFactoryBlocks.PUMP.get()),
            "Pump from BuildCraft tanks (via #buildcraftunofficial:tanks)");
    }

    /** The 8-cheap-glass crafting recipe must produce the BuildCraft tank (the only match without IronTanks). */
    public static void testTankCraftableWithoutIronTanks(GameTestHelper helper) {
        ItemStack g = new ItemStack(Items.GLASS);
        // Tank pattern: ggg / g g / ggg (empty centre)
        CraftingInput input = CraftingInput.of(3, 3, List.of(
            g, g, g,
            g, ItemStack.EMPTY, g,
            g, g, g));
        assertCraftResult(helper, input, new ItemStack(BCFactoryBlocks.TANK.get()),
            "BuildCraft tank from 8 cheap glass (no IronTanks)");
    }

    /** The Assembly Table recipe must turn exactly 6 cheap glass into a BuildCraft tank. */
    public static void testAssemblyTankRecipe(GameTestHelper helper) {
        Item tankItem = BCFactoryBlocks.TANK.get().asItem();
        if (!assemblyMakesTankFrom(new ItemStack(Items.GLASS, 6), tankItem)) {
            helper.fail("Assembly Table recipe '6 cheap glass -> BuildCraft tank' (issue #20) did not "
                + "match 6 glass — the recipe is missing or its ingredient does not accept cheap glass");
        }
        if (assemblyMakesTankFrom(new ItemStack(Items.GLASS, 5), tankItem)) {
            helper.fail("Assembly Table tank recipe matched with only 5 glass — it must require 6");
        }
        helper.succeed();
    }

    private static boolean assemblyMakesTankFrom(ItemStack available, Item tankItem) {
        NonNullList<ItemStack> pool = NonNullList.create();
        pool.add(available);
        return AssemblyRecipeRegistry.getRecipesFor(pool).stream()
            .flatMap(r -> r.getOutputs(pool).stream())
            .anyMatch(out -> out.getItem() == tankItem);
    }

    private static void assertCraftResult(GameTestHelper helper, CraftingInput input, ItemStack expected, String label) {
        ServerLevel level = helper.getLevel();
        Optional<RecipeHolder<CraftingRecipe>> match = level.getServer().getRecipeManager()
            .getRecipeFor(RecipeType.CRAFTING, input, level);
        if (match.isEmpty()) {
            helper.fail("Recipe didn't match: " + label);
        }
        //? if >=26.1 {
        ItemStack out = match.get().value().assemble(input);
        //?} else {
        /*ItemStack out = match.get().value().assemble(input, level.registryAccess());*/
        //?}
        if (!ItemStack.matches(expected, out)) {
            helper.fail("Crafting output mismatch for " + label + ": expected " + expected + " got " + out);
        }
        helper.succeed();
    }
}
