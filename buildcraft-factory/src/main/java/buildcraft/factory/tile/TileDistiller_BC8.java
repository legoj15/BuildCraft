/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.tile;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import buildcraft.api.mj.IMjReceiver;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjBattery;
import buildcraft.api.recipes.BuildcraftRecipeRegistry;
import buildcraft.api.recipes.IRefineryRecipeManager;
import buildcraft.api.recipes.IRefineryRecipeManager.IDistillationRecipe;

import buildcraft.factory.BCFactoryBlockEntities;
import buildcraft.factory.container.ContainerDistiller;
import buildcraft.lib.mj.MjBatteryReceiver;

/**
 * Distiller tile entity. Takes fluid input, consumes MJ power, and produces
 * two fluid outputs (gas + liquid) via distillation recipes.
 * Ported from 1.12.2 TileDistiller_BC8.
 */
public class TileDistiller_BC8 extends BlockEntity implements MenuProvider {

    public static final long MAX_MJ_PER_TICK = 6 * MjAPI.MJ;

    private final FluidTank tankIn = new FluidTank(4000);
    private final FluidTank tankGasOut = new FluidTank(4000);
    private final FluidTank tankLiquidOut = new FluidTank(4000);

    private final MjBattery mjBattery = new MjBattery(1024 * MjAPI.MJ);
    private final IMjReceiver mjReceiver = new MjBatteryReceiver(mjBattery);

    private IDistillationRecipe currentRecipe;
    private long distillPower = 0;
    private boolean isActive = false;

    // Client-sync tracking — send block updates when fluid contents change
    private int lastSyncedIn = -1;
    private int lastSyncedGas = -1;
    private int lastSyncedLiquid = -1;

    public TileDistiller_BC8(BlockPos pos, BlockState state) {
        super(BCFactoryBlockEntities.DISTILLER.get(), pos, state);
    }

    // --- Accessors ---

    public FluidTank getTankIn() {
        return tankIn;
    }

    public FluidTank getTankGasOut() {
        return tankGasOut;
    }

    public FluidTank getTankLiquidOut() {
        return tankLiquidOut;
    }

    public IMjReceiver getMjReceiver() {
        return mjReceiver;
    }

    /**
     * Returns the appropriate tank for the given direction.
     * Horizontal sides → input tank, UP → gas output, DOWN → liquid output.
     */
    @Nullable
    public FluidTank getTankForSide(@Nullable Direction side) {
        if (side == null) return null;
        if (side == Direction.UP) return tankGasOut;
        if (side == Direction.DOWN) return tankLiquidOut;
        return tankIn;
    }

    // --- Recipe Filtering ---

    private boolean isDistillableFluid(FluidStack fluid) {
        IRefineryRecipeManager manager = BuildcraftRecipeRegistry.refineryRecipes;
        if (manager == null) return false;
        IDistillationRecipe recipe = manager.getDistillationRegistry().getRecipeForInput(fluid);
        return recipe != null;
    }

    // --- Ticking ---

    public void serverTick() {
        if (level == null || level.isClientSide()) return;

        mjBattery.tick(level, worldPosition);

        currentRecipe = null;
        IRefineryRecipeManager manager = BuildcraftRecipeRegistry.refineryRecipes;
        if (manager != null) {
            FluidStack inFluid = tankIn.getFluid();
            if (!inFluid.isEmpty()) {
                currentRecipe = manager.getDistillationRegistry().getRecipeForInput(inFluid);
            }
        }

        if (currentRecipe == null) {
            mjBattery.addPowerChecking(distillPower, false);
            distillPower = 0;
            isActive = false;
        } else {
            FluidStack reqIn = currentRecipe.in();
            FluidStack outLiquid = currentRecipe.outLiquid();
            FluidStack outGas = currentRecipe.outGas();

            FluidStack potentialIn = tankIn.drain(reqIn.getAmount(), IFluidHandler.FluidAction.SIMULATE);
            boolean canExtract = !potentialIn.isEmpty()
                    && FluidStack.isSameFluidSameComponents(reqIn, potentialIn)
                    && potentialIn.getAmount() >= reqIn.getAmount();

            boolean canFillLiquid = tankLiquidOut.fill(outLiquid.copy(), IFluidHandler.FluidAction.SIMULATE) >= outLiquid.getAmount();
            boolean canFillGas = tankGasOut.fill(outGas.copy(), IFluidHandler.FluidAction.SIMULATE) >= outGas.getAmount();

            if (canExtract && canFillLiquid && canFillGas) {
                long max = MAX_MJ_PER_TICK;
                max *= mjBattery.getStored() + max;
                max /= mjBattery.getCapacity() / 2;
                max = Math.min(max, MAX_MJ_PER_TICK);
                long power = mjBattery.extractPower(0, max);
                distillPower += power;
                isActive = power > 0;
                long powerReq = currentRecipe.powerRequired();
                if (distillPower >= powerReq) {
                    isActive = true;
                    distillPower -= powerReq;
                    tankIn.drain(reqIn.getAmount(), IFluidHandler.FluidAction.EXECUTE);
                    tankGasOut.fill(outGas.copy(), IFluidHandler.FluidAction.EXECUTE);
                    tankLiquidOut.fill(outLiquid.copy(), IFluidHandler.FluidAction.EXECUTE);
                }
            } else {
                mjBattery.addPowerChecking(distillPower, false);
                distillPower = 0;
                isActive = false;
            }
        }

        // Send client sync when fluid amounts change
        int curIn = tankIn.getFluidAmount();
        int curGas = tankGasOut.getFluidAmount();
        int curLiq = tankLiquidOut.getFluidAmount();
        if (curIn != lastSyncedIn || curGas != lastSyncedGas || curLiq != lastSyncedLiquid) {
            lastSyncedIn = curIn;
            lastSyncedGas = curGas;
            lastSyncedLiquid = curLiq;
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    // --- MenuProvider ---

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.buildcraftfactory.distiller");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new ContainerDistiller(containerId, playerInv, this);
    }

    // --- Save / Load ---

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        // Save each tank's fluid contents directly
        if (!tankIn.getFluid().isEmpty()) {
            output.store("fluidIn", FluidStack.CODEC, tankIn.getFluid());
        }
        if (!tankGasOut.getFluid().isEmpty()) {
            output.store("fluidGasOut", FluidStack.CODEC, tankGasOut.getFluid());
        }
        if (!tankLiquidOut.getFluid().isEmpty()) {
            output.store("fluidLiquidOut", FluidStack.CODEC, tankLiquidOut.getFluid());
        }
        output.putLong("mjStored", mjBattery.getStored());
        output.putLong("distillPower", distillPower);
        output.putBoolean("isActive", isActive);
    }

    @Override
    public void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        tankIn.setFluid(input.read("fluidIn", FluidStack.CODEC).orElse(FluidStack.EMPTY));
        tankGasOut.setFluid(input.read("fluidGasOut", FluidStack.CODEC).orElse(FluidStack.EMPTY));
        tankLiquidOut.setFluid(input.read("fluidLiquidOut", FluidStack.CODEC).orElse(FluidStack.EMPTY));
        mjBattery.addPowerChecking(input.getLongOr("mjStored", 0L), false);
        distillPower = input.getLongOr("distillPower", 0L);
        isActive = input.getBooleanOr("isActive", false);
    }

    // --- Network Sync ---

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}

