/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport.tile;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.core.EnumPipePart;

import buildcraft.lib.gui.IBCMenuProvider;
import buildcraft.lib.misc.BCValueInput;
import buildcraft.lib.misc.BCValueOutput;

import buildcraft.lib.tile.TileBC_Neptune;
import buildcraft.lib.tile.item.ItemHandlerFiltered;
import buildcraft.lib.tile.item.ItemHandlerManager.EnumAccess;
import buildcraft.lib.tile.item.ItemHandlerSimple;
import buildcraft.transport.BCTransportBlockEntities;
import buildcraft.transport.container.ContainerFilteredBuffer;

/**
 * Tile entity for the Filtered Buffer. Has 9 phantom filter slots and 9 real
 * inventory slots. Items can only enter a slot if the corresponding filter slot
 * contains a matching item sample.
 * Ported from 1.12.2 TileFilteredBuffer.
 */
public class TileFilteredBuffer extends TileBC_Neptune implements IBCMenuProvider {
    public final ItemHandlerSimple invFilter;
    public final ItemHandlerFiltered invMain;

    @Override
    protected boolean spillsContentsOnRemoval() {
        return true;
    }

    public TileFilteredBuffer(BlockPos pos, BlockState state) {
        super(BCTransportBlockEntities.FILTERED_BUFFER.get(), pos, state);

        invFilter = itemManager.addInvHandler("filter", 9, EnumAccess.PHANTOM);
        invFilter.setLimitedInsertor(1);

        invMain = new ItemHandlerFiltered(invFilter, false);
        itemManager.addInvHandler("main", invMain, EnumAccess.BOTH, EnumPipePart.VALUES);
    }

    // --- MenuProvider ---

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.buildcraftunofficial.filtered_buffer");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new ContainerFilteredBuffer(containerId, playerInv, this);
    }

    // --- Save / Load ---

    @Override
    protected void writeData(BCValueOutput output) {
        super.writeData(output);
        output.store("items", CompoundTag.CODEC, itemManager.serializeNBT());
    }

    @Override
    protected void readData(BCValueInput input) {
        super.readData(input);
        input.read("items", CompoundTag.CODEC).ifPresent(itemManager::deserializeNBT);
    }
}
