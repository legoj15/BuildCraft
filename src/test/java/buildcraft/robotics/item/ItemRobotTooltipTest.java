/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.item;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.api.boards.RedstoneBoardRegistry;
import buildcraft.api.boards.RedstoneBoardRobotNBT;
import buildcraft.robotics.boards.BoardRobotPickerNBT;

/** The robot item's hover text. 7.1.x showed the BOARD's own description above the charge readout, so a
 *  shelf of robots could be told apart at a glance; the port only ever showed the charge, leaving every
 *  programmed robot looking identical. The board item next door has always shown it.
 *
 *  <p>7.1.x suppressed the whole tooltip for a blank robot — that is kept verbatim now: a blank
 *  chassis with no program is not a functional robot, so it gets no description and no charge line
 *  either (the port's original blank-charge line was a divergence; the 2026-09-03 gameplay audit's
 *  user decision restored 7.1.x's suppression, shared with the icon via {@code hasEmptyBoard}). */
public class ItemRobotTooltipTest extends VanillaSetupBaseTester {

    private static List<String> asStrings(List<Component> lines) {
        List<String> out = new ArrayList<>();
        for (Component line : lines) {
            out.add(line.getString());
        }
        return out;
    }

    /** What the board itself would contribute — computed from the board, so the assertion cannot drift
     *  if the wording changes. */
    private static List<String> boardLines(RedstoneBoardRobotNBT board, ItemStack stack) {
        List<String> lines = new ArrayList<>();
        board.addInformation(stack, null, lines, false);
        return lines;
    }

    @Test
    public void aProgrammedRobotNamesItsBoard() {
        ItemStack stack = ItemRobot.createRobotStack(BoardRobotPickerNBT.INSTANCE.getID(), 0);

        List<String> tooltip = asStrings(ItemRobot.tooltipLines(stack, false));
        List<String> expected = boardLines(BoardRobotPickerNBT.INSTANCE, stack);

        Assertions.assertFalse(expected.isEmpty(),
                "precondition: the picker board describes itself");
        for (String line : expected) {
            Assertions.assertTrue(tooltip.contains(line),
                    "the robot's tooltip must carry the board's own description line \"" + line
                            + "\" — otherwise every programmed robot looks identical on the shelf. Got: "
                            + tooltip);
        }
        Assertions.assertEquals(expected.size() + 1, tooltip.size(),
                "the board's lines plus the port's charge line, and nothing else: " + tooltip);
    }

    @Test
    public void theChargeLineIsStillLast() {
        ItemStack full = ItemRobot.createRobotStack(BoardRobotPickerNBT.INSTANCE.getID(),
                buildcraft.api.robots.EntityRobotBase.MAX_POWER);
        ItemStack flat = ItemRobot.createRobotStack(BoardRobotPickerNBT.INSTANCE.getID(), 0);

        List<Component> fullLines = ItemRobot.tooltipLines(full, false);
        List<Component> flatLines = ItemRobot.tooltipLines(flat, false);

        Assertions.assertNotEquals(
                fullLines.get(fullLines.size() - 1).getString(),
                flatLines.get(flatLines.size() - 1).getString(),
                "the last line is the charge readout, and a full robot must not read like a flat one");
    }

    @Test
    public void aBlankRobotShowsNothing() {
        RedstoneBoardRobotNBT empty = RedstoneBoardRegistry.instance.getEmptyRobotBoard();
        ItemStack stack = ItemRobot.createRobotStack(empty.getID(), 0);

        List<String> tooltip = asStrings(ItemRobot.tooltipLines(stack, false));

        Assertions.assertTrue(tooltip.isEmpty(),
                "7.1.x suppressed the whole tooltip for a blank robot — no description and no charge "
                        + "line. Got: " + tooltip);
    }

    @Test
    public void aBareStackDoesNotThrow() {
        ItemStack bare = new ItemStack(buildcraft.robotics.BCRoboticsItems.ROBOT.get());

        Assertions.assertDoesNotThrow(() -> ItemRobot.tooltipLines(bare, true),
                "a /give-n stack with no data blob must still produce a tooltip");
    }
}
