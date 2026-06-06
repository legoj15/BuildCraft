/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.energy.container;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;

import buildcraft.api.enums.EnumPowerStage;
import buildcraft.energy.BCEnergyMenuTypes;
import buildcraft.energy.tile.TileEngineIron_BC8;
import buildcraft.lib.engine.TileEngineBase_BC8;
import buildcraft.lib.gui.ContainerBC_Neptune;
import buildcraft.lib.gui.widget.WidgetFluidTank;

/**
 * Container (menu) for the combustion engine GUI.
 * Syncs engine state + fluid tank levels to the client via ContainerData.
 */
@SuppressWarnings("this-escape")
public class ContainerEngineIron extends ContainerBC_Neptune {
    public final TileEngineIron_BC8 engine;
    private final ContainerData data;

    // Widget indices (in registration order) for screen to reference
    public final WidgetFluidTank widgetFuel;
    public final WidgetFluidTank widgetCoolant;
    public final WidgetFluidTank widgetResidue;

    // Data slot indices
    private static final int DATA_POWER_HI = 0;
    private static final int DATA_POWER_LO = 1;
    private static final int DATA_HEAT = 2;
    private static final int DATA_POWER_STAGE = 3;
    private static final int DATA_BURNING = 4;
    private static final int DATA_CURRENT_OUTPUT_HI = 5;
    private static final int DATA_CURRENT_OUTPUT_LO = 6;
    private static final int DATA_FUEL_AMOUNT = 7;
    private static final int DATA_COOLANT_AMOUNT = 8;
    private static final int DATA_RESIDUE_AMOUNT = 9;
    private static final int DATA_FUEL_FLUID_ID = 10;
    private static final int DATA_COOLANT_FLUID_ID = 11;
    private static final int DATA_RESIDUE_FLUID_ID = 12;
    private static final int DATA_COUNT = 13;

    /** Server constructor */
    public ContainerEngineIron(int containerId, Inventory playerInv, TileEngineIron_BC8 engine) {
        super(BCEnergyMenuTypes.ENGINE_IRON.get(), containerId, playerInv.player);
        this.engine = engine;

        // Server side: read live values from the tile entity
        // Client side: use SimpleContainerData so set() actually stores values
        if (engine != null && engine.getLevel() != null && !engine.getLevel().isClientSide()) {
            this.data = new ContainerData() {
                @Override
                public int get(int index) {
                    return switch (index) {
                        case DATA_POWER_HI -> (int) (engine.getPower() >>> 32);
                        case DATA_POWER_LO -> (int) (engine.getPower() & 0xFFFFFFFFL);
                        case DATA_HEAT -> Float.floatToIntBits(engine.getHeat());
                        case DATA_POWER_STAGE -> engine.getPowerStage().ordinal();
                        case DATA_BURNING -> engine.isBurning() ? 1 : 0;
                        case DATA_CURRENT_OUTPUT_HI -> (int) (engine.currentOutput >>> 32);
                        case DATA_CURRENT_OUTPUT_LO -> (int) (engine.currentOutput & 0xFFFFFFFFL);
                        //? if >=1.21.10 {
                        case DATA_FUEL_AMOUNT -> (int) engine.tankFuel.getAmountAsLong(0);
                        case DATA_COOLANT_AMOUNT -> (int) engine.tankCoolant.getAmountAsLong(0);
                        case DATA_RESIDUE_AMOUNT -> (int) engine.tankResidue.getAmountAsLong(0);
                        case DATA_FUEL_FLUID_ID -> getFluidRegistryId(engine.tankFuel.getResource(0));
                        case DATA_COOLANT_FLUID_ID -> getFluidRegistryId(engine.tankCoolant.getResource(0));
                        case DATA_RESIDUE_FLUID_ID -> getFluidRegistryId(engine.tankResidue.getResource(0));
                        //?} else {
                        /*case DATA_FUEL_AMOUNT -> engine.tankFuel.getFluidInTank(0).getAmount();
                        case DATA_COOLANT_AMOUNT -> engine.tankCoolant.getFluidInTank(0).getAmount();
                        case DATA_RESIDUE_AMOUNT -> engine.tankResidue.getFluidInTank(0).getAmount();
                        case DATA_FUEL_FLUID_ID -> getFluidRegistryId(engine.tankFuel.getFluidInTank(0));
                        case DATA_COOLANT_FLUID_ID -> getFluidRegistryId(engine.tankCoolant.getFluidInTank(0));
                        case DATA_RESIDUE_FLUID_ID -> getFluidRegistryId(engine.tankResidue.getFluidInTank(0));*/
                        //?}
                        default -> 0;
                    };
                }

                @Override
                public void set(int index, int value) {
                    // Server-side: no-op (values are read from engine directly)
                }

                @Override
                public int getCount() {
                    return DATA_COUNT;
                }
            };
        } else {
            // Client-side: SimpleContainerData stores values written by sync packets
            SimpleContainerData clientData = new SimpleContainerData(DATA_COUNT);
            clientData.set(DATA_HEAT, Float.floatToIntBits(TileEngineBase_BC8.MIN_HEAT));
            this.data = clientData;
        }

        addDataSlots(this.data);

        // Player inventory — positioned at standard location
        addFullPlayerInventory(8, 95);

        // Register fluid tank widgets for in-GUI bucket interaction
        widgetFuel = addWidget(new WidgetFluidTank(this, engine.tankFuel));
        widgetCoolant = addWidget(new WidgetFluidTank(this, engine.tankCoolant));
        widgetResidue = addWidget(new WidgetFluidTank(this, engine.tankResidue));
    }

    /** Client constructor (from network buffer) */
    public ContainerEngineIron(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv, getTile(playerInv, buf));
    }

    private static TileEngineIron_BC8 getTile(Inventory playerInv, FriendlyByteBuf buf) {
        var pos = buf.readBlockPos();
        var level = playerInv.player.level();
        var be = level.getBlockEntity(pos);
        if (be instanceof TileEngineIron_BC8 engine) {
            return engine;
        }
        throw new IllegalStateException("Expected TileEngineIron_BC8 at " + pos);
    }

    // --- Data accessors for the screen ---

    public long getSyncedPower() {
        return ((long) data.get(DATA_POWER_HI) << 32) | (data.get(DATA_POWER_LO) & 0xFFFFFFFFL);
    }

    public float getSyncedHeat() {
        return Float.intBitsToFloat(data.get(DATA_HEAT));
    }

    public EnumPowerStage getSyncedPowerStage() {
        int ord = data.get(DATA_POWER_STAGE);
        EnumPowerStage[] values = EnumPowerStage.VALUES;
        return values[Math.min(ord, values.length - 1)];
    }

    public boolean isSyncedBurning() {
        return data.get(DATA_BURNING) != 0;
    }

    public long getSyncedCurrentOutput() {
        return ((long) data.get(DATA_CURRENT_OUTPUT_HI) << 32) | (data.get(DATA_CURRENT_OUTPUT_LO) & 0xFFFFFFFFL);
    }

    public int getSyncedFuelAmount() {
        return data.get(DATA_FUEL_AMOUNT);
    }

    public int getSyncedCoolantAmount() {
        return data.get(DATA_COOLANT_AMOUNT);
    }

    public int getSyncedResidueAmount() {
        return data.get(DATA_RESIDUE_AMOUNT);
    }

    public Fluid getSyncedFuelFluid() {
        return getFluidFromRegistryId(data.get(DATA_FUEL_FLUID_ID));
    }

    public Fluid getSyncedCoolantFluid() {
        return getFluidFromRegistryId(data.get(DATA_COOLANT_FLUID_ID));
    }

    public Fluid getSyncedResidueFluid() {
        return getFluidFromRegistryId(data.get(DATA_RESIDUE_FLUID_ID));
    }

    //? if >=1.21.10 {
    private static int getFluidRegistryId(net.neoforged.neoforge.transfer.fluid.FluidResource resource) {
    //?} else {
    /*private static int getFluidRegistryId(net.neoforged.neoforge.fluids.FluidStack resource) {*/
    //?}
        if (resource.isEmpty()) return -1;
        return BuiltInRegistries.FLUID.getId(resource.getFluid());
    }

    private static Fluid getFluidFromRegistryId(int id) {
        if (id < 0) return Fluids.EMPTY;
        Fluid fluid = BuiltInRegistries.FLUID.byId(id);
        return fluid != null ? fluid : Fluids.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        if (engine == null || engine.isRemoved()) return false;
        return player.distanceToSqr(
            engine.getBlockPos().getX() + 0.5,
            engine.getBlockPos().getY() + 0.5,
            engine.getBlockPos().getZ() + 0.5
        ) <= 64.0;
    }
}
