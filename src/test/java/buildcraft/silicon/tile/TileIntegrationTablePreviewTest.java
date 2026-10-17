/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.silicon.tile;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.robotics.BCRoboticsRecipes;
import buildcraft.robotics.RobotIntegrationRecipe;
import buildcraft.robotics.boards.BoardRobotPickerNBT;
import buildcraft.robotics.item.ItemRedstoneBoard;
import buildcraft.robotics.item.ItemRobot;
import buildcraft.silicon.BCSiliconBlocks;

/**
 * What the Integration Table's GUI reads: {@code getOutput()} fills the preview slot and {@code getTarget()} drives
 * the progress bar and the power ledger.
 *
 * <p>A client never ticks this tile ({@code serverTick} is the only place the matched recipe was ever resolved), so on
 * a client both used to read as empty / zero and the preview slot showed nothing. A freshly built tile that was never
 * ticked and has no level is the same situation: nothing has resolved the recipe for it, only the inventories (which
 * the menu keeps in step on a client) say what it holds.
 */
public class TileIntegrationTablePreviewTest extends VanillaSetupBaseTester {

    @BeforeAll
    public static void registerRecipes() {
        BCRoboticsRecipes.ensureInitialized();
    }

    private static TileIntegrationTable newTable() {
        return new TileIntegrationTable(BlockPos.ZERO, BCSiliconBlocks.INTEGRATION_TABLE.get().defaultBlockState());
    }

    private static TileIntegrationTable tableWithRobotAndBoard() {
        TileIntegrationTable table = newTable();
        table.invTarget.setStackInSlot(0, ItemRobot.createRobotStack(null, 500L));
        table.invToIntegrate.setStackInSlot(0, ItemRedstoneBoard.createStack(BoardRobotPickerNBT.INSTANCE));
        return table;
    }

    @Test
    public void aTableNobodyTickedStillPreviewsItsCraft() {
        TileIntegrationTable table = tableWithRobotAndBoard();

        ItemStack preview = table.getOutput();

        Assertions.assertFalse(preview.isEmpty(), "the preview slot must show the robot the table would produce");
        Assertions.assertEquals(BoardRobotPickerNBT.ID, ItemRobot.getBoardId(preview),
                "the preview is the robot carrying the offered board");
        Assertions.assertEquals(RobotIntegrationRecipe.REQUIRED_MICROJOULES, table.getTarget(),
                "the progress bar and the ledger need the craft's cost");
    }

    @Test
    public void thePreviewFollowsTheInventory() {
        TileIntegrationTable table = tableWithRobotAndBoard();
        Assertions.assertFalse(table.getOutput().isEmpty(), "precondition: a craft is on the cards");

        table.invToIntegrate.setStackInSlot(0, ItemStack.EMPTY);
        Assertions.assertTrue(table.getOutput().isEmpty(), "no board in the ring: nothing to preview");
        Assertions.assertEquals(0L, table.getTarget(), "...and nothing to pay for");

        table.invToIntegrate.setStackInSlot(0, ItemRedstoneBoard.createStack(BoardRobotPickerNBT.INSTANCE));
        Assertions.assertFalse(table.getOutput().isEmpty(), "the board is back: the preview returns");

        table.invTarget.setStackInSlot(0, ItemStack.EMPTY);
        Assertions.assertTrue(table.getOutput().isEmpty(), "no robot in the centre: nothing to preview");
        Assertions.assertEquals(0L, table.getTarget(), "...and nothing to pay for");
    }

    @Test
    public void aFullResultSlotShowsThePreviewButPausesTheCost() {
        TileIntegrationTable table = tableWithRobotAndBoard();
        table.invResult.setStackInSlot(0, new ItemStack(Items.PAPER));

        Assertions.assertFalse(table.getOutput().isEmpty(), "the preview does not depend on the output slot");
        Assertions.assertEquals(0L, table.getTarget(),
                "an occupied result slot pauses the craft, so the cost reads as zero");
    }
}
