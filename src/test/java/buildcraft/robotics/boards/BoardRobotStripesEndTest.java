/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.boards;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.robotics.ai.MockRobotAccess;

/** A stripes board that ends (aborted for a recharge, say) gives its block back, and must forget it too: the same
 *  board instance resumes afterwards, and a remembered block it no longer holds would be saved, reloaded and worked
 *  again - or released a second time, by then possibly from under the robot that took it meanwhile. */
public class BoardRobotStripesEndTest extends VanillaSetupBaseTester {

    @Test
    public void endForgetsTheBlockItReleased() {
        BoardRobotStripes board = new BoardRobotStripes(new MockRobotAccess());
        CompoundTag self = new CompoundTag();
        self.putIntArray("blockFound", new int[] { 1, 64, 1 });
        board.loadSelfFromNBT(self);
        CompoundTag before = new CompoundTag();
        board.writeSelfToNBT(before);
        Assertions.assertTrue(before.contains("blockFound"), "precondition: the board remembers a block");

        board.end();

        CompoundTag after = new CompoundTag();
        board.writeSelfToNBT(after);
        Assertions.assertFalse(after.contains("blockFound"), "an ended board must not keep the block it released");
    }
}
