/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.recipe;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.api.boards.RedstoneBoardNBT;
import buildcraft.api.boards.RedstoneBoardRegistry;
import buildcraft.api.boards.RedstoneBoardRobotNBT;
import buildcraft.api.recipes.IntegrationRecipe;
import buildcraft.lib.recipe.IntegrationRecipeRegistry;
import buildcraft.robotics.BCRoboticsItems;
import buildcraft.robotics.BCRoboticsRecipes;
import buildcraft.robotics.RobotIntegrationRecipe;
import buildcraft.robotics.boards.BoardRobotEmptyNBT;
import buildcraft.robotics.item.ItemRedstoneBoard;
import buildcraft.robotics.item.ItemRobot;

/** Pins what the Integration Table's JEI category shows: one entry per robot program, each one the table really
 *  crafts. Without it, JEI "recipe" on any functional robot found nothing — the robot's only source is the table,
 *  which had no category. */
public class IntegrationRecipeCollectorTester extends VanillaSetupBaseTester {

    private static List<IntegrationRecipeJei> entries;

    @BeforeAll
    public static void collectOnce() {
        BCRoboticsRecipes.ensureInitialized();
        entries = IntegrationRecipeCollector.collect();
    }

    @Test
    public void oneEntryPerRobotProgramAndNoneForTheBlankBoard() {
        Set<String> expected = new HashSet<>();
        for (RedstoneBoardNBT<?> board : RedstoneBoardRegistry.instance.getAllBoardNBTs()) {
            if (board instanceof RedstoneBoardRobotNBT && !BoardRobotEmptyNBT.ID.equals(board.getID())) {
                expected.add(board.getID());
            }
        }
        Assertions.assertFalse(expected.isEmpty(), "liveness: the registry has robot programs");
        Set<String> shown = new HashSet<>();
        for (IntegrationRecipeJei entry : entries) {
            String id = ItemRobot.getBoardId(entry.outputs().get(0));
            Assertions.assertTrue(shown.add(id), "robot " + id + " appears once");
        }
        Assertions.assertEquals(expected, shown, "every programmed robot can be looked up; the blank one is crafted");
    }

    @Test
    public void everyEntryIsWhatTheTableActuallyCrafts() {
        Assertions.assertFalse(entries.isEmpty(), "liveness");
        for (IntegrationRecipeJei entry : entries) {
            Assertions.assertTrue(ItemStack.isSameItem(entry.center(), new ItemStack(BCRoboticsItems.ROBOT.get())),
                    "the centre is a robot");
            Assertions.assertTrue(ItemRobot.hasEmptyBoard(entry.center()),
                    "the centre is the crafted blank robot, so JEI 'uses' on it lists every program");
            Assertions.assertEquals(1, entry.expansions().size(), "one board in the ring");

            NonNullList<ItemStack> ring = NonNullList.withSize(8, ItemStack.EMPTY);
            ring.set(0, entry.expansions().get(0).copy());
            IntegrationRecipe recipe = IntegrationRecipeRegistry.INSTANCE.getRecipeFor(entry.center().copy(), ring);
            Assertions.assertNotNull(recipe, entry.id() + ": the table finds a recipe for these inputs");
            ItemStack crafted = recipe.getOutput(entry.center().copy(), ring);
            Assertions.assertTrue(ItemStack.isSameItemSameComponents(crafted, entry.outputs().get(0)),
                    entry.id() + ": the first output is exactly the table's result");
            Assertions.assertEquals(RobotIntegrationRecipe.REQUIRED_MICROJOULES, entry.microJoules());
            Assertions.assertEquals(ItemRobot.getBoardId(crafted),
                    ItemRedstoneBoard.getBoardNBT(entry.expansions().get(0)).getID(), "the board's program is installed");
        }
    }

    @Test
    public void transferIdentifierNamesTheProgramTheRobotGets() {
        Assertions.assertFalse(entries.isEmpty(), "liveness");
        for (IntegrationRecipeJei entry : entries) {
            Assertions.assertEquals(ItemRobot.getBoardId(entry.outputs().get(0)), entry.boardId(),
                    entry.id() + ": the board the '+' transfer asks for is the program the robot ends up with");
        }
    }

    @Test
    public void drainedRobotsAlsoLandOnTheirEntry() {
        Assertions.assertFalse(entries.isEmpty(), "liveness");
        for (IntegrationRecipeJei entry : entries) {
            ItemStack crafted = entry.outputs().get(0);
            String id = ItemRobot.getBoardId(crafted);
            Assertions.assertTrue(ItemRobot.getEnergy(crafted) > 0, id + ": a fresh craft is charged");
            Object drainedKey = RoboticsJeiSubtypes.robotKey(ItemRobot.createRobotStack(id, 0));
            boolean listed = false;
            for (ItemStack output : entry.outputs()) {
                listed |= drainedKey.equals(RoboticsJeiSubtypes.robotKey(output));
            }
            Assertions.assertTrue(listed,
                    id + ": the drained robot (a separate JEI entry, also in the creative tab) finds this recipe too");
        }
    }
}
