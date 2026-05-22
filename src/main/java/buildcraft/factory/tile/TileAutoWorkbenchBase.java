/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.tile;

import java.util.Arrays;

import javax.annotation.Nonnull;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import net.minecraft.resources.Identifier;

import buildcraft.api.core.EnumPipePart;
import buildcraft.api.mj.IMjConnector;
import buildcraft.api.mj.IMjRedstoneReceiver;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.tiles.IHasWork;
import buildcraft.lib.misc.StackUtil;
import buildcraft.lib.misc.AdvancementUtil;
import buildcraft.lib.tile.TileBC_Neptune;
import buildcraft.lib.tile.craft.IAutoCraft;
import buildcraft.lib.tile.craft.WorkbenchCrafting;
import buildcraft.lib.tile.item.ItemHandlerFiltered;
import buildcraft.lib.tile.item.ItemHandlerManager.EnumAccess;
import buildcraft.lib.tile.item.ItemHandlerSimple;

public abstract class TileAutoWorkbenchBase extends TileBC_Neptune implements IHasWork, IAutoCraft {

    private static final Identifier ADVANCEMENT = Identifier.parse("buildcraftunofficial:lazy_crafting");

    /** A redstone engine generates 1 * MjAPI.MJ per tick.
     *  This passive rate makes the workbench much slower without one powering it. */
    private static final long POWER_GEN_PASSIVE = MjAPI.MJ / 5;

    /** Total MJ required per craft: 0.2 MJ/tick * 200 ticks = 40 MJ.
     *  Without external power this takes 10 seconds. */
    private static final long POWER_REQUIRED = POWER_GEN_PASSIVE * 20 * 10;

    /** Power drained per tick when the workbench cannot craft (decays stored power). */
    private static final long POWER_LOST = POWER_GEN_PASSIVE * 10;

    protected final int width;
    protected final int height;

    public final ItemHandlerSimple invBlueprint;
    public final ItemHandlerSimple invMaterialFilter;
    public final ItemHandlerSimple invMaterials;
    public final ItemHandlerSimple invResult;
    public final WorkbenchCrafting crafting;

    /** The recipe output, synced to clients for display in the GUI. */
    public ItemStack resultClient = ItemStack.EMPTY;

    /** Accumulated MJ towards the current craft. */
    private long powerStored;

    /** Previous tick's powerStored, used for smooth client-side progress interpolation. */
    private long powerStoredLast;

    /** MJ redstone receiver — engines use pulsed power delivery (at piston midpoint)
     *  rather than constant per-tick delivery, matching 1.12.2 behavior. */
    private final IMjRedstoneReceiver mjReceiver = new IMjRedstoneReceiver() {
        @Override
        public long getPowerRequested() {
            return POWER_REQUIRED - powerStored;
        }

        @Override
        public long receivePower(long microJoules, boolean simulate) {
            long req = getPowerRequested();
            long taken = Math.min(req, microJoules);
            if (!simulate) {
                powerStored += taken;
            }
            return microJoules - taken;
        }

        @Override
        public boolean canConnect(@Nonnull IMjConnector other) {
            return true;
        }
    };

    public TileAutoWorkbenchBase(BlockEntityType<?> type, BlockPos pos, BlockState state, int width, int height) {
        super(type, pos, state);
        this.width = width;
        this.height = height;

        int gridSize = width * height;
        // Register inventories with ItemHandlerManager for automatic save/load and item drops
        invBlueprint = itemManager.addInvHandler("blueprint", gridSize, EnumAccess.PHANTOM);
        invMaterialFilter = itemManager.addInvHandler("material_filter", gridSize, EnumAccess.PHANTOM);
        ItemHandlerFiltered filtered = new ItemHandlerFiltered(invMaterialFilter, true);
        filtered.setCallback(itemManager.callback);
        invMaterials = itemManager.addInvHandler("materials", filtered, EnumAccess.INSERT, EnumPipePart.VALUES);
        invResult = itemManager.addInvHandler("result", 1, EnumAccess.EXTRACT, EnumPipePart.VALUES);

        crafting = new WorkbenchCrafting(width, height, this, invBlueprint, invMaterials, invResult);

        // Wire inventory change callbacks
        invBlueprint.setCallback((handler, slot, before, after) -> {
            setChanged();
            crafting.onInventoryChange(invBlueprint);
        });
        invMaterials.setCallback((handler, slot, before, after) -> {
            setChanged();
            crafting.onInventoryChange(invMaterials);
        });
    }

    // region IHasWork
    @Override
    public boolean hasWork() {
        return powerStored > 0;
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
    /** @return The IMjRedstoneReceiver for capability registration. */
    public IMjRedstoneReceiver getMjReceiver() {
        return mjReceiver;
    }
    // endregion

    // region Progress
    /** @return The crafting progress as a 0.0–1.0 value, interpolated for smooth rendering. */
    public double getProgress(float partialTicks) {
        double interp = powerStoredLast + (powerStored - powerStoredLast) * partialTicks;
        return interp / POWER_REQUIRED;
    }

    /** @return The current power stored (for container data slot sync). */
    public long getPowerStored() {
        return powerStored;
    }

    /** Sets powerStored from the client-side container sync. */
    public void setPowerStored(long value) {
        this.powerStoredLast = this.powerStored;
        this.powerStored = value;
        if (powerStored < 10) {
            // Properly handle crafting finishes — avoid stuttering interpolation
            powerStoredLast = powerStored;
        }
    }
    // endregion

    // region Ticking
    public void serverTick() {
        ItemStack prevResult = resultClient;
        boolean didChange = crafting.tick();

        if (didChange) {
            resultClient = crafting.getAssumedResult().copy();
            createFilters();
        }

        if (crafting.canCraft()) {
            if (powerStored >= POWER_REQUIRED) {
                if (crafting.craft()) {
                    // Keep 1 if more crafts are possible, else reset to 0
                    powerStored = crafting.canCraft() ? 1 : 0;
                    if (getOwner() != null) {
                        AdvancementUtil.unlockAdvancement(getOwner().id(), level, ADVANCEMENT);
                    }
                }
            } else {
                // Passive power generation — allows crafting without engines (slowly)
                powerStored += POWER_GEN_PASSIVE;
            }
        } else if (powerStored >= POWER_LOST) {
            powerStored -= POWER_LOST;
        } else {
            powerStored = 0;
        }

        // Sync to clients when recipe result changes
        if (!ItemStack.matches(prevResult, resultClient)) {
            setChanged();
            if (level != null) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }
    // endregion

    // region Material Filters
    /** Distributes the unique blueprint ingredients across the material filter slots,
     *  allocating slots proportionally so items with smaller stack sizes get more slots.
     *  Ported from 1.12.2 TileAutoWorkbenchBase.createFilters(). */
    private void createFilters() {
        int slotCount = invMaterialFilter.getSlots();
        if (crafting.getAssumedResult().isEmpty()) {
            for (int s = 0; s < slotCount; s++) {
                invMaterialFilter.setStackInSlot(s, ItemStack.EMPTY);
            }
            return;
        }

        // Collect unique stacks from the blueprint and count how many of each are needed
        NonNullList<ItemStack> uniqueStacks = NonNullList.create();
        int[] requirements = new int[slotCount];
        for (int s = 0; s < invBlueprint.getSlots(); s++) {
            ItemStack bptStack = invBlueprint.getStackInSlot(s);
            if (!bptStack.isEmpty()) {
                boolean foundMatch = false;
                for (int i = 0; i < uniqueStacks.size(); i++) {
                    if (StackUtil.canMerge(bptStack, uniqueStacks.get(i))) {
                        foundMatch = true;
                        requirements[i]++;
                        break;
                    }
                }
                if (!foundMatch) {
                    requirements[uniqueStacks.size()] = 1;
                    uniqueStacks.add(bptStack);
                }
            }
        }

        int uniqueSlotCount = uniqueStacks.size();
        if (uniqueSlotCount == 0) {
            for (int s = 0; s < slotCount; s++) {
                invMaterialFilter.setStackInSlot(s, ItemStack.EMPTY);
            }
            return;
        }

        // Allocate material slots proportionally — items needing more per craft
        // or with smaller max stack sizes get more slots
        int[] slotAllocationCount = new int[uniqueSlotCount];
        Arrays.fill(slotAllocationCount, 1);
        int slotsLeft = slotCount - uniqueSlotCount;
        for (int i = 0; i < slotsLeft; i++) {
            int smallestDifference = Integer.MAX_VALUE;
            int smallestDifferenceIndex = 0;
            for (int s = 0; s < uniqueSlotCount; s++) {
                ItemStack stack = uniqueStacks.get(s);
                int uniqueCountTotal = stack.getMaxStackSize() * slotAllocationCount[s];
                int difference = uniqueCountTotal / requirements[s];
                if (difference < smallestDifference) {
                    smallestDifference = difference;
                    smallestDifferenceIndex = s;
                }
            }
            slotAllocationCount[smallestDifferenceIndex]++;
        }

        // Fill filter slots with the allocated distribution
        int realIndex = 0;
        for (int s = 0; s < uniqueSlotCount; s++) {
            ItemStack stack = uniqueStacks.get(s).copyWithCount(1);
            for (int i = 0; i < slotAllocationCount[s]; i++) {
                invMaterialFilter.setStackInSlot(realIndex, stack);
                realIndex++;
            }
        }
    }
    // endregion

    // region Save/Load
    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("items", CompoundTag.CODEC, itemManager.serializeNBT());
        output.putLong("powerStored", powerStored);
        if (!resultClient.isEmpty()) {
            output.store("resultClient", ItemStack.CODEC, resultClient);
        }
    }

    @Override
    public void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.read("items", CompoundTag.CODEC).ifPresent(itemManager::deserializeNBT);
        powerStored = input.getLongOr("powerStored", 0L);
        resultClient = input.read("resultClient", ItemStack.CODEC).orElse(ItemStack.EMPTY);
    }
    // endregion

    // region Network Sync
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
    // endregion
}
