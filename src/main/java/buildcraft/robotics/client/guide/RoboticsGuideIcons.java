/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.client.guide;

import net.minecraft.world.item.ItemStack;

import buildcraft.api.boards.RedstoneBoardNBT;
import buildcraft.api.boards.RedstoneBoardRegistry;
import buildcraft.lib.client.guide.entry.PageEntryExternal;
import buildcraft.robotics.item.ItemRedstoneBoard;

/** Hands the guide book the {@code board} icon kind: an {@code external} entry with
 *  {@code "icon": {"board": "<board id>"}} draws that program's Redstone Board. Every board is the one item with its
 *  program in its data, which a plain {@code item} icon cannot name, and the Robots &gt; Boards pages are external
 *  entries for exactly that reason. Robotics registers the kind itself rather than lib naming it, so lib keeps no
 *  dependency on robotics. Called from client init; the guide only runs on the client. */
public final class RoboticsGuideIcons {
    /** The key of the icon kind in an entry's {@code icon} object. */
    public static final String BOARD_KIND = "board";

    private RoboticsGuideIcons() {}

    public static void register() {
        PageEntryExternal.registerIconKind(BOARD_KIND, RoboticsGuideIcons::boardIcon);
    }

    /** @param boardId A registered board's id, e.g. {@code buildcraftunofficial:boardRobotMiner}.
     *  @return The programmed board stack, as the Programming Table makes it; empty for an id no board has. The
     *          registry answers an unknown id with the blank board, which would draw the wrong icon for a typo'd
     *          page, so the answer is only accepted when it carries the id that was asked for. */
    static ItemStack boardIcon(String boardId) {
        RedstoneBoardRegistry registry = RedstoneBoardRegistry.instance;
        if (registry == null) {
            return ItemStack.EMPTY;
        }
        RedstoneBoardNBT<?> board = registry.getRedstoneBoard(boardId);
        return board != null && boardId.equals(board.getID()) ? ItemRedstoneBoard.createStack(board) : ItemStack.EMPTY;
    }
}
