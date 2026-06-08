/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import buildcraft.lib.misc.CraftingUtil;

import buildcraft.silicon.BCSiliconBlocks;
import buildcraft.silicon.tile.TileAdvancedCraftingTable;

/**
 * Pins the conflicting-recipe cycle-output feature shared by the Auto Workbench and Advanced Crafting
 * Table (the issue #20 follow-up). Two test-only shapeless recipes — {@code test_cycle_a -> diamond}
 * and {@code test_cycle_b -> emerald} — both consume a single bedrock, so a one-bedrock grid matches
 * both.
 *
 * <p>{@link CraftingUtil#findMatchingRecipes} must surface both (vanilla {@code getRecipeFor} keeps
 * only one), sorted by id; and the shared {@code WorkbenchCrafting} engine (exercised here through an
 * Advanced Crafting Table) must let the player cycle the produced output and keep the choice across a
 * grid recompute.
 */
public final class CraftingOutputCycleTester {

    private CraftingOutputCycleTester() {}

    private static final String RECIPE_A = "buildcraftunofficial:test_cycle_a";
    private static final String RECIPE_B = "buildcraftunofficial:test_cycle_b";

    /** findMatchingRecipes surfaces every conflicting recipe (sorted by id); findMatchingRecipe keeps one. */
    public static void testFindMatchingRecipesConflicts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CraftingInput input = CraftingInput.of(1, 1, List.of(new ItemStack(Items.BEDROCK)));

        List<String> ids = CraftingUtil.findMatchingRecipes(input, level).stream()
            .map(CraftingUtil::recipeId).toList();
        if (!ids.equals(List.of(RECIPE_A, RECIPE_B))) {
            helper.fail("findMatchingRecipes should return both conflicts sorted by id ["
                + RECIPE_A + ", " + RECIPE_B + "], got " + ids);
        }
        if (CraftingUtil.findMatchingRecipe(input, level) == null) {
            helper.fail("findMatchingRecipe returned null for a grid that matches recipes");
        }
        helper.succeed();
    }

    /** The shared crafting engine cycles the produced output and preserves the choice across recompute. */
    public static void testAdvancedCraftingTableCyclesOutput(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, BCSiliconBlocks.ADVANCED_CRAFTING_TABLE.get());
        //? if >=1.21.10 {
        TileAdvancedCraftingTable tile = helper.getBlockEntity(pos, TileAdvancedCraftingTable.class);
        //?} else {
        /*TileAdvancedCraftingTable tile = helper.getBlockEntity(pos);*/
        //?}
        if (tile == null) {
            helper.fail("Advanced Crafting Table block-entity must be present");
            return;
        }

        // One bedrock in the phantom blueprint matches both test recipes.
        tile.invBlueprint.setStackInSlot(0, new ItemStack(Items.BEDROCK));
        tile.serverTick(); // resolve matches + assumed result

        if (tile.getCraftingMatchCount() != 2) {
            helper.fail("Expected 2 matching recipes for one bedrock, got " + tile.getCraftingMatchCount());
        }
        if (tile.getCurrentRecipeOutput().getItem() != Items.DIAMOND) {
            helper.fail("Default output should be the first sorted recipe (diamond), got "
                + tile.getCurrentRecipeOutput());
        }

        // Cycle forward -> second output (emerald).
        tile.cycleCraftingOutput(1);
        if (tile.getCurrentRecipeOutput().getItem() != Items.EMERALD || tile.getCraftingSelectedIndex() != 1) {
            helper.fail("After cycling, output should be emerald (index 1), got "
                + tile.getCurrentRecipeOutput() + " at index " + tile.getCraftingSelectedIndex());
        }

        // Cycle forward again -> wraps back to the first output (diamond).
        tile.cycleCraftingOutput(1);
        if (tile.getCurrentRecipeOutput().getItem() != Items.DIAMOND) {
            helper.fail("Cycling past the end should wrap to diamond, got " + tile.getCurrentRecipeOutput());
        }

        // The selection survives a blueprint recompute: pick emerald, dirty the grid, re-tick.
        tile.cycleCraftingOutput(1); // emerald
        tile.invBlueprint.setStackInSlot(0, new ItemStack(Items.BEDROCK)); // fires callback -> blueprint dirty
        tile.serverTick(); // recompute matches
        if (tile.getCurrentRecipeOutput().getItem() != Items.EMERALD) {
            helper.fail("Selected output (emerald) should survive a blueprint recompute, got "
                + tile.getCurrentRecipeOutput());
        }
        helper.succeed();
    }
}
