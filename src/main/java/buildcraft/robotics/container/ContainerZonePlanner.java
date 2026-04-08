/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.robotics.container;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import buildcraft.robotics.BCRoboticsMenuTypes;
import buildcraft.robotics.tile.TileZonePlanner;
import buildcraft.lib.gui.ContainerBCTile;
import buildcraft.lib.gui.slot.SlotBase;
import buildcraft.lib.gui.slot.SlotOutput;

public class ContainerZonePlanner extends ContainerBCTile<TileZonePlanner> {

    // Client-side constructor (from network)
    public ContainerZonePlanner(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv, getTile(playerInv, buf));
    }

    // Server-side constructor
    public ContainerZonePlanner(int containerId, Inventory playerInv, TileZonePlanner tile) {
        super(BCRoboticsMenuTypes.ZONE_PLANNER.get(), containerId, playerInv.player, tile);

        // Player inventory at y=146, matching 1.12.2 addFullPlayerInventory(88, 146)
        addFullPlayerInventory(88, 146);

        // 16 paintbrush slots in a 4×4 grid
        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 4; y++) {
                addSlot(new SlotBase(tile.invPaintbrushes, x * 4 + y, 8 + x * 18, 146 + y * 18));
            }
        }

        // Input section slots
        addSlot(new SlotBase(tile.invInputPaintbrush, 0, 8, 125));
        addSlot(new SlotBase(tile.invInputMapLocation, 0, 26, 125));
        addSlot(new SlotOutput(tile.invInputResult, 0, 74, 125));

        // Output section slots
        addSlot(new SlotBase(tile.invOutputPaintbrush, 0, 233, 9));
        addSlot(new SlotBase(tile.invOutputMapLocation, 0, 233, 27));
        addSlot(new SlotOutput(tile.invOutputResult, 0, 233, 75));
    }

    private static TileZonePlanner getTile(Inventory playerInv, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        if (playerInv.player.level() != null) {
            var be = playerInv.player.level().getBlockEntity(pos);
            if (be instanceof TileZonePlanner planner) {
                return planner;
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
