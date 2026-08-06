/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.ai;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import buildcraft.api.robots.DockingStation;
import buildcraft.api.statements.StatementSlot;

/** {@link AIRobotSearchStation#start()} sweeps {@code IRobotRegistry.getStations()}, which is the LIVE
 *  {@code values()} view of the registry's station map — and a {@code DockingStationPipe} whose pipe has
 *  gone away deregisters itself from inside {@code isInitialized()} ({@code getHolder()} calls
 *  {@code removeStation}). Removing from a map while iterating its values view is a
 *  {@code ConcurrentModificationException}, which the AI cycle catches and treats as a failed search: one
 *  stale station would silently break station-search for every robot until the registry next changes. The
 *  sweep must iterate a snapshot instead. */
public class AIRobotSearchStationTest {

    @Test
    public void aStationThatDeregistersDuringTheSweepDoesNotCrashTheSearch() {
        // LinkedHashMap, not ArrayList: a list iterator can let a single removal slip past silently (the
        // cursor hops over the shifted tail), while a map values-view iterator throws on the next() after
        // ANY external removal — the exact failure mode RobotRegistry's HashMap exhibits.
        Map<BlockPos, DockingStation> live = new LinkedHashMap<>();

        MockRobotAccess robot = new MockRobotAccess();
        robot.setRegistry(new MockRobotAccess.InertRobotRegistry() {
            @Override
            public Collection<DockingStation> getStations() {
                return live.values();
            }

            @Override
            public void removeStation(DockingStation station) {
                live.remove(station.getPos());
            }
        });

        // First in iteration order, so its mid-sweep removal is always followed by another next() call.
        DockingStation stale = new DockingStation(new BlockPos(4, 4, 4), Direction.UP) {
            @Override
            public Iterable<StatementSlot> getActiveActions() {
                return Collections.emptyList();
            }

            @Override
            public boolean isInitialized() {
                // The pipe holder vanished between scans: deregister, exactly as DockingStationPipe's
                // getHolder() does when it finds the pipe gone.
                live.remove(getPos());
                return false;
            }
        };
        DockingStation healthy = new DockingStation(new BlockPos(5, 5, 5), Direction.UP) {
            @Override
            public Iterable<StatementSlot> getActiveActions() {
                return Collections.emptyList();
            }
        };
        live.put(stale.getPos(), stale);
        live.put(healthy.getPos(), healthy);

        AIRobotSearchStation ai = new AIRobotSearchStation(robot, station -> true, null);
        Assertions.assertDoesNotThrow(ai::start,
                "a station deregistering itself mid-sweep must not throw — the search iterates a snapshot "
                        + "of the registry's live station view");
        Assertions.assertSame(healthy, ai.targetStation,
                "the sweep must still visit (and here: select) the stations after the stale one");
    }
}
