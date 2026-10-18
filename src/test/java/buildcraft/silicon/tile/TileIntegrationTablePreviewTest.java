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
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.lib.test.ValueIoTestUtil;
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

    // ---- The client resolves the preview once per inventory change, not once per frame ----
    //
    // The preview slot asks getOutput() every frame (twice: getItem and hasItem), and the progress bar and the power
    // ledger ask getTarget(). Resolving means a registry-wide recipe match plus an extract simulation, so the result
    // is kept until the centre stack or the ring changes.

    @Test
    public void everyFrameAfterTheFirstReadsTheCachedPreview() {
        TileIntegrationTable table = tableWithRobotAndBoard();

        for (int frame = 0; frame < 30; frame++) {
            Assertions.assertFalse(table.getOutput().isEmpty(), "frame " + frame + ": the preview is there");
            Assertions.assertEquals(RobotIntegrationRecipe.REQUIRED_MICROJOULES, table.getTarget(),
                    "frame " + frame + ": and so is the cost");
        }

        Assertions.assertEquals(1, table.previewResolves(), "thirty frames of reads resolve the recipe once");
    }

    @Test
    public void anEmptyTableIsCachedToo() {
        // The expensive case: with nothing to match, every read used to walk the whole recipe registry.
        TileIntegrationTable table = newTable();

        for (int frame = 0; frame < 30; frame++) {
            Assertions.assertTrue(table.getOutput().isEmpty());
            Assertions.assertEquals(0L, table.getTarget());
        }

        Assertions.assertEquals(1, table.previewResolves(), "an empty table resolves once, not once per frame");
    }

    @Test
    public void aChangedRingResolvesAgainAndOnlyOnce() {
        TileIntegrationTable table = tableWithRobotAndBoard();
        Assertions.assertFalse(table.getOutput().isEmpty());
        Assertions.assertEquals(1, table.previewResolves());

        table.invToIntegrate.setStackInSlot(0, ItemStack.EMPTY);
        for (int frame = 0; frame < 5; frame++) {
            Assertions.assertTrue(table.getOutput().isEmpty(), "the board is gone, so is the preview");
        }
        Assertions.assertEquals(2, table.previewResolves(), "one more resolve for the change, none for the frames after");

        table.invToIntegrate.setStackInSlot(3, ItemRedstoneBoard.createStack(BoardRobotPickerNBT.INSTANCE));
        for (int frame = 0; frame < 5; frame++) {
            Assertions.assertFalse(table.getOutput().isEmpty(), "a board in another ring slot brings it back");
        }
        Assertions.assertEquals(3, table.previewResolves());
    }

    @Test
    public void aChangedCentreResolvesAgain() {
        TileIntegrationTable table = tableWithRobotAndBoard();
        Assertions.assertFalse(table.getOutput().isEmpty());

        table.invTarget.setStackInSlot(0, ItemStack.EMPTY);
        Assertions.assertTrue(table.getOutput().isEmpty(), "no robot, no preview");
        Assertions.assertEquals(0L, table.getTarget());
        Assertions.assertEquals(2, table.previewResolves());
    }

    @Test
    public void aStackChangedInPlaceIsSeen() {
        // The menu syncs a changed slot by replacing the stack, but nothing stops a stack being edited where it
        // sits (a name tag applied to the robot in the centre): the cache must not hand back the old preview.
        TileIntegrationTable table = tableWithRobotAndBoard();
        Assertions.assertNull(table.getOutput().get(DataComponents.CUSTOM_NAME), "precondition: an unnamed robot");

        table.invTarget.getStackInSlot(0).set(DataComponents.CUSTOM_NAME, Component.literal("Bob"));

        ItemStack preview = table.getOutput();
        Assertions.assertNotNull(preview.get(DataComponents.CUSTOM_NAME), "the preview robot keeps the new name");
        Assertions.assertEquals("Bob", preview.get(DataComponents.CUSTOM_NAME).getString());
        Assertions.assertEquals(2, table.previewResolves());
    }

    @Test
    public void aWriteStraightIntoTheStackListIsSeen() {
        // No setStackInSlot, so no change callback: the cache compares contents rather than waiting to be told.
        TileIntegrationTable table = tableWithRobotAndBoard();
        Assertions.assertFalse(table.getOutput().isEmpty());

        table.invToIntegrate.stacks.set(0, ItemStack.EMPTY);

        Assertions.assertTrue(table.getOutput().isEmpty(), "the preview follows the ring however it was changed");
        Assertions.assertEquals(2, table.previewResolves());
    }

    @Test
    public void theResultSlotDoesNotInvalidateThePreview() {
        // The cost reads the result slot fresh on every call (a full slot pauses the craft); it is not an input.
        TileIntegrationTable table = tableWithRobotAndBoard();
        Assertions.assertEquals(RobotIntegrationRecipe.REQUIRED_MICROJOULES, table.getTarget());

        table.invResult.setStackInSlot(0, new ItemStack(Items.PAPER));
        Assertions.assertEquals(0L, table.getTarget(), "a full result slot pauses the craft");
        table.invResult.setStackInSlot(0, ItemStack.EMPTY);
        Assertions.assertEquals(RobotIntegrationRecipe.REQUIRED_MICROJOULES, table.getTarget(), "and emptying it resumes");

        Assertions.assertEquals(1, table.previewResolves(), "the result slot is not part of the cache key");
    }

    @Test
    public void theCachedPreviewIsNotShared() {
        // Whoever draws the slot owns the stack it is handed; changing it must not change what the next frame sees.
        TileIntegrationTable table = tableWithRobotAndBoard();

        table.getOutput().setCount(7);
        table.getOutput().set(DataComponents.CUSTOM_NAME, Component.literal("scribbled"));

        ItemStack again = table.getOutput();
        Assertions.assertEquals(1, again.getCount());
        Assertions.assertNull(again.get(DataComponents.CUSTOM_NAME));
    }

    @Test
    public void aLoadResetsThePreviewTooNotJustTheRecipe() {
        // readData drops the matched recipe (it is re-found from the inventories). The inventories read back the
        // same, so a cache that survived the load would hold a preview while the recipe behind the cost was gone.
        TileIntegrationTable table = tableWithRobotAndBoard();
        Assertions.assertEquals(RobotIntegrationRecipe.REQUIRED_MICROJOULES, table.getTarget());
        CompoundTag saved = ValueIoTestUtil.write(table::writeData);

        table.readData(ValueIoTestUtil.inputOf(saved));

        Assertions.assertFalse(table.getOutput().isEmpty(), "still previews what the saved inventories craft");
        Assertions.assertEquals(RobotIntegrationRecipe.REQUIRED_MICROJOULES, table.getTarget(),
                "and still knows what it costs");
    }
}
