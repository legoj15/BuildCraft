/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.energy.tile;

import javax.annotation.Nonnull;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import buildcraft.api.enums.EnumPowerStage;
import buildcraft.api.fuels.BuildcraftFuelRegistry;
import buildcraft.api.fuels.IFuel;
import buildcraft.api.fuels.IFuelManager.IDirtyFuel;
import buildcraft.api.mj.IMjConnector;
import buildcraft.api.mj.MjAPI;

import buildcraft.energy.BCEnergyBlockEntities;
import buildcraft.lib.engine.EngineConnector;
import buildcraft.lib.engine.TileEngineBase_BC8;

/**
 * Combustion engine (Iron Engine). Burns fluid fuels and requires fluid coolant.
 * Ported from 1.12.2 TileEngineIron_BC8.
 */
public class TileEngineIron_BC8 extends TileEngineBase_BC8 {
    public static final int MAX_FLUID = 10_000;
    public static final double COOLDOWN_RATE = 0.05;
    public static final int MAX_COOLANT_PER_TICK = 40;

    // Heat constants (matching 1.12.2)
    public static final double HEAT_PER_MJ = 0.0023;
    public static final double IDEAL_HEAT = 204.0;

    public final FluidTank tankFuel = new FluidTank(MAX_FLUID) {
        @Override
        public boolean isFluidValid(FluidStack stack) {
            return isValidFuel(stack);
        }
    };
    public final FluidTank tankCoolant = new FluidTank(MAX_FLUID) {
        @Override
        public boolean isFluidValid(FluidStack stack) {
            return isValidCoolant(stack);
        }
    };
    public final FluidTank tankResidue = new FluidTank(MAX_FLUID) {
        @Override
        public boolean isFluidValid(FluidStack stack) {
            return isResidue(stack);
        }
    };

    private int penaltyCooling = 0;
    private boolean lastPowered = false;
    private double burnTime;
    private double residueAmount = 0;
    private IFuel currentFuel;
    public long currentOutput = 0;

    public TileEngineIron_BC8(BlockPos pos, BlockState state) {
        super(BCEnergyBlockEntities.ENGINE_IRON.get(), pos, state);
    }

    // --- Engine overrides ---

    @Nonnull
    @Override
    protected IMjConnector createConnector() {
        return new EngineConnector(false);
    }

    @Override
    public boolean isBurning() {
        FluidStack fuel = tankFuel.getFluid();
        return !fuel.isEmpty() && fuel.getAmount() > 0 && penaltyCooling == 0 && isRedstonePowered;
    }

    @Override
    protected void engineUpdate() {
        burn();

        // Combustion engine has its own heat management, so we override the
        // base class updateHeatLevel by doing cooling here
        updateHeatLevel();
    }

    /** Core burn logic — ported from 1.12.2 TileEngineIron_BC8.burn() */
    protected void burn() {
        final FluidStack fuel = this.tankFuel.getFluid();
        if (currentFuel == null || !FluidStack.isSameFluid(currentFuel.getFluid(), fuel)) {
            currentFuel = BuildcraftFuelRegistry.fuel.getFuel(fuel);
        }

        if (fuel.isEmpty() || currentFuel == null) {
            return;
        }

        if (penaltyCooling <= 0) {
            if (isRedstonePowered) {
                lastPowered = true;

                if (burnTime > 0 || fuel.getAmount() > 0) {
                    if (burnTime > 0) {
                        burnTime--;
                    }
                    if (burnTime <= 0) {
                        if (fuel.getAmount() > 0) {
                            tankFuel.drain(1, IFluidHandler.FluidAction.EXECUTE);
                            burnTime += currentFuel.getTotalBurningTime() / 1000.0;

                            // Produce residue for dirty fuels
                            if (currentFuel instanceof IDirtyFuel dirtyFuel) {
                                FluidStack residueFluid = dirtyFuel.getResidue().copy();
                                residueAmount += residueFluid.getAmount() / 1000.0;
                                if (residueAmount >= 1) {
                                    int residueInt = Mth.floor(residueAmount);
                                    FluidStack toFill = new FluidStack(residueFluid.getFluid(), residueInt);
                                    int filled = tankResidue.fill(toFill, IFluidHandler.FluidAction.EXECUTE);
                                    residueAmount -= filled;
                                } else if (tankResidue.getFluid().isEmpty()) {
                                    // Prime the tank with 0-amount so it knows what fluid type it holds
                                    // (NeoForge FluidTank doesn't support 0-amount, so we skip this)
                                }
                            }
                        } else {
                            // No more fuel
                            currentFuel = null;
                            currentOutput = 0;
                            return;
                        }
                    }
                    addPower(currentFuel.getPowerPerCycle());
                    heat += (float) (currentFuel.getPowerPerCycle() * HEAT_PER_MJ / MjAPI.MJ);
                }
            } else if (lastPowered) {
                lastPowered = false;
                penaltyCooling = 10; // 10 tick penalty on top of cooling
            }
        }

        if (burnTime <= 0 && fuel.getAmount() <= 0) {
            tankFuel.drain(MAX_FLUID, IFluidHandler.FluidAction.EXECUTE); // empty the tank
        }
    }

    @Override
    public void updateHeatLevel() {
        // Combustion engine has custom heat management — NOT based on power ratio
        double target;
        if (heat > MIN_HEAT && (penaltyCooling > 0 || !isRedstonePowered)) {
            heat -= (float) COOLDOWN_RATE;
            target = MIN_HEAT;
        } else if (heat > IDEAL_HEAT) {
            target = IDEAL_HEAT;
        } else {
            target = heat;
        }

        if (target != heat) {
            // Cool the engine using coolant
            double coolingBuffer = 0;
            double extraHeat = heat - target;

            if (extraHeat > 0) {
                if (tankCoolant.getFluidAmount() > 0) {
                    float coolPerMb = BuildcraftFuelRegistry.coolant.getDegreesPerMb(
                        tankCoolant.getFluid(), heat);
                    if (coolPerMb > 0) {
                        int coolantAmount = Math.min(MAX_COOLANT_PER_TICK, tankCoolant.getFluidAmount());
                        coolingBuffer += coolantAmount * coolPerMb;
                        tankCoolant.drain(coolantAmount, IFluidHandler.FluidAction.EXECUTE);
                    }
                }
            }

            heat -= (float) coolingBuffer;
            getPowerStage();
        }

        if (heat <= MIN_HEAT && penaltyCooling > 0) {
            penaltyCooling--;
        }

        if (heat <= MIN_HEAT) {
            heat = MIN_HEAT;
        }
    }

    @Override
    public double getPistonSpeed() {
        switch (getPowerStage()) {
            case BLUE:
                return 0.04;
            case GREEN:
                return 0.05;
            case YELLOW:
                return 0.06;
            case RED:
                return 0.07;
            default:
                return 0;
        }
    }

    public boolean isActive() {
        return penaltyCooling <= 0;
    }

    @Override
    public long getMaxPower() {
        return 10_000 * MjAPI.MJ;
    }

    @Override
    public long maxPowerReceived() {
        return 2_000 * MjAPI.MJ;
    }

    @Override
    public long maxPowerExtracted() {
        return 500 * MjAPI.MJ;
    }

    @Override
    public float explosionRange() {
        return 4;
    }

    @Override
    protected int getMaxChainLength() {
        return 4;
    }

    @Override
    public long getCurrentOutput() {
        if (currentFuel == null) {
            return 0;
        }
        return currentFuel.getPowerPerCycle();
    }

    @Override
    public long minPowerReceived() {
        return MjAPI.MJ;
    }

    /** Add power to the engine's internal buffer. */
    protected void addPower(long microMj) {
        power = Math.min(power + microMj, getMaxPower());
    }

    // --- Fluid validation ---

    private boolean isValidFuel(FluidStack fluid) {
        return BuildcraftFuelRegistry.fuel != null
            && BuildcraftFuelRegistry.fuel.getFuel(fluid) != null;
    }

    private boolean isValidCoolant(FluidStack fluid) {
        return BuildcraftFuelRegistry.coolant != null
            && BuildcraftFuelRegistry.coolant.getCoolant(fluid) != null;
    }

    private boolean isResidue(FluidStack fluid) {
        // On the client, trust the server
        if (level != null && level.isClientSide()) {
            return true;
        }
        if (currentFuel instanceof IDirtyFuel dirtyFuel) {
            return FluidStack.isSameFluid(fluid, dirtyFuel.getResidue());
        }
        return false;
    }

    // --- Combined IFluidHandler for capability exposure ---

    /**
     * Combined handler exposing 3 tanks: fuel (0), coolant (1), residue (2).
     * Fuel and coolant tanks accept fills; residue tank allows drains.
     */
    public final IFluidHandler combinedFluidHandler = new IFluidHandler() {
        @Override
        public int getTanks() { return 3; }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return switch (tank) {
                case 0 -> tankFuel.getFluid();
                case 1 -> tankCoolant.getFluid();
                case 2 -> tankResidue.getFluid();
                default -> FluidStack.EMPTY;
            };
        }

        @Override
        public int getTankCapacity(int tank) { return MAX_FLUID; }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return switch (tank) {
                case 0 -> isValidFuel(stack);
                case 1 -> isValidCoolant(stack);
                default -> false;
            };
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            // Try fuel first, then coolant
            int filled = tankFuel.fill(resource, action);
            if (filled == 0) {
                filled = tankCoolant.fill(resource, action);
            }
            if (filled > 0 && action.execute()) setChanged();
            return filled;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            // Try draining from all tanks: fuel, coolant, residue
            FluidStack drained = tankFuel.drain(resource, action);
            if (drained.isEmpty()) {
                drained = tankCoolant.drain(resource, action);
            }
            if (drained.isEmpty()) {
                drained = tankResidue.drain(resource, action);
            }
            if (!drained.isEmpty() && action.execute()) setChanged();
            return drained;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            // Try draining from all tanks: fuel, coolant, residue
            FluidStack drained = tankFuel.drain(maxDrain, action);
            if (drained.isEmpty()) {
                drained = tankCoolant.drain(maxDrain, action);
            }
            if (drained.isEmpty()) {
                drained = tankResidue.drain(maxDrain, action);
            }
            if (!drained.isEmpty() && action.execute()) setChanged();
            return drained;
        }
    };


    // --- NBT ---

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("penaltyCooling", penaltyCooling);
        output.putDouble("burnTime", burnTime);
        output.putDouble("residueAmount", residueAmount);

        // Save fluid tanks
        if (!tankFuel.getFluid().isEmpty()) {
            net.minecraft.resources.Identifier fuelId = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(tankFuel.getFluid().getFluid());
            output.putString("fuelFluid", fuelId.toString());
            output.putInt("fuelAmount", tankFuel.getFluidAmount());
        }
        if (!tankCoolant.getFluid().isEmpty()) {
            net.minecraft.resources.Identifier coolId = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(tankCoolant.getFluid().getFluid());
            output.putString("coolantFluid", coolId.toString());
            output.putInt("coolantAmount", tankCoolant.getFluidAmount());
        }
        if (!tankResidue.getFluid().isEmpty()) {
            net.minecraft.resources.Identifier resId = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(tankResidue.getFluid().getFluid());
            output.putString("residueFluid", resId.toString());
            output.putInt("residueAmountTank", tankResidue.getFluidAmount());
        }
    }

    @Override
    public void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        penaltyCooling = input.getIntOr("penaltyCooling", 0);
        burnTime = input.getDoubleOr("burnTime", 0);
        residueAmount = Math.max(0, input.getDoubleOr("residueAmount", 0));

        // Load fluid tanks
        loadTank(input, "fuelFluid", "fuelAmount", tankFuel);
        loadTank(input, "coolantFluid", "coolantAmount", tankCoolant);
        loadTank(input, "residueFluid", "residueAmountTank", tankResidue);
    }

    private void loadTank(ValueInput input, String fluidKey, String amountKey, FluidTank tank) {
        String fluidId = input.getStringOr(fluidKey, "");
        if (!fluidId.isEmpty()) {
            net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(fluidId);
            if (id != null) {
                net.minecraft.world.level.material.Fluid fluid =
                    net.minecraft.core.registries.BuiltInRegistries.FLUID.getValue(id);
                if (fluid != null && fluid != net.minecraft.world.level.material.Fluids.EMPTY) {
                    int amount = input.getIntOr(amountKey, 0);
                    if (amount > 0) {
                        tank.setFluid(new FluidStack(fluid, amount));
                    }
                }
            }
        }
    }
}
