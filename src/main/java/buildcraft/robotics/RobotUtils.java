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
import net.minecraft.world.item.ItemStack;

import buildcraft.api.boards.RedstoneBoardRobotNBT;
import buildcraft.api.boards.RedstoneBoardRegistry;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.IDockingStationProvider;
import buildcraft.api.transport.pipe.IPipeHolder;
import buildcraft.robotics.item.ItemRobot;

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

    /** The board following {@code current} in the registry's catalog (7.1.x verbatim): an empty or
     *  non-robot stack lands on the first (or, reversed, last) board; a robot stack cycles to the next
     *  (or previous) registered board, wrapping around. */
    public static RedstoneBoardRobotNBT getNextBoard(ItemStack current, boolean reverse) {
        // The registry catalog is Collection<RedstoneBoardNBT<?>>; only robot boards can follow a robot stack.
        List<RedstoneBoardRobotNBT> boards = RedstoneBoardRegistry.instance.getAllBoardNBTs().stream()
                .filter(RedstoneBoardRobotNBT.class::isInstance)
                .map(RedstoneBoardRobotNBT.class::cast)
                .toList();
        if (boards.isEmpty()) {
            return null;
        }
        if (current.isEmpty() || !(current.getItem() instanceof ItemRobot)) {
            return reverse ? boards.get(boards.size() - 1) : boards.get(0);
        }
        int index = boards.indexOf(ItemRobot.getRobotBoard(current));
        if (index == -1) {
            return reverse ? boards.get(boards.size() - 1) : boards.get(0);
        }
        int next = index + (reverse ? -1 : 1);
        if (next < 0) {
            next = boards.size() - 1;
        } else if (next >= boards.size()) {
            next = 0;
        }
        return boards.get(next);
    }
}
