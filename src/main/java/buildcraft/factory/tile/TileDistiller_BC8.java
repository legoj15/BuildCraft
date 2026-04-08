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
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import buildcraft.api.mj.IMjReceiver;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjBattery;
import buildcraft.api.recipes.BuildcraftRecipeRegistry;
import buildcraft.api.recipes.IRefineryRecipeManager;
import buildcraft.api.recipes.IRefineryRecipeManager.IDistillationRecipe;
import buildcraft.api.tiles.IDebuggable;
import buildcraft.lib.fluid.FluidSmoother;

import buildcraft.factory.BCFactoryBlockEntities;
import buildcraft.factory.container.ContainerDistiller;
import buildcraft.lib.mj.MjBatteryReceiver;

/**
 * Distiller tile entity. Takes fluid input, consumes MJ power, and produces
 * two fluid outputs (gas + liquid) via distillation recipes.
 * Ported from 1.12.2 TileDistiller_BC8.
 */
public class TileDistiller_BC8 extends BlockEntity implements MenuProvider, IDebuggable {

    public static final long MAX_MJ_PER_TICK = 6 * MjAPI.MJ;

    private final FluidStacksResourceHandler tankIn = new FluidStacksResourceHandler(1, 4000);
    private final FluidStacksResourceHandler tankGasOut = new FluidStacksResourceHandler(1, 4000);
    private final FluidStacksResourceHandler tankLiquidOut = new FluidStacksResourceHandler(1, 4000);

    private final MjBattery mjBattery = new MjBattery(1024 * MjAPI.MJ);
    private final IMjReceiver mjReceiver = new MjBatteryReceiver(mjBattery);

    public final net.neoforged.neoforge.items.ItemStackHandler containerSlots = new net.neoforged.neoforge.items.ItemStackHandler(3) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }
    };

    // Client-side fluid smoothers for render interpolation
    private final FluidSmoother smoothIn = new FluidSmoother(tankIn);
    private final FluidSmoother smoothGasOut = new FluidSmoother(tankGasOut);
    private final FluidSmoother smoothLiquidOut = new FluidSmoother(tankLiquidOut);

    private IDistillationRecipe currentRecipe;
    private long distillPower = 0;
    private boolean isActive = false;

    // Power average: exponential moving average in micro-MJ,
    // approximating 1.12.2's AverageLong(100) rolling average.
    private long powerAvgSmoothed = 0;
    private long powerAvgClient = 0;

    // Client-side animation state for power indicator cubes
    // Matches 1.12.2 expression: state increments when active, decays when inactive
    private double animState = 0;
    private double prevAnimState = 0;

    // Client-sync tracking — send block updates when fluid contents or active state change
    private int lastSyncedIn = -1;
    private int lastSyncedGas = -1;
    private int lastSyncedLiquid = -1;
    private boolean lastSyncedActive = false;
    private long lastSyncedPower = -1;

    public TileDistiller_BC8(BlockPos pos, BlockState state) {
        super(BCFactoryBlockEntities.DISTILLER.get(), pos, state);
    }

    // --- Accessors ---

    public FluidStacksResourceHandler getTankIn() {
        return tankIn;
    }

    public FluidStacksResourceHandler getTankGasOut() {
        return tankGasOut;
    }

    public FluidStacksResourceHandler getTankLiquidOut() {
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
    public FluidStacksResourceHandler getTankForSide(@Nullable Direction side) {
        if (side == null) return null;
        if (side == Direction.UP) return tankGasOut;
        if (side == Direction.DOWN) return tankLiquidOut;
        return tankIn;
    }

    // --- Fluid Smoothers (for rendering) ---

    public FluidSmoother getSmoothIn() {
        return smoothIn;
    }

    public FluidSmoother getSmoothGasOut() {
        return smoothGasOut;
    }

    public FluidSmoother getSmoothLiquidOut() {
        return smoothLiquidOut;
    }

    // --- Animation Accessors (for renderer) ---

    public boolean isActive() {
        return isActive;
    }

    /** Returns the client-synced power average in micro-MJ. */
    public long getPowerAvgClient() {
        return powerAvgClient;
    }

    /** Returns the current animation phase for power indicator cubes. */
    public double getAnimState() {
        return animState;
    }

    /** Returns the previous tick's animation phase, for per-frame lerp in the renderer. */
    public double getPrevAnimState() {
        return prevAnimState;
    }

    /** Call from a client-side ticker to advance fluid smoothing and power animation. */
    public void clientTick() {
        smoothIn.tick();
        smoothGasOut.tick();
        smoothLiquidOut.tick();

        prevAnimState = animState;

        // Advance power cube animation (matches 1.12.2 expression logic)
        // powerAvgClient is in micro-MJ, MAX_MJ_PER_TICK is in micro-MJ
        double changeSpeed = isActive && MAX_MJ_PER_TICK > 0
                ? ((double) powerAvgClient / MAX_MJ_PER_TICK) * 0.06
                : 0.01;
        if (isActive) {
            animState += changeSpeed;
            // Wrap around: once state >= 1.5, subtract 1 to loop between 0.5 and 1.5
            if (animState >= 1.5) {
                animState -= 1.0;
                prevAnimState -= 1.0; // Keep prev in sync so lerp doesn't jump backwards
            }
        } else {
            animState = animState > changeSpeed ? animState - changeSpeed : 0;
        }
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

        if (level.getGameTime() % 5 == 0) {
            // Slot 0: Input container -> drain into tankIn
            net.minecraft.world.item.ItemStack inStack = containerSlots.getStackInSlot(0);
            if (!inStack.isEmpty()) {
                @SuppressWarnings("removal")
                net.neoforged.neoforge.fluids.FluidActionResult result = net.neoforged.neoforge.fluids.FluidUtil.tryEmptyContainer(
                    inStack, net.neoforged.neoforge.fluids.capability.IFluidHandler.of(tankIn), Integer.MAX_VALUE, null, true
                );
                if (result.isSuccess()) {
                    containerSlots.setStackInSlot(0, result.getResult());
                }
            }
            // Slot 1: Output container gas -> fill from tankGasOut
            net.minecraft.world.item.ItemStack gasStack = containerSlots.getStackInSlot(1);
            if (!gasStack.isEmpty()) {
                @SuppressWarnings("removal")
                net.neoforged.neoforge.fluids.FluidActionResult result = net.neoforged.neoforge.fluids.FluidUtil.tryFillContainer(
                    gasStack, net.neoforged.neoforge.fluids.capability.IFluidHandler.of(tankGasOut), Integer.MAX_VALUE, null, true
                );
                if (result.isSuccess()) {
                    containerSlots.setStackInSlot(1, result.getResult());
                }
            }
            // Slot 2: Output container liquid -> fill from tankLiquidOut
            net.minecraft.world.item.ItemStack liqStack = containerSlots.getStackInSlot(2);
            if (!liqStack.isEmpty()) {
                @SuppressWarnings("removal")
                net.neoforged.neoforge.fluids.FluidActionResult result = net.neoforged.neoforge.fluids.FluidUtil.tryFillContainer(
                    liqStack, net.neoforged.neoforge.fluids.capability.IFluidHandler.of(tankLiquidOut), Integer.MAX_VALUE, null, true
                );
                if (result.isSuccess()) {
                    containerSlots.setStackInSlot(2, result.getResult());
                }
            }
        }

        mjBattery.tick(level, worldPosition);

        currentRecipe = null;
        IRefineryRecipeManager manager = BuildcraftRecipeRegistry.refineryRecipes;
        if (manager != null) {
            FluidStack inFluid = tankIn.getResource(0).toStack(tankIn.getAmountAsInt(0));
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

            FluidResource resIn = tankIn.getResource(0);
            boolean canExtract = !resIn.isEmpty() 
                    && resIn.equals(FluidResource.of(reqIn)) 
                    && tankIn.getAmountAsInt(0) >= reqIn.getAmount();

            boolean canFillLiquid;
            try (Transaction tx = Transaction.openRoot()) {
                canFillLiquid = tankLiquidOut.insert(0, FluidResource.of(outLiquid), outLiquid.getAmount(), tx) >= outLiquid.getAmount();
            }
            boolean canFillGas;
            try (Transaction tx = Transaction.openRoot()) {
                canFillGas = tankGasOut.insert(0, FluidResource.of(outGas), outGas.getAmount(), tx) >= outGas.getAmount();
            }

            if (canExtract && canFillLiquid && canFillGas) {
                long max = MAX_MJ_PER_TICK;
                max *= mjBattery.getStored() + max;
                max /= mjBattery.getCapacity() / 2;
                max = Math.min(max, MAX_MJ_PER_TICK);
                long power = mjBattery.extractPower(0, max);
                // Feed into EWMA (alpha ≈ 0.05 for ~20-tick smoothing)
                powerAvgSmoothed += (long) ((max - powerAvgSmoothed) * 0.05);
                distillPower += power;
                isActive = power > 0;
                long powerReq = currentRecipe.powerRequired();
                if (distillPower >= powerReq) {
                    isActive = true;
                    distillPower -= powerReq;
                    try (Transaction tx = Transaction.openRoot()) {
                        tankIn.extract(0, FluidResource.of(reqIn), reqIn.getAmount(), tx);
                        tankGasOut.insert(0, FluidResource.of(outGas), outGas.getAmount(), tx);
                        tankLiquidOut.insert(0, FluidResource.of(outLiquid), outLiquid.getAmount(), tx);
                        tx.commit();
                    }
                }
            } else {
                mjBattery.addPowerChecking(distillPower, false);
                distillPower = 0;
                isActive = false;
            }
        }

        // When idle, decay the EWMA toward zero
        if (currentRecipe == null || !isActive) {
            powerAvgSmoothed += (long) ((0 - powerAvgSmoothed) * 0.05);
        }
        // Round to nearest 0.5 MJ before syncing (matches 1.12.2 rounding)
        // This prevents EWMA values just below a threshold (e.g. 1,999,999)
        // from causing the texture index to drop by one level.
        long halfMJ = MjAPI.MJ / 2; // 500,000
        powerAvgClient = Math.round(powerAvgSmoothed / (double) halfMJ) * halfMJ;
        powerAvgClient = Math.min(powerAvgClient, MAX_MJ_PER_TICK);

        // Send client sync when fluid amounts or active state change
        int curIn = tankIn.getAmountAsInt(0);
        int curGas = tankGasOut.getAmountAsInt(0);
        int curLiq = tankLiquidOut.getAmountAsInt(0);
        boolean needsSync = curIn != lastSyncedIn || curGas != lastSyncedGas || curLiq != lastSyncedLiquid
                || isActive != lastSyncedActive || powerAvgClient != lastSyncedPower;
        if (needsSync) {
            lastSyncedIn = curIn;
            lastSyncedGas = curGas;
            lastSyncedLiquid = curLiq;
            lastSyncedActive = isActive;
            lastSyncedPower = powerAvgClient;
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    // --- IDebuggable ---

    @Override
    public void getDebugInfo(java.util.List<String> left, java.util.List<String> right, Direction side) {
        // We use fluid stacks here to represent the contents
        left.add("In = " + buildcraft.lib.misc.FluidUtilBC.getDebugString(tankIn.getResource(0).toStack(tankIn.getAmountAsInt(0))));
        left.add("GasOut = " + buildcraft.lib.misc.FluidUtilBC.getDebugString(tankGasOut.getResource(0).toStack(tankGasOut.getAmountAsInt(0))));
        left.add("LiquidOut = " + buildcraft.lib.misc.FluidUtilBC.getDebugString(tankLiquidOut.getResource(0).toStack(tankLiquidOut.getAmountAsInt(0))));
        left.add("Battery = " + mjBattery.getDebugString());
        left.add("Progress = " + MjAPI.formatMj(distillPower));
        left.add("Rate = " + buildcraft.lib.misc.LocaleUtil.localizeMjFlow(powerAvgClient));
        left.add("CurrRecipe = " + currentRecipe);
    }

    @Override
    public void getClientDebugInfo(java.util.List<String> left, java.util.List<String> right, Direction side) {
        if (smoothIn != null) smoothIn.getDebugInfo(left, right, side);
        if (smoothGasOut != null) smoothGasOut.getDebugInfo(left, right, side);
        if (smoothLiquidOut != null) smoothLiquidOut.getDebugInfo(left, right, side);

        // Model Variables (matches 1.12.2 setClientModelVariables output)
        Direction facing = Direction.WEST;
        if (level != null) {
            BlockState state = level.getBlockState(worldPosition);
            if (state.hasProperty(buildcraft.factory.block.BlockDistiller.FACING)) {
                facing = state.getValue(buildcraft.factory.block.BlockDistiller.FACING);
            }
        }
        left.add("Model Variables:");
        left.add("  facing = " + facing);
        left.add("  active = " + isActive);
        left.add("  power_average = " + (powerAvgClient / MjAPI.MJ));
        left.add("  power_max = " + (MAX_MJ_PER_TICK / MjAPI.MJ));
        left.add("Current Model Variables:");
        left.add("  animState = " + String.format("%.4f", animState));
    }

    // --- MenuProvider ---

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.buildcraftunofficial.distiller");
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
        if (!tankIn.getResource(0).isEmpty()) {
            output.store("fluidIn", FluidStack.CODEC, tankIn.getResource(0).toStack(tankIn.getAmountAsInt(0)));
        }
        if (!tankGasOut.getResource(0).isEmpty()) {
            output.store("fluidGasOut", FluidStack.CODEC, tankGasOut.getResource(0).toStack(tankGasOut.getAmountAsInt(0)));
        }
        if (!tankLiquidOut.getResource(0).isEmpty()) {
            output.store("fluidLiquidOut", FluidStack.CODEC, tankLiquidOut.getResource(0).toStack(tankLiquidOut.getAmountAsInt(0)));
        }
        output.putLong("mjStored", mjBattery.getStored());
        output.putLong("distillPower", distillPower);
        output.putBoolean("isActive", isActive);
        output.putLong("powerAvgClient", powerAvgClient);
        output.putChild("containerSlots", containerSlots);
    }

    @Override
    public void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        FluidStack in = input.read("fluidIn", FluidStack.CODEC).orElse(FluidStack.EMPTY);
        FluidStack gas = input.read("fluidGasOut", FluidStack.CODEC).orElse(FluidStack.EMPTY);
        FluidStack liq = input.read("fluidLiquidOut", FluidStack.CODEC).orElse(FluidStack.EMPTY);
        try (Transaction tx = Transaction.openRoot()) {
            if (!in.isEmpty()) tankIn.insert(0, FluidResource.of(in), in.getAmount(), tx);
            if (!gas.isEmpty()) tankGasOut.insert(0, FluidResource.of(gas), gas.getAmount(), tx);
            if (!liq.isEmpty()) tankLiquidOut.insert(0, FluidResource.of(liq), liq.getAmount(), tx);
            tx.commit();
        }
        mjBattery.addPowerChecking(input.getLongOr("mjStored", 0L), false);
        distillPower = input.getLongOr("distillPower", 0L);
        isActive = input.getBooleanOr("isActive", false);
        powerAvgClient = input.getLongOr("powerAvgClient", 0L);
        input.readChild("containerSlots", containerSlots);
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

