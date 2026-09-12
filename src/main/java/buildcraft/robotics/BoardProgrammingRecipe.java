/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.robotics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.world.item.ItemStack;

import buildcraft.api.boards.RedstoneBoardNBT;
import buildcraft.api.boards.RedstoneBoardRegistry;
import buildcraft.api.recipes.IProgrammingRecipe;
import buildcraft.robotics.item.ItemRedstoneBoard;

/** The Programming Table's one built-in recipe: turns any redstone board into any registered board, at a laser-energy
 *  cost taken straight from the board registry. Ported from 7.1.x {@code buildcraft.robotics.BoardProgrammingRecipe}
 *  (upstream carried no copyright header); stacks now go through {@link ItemRedstoneBoard#createStack} and costs are
 *  long micro-MJ. Options cover <em>every</em> registered board — the empty board included, at cost 0 — so addon
 *  boards appear in the table automatically. */
public class BoardProgrammingRecipe implements IProgrammingRecipe {

    /** Sorts options cheapest-first, ties broken by board id — the deterministic order both the GUI grid and the
     *  saved option index rely on. 7.1.x expressed this as {@code (cost1 - cost2) * 200} in an int; longs make that
     *  subtraction an overflow hazard, so this is the same order without the arithmetic. */
    private static final Comparator<ItemStack> BOARD_SORTER = (o1, o2) -> {
        int byCost = Long.compare(getCost(o1), getCost(o2));
        return byCost != 0 ? byCost : getId(o1).compareTo(getId(o2));
    };

    private static long getCost(ItemStack option) {
        RedstoneBoardNBT<?> board = ItemRedstoneBoard.getBoardNBT(option);
        return RedstoneBoardRegistry.instance.getPowerCost(board);
    }

    private static String getId(ItemStack option) {
        RedstoneBoardNBT<?> board = ItemRedstoneBoard.getBoardNBT(option);
        return board.getID();
    }

    @Override
    public String getId() {
        return "buildcraftunofficial:redstone_board";
    }

    @Override
    public List<ItemStack> getOptions(int width, int height) {
        List<ItemStack> options = new ArrayList<>();
        for (RedstoneBoardNBT<?> nbt : RedstoneBoardRegistry.instance.getAllBoardNBTs()) {
            options.add(ItemRedstoneBoard.createStack(nbt));
        }
        options.sort(BOARD_SORTER);
        return options;
    }

    @Override
    public long getEnergyCost(ItemStack option) {
        return getCost(option);
    }

    @Override
    public boolean canCraft(ItemStack input) {
        return input.getItem() instanceof ItemRedstoneBoard;
    }

    @Override
    public ItemStack craft(ItemStack input, ItemStack option) {
        return option.copy();
    }
}
