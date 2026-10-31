/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.ai;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.api.core.EnumPipePart;
import buildcraft.api.core.IZone;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.EntityRobotBase;
import buildcraft.api.statements.StatementSlot;
import buildcraft.api.transport.IInjectable;

/** {@link AIRobotDisposeItems} drops a robot's leftover cargo when no station will take it, and keeps it
 *  ({@code heldBack()}, the board then sleeps and tries again) when the only stations that could take it are out of
 *  reach for a reason that passes ({@link AIRobotSearchStation#unavailableTakers} lists them; #46, #68). A forbid is
 *  waited for without a cap, a claim or an unloaded chunk only {@code MAX_TRANSIENT_HOLDS} times until the next
 *  unload. A station the robot may use but could not reach, a station another robot has HOMED, a station outside the
 *  robot's load/unload zone (but for its home and the station it was evicted from), and an unloaded station other than
 *  the home when there is no zone are not a reason to wait: the cargo goes on the ground, as it did.
 *
 *  <p>The robot has no level, so the drop itself is replaced by a flag; the real spill is pinned by the
 *  {@code robot_dispose_items_drops_cargo_when_no_station} game test, and a real station read back from the save in an
 *  unloaded chunk by {@code robot_dispose_items_keeps_cargo_for_unloaded_station}. A flight to a station the
 *  registry stub cannot resolve ends at once, which is how a station the robot may use but cannot reach looks here. */
public class AIRobotDisposeHeldBackTest extends VanillaSetupBaseTester {

    private static final long OTHER_ROBOT = 7L;

    /** A robot with the real four cargo slots. */
    private static final class CargoRobot extends MockRobotAccess {
        private final ItemStack[] slots = { ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY };

        /** A real id: the mock's own is the null id, which no station can be taken by. */
        @Override
        public long getRobotId() {
            return 3L;
        }

        @Override
        public int getInventorySize() {
            return slots.length;
        }

        @Override
        public ItemStack getInventoryStack(int slot) {
            return slots[slot];
        }

        @Override
        public void setInventoryStack(int slot, ItemStack stack) {
            slots[slot] = stack;
        }

        @Override
        public boolean containsItems() {
            for (ItemStack stack : slots) {
                if (!stack.isEmpty()) {
                    return true;
                }
            }
            return false;
        }
    }

    /** A station that would take cobblestone, in whatever state the test puts it. */
    private static final class Acceptor extends DockingStation {
        boolean accepts = true;
        boolean forbids;
        boolean chunkLoaded = true;
        long takenBy = EntityRobotBase.NULL_ROBOT_ID;
        boolean takenAsHome;

        Acceptor(BlockPos pos) {
            super(pos, Direction.UP);
        }

        @Override
        public Iterable<StatementSlot> getActiveActions() {
            return Collections.emptyList();
        }

        @Override
        public boolean canRobotAcceptItem(ItemStack stack) {
            return accepts;
        }

        @Override
        public boolean isRobotForbidden(buildcraft.api.robots.IRobotAccess robot) {
            return forbids;
        }

        @Override
        public boolean isChunkLoaded() {
            return chunkLoaded;
        }

        @Override
        public boolean isTaken() {
            return takenBy != EntityRobotBase.NULL_ROBOT_ID;
        }

        @Override
        public long robotIdTaking() {
            return takenBy;
        }

        @Override
        public boolean isMainStation() {
            return takenAsHome;
        }

        @Override
        public EnumPipePart getItemOutputSide() {
            return EnumPipePart.DOWN;
        }

        /** None while the chunk is unloaded, as a station read back from the save answers until its pipe loads. */
        @Override
        public IInjectable getItemOutput() {
            if (!chunkLoaded) {
                return null;
            }
            return new IInjectable() {
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
        }
    }

    private final CargoRobot robot = new CargoRobot();
    private final List<DockingStation> stations = new ArrayList<>();
    private boolean dropped;

    {
        robot.setInventoryStack(0, new ItemStack(Items.COBBLESTONE, 5));
        robot.setRegistry(new MockRobotAccess.InertRobotRegistry() {
            @Override
            public Collection<DockingStation> getStations() {
                return stations;
            }

            /** Only the station the robot is docked at resolves, so the goto to it ends docked and every other
             *  flight fails at once. */
            @Override
            public DockingStation getStation(BlockPos pos, Direction side) {
                DockingStation dock = robot.getDockingStation();
                return dock != null && dock.getPos().equals(pos) && dock.side() == side ? dock : null;
            }
        });
    }

    private Acceptor acceptor(int x) {
        Acceptor station = new Acceptor(new BlockPos(x, 64, 0));
        stations.add(station);
        return station;
    }

    /** Runs a disposal to its end and returns it. Every flight here is instant; an unload at the station the robot is
     *  docked at waits its 40 cycles, so the disposal is cycled until it ends. */
    private AIRobotDisposeItems dispose() {
        dropped = false;
        AIRobotDisposeItems dispose = new AIRobotDisposeItems(robot) {
            @Override
            void dropCargo() {
                dropped = true;
            }
        };
        boolean[] ended = new boolean[1];
        AIRobot parent = new AIRobot(robot) {
            @Override
            public void delegateAIEnded(AIRobot ai) {
                ended[0] = true;
            }
        };
        parent.startDelegateAI(dispose);
        for (int cycle = 0; cycle < 100 && !ended[0]; cycle++) {
            dispose.cycle();
        }
        Assertions.assertTrue(ended[0], "fixture: the disposal ends");
        return dispose;
    }

    /** A zone that holds exactly the blocks listed. */
    private static IZone zoneOf(BlockPos... blocks) {
        return new IZone() {
            @Override
            public double distanceTo(BlockPos pos) {
                return contains(Vec3.atCenterOf(pos)) ? 0 : Double.MAX_VALUE;
            }

            @Override
            public double distanceToSquared(BlockPos pos) {
                return distanceTo(pos);
            }

            @Override
            public boolean contains(Vec3 point) {
                for (BlockPos block : blocks) {
                    if (block.equals(BlockPos.containing(point))) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public BlockPos getRandomBlockPos(Random rand) {
                return blocks[0];
            }
        };
    }

    private void assertKept(AIRobotDisposeItems dispose, String why) {
        Assertions.assertTrue(dispose.heldBack(), why);
        Assertions.assertFalse(dropped, "the cargo is not dropped: " + why);
    }

    private void assertDropped(AIRobotDisposeItems dispose, String why) {
        Assertions.assertFalse(dispose.heldBack(), why);
        Assertions.assertTrue(dropped, "the cargo is dropped: " + why);
    }

    // ---- what #46 kept, and what it must go on keeping ----

    @Test
    public void aStationThatForbidsTheRobotHoldsTheCargoBack() {
        acceptor(1).forbids = true;

        assertKept(dispose(), "the only taker forbids the robot, and a forbid can lift");
    }

    @Test
    public void noStationAtAllDropsTheCargo() {
        assertDropped(dispose(), "no station would take it");
    }

    @Test
    public void aStationThatWouldNotTakeTheCargoDoesNotHoldItBack() {
        Acceptor refuses = acceptor(1);
        refuses.forbids = true;
        refuses.accepts = false;

        assertDropped(dispose(), "a station whose gate refuses cobblestone is no taker, forbidding or not");
    }

    // ---- #68: a transient state is not "no taker" ----

    @Test
    public void aStationAnotherRobotIsVisitingHoldsTheCargoBack() {
        acceptor(1).takenBy = OTHER_ROBOT;

        assertKept(dispose(), "the only taker is claimed by another robot for now, and the claim ends");
    }

    @Test
    public void aStationInAnUnloadedChunkInTheZoneHoldsTheCargoBack() {
        robot.setZoneToLoadUnload(zoneOf(new BlockPos(1, 64, 0)));
        acceptor(1).chunkLoaded = false;

        assertKept(dispose(), "the station in the zone is in an unloaded chunk, which loads again, and cannot say "
                + "until then whether it would take the cargo");
    }

    @Test
    public void aForbiddingStationInAnUnloadedChunkHoldsTheCargoBack() {
        robot.setZoneToLoadUnload(zoneOf(new BlockPos(1, 64, 0)));
        Acceptor station = acceptor(1);
        station.chunkLoaded = false;
        station.forbids = true;

        assertKept(dispose(), "unloaded and forbidding are both passing states");
    }

    @Test
    public void anUnloadedStationDoesNotHoldTheCargoBackWithoutAZone() {
        acceptor(1).chunkLoaded = false;

        assertDropped(dispose(), "with no zone the search covers the whole level, so a station in any unloaded chunk "
                + "would keep the robot waiting until someone went there");
    }

    @Test
    public void aStationAnotherRobotHasHomedDoesNotHoldTheCargoBack() {
        Acceptor station = acceptor(1);
        station.takenBy = OTHER_ROBOT;
        station.takenAsHome = true;

        assertDropped(dispose(), "another robot's home moves only on that robot's own redirect, which is rare, and no "
                + "search ever offered it to this robot");
    }

    @Test
    public void aStationTheRobotMayUseButCouldNotReachDropsTheCargo() {
        // A usable station next to a claimed one: the flight to the usable one failed (the registry stub cannot
        // resolve it), which is not a state that passes, so the claimed station is no reason to wait.
        acceptor(1);
        acceptor(2).takenBy = OTHER_ROBOT;

        assertDropped(dispose(), "the robot could use the free station and failed to reach it");
    }

    @Test
    public void aStationTheRobotHoldsItselfIsOneItMayUse() {
        acceptor(1).takenBy = robot.getRobotId();

        assertDropped(dispose(), "its own claim is a station it may use, not one that is taken: reaching it failed");
    }

    // ---- the zone is still honoured, with the exemptions the search itself makes ----

    @Test
    public void aTransientTakerOutsideTheZoneDoesNotHoldTheCargoBack() {
        robot.setZoneToLoadUnload(zoneOf(new BlockPos(100, 64, 0)));
        acceptor(1).takenBy = OTHER_ROBOT;
        acceptor(2).chunkLoaded = false;
        acceptor(3).forbids = true;

        assertDropped(dispose(), "no search would ever offer a station outside the zone, however it passes");
    }

    @Test
    public void aTransientTakerInsideTheZoneHoldsTheCargoBack() {
        robot.setZoneToLoadUnload(zoneOf(new BlockPos(1, 64, 0)));
        acceptor(1).takenBy = OTHER_ROBOT;

        assertKept(dispose(), "the claimed station is in the zone");
    }

    @Test
    public void theHomeOutsideTheZoneStillCountsWhenItForbidsTheRobot() {
        // A Goto Station redirect takes its target as the new home, so a robot sent out of its zone is homed there.
        robot.setZoneToLoadUnload(zoneOf(new BlockPos(100, 64, 0)));
        Acceptor home = acceptor(1);
        home.forbids = true;
        robot.setLinkedStation(home);

        assertKept(dispose(), "the unload search lets the home match outside the zone, and so does this");
    }

    @Test
    public void theHomeInAnUnloadedChunkHoldsTheCargoWithoutAZone() {
        // A robot working away from its home. With no zone only the home counts when it is unloaded: any other
        // unloaded station in the level is no more this robot's than a sibling base's.
        Acceptor home = acceptor(1);
        home.chunkLoaded = false;
        home.takenBy = robot.getRobotId();
        home.takenAsHome = true;
        robot.setLinkedStation(home);

        assertKept(dispose(), "the robot's own home is unloaded, and loads again when a player goes back");
    }

    // ---- the station the robot was evicted from ----

    @Test
    public void theStationTheRobotWasEvictedFromCountsOutsideTheZone() {
        // A Delivery robot docked at a requester outside its zone (a plain visit) is evicted when the requester starts
        // forbidding it, and AIRobotMain undocks it before any disposal runs, so the requester is neither the dock nor
        // in the zone by the time the disposal asks.
        robot.setZoneToLoadUnload(zoneOf(new BlockPos(100, 64, 0)));
        Acceptor requester = acceptor(1);
        requester.forbids = true;
        robot.setEvictedFromStation(requester);

        assertKept(dispose(), "the robot was unloading at that station, and the forbid that evicted it can lift");

        requester.forbids = false;
        AIRobotSearchStation search = new AIRobotSearchStation(robot,
                station -> AIRobotUnload.unload(robot, station, false), robot.getZoneToLoadUnload(), true);
        search.start();
        Assertions.assertSame(requester, search.targetStation,
                "once the forbid lifts the unload search offers that station again, so the wait has an end");

        requester.forbids = true;
        requester.accepts = false;
        assertDropped(dispose(), "a station that would not take the cargo is no reason to wait, evicted from or not");
        requester.accepts = true;
        assertDropped(dispose(), "a disposal that gave the cargo up forgets the eviction: the station is outside the "
                + "zone again");
    }

    // ---- a hold for a taker that is only claimed or unloaded has a cap ----

    @Test
    public void anUnloadedStationInTheZoneHoldsTheCargoOnlyAFewTimes() {
        // An unloaded station cannot be asked, so one that would never take cobblestone holds the cargo as well.
        // Without a cap a chunk no player goes back to kept the robot asleep with its load for good.
        robot.setZoneToLoadUnload(zoneOf(new BlockPos(1, 64, 0)));
        Acceptor station = acceptor(1);
        station.chunkLoaded = false;
        station.accepts = false;

        for (int hold = 1; hold <= AIRobotDisposeItems.MAX_TRANSIENT_HOLDS; hold++) {
            assertKept(dispose(), "hold " + hold + ": the unloaded station may be a taker");
        }
        assertDropped(dispose(), "the station stayed unloaded through every hold");
        assertDropped(dispose(), "and the next leftover does not wait for it again before the robot unloads anywhere");
    }

    @Test
    public void aClaimedTakerHoldsTheCargoOnlyAFewTimesUntilTheRobotUnloadsAgain() {
        acceptor(1).takenBy = OTHER_ROBOT;

        for (int hold = 1; hold <= AIRobotDisposeItems.MAX_TRANSIENT_HOLDS; hold++) {
            assertKept(dispose(), "hold " + hold + ": the claim on the only taker ends with the visit");
        }
        assertDropped(dispose(), "a claim that outlasts every hold is not a visit that ends");

        // The robot docks at a free station and empties its cargo there.
        Acceptor dock = acceptor(2);
        dock.takenBy = robot.getRobotId();
        robot.setDockingStation(dock);
        AIRobotDisposeItems unloaded = dispose();
        Assertions.assertFalse(unloaded.heldBack() || dropped || robot.containsItems(),
                "fixture: the docked station takes the cargo");

        stations.remove(dock);
        robot.setDockingStation(null);
        robot.setInventoryStack(0, new ItemStack(Items.COBBLESTONE, 5));
        assertKept(dispose(), "an unload starts the count afresh, so the busy taker is waited for again");
    }

    @Test
    public void aForbiddingTakerHoldsTheCargoWithoutACap() {
        // #46: a forbid is the player's own gate and lifts when they say; the cap is for states nobody controls.
        acceptor(1).forbids = true;
        acceptor(2).takenBy = OTHER_ROBOT;

        for (int hold = 1; hold <= 2 * AIRobotDisposeItems.MAX_TRANSIENT_HOLDS + 1; hold++) {
            assertKept(dispose(), "hold " + hold + ": a taker forbids the robot");
        }
    }
}
