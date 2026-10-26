/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.robotics;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import io.netty.buffer.Unpooled;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;

import buildcraft.api.boards.RedstoneBoardNBT;
import buildcraft.api.recipes.IProgrammingRecipe;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.lib.compat.viewer.TransferResult;
import buildcraft.lib.gui.ContainerBC_Neptune;
import buildcraft.lib.net.PacketBufferBC;
import buildcraft.lib.recipe.ProgrammingRecipeRegistry;

import buildcraft.robotics.boards.BoardRobotEmptyNBT;
import buildcraft.robotics.boards.BoardRobotMinerNBT;
import buildcraft.robotics.boards.BoardRobotPickerNBT;
import buildcraft.robotics.container.ContainerProgrammingTable;
import buildcraft.robotics.container.ProgrammingTableLayout;
import buildcraft.robotics.container.RoboticsTableTransfer;
import buildcraft.robotics.item.ItemRedstoneBoard;
import buildcraft.robotics.item.ItemRobot;
import buildcraft.robotics.recipe.IntegrationRecipeCollector;
import buildcraft.robotics.recipe.IntegrationRecipeJei;
import buildcraft.robotics.recipe.ProgrammingRecipeCollector;
import buildcraft.robotics.recipe.ProgrammingRecipeJei;
import buildcraft.silicon.BCSiliconBlocks;
import buildcraft.silicon.container.ContainerAssemblyTable;
import buildcraft.silicon.container.ContainerIntegrationTable;
import buildcraft.silicon.tile.TileAssemblyTable;
import buildcraft.silicon.tile.TileIntegrationTable;
import buildcraft.silicon.tile.TileProgrammingTable;

/** Game tests for the server side of the JEI "+" transfer into the Programming and Integration Tables, driven the
 *  way the network payload drives it: a container message (decoded {@code readMessage}) against the player's real
 *  inventory. JEI's own client glue is not headless-testable; everything that moves items or picks an option is.
 *
 *  <p>The tests deliberately never tick the table between the message and the first assertion: the Programming
 *  Table resolves its recipe and option grid only in {@code serverTick}, so a transfer that merely dropped the
 *  board in would leave {@code options == null} and the selection clamped to -1 until the next tick. A board
 *  made the survival way is a <em>bare</em> stack (no components); the helpers that write a blob
 *  ({@code createStack(null)}, {@code createRobotStack}) do not match it item-and-components, so both forms are
 *  exercised. */
public class RoboticsTableTransferTester {

    private static final String RECIPE_ID = "buildcraftunofficial:redstone_board";
    private static final String MINER_ID = BoardRobotMinerNBT.ID;
    private static final long GREEN_BOARD_MICRO_MJ = 800_000_000L;
    private static final long ROBOT_INTEGRATION_MICRO_MJ = 5_000_000_000L;

    // ── Programming Table ───────────────────────────────────────────────────

    /** The headline case: a survival-crafted (bare) blank board is moved in and the picker option is selected
     *  before any tick, so the very next craft can use it. */
    public static void programmingTransferSelectsOptionInTheSameTick(GameTestHelper helper) {
        BCRoboticsRecipes.ensureInitialized();
        TileProgrammingTable table = placeProgrammingTable(helper);
        Player player = survivalPlayer(helper);
        Inventory inv = player.getInventory();
        inv.setItem(3, new ItemStack(BCRoboticsItems.REDSTONE_BOARD.get(), 2));
        ContainerProgrammingTable menu = new ContainerProgrammingTable(0, player, table);

        // The advisory (client-side) check agrees, and changes nothing.
        expect(helper, TransferResult.OK,
                RoboticsTableTransfer.program(inv, table, RECIPE_ID, BoardRobotPickerNBT.ID, false), "advisory check");
        if (!table.invBoard.getStackInSlot(0).isEmpty() || inv.getItem(3).getCount() != 2) {
            helper.fail("the advisory check must not move anything");
        }

        sendProgramming(menu, RECIPE_ID, BoardRobotPickerNBT.ID);

        ItemStack slotted = table.invBoard.getStackInSlot(0);
        if (!slotted.is(BCRoboticsItems.REDSTONE_BOARD.get()) || slotted.getCount() != 1) {
            helper.fail("exactly one blank board should be in the table's board slot, got " + slotted);
        }
        if (inv.getItem(3).getCount() != 1) {
            helper.fail("the player should be down exactly one board, left with " + inv.getItem(3));
        }
        if (table.options == null) {
            helper.fail("the option grid is still unresolved: the transfer must resolve the recipe itself");
        }
        int picker = optionIndexOf(table, BoardRobotPickerNBT.ID);
        if (picker < 0 || table.optionId != picker) {
            helper.fail("the picker option (index " + picker + ") should be selected, optionId=" + table.optionId);
        }
        if (table.getTarget() != GREEN_BOARD_MICRO_MJ) {
            helper.fail("the selected green-tier board should cost 800 MJ at once, got " + table.getTarget());
        }

        table.receiveLaserPower(GREEN_BOARD_MICRO_MJ);
        table.serverTick();
        ItemStack result = table.invResult.getStackInSlot(0);
        if (result.isEmpty() || !BoardRobotPickerNBT.ID.equals(boardIdOf(result))) {
            helper.fail("the transferred setup should craft a programmed picker board, got " + result);
        }
        helper.succeed();
    }

    /** A blank board that carries a CUSTOM_DATA blob ({@code createStack(null)}) is just as blank as a bare one;
     *  and a board already in the slot is kept and reused rather than topped up from the inventory. */
    public static void programmingTransferAcceptsEveryBlankFormAndReusesTheSlot(GameTestHelper helper) {
        BCRoboticsRecipes.ensureInitialized();
        TileProgrammingTable table = placeProgrammingTable(helper);
        Player player = survivalPlayer(helper);
        Inventory inv = player.getInventory();
        inv.setItem(0, ItemRedstoneBoard.createStack(null));
        ContainerProgrammingTable menu = new ContainerProgrammingTable(0, player, table);

        sendProgramming(menu, RECIPE_ID, BoardRobotPickerNBT.ID);
        if (table.invBoard.getStackInSlot(0).isEmpty() || !inv.getItem(0).isEmpty()) {
            helper.fail("a blob-carrying blank board should have moved into the slot");
        }
        int picker = optionIndexOf(table, BoardRobotPickerNBT.ID);
        if (picker < 0 || table.optionId != picker) {
            helper.fail("the picker option should be selected for the blob blank, optionId=" + table.optionId);
        }

        // Slot now holds a board; a spare blank in the inventory must stay put and the selection just moves.
        inv.setItem(1, new ItemStack(BCRoboticsItems.REDSTONE_BOARD.get(), 4));
        sendProgramming(menu, RECIPE_ID, MINER_ID);
        if (inv.getItem(1).getCount() != 4) {
            helper.fail("a board already in the slot must be reused, not topped up from the inventory");
        }
        int miner = optionIndexOf(table, MINER_ID);
        if (miner < 0 || table.optionId != miner) {
            helper.fail("re-requesting another board should just move the selection, optionId=" + table.optionId);
        }
        helper.succeed();
    }

    /** Anything the server cannot honour moves nothing and selects nothing: no blank board, only programmed
     *  boards, unknown board or recipe ids, and a board slot holding something that is not a board. */
    public static void programmingTransferRejectsWhatItCannotHonour(GameTestHelper helper) {
        BCRoboticsRecipes.ensureInitialized();
        TileProgrammingTable table = placeProgrammingTable(helper);
        Player player = survivalPlayer(helper);
        Inventory inv = player.getInventory();
        ContainerProgrammingTable menu = new ContainerProgrammingTable(0, player, table);

        // Nothing at all to move.
        sendProgramming(menu, RECIPE_ID, BoardRobotPickerNBT.ID);
        assertUntouched(helper, table, "empty inventory");
        expect(helper, TransferResult.MISSING,
                RoboticsTableTransfer.program(inv, table, RECIPE_ID, BoardRobotPickerNBT.ID, false), "empty inventory");

        // Only a programmed board: the JEI entry shows a BLANK board, so a programmed one is not taken.
        ItemStack programmed = ItemRedstoneBoard.createStack(BoardRobotPickerNBT.INSTANCE);
        inv.setItem(0, programmed.copy());
        sendProgramming(menu, RECIPE_ID, BoardRobotPickerNBT.ID);
        assertUntouched(helper, table, "programmed board only");
        if (!ItemStack.matches(programmed, inv.getItem(0))) {
            helper.fail("a programmed board must stay in the player's inventory");
        }

        // A blank is present now; unknown ids still move nothing.
        inv.setItem(1, new ItemStack(BCRoboticsItems.REDSTONE_BOARD.get()));
        sendProgramming(menu, RECIPE_ID, "buildcraftunofficial:no_such_board");
        assertUntouched(helper, table, "unknown board id");
        sendProgramming(menu, "buildcraftunofficial:no_such_recipe", BoardRobotPickerNBT.ID);
        assertUntouched(helper, table, "unknown recipe id");
        expect(helper, TransferResult.INVALID,
                RoboticsTableTransfer.program(inv, table, RECIPE_ID, "buildcraftunofficial:no_such_board", false),
                "unknown board id (advisory)");
        if (inv.getItem(1).getCount() != 1) {
            helper.fail("the spare blank board must not have been taken by a rejected request");
        }

        // A board slot that already holds a non-board blocks the transfer, and is left alone.
        table.invBoard.setStackInSlot(0, new ItemStack(Items.STICK));
        sendProgramming(menu, RECIPE_ID, BoardRobotPickerNBT.ID);
        if (!table.invBoard.getStackInSlot(0).is(Items.STICK) || inv.getItem(1).getCount() != 1
                || table.optionId != -1) {
            helper.fail("a board slot holding a stick must block the transfer untouched");
        }
        expect(helper, TransferResult.BUSY,
                RoboticsTableTransfer.program(inv, table, RECIPE_ID, BoardRobotPickerNBT.ID, false), "stick in slot");
        helper.succeed();
    }

    /** The data contract with the JEI category: pressing "+" on ANY Programming entry selects exactly the option
     *  that entry shows (and highlights, on its page of the grid), at the cost it prints. Driven from the
     *  collector's real entries, with the ids the JEI handler would send, so a drift between what JEI shows and
     *  what the server selects fails here. */
    public static void programmingTransferMatchesEveryJeiEntry(GameTestHelper helper) {
        BCRoboticsRecipes.ensureInitialized();
        TileProgrammingTable table = placeProgrammingTable(helper);
        Player player = survivalPlayer(helper);
        Inventory inv = player.getInventory();
        ContainerProgrammingTable menu = new ContainerProgrammingTable(0, player, table);

        List<ProgrammingRecipeJei> entries = ProgrammingRecipeCollector.collect();
        if (entries.size() < 5) {
            helper.fail("liveness: expected the collector to offer several Programming entries, got " + entries.size());
        }
        for (ProgrammingRecipeJei entry : entries) {
            table.invBoard.setStackInSlot(0, ItemStack.EMPTY);
            table.findRecipe(); // forget the previous entry's recipe and selection
            inv.setItem(0, entry.inputs().get(0).copy());

            sendProgramming(menu, entry.recipeId(), entry.boardId());

            if (table.optionId != entry.optionIndex()) {
                helper.fail(entry.id() + ": '+' selected option " + table.optionId + " but the entry is for option "
                        + entry.optionIndex());
            }
            if (!ItemStack.matches(table.options.get(table.optionId), entry.grid().get(entry.gridIndex()))) {
                helper.fail(entry.id() + ": the selected cell is not the board the entry shows");
            }
            if (table.getTarget() != entry.microJoules()) {
                helper.fail(entry.id() + ": the table wants " + table.getTarget() + " but the entry prints "
                        + entry.microJoules());
            }
        }
        helper.succeed();
    }

    /** A recipe offering more options than one grid holds (an addon's boards on top of BuildCraft's own) is paged:
     *  the transfer selects a board past the 24th option, which it used to refuse as unreachable, and the menu shows
     *  that option's page on request while the first page stays as it was. Registers a stub recipe for the length of
     *  one synchronous body, so no other test can see it; the marker input is slotted by hand. */
    public static void programmingTransferReachesBoardsOnLaterPages(GameTestHelper helper) {
        BCRoboticsRecipes.ensureInitialized();
        PagedBoardRecipe recipe = new PagedBoardRecipe();
        ProgrammingRecipeRegistry.INSTANCE.addRecipe(recipe);
        try {
            TileProgrammingTable table = placeProgrammingTable(helper);
            Player player = survivalPlayer(helper);
            ContainerProgrammingTable menu = new ContainerProgrammingTable(0, player, table);
            table.invBoard.setStackInSlot(0, new ItemStack(Items.CARROT_ON_A_STICK));

            sendProgramming(menu, recipe.getId(), MINER_ID);

            int miner = ProgrammingTableLayout.PAGE_SIZE + 1;
            if (table.optionId != miner) {
                helper.fail("the miner is option " + miner + " (second page, second cell) but optionId is "
                        + table.optionId);
            }
            if (menu.getOptionPageCount() != 2) {
                helper.fail("26 options need two pages, the menu reports " + menu.getOptionPageCount());
            }
            if (menu.getOptionPage() != 0 || !menu.getSlot(2).getItem().is(Items.PAPER)) {
                helper.fail("the menu opens on the first page, whose first cell is the filler, got page "
                        + menu.getOptionPage() + " showing " + menu.getSlot(2).getItem());
            }

            menu.setOptionPage(ProgrammingTableLayout.pageOf(table.optionId));
            ItemStack shown = menu.getSlot(2 + ProgrammingTableLayout.cellOf(table.optionId)).getItem();
            if (!MINER_ID.equals(boardIdOf(shown))) {
                helper.fail("the selected miner should show in its cell on the second page, got " + shown);
            }
            if (!menu.getSlot(2).getItem().is(BCRoboticsItems.REDSTONE_BOARD.get())) {
                helper.fail("the second page starts at option 24, the picker board, got " + menu.getSlot(2).getItem());
            }
            if (!menu.getSlot(2 + 2).getItem().isEmpty()) {
                helper.fail("a cell past the last option shows nothing, got " + menu.getSlot(2 + 2).getItem());
            }

            menu.setOptionPage(9);
            if (menu.getOptionPage() != 1) {
                helper.fail("a page past the last is held on the last page, got " + menu.getOptionPage());
            }
            if (table.optionId != miner) {
                helper.fail("turning pages is a view change and must not move the table's selection");
            }

            sendProgramming(menu, recipe.getId(), BoardRobotPickerNBT.ID);
            if (table.optionId != ProgrammingTableLayout.PAGE_SIZE) {
                helper.fail("the picker is option " + ProgrammingTableLayout.PAGE_SIZE + " but optionId is "
                        + table.optionId);
            }
        } finally {
            ProgrammingRecipeRegistry.INSTANCE.removeRecipe(recipe);
        }
        helper.succeed();
    }

    /** Accepts only a carrot on a stick (nothing else in a test world is one) and offers 24 filler options, then
     *  the picker and miner boards: 26 options, two pages. */
    private static final class PagedBoardRecipe implements IProgrammingRecipe {
        @Override
        public String getId() {
            return "a_paging_test:boards";
        }

        @Override
        public List<ItemStack> getOptions(int width, int height) {
            List<ItemStack> options = new ArrayList<>();
            for (int i = 0; i < ProgrammingTableLayout.PAGE_SIZE; i++) {
                options.add(new ItemStack(Items.PAPER));
            }
            options.add(ItemRedstoneBoard.createStack(BoardRobotPickerNBT.INSTANCE));
            options.add(ItemRedstoneBoard.createStack(BoardRobotMinerNBT.INSTANCE));
            return options;
        }

        @Override
        public long getEnergyCost(ItemStack option) {
            return 0;
        }

        @Override
        public boolean canCraft(ItemStack input) {
            return input.is(Items.CARROT_ON_A_STICK);
        }

        @Override
        public ItemStack craft(ItemStack input, ItemStack option) {
            return option.copy();
        }
    }

    // ── Integration Table ───────────────────────────────────────────────────

    /** A survival-crafted (bare) blank robot goes to the centre slot, the board to a ring slot, and the table's
     *  next tick finds the recipe — through to a programmed robot with the safety charge. */
    public static void integrationTransferFillsCentreAndRing(GameTestHelper helper) {
        BCRoboticsRecipes.ensureInitialized();
        TileIntegrationTable table = placeIntegrationTable(helper);
        Player player = survivalPlayer(helper);
        Inventory inv = player.getInventory();
        inv.setItem(0, new ItemStack(BCRoboticsItems.ROBOT.get(), 2));
        inv.setItem(1, ItemRedstoneBoard.createStack(BoardRobotPickerNBT.INSTANCE));
        inv.setItem(2, new ItemStack(Items.DIRT, 5));
        ContainerIntegrationTable menu = new ContainerIntegrationTable(0, player, table);

        expect(helper, TransferResult.OK,
                RoboticsTableTransfer.integrate(inv, table, BoardRobotPickerNBT.ID, false), "advisory check");
        if (!table.invTarget.getStackInSlot(0).isEmpty() || inv.getItem(0).getCount() != 2) {
            helper.fail("the advisory check must not move anything");
        }

        sendIntegration(menu, BoardRobotPickerNBT.ID);

        ItemStack centre = table.invTarget.getStackInSlot(0);
        if (!centre.is(BCRoboticsItems.ROBOT.get()) || centre.getCount() != 1) {
            helper.fail("exactly one blank robot should be in the centre slot, got " + centre);
        }
        if (inv.getItem(0).getCount() != 1 || !inv.getItem(1).isEmpty() || inv.getItem(2).getCount() != 5) {
            helper.fail("the player should be down one robot and the board only, got " + inv.getItem(0) + " / "
                    + inv.getItem(1) + " / " + inv.getItem(2));
        }
        int boards = 0;
        for (ItemStack ring : table.invToIntegrate.stacks) {
            if (!ring.isEmpty()) {
                boards++;
                if (!BoardRobotPickerNBT.ID.equals(boardIdOf(ring))) {
                    helper.fail("the ring holds the wrong board: " + ring);
                }
            }
        }
        if (boards != 1) {
            helper.fail("exactly one ring slot should hold the board, " + boards + " do");
        }

        table.serverTick();
        if (table.getTarget() != ROBOT_INTEGRATION_MICRO_MJ) {
            helper.fail("one tick later the table should want 5000 MJ, got " + table.getTarget());
        }
        table.receiveLaserPower(ROBOT_INTEGRATION_MICRO_MJ);
        table.serverTick();
        ItemStack result = table.invResult.getStackInSlot(0);
        if (result.isEmpty() || !BoardRobotPickerNBT.ID.equals(ItemRobot.getBoardId(result))
                || ItemRobot.getEnergy(result) != IRobotAccess.SAFETY_POWER) {
            helper.fail("expected a charged picker robot in the result slot, got " + result);
        }
        helper.succeed();
    }

    /** A blank robot that is charged or blob-carrying is still a blank robot: it is taken, and its charge rides
     *  into the result as it does for a hand-placed one. A robot already in the centre slot is kept. */
    public static void integrationTransferTakesChargedBlankRobotAndKeepsTheCentre(GameTestHelper helper) {
        BCRoboticsRecipes.ensureInitialized();
        TileIntegrationTable table = placeIntegrationTable(helper);
        Player player = survivalPlayer(helper);
        Inventory inv = player.getInventory();
        inv.setItem(0, ItemRobot.createRobotStack(null, 123_456L));
        inv.setItem(1, ItemRedstoneBoard.createStack(BoardRobotPickerNBT.INSTANCE));
        ContainerIntegrationTable menu = new ContainerIntegrationTable(0, player, table);

        sendIntegration(menu, BoardRobotPickerNBT.ID);
        if (ItemRobot.getEnergy(table.invTarget.getStackInSlot(0)) != 123_456L) {
            helper.fail("the charged blank robot should be in the centre, got " + table.invTarget.getStackInSlot(0));
        }
        table.serverTick();
        table.receiveLaserPower(ROBOT_INTEGRATION_MICRO_MJ);
        table.serverTick();
        if (ItemRobot.getEnergy(table.invResult.getStackInSlot(0)) != 123_456L) {
            helper.fail("the robot's charge must ride into the result, got " + table.invResult.getStackInSlot(0));
        }

        // Second go on a fresh table: the centre already holds a robot, so only the board moves.
        BlockPos second = new BlockPos(4, 1, 4);
        helper.setBlock(second, BCSiliconBlocks.INTEGRATION_TABLE.get());
        TileIntegrationTable table2 = blockEntity(helper, second, TileIntegrationTable.class);
        table2.invTarget.setStackInSlot(0, new ItemStack(BCRoboticsItems.ROBOT.get()));
        inv.setItem(0, new ItemStack(BCRoboticsItems.ROBOT.get(), 3));
        inv.setItem(1, ItemRedstoneBoard.createStack(BoardRobotPickerNBT.INSTANCE));
        ContainerIntegrationTable menu2 = new ContainerIntegrationTable(1, player, table2);
        sendIntegration(menu2, BoardRobotPickerNBT.ID);
        if (inv.getItem(0).getCount() != 3 || !inv.getItem(1).isEmpty()) {
            helper.fail("a robot already in the centre must be reused; only the board should have moved");
        }
        helper.succeed();
    }

    /** All-or-nothing: if either the robot or the board is missing, or the table cannot take them, nothing moves
     *  (no half-filled table). A programmed robot is never taken as the blank chassis. */
    public static void integrationTransferIsAtomicAndSelective(GameTestHelper helper) {
        BCRoboticsRecipes.ensureInitialized();
        TileIntegrationTable table = placeIntegrationTable(helper);
        Player player = survivalPlayer(helper);
        Inventory inv = player.getInventory();
        ContainerIntegrationTable menu = new ContainerIntegrationTable(0, player, table);
        ItemStack board = ItemRedstoneBoard.createStack(BoardRobotPickerNBT.INSTANCE);
        ItemStack robot = new ItemStack(BCRoboticsItems.ROBOT.get());

        // Robot but no board.
        inv.setItem(0, robot.copy());
        sendIntegration(menu, BoardRobotPickerNBT.ID);
        assertUntouched(helper, table, inv, 1, 0, "robot but no board");
        expect(helper, TransferResult.MISSING,
                RoboticsTableTransfer.integrate(inv, table, BoardRobotPickerNBT.ID, false), "robot but no board");

        // Board but no robot.
        inv.setItem(0, ItemStack.EMPTY);
        inv.setItem(1, board.copy());
        sendIntegration(menu, BoardRobotPickerNBT.ID);
        assertUntouched(helper, table, inv, 0, 1, "board but no robot");

        // A programmed robot is not a blank chassis.
        ItemStack programmedRobot = ItemRobot.createRobotStack(BoardRobotPickerNBT.ID, 1_000L);
        inv.setItem(0, programmedRobot.copy());
        sendIntegration(menu, BoardRobotPickerNBT.ID);
        assertUntouched(helper, table, inv, 1, 1, "programmed robot only");
        if (!ItemStack.matches(programmedRobot, inv.getItem(0))) {
            helper.fail("a programmed robot must stay in the player's inventory");
        }

        // Both present, but the ring already holds a different board: the table would pick the wrong program.
        inv.setItem(0, robot.copy());
        table.invToIntegrate.setStackInSlot(5, ItemRedstoneBoard.createStack(BoardRobotMinerNBT.INSTANCE));
        sendIntegration(menu, BoardRobotPickerNBT.ID);
        if (!table.invTarget.getStackInSlot(0).isEmpty() || inv.getItem(0).isEmpty() || inv.getItem(1).isEmpty()) {
            helper.fail("a busy ring must block the whole transfer, robot included");
        }
        expect(helper, TransferResult.BUSY,
                RoboticsTableTransfer.integrate(inv, table, BoardRobotPickerNBT.ID, false), "foreign board in ring");
        table.invToIntegrate.setStackInSlot(5, ItemStack.EMPTY);

        // A centre slot holding a non-robot blocks it too.
        table.invTarget.setStackInSlot(0, new ItemStack(Items.STICK));
        sendIntegration(menu, BoardRobotPickerNBT.ID);
        if (!table.invTarget.getStackInSlot(0).is(Items.STICK) || inv.getItem(0).isEmpty()
                || inv.getItem(1).isEmpty() || !table.invToIntegrate.stacks.stream().allMatch(ItemStack::isEmpty)) {
            helper.fail("a stick in the centre must block the transfer untouched");
        }
        table.invTarget.setStackInSlot(0, ItemStack.EMPTY);

        // The blank board and unknown ids are not programs: nothing moves.
        sendIntegration(menu, BoardRobotEmptyNBT.ID);
        sendIntegration(menu, "buildcraftunofficial:no_such_board");
        if (!table.invTarget.getStackInSlot(0).isEmpty() || inv.getItem(0).isEmpty() || inv.getItem(1).isEmpty()) {
            helper.fail("the blank board and unknown ids must not move anything");
        }
        helper.succeed();
    }

    /** The same contract for the Integration Table: for every JEI entry, the entry's own blank robot and board
     *  fill the table, and one tick later the table offers exactly the entry's first output at its price. */
    public static void integrationTransferMatchesEveryJeiEntry(GameTestHelper helper) {
        BCRoboticsRecipes.ensureInitialized();
        TileIntegrationTable table = placeIntegrationTable(helper);
        Player player = survivalPlayer(helper);
        Inventory inv = player.getInventory();
        ContainerIntegrationTable menu = new ContainerIntegrationTable(0, player, table);

        List<IntegrationRecipeJei> entries = IntegrationRecipeCollector.collect();
        if (entries.size() < 5) {
            helper.fail("liveness: expected the collector to offer several Integration entries, got " + entries.size());
        }
        for (IntegrationRecipeJei entry : entries) {
            table.invTarget.setStackInSlot(0, ItemStack.EMPTY);
            for (int i = 0; i < table.invToIntegrate.getSlots(); i++) {
                table.invToIntegrate.setStackInSlot(i, ItemStack.EMPTY);
            }
            inv.setItem(0, entry.center().copy());
            inv.setItem(1, entry.expansions().get(0).copy());

            sendIntegration(menu, entry.boardId());
            table.serverTick();

            if (!inv.getItem(0).isEmpty() || !inv.getItem(1).isEmpty()) {
                helper.fail(entry.id() + ": the entry's robot and board were not both taken");
            }
            if (!ItemStack.matches(table.getOutput(), entry.outputs().get(0))) {
                helper.fail(entry.id() + ": the table offers " + table.getOutput() + " but the entry shows "
                        + entry.outputs().get(0));
            }
            if (table.getTarget() != entry.microJoules()) {
                helper.fail(entry.id() + ": the table wants " + table.getTarget() + " but the entry prints "
                        + entry.microJoules());
            }
        }
        helper.succeed();
    }

    // ── GUI layout pin ──────────────────────────────────────────────────────

    /** The three table menus' slot order and positions (Programming, Integration and Assembly), pinned against
     *  literals so that pulling the geometry into shared constants (which the GUIs and JEI categories also read)
     *  cannot move a slot off its texture. The Integration ring's container order is the one the JEI category's ring
     *  cells assume: reading order with the centre as menu slot 4. */
    public static void tableMenusKeepTheirSlotLayout(GameTestHelper helper) {
        BCRoboticsRecipes.ensureInitialized();
        Player player = survivalPlayer(helper);

        TileProgrammingTable programming = placeProgrammingTable(helper);
        ContainerProgrammingTable pMenu = new ContainerProgrammingTable(0, player, programming);
        expectSlot(helper, pMenu.getSlot(0), 8, 36, "programming board");
        expectSlot(helper, pMenu.getSlot(1), 8, 90, "programming result");
        for (int i = 0; i < 24; i++) {
            expectSlot(helper, pMenu.getSlot(2 + i), 43 + (i % 6) * 18, 36 + (i / 6) * 18, "programming grid " + i);
        }
        expectSlot(helper, pMenu.getSlot(26), 8, 123, "programming player inventory origin");

        BlockPos pos = new BlockPos(4, 1, 4);
        helper.setBlock(pos, BCSiliconBlocks.INTEGRATION_TABLE.get());
        TileIntegrationTable integration = blockEntity(helper, pos, TileIntegrationTable.class);
        for (int i = 0; i < integration.invToIntegrate.getSlots(); i++) {
            integration.invToIntegrate.setStackInSlot(i, new ItemStack(Items.STICK, i + 1));
        }
        integration.invTarget.setStackInSlot(0, new ItemStack(Items.DIRT, 33));
        ContainerIntegrationTable iMenu = new ContainerIntegrationTable(1, player, integration);
        int ringIndex = 0;
        for (int i = 0; i < 9; i++) {
            expectSlot(helper, iMenu.getSlot(i), 19 + (i % 3) * 25, 24 + (i / 3) * 25, "integration ring cell " + i);
            ItemStack shown = iMenu.getSlot(i).getItem();
            if (i == 4) {
                if (!shown.is(Items.DIRT)) {
                    helper.fail("menu slot 4 must be the centre (target) slot, shows " + shown);
                }
            } else if (!shown.is(Items.STICK) || shown.getCount() != ++ringIndex) {
                helper.fail("menu slot " + i + " must show ring slot " + (ringIndex - 1) + ", shows " + shown);
            }
        }
        expectSlot(helper, iMenu.getSlot(9), 101, 36, "integration preview");
        expectSlot(helper, iMenu.getSlot(10), 138, 49, "integration result");
        expectSlot(helper, iMenu.getSlot(11), 8, 109, "integration player inventory origin");

        BlockPos assemblyPos = new BlockPos(1, 1, 5);
        helper.setBlock(assemblyPos, BCSiliconBlocks.ASSEMBLY_TABLE.get());
        TileAssemblyTable assembly = blockEntity(helper, assemblyPos, TileAssemblyTable.class);
        ContainerAssemblyTable aMenu = new ContainerAssemblyTable(2, player, assembly);
        for (int i = 0; i < 12; i++) {
            expectSlot(helper, aMenu.getSlot(i), 8 + (i % 3) * 18, 36 + (i / 3) * 18, "assembly input " + i);
            expectSlot(helper, aMenu.getSlot(12 + i), 116 + (i % 3) * 18, 36 + (i / 3) * 18,
                    "assembly recipe cell " + i);
        }
        expectSlot(helper, aMenu.getSlot(24), 8, 123, "assembly player inventory origin");
        helper.succeed();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static Player survivalPlayer(GameTestHelper helper) {
        return helper.makeMockPlayer(GameType.SURVIVAL);
    }

    private static TileProgrammingTable placeProgrammingTable(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, BCSiliconBlocks.PROGRAMMING_TABLE.get());
        return blockEntity(helper, pos, TileProgrammingTable.class);
    }

    private static TileIntegrationTable placeIntegrationTable(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, BCSiliconBlocks.INTEGRATION_TABLE.get());
        return blockEntity(helper, pos, TileIntegrationTable.class);
    }

    /** Replays the client's container message into the server-side menu (the mock player is not a
     *  ServerPlayer, so the payload handler cannot run; it only forwards to {@code readMessage}). */
    private static void sendProgramming(ContainerProgrammingTable menu, String recipeId, String boardId) {
        menu.player.containerMenu = menu;
        send(menu, ContainerBC_Neptune.NET_JEI_TRANSFER_PROGRAMMING, buf -> {
            buf.writeUtf(recipeId);
            buf.writeUtf(boardId);
        });
    }

    private static void sendIntegration(ContainerIntegrationTable menu, String boardId) {
        menu.player.containerMenu = menu;
        send(menu, ContainerBC_Neptune.NET_JEI_TRANSFER_INTEGRATION, buf -> buf.writeUtf(boardId));
    }

    private static void send(ContainerBC_Neptune menu, int id, Consumer<PacketBufferBC> writer) {
        PacketBufferBC buffer = new PacketBufferBC(Unpooled.buffer());
        writer.accept(buffer);
        menu.readMessage(id, buffer, false, null);
        buffer.release();
    }

    private static void expect(GameTestHelper helper, TransferResult want,
            TransferResult got, String what) {
        if (want != got) {
            helper.fail(what + ": expected " + want + " but got " + got);
        }
    }

    private static void expectSlot(GameTestHelper helper, Slot slot, int x, int y, String what) {
        if (slot.x != x || slot.y != y) {
            helper.fail(what + " should sit at (" + x + ", " + y + ") but is at (" + slot.x + ", " + slot.y + ")");
        }
    }

    /** The Programming Table is exactly as it was: empty board slot, no selection. */
    private static void assertUntouched(GameTestHelper helper, TileProgrammingTable table, String what) {
        if (!table.invBoard.getStackInSlot(0).isEmpty() || table.optionId != -1) {
            helper.fail(what + ": the table must be untouched, board slot " + table.invBoard.getStackInSlot(0)
                    + ", optionId " + table.optionId);
        }
    }

    /** The Integration Table is exactly as it was (empty), and inventory slots 0 / 1 still hold their items
     *  iff {@code robotLeft} / {@code boardLeft} are non-zero. */
    private static void assertUntouched(GameTestHelper helper, TileIntegrationTable table, Inventory inv,
            int robotLeft, int boardLeft, String what) {
        if (!table.invTarget.getStackInSlot(0).isEmpty()
                || !table.invToIntegrate.stacks.stream().allMatch(ItemStack::isEmpty)) {
            helper.fail(what + ": the table must be untouched");
        }
        if (inv.getItem(0).isEmpty() == (robotLeft != 0) || inv.getItem(1).isEmpty() == (boardLeft != 0)) {
            helper.fail(what + ": the player's items must be untouched");
        }
    }

    private static String boardIdOf(ItemStack board) {
        RedstoneBoardNBT<?> nbt = ItemRedstoneBoard.getBoardNBT(board);
        return nbt == null ? null : nbt.getID();
    }

    private static int optionIndexOf(TileProgrammingTable table, String boardId) {
        if (table.options == null) {
            return -1;
        }
        for (int i = 0; i < table.options.size(); i++) {
            if (boardId.equals(boardIdOf(table.options.get(i)))) {
                return i;
            }
        }
        return -1;
    }

    /** Node-neutral typed block-entity lookup: the 2-arg overload exists only on 1.21.10+, where it also
     *  fails the test for a wrong type; 1.21.1's single-arg form returns a raw BlockEntity to cast. */
    private static <T extends BlockEntity> T blockEntity(GameTestHelper helper, BlockPos pos, Class<T> type) {
        //? if >=1.21.10 {
        return helper.getBlockEntity(pos, type);
        //?} else {
        /*return type.cast(helper.getBlockEntity(pos));*/
        //?}
    }
}
