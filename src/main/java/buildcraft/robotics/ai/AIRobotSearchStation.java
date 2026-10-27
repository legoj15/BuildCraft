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
     *                         cargo at home rather than only through the docked-station shortcut, once a minute */
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

    /** Whether a search with {@code filter} and {@code zone} would find a station but for Forbid Robot: one matches
     *  and is a candidate (loaded, free, in the zone; flight reach is not checked), and every such station forbids the
     *  robot. A forbid is gate state that can lift, so a
     *  robot this leaves without a station has somewhere to go later, which the caller may want to wait for. */
    public static boolean onlyForbiddingStationsMatch(IRobotAccess robot, IStationFilter filter, IZone zone,
            boolean homeIgnoresZone) {
        boolean forbidden = false;
        for (DockingStation station : new ArrayList<>(robot.getRegistry().getStations())) {
            if (isCandidate(robot, station, zone, homeIgnoresZone) && filter.matches(station)) {
                if (!station.isRobotForbidden(robot)) {
                    return false;
                }
                forbidden = true;
            }
        }
        return forbidden;
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

        return zone == null || (homeIgnoresZone && station == robot.getLinkedStation())
                || zone.contains(Vec3.atCenterOf(station.getPos()));
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
