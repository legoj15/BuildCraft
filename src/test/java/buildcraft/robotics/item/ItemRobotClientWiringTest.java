/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.item;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import buildcraft.lib.test.MainSourceSet;

/** The other half of {@code ItemRobotPlacementTester.clientPredictsNoSwingForBlankRobot}: that game test pins what
 *  {@link ItemRobot#predictClientUse} answers, but a game test runs on a server level and can never reach
 *  {@code useOn}'s client branch. So the wiring is pinned on this node's compiled source instead: the client branch
 *  returns the prediction itself, and does so before anything else in the method runs. */
public class ItemRobotClientWiringTest {

    @Test
    public void useOnAnswersTheClientWithThePrediction() {
        String body = MainSourceSet.methodBody(MainSourceSet.codeOf("buildcraft/robotics/item/ItemRobot.java"),
            "InteractionResult useOn(");
        String clientBranch = MainSourceSet.methodBody(body, "if (level.isClientSide())");
        Assertions.assertEquals("return predictClientUse(tile, face, stack);",
            clientBranch.substring(1, clientBranch.length() - 1).strip(),
            "useOn's client branch must answer with predictClientUse and nothing else — it decides the arm swing");
        Assertions.assertTrue(body.indexOf("if (level.isClientSide())") < body.indexOf("stationOnFace("),
            "the client branch must return before the server-only station lookup");
    }
}
