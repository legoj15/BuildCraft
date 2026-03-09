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
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import buildcraft.lib.gui.ContainerBCTile;
import buildcraft.lib.gui.slot.SlotBase;

import buildcraft.builders.BCBuildersMenuTypes;
import buildcraft.builders.snapshot.Snapshot;
import buildcraft.builders.tile.TileElectronicLibrary;

public class ContainerElectronicLibrary extends ContainerBCTile<TileElectronicLibrary> {

    // ContainerData indices
    private static final int DATA_PROGRESS_DOWN = 0;
    private static final int DATA_PROGRESS_UP = 1;
    private static final int DATA_COUNT = 2;

    private final ContainerData data;

    // Client-side constructor (from network)
    public ContainerElectronicLibrary(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv, getTile(playerInv, buf));
    }

    // Server-side constructor
    public ContainerElectronicLibrary(int containerId, Inventory playerInv, TileElectronicLibrary tile) {
        super(BCBuildersMenuTypes.LIBRARY.get(), containerId, playerInv.player, tile);

        // Container data for syncing progress
        if (tile != null && tile.getLevel() != null && !tile.getLevel().isClientSide()) {
            this.data = new ContainerData() {
                @Override
                public int get(int index) {
                    return switch (index) {
                        case DATA_PROGRESS_DOWN -> tile.progressDown;
                        case DATA_PROGRESS_UP -> tile.progressUp;
                        default -> 0;
                    };
                }

                @Override
                public void set(int index, int value) {
                    // Read-only on client
                }

                @Override
                public int getCount() {
                    return DATA_COUNT;
                }
            };
        } else {
            this.data = new SimpleContainerData(DATA_COUNT);
        }

        addDataSlots(this.data);

        // 4 slots matching 1.12.2 positions, using SlotBase for all slots
        if (tile != null) {
            // Download output
            addSlot(new SlotBase(tile.invDownOut, 0, 175, 57));
            // Download input
            addSlot(new SlotBase(tile.invDownIn, 0, 219, 57));
            // Upload input
            addSlot(new SlotBase(tile.invUpIn, 0, 175, 79));
            // Upload output
            addSlot(new SlotBase(tile.invUpOut, 0, 219, 79));
        }

        // Player inventory at y=138 (matching 1.12.2 layout)
        addFullPlayerInventory(8, 138, playerInv);
    }

    private static TileElectronicLibrary getTile(Inventory playerInv, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        if (playerInv.player.level() != null) {
            var be = playerInv.player.level().getBlockEntity(pos);
            if (be instanceof TileElectronicLibrary library) {
                return library;
            }
        }
        return null;
    }

    // --- Synced accessors for GUI ---

    public int getSyncedProgressDown() {
        return data.get(DATA_PROGRESS_DOWN);
    }

    public int getSyncedProgressUp() {
        return data.get(DATA_PROGRESS_UP);
    }

    /** Send the selected snapshot key from the client to the server. */
    public void sendSelectedToServer(Snapshot.Key selected) {
        // For now, set directly on the tile (works in single-player).
        // In multiplayer, this would need a custom packet.
        if (tile != null) {
            tile.selected = selected;
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
