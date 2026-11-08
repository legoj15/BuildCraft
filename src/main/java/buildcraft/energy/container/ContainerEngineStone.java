/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.energy.container;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import buildcraft.energy.BCEnergyMenuTypes;
import buildcraft.energy.tile.TileEngineStone_BC8;
import buildcraft.lib.gui.ContainerBCTile;
import buildcraft.lib.engine.EngineMenuData;
import buildcraft.lib.gui.WideContainerData;

@SuppressWarnings("this-escape")
public class ContainerEngineStone extends ContainerBCTile<TileEngineStone_BC8> {
    // The engine tile is the inherited `tile` field (ContainerBCTile). Screens read it via menu.tile.
    private final WideContainerData.IntValue burnTime;
    private final WideContainerData.IntValue totalBurnTime;
    private final EngineMenuData engineData;

    // Client-side constructor (from network)
    public ContainerEngineStone(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv, resolveTile(playerInv, buf, TileEngineStone_BC8.class));
    }

    // Server-side constructor
    public ContainerEngineStone(int containerId, Inventory playerInv, TileEngineStone_BC8 engine) {
        super(BCEnergyMenuTypes.ENGINE_STONE.get(), containerId, playerInv.player, engine);

        // Engine state synced to the client; WideContainerData keeps every value intact through vanilla's 16-bit
        // menu-data packet (the only one Fabric has).
        WideContainerData.Builder sync = WideContainerData.builder();
        burnTime = sync.addInt(() -> engine.burnTime);
        totalBurnTime = sync.addInt(() -> engine.totalBurnTime);
        engineData = new EngineMenuData(sync, engine, () -> engine.getPower());
        addDataSlots(sync.buildFor(engine));

        // Fuel slot at (80, 41) — same as 1.12
        addSlot(new FuelSlot(engine, 0, 80, 41));

        // Player inventory using BC's helper
        addFullPlayerInventory(8, 84, playerInv);
    }

    // --- Accessors for the screen (use synced menu data, not direct tile access) ---

    public int getBurnTime() {
        return burnTime.get();
    }

    public int getTotalBurnTime() {
        return totalBurnTime.get();
    }

    public boolean isBurning() {
        return getBurnTime() > 0;
    }

    public float getBurnProgress() {
        int total = getTotalBurnTime();
        if (total <= 0) return 0;
        return (float) getBurnTime() / total;
    }

    /** Synced power in micro-MJ. */
    public long getSyncedPower() {
        return engineData.power();
    }

    /** Synced heat in °C. */
    public float getSyncedHeat() {
        return engineData.heat();
    }

    /** Synced power stage (for icon display). */
    public buildcraft.api.enums.EnumPowerStage getSyncedPowerStage() {
        return engineData.stage();
    }

    /** Synced engine-on state (for icon display). */
    public boolean isSyncedBurningEngine() {
        return engineData.burning();
    }

    /** Synced current output in micro-MJ/tick. */
    public long getSyncedCurrentOutput() {
        return engineData.output();
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
