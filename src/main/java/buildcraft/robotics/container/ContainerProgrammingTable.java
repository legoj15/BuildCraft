/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.robotics.container;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import buildcraft.lib.gui.ContainerBCTile;
import buildcraft.lib.gui.slot.SlotBase;
import buildcraft.lib.gui.slot.SlotDisplay;
import buildcraft.lib.gui.slot.SlotOutput;
import buildcraft.robotics.BCRoboticsMenuTypes;
import buildcraft.silicon.tile.TileProgrammingTable;

/** Lives in robotics (mirroring 8.0.x's file placement) even though the tile is silicon's — the table's whole
 *  purpose is boards. 7.1.x layout: board in top-left, result below it, a 6×4 option grid of display-only stacks
 *  in the middle. The grid is not slotted interaction: the GUI hit-tests it and routes clicks through
 *  {@link #clickMenuButton}, exactly like the assembly table's recipe list. */
@SuppressWarnings("this-escape")
public class ContainerProgrammingTable extends ContainerBCTile<TileProgrammingTable> {

    // Client-side constructor (from network)
    public ContainerProgrammingTable(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv.player, resolveTile(playerInv, buf, TileProgrammingTable.class));
    }

    // Server-side constructor
    public ContainerProgrammingTable(int containerId, Player player, TileProgrammingTable tile) {
        super(BCRoboticsMenuTypes.PROGRAMMING_TABLE.get(), containerId, player, tile);

        addSlot(new SlotBase(tile.invBoard, 0, 8, 36));
        addSlot(new SlotOutput(tile.invResult, 0, 8, 90));

        for (int y = 0; y < TileProgrammingTable.OPTION_ROWS; y++) {
            for (int x = 0; x < TileProgrammingTable.OPTION_COLS; x++) {
                addSlot(new SlotDisplay(this::getDisplay, x + y * TileProgrammingTable.OPTION_COLS,
                        43 + x * 18, 36 + y * 18));
            }
        }

        addFullPlayerInventory(8, 123);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (tile == null) {
            return false;
        }
        tile.selectOption(id);
        return true;
    }

    private ItemStack getDisplay(int index) {
        return tile != null && tile.options != null && index < tile.options.size()
                ? tile.options.get(index)
                : ItemStack.EMPTY;
    }
}
