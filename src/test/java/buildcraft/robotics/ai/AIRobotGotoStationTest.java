/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.ai;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** The cell {@link AIRobotGotoStation} pathfinds to before its final straight move onto the dock.
 *
 *  <p>7.1.x had two classes here: {@code AIRobotGotoStation}, which flew to {@code station + side}, and
 *  {@code AIRobotGoAndLinkToDock} — the take-as-main equivalent — which flew to {@code station + side*2}.
 *  The port merged them behind the {@code takeAsMain} flag; this pins that the flag still picks the right
 *  approach distance. */
public class AIRobotGotoStationTest {

    private static final BlockPos STATION = new BlockPos(4, 5, 6);

    @Test
    public void aPlainVisitApproachesOneCellOut() {
        Assertions.assertEquals(STATION.relative(Direction.EAST, 1),
                AIRobotGotoStation.approachCell(STATION, Direction.EAST, false),
                "a plain station visit stops one cell out (7.1.x AIRobotGotoStation)");
    }

    @Test
    public void linkingAsMainApproachesTwoCellsOut() {
        Assertions.assertEquals(STATION.relative(Direction.EAST, 2),
                AIRobotGotoStation.approachCell(STATION, Direction.EAST, true),
                "linking a home station stops two cells out (7.1.x AIRobotGoAndLinkToDock)");
    }

    @Test
    public void theApproachFollowsTheStationSide() {
        for (Direction side : Direction.values()) {
            Assertions.assertEquals(STATION.relative(side, 1),
                    AIRobotGotoStation.approachCell(STATION, side, false),
                    "the plain approach is one cell along " + side);
            Assertions.assertEquals(STATION.relative(side, 2),
                    AIRobotGotoStation.approachCell(STATION, side, true),
                    "the linking approach is two cells along " + side);
        }
    }
}
