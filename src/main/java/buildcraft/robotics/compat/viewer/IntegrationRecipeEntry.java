/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.compat.viewer;

import java.util.List;

import net.minecraft.world.item.ItemStack;

import buildcraft.api.boards.RedstoneBoardNBT;
import buildcraft.robotics.item.ItemRedstoneBoard;

/** One recipe-viewer entry for the Integration Table. Viewer-free on purpose so {@link IntegrationRecipeCollector} is
 *  unit-testable.
 *
 *  @param id          Unique, stable key.
 *  @param center      The stack for the table's centre slot.
 *  @param expansions  The stacks for the ring around it, one slot each.
 *  @param outputs     What the table crafts first; any further stacks are lookup aliases (variants the viewers keep as
 *                     separate entries that come from this same recipe), shown cycling in the output slot.
 *  @param microJoules The laser energy the table needs. */
public record IntegrationRecipeEntry(
        String id,
        ItemStack center,
        List<ItemStack> expansions,
        List<ItemStack> outputs,
        long microJoules
) {
    /** The program id of the board in the ring (null if the entry has none): what the viewer's "+" transfer asks the
     *  server to move in, matched by program so any board stack of that program will do. */
    public String boardId() {
        if (expansions.isEmpty() || !(expansions.get(0).getItem() instanceof ItemRedstoneBoard)) {
            return null;
        }
        RedstoneBoardNBT<?> board = ItemRedstoneBoard.getBoardNBT(expansions.get(0));
        return board == null ? null : board.getID();
    }
}
