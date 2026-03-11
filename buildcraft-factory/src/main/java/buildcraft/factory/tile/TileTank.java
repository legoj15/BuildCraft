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

/**
 * Tank tile entity. Stacks vertically with other tanks to form a multi-block
 * fluid column. Liquids settle to the bottom, gases float to the top.
 * Each individual tank holds 16 buckets (16,000 mB).
 * Ported from 1.12.2 TileTank.
 */
public class TileTank extends BlockEntity implements MenuProvider {

    @SuppressWarnings("removal")
    public final FluidTank tank = new FluidTank(16_000); // 16 buckets

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
     * will move everything as low as possible. */
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

        // Move fluid downward (liquids)
        TileTank prev = null;
        for (TileTank tile : tanks) {
            if (prev != null) {
                FluidUtilBC.move(tile.tank, prev.tank);
            }
            prev = tile;
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
        return Component.translatable("block.buildcraftfactory.tank");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ContainerTank(containerId, playerInventory, this);
    }
}
