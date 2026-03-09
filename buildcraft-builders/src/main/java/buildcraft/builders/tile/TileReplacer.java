/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.tile;

import java.util.Date;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.core.InvalidInputDataException;
import buildcraft.api.enums.EnumSnapshotType;
import buildcraft.api.schematics.ISchematicBlock;

import buildcraft.lib.tile.TileBC_Neptune;
import buildcraft.lib.tile.item.ItemHandlerSimple;

import buildcraft.builders.BCBuildersBlockEntities;
import buildcraft.builders.BCBuildersItems;
import buildcraft.builders.container.ContainerReplacer;
import buildcraft.builders.item.ItemSchematicSingle;
import buildcraft.builders.item.ItemSnapshot;
import buildcraft.builders.snapshot.Blueprint;
import buildcraft.builders.snapshot.GlobalSavedDataSnapshots;
import buildcraft.builders.snapshot.Snapshot;

public class TileReplacer extends TileBC_Neptune implements MenuProvider {

    /** Accepts a used blueprint item. */
    public final ItemHandlerSimple invSnapshot;
    /** Accepts a used schematic-single item (the "from" pattern). */
    public final ItemHandlerSimple invSchematicFrom;
    /** Accepts a used schematic-single item (the "to" pattern). */
    public final ItemHandlerSimple invSchematicTo;

    public TileReplacer(BlockPos pos, BlockState state) {
        super(BCBuildersBlockEntities.REPLACER.get(), pos, state);
        invSnapshot = new ItemHandlerSimple(1);
        invSnapshot.setChecker((slot, stack) ->
                stack.getItem() instanceof ItemSnapshot snap && snap.isUsed()
                && snap.getSnapshotType() == EnumSnapshotType.BLUEPRINT);
        invSchematicFrom = new ItemHandlerSimple(1);
        invSchematicFrom.setChecker((slot, stack) ->
                stack.getItem() instanceof ItemSchematicSingle s && s.isUsed());
        invSchematicTo = new ItemHandlerSimple(1);
        invSchematicTo.setChecker((slot, stack) ->
                stack.getItem() instanceof ItemSchematicSingle s && s.isUsed());
    }

    public void tick() {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (!invSnapshot.getStackInSlot(0).isEmpty()
                && !invSchematicFrom.getStackInSlot(0).isEmpty()
                && !invSchematicTo.getStackInSlot(0).isEmpty()) {

            Snapshot.Header header = ItemSnapshot.getHeader(invSnapshot.getStackInSlot(0));
            if (header != null) {
                Snapshot snapshot = GlobalSavedDataSnapshots.get(level).getSnapshot(header.key);
                if (snapshot instanceof Blueprint blueprint) {
                    try {
                        ISchematicBlock from = ItemSchematicSingle.getSchematic(
                                invSchematicFrom.getStackInSlot(0));
                        ISchematicBlock to = ItemSchematicSingle.getSchematic(
                                invSchematicTo.getStackInSlot(0));
                        if (from != null && to != null) {
                            Blueprint newBlueprint = blueprint.copy();
                            newBlueprint.replace(from, to);
                            newBlueprint.computeKey();
                            GlobalSavedDataSnapshots.get(level).addSnapshot(newBlueprint);
                            // Create a new used blueprint item with the updated header
                            ItemSnapshot usedItem = BCBuildersItems.BLUEPRINT_USED.get();
                            Snapshot.Header newHeader = new Snapshot.Header(
                                    newBlueprint.key,
                                    header.owner,
                                    new Date(),
                                    header.name
                            );
                            invSnapshot.setStackInSlot(0, usedItem.createUsedStack(newHeader));
                            invSchematicFrom.setStackInSlot(0, ItemStack.EMPTY);
                            invSchematicTo.setStackInSlot(0, ItemStack.EMPTY);
                            setChanged();
                        }
                    } catch (InvalidInputDataException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    // --- MenuProvider ---

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.buildcraftbuilders.replacer");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new ContainerReplacer(containerId, playerInv, this);
    }
}
