/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.container;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import buildcraft.factory.BCFactoryMenuTypes;
import buildcraft.factory.tile.TileChute;
import buildcraft.lib.gui.ContainerBCTile;
import buildcraft.lib.gui.slot.SlotBase;

public class ContainerChute extends ContainerBCTile<TileChute> {

    // Client-side constructor (from network)
    public ContainerChute(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv, getTile(playerInv, buf));
    }

    // Server-side constructor
    public ContainerChute(int containerId, Inventory playerInv, TileChute tile) {
        super(BCFactoryMenuTypes.CHUTE.get(), containerId, playerInv.player, tile);

        // 4 item slots matching 1.12.2 positions
        addSlot(new SlotBase(tile.inv, 0, 62, 18));
        addSlot(new SlotBase(tile.inv, 1, 80, 18));
        addSlot(new SlotBase(tile.inv, 2, 98, 18));
        addSlot(new SlotBase(tile.inv, 3, 80, 36));

        // Player inventory at y=71 (matching 1.12.2)
        addFullPlayerInventory(8, 71);
    }

    private static TileChute getTile(Inventory playerInv, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        if (playerInv.player.level() != null) {
            var be = playerInv.player.level().getBlockEntity(pos);
            if (be instanceof TileChute chute) {
                return chute;
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
