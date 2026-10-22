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

import net.neoforged.neoforge.network.handling.IPayloadContext;

import buildcraft.lib.gui.ContainerBCTile;
import buildcraft.lib.gui.slot.SlotBase;
import buildcraft.lib.gui.slot.SlotDisplay;
import buildcraft.lib.gui.slot.SlotOutput;
import buildcraft.lib.net.PacketBufferBC;
import buildcraft.robotics.BCRoboticsMenuTypes;
import buildcraft.silicon.tile.TileProgrammingTable;

/** Lives in robotics (mirroring 8.0.x's file placement) even though the tile is silicon's — the table's whole
 *  purpose is boards. 7.1.x layout: board in top-left, result below it, a 6×4 option grid of display-only stacks
 *  in the middle (positions: {@link ProgrammingTableLayout}). The grid is not slotted interaction: the GUI hit-tests it and routes clicks through
 *  {@link #clickMenuButton}, exactly like the assembly table's recipe list.
 *
 *  <p>A recipe offering more options than the grid has cells (an addon's boards on top of BuildCraft's own) is
 *  shown a page at a time. The page is purely a client-side view: the display slots read it, but the selection the
 *  server keeps ({@link TileProgrammingTable#optionId}) counts over all the options and needs no page. */
@SuppressWarnings("this-escape")
public class ContainerProgrammingTable extends ContainerBCTile<TileProgrammingTable> {

    /** The page of the option grid being shown, as last asked for; read through {@link #getOptionPage}, which keeps
     *  it inside the pages that exist. Kept raw so a page asked for before the options have synced is not lost. */
    private int optionPage;

    // Client-side constructor (from network)
    public ContainerProgrammingTable(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv.player, resolveTile(playerInv, buf, TileProgrammingTable.class));
    }

    // Server-side constructor
    public ContainerProgrammingTable(int containerId, Player player, TileProgrammingTable tile) {
        super(BCRoboticsMenuTypes.PROGRAMMING_TABLE.get(), containerId, player, tile);

        addSlot(new SlotBase(tile.invBoard, 0, ProgrammingTableLayout.BOARD_X, ProgrammingTableLayout.BOARD_Y));
        addSlot(new SlotOutput(tile.invResult, 0, ProgrammingTableLayout.RESULT_X, ProgrammingTableLayout.RESULT_Y));

        for (int index = 0; index < ProgrammingTableLayout.PAGE_SIZE; index++) {
            addSlot(new SlotDisplay(this::getDisplay, index,
                    ProgrammingTableLayout.cellX(index), ProgrammingTableLayout.cellY(index)));
        }

        addFullPlayerInventory(ProgrammingTableLayout.PLAYER_INV_X, ProgrammingTableLayout.PLAYER_INV_Y);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (tile == null) {
            return false;
        }
        tile.selectOption(id);
        return true;
    }

    @Override
    public void readMessage(int id, PacketBufferBC buffer, boolean isClient, IPayloadContext ctx) {
        if (id == NET_JEI_TRANSFER_PROGRAMMING && !isClient && tile != null) {
            // JEI "+" on a Programming Table recipe. Payload: recipe id, board id; everything is re-validated
            // against the player's real inventory and the table's live state.
            RoboticsTableTransfer.program(player.getInventory(), tile, buffer.readUtf(), buffer.readUtf(), true);
            return;
        }
        super.readMessage(id, buffer, isClient, ctx);
    }

    /** The page of the option grid being shown (0-based), within the pages the current options need. */
    public int getOptionPage() {
        return ProgrammingTableLayout.clampPage(optionPage, optionCount());
    }

    /** How many pages the current options need (1 when they fit the grid or there are none). */
    public int getOptionPageCount() {
        return ProgrammingTableLayout.pageCount(optionCount());
    }

    /** Shows page {@code page} of the option grid. Client-side view state only; nothing is sent. */
    public void setOptionPage(int page) {
        optionPage = Math.max(0, page);
    }

    private int optionCount() {
        return tile != null && tile.options != null ? tile.options.size() : 0;
    }

    private ItemStack getDisplay(int index) {
        int option = ProgrammingTableLayout.firstOptionOn(getOptionPage()) + index;
        return option < optionCount() ? tile.options.get(option) : ItemStack.EMPTY;
    }
}
