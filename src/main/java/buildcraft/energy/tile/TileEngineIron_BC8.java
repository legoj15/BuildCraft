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

import net.neoforged.neoforge.fluids.FluidStack;
//? if >=1.21.10 {
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
//?} else {
/*import net.neoforged.neoforge.fluids.capability.IFluidHandler;*/
//?}

import buildcraft.api.enums.EnumPowerStage;
import buildcraft.api.fuels.BuildcraftFuelRegistry;
import buildcraft.api.fuels.IFuel;
import buildcraft.api.fuels.IFuelManager.IDirtyFuel;
import buildcraft.api.mj.IMjConnector;
import buildcraft.api.mj.MjAPI;

import buildcraft.energy.BCEnergyBlockEntities;
import buildcraft.lib.engine.EngineConnector;
import buildcraft.lib.engine.TileEngineBase_BC8;
import buildcraft.lib.fluid.BCFluidTank;
import buildcraft.lib.misc.AdvancementUtil;
import buildcraft.lib.misc.BCValueInput;
import buildcraft.lib.misc.BCValueOutput;
import buildcraft.lib.misc.GameProfileUtil;

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

    public final BCFluidTank tankFuel = new BCFluidTank(1, MAX_FLUID) {
        @Override
        protected boolean isFluidValid(FluidStack stack) {
            return isValidFuel(stack);
        }
    };
    public final BCFluidTank tankCoolant = new BCFluidTank(1, MAX_FLUID) {
        @Override
        protected boolean isFluidValid(FluidStack stack) {
            return isValidCoolant(stack);
        }
    };
    public final BCFluidTank tankResidue = new BCFluidTank(1, MAX_FLUID) {
        @Override
        protected boolean isFluidValid(FluidStack stack) {
            return isResidue(stack);
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
        return !tankFuel.isTankEmpty(0) && tankFuel.getAmountMb(0) > 0 && penaltyCooling == 0 && isRedstonePowered;
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
        final FluidStack fuel = tankFuel.getFluidStack(0);
        if (currentFuel == null || currentFuel.getFluid().getFluid() != fuel.getFluid()) {
            currentFuel = BuildcraftFuelRegistry.fuel.getFuel(fuel);
        }

        if (tankFuel.isTankEmpty(0) || currentFuel == null) {
            return;
        }

        if (penaltyCooling <= 0) {
            if (isRedstonePowered) {
                lastPowered = true;
                if (getOwner() != null && level != null) {
                    AdvancementUtil.unlockAdvancement(GameProfileUtil.getId(getOwner()), level, ADVANCEMENT_POWERING_UP);
                }

                if (burnTime > 0 || fuel.getAmount() > 0) {
                    if (burnTime > 0) {
                        burnTime--;
                    }
                    if (burnTime <= 0) {
                        if (tankFuel.getAmountMb(0) > 0) {
                            tankFuel.drain(0, 1, false);
                            burnTime += currentFuel.getTotalBurningTime() / 1000.0;

                            // Produce residue for dirty fuels
                            if (currentFuel instanceof IDirtyFuel dirtyFuel) {
                                FluidStack residueFluid = dirtyFuel.getResidue().copy();
                                residueAmount += residueFluid.getAmount() / 1000.0;
                                if (residueAmount >= 1) {
                                    int residueInt = Mth.floor(residueAmount);
                                    int filled = tankResidue.fill(0, residueFluid.copyWithAmount(residueInt), false);
                                    residueAmount -= filled;
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

        if (burnTime <= 0 && tankFuel.getAmountMb(0) <= 0) {
            tankFuel.drain(0, MAX_FLUID, false);
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
                if (tankCoolant.getAmountMb(0) > 0) {
                    FluidStack coolFluid = tankCoolant.getFluidStack(0);
                    float coolPerMb = BuildcraftFuelRegistry.coolant.getDegreesPerMb(
                        coolFluid.copyWithAmount(1), heat);
                    if (coolPerMb > 0) {
                        int coolantAmount = (int) Math.min(MAX_COOLANT_PER_TICK, tankCoolant.getAmountMb(0));
                        coolingBuffer += coolantAmount * coolPerMb;
                        tankCoolant.drain(0, coolantAmount, false);
                        // Ice cool advancement: coolant that isn't water
                        if (!coolFluid.isEmpty()
                            && coolFluid.getFluid() != net.minecraft.world.level.material.Fluids.WATER
                            && getOwner() != null && level != null) {
                            AdvancementUtil.unlockAdvancement(GameProfileUtil.getId(getOwner()), level, ADVANCEMENT_ICE_COOL);
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

    /** Combustion engine stalls power output during its {@code penaltyCooling} window (1.12.2 parity). */
    @Override
    public boolean isActive() {
        return penaltyCooling <= 0;
    }

    /**
     * MUST sit above the powered coolant equilibrium: while redstone-powered, {@link #updateHeatLevel()}
     * only cools toward IDEAL_HEAT (204 °C = heatLevel 0.80 exactly), and the per-tick coolant
     * overshoot is bounded by 40 mB x 0.0023 °C/mB = 0.092 °C — so the base 0.75 exit would be
     * unreachable and a running water-cooled engine would stay OVERHEAT forever. 0.84 exits ~25-50
     * ticks after coolant catches up, then settles at the 0.80 equilibrium (RED) without re-entry.
     */
    @Override
    protected float overheatExitLevel() {
        return 0.84f;
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
    //? if >=1.21.10 {
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
    //?} else {
    /*public final IFluidHandler combinedFluidHandler = new IFluidHandler() {
        @Override
        public int getTanks() { return 3; }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return switch (tank) {
                case 0 -> tankFuel.getFluidStack(0);
                case 1 -> tankCoolant.getFluidStack(0);
                case 2 -> tankResidue.getFluidStack(0);
                default -> FluidStack.EMPTY;
            };
        }

        @Override
        public int getTankCapacity(int tank) {
            return switch (tank) {
                case 0 -> tankFuel.getCapacityMb(0);
                case 1 -> tankCoolant.getCapacityMb(0);
                case 2 -> tankResidue.getCapacityMb(0);
                default -> 0;
            };
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return switch (tank) {
                case 0 -> tankFuel.isFluidValid(0, stack);
                case 1 -> tankCoolant.isFluidValid(0, stack);
                default -> false;
            };
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            boolean simulate = action.simulate();
            int filled = tankFuel.fill(0, resource, simulate);
            if (filled > 0) return filled;
            return tankCoolant.fill(0, resource, simulate);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            FluidStack inResidue = tankResidue.getFluidStack(0);
            if (!inResidue.isEmpty() && FluidStack.isSameFluidSameComponents(inResidue, resource)) {
                return tankResidue.drain(0, resource.getAmount(), action.simulate());
            }
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return tankResidue.drain(0, maxDrain, action.simulate());
        }
    };*/
    //?}


    // --- NBT ---

    @Override
    protected void writeData(BCValueOutput output) {
        super.writeData(output);
        output.putInt("penaltyCooling", penaltyCooling);
        output.putDouble("burnTime", burnTime);
        output.putDouble("residueAmount", residueAmount);

        // Save fluid tanks
        if (!tankFuel.isTankEmpty(0)) {
            net.minecraft.resources.Identifier fuelId = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(tankFuel.getFluidStack(0).getFluid());
            output.putString("fuelFluid", fuelId.toString());
            output.putInt("fuelAmount", tankFuel.getAmountMb(0));
        }
        if (!tankCoolant.isTankEmpty(0)) {
            net.minecraft.resources.Identifier coolId = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(tankCoolant.getFluidStack(0).getFluid());
            output.putString("coolantFluid", coolId.toString());
            output.putInt("coolantAmount", tankCoolant.getAmountMb(0));
        }
        if (!tankResidue.isTankEmpty(0)) {
            net.minecraft.resources.Identifier resId = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(tankResidue.getFluidStack(0).getFluid());
            output.putString("residueFluid", resId.toString());
            output.putInt("residueAmountTank", tankResidue.getAmountMb(0));
        }
    }

    @Override
    protected void readData(BCValueInput input) {
        super.readData(input);
        penaltyCooling = input.getIntOr("penaltyCooling", 0);
        burnTime = input.getDoubleOr("burnTime", 0);
        residueAmount = Math.max(0, input.getDoubleOr("residueAmount", 0));

        // Load fluid tanks
        loadTank(input, "fuelFluid", "fuelAmount", tankFuel);
        loadTank(input, "coolantFluid", "coolantAmount", tankCoolant);
        loadTank(input, "residueFluid", "residueAmountTank", tankResidue);
    }

    private void loadTank(BCValueInput input, String fluidKey, String amountKey, BCFluidTank tank) {
        String fluidId = input.getStringOr(fluidKey, "");
        if (!fluidId.isEmpty()) {
            net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(fluidId);
            if (id != null) {
                net.minecraft.world.level.material.Fluid fluid =
                    buildcraft.lib.misc.RegistryUtilBC.getValue(net.minecraft.core.registries.BuiltInRegistries.FLUID, id);
                if (fluid != null && fluid != net.minecraft.world.level.material.Fluids.EMPTY) {
                    int amount = input.getIntOr(amountKey, 0);
                    if (amount > 0) {
                        tank.fill(0, new FluidStack(fluid, amount), false);
                    }
                }
            }
        }
    }
}
