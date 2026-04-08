/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.transport.container;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import buildcraft.lib.gui.ContainerBCTile;
import buildcraft.lib.gui.slot.SlotBase;
import buildcraft.lib.gui.slot.SlotPhantom;
import buildcraft.transport.BCTransportMenuTypes;
import buildcraft.transport.tile.TileFilteredBuffer;

public class ContainerFilteredBuffer extends ContainerBCTile<TileFilteredBuffer> {

    // Client-side constructor (from network)
    public ContainerFilteredBuffer(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv, getTile(playerInv, buf));
    }

    // Server-side constructor
    public ContainerFilteredBuffer(int containerId, Inventory playerInv, TileFilteredBuffer tile) {
        super(BCTransportMenuTypes.FILTERED_BUFFER.get(), containerId, playerInv.player, tile);

        for (int i = 0; i < 9; i++) {
            // Filter slots (phantom) at y=27
            addSlot(new SlotPhantom(tile.invFilter, i, 8 + i * 18, 27, false));
            // Inventory slots at y=61
            addSlot(new SlotBase(tile.invMain, i, 8 + i * 18, 61));
        }

        // Player inventory at y=86 (matching 1.12.2 layout)
        addFullPlayerInventory(8, 86);
    }

    private static TileFilteredBuffer getTile(Inventory playerInv, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        if (playerInv.player.level() != null) {
            var be = playerInv.player.level().getBlockEntity(pos);
            if (be instanceof TileFilteredBuffer filtered) {
                return filtered;
            }
        }
        return null;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        // TODO: implement shift-click transfer
        return ItemStack.EMPTY;
    }
}
