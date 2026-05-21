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
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
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
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import buildcraft.factory.BCFactoryBlockEntities;
import buildcraft.factory.container.ContainerTank;
import buildcraft.lib.misc.FluidUtilBC;
import buildcraft.lib.misc.MessageUtil;
import buildcraft.lib.fluid.FluidSmoother;
import buildcraft.api.tiles.IDebuggable;

/**
 * Tank tile entity. Stacks vertically with other tanks to form a multi-block
 * fluid column. Liquids settle to the bottom, gases float to the top.
 * Each individual tank holds 16 buckets (16,000 mB).
 * Ported from 1.12.2 TileTank.
 */
public class TileTank extends BlockEntity implements MenuProvider, IDebuggable {

    public final FluidStacksResourceHandler tank = new FluidStacksResourceHandler(1, 16_000); // 16 buckets
    public final FluidSmoother smoothedTank = new FluidSmoother(tank);

    private int lastComparatorLevel;
    private int lastSyncedAmount = -1;

    public TileTank(BlockPos pos, BlockState state) {
        super(BCFactoryBlockEntities.TANK.get(), pos, state);
    }

    // --- Comparator ---

    public int getComparatorLevel() {
        int amount = tank.getAmountAsInt(0);
        int cap = tank.getCapacityAsInt(0, FluidResource.EMPTY);
        return amount * 14 / cap + (amount > 0 ? 1 : 0);
    }

    // --- Server Tick ---

    public void serverTick() {
        if (level == null || level.isClientSide()) return;
        ProfilerFiller _profiler = Profiler.get();
        _profiler.push("buildcraft:tank_serverTick");
        try {

        int currentAmount = tank.getAmountAsInt(0);

        // Sync to client when fluid contents change
        if (currentAmount != lastSyncedAmount) {
            lastSyncedAmount = currentAmount;
            setChanged();
            MessageUtil.sendUpdateToTrackingPlayers(this);
        }

        int compLevel = getComparatorLevel();
        if (compLevel != lastComparatorLevel) {
            lastComparatorLevel = compLevel;
            setChanged();
        }
        } finally {
            _profiler.pop();
        }
    }

    public void clientTick() {
        smoothedTank.tick();
    }

    // IDebuggable

    @Override
    public void getDebugInfo(List<String> left, List<String> right, Direction side) {
        String contents = (!tank.getResource(0).isEmpty()) ? "Fluid" : "Empty";
        left.add("fluid = " + buildcraft.lib.misc.FluidUtilBC.getDebugString(tank.getResource(0).toStack(tank.getAmountAsInt(0))));
        left.add("current = " + tank.getAmountAsInt(0) + " of " + contents);
        left.add("lastSent = " + lastSyncedAmount + " of " + ((!tank.getResource(0).isEmpty()) ? "Something" : "Nothing"));
    }

    @Override
    public void getClientDebugInfo(List<String> left, List<String> right, Direction side) {
        if (smoothedTank != null) {
            smoothedTank.getDebugInfo(left, right, side);
            left.add("shown = " + (int) smoothedTank.getDisplayAmount() + ", target = " + tank.getAmountAsInt(0));
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
        FluidResource fluid = FluidResource.EMPTY;
        for (TileTank tile : tanks) {
            FluidResource held = tile.tank.getResource(0);
            if (held.isEmpty()) continue;
            if (fluid.isEmpty()) {
                fluid = held;
            } else if (!fluid.equals(held)) {
                return; // Different fluids — can't balance
            }
        }
        if (fluid.isEmpty()) return;

        if (FluidUtilBC.isGaseous(fluid.toStack(1))) {
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

    // --- Column-Aware ResourceHandler ---

    /**
     * Returns a {@link ResourceHandler} that fills/drains across the entire vertical column. 
     * Used natively for capabilities routing via neoForge.
     */
    public ResourceHandler<FluidResource> getColumnResourceHandler() {
        return new ResourceHandler<FluidResource>() {
            @Override
            public int size() {
                return 1;
            }

            @Override
            public FluidResource getResource(int index) {
                for (TileTank t : getTankColumn()) {
                    FluidResource held = t.tank.getResource(0);
                    if (!held.isEmpty()) {
                        return held;
                    }
                }
                return FluidResource.EMPTY;
            }

            @Override
            public long getAmountAsLong(int index) {
                long result = 0;
                for (TileTank t : getTankColumn()) {
                    FluidResource held = t.tank.getResource(0);
                    if (!held.isEmpty()) {
                        result += t.tank.getAmountAsInt(0);
                    }
                }
                return result;
            }

            @Override
            public long getCapacityAsLong(int index, FluidResource resource) {
                long total = 0;
                for (TileTank t : getTankColumn()) {
                    total += t.tank.getCapacityAsInt(0, resource);
                }
                return total;
            }

            @Override
            public boolean isValid(int index, FluidResource resource) {
                return !resource.isEmpty();
            }

            @Override
            public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
                if (resource.isEmpty() || amount <= 0) return 0;
                List<TileTank> tanks = getTankColumn();
                // Check compatibility
                for (TileTank t : tanks) {
                    FluidResource current = t.tank.getResource(0);
                    if (!current.isEmpty() && !current.equals(resource)) {
                        return 0;
                    }
                }
                // Fill bottom to top for liquids, top to bottom for gases
                boolean gaseous = FluidUtilBC.isGaseous(resource.toStack(1));
                int remaining = amount;
                int totalFilled = 0;
                if (gaseous) {
                    for (int i = tanks.size() - 1; i >= 0; i--) {
                        if (remaining <= 0) break;
                        int filled = tanks.get(i).tank.insert(0, resource, remaining, transaction);
                        remaining -= filled;
                        totalFilled += filled;
                    }
                } else {
                    for (TileTank t : tanks) {
                        if (remaining <= 0) break;
                        int filled = t.tank.insert(0, resource, remaining, transaction);
                        remaining -= filled;
                        totalFilled += filled;
                    }
                }
                return totalFilled;
            }

            @Override
            public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
                if (resource.isEmpty() || amount <= 0) return 0;
                List<TileTank> tanks = getTankColumn();
                // Check compatibility
                // Drain top to bottom for liquids, bottom to top for gases
                boolean gaseous = FluidUtilBC.isGaseous(resource.toStack(1));
                int remaining = amount;
                int totalDrained = 0;
                if (gaseous) {
                    for (TileTank t : tanks) {
                        if (remaining <= 0) break;
                        int drained = t.tank.extract(0, resource, remaining, transaction);
                        if (drained > 0) {
                            remaining -= drained;
                            totalDrained += drained;
                        }
                    }
                } else {
                    for (int i = tanks.size() - 1; i >= 0; i--) {
                        if (remaining <= 0) break;
                        TileTank t = tanks.get(i);
                        int drained = t.tank.extract(0, resource, remaining, transaction);
                        if (drained > 0) {
                            remaining -= drained;
                            totalDrained += drained;
                        }
                    }
                }
                return totalDrained;
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

    /**
     * Strip the fluid-stacks payload from the tag that gets attached as
     * {@code BLOCK_ENTITY_DATA} when this tile is captured into an item — i.e. via creative
     * Ctrl+middle-click pickblock, which calls {@code BlockEntity#saveCustomOnly} then
     * {@code BlockEntity#removeComponentsFromTag} in {@code addBlockDataToItem}, then sets
     * the result as the picked item's {@code BLOCK_ENTITY_DATA} component. Without this
     * override, picking a full tank yields a tank item carrying its 16 000 mB of fluid;
     * placing that item creates a fresh tank already filled, with the source tank still
     * full — effectively duplicating fluid on every placement.
     * <p>
     * BC's fluid economy expects tanks to be broken (which drops fluid as separate item
     * shards) before being moved, so a picked tank should always come back empty. The
     * {@code "stacks"} key is the {@link net.neoforged.neoforge.transfer.StacksResourceHandler#VALUE_IO_KEY},
     * which is what {@code tank.serialize(output)} writes above.
     */
    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        super.removeComponentsFromTag(output);
        output.discard("stacks");
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
