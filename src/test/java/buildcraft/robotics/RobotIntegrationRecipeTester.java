/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.robotics;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.api.recipes.IngredientStack;
import buildcraft.api.recipes.IntegrationRecipe;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.lib.recipe.IntegrationRecipeRegistry;
import buildcraft.robotics.boards.BoardRobotEmptyNBT;
import buildcraft.robotics.boards.BoardRobotKnightNBT;
import buildcraft.robotics.boards.BoardRobotPickerNBT;
import buildcraft.robotics.item.ItemRedstoneBoard;
import buildcraft.robotics.item.ItemRobot;

/** Ph7 pins for the Integration Table's robot recipe: the board transfer, the charge the robot carries into the
 *  result (with 7.1.x's SAFETY top-up for a zero-charge robot), the flat cost, and the requirement/centre stack
 *  contract {@code TileIntegrationTable} consumes. */
public class RobotIntegrationRecipeTester extends VanillaSetupBaseTester {

    private static RobotIntegrationRecipe recipe;

    @BeforeAll
    public static void registerRecipe() {
        BCRoboticsRecipes.ensureInitialized();
        boolean found = false;
        for (IntegrationRecipe registered : IntegrationRecipeRegistry.INSTANCE.getAllRecipes()) {
            if (registered instanceof RobotIntegrationRecipe robotRecipe) {
                found = true;
                recipe = robotRecipe;
            }
        }
        Assertions.assertTrue(found, "ensureInitialized registers the robot integration recipe");
    }

    private static ItemStack output(ItemStack target, ItemStack... expansions) {
        NonNullList<ItemStack> grid = NonNullList.withSize(8, ItemStack.EMPTY);
        for (int i = 0; i < expansions.length; i++) {
            grid.set(i, expansions[i]);
        }
        return recipe.getOutput(target, grid);
    }

    @Test
    public void outputCarriesTheBoard() {
        ItemStack result = output(
                ItemRobot.createRobotStack(null, 123_456L),
                ItemRedstoneBoard.createStack(BoardRobotPickerNBT.INSTANCE));
        Assertions.assertEquals(BoardRobotPickerNBT.ID, ItemRobot.getBoardId(result),
                "the robot is programmed with the offered board");
    }

    @Test
    public void robotChargeIsPreserved() {
        ItemStack result = output(
                ItemRobot.createRobotStack(null, 987_654_321L),
                ItemRedstoneBoard.createStack(BoardRobotKnightNBT.INSTANCE));
        Assertions.assertEquals(987_654_321L, ItemRobot.getEnergy(result),
                "the robot's stored charge rides into the result untouched");
    }

    @Test
    public void zeroChargeRobotIsToppedUpToSafety() {
        ItemStack result = output(
                ItemRobot.createRobotStack(null, 0L),
                ItemRedstoneBoard.createStack(BoardRobotPickerNBT.INSTANCE));
        Assertions.assertEquals(IRobotAccess.SAFETY_POWER, ItemRobot.getEnergy(result),
                "a zero-charge robot is topped up to SAFETY_POWER so it survives to reach a dock");
    }

    @Test
    public void blankBoardIntegrationYieldsABlankRobot() {
        ItemStack result = output(
                ItemRobot.createRobotStack(null, 500L),
                ItemRedstoneBoard.createStack(BoardRobotEmptyNBT.INSTANCE));
        Assertions.assertTrue(ItemRobot.hasEmptyBoard(result), "integrating the empty board wipes the robot");
        Assertions.assertEquals(500L, ItemRobot.getEnergy(result), "and still preserves the charge");
    }

    @Test
    public void robotAndBoardAreBothRequired() {
        ItemStack board = ItemRedstoneBoard.createStack(BoardRobotPickerNBT.INSTANCE);
        Assertions.assertTrue(output(new ItemStack(Items.PAPER), board).isEmpty(), "the centre stack must be a robot");
        Assertions.assertTrue(output(ItemRedstoneBoard.createStack(null), board).isEmpty(),
                "a board in the centre slot is not a robot");
        Assertions.assertTrue(output(ItemRobot.createRobotStack(null, 100L)).isEmpty(),
                "a robot with no board in the grid produces nothing");
        Assertions.assertTrue(output(ItemRobot.createRobotStack(null, 100L), new ItemStack(Items.PAPER)).isEmpty(),
                "paper is not an expansion the recipe accepts");
    }

    @Test
    public void flatCostIsPinned() {
        ItemStack result = output(
                ItemRobot.createRobotStack(null, 1L),
                ItemRedstoneBoard.createStack(BoardRobotPickerNBT.INSTANCE));
        // 7.1.x's flat 50,000 RF at the canonical 1 RF = 100_000 micro-MJ bridge.
        Assertions.assertEquals(5_000_000_000L, recipe.getRequiredMicroJoules(result),
                "programming a robot costs 5000 MJ, flat");
    }

    @Test
    public void requirementsConsumeExactlyOneBoardAndTheCentreRobot() {
        ItemStack board = ItemRedstoneBoard.createStack(BoardRobotPickerNBT.INSTANCE);
        ItemStack result = output(ItemRobot.createRobotStack(null, 1L), board);
        var requirements = recipe.getRequirements(result);
        Assertions.assertEquals(1, requirements.size(), "one requirement: the board");
        IngredientStack requirement = requirements.get(0);
        Assertions.assertEquals(1, requirement.count, "exactly one board is consumed");
        Assertions.assertTrue(requirement.ingredient.test(board), "the requirement matches the offered board");
        Assertions.assertFalse(requirement.ingredient.test(new ItemStack(Items.PAPER)), "and nothing else");

        IngredientStack centre = recipe.getCenterStack();
        Assertions.assertTrue(centre.ingredient.test(ItemRobot.createRobotStack(null, 0L)),
                "the centre requirement matches a robot");
        Assertions.assertFalse(centre.ingredient.test(board), "and not a board");
    }
}
