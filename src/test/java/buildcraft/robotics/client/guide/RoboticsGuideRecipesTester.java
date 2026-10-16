/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.client.guide;

import java.util.List;
import java.util.function.Predicate;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.lib.client.guide.parts.GuidePartFactory;
import buildcraft.lib.client.guide.parts.recipe.GuideLaserTableFactory;
import buildcraft.lib.client.guide.parts.recipe.IStackRecipes;
import buildcraft.lib.client.guide.parts.recipe.RecipeLookupHelper;
import buildcraft.robotics.BCRoboticsItems;
import buildcraft.robotics.BCRoboticsRecipes;
import buildcraft.robotics.boards.BoardRobotMinerNBT;
import buildcraft.robotics.item.ItemRedstoneBoard;
import buildcraft.robotics.item.ItemRobot;
import buildcraft.silicon.BCSiliconItems;

/** Pins the guide book's two robot-making steps: the Programming Table (blank board → programmed board) and the
 *  Integration Table (blank robot + programmed board → programmed robot). Before these lookups existed a Robot or
 *  Redstone Board page could only show the crafting-table recipe for the blank item, so the guide never told a
 *  player how a working robot is made. Exercises the lookups the guide's {@code <recipes_usages>} tag and its
 *  stand-in pages call, without drawing anything. */
public class RoboticsGuideRecipesTester extends VanillaSetupBaseTester {

    private static ItemStack minerBoard;
    private static ItemStack blankBoard;
    private static ItemStack blankRobot;

    @BeforeAll
    public static void setUp() {
        BCRoboticsRecipes.ensureInitialized();
        minerBoard = ItemRedstoneBoard.createStack(BoardRobotMinerNBT.INSTANCE);
        blankBoard = new ItemStack(BCRoboticsItems.REDSTONE_BOARD.get());
        blankRobot = new ItemStack(BCRoboticsItems.ROBOT.get());
    }

    private static boolean isMinerBoard(ItemStack stack) {
        return stack.getItem() instanceof ItemRedstoneBoard
                && BoardRobotMinerNBT.ID.equals(ItemRedstoneBoard.getBoardNBT(stack).getID());
    }

    private static boolean isMinerRobot(ItemStack stack) {
        return stack.getItem() instanceof ItemRobot && BoardRobotMinerNBT.ID.equals(ItemRobot.getBoardId(stack));
    }

    private static GuideLaserTableFactory asTable(GuidePartFactory factory) {
        return Assertions.assertInstanceOf(GuideLaserTableFactory.class, factory, "a laser-table panel");
    }

    private static boolean anyOutput(List<GuidePartFactory> factories, Predicate<ItemStack> test) {
        for (GuidePartFactory factory : factories) {
            for (ItemStack out : asTable(factory).getOutputOptions()) {
                if (test.test(out)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    public void programmedBoardShowsItsProgrammingStep() {
        List<GuidePartFactory> recipes = GuideProgrammingRecipes.INSTANCE.getRecipes(minerBoard);
        Assertions.assertEquals(1, recipes.size(), "exactly the Miner's own programming step");
        GuideLaserTableFactory step = asTable(recipes.get(0));
        Assertions.assertTrue(isMinerBoard(step.getOutputOptions().get(0)), "the step makes the Miner board");
        Assertions.assertEquals(1, step.getInputSlotCount(), "one board goes in");
        Assertions.assertTrue(ItemStack.isSameItem(step.getInputOptions(0).get(0), blankBoard), "a redstone board");
        Assertions.assertTrue(ItemStack.isSameItem(step.getTable(), new ItemStack(BCSiliconItems.PROGRAMMING_TABLE.get())),
                "drawn with the Programming Table");
        Assertions.assertTrue(step.getMjCosts().get(0) > 0, "programming a Miner costs energy");

        List<GuidePartFactory> usages = GuideProgrammingRecipes.INSTANCE.getUsages(blankBoard);
        Assertions.assertTrue(anyOutput(usages, RoboticsGuideRecipesTester::isMinerBoard),
                "the blank board's uses include programming it into a Miner board");
        Assertions.assertEquals(1, usages.size(), "every program folds into ONE cycling panel, not a panel per board");
        Assertions.assertTrue(GuideProgrammingRecipes.INSTANCE.getRecipes(blankBoard).isEmpty(),
                "a blank board is crafted, never programmed - no programming step under 'how to make'");
    }

    @Test
    public void programmedRobotShowsItsIntegrationStep() {
        for (long energy : new long[] { 0, 1234 }) {
            ItemStack minerRobot = ItemRobot.createRobotStack(BoardRobotMinerNBT.ID, energy);
            List<GuidePartFactory> recipes = GuideIntegrationRecipes.INSTANCE.getRecipes(minerRobot);
            Assertions.assertEquals(1, recipes.size(), "exactly the Miner robot's step (energy " + energy + ")");
            GuideLaserTableFactory step = asTable(recipes.get(0));
            Assertions.assertTrue(isMinerRobot(step.getOutputOptions().get(0)), "the step makes the Miner robot");
            Assertions.assertEquals(2, step.getInputSlotCount(), "robot + board go in");
            Assertions.assertTrue(ItemRobot.hasEmptyBoard(step.getInputOptions(0).get(0)), "a blank robot");
            Assertions.assertTrue(isMinerBoard(step.getInputOptions(1).get(0)), "the Miner board");
            Assertions.assertTrue(ItemStack.isSameItem(step.getTable(),
                    new ItemStack(BCSiliconItems.INTEGRATION_TABLE.get())), "drawn with the Integration Table");
        }

        List<GuidePartFactory> usages = GuideIntegrationRecipes.INSTANCE.getUsages(blankRobot);
        Assertions.assertTrue(anyOutput(usages, RoboticsGuideRecipesTester::isMinerRobot),
                "the blank robot's uses include integrating a Miner board");
        Assertions.assertEquals(1, usages.size(), "every program folds into ONE cycling panel, not a panel per robot");

        List<GuidePartFactory> boardUsages = GuideIntegrationRecipes.INSTANCE.getUsages(minerBoard);
        Assertions.assertEquals(1, boardUsages.size(), "the Miner board is used in exactly its own robot's step");
        Assertions.assertTrue(isMinerRobot(asTable(boardUsages.get(0)).getOutputOptions().get(0)));
        Assertions.assertTrue(anyOutput(GuideIntegrationRecipes.INSTANCE.getUsages(blankBoard),
                RoboticsGuideRecipesTester::isMinerRobot), "the blank board's page reaches the robot step too");
    }

    @Test
    public void vanillaStacksHaveNoTableSteps() {
        ItemStack dirt = new ItemStack(Items.DIRT);
        for (IStackRecipes lookup : new IStackRecipes[] { GuideProgrammingRecipes.INSTANCE,
                GuideIntegrationRecipes.INSTANCE }) {
            Assertions.assertTrue(lookup.getRecipes(dirt).isEmpty(), lookup + " recipes of dirt");
            Assertions.assertTrue(lookup.getUsages(dirt).isEmpty(), lookup + " usages of dirt");
        }
    }

    @Test
    public void pagesCanPinOneStepByBoardId() {
        GuidePartFactory programming = GuideProgrammingRecipes.INSTANCE.getFactoryById(BoardRobotMinerNBT.ID);
        Assertions.assertTrue(isMinerBoard(asTable(programming).getOutputOptions().get(0)));
        GuidePartFactory integration = GuideIntegrationRecipes.INSTANCE.getFactoryById(BoardRobotMinerNBT.ID);
        Assertions.assertTrue(isMinerRobot(asTable(integration).getOutputOptions().get(0)));
        Assertions.assertNull(GuideProgrammingRecipes.INSTANCE.getFactoryById("buildcraftunofficial:no_such_board"));
        Assertions.assertNull(GuideIntegrationRecipes.INSTANCE.getFactoryById("buildcraftunofficial:no_such_board"));
    }

    /** The stand-in page (an item with no authored page) and {@code <recipes_usages>} both go through the helper's
     *  handler map, so the two lookups must sit in it under the names a {@code type=} attribute uses. */
    @Test
    public void lookupsAreRegisteredWithTheGuide() {
        RoboticsGuideRecipes.register();
        Assertions.assertSame(GuideProgrammingRecipes.INSTANCE, RecipeLookupHelper.handlerTypes.get("programming"));
        Assertions.assertSame(GuideIntegrationRecipes.INSTANCE, RecipeLookupHelper.handlerTypes.get("integration"));
        // Panels list in handler order: a board's page must show programming before integration (the order a robot
        // is made), and the three built-in panels keep the order pages have always had.
        Assertions.assertEquals(List.of("crafting", "assembling", "smelting", "programming", "integration"),
                List.copyOf(RecipeLookupHelper.handlerTypes.keySet()));
    }
}
