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
 *  7.1.x {@code ActionStationForbidRobot.isForbidden} gate becomes the D1 {@code isRobotForbidden} policy. */
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
                && filter.matches(robot.getDockingStation())) {
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
            // A station in an unloaded chunk is usable (isInitialized: a robot homed there keeps working) but not
            // reachable: the flight cannot cross unloaded cells, so choosing it only claims it, fails, and picks it
            // again next cycle (a Delivery robot disposed of its cargo every time).
            if (!station.isChunkLoaded() || !station.isInitialized()) {
                continue;
            }

            if (station.isTaken() && station.robotIdTaking() != robot.getRobotId()) {
                continue;
            }

            if (zone != null && !(homeIgnoresZone && station == robot.getLinkedStation())
                    && !zone.contains(Vec3.atCenterOf(station.getPos()))) {
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

    @Override
    public void delegateAIEnded(AIRobot ai) {
        terminate();
    }

    @Override
    public boolean success() {
        return targetStation != null;
    }
}
