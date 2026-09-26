/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.compat.jei;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.api.boards.RedstoneBoardNBT;
import buildcraft.api.boards.RedstoneBoardRegistry;
import buildcraft.api.recipes.IProgrammingRecipe;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.lib.recipe.ProgrammingRecipeRegistry;
import buildcraft.robotics.BCRoboticsItems;
import buildcraft.robotics.BCRoboticsRecipes;
import buildcraft.robotics.boards.BoardRobotEmptyNBT;
import buildcraft.robotics.boards.BoardRobotKnightNBT;
import buildcraft.robotics.boards.BoardRobotMinerNBT;
import buildcraft.robotics.boards.BoardRobotPickerNBT;
import buildcraft.robotics.item.ItemRedstoneBoard;
import buildcraft.robotics.item.ItemRobot;
import buildcraft.silicon.tile.TileProgrammingTable;

/** Pins what the Programming Table's JEI category shows. The JEI screen itself is not headless-testable, but every
 *  number and stack it draws comes from {@link ProgrammingRecipeCollector} and {@link RoboticsJeiSubtypes}, which
 *  are JEI-free: one entry per board the table can actually program (reachable in its 6x4 grid), the blank board as
 *  the input, the table's own grid order and cost, and subtype keys that tell boards (and robots) apart by program. */
public class ProgrammingRecipeCollectorTester extends VanillaSetupBaseTester {

    private static final String BOARD_RECIPE_ID = "buildcraftunofficial:redstone_board";
    private static final int GRID_SIZE = TileProgrammingTable.OPTION_COLS * TileProgrammingTable.OPTION_ROWS;

    private static IProgrammingRecipe boardRecipe;
    private static List<ProgrammingRecipeJei> boardEntries;

    @BeforeAll
    public static void collectOnce() {
        BCRoboticsRecipes.ensureInitialized();
        boardRecipe = ProgrammingRecipeRegistry.INSTANCE.getRecipe(BOARD_RECIPE_ID);
        Assertions.assertNotNull(boardRecipe, "the board recipe is registered");
        boardEntries = new ArrayList<>();
        for (ProgrammingRecipeJei entry : ProgrammingRecipeCollector.collect()) {
            if (entry.id().startsWith(BOARD_RECIPE_ID + "/")) {
                boardEntries.add(entry);
            }
        }
    }

    @Test
    public void oneEntryPerProgrammableBoardAndNoneForTheBlankBoard() {
        Set<String> expected = new HashSet<>();
        for (RedstoneBoardNBT<?> board : RedstoneBoardRegistry.instance.getAllBoardNBTs()) {
            if (!BoardRobotEmptyNBT.ID.equals(board.getID())) {
                expected.add(board.getID());
            }
        }
        Assertions.assertFalse(expected.isEmpty(), "liveness: the registry has programmable boards");
        Set<String> shown = new HashSet<>();
        for (ProgrammingRecipeJei entry : boardEntries) {
            String id = ItemRedstoneBoard.getBoardNBT(entry.output()).getID();
            Assertions.assertTrue(shown.add(id), "board " + id + " appears once");
        }
        Assertions.assertEquals(expected, shown,
                "every programmable board gets an entry; blank -> blank is a no-op and is left out");
    }

    @Test
    public void theInputIsTheCraftableBlankBoard() {
        ItemStack crafted = new ItemStack(BCRoboticsItems.REDSTONE_BOARD.get());
        Assertions.assertFalse(boardEntries.isEmpty(), "liveness: the board recipe produced entries");
        for (ProgrammingRecipeJei entry : boardEntries) {
            Assertions.assertEquals(1, entry.inputs().size(), entry.id() + " shows exactly one input");
            ItemStack input = entry.inputs().get(0);
            Assertions.assertTrue(ItemStack.isSameItemSameComponents(crafted, input),
                    entry.id() + " input is the bare board the crafting table makes, so JEI 'uses' on it finds this");
            Assertions.assertTrue(boardRecipe.canCraft(input), entry.id() + " input is accepted by the table");
        }
    }

    @Test
    public void costAndOutputMatchWhatTheTableDoes() {
        Assertions.assertFalse(boardEntries.isEmpty(), "liveness: the board recipe produced entries");
        for (ProgrammingRecipeJei entry : boardEntries) {
            ItemStack option = entry.grid().get(entry.gridIndex());
            Assertions.assertEquals(boardRecipe.getEnergyCost(option), entry.microJoules(),
                    entry.id() + " charges the table's cost for that option");
            ItemStack crafted = boardRecipe.craft(entry.inputs().get(0).copy(), option.copy());
            Assertions.assertTrue(ItemStack.matches(crafted, entry.output()),
                    entry.id() + " output is exactly what the table crafts");
        }
    }

    @Test
    public void gridIsTheTablesOwnGridInItsOwnOrder() {
        List<ItemStack> tableGrid = boardRecipe.getOptions(TileProgrammingTable.OPTION_COLS,
                TileProgrammingTable.OPTION_ROWS);
        int expectedSize = Math.min(tableGrid.size(), GRID_SIZE);
        Assertions.assertFalse(boardEntries.isEmpty(), "liveness");
        for (ProgrammingRecipeJei entry : boardEntries) {
            Assertions.assertEquals(expectedSize, entry.grid().size(), entry.id() + " grid is the table's visible grid");
            for (int i = 0; i < expectedSize; i++) {
                Assertions.assertTrue(ItemStack.matches(tableGrid.get(i), entry.grid().get(i)),
                        entry.id() + " grid cell " + i + " matches the table");
            }
            Assertions.assertTrue(entry.gridIndex() >= 0 && entry.gridIndex() < expectedSize,
                    entry.id() + " highlights a visible cell");
        }
    }

    @Test
    public void entriesFollowTheTablesCheapestFirstOrder() {
        Assertions.assertTrue(boardEntries.size() > 1, "liveness: more than one entry to order");
        for (int i = 1; i < boardEntries.size(); i++) {
            Assertions.assertTrue(boardEntries.get(i - 1).gridIndex() < boardEntries.get(i).gridIndex(),
                    "entries are listed in grid order (position " + i + ")");
            Assertions.assertTrue(boardEntries.get(i - 1).microJoules() <= boardEntries.get(i).microJoules(),
                    "grid order is cheapest first (position " + i + ")");
        }
    }

    @Test
    public void optionsBeyondTheVisibleGridAreNotAdvertised() {
        // A hypothetical addon recipe offering more options than the 6x4 grid can show: the table's GUI can only
        // click the first 24, so JEI must not promise the rest.
        IProgrammingRecipe wide = new FakeRecipe("test:wide", GRID_SIZE + 6);
        List<ProgrammingRecipeJei> entries = ProgrammingRecipeCollector.collect(List.of(wide),
                List.of(new ItemStack(Items.PAPER)));
        Assertions.assertEquals(GRID_SIZE, entries.size(), "only the clickable cells get entries");
        for (ProgrammingRecipeJei entry : entries) {
            Assertions.assertEquals(GRID_SIZE, entry.grid().size(), "the grid is cropped to what the table shows");
        }
    }

    @Test
    public void aRecipeNoCandidateSatisfiesIsSkipped() {
        IProgrammingRecipe wide = new FakeRecipe("test:wide", 3);
        Assertions.assertTrue(ProgrammingRecipeCollector.collect(List.of(wide),
                List.of(new ItemStack(Items.STICK))).isEmpty(), "no craftable input, nothing to show");
    }

    @Test
    public void boardSubtypeKeysSeparateProgramsAndMergeBlanks() {
        Assertions.assertEquals(RoboticsJeiSubtypes.boardKey(new ItemStack(BCRoboticsItems.REDSTONE_BOARD.get())),
                RoboticsJeiSubtypes.boardKey(ItemRedstoneBoard.createStack(BoardRobotEmptyNBT.INSTANCE)),
                "the crafted bare board and the table's blank option are one JEI entry");
        Assertions.assertNotEquals(RoboticsJeiSubtypes.boardKey(ItemRedstoneBoard.createStack(BoardRobotMinerNBT.INSTANCE)),
                RoboticsJeiSubtypes.boardKey(ItemRedstoneBoard.createStack(BoardRobotKnightNBT.INSTANCE)),
                "two programs are two JEI entries");
        Assertions.assertNotNull(RoboticsJeiSubtypes.boardKey(ItemRedstoneBoard.createStack(BoardRobotPickerNBT.INSTANCE)));
    }

    @Test
    public void robotSubtypeKeysSplitOnProgramAndChargedOrNotOnly() {
        Assertions.assertEquals(RoboticsJeiSubtypes.robotKey(ItemRobot.createRobotStack(BoardRobotMinerNBT.ID, 1)),
                RoboticsJeiSubtypes.robotKey(ItemRobot.createRobotStack(BoardRobotMinerNBT.ID, IRobotAccess.MAX_POWER)),
                "the charge LEVEL is state: any charged miner is one JEI entry");
        Assertions.assertNotEquals(RoboticsJeiSubtypes.robotKey(ItemRobot.createRobotStack(BoardRobotMinerNBT.ID, 0)),
                RoboticsJeiSubtypes.robotKey(ItemRobot.createRobotStack(BoardRobotMinerNBT.ID, IRobotAccess.MAX_POWER)),
                "a drained and a charged miner render differently and both sit in the creative tab: two entries,"
                        + " or JEI flags them as duplicates");
        Assertions.assertNotEquals(RoboticsJeiSubtypes.robotKey(ItemRobot.createRobotStack(BoardRobotMinerNBT.ID, 0)),
                RoboticsJeiSubtypes.robotKey(ItemRobot.createRobotStack(BoardRobotKnightNBT.ID, 0)),
                "robots with different programs are different JEI entries");
        Object blank = RoboticsJeiSubtypes.robotKey(ItemRobot.createRobotStack(BoardRobotEmptyNBT.ID, 0));
        Assertions.assertEquals(blank, RoboticsJeiSubtypes.robotKey(new ItemStack(BCRoboticsItems.ROBOT.get())),
                "the crafted bare robot is the blank robot");
        Assertions.assertEquals(blank,
                RoboticsJeiSubtypes.robotKey(ItemRobot.createRobotStack(BoardRobotEmptyNBT.ID, IRobotAccess.MAX_POWER)),
                "the blank robot hides its charge everywhere, so charge never splits it");
    }

    /** Offers {@code optionCount} distinct book stacks (count = position + 1), accepts only paper, costs the count. */
    private static final class FakeRecipe implements IProgrammingRecipe {
        private final String id;
        private final int optionCount;

        FakeRecipe(String id, int optionCount) {
            this.id = id;
            this.optionCount = optionCount;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public List<ItemStack> getOptions(int width, int height) {
            List<ItemStack> options = new ArrayList<>();
            for (int i = 0; i < optionCount; i++) {
                options.add(new ItemStack(Items.BOOK, i + 1));
            }
            return options;
        }

        @Override
        public long getEnergyCost(ItemStack option) {
            return option.getCount();
        }

        @Override
        public boolean canCraft(ItemStack input) {
            return input.is(Items.PAPER);
        }

        @Override
        public ItemStack craft(ItemStack input, ItemStack option) {
            return option.copy();
        }
    }
}
