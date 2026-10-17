/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.world.item.ItemStack;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.api.boards.RedstoneBoardNBT;
import buildcraft.api.boards.RedstoneBoardRegistry;
import buildcraft.api.boards.RedstoneBoardRobotNBT;
import buildcraft.api.statements.StatementMouseClick;
import buildcraft.robotics.item.ItemRobot;
import buildcraft.robotics.statements.StatementParameterRobot;

/** {@link RobotUtils#getNextBoard} drives the Forbid/Force Robot parameter's board picker. 7.1.x walked the
 *  registry's boards one click at a time and answered "none" past the end, so the cycle was
 *  <em>empty, first board, ... last board, empty</em> and an empty click was how a player cleared the parameter
 *  again. The port wrapped from the last board straight back to the first, so a picked board could never be
 *  un-picked by clicking. */
public class RobotUtilsNextBoardTest extends VanillaSetupBaseTester {

    private static final StatementMouseClick LEFT = new StatementMouseClick(0, false);
    private static final StatementMouseClick RIGHT = new StatementMouseClick(1, false);

    /** The boards a robot stack can name, in the registry's order - the sequence the picker walks. */
    private static List<RedstoneBoardRobotNBT> robotBoards() {
        List<RedstoneBoardRobotNBT> boards = new ArrayList<>();
        for (RedstoneBoardNBT<?> candidate : RedstoneBoardRegistry.instance.getAllBoardNBTs()) {
            if (candidate instanceof RedstoneBoardRobotNBT robot) {
                boards.add(robot);
            }
        }
        Assertions.assertTrue(boards.size() > 2, "precondition: the registry has a few robot boards");
        return boards;
    }

    private static ItemStack robotOf(RedstoneBoardRobotNBT board) {
        return ItemRobot.createRobotStack(board.getID(), 0);
    }

    @Test
    public void forwardFromNothingStartsAtTheFirstBoardAndReverseAtTheLast() {
        List<RedstoneBoardRobotNBT> boards = robotBoards();

        Assertions.assertSame(boards.get(0), RobotUtils.getNextBoard(ItemStack.EMPTY, false));
        Assertions.assertSame(boards.get(boards.size() - 1), RobotUtils.getNextBoard(ItemStack.EMPTY, true));
    }

    @Test
    public void forwardWalksEveryBoardInOrderThenStops() {
        List<RedstoneBoardRobotNBT> boards = robotBoards();

        List<RedstoneBoardRobotNBT> walked = new ArrayList<>();
        ItemStack current = ItemStack.EMPTY;
        for (int guard = 0; guard <= boards.size() + 1; guard++) {
            RedstoneBoardRobotNBT next = RobotUtils.getNextBoard(current, false);
            if (next == null) {
                break;
            }
            walked.add(next);
            current = robotOf(next);
        }

        Assertions.assertEquals(boards, walked,
                "clicking forward from nothing visits each board once, in registry order, and then runs out "
                        + "(it must not wrap round and walk them again)");
    }

    @Test
    public void reverseWalksEveryBoardBackwardThenStops() {
        List<RedstoneBoardRobotNBT> boards = robotBoards();

        List<RedstoneBoardRobotNBT> walked = new ArrayList<>();
        ItemStack current = ItemStack.EMPTY;
        for (int guard = 0; guard <= boards.size() + 1; guard++) {
            RedstoneBoardRobotNBT next = RobotUtils.getNextBoard(current, true);
            if (next == null) {
                break;
            }
            walked.add(next);
            current = robotOf(next);
        }

        List<RedstoneBoardRobotNBT> expected = new ArrayList<>(boards);
        java.util.Collections.reverse(expected);
        Assertions.assertEquals(expected, walked, "backward is the same walk, last board first, then it runs out");
    }

    @Test
    public void thereIsNoBoardAfterTheLastOrBeforeTheFirst() {
        List<RedstoneBoardRobotNBT> boards = robotBoards();

        Assertions.assertNull(RobotUtils.getNextBoard(robotOf(boards.get(boards.size() - 1)), false),
                "past the last board there is none");
        Assertions.assertNull(RobotUtils.getNextBoard(robotOf(boards.get(0)), true),
                "before the first board there is none");
    }

    @Test
    public void clickingPastTheEndClearsTheParameter() {
        List<RedstoneBoardRobotNBT> boards = robotBoards();
        StatementParameterRobot onLast = new StatementParameterRobot(robotOf(boards.get(boards.size() - 1)));
        StatementParameterRobot onFirst = new StatementParameterRobot(robotOf(boards.get(0)));

        Assertions.assertTrue(onLast.onClick(null, null, ItemStack.EMPTY, LEFT).getItemStack().isEmpty(),
                "an empty click on the last board clears the parameter (the way back to 'any robot')");
        Assertions.assertTrue(onFirst.onClick(null, null, ItemStack.EMPTY, RIGHT).getItemStack().isEmpty(),
                "an empty right-click on the first board clears it too");
    }
}
