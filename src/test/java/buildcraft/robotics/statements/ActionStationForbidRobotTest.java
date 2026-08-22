/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.statements;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.world.item.ItemStack;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.api.boards.RedstoneBoardRobot;
import buildcraft.api.statements.IStatementParameter;
import buildcraft.api.statements.StatementSlot;
import buildcraft.robotics.ai.MockRobotAccess;
import buildcraft.robotics.boards.BoardRobotPickerNBT;
import buildcraft.robotics.item.ItemRobot;

/** {@link ActionStationForbidRobot.isForbidden}: a robot whose board matches a forbid action's
 *  parameter is forbidden; the invert (force) variant flips the match. Matching is by board NBT id. */
public class ActionStationForbidRobotTest extends VanillaSetupBaseTester {

    private static MockRobotAccess robotWithBoard() {
        MockRobotAccess robot = new MockRobotAccess();
        RedstoneBoardRobot board = BoardRobotPickerNBT.INSTANCE.create(robot);
        robot.setBoard(board);
        return robot;
    }

    private static TestStation stationWithForbid(boolean invert, IStatementParameter... params) {
        StatementSlot slot = new StatementSlot();
        slot.statement = new ActionStationForbidRobot(invert);
        slot.parameters = params;
        return new TestStation(List.of(slot));
    }

    private static StatementParameterRobot boardParam() {
        return new StatementParameterRobot(ItemRobot.createRobotStack(BoardRobotPickerNBT.INSTANCE.getID(), 0));
    }

    @Test
    public void noForbidActionAllowsRobot() {
        Assertions.assertFalse(
                ActionStationForbidRobot.isForbidden(robotWithBoard(), new TestStation(List.of())),
                "a station with no forbid action must allow the robot");
    }

    @Test
    public void matchingBoardIsForbidden() {
        Assertions.assertTrue(
                ActionStationForbidRobot.isForbidden(robotWithBoard(), stationWithForbid(false, boardParam())),
                "a robot whose board matches the forbid parameter must be forbidden");
    }

    @Test
    public void nonMatchingBoardIsAllowed() {
        Assertions.assertFalse(
                ActionStationForbidRobot.isForbidden(robotWithBoard(), stationWithForbid(false)),
                "a forbid action with an empty parameter must not forbid a board it does not name");
    }

    @Test
    public void forceVariantFlipsTheMatch() {
        // force_robot inverts: the named board is ALLOWED, everyone else is forbidden.
        Assertions.assertFalse(
                ActionStationForbidRobot.isForbidden(robotWithBoard(), stationWithForbid(true, boardParam())),
                "the force variant must allow the robot it names");
    }

    @Test
    public void forceVariantForbidsOthers() {
        // A robot whose board does NOT match a force parameter is forbidden by it. No setBoard here:
        // matches must null-guard the board and report no match, so the force parameter forbids.
        MockRobotAccess robot = new MockRobotAccess();
        Assertions.assertTrue(
                ActionStationForbidRobot.isForbidden(robot, stationWithForbid(true, boardParam())),
                "the force variant must forbid a robot whose board it does not name");
    }
}
