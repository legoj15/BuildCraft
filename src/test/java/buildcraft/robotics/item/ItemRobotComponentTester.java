/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import buildcraft.robotics.BCRoboticsItems;

/**
 * Pins the two values a robot item actually carries — which board it holds and how much charge — and the
 * exact {@code CUSTOM_DATA} shape they ride in (robotics Ph3, Decision 8).
 *
 * <p><b>Why this is a game test and not JUnit.</b> It started life as {@code ItemRobotComponentTest}, plain
 * JUnit. It could never have passed there: the {@code test} task cannot class-load an {@code ItemStack} at
 * all — the item registry is empty because nothing boots the game, and {@code Bootstrap.bootStrap()} itself
 * throws ("There is no current FML Loader", out of {@code FeatureFlags}' static initialiser). Moving the same
 * six pins here buys back the two things the JUnit version had to fake: the assertions run against the real
 * {@code buildcraftunofficial:robot} item rather than a vanilla stack wearing a robot's data, and
 * {@link ItemRobot#createRobotStack} — which has to resolve a {@code DeferredItem} — is exercised directly.
 * The proper fix for JUnit generally (moddev's {@code neoforge { unitTest }} FML environment) is its own
 * scoped task, not Ph3 work.
 */
public class ItemRobotComponentTester {

    /** The board sub-compound's own id key. Written by {@code RedstoneBoardNBT.createBoard} and read back by
     *  the board registry, so it is not the robot item's to rename. */
    private static final String BOARD_ID_KEY = "id";

    private static final String EMPTY_BOARD = "buildcraftunofficial:empty_robot_board";

    /** All six pins in one test: they share a fixture cost of exactly nothing and split into six manifests
     *  only to make the failure message longer. */
    public static void robotItemComponentPins(GameTestHelper helper) {
        tagKeysMatchTheSevenOneFormat(helper);
        aStackWithNoBlobReadsAsEmptyBoardAndZeroCharge(helper);
        boardIdAndEnergyRoundTripThroughCustomData(helper);
        energySurvivesValuesPastTheIntCeiling(helper);
        theTwoKeysAreIndependent(helper);
        anEmptyBoardCompoundReadsAsNoBoard(helper);
        helper.succeed();
    }

    // ── Save-format key names ───────────────────────────────────────────────

    private static void tagKeysMatchTheSevenOneFormat(GameTestHelper helper) {
        helper.assertTrue("board".equals(ItemRobot.TAG_BOARD),
                "7.1.x stored the board under 'board'; renaming it silently blanks every existing robot item");
        helper.assertTrue("energy".equals(ItemRobot.TAG_ENERGY),
                "and the charge under 'energy'");
    }

    // ── Reading the blob ────────────────────────────────────────────────────

    /**
     * A bare stack — {@code /give}n, taken from the creative tab, or handed over by a future recipe — must
     * read as an empty-board robot at zero charge rather than blowing up. 7.1.x leaned on this too.
     */
    private static void aStackWithNoBlobReadsAsEmptyBoardAndZeroCharge(GameTestHelper helper) {
        ItemStack bare = new ItemStack(BCRoboticsItems.ROBOT.get());

        helper.assertTrue(ItemRobot.getBoardId(bare) == null,
                "no blob means no board id — the caller substitutes the empty board");
        helper.assertTrue(ItemRobot.getEnergy(bare) == 0L, "and no stored charge");
    }

    private static void boardIdAndEnergyRoundTripThroughCustomData(GameTestHelper helper) {
        long charge = 1234L * 1_000_000L;
        ItemStack handBuilt = robotStack(EMPTY_BOARD, charge);

        helper.assertTrue(EMPTY_BOARD.equals(ItemRobot.getBoardId(handBuilt)),
                "the board id must come back out of the {board:{id:...}} sub-compound");
        helper.assertTrue(ItemRobot.getEnergy(handBuilt) == charge,
                "and the charge out of the top-level 'energy' key");

        // The write side, which the JUnit version could not reach: createRobotStack has to produce a stack
        // its own accessors read back identically, or a creative-tab robot and a placed one disagree.
        ItemStack built = ItemRobot.createRobotStack(EMPTY_BOARD, charge);
        helper.assertFalse(built.isEmpty(), "createRobotStack must build a real robot stack");
        helper.assertTrue(built.getItem() == BCRoboticsItems.ROBOT.get(),
                "createRobotStack must build a stack of the robot item itself");
        helper.assertTrue(EMPTY_BOARD.equals(ItemRobot.getBoardId(built)),
                "createRobotStack's board id must survive its own reader");
        helper.assertTrue(ItemRobot.getEnergy(built) == charge,
                "createRobotStack's charge must survive its own reader");
    }

    /**
     * The single most important thing about the charge is that it is a long of micro-MJ, not 7.1.x's RF
     * int. A full robot holds 10 000 MJ = 1e10 µMJ, four times past {@code Integer.MAX_VALUE}: any int
     * narrowing on this path turns a fully charged robot item into a negative one.
     */
    private static void energySurvivesValuesPastTheIntCeiling(GameTestHelper helper) {
        long fullCharge = 10_000L * 1_000_000L;
        helper.assertTrue(fullCharge > Integer.MAX_VALUE,
                "precondition: a full robot's charge really is past the int ceiling");

        helper.assertTrue(ItemRobot.getEnergy(robotStack(EMPTY_BOARD, fullCharge)) == fullCharge,
                "a fully charged robot item must not wrap — the stored charge is a long of micro-MJ");
        helper.assertTrue(ItemRobot.getEnergy(ItemRobot.createRobotStack(EMPTY_BOARD, fullCharge)) == fullCharge,
                "and the same holds for a stack this class built itself");
    }

    private static void theTwoKeysAreIndependent(GameTestHelper helper) {
        CompoundTag energyOnly = new CompoundTag();
        energyOnly.putLong(ItemRobot.TAG_ENERGY, 500L);
        ItemStack chargedNoBoard = withCustomData(new ItemStack(BCRoboticsItems.ROBOT.get()), energyOnly);

        helper.assertTrue(ItemRobot.getBoardId(chargedNoBoard) == null,
                "a charge without a board must not invent a board id");
        helper.assertTrue(ItemRobot.getEnergy(chargedNoBoard) == 500L,
                "and the charge still reads back");

        CompoundTag boardOnly = new CompoundTag();
        CompoundTag board = new CompoundTag();
        board.putString(BOARD_ID_KEY, EMPTY_BOARD);
        boardOnly.put(ItemRobot.TAG_BOARD, board);
        ItemStack boardNoCharge = withCustomData(new ItemStack(BCRoboticsItems.ROBOT.get()), boardOnly);

        helper.assertTrue(EMPTY_BOARD.equals(ItemRobot.getBoardId(boardNoCharge)),
                "a board without a charge still reports its board");
        helper.assertTrue(ItemRobot.getEnergy(boardNoCharge) == 0L,
                "a board without a charge reads as flat, not as some default");
    }

    /**
     * A board sub-compound that exists but carries no id is corrupt data, not a crash: an item stack has to
     * survive it the same way it survives a missing blob.
     */
    private static void anEmptyBoardCompoundReadsAsNoBoard(GameTestHelper helper) {
        CompoundTag blob = new CompoundTag();
        blob.put(ItemRobot.TAG_BOARD, new CompoundTag());
        ItemStack stack = withCustomData(new ItemStack(BCRoboticsItems.ROBOT.get()), blob);

        helper.assertTrue(ItemRobot.getBoardId(stack) == null,
                "an id-less board compound resolves to no board rather than an empty-string board id");
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    /** Hand-builds the blob rather than calling {@code createRobotStack}, so the read side is pinned against
     *  the literal save format and not merely against whatever the writer happens to emit. */
    private static ItemStack robotStack(String boardId, long energy) {
        CompoundTag board = new CompoundTag();
        board.putString(BOARD_ID_KEY, boardId);

        CompoundTag blob = new CompoundTag();
        blob.put(ItemRobot.TAG_BOARD, board);
        blob.putLong(ItemRobot.TAG_ENERGY, energy);

        return withCustomData(new ItemStack(BCRoboticsItems.ROBOT.get()), blob);
    }

    /** The documented write incantation, uniform across every node: update {@code CUSTOM_DATA} from its
     *  {@code EMPTY} default rather than assuming the component is already present. */
    private static ItemStack withCustomData(ItemStack stack, CompoundTag blob) {
        stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, existing -> CustomData.of(blob));
        return stack;
    }
}
