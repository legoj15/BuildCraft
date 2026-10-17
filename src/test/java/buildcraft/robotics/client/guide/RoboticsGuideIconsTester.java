/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.client.guide;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.api.boards.RedstoneBoardRegistry;
import buildcraft.api.registry.IScriptableRegistry.OptionallyDisabled;
import buildcraft.lib.client.guide.entry.PageEntry;
import buildcraft.lib.client.guide.entry.PageEntryExternal;
import buildcraft.lib.gui.GuiStack;
import buildcraft.lib.gui.ISimpleDrawable;
import buildcraft.robotics.BCRoboticsItems;
import buildcraft.robotics.boards.BoardRobotEmptyNBT;
import buildcraft.robotics.boards.BoardRobotMinerNBT;
import buildcraft.robotics.item.ItemRedstoneBoard;

/** The guide's Robots &gt; Boards pages are {@code external} entries (every board is the one Redstone Board item with a
 *  program in its data, which an item entry cannot name), and a text-only line stood out in a tree of icons. Robotics
 *  registers the {@code board} icon kind so each of those entries can draw the programmed board itself. */
public class RoboticsGuideIconsTester extends VanillaSetupBaseTester {

    @BeforeAll
    public static void registerKind() {
        RoboticsGuideIcons.register();
    }

    private static ISimpleDrawable iconOf(String boardId) {
        JsonObject json = JsonParser.parseString(
                "{\"title_raw\": \"Board\", \"icon\": {\"board\": \"" + boardId + "\"}}").getAsJsonObject();
        OptionallyDisabled<PageEntry<String>> entry = PageEntryExternal.INSTANCE.deserialize(
                Identifier.parse("buildcraftunofficial:board/test"), json, null);
        Assertions.assertTrue(entry.isPresent(), "a board icon is a valid external entry");
        return entry.get().createDrawable();
    }

    @Test
    public void aBoardEntryDrawsTheProgrammedBoard() {
        ISimpleDrawable icon = iconOf(BoardRobotMinerNBT.ID);

        Assertions.assertNotNull(icon, "the Miner board page has an icon");
        ItemStack stack = Assertions.assertInstanceOf(GuiStack.class, icon).getStack();
        Assertions.assertEquals(BCRoboticsItems.REDSTONE_BOARD.get(), stack.getItem());
        Assertions.assertEquals(BoardRobotMinerNBT.ID, ItemRedstoneBoard.getBoardNBT(stack).getID(),
                "it is the Miner program, not the blank board every board falls back to");
        Assertions.assertTrue(ItemStack.matches(ItemRedstoneBoard.createStack(BoardRobotMinerNBT.INSTANCE), stack),
                "exactly the stack the Programming Table makes");
    }

    @Test
    public void theEmptyBoardPageDrawsTheBlankBoard() {
        ISimpleDrawable icon = iconOf(BoardRobotEmptyNBT.ID);

        Assertions.assertNotNull(icon);
        ItemStack stack = Assertions.assertInstanceOf(GuiStack.class, icon).getStack();
        Assertions.assertTrue(ItemStack.matches(
                ItemRedstoneBoard.createStack(RedstoneBoardRegistry.instance.getEmptyRobotBoard()), stack));
    }

    @Test
    public void anUnknownBoardIdDrawsNothingInsteadOfTheBlankBoardItFallsBackTo() {
        // RedstoneBoardRegistry resolves an unknown id to the empty board, so a typo would quietly show the wrong icon.
        Assertions.assertNull(iconOf("buildcraftunofficial:boardRobotTypo"));
    }
}
