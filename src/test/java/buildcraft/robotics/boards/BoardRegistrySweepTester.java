/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.boards;

import net.minecraft.gametest.framework.GameTestHelper;

import buildcraft.api.boards.RedstoneBoardNBT;
import buildcraft.api.boards.RedstoneBoardRegistry;
import buildcraft.api.boards.RedstoneBoardRobotNBT;

/** Ph5 game test: the eight work boards resolve from the live registry by id, are robot boards, and
 *  report their own id. The item-blob side of the same set is pinned by the JUnit
 *  {@code BoardNbtRoundTripTest}; the {@code create(IRobotAccess)} side is covered automatically by the
 *  existing {@code everyRegisteredBoardSelfResolves} sweep once these boards are registered. Being a game
 *  test, this one runs on every node's game-test suite, not just the FML-JUnit boot. Red until the
 *  registration commit wires the boards in. */
public class BoardRegistrySweepTester {

    private static final String[] PH5_BOARD_IDS = {
            "buildcraftunofficial:boardRobotLumberjack",
            "buildcraftunofficial:boardRobotMiner",
            "buildcraftunofficial:boardRobotHarvester",
            "buildcraftunofficial:boardRobotPlanter",
            "buildcraftunofficial:boardRobotFarmer",
            "buildcraftunofficial:boardRobotPump",
            "buildcraftunofficial:boardRobotKnight",
            "buildcraftunofficial:boardRobotButcher",
    };

    /** The Ph9 advanced boards — the two break-board twins, the bomber, stripes, and the rewritten
     *  builder (7.1.x gated the builder behind the Builders sub-mod; the port is one mod, so it is
     *  always registered). */
    private static final String[] PH9_BOARD_IDS = {
            "buildcraftunofficial:boardRobotLeaveCutter",
            "buildcraftunofficial:boardRobotShovelman",
            "buildcraftunofficial:boardRobotBomber",
            "buildcraftunofficial:boardRobotStripes",
            "buildcraftunofficial:boardRobotBuilder",
    };

    public static void everyPh5BoardResolvesAndRoundTrips(GameTestHelper helper) {
        sweepBoards(helper, PH5_BOARD_IDS);
    }

    public static void everyPh9BoardResolvesAndRoundTrips(GameTestHelper helper) {
        sweepBoards(helper, PH9_BOARD_IDS);
    }

    private static void sweepBoards(GameTestHelper helper, String[] ids) {
        RedstoneBoardRegistry registry = RedstoneBoardRegistry.instance;
        helper.assertTrue(registry != null, "the board registry must be wired by the mod load");

        for (String id : ids) {
            RedstoneBoardNBT<?> boardNBT = registry.getRedstoneBoard(id);
            helper.assertTrue(boardNBT != null, "board " + id + " must be registered");
            helper.assertTrue(boardNBT instanceof RedstoneBoardRobotNBT,
                    "board " + id + " must be a ROBOT board");
            helper.assertTrue(boardNBT.getID().equals(id),
                    "board " + id + " must report its own id, got " + boardNBT.getID());
        }
        helper.succeed();
    }
}
