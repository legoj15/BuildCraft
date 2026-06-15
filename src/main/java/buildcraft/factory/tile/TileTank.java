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
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.nbt.CompoundTag;
//? if >=1.21.10 {
import net.minecraft.util.profiling.Profiler;
//?}
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
//? if >=1.21.10 {
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
//?}

import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.factory.BCFactoryBlockEntities;
import buildcraft.factory.container.ContainerTank;
import buildcraft.lib.misc.BCValueInput;
import buildcraft.lib.misc.BCValueOutput;
import buildcraft.lib.misc.FluidUtilBC;
import buildcraft.lib.misc.MessageUtil;
import buildcraft.lib.fluid.BCFluidTank;
import buildcraft.lib.fluid.FluidSmoother;
import buildcraft.api.tiles.IDebuggable;

/**
 * Tank tile entity. Stacks vertically with other tanks to form a multi-block
 * fluid column. Liquids settle to the bottom, gases float to the top.
 * Each individual tank holds 16 buckets (16,000 mB).
 * Ported from 1.12.2 TileTank.
 */
@SuppressWarnings("deprecation")
public class TileTank extends BlockEntity implements MenuProvider, IDebuggable {

    public final BCFluidTank tank = new BCFluidTank(1, 16_000); // 16 buckets
    public final FluidSmoother smoothedTank = new FluidSmoother(tank);

    private int lastComparatorLevel;
    private int lastSyncedAmount = -1;

    public TileTank(BlockPos pos, BlockState state) {
        super(BCFactoryBlockEntities.TANK.get(), pos, state);
    }

    // --- Non-player removal drops (explosion / piston / command) ---
    // TileTank extends BlockEntity directly (not TileBC_Neptune), so it carries its own copy of the
    // drop hook. Spills the tank as fragile fluid shards, mirroring BlockTank#playerWillDestroy.

    private boolean dropsHandled = false;

    public void markDropsHandled() {
        dropsHandled = true;
    }

    public void dropContentsOnRemoval(net.minecraft.world.level.Level level, BlockPos pos) {
        if (dropsHandled || level.isClientSide()) {
            return;
        }
        dropsHandled = true;
        buildcraft.lib.misc.BlockDropsUtil.dropFluidShards(level, pos, tank);
    }

    //? if >=1.21.10 {
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            dropContentsOnRemoval(level, pos);
        }
    }
    //?}

    // --- Comparator ---

    public int getComparatorLevel() {
        int amount = tank.getAmountMb(0);
        int cap = tank.getCapacityMb(0);
        return amount * 14 / cap + (amount > 0 ? 1 : 0);
    }

    // --- Server Tick ---

    public void serverTick() {
        if (level == null || level.isClientSide()) return;
        //? if >=1.21.10 {
        ProfilerFiller _profiler = Profiler.get();
        //?} else {
        /*// 1.21.1 has no thread-local Profiler.get(); profiling is a non-gameplay dev tool, so use a no-op.
        ProfilerFiller _profiler = net.minecraft.util.profiling.InactiveProfiler.INSTANCE;*/
        //?}
        _profiler.push("buildcraft:tank_serverTick");
        try {

        int currentAmount = tank.getAmountMb(0);

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
        String contents = (!tank.isTankEmpty(0)) ? "Fluid" : "Empty";
        left.add("fluid = " + buildcraft.lib.misc.FluidUtilBC.getDebugString(tank.getFluidStack(0)));
        left.add("current = " + tank.getAmountMb(0) + " of " + contents);
        left.add("lastSent = " + lastSyncedAmount + " of " + ((!tank.isTankEmpty(0)) ? "Something" : "Nothing"));
    }

    @Override
    public void getClientDebugInfo(List<String> left, List<String> right, Direction side) {
        if (smoothedTank != null) {
            smoothedTank.getDebugInfo(left, right, side);
            left.add("shown = " + (int) smoothedTank.getDisplayAmount() + ", target = " + tank.getAmountMb(0));
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

    //? if <1.21.10 {
    /*// 1.21.1's onDataPacket only applies a NON-empty update tag, so a tank drained to empty (which
    // serialises to an empty tag) would never clear on the client — it stays showing the last contents
    // until reload. Apply unconditionally, matching 26.1.2. (TileTank doesn't extend TileBC_Neptune,
    // so it needs its own copy of this override.) 1.21.1-only.
    @Override
    public void onDataPacket(net.minecraft.network.Connection net,
            ClientboundBlockEntityDataPacket pkt, net.minecraft.core.HolderLookup.Provider registries) {
        loadWithComponents(pkt.getTag(), registries);
    }*/
    //?}

    // --- Tank Column Balancing ---

    /** Moves fluids around to their preferred positions. For liquid fluids this
     * will move everything as low as possible. For gaseous fluids this will
     * move everything as high as possible. */
    public void balanceTankFluids() {
        List<TileTank> tanks = getTankColumn();
        FluidStack fluid = FluidStack.EMPTY;
        for (TileTank tile : tanks) {
            FluidStack held = tile.tank.getFluidStack(0);
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

    // --- Save / Load ---

    // Platform bridge — TileTank extends BlockEntity directly (not TileBC_Neptune), so it carries
    // its own copy of the load/save signature directive (see TileBC_Neptune for the rationale).
    //? if >=1.21.10 {
    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        writeData(new BCValueOutput(output));
    }

    @Override
    public void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        readData(new BCValueInput(input));
    }
    //?} else {
    /*@Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeData(new BCValueOutput(tag));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        readData(new BCValueInput(tag));
    }*/
    //?}

    protected void writeData(BCValueOutput output) {
        tank.serialize(output.raw);
    }

    protected void readData(BCValueInput input) {
        tank.deserialize(input.raw);
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
     * shards) before being moved, so a picked tank should always come back empty. On 1.21.10+
     * the {@code "stacks"} key is {@code StacksResourceHandler.VALUE_IO_KEY}, what
     * {@code tank.serialize} writes; on 1.21.1 {@link buildcraft.lib.fluid.BCFluidTank} writes
     * per-slot {@code "fluid<i>"}/{@code "amount<i>"} keys instead, so those are discarded there.
     */
    //? if >=1.21.10 {
    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        super.removeComponentsFromTag(output);
        output.discard("stacks");
    }
    //?} else {
    /*@Override
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove("fluid0");
        tag.remove("amount0");
    }*/
    //?}

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
