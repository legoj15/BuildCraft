/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.container;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import buildcraft.factory.BCFactoryMenuTypes;
import buildcraft.factory.tile.TileHeatExchange;
import buildcraft.factory.tile.TileHeatExchange.ExchangeSection;
import buildcraft.factory.tile.TileHeatExchange.ExchangeSectionEnd;
import buildcraft.factory.tile.TileHeatExchange.ExchangeSectionStart;
import buildcraft.lib.gui.ContainerBC_Neptune;
import buildcraft.lib.gui.slot.SlotBase;
import buildcraft.lib.gui.widget.WidgetFluidTank;

public class ContainerHeatExchange extends ContainerBC_Neptune {
    /** The START tile of the heat exchanger multi-block, or {@code null} if the
     * structure is missing/incomplete. The end tanks are reached via this. */
    @Nullable
    public final TileHeatExchange tile;

    public final WidgetFluidTank widgetTankStartInput;
    public final WidgetFluidTank widgetTankStartOutput;
    public final WidgetFluidTank widgetTankEndInput;
    public final WidgetFluidTank widgetTankEndOutput;

    // Client-side constructor (from network)
    public ContainerHeatExchange(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv, getStartTile(playerInv, buf));
    }

    // Server-side constructor
    public ContainerHeatExchange(int containerId, Inventory playerInv, @Nullable TileHeatExchange tile) {
        super(BCFactoryMenuTypes.HEAT_EXCHANGE.get(), containerId, playerInv.player);
        this.tile = tile;

        // Bucket / fluid-shard slots (1.12.2 layout: top-left, mid-left, top-right, mid-right
        // — paired geometrically with the four tanks).
        // Slots must be added BEFORE the player inventory so shift-click ordering is correct.
        if (tile != null) {
            addSlot(new SlotBase(tile.containerSlots, 0, 8, 23));    // → END.tankInput   (hot in)
            addSlot(new SlotBase(tile.containerSlots, 1, 8, 64));    // → START.tankInput (cold in)
            addSlot(new SlotBase(tile.containerSlots, 2, 152, 12));  // ← END.tankOutput  (heated out)
            addSlot(new SlotBase(tile.containerSlots, 3, 152, 54));  // ← START.tankOutput (cooled out)
        }

        addFullPlayerInventory(8, 89);

        ExchangeSectionStart start = startSection(tile);
        ExchangeSectionEnd end = start != null ? start.getEndSection() : null;

        widgetTankStartInput = addWidget(new WidgetFluidTank(this, start != null ? start.tankInput : null));
        widgetTankStartOutput = addWidget(new WidgetFluidTank(this, start != null ? start.tankOutput : null));
        widgetTankEndInput = addWidget(new WidgetFluidTank(this, end != null ? end.tankInput : null));
        widgetTankEndOutput = addWidget(new WidgetFluidTank(this, end != null ? end.tankOutput : null));
    }

    @Nullable
    public ExchangeSectionStart startSection() {
        return startSection(tile);
    }

    @Nullable
    public ExchangeSectionEnd endSection() {
        ExchangeSectionStart start = startSection();
        return start != null ? start.getEndSection() : null;
    }

    @Nullable
    private static ExchangeSectionStart startSection(@Nullable TileHeatExchange tile) {
        if (tile == null) return null;
        ExchangeSection section = tile.getSection();
        return section instanceof ExchangeSectionStart s ? s : null;
    }

    @Nullable
    private static TileHeatExchange getStartTile(Inventory playerInv, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        if (playerInv.player.level() == null) return null;
        BlockEntity be = playerInv.player.level().getBlockEntity(pos);
        if (!(be instanceof TileHeatExchange exchange)) return null;
        return exchange.findStart();
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

            if (index < 4) {
                // Machine slot → player inventory
                if (!this.moveItemStackTo(itemstack1, 4, 40, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // Player inventory → first available machine slot
                if (!this.moveItemStackTo(itemstack1, 0, 4, false)) {
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
