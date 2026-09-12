/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * The BuildCraft API is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located
 * as "LICENSE.API" in the BuildCraft source code distribution.
 * <p/>
 * Ported from 7.1.x {@code buildcraft.robotics.StackRequest} (8.0.x's mechanically-migrated
 * {@code BlockPos}/{@code EnumFacing} shape) onto modern NBT.
 */
package buildcraft.robotics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import buildcraft.api.core.NbtApiUtil;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.IRequestProvider;
import buildcraft.api.robots.ResourceId;
import buildcraft.api.robots.ResourceIdRequest;
import buildcraft.api.robots.RobotManager;

/** One open order on the request network: a provider (the Requester block, or a station whose gates hold
 *  request-items actions), a slot in that provider, and the stack still owed. The station link is kept by
 *  value (pos + side) so a request survives the robot's save/load with only NBT in hand — the provider and
 *  station objects are re-resolved lazily against the live registry. */
public class StackRequest {
    private IRequestProvider requester;

    private int slot;

    private ItemStack stack;

    private DockingStation station;
    private BlockPos stationIndex;
    private Direction stationSide;

    public StackRequest(IRequestProvider requester, int slot, ItemStack stack) {
        this.requester = requester;
        this.slot = slot;
        this.stack = stack;
        this.station = null;
    }

    private StackRequest(int slot, ItemStack stack, BlockPos stationIndex, Direction stationSide) {
        requester = null;
        this.slot = slot;
        this.stack = stack;
        station = null;
        this.stationIndex = stationIndex;
        this.stationSide = stationSide;
    }

    public IRequestProvider getRequester(Level world) {
        if (requester == null) {
            DockingStation dockingStation = getStation(world);
            if (dockingStation != null) {
                requester = dockingStation.getRequestProvider();
            }
        }
        return requester;
    }

    public int getSlot() {
        return slot;
    }

    public ItemStack getStack() {
        return stack;
    }

    public DockingStation getStation(Level world) {
        if (station == null) {
            station = RobotManager.registryProvider.getRegistry(world).getStation(stationIndex, stationSide);
        }
        return station;
    }

    public void setStation(DockingStation station) {
        this.station = station;
        this.stationIndex = station.index();
        this.stationSide = station.side();
    }

    public void writeToNBT(CompoundTag nbt) {
        nbt.putInt("slot", slot);

        if (!stack.isEmpty()) {
            ItemStack.CODEC.encodeStart(NbtApiUtil.registryAwareOps(), stack)
                    .resultOrPartial()
                    .ifPresent(payload -> nbt.put("stack", payload));
        }

        if (station != null) {
            CompoundTag stationIndexNBT = new CompoundTag();
            stationIndexNBT.putInt("i", stationIndex.getX());
            stationIndexNBT.putInt("j", stationIndex.getY());
            stationIndexNBT.putInt("k", stationIndex.getZ());
            nbt.put("stationIndex", stationIndexNBT);
            nbt.putByte("stationSide", (byte) stationSide.ordinal());
        }
    }

    public static StackRequest loadFromNBT(CompoundTag nbt) {
        if (!nbt.contains("stationIndex")) {
            return null;
        }
        int slot = NbtApiUtil.getInt(nbt, "slot", 0);

        ItemStack stack = ItemStack.EMPTY;
        if (nbt.contains("stack")) {
            stack = ItemStack.CODEC.parse(NbtApiUtil.registryAwareOps(), nbt.get("stack"))
                    .resultOrPartial()
                    .orElse(ItemStack.EMPTY);
        }

        CompoundTag indexNBT = NbtApiUtil.getCompound(nbt, "stationIndex");
        BlockPos stationIndex = new BlockPos(
                NbtApiUtil.getInt(indexNBT, "i", 0),
                NbtApiUtil.getInt(indexNBT, "j", 0),
                NbtApiUtil.getInt(indexNBT, "k", 0));
        Direction stationSide = Direction.values()[NbtApiUtil.getByte(nbt, "stationSide", (byte) 0)];

        return new StackRequest(slot, stack, stationIndex, stationSide);
    }

    public ResourceId getResourceId(Level world) {
        return getStation(world) != null ? new ResourceIdRequest(getStation(world), slot) : null;
    }
}
