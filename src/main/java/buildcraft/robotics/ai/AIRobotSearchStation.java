/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.ai;

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

    public AIRobotSearchStation(IRobotAccess iRobot) {
        super(iRobot);
    }

    public AIRobotSearchStation(IRobotAccess iRobot, IStationFilter iFilter, IZone iZone) {
        this(iRobot);

        filter = iFilter;
        zone = iZone;
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

        for (DockingStation station : robot.getRegistry().getStations()) {
            if (!station.isInitialized()) {
                continue;
            }

            if (station.isTaken() && station.robotIdTaking() != robot.getRobotId()) {
                continue;
            }

            if (zone != null && !zone.contains(Vec3.atCenterOf(station.getPos()))) {
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
