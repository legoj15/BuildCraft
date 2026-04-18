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

import buildcraft.api.filler.IFillerPattern;
import buildcraft.api.tiles.IControllable;

import buildcraft.lib.gui.ContainerBCTile;
import buildcraft.lib.statement.FullStatement;

import buildcraft.builders.BCBuildersMenuTypes;
import buildcraft.builders.filler.FillerType;
import buildcraft.builders.tile.TileFiller;

public class ContainerFiller extends ContainerBCTile<TileFiller> implements IContainerFilling {

    // ContainerData indices
    private static final int DATA_CAN_EXCAVATE = 0;
    private static final int DATA_INVERTED = 1;
    private static final int DATA_FINISHED = 2;
    private static final int DATA_LOCKED = 3;
    private static final int DATA_MODE = 4;
    private static final int DATA_TO_PLACE = 5;
    private static final int DATA_TO_BREAK = 6;
    private static final int DATA_COUNT = 7;

    private final ContainerData data;
    private final FullStatement<IFillerPattern> patternStatementClient = new FullStatement<>(
        FillerType.INSTANCE,
        4,
        (statement, paramIndex) -> onStatementChange()
    );

    public final buildcraft.lib.statement.StatementContext<IFillerPattern> possiblePatternsContext = () -> java.util.List.of(
        new buildcraft.lib.statement.StatementContext.StatementGroup<IFillerPattern>() {
            @Override
            public java.util.List<IFillerPattern> getValues() {
                return new java.util.ArrayList<>(buildcraft.builders.registry.FillerRegistry.INSTANCE.getPatterns());
            }

            @Override
            public buildcraft.lib.gui.ISimpleDrawable getSourceIcon() {
                return null;
            }
        }
    );

    public void onStatementChange() {
        if (player != null && player.level() != null && player.level().isClientSide()) {
            sendMessage(NET_STATEMENT, (buf) -> {
                buildcraft.lib.net.PacketBufferBC buffer = new buildcraft.lib.net.PacketBufferBC(buf.unwrap());
                patternStatementClient.writeToBuffer(buffer);
            });
        }
    }

    // Client-side constructor (from network)
    public ContainerFiller(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv, getTile(playerInv, buf));
    }

    // Server-side constructor
    public ContainerFiller(int containerId, Inventory playerInv, TileFiller tile) {
        super(BCBuildersMenuTypes.FILLER.get(), containerId, playerInv.player, tile);

        // Container data for syncing
        if (tile != null && tile.getLevel() != null && !tile.getLevel().isClientSide()) {
            this.data = new ContainerData() {
                @Override
                public int get(int index) {
                    return switch (index) {
                        case DATA_CAN_EXCAVATE -> tile.getCanExcavate() ? 1 : 0;
                        case DATA_INVERTED -> tile.inverted ? 1 : 0;
                        case DATA_FINISHED -> tile.getFinished() ? 1 : 0;
                        case DATA_LOCKED -> tile.getLockedTicks();
                        case DATA_MODE -> tile.getModeOrdinal();
                        case DATA_TO_PLACE -> tile.getCountToPlace();
                        case DATA_TO_BREAK -> tile.getCountToBreak();
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

        // 27 resource slots in a 3x9 grid starting at (8, 40) — matches 1.12.2 layout
        // Uses a wrapper that reads/writes directly from the tile's invResources
        if (tile != null) {
            TileInventoryWrapper tileInv = new TileInventoryWrapper(tile);
            for (int sy = 0; sy < 3; sy++) {
                for (int sx = 0; sx < 9; sx++) {
                    addSlot(new Slot(tileInv, sx + sy * 9, 8 + sx * 18, 40 + sy * 18));
                }
            }
        }

        // Player inventory at y=153 (matches 1.12.2)
        addFullPlayerInventory(8, 153, playerInv);
    }

    private static TileFiller getTile(Inventory playerInv, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        if (playerInv.player.level() != null) {
            var be = playerInv.player.level().getBlockEntity(pos);
            if (be instanceof TileFiller filler) {
                return filler;
            }
        }
        return null;
    }

    // IContainerFilling

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public FullStatement<IFillerPattern> getPatternStatementClient() {
        return patternStatementClient;
    }

    @Override
    public FullStatement<IFillerPattern> getPatternStatement() {
        return tile.addon != null ? tile.addon.patternStatement : tile.patternStatement;
    }

    @Override
    public boolean isInverted() {
        return data.get(DATA_INVERTED) != 0;
    }

    public static final int NET_EXCAVATE = 10;
    public static final int NET_STATEMENT = 11;
    public static final int NET_INVERT = 12;

    @Override
    public void setInverted(boolean value) {
        if (tile.addon != null) {
            tile.addon.inverted = value;
        } else {
            tile.inverted = value;
        }
    }

    @Override
    public void readMessage(int id, buildcraft.lib.net.PacketBufferBC buffer, boolean isClient, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        super.readMessage(id, buffer, isClient, ctx);
        if (isClient) return;
        
        if (id == NET_EXCAVATE) {
            if (tile != null) {
                tile.setCanExcavate(!tile.getCanExcavate());
            }
        } else if (id == NET_INVERT) {
            if (tile != null) {
                if (tile.addon != null) {
                    tile.addon.inverted = !tile.addon.inverted;
                } else {
                    tile.inverted = !tile.inverted;
                }
            }
        } else if (id == NET_STATEMENT) {
            if (tile != null) {
                try {
                    buildcraft.lib.statement.FullStatement<IFillerPattern> stat = getPatternStatement();
                    if (stat != null) {
                        stat.readFromBuffer(buffer);
                        tile.onStatementChange();
                    }
                } catch (java.io.IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void valuesChanged() {
        if (tile.addon != null) {
            tile.addon.updateBuildingInfo();
        }
        if (tile.getLevel() != null && !tile.getLevel().isClientSide()) {
            tile.onStatementChange();
        }
    }

    // Synced accessors

    public boolean getSyncedCanExcavate() {
        return data.get(DATA_CAN_EXCAVATE) != 0;
    }

    public boolean getSyncedFinished() {
        return data.get(DATA_FINISHED) != 0;
    }

    public boolean getSyncedLocked() {
        return data.get(DATA_LOCKED) > 0;
    }

    public IControllable.Mode getSyncedMode() {
        int ordinal = data.get(DATA_MODE);
        IControllable.Mode[] values = IControllable.Mode.values();
        if (ordinal >= 0 && ordinal < values.length) return values[ordinal];
        return IControllable.Mode.ON;
    }

    public int getSyncedToPlace() {
        return data.get(DATA_TO_PLACE);
    }

    public int getSyncedToBreak() {
        return data.get(DATA_TO_BREAK);
    }

    /** A Container implementation wrapping the tile's persistent 27-slot resource inventory.
     * Reads and writes directly to TileFiller.invResources so items persist and are
     * accessible to the TemplateBuilder. */
    private static class TileInventoryWrapper implements net.minecraft.world.Container {
        private final TileFiller tile;

        TileInventoryWrapper(TileFiller tile) {
            this.tile = tile;
        }

        @Override public int getContainerSize() { return TileFiller.INV_SIZE; }
        @Override public boolean isEmpty() {
            for (int i = 0; i < TileFiller.INV_SIZE; i++) {
                if (!tile.invResources.get(i).isEmpty()) return false;
            }
            return true;
        }
        @Override public ItemStack getItem(int slot) { return tile.invResources.get(slot); }
        @Override public ItemStack removeItem(int slot, int count) {
            ItemStack stack = tile.invResources.get(slot);
            if (stack.isEmpty()) return ItemStack.EMPTY;
            ItemStack result = stack.split(count);
            if (stack.isEmpty()) tile.invResources.set(slot, ItemStack.EMPTY);
            setChanged();
            return result;
        }
        @Override public ItemStack removeItemNoUpdate(int slot) {
            ItemStack stack = tile.invResources.get(slot);
            tile.invResources.set(slot, ItemStack.EMPTY);
            return stack;
        }
        @Override public void setItem(int slot, ItemStack stack) {
            tile.invResources.set(slot, stack);
            setChanged();
        }
        @Override public void setChanged() {
            if (tile != null) tile.setChanged();
        }
        @Override public boolean stillValid(Player player) { return true; }
        @Override public void clearContent() {
            for (int i = 0; i < TileFiller.INV_SIZE; i++) {
                tile.invResources.set(i, ItemStack.EMPTY);
            }
        }
    }
}

