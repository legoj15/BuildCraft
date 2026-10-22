/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.robotics.container;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import buildcraft.api.boards.RedstoneBoardNBT;
import buildcraft.api.boards.RedstoneBoardRegistry;
import buildcraft.api.boards.RedstoneBoardRobotNBT;
import buildcraft.api.recipes.IProgrammingRecipe;
import buildcraft.api.recipes.IntegrationRecipe;
import buildcraft.lib.compat.jei.TransferResult;
import buildcraft.lib.recipe.IntegrationRecipeRegistry;
import buildcraft.lib.recipe.ProgrammingRecipeRegistry;
import buildcraft.lib.tile.item.ItemHandlerSimple;

import buildcraft.robotics.BCRoboticsItems;
import buildcraft.robotics.boards.BoardRobotEmptyNBT;
import buildcraft.robotics.item.ItemRedstoneBoard;
import buildcraft.robotics.item.ItemRobot;
import buildcraft.silicon.tile.TileIntegrationTable;
import buildcraft.silicon.tile.TileProgrammingTable;

/** The server-side core of the JEI "+" transfer into the Programming and Integration Tables, kept free of JEI
 *  classes so it is testable headless and so the JEI handlers can run the very same checks client-side as their
 *  advisory "can the button be pressed" answer ({@code doTransfer = false}: validate, change nothing).
 *
 *  <p>Both tables work from <em>blank</em> inputs, because that is what the JEI entries show: a board nobody has
 *  programmed (Programming) and a robot nobody has programmed (Integration). A blank is recognised by its program,
 *  not by its exact stack: a survival-crafted board or robot is a bare stack while the ones the table helpers build
 *  carry a {@code CUSTOM_DATA} blob, and an exact item-and-components match would see only one of the two. A
 *  <em>programmed</em> board or robot is never taken: the player may value it, and the JEI entry never asked for it.
 *
 *  <p>Every request is checked against the live state — the player's real inventory and the table's real slots —
 *  and is all-or-nothing: nothing is debited from the player unless every item the request needs can be
 *  inserted. Nothing is cleared out of a table to make room; a slot holding something unexpected answers
 *  {@link TransferResult#BUSY} instead. */
public final class RoboticsTableTransfer {
    private RoboticsTableTransfer() {}

    // ── Programming Table ───────────────────────────────────────────────────

    /** Gets {@code table} ready to program {@code boardId} through the recipe {@code recipeId}: moves one blank
     *  board in from the player (an empty board slot only — a board already there is reused, whatever its program,
     *  since the table reprograms any board), resolves the table's recipe and option grid on the spot, and selects
     *  that board's grid cell. The resolve is the point of doing this server-side: the table otherwise finds its
     *  recipe only in the next tick, leaving the grid unresolved and the selection clamped to "none".
     *
     *  @param boardId The id of the board to be programmed, i.e. the option to select.
     *  @param doTransfer false to only validate. */
    public static TransferResult program(Inventory inv, TileProgrammingTable table, String recipeId, String boardId,
            boolean doTransfer) {
        IProgrammingRecipe recipe = ProgrammingRecipeRegistry.INSTANCE.getRecipe(recipeId);
        if (recipe == null) {
            return TransferResult.INVALID;
        }
        int option = optionIndex(recipe, boardId);
        if (option < 0) {
            return TransferResult.INVALID;
        }

        ItemStack slotted = table.invBoard.getStackInSlot(0);
        int source = -1;
        if (slotted.isEmpty()) {
            source = findBlankBoard(inv, recipe);
            if (source < 0) {
                return TransferResult.MISSING;
            }
        } else if (!isMatchedBy(recipe, slotted)) {
            return TransferResult.BUSY;
        }

        if (!doTransfer) {
            return TransferResult.OK;
        }
        if (source >= 0) {
            ItemStack one = inv.getItem(source).copyWithCount(1);
            if (!table.invBoard.insertItem(0, one, false).isEmpty()) {
                return TransferResult.BUSY; // the slot refused the board: nothing was debited
            }
            debit(inv, source);
        }
        table.findRecipe();
        table.selectOption(option);
        return TransferResult.OK;
    }

    /** The index of the option that is the board {@code boardId}, over all the recipe's options (the GUI shows them
     *  a page at a time and turns to the selection's page), or -1. Only board options are matched: the table's one
     *  recipe programs boards. */
    private static int optionIndex(IProgrammingRecipe recipe, String boardId) {
        List<ItemStack> options = recipe.getOptions(TileProgrammingTable.OPTION_COLS, TileProgrammingTable.OPTION_ROWS);
        for (int i = 0; i < options.size(); i++) {
            ItemStack option = options.get(i);
            if (option != null && option.getItem() instanceof ItemRedstoneBoard && boardId.equals(boardIdOf(option))) {
                return i;
            }
        }
        return -1;
    }

    /** The inventory slot of a blank board that the table would run through {@code recipe}, or -1. */
    private static int findBlankBoard(Inventory inv, IProgrammingRecipe recipe) {
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            ItemStack stack = inv.getItem(i);
            if (isBlankBoard(stack) && isMatchedBy(recipe, stack)) {
                return i;
            }
        }
        return -1;
    }

    /** True if the table, given {@code stack} in its board slot, would run {@code recipe} (not merely any recipe). */
    private static boolean isMatchedBy(IProgrammingRecipe recipe, ItemStack stack) {
        IProgrammingRecipe match = TileProgrammingTable.matchRecipe(stack);
        return match != null && match.getId().equals(recipe.getId());
    }

    // ── Integration Table ───────────────────────────────────────────────────

    /** Fills {@code table} for programming a robot with {@code boardId}: one blank robot into the centre slot and
     *  one board of that program into a ring slot. The centre may already hold a robot (any, kept as is) and the
     *  ring may already hold exactly that board; anything else in either — a stranger in the centre, or any other
     *  stack in the ring, which would also stop the table crafting — answers {@link TransferResult#BUSY}. The table
     *  recognises the recipe on its next tick, as it does for hand-placed items.
     *
     *  @param boardId The program id, as on the JEI entry's board.
     *  @param doTransfer false to only validate. */
    public static TransferResult integrate(Inventory inv, TileIntegrationTable table, String boardId,
            boolean doTransfer) {
        if (!isIntegrableProgram(boardId)) {
            return TransferResult.INVALID;
        }

        ItemStack centre = table.invTarget.getStackInSlot(0);
        boolean needRobot = centre.isEmpty();
        if (!needRobot && !(centre.getItem() instanceof ItemRobot)) {
            return TransferResult.BUSY;
        }
        boolean needBoard = true;
        for (ItemStack ring : table.invToIntegrate.stacks) {
            if (ring.isEmpty()) {
                continue;
            }
            // The table only crafts from a ring holding exactly the one board it consumes.
            if (!needBoard || ring.getCount() != 1 || !isBoardOfProgram(ring, boardId)) {
                return TransferResult.BUSY;
            }
            needBoard = false;
        }

        int robotSource = needRobot ? findBlankRobot(inv) : -1;
        int boardSource = needBoard ? findBoard(inv, boardId) : -1;
        if ((needRobot && robotSource < 0) || (needBoard && boardSource < 0)) {
            return TransferResult.MISSING;
        }
        if (!doTransfer) {
            return TransferResult.OK;
        }

        ItemStack robot = needRobot ? inv.getItem(robotSource).copyWithCount(1) : ItemStack.EMPTY;
        ItemStack board = needBoard ? inv.getItem(boardSource).copyWithCount(1) : ItemStack.EMPTY;
        // Both must fit before either is debited, so a refusal cannot leave the table half-filled.
        if (needRobot && !table.invTarget.insertItem(0, robot, true).isEmpty()) {
            return TransferResult.BUSY;
        }
        // The ring only takes a board with a robot in the centre, which a robot still in the player's inventory is
        // not yet, so ask about the robot the transfer is about to put there.
        if (needBoard && !table.canRingTake(needRobot ? robot : centre, 0, board)) {
            return TransferResult.BUSY;
        }
        if (needRobot) {
            table.invTarget.insertItem(0, robot, false);
            debit(inv, robotSource);
        }
        if (needBoard) {
            insertIntoFirstFree(table.invToIntegrate, board);
            debit(inv, boardSource);
        }
        return TransferResult.OK;
    }

    /** True if {@code boardId} names a registered robot program that the Integration Table turns a blank robot
     *  into — the blank board is no program, and an unknown id is stale or hand-written. */
    private static boolean isIntegrableProgram(String boardId) {
        RedstoneBoardRegistry registry = RedstoneBoardRegistry.instance;
        if (registry == null || BoardRobotEmptyNBT.ID.equals(boardId)) {
            return false;
        }
        RedstoneBoardNBT<?> board = registry.getRedstoneBoard(boardId);
        if (!(board instanceof RedstoneBoardRobotNBT) || !boardId.equals(board.getID())) {
            return false;
        }
        NonNullList<ItemStack> ring = NonNullList.withSize(8, ItemStack.EMPTY);
        ring.set(0, ItemRedstoneBoard.createStack(board));
        IntegrationRecipe recipe = IntegrationRecipeRegistry.INSTANCE
                .getRecipeFor(new ItemStack(BCRoboticsItems.ROBOT.get()), ring);
        return recipe != null;
    }

    private static int findBlankRobot(Inventory inv) {
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.getItem() instanceof ItemRobot && ItemRobot.hasEmptyBoard(stack)) {
                return i;
            }
        }
        return -1;
    }

    private static int findBoard(Inventory inv, String boardId) {
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            if (isBoardOfProgram(inv.getItem(i), boardId)) {
                return i;
            }
        }
        return -1;
    }

    private static void insertIntoFirstFree(ItemHandlerSimple handler, ItemStack stack) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (handler.getStackInSlot(slot).isEmpty() && handler.insertItem(slot, stack, true).isEmpty()) {
                handler.insertItem(slot, stack, false);
                return;
            }
        }
    }

    // ── Shared ──────────────────────────────────────────────────────────────

    private static boolean isBlankBoard(ItemStack stack) {
        return stack.getItem() instanceof ItemRedstoneBoard && BoardRobotEmptyNBT.ID.equals(boardIdOf(stack));
    }

    private static boolean isBoardOfProgram(ItemStack stack, String boardId) {
        return stack.getItem() instanceof ItemRedstoneBoard && boardId.equals(boardIdOf(stack));
    }

    @Nullable
    private static String boardIdOf(ItemStack board) {
        RedstoneBoardNBT<?> nbt = ItemRedstoneBoard.getBoardNBT(board);
        return nbt == null ? null : nbt.getID();
    }

    /** Takes one item from the inventory slot, clearing it when that was the last. */
    private static void debit(Inventory inv, int slot) {
        ItemStack stack = inv.getItem(slot);
        stack.shrink(1);
        if (stack.isEmpty()) {
            inv.setItem(slot, ItemStack.EMPTY);
        }
        inv.setChanged();
    }
}
