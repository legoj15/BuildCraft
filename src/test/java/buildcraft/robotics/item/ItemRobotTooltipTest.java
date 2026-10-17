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

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
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

    /** The charge line is the tooltip's last line (the empty-board case has none, so callers use a board robot). */
    private static Component chargeLineOf(long energy) {
        List<Component> lines = ItemRobot.tooltipLines(
                ItemRobot.createRobotStack(BoardRobotPickerNBT.INSTANCE.getID(), energy), false);
        return lines.get(lines.size() - 1);
    }

    private static TranslatableContents translatable(Component line) {
        Assertions.assertInstanceOf(TranslatableContents.class, line.getContents(),
                "the charge line must be a translatable Component so each language owns its wording: " + line);
        return (TranslatableContents) line.getContents();
    }

    @Test
    public void aPartialChargeIsOneFormatKeyTakingThePercentage() {
        // The line used to be Component.literal(pct + "% ").append(translatable("tip.charge.charged")): the percentage
        // always came first, whatever the language. One format key lets the translation place the number itself.
        Component line = chargeLineOf(buildcraft.api.robots.EntityRobotBase.MAX_POWER / 2);

        TranslatableContents contents = translatable(line);
        Assertions.assertEquals("tip.charge.percent", contents.getKey());
        Assertions.assertArrayEquals(new Object[] { 50 }, contents.getArgs(),
                "the only argument is the whole-number percentage; the '%' sign is the lang file's");
        Assertions.assertTrue(line.getSiblings().isEmpty(),
                "no glued-on siblings: anything appended around the translation fixes the word order in code");
    }

    @Test
    public void aPartialChargeKeepsItsRampColour() {
        long max = buildcraft.api.robots.EntityRobotBase.MAX_POWER;
        Assertions.assertEquals(Component.literal("").withStyle(ChatFormatting.GREEN).getStyle(),
                chargeLineOf(max * 85 / 100).getStyle(), "85% reads green");
        Assertions.assertEquals(Component.literal("").withStyle(ChatFormatting.YELLOW).getStyle(),
                chargeLineOf(max / 2).getStyle(), "50% reads yellow");
        Assertions.assertEquals(Component.literal("").withStyle(ChatFormatting.DARK_RED).getStyle(),
                chargeLineOf(max / 100).getStyle(), "1% reads dark red");
    }

    @Test
    public void theEmptyAndFullChargeLinesStayTheirOwnKeys() {
        long max = buildcraft.api.robots.EntityRobotBase.MAX_POWER;
        Assertions.assertEquals("tip.charge.fullcharge", translatable(chargeLineOf(max)).getKey());
        Assertions.assertEquals("tip.charge.nocharge", translatable(chargeLineOf(0L)).getKey());
    }

    @Test
    public void theBoardNameLineIsBoldWithoutTheWordBoard() {
        // 1.7.10 parity, verified against a live 1.7.10 client (2026-09-12): the tooltip's first board line
        // was EnumChatFormatting.BOLD + the localized name, and no board name carried a "Board" suffix.
        for (RedstoneBoardRobotNBT board : allRobotBoards()) {
            ItemStack stack = ItemRobot.createRobotStack(board.getID(), 0);
            List<Component> lines = ItemRobot.tooltipLines(stack, false);

            Assertions.assertFalse(lines.isEmpty(), board.getID() + " contributes tooltip lines");
            Assertions.assertEquals(ChatFormatting.BOLD + board.getDisplayName(), lines.get(0).getString(),
                    board.getID() + " must lead its tooltip with the bold board name, as 7.1.x did");
            Assertions.assertFalse(board.getDisplayName().endsWith("Board"),
                    "1.7.10's board names carried no \"Board\" suffix (got \"" + board.getDisplayName() + "\" for "
                            + board.getID() + ")");
        }
    }

    @Test
    public void theBoardLinesRenderInThe1710TooltipGray() {
        // 1.7.10's RenderItem.renderToolTip prepended GRAY to every tooltip line after the item's own
        // name — the bold board name read as gray-bold, the description as plain gray (both measured
        // #AAAAAA against a live 1.7.10 client, 2026-09-12). Modern MC renders unstyled lines white, so
        // the consumers restore the shade; the charge line keeps its own colour, as its explicit colour
        // code did back then.
        Style gray = Component.literal("").withStyle(ChatFormatting.GRAY).getStyle();
        for (RedstoneBoardRobotNBT board : allRobotBoards()) {
            ItemStack stack = ItemRobot.createRobotStack(board.getID(), 0);
            List<Component> lines = ItemRobot.tooltipLines(stack, false);

            for (int i = 0; i < lines.size() - 1; i++) {
                Assertions.assertEquals(gray, lines.get(i).getStyle(),
                        board.getID() + ": board line " + i + " renders in the 1.7.10 tooltip gray");
            }
            Assertions.assertNotEquals(gray, lines.get(lines.size() - 1).getStyle(),
                    board.getID() + ": the charge line keeps its own charge colour");
        }
    }

    private static List<RedstoneBoardRobotNBT> allRobotBoards() {
        RedstoneBoardRobotNBT empty = RedstoneBoardRegistry.instance.getEmptyRobotBoard();
        List<RedstoneBoardRobotNBT> boards = new ArrayList<>();
        for (buildcraft.api.boards.RedstoneBoardNBT<?> candidate : RedstoneBoardRegistry.instance.getAllBoardNBTs()) {
            if (candidate instanceof RedstoneBoardRobotNBT robot && robot != empty) {
                boards.add(robot);
            }
        }
        Assertions.assertFalse(boards.isEmpty(), "precondition: the robot boards are registered");
        return boards;
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

    private static final String UNKNOWN_BOARD_KEY = "buildcraft.robot.unknown_board";

    private static boolean hasUnknownBoardLine(List<Component> lines) {
        return lines.stream().anyMatch(line -> line.getContents() instanceof TranslatableContents contents
                && UNKNOWN_BOARD_KEY.equals(contents.getKey()));
    }

    @Test
    public void aRobotWhoseBoardIsNotRegisteredSaysSo() {
        // A removed addon's board, one renamed between releases, hand-edited data: the placement guard refuses
        // such a stack (it would resolve to the empty board), and the tooltip must say why instead of showing a
        // plain charged robot that "won't go on the station".
        ItemStack stack = ItemRobot.createRobotStack("buildcraftunofficial:boardRobotFromARemovedAddon",
                buildcraft.api.robots.EntityRobotBase.MAX_POWER / 2);

        List<Component> lines = ItemRobot.tooltipLines(stack, false);

        Assertions.assertTrue(hasUnknownBoardLine(lines),
                "a robot with an unregistered board must carry the Unknown board line, got " + asStrings(lines));
        Assertions.assertEquals("tip.charge.percent",
                ((TranslatableContents) lines.get(lines.size() - 1).getContents()).getKey(),
                "the charge readout stays last, the hint sits above it");
    }

    @Test
    public void aKnownBoardOrABlankRobotNeverSaysUnknown() {
        Assertions.assertFalse(hasUnknownBoardLine(ItemRobot.tooltipLines(pickerStack(), false)),
                "a registered board is not unknown");
        Assertions.assertFalse(hasUnknownBoardLine(ItemRobot.tooltipLines(
                ItemRobot.createRobotStack(RedstoneBoardRegistry.instance.getEmptyRobotBoard().getID(), 0), false)),
                "a blank robot is just blank - it shows nothing at all");
        Assertions.assertFalse(hasUnknownBoardLine(ItemRobot.tooltipLines(
                new ItemStack(buildcraft.robotics.BCRoboticsItems.ROBOT.get()), false)),
                "a bare /give-n stack is a blank robot, not an unknown board");
    }

    private static ItemStack pickerStack() {
        return ItemRobot.createRobotStack(BoardRobotPickerNBT.INSTANCE.getID(), 0);
    }

    @Test
    public void aBareStackDoesNotThrow() {
        ItemStack bare = new ItemStack(buildcraft.robotics.BCRoboticsItems.ROBOT.get());

        Assertions.assertDoesNotThrow(() -> ItemRobot.tooltipLines(bare, true),
                "a /give-n stack with no data blob must still produce a tooltip");
    }
}
