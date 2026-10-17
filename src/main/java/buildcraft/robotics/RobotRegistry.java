/*
 * Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics;

import java.security.InvalidParameterException;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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
import buildcraft.lib.misc.LegacySavedData;
import buildcraft.lib.misc.NBTUtilBC;

import org.jspecify.annotations.Nullable;

/**
 * Per-dimension {@link SavedData} implementation of {@link IRobotRegistry}, ported from 7.1.x
 * {@code buildcraft.robotics.RobotRegistry}. Tracks loaded robots, their reserved {@link ResourceId}s and
 * docking stations, and persists the parts that don't ride along with chunk/entity saves.
 *
 * <p>The cross-node {@code SavedData} registration boilerplate (the {@code TYPE} declaration and the 1.21.1
 * {@code save} override) mirrors the proven {@code SavedDataWireSystems} template verbatim — only the body is
 * BuildCraft-specific. The interface's {@link #writeToNbt}/{@link #readFromNbt} are the decoupled NBT
 * serializers (seam c): they touch no {@link Level}, so the full reservation + reverse-index round-trip is
 * exercised as pure JUnit without bootstrapping the game.
 */
public class RobotRegistry extends SavedData implements IRobotRegistry {

    //? if >=26.1 {
    public static final SavedDataType<RobotRegistry> TYPE = LegacySavedData.type(
        "buildcraft_robot_registry",
        RobotRegistry::new,
        RobotRegistry::makeCodec,
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

    // Runtime-only: block reservations shared by the dimension's concurrent PathFindingSearches. Never
    // persisted — a save/load cycle re-derives them as the searches resume (7.1.x's static
    // dimension-keyed table, now per-SavedData-instance since this registry IS the dimension).
    private final Set<BlockPos> blockReservations = new HashSet<>();
    // Runtime-only reverse index of the above: which robot reserved which cell. 7.1.x's table had no owner, so a
    // robot that unloaded or died between "search found a cell" and "the AI collected it" (the AI is the only
    // thing that ever unreserved) left the cell pinned until the dimension reloaded. Guarded by the same monitor
    // as blockReservations.
    private final Map<Long, Set<BlockPos>> blockReservationsByRobot = new HashMap<>();
    // The ownerless accessor's read-only window onto blockReservations: a cell added or removed through a raw,
    // mutable set would bypass the owner index, and releaseBlockReservations (which frees by position) could then
    // free a cell another robot had re-reserved in between.
    private final Set<BlockPos> blockReservationsReadOnly = new ReadOnlyBlockReservations();

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
        writeToNbt(nbt);
        return nbt;
    }

    public void readFromTag(CompoundTag nbt) {
        readFromNbt(nbt);
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
        } else {
            // A robot loading with the id it was saved with. The counter is persisted with this registry, so it is
            // normally already past that id - but a registry that was lost or reset (a world upgraded across a save
            // layout change, a restored backup) restarts the counter behind the robots still in the world, and the
            // next robot placed would be handed a live one's id.
            raiseNextRobotIdPast(robot.getRobotId());
        }
        if (robotsLoaded.containsKey(robot.getRobotId())) {
            BCLog.logger.warn("[robots] Robot with id " + robot.getRobotId() + " was not unregistered properly");
        }

        addRobotLoaded(robot);
    }

    /** Makes sure the next id handed out is greater than {@code robotId}. The null id is the one value that is never
     *  a robot (it is also the largest, so counting it would overflow the counter). */
    private void raiseNextRobotIdPast(long robotId) {
        if (robotId != EntityRobotBase.NULL_ROBOT_ID && robotId >= nextRobotID) {
            nextRobotID = robotId + 1;
            setDirty();
        }
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

    /** Read-only: every reservation goes through {@link #getBlockReservations(long)}, so each cell has an owner. */
    @Override
    public Set<BlockPos> getBlockReservations() {
        return blockReservationsReadOnly;
    }

    @Override
    public Set<BlockPos> getBlockReservations(long robotId) {
        return new OwnedBlockReservations(robotId);
    }

    /** Frees every search cell {@code robotId} reserved and forgets the robot's entry. Run by every release path
     *  ({@link #killRobot}, {@link #unloadRobot}, {@link #releaseResources}); public so the search-cell
     *  bookkeeping can be driven without a live robot entity. */
    public void releaseBlockReservations(long robotId) {
        synchronized (blockReservations) {
            Set<BlockPos> cells = blockReservationsByRobot.remove(robotId);
            if (cells != null) {
                blockReservations.removeAll(cells);
            }
        }
    }

    /** What {@link #getBlockReservations()} hands out: every robot's cells, read-only, under the shared set's monitor.
     *  ({@link AbstractSet} refuses {@code add}; removal throws.) */
    private final class ReadOnlyBlockReservations extends AbstractSet<BlockPos> {
        @Override
        public boolean contains(Object o) {
            synchronized (blockReservations) {
                return blockReservations.contains(o);
            }
        }

        @Override
        public int size() {
            synchronized (blockReservations) {
                return blockReservations.size();
            }
        }

        @Override
        public Iterator<BlockPos> iterator() {
            synchronized (blockReservations) {
                // A snapshot, read-only: a live iterator over a set other searches mutate would throw, and
                // removal must go through an owned view's remove() or it would skip the owner index.
                return Collections.unmodifiableSet(new HashSet<>(blockReservations)).iterator();
            }
        }

        @Override
        public boolean remove(Object o) {
            throw new UnsupportedOperationException("free a search cell through getBlockReservations(robotId)");
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException("free search cells through releaseBlockReservations(robotId)");
        }
    }

    /** One robot's cells of the shared dimension-wide set, as a set of its own: {@code contains}, {@code size},
     *  iteration and {@code remove} all see this robot's cells only, so every {@link AbstractSet} operation
     *  ({@code isEmpty}, {@code clear}, {@code removeAll}, iterator removal) agrees with them. {@code add} reserves a
     *  cell for this robot and refuses (false) one that ANY robot holds - the dimension-wide test-and-set. A search
     *  reads who holds a cell through the ownerless view ({@code BlockClaims.searchCells} pairs the two); {@code
     *  remove} only ever frees this robot's cell, so a stale search that finishes after its robot was released can
     *  never free the cell a different robot has since taken. All of it runs under the shared set's monitor. */
    private final class OwnedBlockReservations extends AbstractSet<BlockPos> {
        private final long robotId;

        OwnedBlockReservations(long robotId) {
            this.robotId = robotId;
        }

        @Override
        public boolean contains(Object o) {
            synchronized (blockReservations) {
                Set<BlockPos> mine = blockReservationsByRobot.get(robotId);
                return mine != null && mine.contains(o);
            }
        }

        @Override
        public int size() {
            synchronized (blockReservations) {
                Set<BlockPos> mine = blockReservationsByRobot.get(robotId);
                return mine == null ? 0 : mine.size();
            }
        }

        /** A snapshot (other searches mutate the shared set); its {@code remove} frees the cell through this view. */
        @Override
        public Iterator<BlockPos> iterator() {
            Iterator<BlockPos> snapshot;
            synchronized (blockReservations) {
                Set<BlockPos> mine = blockReservationsByRobot.get(robotId);
                snapshot = mine == null ? Collections.emptyIterator() : new ArrayList<>(mine).iterator();
            }
            return new Iterator<>() {
                private BlockPos last;

                @Override
                public boolean hasNext() {
                    return snapshot.hasNext();
                }

                @Override
                public BlockPos next() {
                    last = snapshot.next();
                    return last;
                }

                @Override
                public void remove() {
                    if (last == null) {
                        throw new IllegalStateException();
                    }
                    OwnedBlockReservations.this.remove(last);
                    last = null;
                }
            };
        }

        @Override
        public void clear() {
            releaseBlockReservations(robotId);
        }

        @Override
        public boolean add(BlockPos pos) {
            synchronized (blockReservations) {
                if (!blockReservations.add(pos)) {
                    return false;
                }
                blockReservationsByRobot.computeIfAbsent(robotId, k -> new HashSet<>()).add(pos);
                return true;
            }
        }

        @Override
        public boolean remove(Object o) {
            synchronized (blockReservations) {
                Set<BlockPos> mine = blockReservationsByRobot.get(robotId);
                if (mine == null || !mine.remove(o)) {
                    return false;
                }
                if (mine.isEmpty()) {
                    blockReservationsByRobot.remove(robotId);
                }
                blockReservations.remove(o);
                return true;
            }
        }
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

    /** The raw holder is compared (not {@link #robotIdTaking}), so this neither needs the robot loaded nor
     *  sweeps a stale claim on the way. */
    @Override
    public synchronized void release(ResourceId resourceId, long robotId) {
        if (resourceId != null && resourcesTaken.get(resourceId) instanceof Long holder && holder == robotId) {
            release(resourceId);
        }
    }

    /** The interface's check-then-take, held under the registry monitor so no other claim lands in between. */
    @Override
    public synchronized boolean reclaim(ResourceId resourceId, long robotId) {
        return IRobotRegistry.super.reclaim(resourceId, robotId);
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

        // The search-phase cells (not ResourceIds): the AI that would have collected them is the one thing that
        // can no longer be relied on to run.
        releaseBlockReservations(robotId);

        Set<StationIndex> held = stationsTakenByRobot.get(robotId);
        if (held != null) {
            for (StationIndex s : new HashSet<>(held)) {
                DockingStation d = stations.get(s);
                if (d != null) {
                    // By id: a robot released after an unload (killed later, or reloaded as a new entity) is no
                    // longer the station's cached entity, so an entity comparison would leave the station linked.
                    if (!d.canRelease()) {
                        if (forceAll) {
                            d.unsafeRelease(robotId);
                        } else if (resetEntities && d.robotIdTaking() == robotId) {
                            d.invalidateRobotTakingEntity();
                        }
                    } else {
                        d.unsafeRelease(robotId);
                    }
                }
                // The index must say exactly what the robot still holds. unsafeRelease frees the station but, by
                // design, never touches this registry (it is normally CALLED from here), so every station freed
                // above has to leave the index here too — a freed station left in it was a stale claim that
                // removeStation could never trim afterwards (its holder is no longer this robot). Kept: the home
                // and the dock (a plain release or an unload must not strand the robot), which still name it.
                if (!forceAll && (d == null || d.robotIdTaking() != robotId)) {
                    held.remove(s);
                }
            }

            if (forceAll || held.isEmpty()) {
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
            // Captured up front: undock() RELEASES a non-main station, after which robotTaking() is null and
            // robotIdTaking() is the sentinel — re-reading either below NPE'd (a robot docked at a station it
            // was only visiting made every removal of that station throw) and skipped the index cleanup.
            EntityRobotBase robot = station.robotTaking();
            long robotId = station.robotIdTaking();
            boolean main = station.isMainStation();
            if (robot != null) {
                // A robot must never be left pointing at a station that no longer exists. The main-station
                // branch below only clears the LINK, so without this a robot docked at its own main station
                // kept `dockingStation` referencing the deleted station — observed in-game on 26.2 as a
                // robot still "docked" at an air block. (7.1.x's non-main branch undocked unconditionally,
                // i.e. from whatever station the robot sat at; only a dock at THIS pos+side is ours to end —
                // matched by index, not identity, so a stale instance of the same station still counts.)
                DockingStation dockedAt = robot.getDockingStation();
                if (dockedAt != null && index.equals(new StationIndex(dockedAt))) {
                    robot.undock();
                }
                if (main) {
                    robot.setMainStation(null);
                }
            }
            // Loaded or not, the station leaves the holder's reverse index: 7.1.x trimmed it only for an
            // UNLOADED holder, so a loaded robot kept a stale claim on a deleted (pos, side) — one that a
            // station later registered at the same spot would then inherit.
            if (robotId != EntityRobotBase.NULL_ROBOT_ID) {
                Set<StationIndex> taken = getStationsTakenByRobot(robotId);
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
    public synchronized void writeToNbt(CompoundTag nbt) {
        nbt.putLong("nextRobotID", nextRobotID);

        ListTag resourceList = new ListTag();
        for (Map.Entry<ResourceId, Long> e : resourcesTaken.entrySet()) {
            CompoundTag cpt = new CompoundTag();
            CompoundTag resourceId = new CompoundTag();
            e.getKey().writeToNbt(resourceId);
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
            station.writeToNbt(cpt);
            cpt.putString("stationType", type);
            stationList.add(cpt);
        }
        nbt.put("stationList", stationList);
    }

    @Override
    public synchronized void readFromNbt(CompoundTag nbt) {
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
                station.readFromNbt(cpt);
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

        // The counter must stay ahead of every robot id the data names, whatever the saved counter says: the robots
        // those ids belong to are still out in the world, and are never asked about the counter again.
        for (long robotId : resourcesTaken.values()) {
            raiseNextRobotIdPast(robotId);
        }
        for (long robotId : stationsTakenByRobot.keySet()) {
            raiseNextRobotIdPast(robotId);
        }
        for (DockingStation station : stations.values()) {
            raiseNextRobotIdPast(station.linkedId());
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

    Set<BlockPos> blockCellsReservedBy(long robotId) {
        synchronized (blockReservations) {
            Set<BlockPos> s = blockReservationsByRobot.get(robotId);
            return s == null ? Collections.emptySet() : new HashSet<>(s);
        }
    }

    long nextRobotIdPeek() {
        return nextRobotID;
    }
}
