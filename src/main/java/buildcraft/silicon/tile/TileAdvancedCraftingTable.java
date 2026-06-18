/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon.tile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.core.EnumPipePart;
import buildcraft.api.mj.MjAPI;

import buildcraft.lib.misc.BCValueInput;
import buildcraft.lib.misc.BCValueOutput;
import buildcraft.lib.tile.craft.WorkbenchCrafting;
import buildcraft.lib.tile.item.ItemHandlerManager.EnumAccess;
import buildcraft.lib.tile.item.ItemHandlerSimple;

import buildcraft.silicon.BCSiliconBlockEntities;

@SuppressWarnings("this-escape")
public class TileAdvancedCraftingTable extends TileLaserTableBase {
    private static final long POWER_REQ = 500 * MjAPI.MJ;

    public final ItemHandlerSimple invBlueprint;
    public final ItemHandlerSimple invMaterials;
    public final ItemHandlerSimple invResults;
    private final WorkbenchCrafting crafting;

    public ItemStack resultClient = ItemStack.EMPTY;

    public TileAdvancedCraftingTable(BlockPos pos, BlockState state) {
        super(BCSiliconBlockEntities.ADVANCED_CRAFTING_TABLE.get(), pos, state);
        invBlueprint = itemManager.addInvHandler("blueprint", 3 * 3, EnumAccess.PHANTOM);
        invMaterials = itemManager.addInvHandler("materials", 5 * 3, EnumAccess.INSERT, EnumPipePart.VALUES);
        invResults = itemManager.addInvHandler("result", 3 * 3, EnumAccess.EXTRACT, EnumPipePart.VALUES);
        crafting = new WorkbenchCrafting(3, 3, this, invBlueprint, invMaterials, invResults);

        // Wire inventory change callbacks to WorkbenchCrafting
        invBlueprint.setCallback((handler, slot, before, after) -> {
            setChanged();
            crafting.onInventoryChange(invBlueprint);
        });
        invMaterials.setCallback((handler, slot, before, after) -> {
            setChanged();
            crafting.onInventoryChange(invMaterials);
        });
    }

    @Override
    public long getTarget() {
        if (level == null) return 0;
        if (level.isClientSide()) {
            // Client mirror for the HAS_WORK display only; lasers never read this client-side.
            return !resultClient.isEmpty() ? POWER_REQ : 0;
        }
        // 1.12.2: the server only requests laser power when the recipe can actually be crafted
        // (materials present). Requesting on a blueprint-only table tied up lasers it couldn't use,
        // starving tables that could actually craft.
        return crafting.canCraft() ? POWER_REQ : 0;
    }

    @Override
    public void serverTick() {
        super.serverTick();

        ItemStack prevResult = resultClient;
        boolean didChange = crafting.tick();
        if (didChange) {
            resultClient = crafting.getAssumedResult().copy();
        }
        if (crafting.canCraft()) {
            if (power >= POWER_REQ) {
                if (crafting.craft()) {
                    power -= POWER_REQ;
                }
            }
        }

        // Sync to clients when recipe result changes
        if (!ItemStack.matches(prevResult, resultClient)) {
            setChanged();
            if (level != null) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    public ItemStack getCurrentRecipeOutput() {
        return crafting.getAssumedResult();
    }

    public ItemHandlerSimple getInvBlueprint() {
        return invBlueprint;
    }

    /** Cycles which output the blueprint produces when 2+ recipes match (server-side; dir +1/-1) and
     *  re-syncs the result preview to clients. Called from the container's cycle-output button. */
    public void cycleCraftingOutput(int dir) {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (crafting.cycleOutput(dir)) {
            resultClient = crafting.getAssumedResult().copy();
            setChanged();
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Number of recipes the current blueprint matches (&gt;1 enables the GUI cycle button). */
    public int getCraftingMatchCount() {
        return crafting.getMatchCount();
    }

    /** Index of the currently selected output among the matches. */
    public int getCraftingSelectedIndex() {
        return crafting.getSelectedIndex();
    }

    // --- Save / Load ---

    @Override
    protected void writeData(BCValueOutput output) {
        super.writeData(output);
        if (!resultClient.isEmpty()) {
            output.store("resultClient", ItemStack.CODEC, resultClient);
        }
        output.putString("selectedRecipe", crafting.getSelectedRecipeId());
    }

    @Override
    protected void readData(BCValueInput input) {
        super.readData(input);
        resultClient = input.read("resultClient", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        crafting.setPendingSelectedRecipeId(input.getStringOr("selectedRecipe", ""));
    }

    // --- Network Sync ---

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
