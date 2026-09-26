/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.compat.jei;

import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.world.item.ItemStack;

import buildcraft.api.boards.RedstoneBoardNBT;
import buildcraft.api.boards.RedstoneBoardRobotNBT;
import buildcraft.robotics.item.ItemRedstoneBoard;
import buildcraft.robotics.item.ItemRobot;

/** The JEI identity of the two program-carrying robotics items, kept JEI-free so it is unit-testable.
 *
 *  <p>Both items keep their program in {@code CUSTOM_DATA}, which JEI ignores by default — without these keys every
 *  board and every robot collapsed into ONE entry each (JEI logged 51 duplicates in the robots tab), so "recipe" on a
 *  Miner board listed every Programming Table entry at once. The key is the program id, resolved the same way the
 *  items themselves resolve it: a bare stack (what the crafting table makes) reads as the empty board, exactly like
 *  the table's own blank option.
 *
 *  <p>A programmed robot's key also carries whether it is charged at all — never the charge level. Drained and
 *  charged robots render differently (the charge tint, eyes and bar) and the creative tab deliberately lists both,
 *  so one key for both would make JEI hide one and flag them as duplicates; a per-level key would split every robot
 *  into countless entries instead. The blank robot hides its charge everywhere ({@link ItemRobot#hasEmptyBoard}), so
 *  charge never splits it. */
public final class RoboticsJeiSubtypes {
    private static final String CHARGED_SUFFIX = "#charged";

    private RoboticsJeiSubtypes() {}

    /** @return The board's program id, or null when no board registry exists yet. */
    @Nullable
    public static String boardKey(ItemStack stack) {
        RedstoneBoardNBT<?> board = ItemRedstoneBoard.getBoardNBT(stack);
        return board == null ? null : board.getID();
    }

    /** @return The robot's program id, plus a charged marker for a programmed robot holding any energy; null when no
     *          board registry exists yet. */
    @Nullable
    public static String robotKey(ItemStack stack) {
        RedstoneBoardRobotNBT board = ItemRobot.getRobotBoard(stack);
        if (board == null) {
            return null;
        }
        boolean charged = !ItemRobot.hasEmptyBoard(stack) && ItemRobot.getEnergy(stack) > 0;
        return charged ? board.getID() + CHARGED_SUFFIX : board.getID();
    }

    /** Whether the collector should treat {@code a} and {@code b} as the same stack when deciding that a programming
     *  option is a no-op: same item, and — for boards and robots — the same JEI key. Any other item counts as the
     *  same only when its components match exactly. That is deliberately STRICTER than JEI, which merges every stack
     *  of an item it has no key for: an addon recipe that rewrites such an item's components is still a real
     *  conversion (the tooltips differ), so it keeps its entry rather than being hidden as a no-op. */
    static boolean sameVariant(ItemStack a, ItemStack b) {
        if (!ItemStack.isSameItem(a, b)) {
            return false;
        }
        if (a.getItem() instanceof ItemRedstoneBoard) {
            return Objects.equals(boardKey(a), boardKey(b));
        }
        if (a.getItem() instanceof ItemRobot) {
            return Objects.equals(robotKey(a), robotKey(b));
        }
        return ItemStack.isSameItemSameComponents(a, b);
    }
}
