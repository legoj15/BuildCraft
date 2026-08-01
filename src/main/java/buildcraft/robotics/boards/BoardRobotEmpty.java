/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.boards;

import buildcraft.api.boards.RedstoneBoardRobot;
import buildcraft.api.boards.RedstoneBoardRobotNBT;
import buildcraft.api.robots.IRobotAccess;

/**
 * The board a robot has when it has no board: it does nothing at all.
 *
 * <p>7.1.x's equivalent immediately delegated to {@code AIRobotGotoSleep}, so an unprogrammed robot flew back to
 * its station and parked. That AI arrives in Ph4 along with the rest of the AI tree; until then this board's
 * {@code update()} is deliberately empty, which — because {@code AIRobot.update}'s own default is
 * {@code terminate()} — is the difference between "idles quietly" and "tears its own AI down on the first
 * tick".
 *
 * <p>It still has to exist in Ph3 for three reasons that have nothing to do with behaviour: it is what an
 * unknown or absent board id resolves to, it supplies the robot's skin through
 * {@link BoardRobotEmptyNBT#getRobotTexture()}, and it is the board a dropped robot item records.
 */
public class BoardRobotEmpty extends RedstoneBoardRobot {

    public BoardRobotEmpty(IRobotAccess robot) {
        super(robot);
    }

    @Override
    public RedstoneBoardRobotNBT getNBTHandler() {
        return BoardRobotEmptyNBT.INSTANCE;
    }

    @Override
    public void update() {
        // Nothing — see the class javadoc. Explicitly NOT the inherited default (which terminates).
    }
}
