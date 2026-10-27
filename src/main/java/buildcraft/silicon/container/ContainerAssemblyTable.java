/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon.container;

import java.util.ArrayList;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.network.handling.IPayloadContext;

import buildcraft.lib.gui.ContainerBCTile;
import buildcraft.lib.gui.slot.SlotBase;
import buildcraft.lib.gui.slot.SlotDisplay;
import buildcraft.lib.net.PacketBufferBC;

import buildcraft.silicon.BCSiliconMenuTypes;
import buildcraft.silicon.EnumAssemblyRecipeState;
import buildcraft.silicon.tile.TileAssemblyTable;

@SuppressWarnings("this-escape")
public class ContainerAssemblyTable extends ContainerBCTile<TileAssemblyTable> {

    // Client-side constructor (from network)
    public ContainerAssemblyTable(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv.player, resolveTile(playerInv, buf, TileAssemblyTable.class));
    }

    // Server-side constructor
    public ContainerAssemblyTable(int containerId, Player player, TileAssemblyTable tile) {
        super(BCSiliconMenuTypes.ASSEMBLY_TABLE.get(), containerId, player, tile);

        for (int index = 0; index < AssemblyTableLayout.GRID_CELLS; index++) {
            addSlot(new SlotBase(tile.inv, index,
                    AssemblyTableLayout.inputX(index), AssemblyTableLayout.inputY(index)));
        }

        for (int index = 0; index < AssemblyTableLayout.GRID_CELLS; index++) {
            addSlot(new SlotDisplay(this::getDisplay, index,
                    AssemblyTableLayout.displayX(index), AssemblyTableLayout.displayY(index)));
        }

        addFullPlayerInventory(AssemblyTableLayout.PLAYER_INV_X, AssemblyTableLayout.PLAYER_INV_Y);
    }

    @Override
    public boolean clickMenuButton(Player player, int index) {
        if (tile == null) return false;
        var keys = new ArrayList<>(tile.recipesStates.keySet());
        if (index < 0 || index >= keys.size()) return false;

        TileAssemblyTable.AssemblyInstruction instruction = keys.get(index);
        EnumAssemblyRecipeState current = tile.recipesStates.get(instruction);

        if (current == EnumAssemblyRecipeState.POSSIBLE) {
            // Select: mark as saved so it becomes eligible for crafting
            tile.recipesStates.put(instruction, EnumAssemblyRecipeState.SAVED);
        } else if (current == EnumAssemblyRecipeState.SAVED) {
            // Deselect: go back to possible, remove from consideration
            tile.recipesStates.put(instruction, EnumAssemblyRecipeState.POSSIBLE);
        } else if (current == EnumAssemblyRecipeState.PAUSED) {
            // Resume: go back to SAVED, updateRecipes will re-promote
            tile.recipesStates.put(instruction, EnumAssemblyRecipeState.SAVED);
        } else {
            // Pause: SAVED_ENOUGH or SAVED_ENOUGH_ACTIVE → PAUSED
            // MJ is retained — click again to resume
            tile.recipesStates.put(instruction, EnumAssemblyRecipeState.PAUSED);
        }
        return true;
    }

    @Override
    public void readMessage(int id, PacketBufferBC buffer, boolean isClient, IPayloadContext ctx) {
        if (id == NET_TRANSFER_ITEMS && !isClient && tile != null) {
            // JEI "+" pressed on an assembly recipe: move the chosen ingredients from the player's
            // inventory into the table's input inv. Only stacks the player really holds can move.
            AssemblyTableTransfer.apply(player.getInventory(), tile.inv,
                    AssemblyTableTransfer.read(buffer, player.level().registryAccess()));
            return;
        }
        super.readMessage(id, buffer, isClient, ctx);
    }

    private ItemStack getDisplay(int index) {
        return index < tile.recipesStates.size()
                ? new ArrayList<>(tile.recipesStates.keySet()).get(index).output
                : ItemStack.EMPTY;
    }
}

