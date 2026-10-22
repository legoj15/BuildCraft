/* Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License 1.0, or MMPL. Please check the contents
 * of the license located in http://www.mod-buildcraft.com/MMPL-1.0.txt */
package buildcraft.api.robots;

import org.apache.commons.lang3.builder.HashCodeBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

import buildcraft.api.core.EnumPipePart;
import buildcraft.api.core.NbtApiUtil;

public class ResourceIdRequest extends ResourceIdBlock {

    private int slot;

    public ResourceIdRequest() {

    }

    public ResourceIdRequest(DockingStation station, int slot) {
        this(station.index(), station.side(), slot);
    }

    /** The request in {@code slot} of the station at {@code stationPos} on {@code stationSide}: the same id the station
     *  form gives, for a holder that keeps the station by value (it may be gone, its reservation still held). */
    public ResourceIdRequest(BlockPos stationPos, Direction stationSide, int slot) {
        pos = stationPos;
        side = EnumPipePart.fromFacing(stationSide);
        this.slot = slot;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (!super.equals(obj)) return false;
        ResourceIdRequest compareId = (ResourceIdRequest) obj;

        return slot == compareId.slot;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(super.hashCode()).append(slot).build();
    }

    @Override
    public void writeToNbt(CompoundTag nbt) {
        super.writeToNbt(nbt);

        nbt.putInt("localId", slot);
    }

    @Override
    protected void readFromNbt(CompoundTag nbt) {
        super.readFromNbt(nbt);

        slot = NbtApiUtil.getInt(nbt, "localId", 0);
    }
}

