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

import buildcraft.api.enums.EnumSnapshotType;

import buildcraft.lib.gui.ContainerBCTile;

import buildcraft.builders.BCBuildersMenuTypes;
import buildcraft.builders.tile.TileBuilder;

public class ContainerBuilder extends ContainerBCTile<TileBuilder> {

    private static final int DATA_CAN_EXCAVATE = 0;
    private static final int DATA_SNAPSHOT_TYPE = 1;  // -1 = none, 0 = TEMPLATE, 1 = BLUEPRINT
    private static final int DATA_LEFT_TO_BREAK = 2;
    private static final int DATA_LEFT_TO_PLACE = 3;
    private static final int DATA_COUNT = 4;

    private final ContainerData data;

    // Client-side constructor (from network)
    public ContainerBuilder(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv, getTile(playerInv, buf));
    }

    // Server-side constructor
    public ContainerBuilder(int containerId, Inventory playerInv, TileBuilder tile) {
        super(BCBuildersMenuTypes.BUILDER.get(), containerId, playerInv.player, tile);

        // Container data for syncing
        if (tile != null && tile.getLevel() != null && !tile.getLevel().isClientSide()) {
            this.data = new ContainerData() {
                @Override
                public int get(int index) {
                    return switch (index) {
                        case DATA_CAN_EXCAVATE -> tile.canExcavate() ? 1 : 0;
                        case DATA_SNAPSHOT_TYPE -> tile.snapshotType == null ? -1 : tile.snapshotType.ordinal();
                        case DATA_LEFT_TO_BREAK -> tile.getBuilder() != null ? tile.getBuilder().leftToBreak : 0;
                        case DATA_LEFT_TO_PLACE -> tile.getBuilder() != null ? tile.getBuilder().leftToPlace : 0;
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

        // Player inventory at y=140 (matches 1.12.2 layout)
        addFullPlayerInventory(8, 140, playerInv);
    }

    private static TileBuilder getTile(Inventory playerInv, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        if (playerInv.player.level() != null) {
            var be = playerInv.player.level().getBlockEntity(pos);
            if (be instanceof TileBuilder builder) {
                return builder;
            }
        }
        return null;
    }

    public boolean getSyncedCanExcavate() {
        return data.get(DATA_CAN_EXCAVATE) != 0;
    }

    public int getSyncedSnapshotType() {
        return data.get(DATA_SNAPSHOT_TYPE);
    }

    public int getSyncedLeftToBreak() {
        return data.get(DATA_LEFT_TO_BREAK);
    }

    public int getSyncedLeftToPlace() {
        return data.get(DATA_LEFT_TO_PLACE);
    }
}
