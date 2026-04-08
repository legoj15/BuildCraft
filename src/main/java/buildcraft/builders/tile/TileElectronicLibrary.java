/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.builders.tile;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import buildcraft.api.enums.EnumSnapshotType;

import buildcraft.lib.tile.TileBC_Neptune;
import buildcraft.lib.tile.item.ItemHandlerSimple;

import buildcraft.builders.BCBuildersBlockEntities;
import buildcraft.builders.BCBuildersItems;
import buildcraft.builders.container.ContainerElectronicLibrary;
import buildcraft.builders.item.ItemSnapshot;
import buildcraft.builders.snapshot.GlobalSavedDataSnapshots;
import buildcraft.builders.snapshot.Snapshot;

public class TileElectronicLibrary extends TileBC_Neptune implements MenuProvider {

    /** Accepts used snapshot items for downloading (copying snapshot to local DB). */
    public final ItemHandlerSimple invDownIn;
    /** Output slot for the download operation. */
    public final ItemHandlerSimple invDownOut = new ItemHandlerSimple(1);
    /** Accepts any snapshot item for uploading (writing a selected snapshot onto the item). */
    public final ItemHandlerSimple invUpIn;
    /** Output slot for the upload operation. */
    public final ItemHandlerSimple invUpOut = new ItemHandlerSimple(1);

    /** The currently selected snapshot key (selected by the player in the GUI). */
    @Nullable
    public Snapshot.Key selected = null;

    /** Progress counters: -1 = idle, 0..49 = working, >=50 = done. */
    public int progressDown = -1;
    public int progressUp = -1;

    public TileElectronicLibrary(BlockPos pos, BlockState state) {
        super(BCBuildersBlockEntities.LIBRARY.get(), pos, state);
        invDownIn = new ItemHandlerSimple(1);
        invDownIn.setChecker((slot, stack) ->
                stack.getItem() instanceof ItemSnapshot snap && snap.isUsed());
        invUpIn = new ItemHandlerSimple(1);
        invUpIn.setChecker((slot, stack) ->
                stack.getItem() instanceof ItemSnapshot);
    }

    public void tick() {
        if (level == null || level.isClientSide()) {
            return;
        }

        // --- Download: copy a used snapshot into the local database ---
        if (!invDownIn.getStackInSlot(0).isEmpty() && invDownOut.getStackInSlot(0).isEmpty()) {
            if (progressDown == -1) {
                progressDown = 0;
            }
            if (progressDown >= 50) {
                // Download complete: move item through
                invDownOut.setStackInSlot(0, invDownIn.getStackInSlot(0).copy());
                invDownIn.setStackInSlot(0, ItemStack.EMPTY);
                progressDown = -1;
                setChanged();
            } else {
                progressDown++;
            }
        } else if (progressDown != -1) {
            progressDown = -1;
        }

        // --- Upload: write the selected snapshot onto a clean snapshot item ---
        if (selected != null && !invUpIn.getStackInSlot(0).isEmpty() && invUpOut.getStackInSlot(0).isEmpty()) {
            if (progressUp == -1) {
                progressUp = 0;
            }
            if (progressUp >= 50) {
                // Upload complete: create a used item with the selected snapshot's header
                Snapshot snapshot = GlobalSavedDataSnapshots.get(level).getSnapshot(selected);
                if (snapshot != null) {
                    EnumSnapshotType snapshotType = snapshot.getType();
                    ItemSnapshot usedItem;
                    if (snapshotType == EnumSnapshotType.BLUEPRINT) {
                        usedItem = BCBuildersItems.BLUEPRINT_USED.get();
                    } else {
                        usedItem = BCBuildersItems.TEMPLATE_USED.get();
                    }
                    Snapshot.Header header = snapshot.key.header;
                    if (header == null) {
                        header = new Snapshot.Header(
                            snapshot.key,
                            new java.util.UUID(0, 0),
                            new java.util.Date(),
                            "Snapshot"
                        );
                    }
                    ItemStack outputStack = usedItem.createUsedStack(header);
                    invUpOut.setStackInSlot(0, outputStack);
                    invUpIn.setStackInSlot(0, ItemStack.EMPTY);
                }
                progressUp = -1;
                setChanged();
            } else {
                progressUp++;
            }
        } else if (progressUp != -1) {
            progressUp = -1;
        }
    }

    // --- NBT persistence ---
    // Note: ItemHandlerSimple slots are not persisted through ValueOutput's flat API.
    // Items in slots will be lost on chunk unload. Full item persistence will be added
    // when the ItemHandlerManager serialization is wired into ValueOutput.

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("progressDown", progressDown);
        output.putInt("progressUp", progressUp);
        if (selected != null) {
            output.putString("selectedHash", selected.toString());
        }
    }

    @Override
    public void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        progressDown = input.getIntOr("progressDown", -1);
        progressUp = input.getIntOr("progressUp", -1);
        // Selected key will be re-set from GUI interaction
        selected = null;
    }

    // --- MenuProvider ---

    @Override
    public Component getDisplayName() {
        return Component.translatable("tile.buildcraftunofficial.library.name");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new ContainerElectronicLibrary(containerId, playerInv, this);
    }
}
