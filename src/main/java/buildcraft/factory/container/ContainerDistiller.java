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
import buildcraft.factory.tile.TileDistiller_BC8;
import buildcraft.lib.gui.ContainerBC_Neptune;
import buildcraft.lib.gui.widget.WidgetFluidTank;

public class ContainerDistiller extends ContainerBC_Neptune {
    public final TileDistiller_BC8 tile;
    public final WidgetFluidTank widgetTankIn;
    public final WidgetFluidTank widgetTankGasOut;
    public final WidgetFluidTank widgetTankLiquidOut;

    // Client-side constructor (from network)
    public ContainerDistiller(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv, getTile(playerInv, buf));
    }

    // Server-side constructor
    public ContainerDistiller(int containerId, Inventory playerInv, TileDistiller_BC8 tile) {
        super(BCFactoryMenuTypes.DISTILLER.get(), containerId, playerInv.player);
        this.tile = tile;

        if (tile != null) {
            addSlot(new net.neoforged.neoforge.items.SlotItemHandler(tile.containerSlots, 0, 8, 35));
            addSlot(new net.neoforged.neoforge.items.SlotItemHandler(tile.containerSlots, 1, 152, 10));
            addSlot(new net.neoforged.neoforge.items.SlotItemHandler(tile.containerSlots, 2, 152, 55));
        }

        addFullPlayerInventory(8, 79);

        widgetTankIn = addWidget(new WidgetFluidTank(this, tile != null ? tile.getTankIn() : null));
        widgetTankGasOut = addWidget(new WidgetFluidTank(this, tile != null ? tile.getTankGasOut() : null));
        widgetTankLiquidOut = addWidget(new WidgetFluidTank(this, tile != null ? tile.getTankLiquidOut() : null));
    }

    private static TileDistiller_BC8 getTile(Inventory playerInv, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        if (playerInv.player.level() != null) {
            var be = playerInv.player.level().getBlockEntity(pos);
            if (be instanceof TileDistiller_BC8 distiller) {
                return distiller;
            }
        }
        return null;
    }

    @Override
    public boolean stillValid(Player player) {
        if (tile == null) return false;
        if (tile.getLevel() == null || tile.getLevel().getBlockEntity(tile.getBlockPos()) != tile) {
            return false;
        }
        return player.distanceToSqr(
            tile.getBlockPos().getX() + 0.5,
            tile.getBlockPos().getY() + 0.5,
            tile.getBlockPos().getZ() + 0.5
        ) <= 64.0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        net.minecraft.world.inventory.Slot slot = this.slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();
            itemstack = itemstack1.copy();

            if (index < 3) {
                if (!this.moveItemStackTo(itemstack1, 3, 39, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (!this.moveItemStackTo(itemstack1, 0, 3, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (itemstack1.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (itemstack1.getCount() == itemstack.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTake(player, itemstack1);
        }

        return itemstack;
    }
}
