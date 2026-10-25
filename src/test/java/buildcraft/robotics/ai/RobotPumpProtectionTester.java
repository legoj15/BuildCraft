/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.ai;

import static buildcraft.lib.test.GameTestUtil.check;

import java.util.Collections;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import net.neoforged.neoforge.common.util.FakePlayer;

import buildcraft.api.robots.DockingStation;
import buildcraft.api.statements.StatementSlot;
import buildcraft.core.BCCoreConfig;
import buildcraft.factory.BCFactoryBlocks;
import buildcraft.lib.misc.FakePlayerUtil;
import buildcraft.lib.misc.GameProfileUtil;
import buildcraft.lib.test.ClaimProtectionMod;
import buildcraft.lib.test.EntityArenaUtil;
import buildcraft.lib.test.RefusingSourceBlock;
import buildcraft.lib.test.TestOnlyBlocks;
import buildcraft.robotics.RobotArenaUtil;
import buildcraft.robotics.boards.BoardRobotPump;
import buildcraft.robotics.entity.EntityRobot;

/**
 * The pump robot asks protection mods before it drains, and its board does not send it back to a block that refused.
 *
 * <p>Like {@link RobotActionAIsTester}, the AIs are driven synchronously on an unadded robot and every loop observes
 * world state. The board is driven by hand too ({@code isSearchCandidate} and {@code delegateAIEnded}), because the
 * memory under test lives on the board, not on the one-attempt AI.
 */
public class RobotPumpProtectionTester {

    private static void requireProtectionChecksOn() {
        check(!BCCoreConfig.minePlayerProtected.get(),
                "precondition: minePlayerProtected must be off (the default), or no machine asks at all");
    }

    /** The robot, unadded, at {@code relPos}. The cell's chunks are force-loaded first: the pump AI and the board's
     *  search leave a block whose drain would read a chunk that is not in memory alone, and the framework loads only
     *  the one chunk under the arena. */
    private static EntityRobot unaddedRobot(GameTestHelper helper, BlockPos relPos) {
        EntityArenaUtil.forceLoadCell(helper);
        return RobotArenaUtil.unaddedRobot(helper, relPos);
    }

    /** A stone cup around {@code wellRel} so no neighbour can re-feed the cell. */
    private static void buildCup(GameTestHelper helper, BlockPos wellRel) {
        helper.setBlock(wellRel.below(), Blocks.STONE);
        for (Direction dir : new Direction[] { Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST }) {
            helper.setBlock(wellRel.relative(dir), Blocks.STONE);
        }
    }

    /** Cycles a pump AI until it has pumped, been refused, or run out of cycles. */
    private static void runPumpAi(AIRobotPumpBlock pumper) {
        for (int cycles = 0; cycles < 120 && pumper.pumped == 0 && !pumper.refused(); cycles++) {
            pumper.cycle();
        }
    }

    /**
     * A claim covers the source: the robot asks (as the [BuildCraft] identity — this robot has no home station),
     * is refused, takes nothing, and the AI reports the refusal. With the claim gone it pumps the bucket.
     */
    public static void pumpAsksBeforeDraining(GameTestHelper helper) {
        requireProtectionChecksOn();
        ServerLevel level = helper.getLevel();
        BlockPos wellRel = new BlockPos(2, 1, 2);
        buildCup(helper, wellRel);
        helper.setBlock(wellRel, Blocks.WATER);
        BlockPos well = helper.absolutePos(wellRel);
        EntityRobot robot = unaddedRobot(helper, wellRel.above());

        try (ClaimProtectionMod claim = new ClaimProtectionMod(well, true)) {
            AIRobotPumpBlock refused = new AIRobotPumpBlock(robot, well);
            runPumpAi(refused);
            check(refused.refused(), "a refused pump AI reports the refusal");
            check(refused.pumped == 0 && !refused.success(), "a refused pump AI pumps nothing");
            check(EntityArenaUtil.tankAmount(robot.getFluidHandler()) == 0, "the robot's tank stays empty");
            check(level.getFluidState(well).isSource(), "a refused robot must leave the claimed source alone");
            check(claim.asks() == 1, "the robot asks once, got " + claim.asks());
            Player asker = claim.askedBy.get(0);
            check(asker instanceof FakePlayer, "the pump robot asks as a fake player");
            check(GameProfileUtil.getId(FakePlayerUtil.BUILDCRAFT_PROFILE).equals(asker.getUUID()),
                    "an unowned robot asks as the [BuildCraft] identity");
        }

        try (ClaimProtectionMod observer = new ClaimProtectionMod(well, false)) {
            AIRobotPumpBlock allowed = new AIRobotPumpBlock(robot, well);
            runPumpAi(allowed);
            check(!allowed.refused(), "an allowed pump AI is not refused");
            check(allowed.pumped == 1000, "once allowed, the robot pumps its bucket, got " + allowed.pumped);
            check(level.getFluidState(well).isEmpty(), "and the source is drained");
            check(observer.asks() == 1, "the allowed robot asks once, got " + observer.asks());
        }
        helper.succeed();
    }

    /**
     * The board that sent the robot remembers the refusal: the refused block stops being a search candidate (so the
     * robot flies on to the next source instead of back to this one), while other sources stay candidates.
     */
    public static void boardSkipsABlockThatRefused(GameTestHelper helper) {
        requireProtectionChecksOn();
        ServerLevel level = helper.getLevel();
        BlockPos wellRel = new BlockPos(2, 1, 2);
        BlockPos otherRel = new BlockPos(4, 1, 2);
        buildCup(helper, wellRel);
        buildCup(helper, otherRel);
        helper.setBlock(wellRel, Blocks.WATER);
        helper.setBlock(otherRel, Blocks.WATER);
        BlockPos well = helper.absolutePos(wellRel);
        BlockPos other = helper.absolutePos(otherRel);
        EntityRobot robot = unaddedRobot(helper, wellRel.above());
        BoardRobotPump board = new BoardRobotPump(robot);
        check(board.isSearchCandidate(level, well) && board.isSearchCandidate(level, other),
                "precondition: both sources are candidates");

        AIRobotPumpBlock pumper = new AIRobotPumpBlock(robot, well);
        try (ClaimProtectionMod claim = new ClaimProtectionMod(well, true)) {
            runPumpAi(pumper);
        }
        check(pumper.refused(), "precondition: the pump AI was refused");
        board.delegateAIEnded(pumper);

        check(!board.isSearchCandidate(level, well),
                "the board must not target a block that refused while it is unchanged — even though the claim "
                        + "is gone, the cell has not changed, so it stays skipped");
        check(board.isSearchCandidate(level, other), "other sources are still candidates");
        check(level.getFluidState(well).isSource(), "the refused source is untouched");
        helper.succeed();
    }

    /**
     * A refusal is about who asked as well as where: the pump AI asks as the home station's owner (this robot records
     * no deployer), so when the robot is relinked to a station someone else owns, a block the old owner was refused is a candidate again (the new
     * owner may well be allowed there). Only an unchanged cell asked by the same owner stays skipped.
     */
    public static void boardAsksAgainForANewOwner(GameTestHelper helper) {
        requireProtectionChecksOn();
        ServerLevel level = helper.getLevel();
        BlockPos wellRel = new BlockPos(2, 1, 2);
        buildCup(helper, wellRel);
        helper.setBlock(wellRel, Blocks.WATER);
        BlockPos well = helper.absolutePos(wellRel);
        EntityRobot robot = unaddedRobot(helper, wellRel.above());
        GameProfile alice = new GameProfile(UUID.fromString("5b7a9f4e-0000-4000-8000-00000000a11c"), "Alice");
        GameProfile bob = new GameProfile(UUID.fromString("5b7a9f4e-0000-4000-8000-000000000b0b"), "Bob");
        robot.setMainStation(new OwnedStation(helper.absolutePos(new BlockPos(1, 1, 1)), alice));
        BoardRobotPump board = new BoardRobotPump(robot);
        check(board.isSearchCandidate(level, well), "precondition: the source is a candidate");

        AIRobotPumpBlock pumper = new AIRobotPumpBlock(robot, well);
        try (ClaimProtectionMod claim = new ClaimProtectionMod(well, true)) {
            runPumpAi(pumper);
            check(claim.asks() == 1 && GameProfileUtil.getId(alice).equals(claim.askedBy.get(0).getUUID()),
                    "precondition: the robot asked once, as its home station's owner");
        }
        check(pumper.refused(), "precondition: the pump AI was refused");
        board.delegateAIEnded(pumper);
        check(!board.isSearchCandidate(level, well), "precondition: the refusal is remembered for the same owner");

        robot.setMainStation(new OwnedStation(helper.absolutePos(new BlockPos(3, 1, 1)), bob));
        check(board.isSearchCandidate(level, well),
                "relinked to a station another player owns, the robot must ask again rather than skip the block "
                        + "on a refusal that was given to someone else");
        check(level.getFluidState(well).isSource(), "the refused source is untouched");
        robot.setMainStation(null);
        helper.succeed();
    }

    /**
     * A Mining Well / Pump tube drilled through a pond holds the pond's water (it is waterlogged), but that water is
     * the machine's own plumbing: the pump board must not pick it as a source to drain, while the plain source beside
     * it stays a candidate.
     */
    public static void boardSkipsWaterloggedMinerTube(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos tubeRel = new BlockPos(2, 1, 2);
        BlockPos plainRel = new BlockPos(4, 1, 2);
        buildCup(helper, tubeRel);
        buildCup(helper, plainRel);
        helper.setBlock(tubeRel, BCFactoryBlocks.TUBE.get().defaultBlockState()
                .setValue(BlockStateProperties.WATERLOGGED, true));
        helper.setBlock(plainRel, Blocks.WATER);
        BlockPos tube = helper.absolutePos(tubeRel);
        BlockPos plain = helper.absolutePos(plainRel);
        check(level.getFluidState(tube).isSource(), "precondition: the waterlogged tube holds a water source");
        EntityRobot robot = unaddedRobot(helper, tubeRel.above());
        BoardRobotPump board = new BoardRobotPump(robot);

        check(board.isSearchCandidate(level, plain), "precondition: the plain water source is a candidate");
        check(!board.isSearchCandidate(level, tube), "the pump board must not target the water inside a miner tube");
        helper.setBlock(tubeRel, Blocks.AIR);
        helper.setBlock(plainRel, Blocks.AIR);
        helper.succeed();
    }

    /** A bare home station owned by {@code owner}: just enough for {@link buildcraft.robotics.RobotProtection}. */
    private static final class OwnedStation extends DockingStation {
        private final GameProfile owner;

        OwnedStation(BlockPos pos, GameProfile owner) {
            super(pos, Direction.UP);
            this.owner = owner;
        }

        @Override
        public GameProfile getOwner() {
            return owner;
        }

        @Override
        public Iterable<StatementSlot> getActiveActions() {
            return Collections.emptyList();
        }
    }

    /**
     * A modded block that reports a source but refuses its bucket: the AI reports the refusal (not a silent
     * nothing), and the board remembers it. Before, the board sent the robot back to the same block every pass.
     */
    public static void boardSkipsABlockWhosePickupRefused(GameTestHelper helper) {
        requireProtectionChecksOn();
        ServerLevel level = helper.getLevel();
        BlockPos wellRel = new BlockPos(2, 1, 2);
        buildCup(helper, wellRel);
        helper.setBlock(wellRel, TestOnlyBlocks.refusingSource());
        BlockPos well = helper.absolutePos(wellRel);
        RefusingSourceBlock.resetAttempts(well);
        EntityRobot robot = unaddedRobot(helper, wellRel.above());
        BoardRobotPump board = new BoardRobotPump(robot);
        check(board.isSearchCandidate(level, well), "precondition: the refusing block looks like a pumpable source");

        AIRobotPumpBlock pumper = new AIRobotPumpBlock(robot, well);
        runPumpAi(pumper);
        check(pumper.refused(), "a refused pickup is reported as a refusal");
        check(pumper.pumped == 0, "and pumps nothing");
        check(EntityArenaUtil.tankAmount(robot.getFluidHandler()) == 0,
                "the robot's tank stays empty (a refused pickup must not mint fluid)");
        check(RefusingSourceBlock.attemptsAt(well) == 1, "the pickup was tried once, got "
                + RefusingSourceBlock.attemptsAt(well));
        board.delegateAIEnded(pumper);

        check(!board.isSearchCandidate(level, well), "the board must not send the robot back to a refusing block");
        helper.setBlock(wellRel, Blocks.AIR);
        helper.succeed();
    }
}
