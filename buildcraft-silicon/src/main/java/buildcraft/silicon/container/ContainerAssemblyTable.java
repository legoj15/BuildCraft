/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon.container;

import java.util.ArrayList;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import buildcraft.lib.gui.ContainerBCTile;
import buildcraft.lib.gui.slot.SlotBase;
import buildcraft.lib.gui.slot.SlotDisplay;

import buildcraft.silicon.BCSiliconMenuTypes;
import buildcraft.silicon.tile.TileAssemblyTable;

public class ContainerAssemblyTable extends ContainerBCTile<TileAssemblyTable> {

    // Client-side constructor (from network)
    public ContainerAssemblyTable(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv.player, getTile(playerInv, buf));
    }

    // Server-side constructor
    public ContainerAssemblyTable(int containerId, Player player, TileAssemblyTable tile) {
        super(BCSiliconMenuTypes.ASSEMBLY_TABLE.get(), containerId, player, tile);

        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 3; x++) {
                addSlot(new SlotBase(tile.inv, x + y * 3, 8 + x * 18, 36 + y * 18));
            }
        }

        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 3; x++) {
                addSlot(new SlotDisplay(this::getDisplay, x + y * 3, 116 + x * 18, 36 + y * 18));
            }
        }

        addFullPlayerInventory(8, 138);
    }

    private ItemStack getDisplay(int index) {
        return index < tile.recipesStates.size()
                ? new ArrayList<>(tile.recipesStates.keySet()).get(index).output
                : ItemStack.EMPTY;
    }

    private static TileAssemblyTable getTile(Inventory inv, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        var be = inv.player.level().getBlockEntity(pos);
        return be instanceof TileAssemblyTable t ? t : null;
    }
}
