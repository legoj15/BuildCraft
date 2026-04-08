/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.tile;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.nbt.CompoundTag;
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
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import buildcraft.factory.BCFactoryBlockEntities;
import buildcraft.factory.container.ContainerTank;
import buildcraft.lib.misc.FluidUtilBC;
import buildcraft.lib.fluid.FluidSmoother;
import buildcraft.api.tiles.IDebuggable;

/**
 * Tank tile entity. Stacks vertically with other tanks to form a multi-block
 * fluid column. Liquids settle to the bottom, gases float to the top.
 * Each individual tank holds 16 buckets (16,000 mB).
 * Ported from 1.12.2 TileTank.
 */
public class TileTank extends BlockEntity implements MenuProvider, IDebuggable {

    @SuppressWarnings("removal")
    public final FluidTank tank = new FluidTank(16_000); // 16 buckets
    public final FluidSmoother smoothedTank = new FluidSmoother(tank);

    private int lastComparatorLevel;
    private int lastSyncedAmount = -1;

    public TileTank(BlockPos pos, BlockState state) {
        super(BCFactoryBlockEntities.TANK.get(), pos, state);
    }

    // --- Comparator ---

    public int getComparatorLevel() {
        int amount = tank.getFluidAmount();
        int cap = tank.getCapacity();
        return amount * 14 / cap + (amount > 0 ? 1 : 0);
    }

    // --- Server Tick ---

    public void serverTick() {
        if (level == null || level.isClientSide()) return;

        int currentAmount = tank.getFluidAmount();

        // Sync to client when fluid contents change
        if (currentAmount != lastSyncedAmount) {
            lastSyncedAmount = currentAmount;
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }

        int compLevel = getComparatorLevel();
        if (compLevel != lastComparatorLevel) {
            lastComparatorLevel = compLevel;
            setChanged();
        }
    }

    public void clientTick() {
        smoothedTank.tick();
    }

    // IDebuggable

    @Override
    public void getDebugInfo(List<String> left, List<String> right, Direction side) {
        String contents = (!tank.getFluid().isEmpty()) ? "Fluid" : "Empty";
        left.add("fluid = " + buildcraft.lib.misc.FluidUtilBC.getDebugString(tank.getFluid()));
        left.add("current = " + tank.getFluidAmount() + " of " + contents);
        left.add("lastSent = " + lastSyncedAmount + " of " + ((!tank.getFluid().isEmpty()) ? "Something" : "Nothing"));
    }

    @Override
    public void getClientDebugInfo(List<String> left, List<String> right, Direction side) {
        if (smoothedTank != null) {
            smoothedTank.getDebugInfo(left, right, side);
            left.add("shown = " + (int) smoothedTank.getDisplayAmount() + ", target = " + tank.getFluidAmount());
        }
    }

    // --- Client Sync ---

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // --- Tank Column Balancing ---

    /** Moves fluids around to their preferred positions. For liquid fluids this
     * will move everything as low as possible. For gaseous fluids this will
     * move everything as high as possible. */
    public void balanceTankFluids() {
        List<TileTank> tanks = getTankColumn();
        FluidStack fluid = FluidStack.EMPTY;
        for (TileTank tile : tanks) {
            FluidStack held = tile.tank.getFluid();
            if (held.isEmpty()) continue;
            if (fluid.isEmpty()) {
                fluid = held;
            } else if (!FluidStack.isSameFluidSameComponents(fluid, held)) {
                return; // Different fluids — can't balance
            }
        }
        if (fluid.isEmpty()) return;

        if (FluidUtilBC.isGaseous(fluid)) {
            // Move fluid upward (gaseous) — iterate from bottom, move to the tank above
            TileTank prev = null;
            for (int i = tanks.size() - 1; i >= 0; i--) {
                TileTank tile = tanks.get(i);
                if (prev != null) {
                    FluidUtilBC.move(tile.tank, prev.tank);
                }
                prev = tile;
            }
        } else {
            // Move fluid downward (liquids)
            TileTank prev = null;
            for (TileTank tile : tanks) {
                if (prev != null) {
                    FluidUtilBC.move(tile.tank, prev.tank);
                }
                prev = tile;
            }
        }
    }

    // --- Tank Connection Helpers ---

    /** Tests if this tank can connect to another in the given direction. */
    public boolean canConnectTo(TileTank other, Direction direction) {
        return true;
    }

    /** Returns true if both tanks can connect to each other. */
    public static boolean canTanksConnect(TileTank from, TileTank to, Direction direction) {
        return from.canConnectTo(to, direction) && to.canConnectTo(from, direction.getOpposite());
    }

    /** Returns a list of all connected tanks, ordered from bottom to top. */
    public List<TileTank> getTankColumn() {
        Deque<TileTank> tanks = new ArrayDeque<>();
        tanks.add(this);

        // Search upward
        TileTank prevTank = this;
        while (true) {
            BlockEntity tileAbove = level.getBlockEntity(prevTank.worldPosition.above());
            if (!(tileAbove instanceof TileTank tankUp)) break;
            if (canTanksConnect(prevTank, tankUp, Direction.UP)) {
                tanks.addLast(tankUp);
            } else {
                break;
            }
            prevTank = tankUp;
        }

        // Search downward
        prevTank = this;
        while (true) {
            BlockEntity tileBelow = level.getBlockEntity(prevTank.worldPosition.below());
            if (!(tileBelow instanceof TileTank tankBelow)) break;
            if (canTanksConnect(prevTank, tankBelow, Direction.DOWN)) {
                tanks.addFirst(tankBelow);
            } else {
                break;
            }
            prevTank = tankBelow;
        }

        return new ArrayList<>(tanks);
    }

    // --- Column-Aware IFluidHandler (for bucket interaction / legacy APIs) ---

    /**
     * Returns an {@link net.neoforged.neoforge.fluids.capability.IFluidHandler} that fills/drains
     * across the entire vertical column. Used by bucket interaction and any code
     * that works with the legacy IFluidHandler API.
     */
    @SuppressWarnings("removal")
    public net.neoforged.neoforge.fluids.capability.IFluidHandler getColumnFluidHandler() {
        return new net.neoforged.neoforge.fluids.capability.IFluidHandler() {
            @Override
            public int getTanks() {
                return 1;
            }

            @Override
            public FluidStack getFluidInTank(int tankIndex) {
                // Return combined fluid from the column
                FluidStack result = FluidStack.EMPTY;
                for (TileTank t : getTankColumn()) {
                    FluidStack held = t.tank.getFluid();
                    if (!held.isEmpty()) {
                        if (result.isEmpty()) {
                            result = held.copy();
                        } else {
                            result.grow(held.getAmount());
                        }
                    }
                }
                return result;
            }

            @Override
            public int getTankCapacity(int tankIndex) {
                int total = 0;
                for (TileTank t : getTankColumn()) {
                    total += t.tank.getCapacity();
                }
                return total;
            }

            @Override
            public boolean isFluidValid(int tankIndex, FluidStack stack) {
                return !stack.isEmpty();
            }

            @Override
            public int fill(FluidStack resource, FluidAction action) {
                if (resource.isEmpty()) return 0;
                List<TileTank> tanks = getTankColumn();
                // Check compatibility
                for (TileTank t : tanks) {
                    FluidStack current = t.tank.getFluid();
                    if (!current.isEmpty() && !FluidStack.isSameFluidSameComponents(current, resource)) {
                        return 0;
                    }
                }
                // Fill bottom to top for liquids, top to bottom for gases
                boolean gaseous = FluidUtilBC.isGaseous(resource);
                int remaining = resource.getAmount();
                int totalFilled = 0;
                if (gaseous) {
                    for (int i = tanks.size() - 1; i >= 0; i--) {
                        if (remaining <= 0) break;
                        int filled = tanks.get(i).tank.fill(resource.copyWithAmount(remaining), action);
                        remaining -= filled;
                        totalFilled += filled;
                    }
                } else {
                    for (TileTank t : tanks) {
                        if (remaining <= 0) break;
                        int filled = t.tank.fill(resource.copyWithAmount(remaining), action);
                        remaining -= filled;
                        totalFilled += filled;
                    }
                }
                return totalFilled;
            }

            @Override
            public FluidStack drain(FluidStack resource, FluidAction action) {
                if (resource.isEmpty()) return FluidStack.EMPTY;
                List<TileTank> tanks = getTankColumn();
                // Drain top to bottom for liquids, bottom to top for gases
                boolean gaseous = FluidUtilBC.isGaseous(resource);
                int remaining = resource.getAmount();
                int totalDrained = 0;
                if (gaseous) {
                    for (TileTank t : tanks) {
                        if (remaining <= 0) break;
                        FluidStack drained = t.tank.drain(resource.copyWithAmount(remaining), action);
                        if (!drained.isEmpty()) {
                            remaining -= drained.getAmount();
                            totalDrained += drained.getAmount();
                        }
                    }
                } else {
                    for (int i = tanks.size() - 1; i >= 0; i--) {
                        if (remaining <= 0) break;
                        TileTank t = tanks.get(i);
                        FluidStack drained = t.tank.drain(resource.copyWithAmount(remaining), action);
                        if (!drained.isEmpty()) {
                            remaining -= drained.getAmount();
                            totalDrained += drained.getAmount();
                        }
                    }
                }
                return totalDrained > 0 ? resource.copyWithAmount(totalDrained) : FluidStack.EMPTY;
            }

            @Override
            public FluidStack drain(int maxDrain, FluidAction action) {
                if (maxDrain <= 0) return FluidStack.EMPTY;
                List<TileTank> tanks = getTankColumn();
                // Find the fluid type first to determine gaseous
                FluidStack sampleFluid = FluidStack.EMPTY;
                for (TileTank t : tanks) {
                    if (!t.tank.getFluid().isEmpty()) {
                        sampleFluid = t.tank.getFluid();
                        break;
                    }
                }
                if (sampleFluid.isEmpty()) return FluidStack.EMPTY;
                // Drain top to bottom for liquids, bottom to top for gases
                boolean gaseous = FluidUtilBC.isGaseous(sampleFluid);
                FluidStack result = FluidStack.EMPTY;
                int remaining = maxDrain;
                if (gaseous) {
                    for (TileTank t : tanks) {
                        if (remaining <= 0) break;
                        FluidStack drained = t.tank.drain(remaining, action);
                        if (!drained.isEmpty()) {
                            if (result.isEmpty()) {
                                result = drained.copy();
                            } else {
                                result.grow(drained.getAmount());
                            }
                            remaining -= drained.getAmount();
                        }
                    }
                } else {
                    for (int i = tanks.size() - 1; i >= 0; i--) {
                        if (remaining <= 0) break;
                        TileTank t = tanks.get(i);
                        FluidStack drained = t.tank.drain(remaining, action);
                        if (!drained.isEmpty()) {
                            if (result.isEmpty()) {
                                result = drained.copy();
                            } else {
                                result.grow(drained.getAmount());
                            }
                            remaining -= drained.getAmount();
                        }
                    }
                }
                return result;
            }
        };
    }

    // --- Save / Load ---

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        tank.serialize(output);
    }

    @Override
    public void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        tank.deserialize(input);
    }

    // --- MenuProvider (GUI) ---

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.buildcraftunofficial.tank");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ContainerTank(containerId, playerInventory, this);
    }
}
