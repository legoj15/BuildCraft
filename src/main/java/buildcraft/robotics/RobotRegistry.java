/*
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics;

import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
//? if >=1.21.10 {
import net.minecraft.world.level.saveddata.SavedDataType;
//?}

import buildcraft.api.core.BCLog;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.EntityRobotBase;
import buildcraft.api.robots.IRobotRegistry;
import buildcraft.api.robots.ResourceId;
import buildcraft.api.robots.RobotManager;
import buildcraft.lib.misc.NBTUtilBC;

import org.jspecify.annotations.Nullable;

/**
 * Per-dimension {@link SavedData} implementation of {@link IRobotRegistry}, ported from 7.1.x
 * {@code buildcraft.robotics.RobotRegistry}. Tracks loaded robots, their reserved {@link ResourceId}s and
 * docking stations, and persists the parts that don't ride along with chunk/entity saves.
 *
 * <p>The cross-node {@code SavedData} registration boilerplate (the {@code TYPE} declaration and the 1.21.1
 * {@code save} override) mirrors the proven {@code SavedDataWireSystems} template verbatim — only the body is
 * BuildCraft-specific. The interface's {@link #writeToNBT}/{@link #readFromNBT} are the decoupled NBT
 * serializers (seam c): they touch no {@link Level}, so the full reservation + reverse-index round-trip is
 * exercised as pure JUnit without bootstrapping the game.
 */
public class RobotRegistry extends SavedData implements IRobotRegistry {

    //? if >=26.1 {
    public static final SavedDataType<RobotRegistry> TYPE = new SavedDataType<>(
        Identifier.withDefaultNamespace("buildcraft_robot_registry"),
        () -> new RobotRegistry(null),
        makeCodec(null),
        DataFixTypes.SAVED_DATA_MAP_DATA
    );
    //?} elif >=1.21.10 {
    /*public static final SavedDataType<RobotRegistry> TYPE = new SavedDataType<>(
        "buildcraft_robot_registry",
        () -> new RobotRegistry(null),
        makeCodec(null),
        DataFixTypes.SAVED_DATA_MAP_DATA
    );*/
    //?} else {
    /*// 1.21.1: no SavedDataType — use SavedData.Factory (ctor, (tag,provider)->T deserializer, DataFixTypes).
    public static final SavedData.Factory<RobotRegistry> TYPE = new SavedData.Factory<>(
        () -> new RobotRegistry(null),
        (tag, provider) -> {
            RobotRegistry data = new RobotRegistry(null);
            data.readFromTag(tag);
            return data;
        },
        DataFixTypes.SAVED_DATA_MAP_DATA
    );*/
    //?}

    protected Level world;
    protected final Map<StationIndex, DockingStation> stations = new HashMap<>();

    private long nextRobotID = Long.MIN_VALUE;

    private final Map<Long, EntityRobotBase> robotsLoaded = new HashMap<>();
    private final Set<EntityRobotBase> robotsLoadedSet = new HashSet<>();
    private final Map<ResourceId, Long> resourcesTaken = new HashMap<>();
    private final Map<Long, Set<ResourceId>> resourcesTakenByRobot = new HashMap<>();
    private final Map<Long, Set<StationIndex>> stationsTakenByRobot = new HashMap<>();

    public RobotRegistry(@Nullable ServerLevel level) {
        this.world = level;
    }

    //? if <1.21.10 {
    /*// 1.21.1: SavedData.save(CompoundTag, Provider) is abstract (no Codec-driven SavedDataType).
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        return writeToTag();
    }*/
    //?}

    private static Codec<RobotRegistry> makeCodec(@Nullable ServerLevel level) {
        return CompoundTag.CODEC.flatXmap(
            tag -> {
                RobotRegistry data = new RobotRegistry(level);
                data.readFromTag(tag);
                return DataResult.success(data);
            },
            data -> DataResult.success(data.writeToTag())
        );
    }

    public CompoundTag writeToTag() {
        CompoundTag nbt = new CompoundTag();
        writeToNBT(nbt);
        return nbt;
    }

    public void readFromTag(CompoundTag nbt) {
        readFromNBT(nbt);
    }

    @Override
    public long getNextRobotId() {
        long result = nextRobotID;
        nextRobotID = nextRobotID + 1;
        return result;
    }

    @Override
    public void registerRobot(EntityRobotBase robot) {
        setDirty();

        if (robot.getRobotId() == EntityRobotBase.NULL_ROBOT_ID) {
            robot.setUniqueRobotId(getNextRobotId());
        }
        if (robotsLoaded.containsKey(robot.getRobotId())) {
            BCLog.logger.warn("[robots] Robot with id " + robot.getRobotId() + " was not unregistered properly");
        }

        addRobotLoaded(robot);
    }

    private Set<ResourceId> getResourcesTakenByRobot(long robotId) {
        return resourcesTakenByRobot.get(robotId);
    }

    private Set<StationIndex> getStationsTakenByRobot(long robotId) {
        return stationsTakenByRobot.get(robotId);
    }

    private void addRobotLoaded(EntityRobotBase robot) {
        robotsLoaded.put(robot.getRobotId(), robot);
        robotsLoadedSet.add(robot);
    }

    private void removeRobotLoaded(EntityRobotBase robot) {
        robotsLoaded.remove(robot.getRobotId());
        robotsLoadedSet.remove(robot);
    }

    @Override
    public void killRobot(EntityRobotBase robot) {
        setDirty();
        releaseResources(robot, true);
        removeRobotLoaded(robot);
    }

    @Override
    public void unloadRobot(EntityRobotBase robot) {
        setDirty();
        releaseResources(robot, false, true);
        removeRobotLoaded(robot);
    }

    @Override
    public EntityRobotBase getLoadedRobot(long id) {
        return robotsLoaded.get(id);
    }

    @Override
    public synchronized boolean isTaken(ResourceId resourceId) {
        return robotIdTaking(resourceId) != EntityRobotBase.NULL_ROBOT_ID;
    }

    @Override
    public synchronized long robotIdTaking(ResourceId resourceId) {
        if (!resourcesTaken.containsKey(resourceId)) {
            return EntityRobotBase.NULL_ROBOT_ID;
        }

        long robotId = resourcesTaken.get(resourceId);

        EntityRobotBase robot = robotsLoaded.get(robotId);
        if (robot != null && robot.isAlive()) {
            return robotId;
        } else {
            // If the robot is either not loaded or dead, the resource is not actively used anymore. Release it.
            release(resourceId);
            return EntityRobotBase.NULL_ROBOT_ID;
        }
    }

    @Override
    public synchronized EntityRobotBase robotTaking(ResourceId resourceId) {
        long robotId = robotIdTaking(resourceId);
        if (robotId == EntityRobotBase.NULL_ROBOT_ID) {
            return null;
        }
        return robotsLoaded.get(robotId);
    }

    @Override
    public synchronized boolean take(ResourceId resourceId, EntityRobotBase robot) {
        setDirty();
        return take(resourceId, robot.getRobotId());
    }

    @Override
    public synchronized boolean take(ResourceId resourceId, long robotId) {
        if (resourceId == null) {
            return false;
        }

        setDirty();

        if (!resourcesTaken.containsKey(resourceId)) {
            resourcesTaken.put(resourceId, robotId);
            resourcesTakenByRobot.computeIfAbsent(robotId, k -> new HashSet<>()).add(resourceId);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public synchronized void release(ResourceId resourceId) {
        if (resourceId == null) {
            return;
        }

        setDirty();

        if (resourcesTaken.containsKey(resourceId)) {
            long robotId = resourcesTaken.get(resourceId);
            Set<ResourceId> taken = getResourcesTakenByRobot(robotId);
            if (taken != null) {
                taken.remove(resourceId);
            }
            resourcesTaken.remove(resourceId);
        }
    }

    @Override
    public synchronized void releaseResources(EntityRobotBase robot) {
        releaseResources(robot, false);
    }

    private synchronized void releaseResources(EntityRobotBase robot, boolean forceAll) {
        releaseResources(robot, forceAll, false);
    }

    private synchronized void releaseResources(EntityRobotBase robot, boolean forceAll, boolean resetEntities) {
        setDirty();

        long robotId = robot.getRobotId();

        if (resourcesTakenByRobot.containsKey(robotId)) {
            for (ResourceId id : new HashSet<>(getResourcesTakenByRobot(robotId))) {
                release(id);
            }
            resourcesTakenByRobot.remove(robotId);
        }

        if (stationsTakenByRobot.containsKey(robotId)) {
            for (StationIndex s : new HashSet<>(getStationsTakenByRobot(robotId))) {
                DockingStation d = stations.get(s);
                if (d != null) {
                    if (!d.canRelease()) {
                        if (forceAll) {
                            d.unsafeRelease(robot);
                        } else if (resetEntities && d.robotIdTaking() == robotId) {
                            d.invalidateRobotTakingEntity();
                        }
                    } else {
                        d.unsafeRelease(robot);
                    }
                }
            }

            if (forceAll) {
                stationsTakenByRobot.remove(robotId);
            }
        }
    }

    @Override
    public synchronized DockingStation getStation(BlockPos pos, Direction side) {
        return stations.get(new StationIndex(side, pos));
    }

    @Override
    public synchronized Collection<DockingStation> getStations() {
        return stations.values();
    }

    @Override
    public synchronized void registerStation(DockingStation station) {
        setDirty();

        StationIndex index = new StationIndex(station);
        if (stations.containsKey(index)) {
            throw new InvalidParameterException("Station " + index + " already registered");
        }
        stations.put(index, station);
    }

    @Override
    public synchronized void removeStation(DockingStation station) {
        setDirty();

        StationIndex index = new StationIndex(station);
        if (stations.containsKey(index)) {
            if (station.robotTaking() != null) {
                if (!station.isMainStation()) {
                    station.robotTaking().undock();
                } else {
                    station.robotTaking().setMainStation(null);
                }
            } else if (station.robotIdTaking() != EntityRobotBase.NULL_ROBOT_ID) {
                Set<StationIndex> taken = getStationsTakenByRobot(station.robotIdTaking());
                if (taken != null) {
                    taken.remove(index);
                }
            }

            stations.remove(index);
        }
    }

    @Override
    public synchronized void take(DockingStation station, long robotId) {
        stationsTakenByRobot.computeIfAbsent(robotId, k -> new HashSet<>()).add(new StationIndex(station));
    }

    @Override
    public synchronized void release(DockingStation station, long robotId) {
        Set<StationIndex> taken = getStationsTakenByRobot(robotId);
        if (taken != null) {
            taken.remove(new StationIndex(station));
        }
    }

    @Override
    public synchronized void writeToNBT(CompoundTag nbt) {
        nbt.putLong("nextRobotID", nextRobotID);

        ListTag resourceList = new ListTag();
        for (Map.Entry<ResourceId, Long> e : resourcesTaken.entrySet()) {
            CompoundTag cpt = new CompoundTag();
            CompoundTag resourceId = new CompoundTag();
            e.getKey().writeToNBT(resourceId);
            cpt.put("resourceId", resourceId);
            cpt.putLong("robotId", e.getValue());
            resourceList.add(cpt);
        }
        nbt.put("resourceList", resourceList);

        ListTag stationList = new ListTag();
        for (DockingStation station : stations.values()) {
            String type = RobotManager.getDockingStationName(station.getClass());
            if (type == null) {
                BCLog.logger.warn("[robots] Skipping save of unregistered docking station type "
                        + station.getClass().getName());
                continue;
            }
            CompoundTag cpt = new CompoundTag();
            station.writeToNBT(cpt);
            cpt.putString("stationType", type);
            stationList.add(cpt);
        }
        nbt.put("stationList", stationList);
    }

    @Override
    public synchronized void readFromNBT(CompoundTag nbt) {
        nextRobotID = NBTUtilBC.getLong(nbt, "nextRobotID", Long.MIN_VALUE);

        resourcesTaken.clear();
        resourcesTakenByRobot.clear();
        stationsTakenByRobot.clear();
        stations.clear();

        ListTag resourceList = NBTUtilBC.getList(nbt, "resourceList", Tag.TAG_COMPOUND);
        for (int i = 0; i < resourceList.size(); i++) {
            if (resourceList.get(i) instanceof CompoundTag cpt) {
                ResourceId resourceId = ResourceId.load(NBTUtilBC.getCompound(cpt, "resourceId"));
                long robotId = NBTUtilBC.getLong(cpt, "robotId", EntityRobotBase.NULL_ROBOT_ID);
                if (resourceId != null) {
                    // Rebuilds both resourcesTaken and the resourcesTakenByRobot reverse index.
                    take(resourceId, robotId);
                }
            }
        }

        ListTag stationList = NBTUtilBC.getList(nbt, "stationList", Tag.TAG_COMPOUND);
        for (int i = 0; i < stationList.size(); i++) {
            if (!(stationList.get(i) instanceof CompoundTag cpt)) {
                continue;
            }
            String type = NBTUtilBC.getString(cpt, "stationType", "");
            Class<? extends DockingStation> cls = RobotManager.getDockingStationByName(type);
            if (cls == null) {
                BCLog.logger.error("[robots] Could not load docking station of unknown type '" + type + "'");
                continue;
            }
            try {
                DockingStation station = cls.getDeclaredConstructor().newInstance();
                station.readFromNBT(cpt);
                station.world = world;
                registerStation(station);
                if (station.linkedId() != EntityRobotBase.NULL_ROBOT_ID) {
                    // Rebuilds the stationsTakenByRobot reverse index.
                    take(station, station.linkedId());
                }
            } catch (Exception e) {
                BCLog.logger.error("[robots] Could not load docking station", e);
            }
        }
    }

    @Override
    public void registryMarkDirty() {
        setDirty();
    }

    public static RobotRegistry get(Level world) {
        if (world.isClientSide()) {
            throw new UnsupportedOperationException("Attempted to get the RobotRegistry on the client!");
        }
        if (world instanceof ServerLevel serverLevel) {
            //? if >=1.21.10 {
            RobotRegistry instance = serverLevel.getDataStorage().computeIfAbsent(TYPE);
            //?} else {
            /*RobotRegistry instance = serverLevel.getDataStorage().computeIfAbsent(TYPE, "buildcraft_robot_registry");*/
            //?}
            instance.world = world;
            for (DockingStation station : instance.stations.values()) {
                station.world = world;
            }
            return instance;
        }
        throw new IllegalArgumentException("World is not a ServerLevel!");
    }

    // ---- package-private inspection helpers for pure-JUnit tests (no live entity required) ----

    /** Raw holder of a resource, ignoring the loaded/alive gating that {@link #robotIdTaking} applies. */
    long rawHolderOf(ResourceId id) {
        Long v = resourcesTaken.get(id);
        return v == null ? EntityRobotBase.NULL_ROBOT_ID : v;
    }

    Set<ResourceId> resourcesReservedBy(long robotId) {
        Set<ResourceId> s = resourcesTakenByRobot.get(robotId);
        return s == null ? Collections.emptySet() : s;
    }

    Set<StationIndex> stationsReservedBy(long robotId) {
        Set<StationIndex> s = stationsTakenByRobot.get(robotId);
        return s == null ? Collections.emptySet() : s;
    }

    long nextRobotIdPeek() {
        return nextRobotID;
    }
}
