/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.statements;

import java.util.List;
import java.util.function.Predicate;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
//? if >=26.2 {
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
//?}

import buildcraft.VanillaSetupBaseTester;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.IDockingStationProvider;
import buildcraft.api.statements.IActionInternal;
import buildcraft.api.statements.IStatementContainer;
import buildcraft.api.statements.ITriggerInternal;
import buildcraft.api.statements.StatementManager;
import buildcraft.robotics.BCRoboticsStatements;

/** The Ph6 providers ({@link RobotsActionProvider} / {@link RobotsTriggerProvider}) must be registered
 *  with {@link StatementManager} (the mod boot does it) and offer the 14 station/robot actions and 4
 *  robot triggers to any container whose tile hosts a docking station — discovery through
 *  {@code RobotUtils.getStations} on the {@link IDockingStationProvider} seam. */
public class RoboticsProvidersTest extends VanillaSetupBaseTester {

    /** A bare block entity that hosts a station through the API seam — what a future station-hosting
     *  tile (or a {@code RobotStationPluggable}) looks like to the providers. */
    private static class StationTile extends BlockEntity implements IDockingStationProvider {
        private final DockingStation station;

        StationTile(DockingStation station) {
            // Any type serves — the tile is a bare station seam for the providers. The block state MUST
            // match the type: the BlockEntity ctor validates it against the type's valid blocks on 26.x.
            // 26.2 deleted the static BlockEntityType fields, and a hand-built type's intrusive holder
            // needs an unfrozen registry — so 26.2 takes the CHEST type from the built-in registry.
            //? if >=26.2 {
            /*super(BuiltInRegistries.BLOCK_ENTITY_TYPE.getValue(Identifier.withDefaultNamespace("chest")),
                    BlockPos.ZERO, Blocks.CHEST.defaultBlockState());*/
            //?} else {
            super(BlockEntityType.CHEST, BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
            //?}
            this.station = station;
        }

        @Override
        public DockingStation getStation() {
            return station;
        }
    }

    private static IStatementContainer containerFor(DockingStation station) {
        StationTile tile = new StationTile(station);
        return new IStatementContainer() {
            @Override
            public BlockEntity getTile() {
                return tile;
            }

            @Override
            public BlockEntity getNeighbourTile(Direction side) {
                return null;
            }
        };
    }

    /** The 14 action instances the catalog registers, exactly the set {@link RobotsActionProvider} must
     *  offer for a fully equipped station. */
    private static final IActionInternal[] ALL_ACTIONS = {
            BCRoboticsStatements.ACTION_ROBOT_GOTO_STATION,
            BCRoboticsStatements.ACTION_ROBOT_WORK_IN_AREA,
            BCRoboticsStatements.ACTION_ROBOT_LOAD_UNLOAD_AREA,
            BCRoboticsStatements.ACTION_ROBOT_WAKE_UP,
            BCRoboticsStatements.ACTION_ROBOT_FILTER,
            BCRoboticsStatements.ACTION_ROBOT_FILTER_TOOL,
            BCRoboticsStatements.ACTION_STATION_FORBID_ROBOT,
            BCRoboticsStatements.ACTION_STATION_FORCE_ROBOT,
            BCRoboticsStatements.ACTION_STATION_REQUEST_ITEMS,
            BCRoboticsStatements.ACTION_STATION_ACCEPT_ITEMS,
            BCRoboticsStatements.ACTION_STATION_PROVIDE_ITEMS,
            BCRoboticsStatements.ACTION_STATION_PROVIDE_FLUIDS,
            BCRoboticsStatements.ACTION_STATION_ACCEPT_FLUIDS,
            BCRoboticsStatements.ACTION_STATION_MACHINE_REQUEST,
    };

    private static final ITriggerInternal[] ALL_TRIGGERS = {
            BCRoboticsStatements.TRIGGER_ROBOT_SLEEP,
            BCRoboticsStatements.TRIGGER_ROBOT_IN_STATION,
            BCRoboticsStatements.TRIGGER_ROBOT_LINKED,
            BCRoboticsStatements.TRIGGER_ROBOT_RESERVED,
    };

    private static void assertContainsActions(List<IActionInternal> actions, IActionInternal[] expected) {
        for (IActionInternal action : expected) {
            Predicate<IActionInternal> same = a -> a == action;
            Assertions.assertTrue(actions.stream().anyMatch(same),
                    "the provider must offer action '" + action.getUniqueTag() + "'");
        }
    }

    private static void assertContainsTriggers(List<ITriggerInternal> triggers, ITriggerInternal[] expected) {
        for (ITriggerInternal trigger : expected) {
            Predicate<ITriggerInternal> same = t -> t == trigger;
            Assertions.assertTrue(triggers.stream().anyMatch(same),
                    "the provider must offer trigger '" + trigger.getUniqueTag() + "'");
        }
    }

    @Test
    public void actionProviderOffersFullCatalog() {
        assertContainsActions(
                StatementManager.getInternalActions(containerFor(new TestStation(List.of()))),
                ALL_ACTIONS);
    }

    @Test
    public void triggerProviderOffersRobotTriggers() {
        assertContainsTriggers(
                StatementManager.getInternalTriggers(containerFor(new TestStation(List.of()))),
                ALL_TRIGGERS);
    }

    @Test
    public void providersOfferNothingWithoutAStation() {
        // A tile that is not an IDockingStationProvider must not be offered robot statements.
        IStatementContainer bare = new IStatementContainer() {
            @Override
            public BlockEntity getTile() {
                return new StationTile(null);
            }

            @Override
            public BlockEntity getNeighbourTile(Direction side) {
                return null;
            }
        };
        // The station seam returns null here, so discovery finds no station.
        List<IActionInternal> actions = StatementManager.getInternalActions(bare);
        for (IActionInternal action : ALL_ACTIONS) {
            Assertions.assertFalse(actions.stream().anyMatch(a -> a == action),
                    "a stationless tile must not be offered '" + action.getUniqueTag() + "'");
        }
    }
}
