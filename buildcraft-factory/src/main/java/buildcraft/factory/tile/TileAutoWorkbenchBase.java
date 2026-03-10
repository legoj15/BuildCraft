/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.tile;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import buildcraft.api.core.EnumPipePart;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjBattery;
import buildcraft.api.tiles.IHasWork;
import buildcraft.lib.tile.TileBC_Neptune;
import buildcraft.lib.tile.craft.IAutoCraft;
import buildcraft.lib.tile.craft.WorkbenchCrafting;
import buildcraft.lib.tile.item.ItemHandlerFiltered;
import buildcraft.lib.tile.item.ItemHandlerManager.EnumAccess;
import buildcraft.lib.tile.item.ItemHandlerSimple;

public abstract class TileAutoWorkbenchBase extends TileBC_Neptune implements IHasWork, IAutoCraft {

    private static final long MJ_COST = 64 * MjAPI.MJ; // 64 MJ per craft

    protected final int width;
    protected final int height;

    public final ItemHandlerSimple invBlueprint;
    public final ItemHandlerSimple invMaterials;
    public final ItemHandlerSimple invResult;
    public final WorkbenchCrafting crafting;

    private final MjBattery mjBattery = new MjBattery(1024 * MjAPI.MJ);

    private long mjCostRemaining = 0;
    private boolean isActive = false;

    public TileAutoWorkbenchBase(BlockEntityType<?> type, BlockPos pos, BlockState state, int width, int height) {
        super(type, pos, state);
        this.width = width;
        this.height = height;

        int gridSize = width * height;
        // Register inventories with ItemHandlerManager for automatic save/load and item drops
        invBlueprint = itemManager.addInvHandler("blueprint", gridSize, EnumAccess.PHANTOM);
        ItemHandlerFiltered filtered = new ItemHandlerFiltered(invBlueprint, true);
        filtered.setCallback(itemManager.callback);
        invMaterials = itemManager.addInvHandler("materials", filtered, EnumAccess.BOTH, EnumPipePart.VALUES);
        invResult = itemManager.addInvHandler("result", 1, EnumAccess.EXTRACT, EnumPipePart.VALUES);

        crafting = new WorkbenchCrafting(width, height, this, invBlueprint, invMaterials, invResult);
    }

    // region IHasWork
    @Override
    public boolean hasWork() {
        return crafting.canCraft();
    }
    // endregion

    // region IAutoCraft
    @Override
    public ItemStack getCurrentRecipeOutput() {
        return crafting.getAssumedResult();
    }

    @Override
    public ItemHandlerSimple getInvBlueprint() {
        return invBlueprint;
    }
    // endregion

    // region MJ
    public MjBattery getMjBattery() {
        return mjBattery;
    }
    // endregion

    // region Ticking
    public void serverTick() {
        crafting.tick();

        if (crafting.canCraft()) {
            if (mjCostRemaining <= 0) {
                mjCostRemaining = MJ_COST;
            }

            long extracted = mjBattery.extractPower(0, mjCostRemaining);
            mjCostRemaining -= extracted;

            if (mjCostRemaining <= 0) {
                if (crafting.craft()) {
                    mjCostRemaining = 0;
                }
            }
            if (!isActive) {
                isActive = true;
                setChanged();
            }
        } else {
            if (isActive) {
                isActive = false;
                mjCostRemaining = 0;
                setChanged();
            }
        }
    }
    // endregion

    // region Save/Load
    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        // Inventories are saved by ItemHandlerManager via store(CompoundTag.CODEC)
        output.store("items", CompoundTag.CODEC, itemManager.serializeNBT());
        output.putLong("mjStored", mjBattery.getStored());
        output.putLong("mjCostRemaining", mjCostRemaining);
    }

    @Override
    public void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.read("items", CompoundTag.CODEC).ifPresent(itemManager::deserializeNBT);
        mjBattery.addPowerChecking(input.getLongOr("mjStored", 0L), false);
        mjCostRemaining = input.getLongOr("mjCostRemaining", 0L);
    }
    // endregion
}
