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
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import buildcraft.api.enums.EnumPowerStage;
import buildcraft.api.fuels.BuildcraftFuelRegistry;
import buildcraft.api.fuels.IFuel;
import buildcraft.api.fuels.IFuelManager.IDirtyFuel;
import buildcraft.api.mj.IMjConnector;
import buildcraft.api.mj.MjAPI;

import buildcraft.energy.BCEnergyBlockEntities;
import buildcraft.lib.engine.EngineConnector;
import buildcraft.lib.engine.TileEngineBase_BC8;
import buildcraft.lib.misc.AdvancementUtil;

/**
 * Combustion engine (Iron Engine). Burns fluid fuels and requires fluid coolant.
 * Ported from 1.12.2 TileEngineIron_BC8.
 */
public class TileEngineIron_BC8 extends TileEngineBase_BC8 {
    private static final net.minecraft.resources.Identifier ADVANCEMENT_POWERING_UP
        = net.minecraft.resources.Identifier.parse("buildcraftunofficial:powering_up");
    private static final net.minecraft.resources.Identifier ADVANCEMENT_ICE_COOL
        = net.minecraft.resources.Identifier.parse("buildcraftunofficial:ice_cool");

    public static final int MAX_FLUID = 10_000;
    public static final double COOLDOWN_RATE = 0.05;
    public static final int MAX_COOLANT_PER_TICK = 40;

    // Heat constants (matching 1.12.2)
    public static final double HEAT_PER_MJ = 0.0023;
    public static final double IDEAL_HEAT = 204.0;

    public final FluidStacksResourceHandler tankFuel = new FluidStacksResourceHandler(1, MAX_FLUID) {
        @Override
        public boolean isValid(int index, FluidResource resource) {
            return isValidFuel(resource.toStack(1));
        }
    };
    public final FluidStacksResourceHandler tankCoolant = new FluidStacksResourceHandler(1, MAX_FLUID) {
        @Override
        public boolean isValid(int index, FluidResource resource) {
            return isValidCoolant(resource.toStack(1));
        }
    };
    public final FluidStacksResourceHandler tankResidue = new FluidStacksResourceHandler(1, MAX_FLUID) {
        @Override
        public boolean isValid(int index, FluidResource resource) {
            return isResidue(resource.toStack(1));
        }
    };

    private int penaltyCooling = 0;
    private boolean lastPowered = false;
    private double burnTime;
    private double residueAmount = 0;
    private IFuel currentFuel;

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
        FluidResource fuelres = tankFuel.getResource(0);
        return !fuelres.isEmpty() && tankFuel.getAmountAsLong(0) > 0 && penaltyCooling == 0 && isRedstonePowered;
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
        // Overheated engines stop consuming fuel and producing power/heat until cooled.
        // Coolant is still extracted via updateHeatLevel(), so combustion engines can
        // self-recover from overheat if coolant is available.
        if (getPowerStage() == EnumPowerStage.OVERHEAT) return;
        final FluidResource fuelRes = tankFuel.getResource(0);
        final FluidStack fuel = fuelRes.toStack((int) tankFuel.getAmountAsLong(0));
        if (currentFuel == null || currentFuel.getFluid().getFluid() != fuel.getFluid()) {
            currentFuel = BuildcraftFuelRegistry.fuel.getFuel(fuel);
        }

        if (fuelRes.isEmpty() || currentFuel == null) {
            return;
        }

        if (penaltyCooling <= 0) {
            if (isRedstonePowered) {
                lastPowered = true;
                if (getOwner() != null && level != null) {
                    AdvancementUtil.unlockAdvancement(getOwner().id(), level, ADVANCEMENT_POWERING_UP);
                }

                if (burnTime > 0 || fuel.getAmount() > 0) {
                    if (burnTime > 0) {
                        burnTime--;
                    }
                    if (burnTime <= 0) {
                        if (tankFuel.getAmountAsLong(0) > 0) {
                            try (Transaction tx = Transaction.openRoot()) {
                                tankFuel.extract(0, fuelRes, 1, tx);
                                tx.commit();
                            }
                            burnTime += currentFuel.getTotalBurningTime() / 1000.0;

                            // Produce residue for dirty fuels
                            if (currentFuel instanceof IDirtyFuel dirtyFuel) {
                                FluidStack residueFluid = dirtyFuel.getResidue().copy();
                                residueAmount += residueFluid.getAmount() / 1000.0;
                                if (residueAmount >= 1) {
                                    int residueInt = Mth.floor(residueAmount);
                                    try (Transaction tx = Transaction.openRoot()) {
                                        int filled = tankResidue.insert(0, FluidResource.of(residueFluid), residueInt, tx);
                                        if (filled > 0) tx.commit();
                                        residueAmount -= filled;
                                    }
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

        if (burnTime <= 0 && tankFuel.getAmountAsLong(0) <= 0) {
            try (Transaction tx = Transaction.openRoot()) {
                tankFuel.extract(0, tankFuel.getResource(0), MAX_FLUID, tx);
                tx.commit();
            }
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
                if (tankCoolant.getAmountAsLong(0) > 0) {
                    FluidResource coolRes = tankCoolant.getResource(0);
                    float coolPerMb = BuildcraftFuelRegistry.coolant.getDegreesPerMb(
                        coolRes.toStack(1), heat);
                    if (coolPerMb > 0) {
                        int coolantAmount = (int) Math.min(MAX_COOLANT_PER_TICK, tankCoolant.getAmountAsLong(0));
                        coolingBuffer += coolantAmount * coolPerMb;
                        try (Transaction tx = Transaction.openRoot()) {
                            tankCoolant.extract(0, coolRes, coolantAmount, tx);
                            tx.commit();
                        }
                        // Ice cool advancement: coolant that isn't water
                        if (!coolRes.isEmpty() && !coolRes.is(net.minecraft.world.level.material.Fluids.WATER)
                            && getOwner() != null && level != null) {
                            AdvancementUtil.unlockAdvancement(getOwner().id(), level, ADVANCEMENT_ICE_COOL);
                        }
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
    public final ResourceHandler<FluidResource> combinedFluidHandler = new ResourceHandler<FluidResource>() {
        @Override
        public int size() { return 3; }

        @Override
        public FluidResource getResource(int tank) {
            return switch (tank) {
                case 0 -> tankFuel.getResource(0);
                case 1 -> tankCoolant.getResource(0);
                case 2 -> tankResidue.getResource(0);
                default -> FluidResource.EMPTY;
            };
        }
        
        @Override
        public long getAmountAsLong(int tank) {
            return switch (tank) {
                case 0 -> tankFuel.getAmountAsLong(0);
                case 1 -> tankCoolant.getAmountAsLong(0);
                case 2 -> tankResidue.getAmountAsLong(0);
                default -> 0;
            };
        }

        @Override
        public long getCapacityAsLong(int tank, FluidResource resource) { return MAX_FLUID; }

        @Override
        public boolean isValid(int tank, FluidResource stack) {
            return switch (tank) {
                case 0 -> tankFuel.isValid(0, stack);
                case 1 -> tankCoolant.isValid(0, stack);
                default -> false;
            };
        }

        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext tx) {
            if (index == 0) return tankFuel.insert(0, resource, amount, tx);
            if (index == 1) return tankCoolant.insert(0, resource, amount, tx);
            return 0; // cannot insert to residue
        }

        @Override
        public int extract(int index, FluidResource resource, int amount, TransactionContext tx) {
            if (index == 2) return tankResidue.extract(0, resource, amount, tx);
            return 0; // only residue can be extracted
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
        if (!tankFuel.getResource(0).isEmpty()) {
            net.minecraft.resources.Identifier fuelId = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(tankFuel.getResource(0).getFluid());
            output.putString("fuelFluid", fuelId.toString());
            output.putInt("fuelAmount", (int) tankFuel.getAmountAsLong(0));
        }
        if (!tankCoolant.getResource(0).isEmpty()) {
            net.minecraft.resources.Identifier coolId = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(tankCoolant.getResource(0).getFluid());
            output.putString("coolantFluid", coolId.toString());
            output.putInt("coolantAmount", (int) tankCoolant.getAmountAsLong(0));
        }
        if (!tankResidue.getResource(0).isEmpty()) {
            net.minecraft.resources.Identifier resId = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(tankResidue.getResource(0).getFluid());
            output.putString("residueFluid", resId.toString());
            output.putInt("residueAmountTank", (int) tankResidue.getAmountAsLong(0));
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

    private void loadTank(ValueInput input, String fluidKey, String amountKey, FluidStacksResourceHandler tank) {
        String fluidId = input.getStringOr(fluidKey, "");
        if (!fluidId.isEmpty()) {
            net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(fluidId);
            if (id != null) {
                net.minecraft.world.level.material.Fluid fluid =
                    net.minecraft.core.registries.BuiltInRegistries.FLUID.getValue(id);
                if (fluid != null && fluid != net.minecraft.world.level.material.Fluids.EMPTY) {
                    int amount = input.getIntOr(amountKey, 0);
                    if (amount > 0) {
                        try (Transaction tx = Transaction.openRoot()) {
                            tank.insert(0, FluidResource.of(fluid), amount, tx);
                            tx.commit();
                        }
                    }
                }
            }
        }
    }
}
