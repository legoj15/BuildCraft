/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.boards;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.api.boards.RedstoneBoardRobotNBT;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.api.statements.IStatement;
import buildcraft.api.statements.IStatementParameter;
import buildcraft.api.statements.StatementParameterItemStack;
import buildcraft.api.statements.StatementSlot;
import buildcraft.robotics.ai.MockRobotAccess;
import buildcraft.robotics.statements.ActionRobotFilter;
import buildcraft.robotics.statements.TestStation;

/** The gate "Filter" action against the work boards — the conjunct 7.1.x AND-ed into every search and
 *  every seed fetch and the port had left out.
 *
 *  <p>7.1.x {@code BoardRobotGenericSearchBlock.updateFilter()/matchesGateFilter()} read the linked
 *  station's {@code ActionRobotFilter} parameters each cycle and required the candidate block to be one
 *  of them; a station with no Filter action passed everything. {@code BoardRobotPlanter} AND-ed the same
 *  filter into the seed it would fetch. */
public class BoardGateFilterTest extends VanillaSetupBaseTester {

    /** A robot whose linked station is settable — the only seam these predicates read. */
    private static class LinkedRobot extends MockRobotAccess {
        DockingStation linked;

        @Override
        public DockingStation getLinkedStation() {
            return linked;
        }
    }

    /** A concrete search board that hunts logs, so the gate filter is the only other conjunct. */
    private static class LogSearchBoard extends BoardRobotGenericSearchBlock {
        LogSearchBoard(IRobotAccess robot) {
            super(robot);
        }

        @Override
        public RedstoneBoardRobotNBT getNBTHandler() {
            return BoardRobotLumberjackNBT.INSTANCE;
        }

        @Override
        public boolean isExpectedBlock(BlockState state) {
            return state.is(Blocks.OAK_LOG) || state.is(Blocks.BIRCH_LOG);
        }
    }

    private static StatementSlot gateSlot(IStatement action, ItemStack... stacks) {
        StatementSlot slot = new StatementSlot();
        slot.statement = action;
        List<IStatementParameter> params = new ArrayList<>();
        for (ItemStack stack : stacks) {
            params.add(new StatementParameterItemStack(stack));
        }
        slot.parameters = params.toArray(new IStatementParameter[0]);
        return slot;
    }

    private static TestStation filterStation(ItemStack... stacks) {
        return new TestStation(List.of(gateSlot(new ActionRobotFilter(), stacks)));
    }

    // ── the search boards ────────────────────────────────────────────────────

    @Test
    public void aFilterActionNarrowsTheSearchToItsBlocks() {
        LinkedRobot robot = new LinkedRobot();
        robot.linked = filterStation(new ItemStack(Blocks.OAK_LOG));

        LogSearchBoard board = new LogSearchBoard(robot);
        board.updateFilter();

        Assertions.assertTrue(board.isSearchTarget(Blocks.OAK_LOG.defaultBlockState()),
                "the Filter action named oak logs, so an oak log is a target");
        Assertions.assertFalse(board.isSearchTarget(Blocks.BIRCH_LOG.defaultBlockState()),
                "a birch log matches the board's own predicate but not the gate's Filter action — 7.1.x "
                        + "AND-ed matchesGateFilter into every search board");
    }

    @Test
    public void noFilterActionSearchesEverythingTheBoardWants() {
        LinkedRobot robot = new LinkedRobot();
        robot.linked = new TestStation(Collections.emptyList());

        LogSearchBoard board = new LogSearchBoard(robot);
        board.updateFilter();

        Assertions.assertTrue(board.isSearchTarget(Blocks.OAK_LOG.defaultBlockState()),
                "a station with no Filter action restricts nothing");
        Assertions.assertTrue(board.isSearchTarget(Blocks.BIRCH_LOG.defaultBlockState()),
                "a station with no Filter action restricts nothing");
        Assertions.assertFalse(board.isSearchTarget(Blocks.STONE.defaultBlockState()),
                "the board's own predicate still applies");
    }

    @Test
    public void aFilterOfNonBlockItemsRestrictsNothing() {
        LinkedRobot robot = new LinkedRobot();
        robot.linked = filterStation(new ItemStack(Items.DIAMOND));

        LogSearchBoard board = new LogSearchBoard(robot);
        board.updateFilter();

        Assertions.assertTrue(board.isSearchTarget(Blocks.OAK_LOG.defaultBlockState()),
                "7.1.x only collected block items into the block filter, so a filter holding no block "
                        + "item leaves the search unrestricted");
    }

    @Test
    public void noLinkedStationSearchesEverythingTheBoardWants() {
        LinkedRobot robot = new LinkedRobot();
        robot.linked = null;

        LogSearchBoard board = new LogSearchBoard(robot);
        board.updateFilter();

        Assertions.assertTrue(board.isSearchTarget(Blocks.OAK_LOG.defaultBlockState()),
                "an unlinked robot must still search — the gate filter is simply not consulted");
    }

    @Test
    public void theFilterIsRefreshedEachCycle() {
        LinkedRobot robot = new LinkedRobot();
        robot.linked = filterStation(new ItemStack(Blocks.OAK_LOG));

        LogSearchBoard board = new LogSearchBoard(robot);
        board.updateFilter();
        Assertions.assertFalse(board.isSearchTarget(Blocks.BIRCH_LOG.defaultBlockState()),
                "precondition: oak only");

        // The player re-parameterises the gate mid-run; 7.1.x re-read the station every update().
        robot.linked = filterStation(new ItemStack(Blocks.BIRCH_LOG));
        board.updateFilter();
        Assertions.assertTrue(board.isSearchTarget(Blocks.BIRCH_LOG.defaultBlockState()),
                "re-reading the station each cycle is what makes the gate editable while robots run");
        Assertions.assertFalse(board.isSearchTarget(Blocks.OAK_LOG.defaultBlockState()),
                "the previous filter must not linger");
    }

    // ── the planter ──────────────────────────────────────────────────────────

    @Test
    public void aFilterActionNarrowsTheSeedThePlanterFetches() {
        LinkedRobot robot = new LinkedRobot();
        robot.linked = filterStation(new ItemStack(Items.PUMPKIN_SEEDS));

        BoardRobotPlanter planter = new BoardRobotPlanter(robot);

        Assertions.assertTrue(planter.seedFetchFilter().matches(new ItemStack(Items.PUMPKIN_SEEDS)),
                "the Filter action named pumpkin seeds, so those are what the planter fetches");
        Assertions.assertFalse(planter.seedFetchFilter().matches(new ItemStack(Items.WHEAT_SEEDS)),
                "wheat seeds are seeds, but the gate said pumpkin — 7.1.x AND-ed the work filter into "
                        + "the planter's fetch");
    }

    @Test
    public void noFilterActionLetsThePlanterFetchAnySeed() {
        LinkedRobot robot = new LinkedRobot();
        robot.linked = new TestStation(Collections.emptyList());

        BoardRobotPlanter planter = new BoardRobotPlanter(robot);

        Assertions.assertTrue(planter.seedFetchFilter().matches(new ItemStack(Items.WHEAT_SEEDS)),
                "an unfiltered station lets the planter fetch any seed");
        Assertions.assertFalse(planter.seedFetchFilter().matches(new ItemStack(Items.COBBLESTONE)),
                "the board's own seed predicate still applies");
    }

    @Test
    public void anUnlinkedPlanterStillFetchesSeeds() {
        LinkedRobot robot = new LinkedRobot();
        robot.linked = null;

        BoardRobotPlanter planter = new BoardRobotPlanter(robot);

        Assertions.assertTrue(planter.seedFetchFilter().matches(new ItemStack(Items.WHEAT_SEEDS)),
                "an unlinked planter must still be able to fetch a seed");
    }
}
