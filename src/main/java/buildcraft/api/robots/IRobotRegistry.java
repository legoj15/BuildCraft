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
     *  dimension-keyed map; the modern seam hands the caller its own {@link Set}. This is the ownerless view, for
     *  reads: a search reserves and frees through {@link #getBlockReservations(long)}, and a registry that tracks
     *  owners (BuildCraft's does) hands this out read-only, since a cell changed here would bypass the owner
     *  index. */
    Set<BlockPos> getBlockReservations();

    /** {@code robotId}'s own cells of the dimension-wide reservation set: every cell added through the returned view
     *  is remembered as that robot's, so the registry can free it when the robot unloads or dies
     *  ({@link #killRobot}/{@link #unloadRobot}/{@link #releaseResources}) instead of leaving a cell pinned by a
     *  search whose AI never got to run {@code end()}. The view is a consistent set of the robot's own cells:
     *  {@code contains}, {@code size}, iteration and {@code remove} see only those; {@code add} refuses (false) a cell
     *  that ANY robot has reserved. Whether any robot holds a cell is read through {@link #getBlockReservations()}.
     *  The default hands back the ownerless set, for registries that do not track owners. */
    default Set<BlockPos> getBlockReservations(long robotId) {
        return getBlockReservations();
    }

    boolean isTaken(ResourceId resourceId);

    long robotIdTaking(ResourceId resourceId);

    EntityRobotBase robotTaking(ResourceId resourceId);

    boolean take(ResourceId resourceId, EntityRobotBase robot);

    boolean take(ResourceId resourceId, long robotId);

    void release(ResourceId resourceId);

    /** Releases {@code resourceId} only if {@code robotId} is the robot holding it. A robot's own job should give its
     *  block back through this, never through the unchecked {@link #release(ResourceId)}: a robot that unloads
     *  loses every reservation, another robot may take the block meanwhile, and the reloaded robot still remembers
     *  the block it was working on - its end-of-job release must not drop the new holder's claim. */
    default void release(ResourceId resourceId, long robotId) {
        if (resourceId != null && robotIdTaking(resourceId) == robotId) {
            release(resourceId);
        }
    }

    /** Re-takes a reservation a robot remembers from before an unload (or a server restart, after which a claim
     *  whose robot was not loaded yet is dropped the first time anyone asks about it): true when {@code robotId}
     *  holds {@code resourceId} on return - it still held it, or it was free and is now taken. False means another
     *  robot holds it, and the caller must give that job up rather than work it unreserved. */
    default boolean reclaim(ResourceId resourceId, long robotId) {
        if (resourceId == null) {
            return false;
        }
        long holder = robotIdTaking(resourceId);
        return holder == robotId || (holder == EntityRobotBase.NULL_ROBOT_ID && take(resourceId, robotId));
    }

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

