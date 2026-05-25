/* Copyright (c) 2017 SpaceToad and the BuildCraft team
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.energy.container;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import buildcraft.energy.BCEnergyMenuTypes;
import buildcraft.energy.tile.TileEngineStone_BC8;
import buildcraft.lib.gui.ContainerBC_Neptune;
import buildcraft.lib.engine.TileEngineBase_BC8;

@SuppressWarnings("this-escape")
public class ContainerEngineStone extends ContainerBC_Neptune {
    public final TileEngineStone_BC8 engine;
    private final ContainerData data;

    // Data indices — ContainerData only supports int, so longs are split into hi/lo
    private static final int DATA_BURN_TIME = 0;
    private static final int DATA_TOTAL_BURN_TIME = 1;
    private static final int DATA_POWER_HI = 2;
    private static final int DATA_POWER_LO = 3;
    private static final int DATA_HEAT = 4;         // float → Float.floatToIntBits
    private static final int DATA_OUTPUT_HI = 5;
    private static final int DATA_OUTPUT_LO = 6;
    private static final int DATA_POWER_STAGE = 7;  // EnumPowerStage ordinal
    private static final int DATA_IS_BURNING_ENGINE = 8; // 1 if engine is actively burning
    private static final int DATA_COUNT = 9;

    // Client-side constructor (from network)
    public ContainerEngineStone(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv, getTile(playerInv, buf));
    }

    // Server-side constructor
    public ContainerEngineStone(int containerId, Inventory playerInv, TileEngineStone_BC8 engine) {
        super(BCEnergyMenuTypes.ENGINE_STONE.get(), containerId, playerInv.player);
        this.engine = engine;

        // Create container data for syncing engine state to client
        if (engine != null && engine.getLevel() != null && !engine.getLevel().isClientSide()) {
            this.data = new ContainerData() {
                @Override
                public int get(int index) {
                    return switch (index) {
                        case DATA_BURN_TIME -> engine.burnTime;
                        case DATA_TOTAL_BURN_TIME -> engine.totalBurnTime;
                        case DATA_POWER_HI -> (int) (engine.getPower() >>> 32);
                        case DATA_POWER_LO -> (int) (engine.getPower() & 0xFFFFFFFFL);
                        case DATA_HEAT -> Float.floatToIntBits(engine.getHeat());
                        case DATA_OUTPUT_HI -> (int) (engine.currentOutput >>> 32);
                        case DATA_OUTPUT_LO -> (int) (engine.currentOutput & 0xFFFFFFFFL);
                        case DATA_POWER_STAGE -> engine.getPowerStage().ordinal();
                        case DATA_IS_BURNING_ENGINE -> engine.isBurning() ? 1 : 0;
                        default -> 0;
                    };
                }

                @Override
                public void set(int index, int value) {
                    switch (index) {
                        case DATA_BURN_TIME -> engine.burnTime = value;
                        case DATA_TOTAL_BURN_TIME -> engine.totalBurnTime = value;
                        // Other fields are read-only on client
                    }
                }

                @Override
                public int getCount() {
                    return DATA_COUNT;
                }
            };
        } else {
            SimpleContainerData clientData = new SimpleContainerData(DATA_COUNT);
            // Pre-seed heat with MIN_HEAT so ledger shows 20°C, not 0°C, before first sync
            clientData.set(DATA_HEAT, Float.floatToIntBits(TileEngineBase_BC8.MIN_HEAT));
            this.data = clientData;
        }

        addDataSlots(this.data);

        // Fuel slot at (80, 41) — same as 1.12
        addSlot(new FuelSlot(engine, 0, 80, 41));

        // Player inventory using BC's helper
        addFullPlayerInventory(8, 84, playerInv);
    }

    private static TileEngineStone_BC8 getTile(Inventory playerInv, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        if (playerInv.player.level() != null) {
            var be = playerInv.player.level().getBlockEntity(pos);
            if (be instanceof TileEngineStone_BC8 engine) {
                return engine;
            }
        }
        return null;
    }

    // --- Accessors for the screen (use synced ContainerData, not direct tile access) ---

    public int getBurnTime() {
        return data.get(DATA_BURN_TIME);
    }

    public int getTotalBurnTime() {
        return data.get(DATA_TOTAL_BURN_TIME);
    }

    public boolean isBurning() {
        return getBurnTime() > 0;
    }

    public float getBurnProgress() {
        int total = getTotalBurnTime();
        if (total <= 0) return 0;
        return (float) getBurnTime() / total;
    }

    /** Synced power in micro-MJ (reconstructed from hi/lo ints). */
    public long getSyncedPower() {
        return ((long) data.get(DATA_POWER_HI) << 32) | (data.get(DATA_POWER_LO) & 0xFFFFFFFFL);
    }

    /** Synced heat level (reconstructed from float bits). */
    public float getSyncedHeat() {
        return Float.intBitsToFloat(data.get(DATA_HEAT));
    }

    /** Synced power stage (for icon display). */
    public buildcraft.api.enums.EnumPowerStage getSyncedPowerStage() {
        int ordinal = data.get(DATA_POWER_STAGE);
        buildcraft.api.enums.EnumPowerStage[] values = buildcraft.api.enums.EnumPowerStage.values();
        if (ordinal >= 0 && ordinal < values.length) return values[ordinal];
        return buildcraft.api.enums.EnumPowerStage.BLUE;
    }

    /** Synced engine-on state (for icon display). */
    public boolean isSyncedBurningEngine() {
        return data.get(DATA_IS_BURNING_ENGINE) != 0;
    }

    /** Synced current output in micro-MJ/tick (reconstructed from hi/lo ints). */
    public long getSyncedCurrentOutput() {
        return ((long) data.get(DATA_OUTPUT_HI) << 32) | (data.get(DATA_OUTPUT_LO) & 0xFFFFFFFFL);
    }

    // --- Standard menu overrides ---

    @Override
    public boolean stillValid(Player player) {
        if (engine == null || engine.isRemoved()) return false;
        return player.distanceToSqr(
            engine.getBlockPos().getX() + 0.5,
            engine.getBlockPos().getY() + 0.5,
            engine.getBlockPos().getZ() + 0.5
        ) <= 64.0;
    }

    // --- Fuel slot that delegates to the tile entity's fuel stack ---

    private static class FuelSlot extends Slot {
        private final TileEngineStone_BC8 engine;

        public FuelSlot(TileEngineStone_BC8 engine, int index, int x, int y) {
            super(new FuelContainer(engine), index, x, y);
            this.engine = engine;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return engine != null && engine.isValidFuel(stack);
        }
    }

    /** A simple Container implementation that wraps the engine's fuel slot. */
    private static class FuelContainer implements net.minecraft.world.Container {
        private final TileEngineStone_BC8 engine;

        FuelContainer(TileEngineStone_BC8 engine) {
            this.engine = engine;
        }

        @Override
        public int getContainerSize() { return 1; }

        @Override
        public boolean isEmpty() {
            return engine == null || engine.getFuelStack().isEmpty();
        }

        @Override
        public ItemStack getItem(int slot) {
            return engine != null ? engine.getFuelStack() : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(int slot, int count) {
            if (engine == null) return ItemStack.EMPTY;
            ItemStack stack = engine.getFuelStack();
            if (stack.isEmpty()) return ItemStack.EMPTY;
            ItemStack result = stack.split(count);
            if (stack.isEmpty()) engine.setFuelStack(ItemStack.EMPTY);
            else engine.setFuelStack(stack);
            return result;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            if (engine == null) return ItemStack.EMPTY;
            ItemStack stack = engine.getFuelStack();
            engine.setFuelStack(ItemStack.EMPTY);
            return stack;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            if (engine != null) engine.setFuelStack(stack);
        }

        @Override
        public void setChanged() {
            if (engine != null) engine.setChanged();
        }

        @Override
        public boolean stillValid(Player player) { return true; }

        @Override
        public void clearContent() {
            if (engine != null) engine.setFuelStack(ItemStack.EMPTY);
        }
    }
}
