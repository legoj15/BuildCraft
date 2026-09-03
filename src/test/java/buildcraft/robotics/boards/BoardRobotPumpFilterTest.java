/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.boards;

import java.util.Collections;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.api.core.IFluidFilter;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.statements.StatementSlot;
import buildcraft.robotics.ai.MockRobotAccess;

/** The pump board must honour the gate fluid filter on its LINKED (home) station — 7.1.x's
 *  {@code BoardRobotPump.updateFilter()} + {@code matchesGateFilter}. Without it a pump with a "Filter"
 *  gate holding a lava bucket happily pumps the first water pool it finds.
 *
 *  <p>The filter is read through the D1 seam {@link DockingStation#getRobotFluidFilter()} (which
 *  {@code DockingStationPipe} implements from the real gate actions), so the board's half of the contract
 *  is pinned here without a Level; the whole search predicate additionally needs the {@code fluidSource}
 *  world property and a live level, which the {@code world_properties_match} game test covers. */
public class BoardRobotPumpFilterTest extends VanillaSetupBaseTester {

    private final MockRobotAccess robot = new MockRobotAccess();

    @Test
    public void aPumpWithNoLinkedStationPumpsAnything() {
        BoardRobotPump pump = new BoardRobotPump(robot);
        pump.update();

        Assertions.assertTrue(pump.matchesGateFilter(Blocks.WATER.defaultBlockState()),
                "no linked station means no filter — water passes");
        Assertions.assertTrue(pump.matchesGateFilter(Blocks.LAVA.defaultBlockState()),
                "no linked station means no filter — lava passes");
    }

    @Test
    public void anUnfilteredStationPumpsAnything() {
        // The gateless/unfiltered contract: DockingStation's default fluid filter passes every fluid.
        robot.setLinkedStation(new FilteredStation(null));
        BoardRobotPump pump = new BoardRobotPump(robot);
        pump.update();

        Assertions.assertTrue(pump.matchesGateFilter(Blocks.WATER.defaultBlockState()),
                "an unfiltered station lets the pump take water");
        Assertions.assertTrue(pump.matchesGateFilter(Blocks.LAVA.defaultBlockState()),
                "an unfiltered station lets the pump take lava");
    }

    @Test
    public void aLavaFilteredStationRejectsWater() {
        robot.setLinkedStation(new FilteredStation(fluid -> fluid.getFluid() == Fluids.LAVA));
        BoardRobotPump pump = new BoardRobotPump(robot);
        pump.update();

        Assertions.assertFalse(pump.matchesGateFilter(Blocks.WATER.defaultBlockState()),
                "a lava-only gate filter must keep the pump away from water");
        Assertions.assertTrue(pump.matchesGateFilter(Blocks.LAVA.defaultBlockState()),
                "…while lava is exactly what it was told to pump");
    }

    @Test
    public void aNonFluidBlockNeverMatchesAFilter() {
        robot.setLinkedStation(new FilteredStation(fluid -> fluid.getFluid() == Fluids.LAVA));
        BoardRobotPump pump = new BoardRobotPump(robot);
        pump.update();

        Assertions.assertFalse(pump.matchesGateFilter(Blocks.STONE.defaultBlockState()),
                "stone holds no fluid, so it can never satisfy a fluid filter");
    }

    /** A station whose gate fluid filter is whatever the test says (null = the permissive default). */
    private static final class FilteredStation extends DockingStation {
        private final IFluidFilter filter;

        FilteredStation(IFluidFilter filter) {
            super();
            this.filter = filter;
        }

        @Override
        public Iterable<StatementSlot> getActiveActions() {
            return Collections.emptyList();
        }

        @Override
        public IFluidFilter getRobotFluidFilter() {
            return filter == null ? super.getRobotFluidFilter() : filter;
        }
    }
}
