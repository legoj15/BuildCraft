/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.ai;

import java.util.ArrayList;

import buildcraft.api.core.IZone;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.IRobotAccess;
import net.minecraft.world.phys.Vec3;

import buildcraft.robotics.IStationFilter;

/** Picks the nearest {@link DockingStation} matching a {@link IStationFilter} and zone. Sets {@code targetStation}
 *  and terminates immediately — the search is a single sweep of the registry, not a long-running scan. The
 *  7.1.x {@code ActionStationForbidRobot.isForbidden} gate becomes the D1 {@code isRobotForbidden} policy, and (not
 *  7.1.x) it applies to the station the robot is docked at too: {@link AIRobotMain} evicts a robot whose dock starts
 *  forbidding it, and this search then picks another station. */
public class AIRobotSearchStation extends AIRobot {

    public DockingStation targetStation;
    private IStationFilter filter;
    private IZone zone;
    private boolean homeIgnoresZone;

    public AIRobotSearchStation(IRobotAccess iRobot) {
        super(iRobot);
    }

    public AIRobotSearchStation(IRobotAccess iRobot, IStationFilter iFilter, IZone iZone) {
        this(iRobot);

        filter = iFilter;
        zone = iZone;
    }

    /** @param homeIgnoresZone let the robot's own linked station match even outside {@code iZone} (not 7.1.x):
     *                         unloading passes it, so a robot homed outside its work zone can still empty its
     *                         cargo at home rather than only through the docked-station shortcut, once a minute.
     *                         The station Forbid Robot evicted the robot from while it carried cargo matches
     *                         outside the zone too, so it can go back there once the forbid lifts (#68) */
    public AIRobotSearchStation(IRobotAccess iRobot, IStationFilter iFilter, IZone iZone, boolean homeIgnoresZone) {
        this(iRobot, iFilter, iZone);
        this.homeIgnoresZone = homeIgnoresZone;
    }

    @Override
    public void start() {
        if (robot.getDockingStation() != null
                && filter.matches(robot.getDockingStation())
                && !robot.getDockingStation().isRobotForbidden(robot)) {
            targetStation = robot.getDockingStation();
            terminate();
            return;
        }

        double potentialStationDistance = Float.MAX_VALUE;
        DockingStation potentialStation = null;

        // Iterate a SNAPSHOT: getStations() is the live values view of the registry's station map, and a
        // DockingStationPipe whose pipe has gone away deregisters itself from inside isInitialized()
        // (getHolder() → removeStation) — removing from a map while iterating its values is a
        // ConcurrentModificationException, which the AI cycle would swallow as a silently failed search.
        for (DockingStation station : new ArrayList<>(robot.getRegistry().getStations())) {
            if (!isCandidate(robot, station, zone, homeIgnoresZone)) {
                continue;
            }

            if (filter.matches(station)) {
                if (station.isRobotForbidden(robot)) {
                    continue;
                }

                double dx = robot.position().x - station.getPos().getX();
                double dy = robot.position().y - station.getPos().getY();
                double dz = robot.position().z - station.getPos().getZ();
                double distance = dx * dx + dy * dy + dz * dz;

                if (potentialStation == null || distance < potentialStationDistance) {
                    potentialStation = station;
                    potentialStationDistance = distance;
                }
            }
        }

        if (potentialStation != null) {
            targetStation = potentialStation;
        }

        terminate();
    }

    /** What a disposal may wait for when an unload search came up empty ({@link #unavailableTakers}). */
    public enum Hold {
        /** Nothing: a station the robot may use now exists (the caller already tried it and failed), or none would
         *  take the cargo at all. */
        NONE,
        /** A taker forbids the robot (Forbid Robot, #46): the player's own gate state, waited for without a cap. */
        FORBIDDEN,
        /** Takers are only claimed by another robot or not loaded (#68): states nobody controls, so the disposal
         *  waits for them a few times only ({@code AIRobotDisposeItems.MAX_TRANSIENT_HOLDS}). */
        TRANSIENT
    }

    /** Why a search with {@code filter} and {@code zone} comes up empty, when the reason can pass, so a caller whose
     *  own try at the stations failed may wait for one rather than give up. The one list of those reasons (the callers
     *  point here): a station in the search's zone that
     *  <ul>
     *  <li>matches and forbids the robot: {@link Hold#FORBIDDEN}, which wins over the rest;</li>
     *  <li>matches and is held by a plain claim of another robot (a visit or a dock): {@link Hold#TRANSIENT}, the claim
     *      ends with the visit;</li>
     *  <li>is in a chunk that is not loaded: {@link Hold#TRANSIENT}, the chunk loads again once a player comes near.
     *      The filter is not asked: a station read back from the save has no pipe to answer with until its chunk loads,
     *      and one cached from earlier answers from a pipe that has unloaded since. With no zone the search covers the
     *      whole level, where some station is nearly always unloaded, so then only the robot's own home counts.</li>
     *  </ul>
     *  {@link Hold#NONE} as soon as a match is usable now (loaded, free or this robot's, not forbidding), even one the
     *  flight failed to reach: what failed then was not a state that passes. Another robot's HOME never counts: only
     *  that robot's own redirect (a Goto Station action) moves it, and no search offers it to anyone else. The zone is
     *  the search's own ({@code homeIgnoresZone}: the home and the station the robot was evicted from match outside
     *  it). Flight reach is not checked. */
    public static Hold unavailableTakers(IRobotAccess robot, IStationFilter filter, IZone zone,
            boolean homeIgnoresZone) {
        Takers takers = scanTakers(robot, filter, zone, homeIgnoresZone);
        return takers == Takers.FORBIDDEN ? Hold.FORBIDDEN : takers == Takers.TRANSIENT ? Hold.TRANSIENT : Hold.NONE;
    }

    /** Whether a search with {@code filter} and {@code zone} has any station to hope for: one usable now, or one that
     *  is only unavailable for now in any of the ways {@link #unavailableTakers} lists. False means no such station
     *  exists at all, which is what a robot sleeping for "no tool" or "nothing to load" should mean; a failed search
     *  for which this is true failed on a station that is busy, forbidding, unloaded or out of the flight's reach. */
    public static boolean anyTaker(IRobotAccess robot, IStationFilter filter, IZone zone, boolean homeIgnoresZone) {
        return scanTakers(robot, filter, zone, homeIgnoresZone) != Takers.NONE;
    }

    /** What {@link #unavailableTakers} and {@link #anyTaker} both read: the best station a search could hope for. */
    private enum Takers {
        NONE, TRANSIENT, FORBIDDEN, USABLE
    }

    private static Takers scanTakers(IRobotAccess robot, IStationFilter filter, IZone zone,
            boolean homeIgnoresZone) {
        boolean forbidden = false;
        boolean passing = false;
        for (DockingStation station : new ArrayList<>(robot.getRegistry().getStations())) {
            if (!station.isInitialized() || !inZone(robot, station, zone, homeIgnoresZone)) {
                continue;
            }
            boolean heldByAnother = station.isTaken() && station.robotIdTaking() != robot.getRobotId();
            if (heldByAnother && station.isMainStation()) {
                continue;
            }
            if (!station.isChunkLoaded()) {
                passing |= zone != null || station == robot.getLinkedStation();
                continue;
            }
            if (!filter.matches(station)) {
                continue;
            }
            if (station.isRobotForbidden(robot)) {
                forbidden = true;
            } else if (heldByAnother) {
                passing = true;
            } else {
                return Takers.USABLE;
            }
        }
        return forbidden ? Takers.FORBIDDEN : passing ? Takers.TRANSIENT : Takers.NONE;
    }

    /** Everything but the filter and the forbid: the station is loaded, free (or this robot's) and in the zone. */
    private static boolean isCandidate(IRobotAccess robot, DockingStation station, IZone zone,
            boolean homeIgnoresZone) {
        // A station in an unloaded chunk is usable (isInitialized: a robot homed there keeps working) but not
        // reachable: the flight cannot cross unloaded cells, so choosing it only claims it, fails, and picks it
        // again next cycle (a Delivery robot disposed of its cargo every time).
        if (!station.isChunkLoaded() || !station.isInitialized()) {
            return false;
        }

        if (station.isTaken() && station.robotIdTaking() != robot.getRobotId()) {
            return false;
        }

        return inZone(robot, station, zone, homeIgnoresZone);
    }

    /** The zone test of a search: no zone, a station the zone contains, or, when {@code homeIgnoresZone}, the robot's
     *  own home or the station Forbid Robot evicted it from while it carried cargo
     *  ({@link IRobotAccess#getEvictedFromStation}, matched by place since the registry may have rebuilt it). */
    private static boolean inZone(IRobotAccess robot, DockingStation station, IZone zone, boolean homeIgnoresZone) {
        return zone == null
                || (homeIgnoresZone && (station == robot.getLinkedStation() || isEvictedFrom(robot, station)))
                || zone.contains(Vec3.atCenterOf(station.getPos()));
    }

    private static boolean isEvictedFrom(IRobotAccess robot, DockingStation station) {
        DockingStation evicted = robot.getEvictedFromStation();
        return evicted != null && evicted.side() == station.side() && evicted.getPos().equals(station.getPos());
    }

    @Override
    public void delegateAIEnded(AIRobot ai) {
        terminate();
    }

    @Override
    public boolean success() {
        return targetStation != null;
    }
}
