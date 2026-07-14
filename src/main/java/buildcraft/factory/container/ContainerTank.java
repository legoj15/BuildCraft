/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.container;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import buildcraft.factory.BCFactoryMenuTypes;
import buildcraft.factory.tile.TileTank;
import buildcraft.lib.gui.ContainerBCTile;
import buildcraft.lib.gui.widget.WidgetFluidTank;

@SuppressWarnings("this-escape")
public class ContainerTank extends ContainerBCTile<TileTank> {
    public final WidgetFluidTank widgetTank;

    // Client-side constructor (from network)
    public ContainerTank(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv, resolveTile(playerInv, buf, TileTank.class));
    }

    // Server-side constructor
    public ContainerTank(int containerId, Inventory playerInv, TileTank tank) {
        super(BCFactoryMenuTypes.TANK.get(), containerId, playerInv.player, tank);

        addFullPlayerInventory(8, 99);

        widgetTank = addWidget(new WidgetFluidTank(this, tank != null ? tank.tank : null));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // The only slots are player slots — no container slots to move into
        return ItemStack.EMPTY;
    }
}
