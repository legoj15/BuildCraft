/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.item;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

/**
 * Pins the two values a robot item actually carries — which board it holds and how much charge — and the
 * exact {@code CUSTOM_DATA} shape they ride in (robotics Ph3, Decision 8).
 *
 * <p><b>Why only two values.</b> The design deliberately limits what this file asserts. Component codecs
 * that reach into dynamic registries resolve against the plain NBT ops fallback when there is no server to
 * ask, and drop their payload silently rather than failing loudly; asserting general component fidelity
 * here would be asserting the fallback's behaviour, not the item's. A board id is a plain string and a
 * charge is a plain long — neither touches a dynamic registry, both survive the fallback, and both are a
 * save format that has to stay stable. Anything richer belongs in a game test with a real level behind it.
 *
 * <p><b>Why a vanilla item carries the blob.</b> The robot item is a {@code DeferredItem}, so outside a
 * registered mod environment it cannot be resolved and {@code createRobotStack} — which has to build a
 * stack of it — is only meaningfully testable in a game test. What is testable here is the read side:
 * given a stack whose {@code CUSTOM_DATA} looks like a robot's, the accessors must pull the right values
 * out. The stacks below are therefore ordinary vanilla stacks wearing a robot's data, and the assertions
 * are about {@link ItemRobot}'s reading of that blob rather than about which item holds it.
 *
 * <p><b>Environment note.</b> Every assertion that builds an {@link ItemStack} currently fails before it
 * reaches {@link ItemRobot}: the item registry is empty because nothing boots the game, and
 * {@code Bootstrap.bootStrap()} itself now throws ("There is no current FML Loader", raised out of
 * {@code FeatureFlags}' static initialiser) under the plain {@code test} task — which is why this class no
 * longer extends the bootstrap base tester. Leaving the failures per-test rather than collapsing them into
 * one class-level initialisation error keeps {@link #tagKeysMatchTheSevenOneFormat()} — which needs no
 * registry at all — reporting honestly. Fixing the rest is a build change, not a test change; see the Ph3
 * hand-off notes.
 */
public class ItemRobotComponentTest {

    /** The board sub-compound's own id key. Written by {@code RedstoneBoardNBT.createBoard} and read back by
     *  the board registry, so it is not the robot item's to rename. */
    private static final String BOARD_ID_KEY = "id";

    private static final String EMPTY_BOARD = "buildcraftunofficial:empty_robot_board";

    // ── Save-format key names ───────────────────────────────────────────────

    @Test
    public void tagKeysMatchTheSevenOneFormat() {
        Assertions.assertEquals("board", ItemRobot.TAG_BOARD,
                "7.1.x stored the board under 'board'; renaming it silently blanks every existing robot item");
        Assertions.assertEquals("energy", ItemRobot.TAG_ENERGY,
                "and the charge under 'energy'");
    }

    // ── Reading the blob ────────────────────────────────────────────────────

    /**
     * A bare stack — {@code /give}n, taken from the creative tab, or handed over by a future recipe — must
     * read as an empty-board robot at zero charge rather than blowing up. 7.1.x leaned on this too.
     */
    @Test
    public void aStackWithNoBlobReadsAsEmptyBoardAndZeroCharge() {
        ItemStack bare = new ItemStack(Items.STICK);

        Assertions.assertNull(ItemRobot.getBoardId(bare),
                "no blob means no board id — the caller substitutes the empty board");
        Assertions.assertEquals(0L, ItemRobot.getEnergy(bare), "and no stored charge");
    }

    @Test
    public void boardIdAndEnergyRoundTripThroughCustomData() {
        ItemStack stack = robotStack(EMPTY_BOARD, 1234L * 1_000_000L);

        Assertions.assertEquals(EMPTY_BOARD, ItemRobot.getBoardId(stack),
                "the board id must come back out of the {board:{id:...}} sub-compound");
        Assertions.assertEquals(1234L * 1_000_000L, ItemRobot.getEnergy(stack),
                "and the charge out of the top-level 'energy' key");
    }

    /**
     * The single most important thing about the charge is that it is a long of micro-MJ, not 7.1.x's RF
     * int. A full robot holds 10 000 MJ = 1e10 µMJ, four times past {@code Integer.MAX_VALUE}: any int
     * narrowing on this path turns a fully charged robot item into a negative one.
     */
    @Test
    public void energySurvivesValuesPastTheIntCeiling() {
        long fullCharge = 10_000L * 1_000_000L;
        ItemStack stack = robotStack(EMPTY_BOARD, fullCharge);

        Assertions.assertEquals(fullCharge, ItemRobot.getEnergy(stack),
                "a fully charged robot item must not wrap — the stored charge is a long of micro-MJ");
    }

    @Test
    public void theTwoKeysAreIndependent() {
        CompoundTag energyOnly = new CompoundTag();
        energyOnly.putLong(ItemRobot.TAG_ENERGY, 500L);
        ItemStack chargedNoBoard = withCustomData(new ItemStack(Items.STICK), energyOnly);

        Assertions.assertNull(ItemRobot.getBoardId(chargedNoBoard),
                "a charge without a board must not invent a board id");
        Assertions.assertEquals(500L, ItemRobot.getEnergy(chargedNoBoard));

        CompoundTag boardOnly = new CompoundTag();
        CompoundTag board = new CompoundTag();
        board.putString(BOARD_ID_KEY, EMPTY_BOARD);
        boardOnly.put(ItemRobot.TAG_BOARD, board);
        ItemStack boardNoCharge = withCustomData(new ItemStack(Items.STICK), boardOnly);

        Assertions.assertEquals(EMPTY_BOARD, ItemRobot.getBoardId(boardNoCharge));
        Assertions.assertEquals(0L, ItemRobot.getEnergy(boardNoCharge),
                "a board without a charge reads as flat, not as some default");
    }

    /**
     * A board sub-compound that exists but carries no id is corrupt data, not a crash: an item stack has to
     * survive it the same way it survives a missing blob.
     */
    @Test
    public void anEmptyBoardCompoundReadsAsNoBoard() {
        CompoundTag blob = new CompoundTag();
        blob.put(ItemRobot.TAG_BOARD, new CompoundTag());
        ItemStack stack = withCustomData(new ItemStack(Items.STICK), blob);

        Assertions.assertNull(ItemRobot.getBoardId(stack),
                "an id-less board compound resolves to no board rather than an empty-string board id");
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static ItemStack robotStack(String boardId, long energy) {
        CompoundTag board = new CompoundTag();
        board.putString(BOARD_ID_KEY, boardId);

        CompoundTag blob = new CompoundTag();
        blob.put(ItemRobot.TAG_BOARD, board);
        blob.putLong(ItemRobot.TAG_ENERGY, energy);

        return withCustomData(new ItemStack(Items.STICK), blob);
    }

    /** The documented write incantation, uniform across every node: update {@code CUSTOM_DATA} from its
     *  {@code EMPTY} default rather than assuming the component is already present. */
    private static ItemStack withCustomData(ItemStack stack, CompoundTag blob) {
        stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, existing -> CustomData.of(blob));
        return stack;
    }
}
