/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.statements;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
//? if >=26.2 {
/*import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;*/
//?}
//? if >=1.21.10 {
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
//?} else {
/*import net.neoforged.neoforge.fluids.capability.IFluidHandler;*/
//?}

import buildcraft.VanillaSetupBaseTester;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.IDockingStationProvider;
import buildcraft.api.robots.IRequestProvider;
import buildcraft.api.statements.IActionInternal;
import buildcraft.api.statements.IStatementContainer;
import buildcraft.api.statements.ITriggerInternal;
import buildcraft.api.statements.StatementManager;
import buildcraft.api.transport.IInjectable;
import buildcraft.robotics.BCRoboticsStatements;

/** The Ph6 providers ({@link RobotsActionProvider} / {@link RobotsTriggerProvider}) must be registered
 *  with {@link StatementManager} (the mod boot does it) and offer the robot statements to any container
 *  whose tile hosts a docking station — discovery through {@code RobotUtils.getStations} on the
 *  {@link IDockingStationProvider} seam.
 *
 *  <p>The eight robot actions (goto/work/load-unload/wake-up/filter/filter-tool/forbid/force) go on every
 *  station. The six cargo actions are gated on what the station can actually move, exactly as 7.1.x's
 *  {@code RobotsActionProvider} gated them:
 *  <ul>
 *  <li>Request Items / Accept Items — an item pipe (7.1.x {@code getPipeType() == ITEM}; here the station's
 *      item OUTPUT, which {@code DockingStationPipe} exposes exactly on item pipes);</li>
 *  <li>Accept Fluids — a fluid pipe (the station's fluid output);</li>
 *  <li>Provide Items / Provide Fluids — a wooden pipe facing an inventory / tank (the station's item / fluid
 *      INPUT, the very seams 7.1.x tested);</li>
 *  <li>Request Needed Items — a real request provider beside the pipe. 7.1.x tested
 *      {@code getRequestProvider() != null}, but its pipe station answered {@code this} as the fallback, so the
 *      check never failed; the port asks for a provider OTHER than the station itself, which is what the
 *      check was evidently meant to say.</li>
 *  </ul> */
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

    /** What a station can move — each flag switches one of the provider's gating seams on. */
    private enum Seam { ITEM_OUTPUT, ITEM_INPUT, FLUID_OUTPUT, FLUID_INPUT, FOREIGN_REQUESTER, SELF_REQUESTER }

    /** A station whose cargo seams are switched on one by one. Reuses {@link TestStation}'s non-null
     *  container / fluid handler / request provider for the "present" answers; everything off answers null,
     *  which is what a station on a plain power pipe looks like. */
    private static final class GatedStation extends TestStation implements IRequestProvider {
        private final Set<Seam> seams;

        GatedStation(Set<Seam> seams) {
            super(List.of());
            this.seams = seams;
        }

        private final IInjectable itemOutput = new IInjectable() {
            @Override
            public boolean canInjectItems(Direction from) {
                return true;
            }

            @Override
            public ItemStack injectItem(ItemStack stack, boolean doAdd, Direction from, DyeColor color,
                    double speed) {
                return ItemStack.EMPTY;
            }
        };

        @Override
        public IInjectable getItemOutput() {
            return seams.contains(Seam.ITEM_OUTPUT) ? itemOutput : null;
        }

        @Override
        public SimpleContainer getItemInput() {
            return seams.contains(Seam.ITEM_INPUT) ? super.getItemInput() : null;
        }

        //? if >=1.21.10 {
        @Override
        public ResourceHandler<FluidResource> getFluidOutput() {
            return seams.contains(Seam.FLUID_OUTPUT) ? super.getFluidInput() : null;
        }

        @Override
        public ResourceHandler<FluidResource> getFluidInput() {
            return seams.contains(Seam.FLUID_INPUT) ? super.getFluidInput() : null;
        }
        //?} else {
        /*@Override
        public IFluidHandler getFluidOutput() {
            return seams.contains(Seam.FLUID_OUTPUT) ? super.getFluidInput() : null;
        }

        @Override
        public IFluidHandler getFluidInput() {
            return seams.contains(Seam.FLUID_INPUT) ? super.getFluidInput() : null;
        }*/
        //?}

        @Override
        public IRequestProvider getRequestProvider() {
            if (seams.contains(Seam.FOREIGN_REQUESTER)) {
                return super.getRequestProvider();
            }
            // DockingStationPipe's fallback: with no requester beside the pipe, the station answers itself.
            return seams.contains(Seam.SELF_REQUESTER) ? this : null;
        }

        @Override
        public int getRequestsCount() {
            return 0;
        }

        @Override
        public ItemStack getRequest(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack offerItem(int slot, ItemStack stack) {
            return stack;
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

    private static List<IActionInternal> actionsFor(Seam... seams) {
        Set<Seam> set = EnumSet.noneOf(Seam.class);
        set.addAll(List.of(seams));
        return StatementManager.getInternalActions(containerFor(new GatedStation(set)));
    }

    /** The eight actions every station gets, whatever its pipe carries. */
    private static final IActionInternal[] ROBOT_ACTIONS = {
            BCRoboticsStatements.ACTION_ROBOT_GOTO_STATION,
            BCRoboticsStatements.ACTION_ROBOT_WORK_IN_AREA,
            BCRoboticsStatements.ACTION_ROBOT_LOAD_UNLOAD_AREA,
            BCRoboticsStatements.ACTION_ROBOT_WAKE_UP,
            BCRoboticsStatements.ACTION_ROBOT_FILTER,
            BCRoboticsStatements.ACTION_ROBOT_FILTER_TOOL,
            BCRoboticsStatements.ACTION_STATION_FORBID_ROBOT,
            BCRoboticsStatements.ACTION_STATION_FORCE_ROBOT,
    };

    /** The six cargo actions, each gated on one seam. */
    private static final IActionInternal[] CARGO_ACTIONS = {
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

    private static boolean offers(List<IActionInternal> actions, IActionInternal action) {
        Predicate<IActionInternal> same = a -> a == action;
        return actions.stream().anyMatch(same);
    }

    private static void assertContainsActions(List<IActionInternal> actions, IActionInternal... expected) {
        for (IActionInternal action : expected) {
            Assertions.assertTrue(offers(actions, action),
                    "the provider must offer action '" + action.getUniqueTag() + "'");
        }
    }

    /** Exactly {@code expected} out of the six cargo actions — no more. */
    private static void assertCargoExactly(List<IActionInternal> actions, IActionInternal... expected) {
        List<IActionInternal> want = List.of(expected);
        for (IActionInternal action : CARGO_ACTIONS) {
            Assertions.assertEquals(want.contains(action), offers(actions, action),
                    "cargo action '" + action.getUniqueTag() + "' must be offered "
                            + (want.contains(action) ? "here" : "only where its seam exists"));
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
    public void aFullyEquippedStationGetsTheFullCatalog() {
        List<IActionInternal> actions = actionsFor(Seam.ITEM_OUTPUT, Seam.ITEM_INPUT, Seam.FLUID_OUTPUT,
                Seam.FLUID_INPUT, Seam.FOREIGN_REQUESTER);
        assertContainsActions(actions, ROBOT_ACTIONS);
        assertContainsActions(actions, CARGO_ACTIONS);
    }

    @Test
    public void aStationThatMovesNothingGetsOnlyTheRobotActions() {
        // A station on a power pipe: no item or fluid flow, no requester.
        List<IActionInternal> actions = actionsFor();
        assertContainsActions(actions, ROBOT_ACTIONS);
        assertCargoExactly(actions);
    }

    @Test
    public void anItemPipeOffersRequestAndAcceptItemsButNotProvide() {
        // A plain (non-wooden) item pipe: items can be unloaded into it, nothing can be pulled out.
        List<IActionInternal> actions = actionsFor(Seam.ITEM_OUTPUT, Seam.SELF_REQUESTER);
        assertContainsActions(actions, ROBOT_ACTIONS);
        assertCargoExactly(actions, BCRoboticsStatements.ACTION_STATION_REQUEST_ITEMS,
                BCRoboticsStatements.ACTION_STATION_ACCEPT_ITEMS);
    }

    @Test
    public void aWoodenItemPipeFacingAnInventoryAlsoOffersProvideItems() {
        List<IActionInternal> actions = actionsFor(Seam.ITEM_OUTPUT, Seam.ITEM_INPUT, Seam.SELF_REQUESTER);
        assertCargoExactly(actions, BCRoboticsStatements.ACTION_STATION_REQUEST_ITEMS,
                BCRoboticsStatements.ACTION_STATION_ACCEPT_ITEMS,
                BCRoboticsStatements.ACTION_STATION_PROVIDE_ITEMS);
    }

    @Test
    public void aFluidPipeOffersAcceptFluidsOnly() {
        List<IActionInternal> actions = actionsFor(Seam.FLUID_OUTPUT, Seam.SELF_REQUESTER);
        assertContainsActions(actions, ROBOT_ACTIONS);
        assertCargoExactly(actions, BCRoboticsStatements.ACTION_STATION_ACCEPT_FLUIDS);
    }

    @Test
    public void aWoodenFluidPipeFacingATankAlsoOffersProvideFluids() {
        List<IActionInternal> actions = actionsFor(Seam.FLUID_OUTPUT, Seam.FLUID_INPUT, Seam.SELF_REQUESTER);
        assertCargoExactly(actions, BCRoboticsStatements.ACTION_STATION_ACCEPT_FLUIDS,
                BCRoboticsStatements.ACTION_STATION_PROVIDE_FLUIDS);
    }

    @Test
    public void requestNeededItemsNeedsARequesterOtherThanTheStationItself() {
        assertCargoExactly(actionsFor(Seam.SELF_REQUESTER));
        assertCargoExactly(actionsFor(Seam.FOREIGN_REQUESTER),
                BCRoboticsStatements.ACTION_STATION_MACHINE_REQUEST);
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
        for (IActionInternal action : ROBOT_ACTIONS) {
            Assertions.assertFalse(offers(actions, action),
                    "a stationless tile must not be offered '" + action.getUniqueTag() + "'");
        }
        for (IActionInternal action : CARGO_ACTIONS) {
            Assertions.assertFalse(offers(actions, action),
                    "a stationless tile must not be offered '" + action.getUniqueTag() + "'");
        }
    }
}
