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
import buildcraft.factory.tile.TileAutoWorkbenchItems;
import buildcraft.lib.gui.ContainerBCTile;
import buildcraft.lib.gui.slot.SlotBase;
import buildcraft.lib.gui.slot.SlotOutput;
import buildcraft.lib.gui.slot.SlotPhantom;

public class ContainerAutoCraftItems extends ContainerBCTile<TileAutoWorkbenchItems> {

    // Client-side constructor (from network)
    public ContainerAutoCraftItems(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv, getTile(playerInv, buf));
    }

    // Server-side constructor
    public ContainerAutoCraftItems(int containerId, Inventory playerInv, TileAutoWorkbenchItems tile) {
        super(BCFactoryMenuTypes.AUTO_WORKBENCH_ITEMS.get(), containerId, playerInv.player, tile);

        // Blueprint phantom slots (3x3 grid) — top-left at (30, 17)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                addSlot(new SlotPhantom(tile.invBlueprint, col + row * 3,
                        30 + col * 18, 17 + row * 18, false));
            }
        }

        // Result output slot at (124, 35)
        addSlot(new SlotOutput(tile.invResult, 0, 124, 35));

        // Materials slots (3x3) — at (30, 85)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                addSlot(new SlotBase(tile.invMaterials, col + row * 3,
                        30 + col * 18, 85 + row * 18));
            }
        }

        // Player inventory at y=153
        addFullPlayerInventory(8, 153);
    }

    private static TileAutoWorkbenchItems getTile(Inventory playerInv, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        if (playerInv.player.level() != null) {
            var be = playerInv.player.level().getBlockEntity(pos);
            if (be instanceof TileAutoWorkbenchItems workbench) {
                return workbench;
            }
        }
        return null;
    }

    /** Sets the phantom blueprint slots from a recipe's ingredient list.
     *  Called from the recipe book widget. */
    public void setPhantomSlots(java.util.List<ItemStack> stacks) {
        for (int i = 0; i < tile.invBlueprint.getSlots(); i++) {
            if (i < stacks.size()) {
                tile.invBlueprint.setStackInSlot(i, stacks.get(i).copyWithCount(1));
            } else {
                tile.invBlueprint.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        // TODO: implement shift-click transfer
        return ItemStack.EMPTY;
    }
}
