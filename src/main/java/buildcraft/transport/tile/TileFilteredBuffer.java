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
import buildcraft.lib.misc.InventoryUtil;

import buildcraft.lib.tile.IComparatorOutputTile;
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
public class TileFilteredBuffer extends TileBC_Neptune implements IBCMenuProvider, IComparatorOutputTile {
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

    /** 7.1.x {@code BlockFilteredBuffer.doesSlotCountComparator}: only filtered slots count. */
    @Override
    public int getComparatorLevel() {
        return InventoryUtil.getComparatorLevel(invMain, slot -> !invFilter.getStackInSlot(slot).isEmpty());
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

    /**
     * Strips everything {@link #writeData} (and the {@code TileBC_Neptune} owner it calls) saves from
     * the tag vanilla attaches as {@code BLOCK_ENTITY_DATA} when a picked item captures this tile
     * (creative Ctrl+middle-click pickblock — see {@code TileTank#removeComponentsFromTag} for the
     * pipeline). Without this the cloned buffer item carries the full 9 filter slots and any stored
     * cargo; placing the clone would recreate the filters (and duplicate stored items) for free.
     * Every key writeData can emit is discarded, so the tag ends empty and vanilla drops the
     * component entirely — the clone places as a bare filtered buffer.
     */
    // Mojang's override-only @Deprecated marker (not for removal) — vanilla block entities override it the same way.
    @SuppressWarnings("deprecation")
    //? if >=1.21.10 {
    @Override
    public void removeComponentsFromTag(net.minecraft.world.level.storage.ValueOutput output) {
        super.removeComponentsFromTag(output);
        output.discard("ownerUUID");
        output.discard("ownerName");
        output.discard("items");
    }
    //?} else {
    /*@Override
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove("ownerUUID");
        tag.remove("ownerName");
        tag.remove("items");
    }*/
    //?}
}
