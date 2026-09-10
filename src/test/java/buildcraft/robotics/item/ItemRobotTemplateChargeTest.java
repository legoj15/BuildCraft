/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.item;

import net.minecraft.world.item.ItemStack;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.robotics.BCRoboticsItems;
import buildcraft.robotics.boards.BoardRobotEmptyNBT;
import buildcraft.robotics.boards.BoardRobotLumberjackNBT;

/**
 * Pins the empty-board template's exemption from the charge readouts: a template robot — a bare stack
 * with no board data at all, a blob with no board sub-compound, or the empty board's own id — must
 * show no charge anywhere, because it is not a functional robot and holds nothing worth reading.
 * 7.1.x hid its charge line too.
 *
 * <p>The shared rule is {@link ItemRobot#hasEmptyBoard}: {@code appendHoverText} gates the charge
 * line on it and {@code RobotChargeTintSource} keeps the eyes dark for it. The tooltip itself is not
 * invoked here (its signature forks across the nodes — it is one guarded {@code tooltip.accept});
 * the bar's visibility is, because {@code isBarVisible} is node-stable. The pure ramp math behind
 * the readouts is {@code ItemRobotChargeDisplayTest}'s territory.
 *
 * <p>Runs under the FML-JUnit environment and uses the registered item — a test cannot {@code new}
 * an {@code ItemRobot} (the constructor registers an intrusive holder into the long-frozen item
 * registry) — and writes blobs through {@link ItemRobot#createRobotStack} itself, so the production
 * writer is what's under test, not a hand-rolled lookalike. VanillaSetupBaseTester binds the item
 * components on the nodes that need it.
 */
public class ItemRobotTemplateChargeTest extends VanillaSetupBaseTester {

    /** The bare stack — what a {@code /give} or a registry-less fallback hands out: no blob at all. */
    private static ItemStack bareRobot() {
        return new ItemStack(BCRoboticsItems.ROBOT.get());
    }

    @Test
    public void templateShapesAreTheEmptyBoard() {
        Assertions.assertTrue(ItemRobot.hasEmptyBoard(bareRobot()),
                "a bare stack with no blob at all is the empty-board template");
        Assertions.assertTrue(ItemRobot.hasEmptyBoard(ItemRobot.createRobotStack(null, 0L)),
                "a blob with no board sub-compound is the empty-board template");
        Assertions.assertTrue(
                ItemRobot.hasEmptyBoard(ItemRobot.createRobotStack(BoardRobotEmptyNBT.ID, 0L)),
                "the empty board's own id is the template");
    }

    @Test
    public void aProgrammedBoardIsNotTheTemplate() {
        Assertions.assertFalse(
                ItemRobot.hasEmptyBoard(ItemRobot.createRobotStack(BoardRobotLumberjackNBT.ID, 0L)),
                "any other board id is a functional robot and keeps its charge readouts");
    }

    @Test
    public void theInventoryBarFollowsTheTemplateGate() {
        ItemRobot item = BCRoboticsItems.ROBOT.get();
        Assertions.assertFalse(item.isBarVisible(bareRobot()), "the bare template shows no bar");
        Assertions.assertFalse(item.isBarVisible(ItemRobot.createRobotStack(null, 0L)),
                "the board-less template shows no bar");
        Assertions.assertFalse(item.isBarVisible(ItemRobot.createRobotStack(BoardRobotEmptyNBT.ID, 0L)),
                "the drained empty-board template shows no bar");
        Assertions.assertFalse(
                item.isBarVisible(ItemRobot.createRobotStack(BoardRobotEmptyNBT.ID, Long.MAX_VALUE)),
                "the empty-board template shows no bar even charged — it is not a functional robot");
        Assertions.assertTrue(
                item.isBarVisible(ItemRobot.createRobotStack(BoardRobotLumberjackNBT.ID, 0L)),
                "a board robot shows the bar even drained: the trough IS the no-charge state");
    }
}
