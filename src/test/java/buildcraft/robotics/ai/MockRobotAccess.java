/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.ai;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

//? if >=1.21.10 {
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
//?} else {
/*import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;*/
//?}

import buildcraft.api.boards.RedstoneBoardRobot;
import buildcraft.api.core.IFluidFilter;
import buildcraft.api.core.IFluidHandlerAdv;
import buildcraft.api.core.IZone;
import buildcraft.api.inventory.IItemTransactor;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjBattery;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.EntityRobotBase;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.api.robots.IRobotRegistry;
import buildcraft.api.robots.ResourceId;

/** A whole robot, as far as an AI test cares: a real {@link MjBattery}, a settable position and docking
 *  station, a settable held item, a real single-slot fluid tank (Ph5), and a registry stub whose station
 *  list is empty (so the search/load AIs unwind without a world). Everything else returns the inert value
 *  an honest test double should. Public (not package-private) because the Ph5 board tests in
 *  {@code buildcraft.robotics.boards} construct boards against it. */
public class MockRobotAccess implements IRobotAccess {

    private static final long TEST_CAPACITY = 10_000L * MjAPI.MJ;

    private final MjBattery battery = new MjBattery(TEST_CAPACITY);
    private Vec3 position = Vec3.ZERO;
    private DockingStation dockingStation;
    private ItemStack heldItem = ItemStack.EMPTY;
    private final RobotFluidTank fluidTank = new RobotFluidTank(4000);

    public MockRobotAccess() {
        battery.addPower(TEST_CAPACITY, false);
    }

    public void setHeldItem(ItemStack stack) {
        heldItem = stack;
    }

    //? if >=1.21.10 {
    void setFluid(FluidResource resource, int amount) {
        fluidTank.set(0, resource, amount);
    }

    FluidResource getFluidResource() {
        return fluidTank.getResource(0);
    }
    //?} else {
    /*void setFluid(FluidStack stack) {
        fluidTank.setFluid(stack);
    }

    FluidStack getFluidStack() {
        return fluidTank.getFluidInTank(0);
    }*/
    //?}

    void setPosition(Vec3 pos) {
        position = pos;
    }

    void setDockingStation(DockingStation station) {
        dockingStation = station;
    }

    // -- energy: the live part --

    @Override
    public MjBattery getBattery() {
        return battery;
    }

    @Override
    public long getPower() {
        return battery.getStored();
    }

    // -- entity-derived --

    @Override
    public Level level() {
        return null;
    }

    @Override
    public Vec3 position() {
        return position;
    }

    @Override
    public BlockPos blockPosition() {
        return BlockPos.containing(position);
    }

    @Override
    public int getId() {
        return 1;
    }

    @Override
    public Vec3 getDeltaMovement() {
        return Vec3.ZERO;
    }

    @Override
    public void setDeltaMovement(Vec3 movement) {
    }

    @Override
    public AABB getBoundingBox() {
        return AABB.ofSize(position, 0.5, 0.5, 0.5);
    }

    // -- held item / aiming --

    @Override
    public ItemStack getHeldItem() {
        return heldItem;
    }

    @Override
    public void setItemInUse(ItemStack stack) {
        heldItem = stack;
    }

    @Override
    public IFluidHandlerAdv getFluidHandler() {
        return fluidTank;
    }

    @Override
    public void attackTargetEntityWithCurrentItem(Entity target) {
    }

    @Override
    public void setItemActive(boolean active) {
    }

    @Override
    public void aimItemAt(float yaw, float pitch) {
    }

    @Override
    public void aimItemAt(BlockPos pos) {
    }

    @Override
    public float getAimYaw() {
        return 0;
    }

    @Override
    public float getAimPitch() {
        return 0;
    }

    // -- docking --

    @Override
    public void dock(DockingStation station) {
    }

    @Override
    public void undock() {
    }

    @Override
    public DockingStation getDockingStation() {
        return dockingStation;
    }

    @Override
    public DockingStation getLinkedStation() {
        return null;
    }

    @Override
    public void setMainStation(DockingStation station) {
    }

    // -- registry / identity --

    private IRobotRegistry registry = new InertRobotRegistry();

    /** Swap the registry stub — a search-AI test lights up exactly the methods it drives by subclassing
     *  {@link InertRobotRegistry}. */
    void setRegistry(IRobotRegistry registry) {
        this.registry = registry;
    }

    @Override
    public IRobotRegistry getRegistry() {
        return registry;
    }

    /** A registry stub: no stations, no resolved station, no resources — enough for a search AI to unwind
     *  instead of null-dereferencing. Named (not anonymous) so a test can subclass it and override just the
     *  one or two methods its scenario exercises. */
    static class InertRobotRegistry implements IRobotRegistry {
        @Override
        public Collection<DockingStation> getStations() {
            return Collections.emptyList();
        }

        @Override
        public DockingStation getStation(BlockPos pos, Direction side) {
            return null;
        }

        @Override
        public void registerRobot(EntityRobotBase robot) {
        }

        @Override
        public void killRobot(EntityRobotBase robot) {
        }

        @Override
        public void unloadRobot(EntityRobotBase robot) {
        }

        @Override
        public EntityRobotBase getLoadedRobot(long id) {
            return null;
        }

        @Override
        public boolean isTaken(ResourceId resourceId) {
            return false;
        }

        @Override
        public long robotIdTaking(ResourceId resourceId) {
            return EntityRobotBase.NULL_ROBOT_ID;
        }

        @Override
        public EntityRobotBase robotTaking(ResourceId resourceId) {
            return null;
        }

        @Override
        public boolean take(ResourceId resourceId, EntityRobotBase robot) {
            return false;
        }

        @Override
        public boolean take(ResourceId resourceId, long robotId) {
            return false;
        }

        @Override
        public void release(ResourceId resourceId) {
        }

        @Override
        public void releaseResources(EntityRobotBase robot) {
        }

        @Override
        public void registerStation(DockingStation station) {
        }

        @Override
        public void removeStation(DockingStation station) {
        }

        @Override
        public void take(DockingStation station, long robotId) {
        }

        @Override
        public void release(DockingStation station, long robotId) {
        }

        @Override
        public void writeToNbt(net.minecraft.nbt.CompoundTag nbt) {
        }

        @Override
        public void readFromNbt(net.minecraft.nbt.CompoundTag nbt) {
        }

        @Override
        public void registryMarkDirty() {
        }

        @Override
        public long getNextRobotId() {
            return 1;
        }

        @Override
        public Set<BlockPos> getBlockReservations() {
            // Mutable: a search AI pathfinds against the set it is given, reserving cells in place.
            return new HashSet<>();
        }
    }

    /** The mock robot's fluid tank — a real single-slot tank, forked on the same cliff as
     *  {@link IFluidHandlerAdv}: the Transfer API on 1.21.10+, the classic {@code IFluidHandler}
     *  {@code FluidTank} on 1.21.1. */
    //? if >=1.21.10 {
    static class RobotFluidTank extends FluidStacksResourceHandler implements IFluidHandlerAdv {
        RobotFluidTank(int capacity) {
            super(1, capacity);
        }

        @Override
        public int extract(IFluidFilter filter, int maxDrain, TransactionContext tx) {
            FluidResource res = getResource(0);
            if (res == null || res.isEmpty() || !filter.matches(res.toStack(getAmountAsInt(0)))) {
                return 0;
            }
            return extract(0, res, maxDrain, tx);
        }
    }
    //?} else {
    /*static class RobotFluidTank extends FluidTank implements IFluidHandlerAdv {
        RobotFluidTank(int capacity) {
            super(capacity);
        }

        @Override
        public int extract(IFluidFilter filter, int maxDrain, boolean simulate) {
            FluidStack inTank = getFluidInTank(0);
            if (inTank.isEmpty() || !filter.matches(inTank)) {
                return 0;
            }
            return drain(maxDrain, simulate ? net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE
                    : net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE).getAmount();
        }
    }*/
    //?}

    @Override
    public long getRobotId() {
        return Long.MAX_VALUE;
    }

    @Override
    public void releaseResources() {
    }

    // -- zones --

    @Override
    public IZone getZoneToWork() {
        return null;
    }

    @Override
    public IZone getZoneToLoadUnload() {
        return null;
    }

    // -- inventory --

    @Override
    public int getInventorySize() {
        return 0;
    }

    @Override
    public ItemStack getInventoryStack(int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setInventoryStack(int slot, ItemStack stack) {
    }

    @Override
    public IItemTransactor getTransactor() {
        return new IItemTransactor() {
            @Override
            public ItemStack insert(ItemStack stack, boolean allOrNone, boolean simulate) {
                return stack;
            }

            @Override
            public ItemStack extract(buildcraft.api.core.IStackFilter filter, int min, int max, boolean simulate) {
                return ItemStack.EMPTY;
            }
        };
    }

    @Override
    public boolean containsItems() {
        return false;
    }

    @Override
    public boolean hasFreeSlot() {
        return true;
    }

    @Override
    public ItemStack receiveItem(BlockEntity tile, ItemStack stack) {
        return stack;
    }

    // -- pathing hints --

    @Override
    public void unreachableEntityDetected(Entity entity) {
    }

    @Override
    public boolean isKnownUnreachable(Entity entity) {
        return false;
    }

    // -- board / motion --

    private RedstoneBoardRobot board;

    public void setBoard(RedstoneBoardRobot board) {
        this.board = board;
    }

    @Override
    public RedstoneBoardRobot getBoard() {
        return board;
    }

    @Override
    public boolean isMoving() {
        return false;
    }
}
