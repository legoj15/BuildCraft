/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.robotics;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.api.boards.RedstoneBoardNBT;
import buildcraft.api.boards.RedstoneBoardRegistry;
import buildcraft.api.recipes.IProgrammingRecipe;
import buildcraft.lib.recipe.ProgrammingRecipeRegistry;
import buildcraft.robotics.boards.BoardRobotCarrierNBT;
import buildcraft.robotics.boards.BoardRobotEmptyNBT;
import buildcraft.robotics.boards.BoardRobotFluidCarrierNBT;
import buildcraft.robotics.boards.BoardRobotKnightNBT;
import buildcraft.robotics.boards.BoardRobotPickerNBT;
import buildcraft.robotics.item.ItemRedstoneBoard;
import buildcraft.robotics.item.ItemRobot;

/** Ph7 pins for the Programming Table's recipe core: the board crafting-cost tiers the table charges (distinct from
 *  the per-tick AI leaf costs), the deterministic option ordering the GUI grid and the saved option index rely on,
 *  and the registry the tile resolves saved recipe ids through. Runs against the live board registry booted by the
 *  FML-JUnit mod load. */
public class ProgrammingRecipeTester extends VanillaSetupBaseTester {

    private static BoardProgrammingRecipe recipe;

    @BeforeAll
    public static void registerRecipe() {
        BCRoboticsRecipes.ensureInitialized();
        IProgrammingRecipe registered = ProgrammingRecipeRegistry.INSTANCE.getRecipe("buildcraftunofficial:redstone_board");
        Assertions.assertInstanceOf(BoardProgrammingRecipe.class, registered,
                "ensureInitialized registers the board recipe under its id");
        recipe = (BoardProgrammingRecipe) registered;
    }

    @Test
    public void boardCostTiersMatchTheMicroMjBridge() {
        // 7.1.x RF costs at the canonical 1 RF = 100_000 micro-MJ bridge, as registered in BCRobotics.
        Map<String, Long> expected = Map.of(
                BoardRobotEmptyNBT.ID, 0L,
                BoardRobotPickerNBT.ID, 800_000_000L,
                BoardRobotCarrierNBT.ID, 800_000_000L,
                BoardRobotFluidCarrierNBT.ID, 800_000_000L,
                BoardRobotKnightNBT.ID, 12_800_000_000L);
        for (Map.Entry<String, Long> entry : expected.entrySet()) {
            Assertions.assertEquals(entry.getValue(),
                    RedstoneBoardRegistry.instance.getPowerCost(RedstoneBoardRegistry.instance.getRedstoneBoard(entry.getKey())),
                    "board " + entry.getKey() + " keeps its registered cost");
        }
        // The seven blue boards all sit at 32000 RF.
        for (String id : new String[] {
                "buildcraftunofficial:boardRobotLumberjack", "buildcraftunofficial:boardRobotHarvester",
                "buildcraftunofficial:boardRobotMiner", "buildcraftunofficial:boardRobotPlanter",
                "buildcraftunofficial:boardRobotFarmer", "buildcraftunofficial:boardRobotPump",
                "buildcraftunofficial:boardRobotButcher"}) {
            Assertions.assertEquals(3_200_000_000L,
                    RedstoneBoardRegistry.instance.getPowerCost(RedstoneBoardRegistry.instance.getRedstoneBoard(id)),
                    "blue board " + id + " keeps its registered cost");
        }
    }

    @Test
    public void energyCostOfAnOptionIsTheRegistryCostOfItsBoard() {
        for (ItemStack option : recipe.getOptions(6, 4)) {
            RedstoneBoardNBT<?> board = ItemRedstoneBoard.getBoardNBT(option);
            Assertions.assertEquals(RedstoneBoardRegistry.instance.getPowerCost(board), recipe.getEnergyCost(option),
                    "option for " + board.getID() + " is priced by the registry");
        }
    }

    @Test
    public void optionsCoverEveryBoardExactlyOnceCheapestFirst() {
        List<ItemStack> options = recipe.getOptions(6, 4);
        Assertions.assertEquals(RedstoneBoardRegistry.instance.getAllBoardNBTs().size(), options.size(),
                "one option per registered board");
        for (ItemStack option : options) {
            Assertions.assertEquals(1, option.getCount(), "options are single stacks");
            long count = options.stream().filter(other -> ItemStack.matches(other, option)).count();
            Assertions.assertEquals(1, count, "no duplicate options");
        }
        // The empty board is free, so it must be the first slot in every ordering.
        Assertions.assertEquals(BoardRobotEmptyNBT.ID, ItemRedstoneBoard.getBoardNBT(options.get(0)).getID(),
                "the free empty board sorts first");
    }

    @Test
    public void optionOrderingIsDeterministic() {
        List<ItemStack> first = recipe.getOptions(6, 4);
        List<ItemStack> second = recipe.getOptions(6, 4);
        Assertions.assertEquals(first.size(), second.size());
        for (int i = 0; i < first.size(); i++) {
            Assertions.assertTrue(ItemStack.matches(first.get(i), second.get(i)),
                    "option " + i + " is stable across calls (the saved option index depends on it)");
        }
        for (int i = 1; i < first.size(); i++) {
            long prevCost = recipe.getEnergyCost(first.get(i - 1));
            long cost = recipe.getEnergyCost(first.get(i));
            Assertions.assertTrue(prevCost <= cost, "costs are non-decreasing (position " + i + ")");
        }
    }

    @Test
    public void canCraftAcceptsOnlyRedstoneBoards() {
        Assertions.assertTrue(recipe.canCraft(ItemRedstoneBoard.createStack(BoardRobotPickerNBT.INSTANCE)),
                "a programmed board is a valid input");
        Assertions.assertTrue(recipe.canCraft(ItemRedstoneBoard.createStack(null)),
                "a blank board is a valid input (reprogramming wipes it)");
        Assertions.assertFalse(recipe.canCraft(ItemRobot.createRobotStack(BoardRobotPickerNBT.ID, 0)),
                "a robot is not a board");
        Assertions.assertFalse(recipe.canCraft(new ItemStack(Items.PAPER)), "paper is not a board");
    }

    @Test
    public void craftReturnsTheSelectedOptionUntouched() {
        ItemStack option = ItemRedstoneBoard.createStack(BoardRobotKnightNBT.INSTANCE);
        ItemStack result = recipe.craft(ItemRedstoneBoard.createStack(null), option);
        Assertions.assertTrue(ItemStack.matches(option, result), "the output is the selected option, verbatim");
    }

    @Test
    public void registryIgnoresDuplicateIds() {
        int before = ProgrammingRecipeRegistry.INSTANCE.getRecipes().size();
        ProgrammingRecipeRegistry.INSTANCE.addRecipe(new BoardProgrammingRecipe());
        Assertions.assertEquals(before, ProgrammingRecipeRegistry.INSTANCE.getRecipes().size(),
                "a duplicate id is warned about and dropped, not added");
    }
}
