/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
//? if >=1.21.10 {
import net.minecraft.world.level.storage.TagValueInput;
//?}

import buildcraft.api.mj.MjAPI;

import buildcraft.factory.BCFactoryBlocks;
import buildcraft.factory.tile.TileAutoWorkbenchItems;
import buildcraft.lib.misc.CraftingUtil;
import buildcraft.lib.tile.item.ItemHandlerSimple;

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
 *
 * <p>Also pins the Advanced Crafting Table's two swap features (issue #41): the accept-substitutes toggle
 * (craft the selected recipe from other materials) and the wood/material variants in the cycle (the cycle
 * rewrites the blueprint), plus that the Auto Workbench, which shares the engine, offers neither.
 */
public final class CraftingOutputCycleTester {

    private CraftingOutputCycleTester() {}

    private static final String RECIPE_A = "buildcraftunofficial:test_cycle_a";
    private static final String RECIPE_B = "buildcraftunofficial:test_cycle_b";

    /** Blueprint slots of the 3x3 stair pattern ({@code #  / ## / ###}). */
    private static final int[] STAIR_SLOTS = { 0, 3, 4, 6, 7, 8 };

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

    // --- Advanced Crafting Table: accept-substitutes toggle + output variants (issue #41) ---

    private static TileAdvancedCraftingTable placeTable(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, BCSiliconBlocks.ADVANCED_CRAFTING_TABLE.get());
        //? if >=1.21.10 {
        TileAdvancedCraftingTable tile = helper.getBlockEntity(pos, TileAdvancedCraftingTable.class);
        //?} else {
        /*TileAdvancedCraftingTable tile = helper.getBlockEntity(pos);*/
        //?}
        if (tile == null) {
            helper.fail("Advanced Crafting Table block-entity must be present");
        }
        return tile;
    }

    private static int count(ItemHandlerSimple inv, Item item) {
        int total = 0;
        for (int s = 0; s < inv.getSlots(); s++) {
            ItemStack stack = inv.getStackInSlot(s);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** A vertical pair of oak planks: the {@code #minecraft:planks} stick recipe, which accepts every wood. */
    private static void setSticksBlueprint(TileAdvancedCraftingTable tile) {
        tile.invBlueprint.setStackInSlot(0, new ItemStack(Items.OAK_PLANKS));
        tile.invBlueprint.setStackInSlot(3, new ItemStack(Items.OAK_PLANKS));
    }

    /** More than the table's per-craft cost, so a craftable grid crafts on the next tick. */
    private static void chargeTable(TileAdvancedCraftingTable tile) {
        tile.power = 1000 * MjAPI.MJ;
    }

    /** Off by default (a blueprint of oak planks does not craft from birch planks); on, the table crafts the
     *  selected recipe from the substitute and consumes it; the toggle survives a save/load of the tile data. */
    public static void testSubstitutesToggleCraftsFromOtherMaterial(GameTestHelper helper) {
        TileAdvancedCraftingTable tile = placeTable(helper);
        if (tile == null) {
            return;
        }
        setSticksBlueprint(tile);
        tile.invMaterials.setStackInSlot(0, new ItemStack(Items.BIRCH_PLANKS, 2));
        chargeTable(tile);
        tile.serverTick(); // resolve the recipe; the exact materials are missing

        if (tile.getCurrentRecipeOutput().getItem() != Items.STICK) {
            helper.fail("Precondition: two vertical oak planks must resolve to sticks, got "
                + tile.getCurrentRecipeOutput());
        }
        if (tile.isAcceptingSubstitutes()) {
            helper.fail("Accepting substitutes must default to off");
        }
        if (tile.getTarget() != 0) {
            helper.fail("With substitutes off, birch planks must not satisfy an oak planks blueprint");
        }
        tile.serverTick();
        if (count(tile.invResults, Items.STICK) != 0 || count(tile.invMaterials, Items.BIRCH_PLANKS) != 2) {
            helper.fail("With substitutes off nothing may be crafted or consumed, got "
                + count(tile.invResults, Items.STICK) + " sticks and "
                + count(tile.invMaterials, Items.BIRCH_PLANKS) + " birch planks left");
        }

        tile.setAcceptSubstitutes(true);
        if (!tile.isAcceptingSubstitutes()) {
            helper.fail("setAcceptSubstitutes(true) must be readable back");
        }
        if (tile.getTarget() == 0) {
            helper.fail("With substitutes on, birch planks must satisfy the oak planks stick blueprint");
        }
        tile.serverTick();
        if (count(tile.invResults, Items.STICK) != 4) {
            helper.fail("Expected one stick craft (4 sticks), got " + count(tile.invResults, Items.STICK));
        }
        if (count(tile.invMaterials, Items.BIRCH_PLANKS) != 0) {
            helper.fail("The craft must consume the substitute birch planks, "
                + count(tile.invMaterials, Items.BIRCH_PLANKS) + " left");
        }
        if (!tile.invBlueprint.getStackInSlot(0).is(Items.OAK_PLANKS)
            || !tile.invBlueprint.getStackInSlot(3).is(Items.OAK_PLANKS)) {
            helper.fail("A substituted craft must leave the blueprint showing what was asked for");
        }

        // The toggle persists: clobber it, then reload the saved tile data.
        ServerLevel level = helper.getLevel();
        CompoundTag onTag = tile.saveCustomOnly(level.registryAccess());
        tile.setAcceptSubstitutes(false);
        //? if >=1.21.10 {
        tile.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), onTag));
        //?} else {
        /*tile.loadCustomOnly(onTag, level.registryAccess());*/
        //?}
        if (!tile.isAcceptingSubstitutes()) {
            helper.fail("The accept-substitutes toggle (on) must survive a save/load round trip");
        }
        tile.setAcceptSubstitutes(false);
        CompoundTag offTag = tile.saveCustomOnly(level.registryAccess());
        tile.setAcceptSubstitutes(true);
        //? if >=1.21.10 {
        tile.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), offTag));
        //?} else {
        /*tile.loadCustomOnly(offTag, level.registryAccess());*/
        //?}
        if (tile.isAcceptingSubstitutes()) {
            helper.fail("The accept-substitutes toggle (off) must survive a save/load round trip");
        }
        helper.succeed();
    }

    /** With substitutes on: a stack that does not fit the recipe is never a substitute, two woods are never mixed in
     *  one craft, and the blueprint's own items are used first. */
    public static void testSubstitutesSelectionRules(GameTestHelper helper) {
        TileAdvancedCraftingTable tile = placeTable(helper);
        if (tile == null) {
            return;
        }
        tile.setAcceptSubstitutes(true);
        setSticksBlueprint(tile);
        chargeTable(tile);

        // A material that does not satisfy the recipe is no substitute.
        tile.invMaterials.setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 2));
        tile.serverTick();
        if (tile.getTarget() != 0) {
            helper.fail("Cobblestone must not substitute for planks in the stick recipe");
        }

        // Two different woods, one plank each: every slot of a blueprint item gets the SAME replacement, so
        // neither wood alone covers the two planks the recipe needs.
        tile.invMaterials.setStackInSlot(0, new ItemStack(Items.BIRCH_PLANKS, 1));
        tile.invMaterials.setStackInSlot(1, new ItemStack(Items.SPRUCE_PLANKS, 1));
        if (tile.getTarget() != 0) {
            helper.fail("One birch plank and one spruce plank must not be mixed into one craft");
        }

        // One wood with enough planks is a substitute (the counterpart of the two checks above).
        tile.invMaterials.setStackInSlot(0, new ItemStack(Items.BIRCH_PLANKS, 2));
        tile.invMaterials.setStackInSlot(1, ItemStack.EMPTY);
        if (tile.getTarget() == 0) {
            helper.fail("Two birch planks must substitute for the two oak planks of the stick blueprint");
        }

        // The exact blueprint material is preferred over an available substitute.
        tile.invMaterials.setStackInSlot(0, new ItemStack(Items.BIRCH_PLANKS, 2));
        tile.invMaterials.setStackInSlot(1, new ItemStack(Items.OAK_PLANKS, 2));
        tile.serverTick();
        if (count(tile.invResults, Items.STICK) != 4) {
            helper.fail("Expected one stick craft (4 sticks), got " + count(tile.invResults, Items.STICK));
        }
        if (count(tile.invMaterials, Items.OAK_PLANKS) != 0 || count(tile.invMaterials, Items.BIRCH_PLANKS) != 2) {
            helper.fail("The blueprint's own oak planks must be used before a substitute: "
                + count(tile.invMaterials, Items.OAK_PLANKS) + " oak and "
                + count(tile.invMaterials, Items.BIRCH_PLANKS) + " birch left");
        }
        helper.succeed();
    }

    /** Oak planks in the stair pattern also offer the other woods' stairs: cycling to one rewrites the blueprint to
     *  that wood, and the cycle keeps a stable order whichever variant the blueprint shows. */
    public static void testVariantsRewriteBlueprint(GameTestHelper helper) {
        TileAdvancedCraftingTable tile = placeTable(helper);
        if (tile == null) {
            return;
        }
        for (int slot : STAIR_SLOTS) {
            tile.invBlueprint.setStackInSlot(slot, new ItemStack(Items.OAK_PLANKS));
        }
        tile.serverTick();
        if (tile.getCurrentRecipeOutput().getItem() != Items.OAK_STAIRS) {
            helper.fail("Precondition: the oak planks stair pattern must resolve to oak stairs, got "
                + tile.getCurrentRecipeOutput());
        }
        int size = tile.getCraftingMatchCount();
        if (size <= 1) {
            helper.fail("The other woods' stairs must be offered as variants, match count was " + size);
            return;
        }

        // Walk one full lap from oak, recording the outputs in cycle order; birch must show up with a birch blueprint.
        List<Item> lap = new ArrayList<>();
        lap.add(tile.getCurrentRecipeOutput().getItem());
        int birchStep = -1;
        for (int i = 1; i <= size; i++) {
            tile.cycleCraftingOutput(1);
            if (tile.getCraftingMatchCount() != size) {
                helper.fail("The cycle length must not change while cycling: " + size + " then "
                    + tile.getCraftingMatchCount());
            }
            Item out = tile.getCurrentRecipeOutput().getItem();
            if (i < size) {
                lap.add(out);
            } else if (out != lap.get(0)) {
                helper.fail("A full lap must return to the first output (" + lap.get(0) + "), got " + out);
            }
            if (out == Items.BIRCH_STAIRS && birchStep < 0) {
                birchStep = i;
                for (int slot : STAIR_SLOTS) {
                    if (!tile.invBlueprint.getStackInSlot(slot).is(Items.BIRCH_PLANKS)) {
                        helper.fail("Selecting birch stairs must rewrite the blueprint to birch planks, slot " + slot
                            + " holds " + tile.invBlueprint.getStackInSlot(slot));
                    }
                }
                if (!tile.invBlueprint.getStackInSlot(1).isEmpty()) {
                    helper.fail("Rewriting the blueprint must keep empty slots empty");
                }
            }
        }
        if (birchStep < 0) {
            helper.fail("Cycling never reached birch stairs, outputs were " + lap);
            return;
        }
        if (!tile.invBlueprint.getStackInSlot(0).is(Items.OAK_PLANKS)) {
            helper.fail("Back on oak stairs the blueprint must show oak planks again, slot 0 holds "
                + tile.invBlueprint.getStackInSlot(0));
        }

        // Stable order: stand on birch stairs (blueprint now birch) and walk a lap; it is the same ring of outputs.
        for (int i = 0; i < birchStep; i++) {
            tile.cycleCraftingOutput(1);
        }
        if (tile.getCurrentRecipeOutput().getItem() != Items.BIRCH_STAIRS) {
            helper.fail("Expected to be back on birch stairs, got " + tile.getCurrentRecipeOutput());
        }
        for (int i = 1; i <= size; i++) {
            tile.cycleCraftingOutput(1);
            Item expected = lap.get((birchStep + i) % size);
            Item out = tile.getCurrentRecipeOutput().getItem();
            if (out != expected) {
                helper.fail("Cycle order must not depend on which variant the blueprint shows: step " + i
                    + " from birch expected " + expected + ", got " + out);
            }
        }
        helper.succeed();
    }

    /** The Auto Workbench shares the engine but never offers variants, and never rewrites its blueprint. */
    public static void testAutoWorkbenchOffersNoVariants(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, BCFactoryBlocks.AUTOWORKBENCH_ITEM.get());
        //? if >=1.21.10 {
        TileAutoWorkbenchItems tile = helper.getBlockEntity(pos, TileAutoWorkbenchItems.class);
        //?} else {
        /*TileAutoWorkbenchItems tile = helper.getBlockEntity(pos);*/
        //?}
        if (tile == null) {
            helper.fail("Auto Workbench block-entity must be present");
            return;
        }
        for (int slot : STAIR_SLOTS) {
            tile.invBlueprint.setStackInSlot(slot, new ItemStack(Items.OAK_PLANKS));
        }
        tile.serverTick();
        if (tile.crafting.getAssumedResult().getItem() != Items.OAK_STAIRS) {
            helper.fail("Precondition: the oak planks stair pattern must resolve to oak stairs, got "
                + tile.crafting.getAssumedResult());
        }
        List<ItemStack> grid = new ArrayList<>();
        for (int s = 0; s < 9; s++) {
            grid.add(tile.invBlueprint.getStackInSlot(s));
        }
        int sameGrid = CraftingUtil.findMatchingRecipes(CraftingInput.of(3, 3, grid), helper.getLevel()).size();
        if (tile.getCraftingMatchCount() != sameGrid) {
            helper.fail("The Auto Workbench must only cycle same-grid matches (" + sameGrid
                + "), not wood variants; match count was " + tile.getCraftingMatchCount());
        }
        tile.cycleCraftingOutput(1);
        tile.serverTick();
        for (int slot : STAIR_SLOTS) {
            if (!tile.invBlueprint.getStackInSlot(slot).is(Items.OAK_PLANKS)) {
                helper.fail("The Auto Workbench blueprint must never be rewritten, slot " + slot + " holds "
                    + tile.invBlueprint.getStackInSlot(slot));
            }
        }
        helper.succeed();
    }
}
