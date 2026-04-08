/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.container;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import buildcraft.lib.gui.ContainerBCTile;
import buildcraft.lib.gui.slot.SlotBase;

import buildcraft.builders.BCBuildersMenuTypes;
import buildcraft.builders.tile.TileReplacer;

public class ContainerReplacer extends ContainerBCTile<TileReplacer> {

    // Client-side constructor (from network)
    public ContainerReplacer(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv, getTile(playerInv, buf));
    }

    // Server-side constructor
    public ContainerReplacer(int containerId, Inventory playerInv, TileReplacer tile) {
        super(BCBuildersMenuTypes.REPLACER.get(), containerId, playerInv.player, tile);

        if (tile != null) {
            addSlot(new SlotBase(tile.invSnapshot, 0, 8, 115));
            addSlot(new SlotBase(tile.invSchematicFrom, 0, 8, 137));
            addSlot(new SlotBase(tile.invSchematicTo, 0, 56, 137));
        }

        // Player inventory at y=159 (matches 1.12.2 layout)
        addFullPlayerInventory(8, 159, playerInv);
    }

    private static TileReplacer getTile(Inventory playerInv, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        if (playerInv.player.level() != null) {
            var be = playerInv.player.level().getBlockEntity(pos);
            if (be instanceof TileReplacer replacer) {
                return replacer;
            }
        }
        return null;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
