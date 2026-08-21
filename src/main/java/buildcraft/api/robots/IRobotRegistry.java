/* Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the license, which
 * should be located as "LICENSE.API" in the BuildCraft source code distribution. */
package buildcraft.api.robots;

import java.util.Collection;
import java.util.Set;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;

public interface IRobotRegistry {

    long getNextRobotId();

    void registerRobot(EntityRobotBase robot);

    void killRobot(EntityRobotBase robot);

    void unloadRobot(EntityRobotBase robot);

    EntityRobotBase getLoadedRobot(long id);

    /** The dimension's mutable block-reservation set shared by every concurrent
     *  {@code PathFindingSearch} (a search that finds a target reserves its block here so no second search
     *  pathfinds to the same target). Runtime-only state — never persisted: a save/load cycle simply
     *  re-derives reservations as the searches resume. 7.1.x kept the same table as a static
     *  dimension-keyed map; the modern seam hands the caller its own {@link Set}. */
    Set<BlockPos> getBlockReservations();

    boolean isTaken(ResourceId resourceId);

    long robotIdTaking(ResourceId resourceId);

    EntityRobotBase robotTaking(ResourceId resourceId);

    boolean take(ResourceId resourceId, EntityRobotBase robot);

    boolean take(ResourceId resourceId, long robotId);

    void release(ResourceId resourceId);

    void releaseResources(EntityRobotBase robot);

    DockingStation getStation(BlockPos pos, Direction side);

    Collection<DockingStation> getStations();

    void registerStation(DockingStation station);

    void removeStation(DockingStation station);

    void take(DockingStation station, long robotId);

    void release(DockingStation station, long robotId);

    void writeToNbt(CompoundTag nbt);

    void readFromNbt(CompoundTag nbt);

    void registryMarkDirty();
}

