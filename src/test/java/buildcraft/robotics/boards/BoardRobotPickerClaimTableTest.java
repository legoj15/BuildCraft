/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.boards;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import buildcraft.robotics.ai.AIRobotFetchItem;
import buildcraft.robotics.ai.MockRobotAccess;

/** The picker's claim table holds the claiming fetch AI, which holds its robot, which holds its level. A robot
 *  mid-fetch when a server stops never runs {@code end()}, so without a stop-time clear the stopped server's whole
 *  world graph would stay reachable from a static field — on an integrated server, for as long as the player
 *  sits on the title screen. These pin that the table is emptied when the server stops, and that the clear is
 *  actually wired to {@link ServerStoppedEvent} (the unit-test JVM runs full mod loading, so BC's listeners are
 *  live on the NeoForge bus). */
public class BoardRobotPickerClaimTableTest {

    private final MockRobotAccess robot = new MockRobotAccess();

    @AfterEach
    public void clearTable() {
        BoardRobotPicker.clearTargets();
    }

    @Test
    public void aLiveClaimIsHonoured() {
        UUID item = UUID.randomUUID();
        BoardRobotPicker.claimTarget(item, new AIRobotFetchItem(robot));
        Assertions.assertTrue(BoardRobotPicker.isTargetted(item),
                "fixture: a claim held by a robot that is still around must lock the item");
    }

    @Test
    public void serverStoppedReleasesEveryClaim() {
        UUID item = UUID.randomUUID();
        BoardRobotPicker.claimTarget(item, new AIRobotFetchItem(robot));

        NeoForge.EVENT_BUS.post(new ServerStoppedEvent(null));

        Assertions.assertFalse(BoardRobotPicker.isTargetted(item),
                "a stopped server must not leave claims (and the robot/level graph they reference) in the "
                        + "static table until the next server start");
    }
}
