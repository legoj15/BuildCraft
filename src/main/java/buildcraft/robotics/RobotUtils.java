/*
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.Direction;

import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.IDockingStationProvider;
import buildcraft.api.transport.pipe.IPipeHolder;

/**
 * Static docking-station discovery helper, ported from 7.1.x {@code buildcraft.robotics.RobotUtils}.
 * {@code Object tile} in the original becomes {@code Object tile} here too — callers pass either a plain
 * {@link IDockingStationProvider} (a future station-hosting block entity) or an {@link IPipeHolder} whose
 * per-side pluggables may each provide one.
 */
public final class RobotUtils {
    private RobotUtils() {
    }

    public static List<DockingStation> getStations(Object tile) {
        List<DockingStation> stations = new ArrayList<>();

        if (tile instanceof IDockingStationProvider provider) {
            DockingStation station = provider.getStation();
            if (station != null) {
                stations.add(station);
            }
        }

        if (tile instanceof IPipeHolder holder) {
            for (Direction dir : Direction.values()) {
                if (holder.getPluggable(dir) instanceof IDockingStationProvider provider) {
                    DockingStation station = provider.getStation();
                    if (station != null) {
                        stations.add(station);
                    }
                }
            }
        }

        return stations;
    }
}
