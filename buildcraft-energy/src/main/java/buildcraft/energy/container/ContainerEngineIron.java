/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.energy.container;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;

import buildcraft.api.enums.EnumPowerStage;
import buildcraft.energy.BCEnergyMenuTypes;
import buildcraft.energy.tile.TileEngineIron_BC8;
import buildcraft.lib.gui.ContainerBC_Neptune;

/**
 * Container (menu) for the combustion engine GUI.
 * Syncs engine state + fluid tank levels to the client via ContainerData.
 */
public class ContainerEngineIron extends ContainerBC_Neptune {
    public final TileEngineIron_BC8 engine;
    private final ContainerData data;

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
    private static final int DATA_COUNT = 10;

    /** Server constructor */
    public ContainerEngineIron(int containerId, Inventory playerInv, TileEngineIron_BC8 engine) {
        super(BCEnergyMenuTypes.ENGINE_IRON.get(), containerId, playerInv.player);
        this.engine = engine;

        this.data = new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case DATA_POWER_HI -> (int) (engine.getPower() >> 32);
                    case DATA_POWER_LO -> (int) (engine.getPower() & 0xFFFFFFFFL);
                    case DATA_HEAT -> Float.floatToIntBits(engine.getHeat());
                    case DATA_POWER_STAGE -> engine.getPowerStage().ordinal();
                    case DATA_BURNING -> engine.isBurning() ? 1 : 0;
                    case DATA_CURRENT_OUTPUT_HI -> (int) (engine.getCurrentOutput() >> 32);
                    case DATA_CURRENT_OUTPUT_LO -> (int) (engine.getCurrentOutput() & 0xFFFFFFFFL);
                    case DATA_FUEL_AMOUNT -> engine.tankFuel.getFluidAmount();
                    case DATA_COOLANT_AMOUNT -> engine.tankCoolant.getFluidAmount();
                    case DATA_RESIDUE_AMOUNT -> engine.tankResidue.getFluidAmount();
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                // Server→client sync only
            }

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        };

        addDataSlots(data);

        // Player inventory — positioned at standard location
        addFullPlayerInventory(8, 95);
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
