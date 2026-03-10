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

        // Result output slot at (124, 35) — matches 1.12.2
        addSlot(new SlotOutput(tile.invResult, 0, 124, 35));

        // Blueprint phantom slots (3x3 grid) — top-left at (30, 17) — matches 1.12.2
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                addSlot(new SlotPhantom(tile.invBlueprint, x + y * 3,
                        30 + x * 18, 17 + y * 18, false));
            }
        }

        // Materials slots — single row of 9 at (8 + x*18, 84) — matches 1.12.2
        for (int x = 0; x < 9; x++) {
            addSlot(new SlotBase(tile.invMaterials, x, 8 + x * 18, 84));
        }

        // Player inventory at (8, 115) — matches 1.12.2
        addFullPlayerInventory(8, 115);
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
