/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.energy.tile;

import javax.annotation.Nonnull;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import buildcraft.api.enums.EnumPowerStage;
import buildcraft.api.mj.IMjConnector;
import buildcraft.api.mj.MjAPI;

import buildcraft.energy.BCEnergyBlockEntities;
import buildcraft.lib.engine.EngineConnector;
import buildcraft.lib.engine.TileEngineBase_BC8;

public class TileEngineStone_BC8 extends TileEngineBase_BC8 {
    private static final long MAX_OUTPUT = MjAPI.MJ;
    private static final long MIN_OUTPUT = MAX_OUTPUT / 3;
    private static final long eLimit = (MAX_OUTPUT - MIN_OUTPUT) * 20;

    // --- Fuel state ---
    public int burnTime = 0;
    public int totalBurnTime = 0;
    private long esum = 0;

    // Fuel slot: single ItemStack managed manually
    private ItemStack fuelStack = ItemStack.EMPTY;

    public TileEngineStone_BC8(BlockPos pos, BlockState state) {
        super(BCEnergyBlockEntities.ENGINE_STONE.get(), pos, state);
    }

    // --- Fuel slot accessors (for container data sync) ---

    @Nonnull
    public ItemStack getFuelStack() {
        return fuelStack;
    }

    public void setFuelStack(@Nonnull ItemStack stack) {
        fuelStack = stack;
        setChanged();
    }

    public boolean isValidFuel(@Nonnull ItemStack stack) {
        return getBurnTime(stack) > 0;
    }

    private int getBurnTime(@Nonnull ItemStack stack) {
        if (stack.isEmpty() || level == null) return 0;
        return stack.getBurnTime(null, level.fuelValues());
    }

    // --- Engine overrides ---

    @Nonnull
    @Override
    protected IMjConnector createConnector() {
        return new EngineConnector(false);
    }

    @Override
    public boolean isBurning() {
        return burnTime > 0;
    }

    @Override
    protected void engineUpdate() {
        // Burn fuel
        if (burnTime > 0) {
            burnTime--;
            if (getPowerStage() != EnumPowerStage.OVERHEAT) {
                long output = getCurrentOutput();
                addPower(output);

                // Heat up while burning (matches 1.12 behavior)
                heat = Math.min(heat + 0.2f, MAX_HEAT);
            }
        }

        // Passive cooling towards MIN_HEAT
        if (heat > MIN_HEAT) {
            heat -= 0.1f;
            if (heat < MIN_HEAT) heat = MIN_HEAT;
        }

        // Try to consume new fuel
        if (burnTime == 0 && isRedstonePowered) {
            int newBurn = getBurnTime(fuelStack);
            if (newBurn > 0) {
                burnTime = newBurn;
                totalBurnTime = newBurn;

                // Consume one fuel item
                ItemStack consumed = fuelStack.copy();
                consumed.setCount(1);

                fuelStack.shrink(1);
                if (fuelStack.isEmpty()) {
                    fuelStack = ItemStack.EMPTY;
                }

                // Handle container items (e.g., empty bucket from lava bucket)
                ItemStack container = consumed.getCraftingRemainder();
                if (!container.isEmpty()) {
                    if (fuelStack.isEmpty()) {
                        fuelStack = container;
                    } else {
                        // Drop the container item — no room
                        if (level != null) {
                            net.minecraft.world.entity.item.ItemEntity entity = new net.minecraft.world.entity.item.ItemEntity(
                                level,
                                getBlockPos().getX() + 0.5,
                                getBlockPos().getY() + 1.0,
                                getBlockPos().getZ() + 0.5,
                                container
                            );
                            level.addFreshEntity(entity);
                        }
                    }
                }
                setChanged();
            }
        }
    }

    /** Add power to the engine's internal buffer. */
    protected void addPower(long microMj) {
        power = Math.min(power + microMj, getMaxPower());
    }

    @Override
    public long maxPowerReceived() {
        return 200 * MjAPI.MJ;
    }

    @Override
    public long maxPowerExtracted() {
        return 100 * MjAPI.MJ;
    }

    @Override
    public long getMaxPower() {
        return 1000 * MjAPI.MJ;
    }

    @Override
    public float explosionRange() {
        return 2;
    }

    @Override
    public long getCurrentOutput() {
        // PID output control
        long e = 3 * getMaxPower() / 8 - power;
        esum = clamp(esum + e, -eLimit, eLimit);
        return clamp(e + esum / 20, MIN_OUTPUT, MAX_OUTPUT);
    }

    @Override
    public long minPowerReceived() {
        return MjAPI.MJ / 10;
    }

    private static long clamp(long val, long min, long max) {
        return Math.max(min, Math.min(max, val));
    }

    // --- NBT ---

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("burnTime", burnTime);
        output.putInt("totalBurnTime", totalBurnTime);
        output.putLong("esum", esum);
        if (!fuelStack.isEmpty()) {
            net.minecraft.resources.Identifier itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(fuelStack.getItem());
            output.putString("fuelId", itemId.toString());
            output.putInt("fuelCount", fuelStack.getCount());
        }
    }

    @Override
    public void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        burnTime = input.getIntOr("burnTime", 0);
        totalBurnTime = input.getIntOr("totalBurnTime", 0);
        esum = input.getLongOr("esum", 0L);
        String fuelId = input.getStringOr("fuelId", "");
        if (!fuelId.isEmpty()) {
            net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(fuelId);
            if (id != null) {
                net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(id);
                int count = input.getIntOr("fuelCount", 1);
                if (item != null && item != net.minecraft.world.item.Items.AIR) {
                    fuelStack = new ItemStack(item, count);
                } else {
                    fuelStack = ItemStack.EMPTY;
                }
            }
        } else {
            fuelStack = ItemStack.EMPTY;
        }
    }
}
