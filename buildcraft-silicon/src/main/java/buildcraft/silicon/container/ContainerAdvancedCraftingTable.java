/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon.container;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

import buildcraft.lib.gui.ContainerBCTile;
import buildcraft.lib.gui.slot.SlotBase;
import buildcraft.lib.gui.slot.SlotDisplay;
import buildcraft.lib.gui.slot.SlotOutput;
import buildcraft.lib.gui.slot.SlotPhantom;

import buildcraft.silicon.BCSiliconMenuTypes;
import buildcraft.silicon.tile.TileAdvancedCraftingTable;

public class ContainerAdvancedCraftingTable extends ContainerBCTile<TileAdvancedCraftingTable> {

    // Client-side constructor (from network)
    public ContainerAdvancedCraftingTable(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv.player, getTile(playerInv, buf));
    }

    // Server-side constructor
    public ContainerAdvancedCraftingTable(int containerId, Player player, TileAdvancedCraftingTable tile) {
        super(BCSiliconMenuTypes.ADVANCED_CRAFTING_TABLE.get(), containerId, player, tile);

        // Display slot showing current recipe result
        addSlot(new SlotDisplay(i -> tile.resultClient, 0, 127, 33));

        // 5x3 material input slots
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 5; x++) {
                addSlot(new SlotBase(tile.invMaterials, x + y * 5, 15 + x * 18, 85 + y * 18));
            }
        }

        // 3x3 result output slots
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                addSlot(new SlotOutput(tile.invResults, x + y * 3, 109 + x * 18, 85 + y * 18));
            }
        }

        // 3x3 phantom blueprint slots
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                addSlot(new SlotPhantom(tile.invBlueprint, x + y * 3, 33 + x * 18, 16 + y * 18, false));
            }
        }

        addFullPlayerInventory(8, 153);
    }

    private static TileAdvancedCraftingTable getTile(Inventory inv, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        var be = inv.player.level().getBlockEntity(pos);
        return be instanceof TileAdvancedCraftingTable t ? t : null;
    }
}
