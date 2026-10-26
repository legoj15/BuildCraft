/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.robotics.container;

import java.util.ArrayList;
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
import buildcraft.lib.compat.viewer.TransferResult;
import buildcraft.lib.misc.EntityUtil;
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
 *  When the only thing standing between the player and the transfer is that they hold programmed ones and no blank,
 *  the answer is {@link TransferResult#ONLY_PROGRAMMED} rather than a bare {@link TransferResult#MISSING}, so the
 *  tooltip can say why.
 *
 *  <p>Every request is checked against the live state — the player's real inventory and the table's real slots —
 *  and is all-or-nothing: nothing is debited from the player unless every item the request needs can be inserted,
 *  and nothing is moved at all unless everything that has to leave the table has somewhere to go. Contents of the
 *  table's input slots that already fit the request stay (a board the recipe matches in the Programming Table, any
 *  robot in the Integration Table's centre, a ring holding exactly one board of the requested program); anything
 *  else in those slots is moved back into the player's inventory to make way, as JEI does for a crafting grid. The
 *  result slot is never touched. If the inventory has no room for all of it, counting the slots the transfer
 *  itself frees by using up the last of a stack, the answer is {@link TransferResult#BUSY} and nothing moves; the
 *  answer is the same for the validate-only call. A craft under way stops when its inputs are cleared; the table
 *  keeps the power it had gathered, as it does whenever a recipe pauses. */
public final class RoboticsTableTransfer {
    private RoboticsTableTransfer() {}

    // ── Programming Table ───────────────────────────────────────────────────

    /** Gets {@code table} ready to program {@code boardId} through the recipe {@code recipeId}: moves one blank
     *  board in from the player (a board already in the slot is reused, whatever its program, since the table
     *  reprograms any board it can run the recipe on; anything else in the slot goes back to the player's
     *  inventory first), resolves the table's recipe and option grid on the spot, and selects that board's grid
     *  cell. The resolve is the point of doing this server-side: the table otherwise finds its recipe only in the
     *  next tick, leaving the grid unresolved and the selection clamped to "none".
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
        boolean keep = !slotted.isEmpty() && isMatchedBy(recipe, slotted);
        int source = -1;
        if (!keep) {
            source = findBlankBoard(inv, recipe);
            if (source < 0) {
                return holdsProgrammedBoard(inv, recipe) ? TransferResult.ONLY_PROGRAMMED : TransferResult.MISSING;
            }
        }
        List<ItemStack> leaving = new ArrayList<>();
        if (!keep && !slotted.isEmpty()) {
            leaving.add(slotted.copy());
        }
        if (!roomFor(inv, leaving, source)) {
            return TransferResult.BUSY;
        }

        if (!doTransfer) {
            return TransferResult.OK;
        }
        if (!keep) {
            ItemStack one = inv.getItem(source).copyWithCount(1);
            if (!slotted.isEmpty()) {
                table.invBoard.setStackInSlot(0, ItemStack.EMPTY);
            }
            if (!table.invBoard.insertItem(0, one, false).isEmpty()) {
                // Not reachable while isMatchedBy says the table accepts this board; refuse at no cost all the same.
                table.invBoard.setStackInSlot(0, slotted);
                return TransferResult.INVALID;
            }
            debit(inv, source);
            giveBack(inv, leaving);
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

    /** True if the player holds a programmed board that the table would run through {@code recipe}: one that would
     *  have served had it been blank. */
    private static boolean holdsProgrammedBoard(Inventory inv, IProgrammingRecipe recipe) {
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.getItem() instanceof ItemRedstoneBoard && !isBlankBoard(stack) && isMatchedBy(recipe, stack)) {
                return true;
            }
        }
        return false;
    }

    /** True if the table, given {@code stack} in its board slot, would run {@code recipe} (not merely any recipe). */
    private static boolean isMatchedBy(IProgrammingRecipe recipe, ItemStack stack) {
        IProgrammingRecipe match = TileProgrammingTable.matchRecipe(stack);
        return match != null && match.getId().equals(recipe.getId());
    }

    // ── Integration Table ───────────────────────────────────────────────────

    /** Fills {@code table} for programming a robot with {@code boardId}: one blank robot into the centre slot and
     *  one board of that program into a ring slot. The centre may already hold a robot (any, kept as is) and the
     *  ring may already hold exactly that one board (kept where it is); every other stack in the centre or the ring
     *  — a stranger in the centre, any other stack in the ring, which would also stop the table crafting — is moved
     *  back into the player's inventory first. The table recognises the recipe on its next tick, as it does for
     *  hand-placed items.
     *
     *  @param boardId The program id, as on the JEI entry's board.
     *  @param doTransfer false to only validate. */
    public static TransferResult integrate(Inventory inv, TileIntegrationTable table, String boardId,
            boolean doTransfer) {
        if (!isIntegrableProgram(boardId)) {
            return TransferResult.INVALID;
        }

        ItemStack centre = table.invTarget.getStackInSlot(0);
        boolean keepCentre = centre.getItem() instanceof ItemRobot;
        // The table only crafts from a ring holding exactly the one board it consumes: the first stack that is
        // exactly that stays, every other stack in the ring leaves.
        int keptRing = -1;
        for (int slot = 0; slot < table.invToIntegrate.getSlots() && keptRing < 0; slot++) {
            ItemStack ring = table.invToIntegrate.getStackInSlot(slot);
            if (ring.getCount() == 1 && isBoardOfProgram(ring, boardId)) {
                keptRing = slot;
            }
        }
        boolean needRobot = !keepCentre;
        boolean needBoard = keptRing < 0;

        int robotSource = needRobot ? findBlankRobot(inv) : -1;
        int boardSource = needBoard ? findBoard(inv, boardId) : -1;
        // The board first: ONLY_PROGRAMMED claims a blank robot is all that is missing.
        if (needBoard && boardSource < 0) {
            return TransferResult.MISSING;
        }
        if (needRobot && robotSource < 0) {
            return holdsProgrammedRobot(inv) ? TransferResult.ONLY_PROGRAMMED : TransferResult.MISSING;
        }

        List<ItemStack> leaving = new ArrayList<>();
        if (!keepCentre && !centre.isEmpty()) {
            leaving.add(centre.copy());
        }
        for (int slot = 0; slot < table.invToIntegrate.getSlots(); slot++) {
            ItemStack ring = table.invToIntegrate.getStackInSlot(slot);
            if (slot != keptRing && !ring.isEmpty()) {
                leaving.add(ring.copy());
            }
        }
        if (!roomFor(inv, leaving, robotSource, boardSource)) {
            return TransferResult.BUSY;
        }

        ItemStack robot = needRobot ? inv.getItem(robotSource).copyWithCount(1) : ItemStack.EMPTY;
        ItemStack board = needBoard ? inv.getItem(boardSource).copyWithCount(1) : ItemStack.EMPTY;
        // Both must be acceptable once the table is cleared, by the table's own rules. Not reachable for a blank
        // robot and an integrable program; asked here, before anything moves, so that it could never cost the player.
        if ((needRobot && !table.invTarget.canSet(0, robot))
                || (needBoard && !table.canEmptiedRingTake(needRobot ? robot : centre, board))) {
            return TransferResult.INVALID;
        }
        if (!doTransfer) {
            return TransferResult.OK;
        }

        // Clear the way, keeping hold of what was there so that a refusal below can put it all back.
        List<ItemStack> ringBefore = new ArrayList<>();
        for (int slot = 0; slot < table.invToIntegrate.getSlots(); slot++) {
            ringBefore.add(table.invToIntegrate.getStackInSlot(slot));
        }
        if (!keepCentre && !centre.isEmpty()) {
            table.invTarget.setStackInSlot(0, ItemStack.EMPTY);
        }
        for (int slot = 0; slot < ringBefore.size(); slot++) {
            if (slot != keptRing && !ringBefore.get(slot).isEmpty()) {
                table.invToIntegrate.setStackInSlot(slot, ItemStack.EMPTY);
            }
        }
        // Both must go in before either is debited, so a refusal cannot leave the table half-filled. The robot goes
        // first: the ring only takes a board with a robot in the centre.
        boolean filled = (!needRobot || table.invTarget.insertItem(0, robot, false).isEmpty())
                && (!needBoard || insertIntoFirstFree(table.invToIntegrate, board));
        if (!filled) {
            table.invTarget.setStackInSlot(0, centre);
            for (int slot = 0; slot < ringBefore.size(); slot++) {
                table.invToIntegrate.setStackInSlot(slot, ringBefore.get(slot));
            }
            return TransferResult.INVALID;
        }
        if (needRobot) {
            debit(inv, robotSource);
        }
        if (needBoard) {
            debit(inv, boardSource);
        }
        giveBack(inv, leaving);
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

    /** True if the player holds a programmed robot: one that would have served as the chassis had it been blank. */
    private static boolean holdsProgrammedRobot(Inventory inv) {
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.getItem() instanceof ItemRobot && !ItemRobot.hasEmptyBoard(stack)) {
                return true;
            }
        }
        return false;
    }

    private static int findBoard(Inventory inv, String boardId) {
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            if (isBoardOfProgram(inv.getItem(i), boardId)) {
                return i;
            }
        }
        return -1;
    }

    /** Puts {@code stack} in the first ring slot that takes it; false if none does. */
    private static boolean insertIntoFirstFree(ItemHandlerSimple handler, ItemStack stack) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (handler.getStackInSlot(slot).isEmpty() && handler.insertItem(slot, stack, true).isEmpty()) {
                handler.insertItem(slot, stack, false);
                return true;
            }
        }
        return false;
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

    /** Whether the player's 36 main slots could take every stack of {@code leaving}, after one item has been taken
     *  from each slot in {@code debits} (negative entries are no slot). The same order as {@link Inventory#add}: a
     *  stack tops up the partial stacks of its item first, then fills empty slots, and a slot the debits emptied is
     *  one of those. Simulated on copies, so nothing is touched. It leaves out the off hand, which {@code add} would
     *  also top up, so it never promises room that {@code add} lacks. */
    private static boolean roomFor(Inventory inv, List<ItemStack> leaving, int... debits) {
        if (leaving.isEmpty()) {
            return true;
        }
        ItemStack[] slots = new ItemStack[Inventory.INVENTORY_SIZE];
        for (int i = 0; i < slots.length; i++) {
            slots[i] = inv.getItem(i).copy();
        }
        for (int debit : debits) {
            if (debit >= 0) {
                slots[debit].shrink(1);
            }
        }
        for (ItemStack stack : leaving) {
            int remaining = stack.getCount();
            for (ItemStack slot : slots) {
                if (remaining > 0 && !slot.isEmpty() && slot.isStackable()
                        && ItemStack.isSameItemSameComponents(slot, stack)) {
                    int moved = Math.min(remaining, slot.getMaxStackSize() - slot.getCount());
                    if (moved > 0) {
                        slot.grow(moved);
                        remaining -= moved;
                    }
                }
            }
            for (int i = 0; i < slots.length && remaining > 0; i++) {
                if (slots[i].isEmpty()) {
                    int placed = Math.min(remaining, stack.getMaxStackSize());
                    slots[i] = stack.copyWithCount(placed);
                    remaining -= placed;
                }
            }
            if (remaining > 0) {
                return false;
            }
        }
        return true;
    }

    /** Puts what the transfer cleared out of the table into the player's inventory. {@link #roomFor} has promised
     *  the room; anything {@code add} still cannot place (it cannot happen) is dropped at the player's feet rather
     *  than lost. */
    private static void giveBack(Inventory inv, List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            ItemStack rest = stack.copy();
            inv.add(rest);
            if (!rest.isEmpty()) {
                EntityUtil.dropOverflow(inv.player, rest);
            }
        }
        inv.setChanged();
    }
}
