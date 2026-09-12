/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.robotics.ai;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.IRequestProvider;
import buildcraft.api.robots.ResourceId;
import buildcraft.api.robots.ResourceIdRequest;
import buildcraft.api.statements.StatementSlot;
import buildcraft.robotics.StackRequest;

/** The request-search and request-delivery AIs against the {@link MockRobotAccess} seam — no level, no
 *  entity, no pipes: a provider double behind a station double, and a registry double that records the
 *  reservation. The flight legs are pinned by the {@code DeliveryRequesterTester} game tests; these pin
 *  the choosing and the arithmetic. */
public class AIRobotRequestNetworkTest extends VanillaSetupBaseTester {

    /** A provider double: slot 0 owes {@code owed}, every other slot owes nothing. */
    private static IRequestProvider providerOwing(ItemStack owed) {
        return new IRequestProvider() {
            @Override
            public int getRequestsCount() {
                return 3;
            }

            @Override
            public ItemStack getRequest(int slot) {
                return slot == 0 ? owed.copy() : ItemStack.EMPTY;
            }

            @Override
            public ItemStack offerItem(int slot, ItemStack stack) {
                return stack;
            }
        };
    }

    /** A station double whose provider is {@code provider}. */
    private static DockingStation stationWith(IRequestProvider provider) {
        return new DockingStation(new BlockPos(3, 2, 1), Direction.UP) {
            @Override
            public Iterable<StatementSlot> getActiveActions() {
                return List.of();
            }

            @Override
            public IRequestProvider getRequestProvider() {
                return provider;
            }
        };
    }

    /** A registry double with one station, recording reservations like the real one. */
    private static final class RecordingRegistry extends MockRobotAccess.InertRobotRegistry {
        final DockingStation station;
        final Set<ResourceId> taken = new HashSet<>();

        RecordingRegistry(DockingStation station) {
            this.station = station;
        }

        @Override
        public List<DockingStation> getStations() {
            return List.of(station);
        }
        @Override
        public DockingStation getStation(BlockPos pos, Direction side) {
            return pos.equals(station.index()) && side == station.side() ? station : null;
        }

        @Override
        public boolean isTaken(ResourceId resourceId) {
            return taken.contains(resourceId);
        }

        @Override
        public boolean take(ResourceId resourceId, long robotId) {
            taken.add(resourceId);
            return true;
        }

        @Override
        public void release(ResourceId resourceId) {
            taken.remove(resourceId);
        }
    }

    private static MockRobotAccess robotOn(RecordingRegistry registry) {
        MockRobotAccess robot = new MockRobotAccess();
        robot.setRegistry(registry);
        return robot;
    }

    // ── AIRobotSearchStackRequest ────────────────────────────────────────

    @Test
    public void searchFindsAndReservesAnOpenRequest() {
        DockingStation station = stationWith(providerOwing(new ItemStack(Items.DIAMOND, 5)));
        RecordingRegistry registry = new RecordingRegistry(station);
        MockRobotAccess robot = robotOn(registry);

        AIRobotSearchStackRequest ai =
                new AIRobotSearchStackRequest(robot, stack -> true, new ArrayList<>());
        ai.start();

        Assertions.assertTrue(ai.success(), "an open, unfiltered, unclaimed request must be found");
        Assertions.assertNotNull(ai.request);
        Assertions.assertTrue(ItemStack.matches(new ItemStack(Items.DIAMOND, 5), ai.request.getStack()),
                "the found request carries the provider's owed stack");
        Assertions.assertSame(station, ai.request.getStation(null),
                "the request is anchored to the station the search chose");
        Assertions.assertEquals(
                new ResourceIdRequest(station, 0), ai.request.getResourceId(null),
                "the reservation id is the station plus the provider slot");
        Assertions.assertTrue(registry.taken.contains(ai.request.getResourceId(null)),
                "a successful search RESERVES the request — two robots cannot both take it");
    }

    @Test
    public void searchSkipsAnAlreadyTakenRequest() {
        DockingStation station = stationWith(providerOwing(new ItemStack(Items.DIAMOND, 5)));
        RecordingRegistry registry = new RecordingRegistry(station);
        registry.taken.add(new ResourceIdRequest(station, 0));
        MockRobotAccess robot = robotOn(registry);

        AIRobotSearchStackRequest ai =
                new AIRobotSearchStackRequest(robot, stack -> true, new ArrayList<>());
        ai.start();

        Assertions.assertFalse(ai.success(),
                "a request another robot holds must be invisible to this one");
    }

    @Test
    public void searchSkipsBlacklistedAndFilteredOutStacks() {
        // Blacklisted: the board already failed to load this exact stack once.
        List<ItemStack> blacklist = List.of(new ItemStack(Items.DIAMOND, 5));
        DockingStation stationB = stationWith(providerOwing(new ItemStack(Items.DIAMOND, 5)));
        MockRobotAccess robotBlacklist = robotOn(new RecordingRegistry(stationB));
        AIRobotSearchStackRequest blacklisted =
                new AIRobotSearchStackRequest(robotBlacklist, stack -> true, blacklist);
        blacklisted.start();
        Assertions.assertFalse(blacklisted.success(),
                "a stack the board blacklisted (its load failed once) is not re-chosen");

        // Filtered out: the linked station's gate filter refuses diamonds.
        DockingStation stationC = stationWith(providerOwing(new ItemStack(Items.DIAMOND, 5)));
        MockRobotAccess robotFiltered = robotOn(new RecordingRegistry(stationC));
        AIRobotSearchStackRequest filtered = new AIRobotSearchStackRequest(robotFiltered,
                stack -> stack.getItem() != Items.DIAMOND, new ArrayList<>());
        filtered.start();
        Assertions.assertFalse(filtered.success(),
                "a request the robot's gate filter refuses is skipped");
    }

    // ── AIRobotDeliverRequested ──────────────────────────────────────────

    /** A delivery robot mock: four real inventory slots, settable docking station. */
    private static final class DeliveryMockRobot extends MockRobotAccess {
        private final ItemStack[] slots = new ItemStack[4];

        {
            java.util.Arrays.fill(slots, ItemStack.EMPTY);
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
            for (ItemStack slot : slots) {
                if (slot != null && !slot.isEmpty()) {
                    return true;
                }
            }
            return false;
        }
    }

    /** A provider double that accepts its template's item into slot 0 up to the template count, mirroring
     *  TileRequester's offer arithmetic (pinned independently in {@code TileRequesterMathTest}). */
    private static final class AcceptingProvider implements IRequestProvider {
        private final ItemStack template;
        private int stored;

        AcceptingProvider(ItemStack template) {
            this.template = template;
        }

        @Override
        public int getRequestsCount() {
            return 1;
        }

        @Override
        public ItemStack getRequest(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack offerItem(int slot, ItemStack stack) {
            if (!ItemStack.isSameItemSameComponents(template, stack)) {
                return stack;
            }
            int room = template.getCount() - stored;
            int taken = Math.min(room, stack.getCount());
            if (taken <= 0) {
                return stack;
            }
            stored += taken;
            ItemStack leftover = stack.copy();
            leftover.setCount(stack.getCount() - taken);
            return leftover;
        }

        int delivered() {
            return stored;
        }
    }

    @Test
    public void deliverOffersMatchingCargoAndKeepsTheExcess() {
        // The provider wants 5 diamonds; the robot carries 3 diamonds + 4 dirt.
        AcceptingProvider provider = new AcceptingProvider(new ItemStack(Items.DIAMOND, 5));
        DockingStation station = stationWith(provider);

        DeliveryMockRobot robot = new DeliveryMockRobot();
        robot.setDockingStation(station); // already docked: AIRobotGotoStation short-circuits to success
        RecordingRegistry registry = new RecordingRegistry(station);
        registry.take(new ResourceIdRequest(station, 0), robot.getRobotId());
        robot.setRegistry(registry);
        robot.setInventoryStack(0, new ItemStack(Items.DIAMOND, 3));
        robot.setInventoryStack(1, new ItemStack(Items.DIRT, 4));

        StackRequest request = new StackRequest(provider, 0, new ItemStack(Items.DIAMOND, 5));
        request.setStation(station);

        AIRobotDeliverRequested ai = new AIRobotDeliverRequested(robot, request);
        ai.start(); // goto terminates synchronously (docked) -> deliver runs -> terminate

        Assertions.assertTrue(ai.success(), "handing over any of the owed cargo is a delivery");
        Assertions.assertTrue(robot.getInventoryStack(0).isEmpty(),
                "the matched cargo left the robot");
        Assertions.assertTrue(ItemStack.matches(new ItemStack(Items.DIRT, 4), robot.getInventoryStack(1)),
                "unrelated cargo is untouched");
    }

    @Test
    public void deliverFailsWhenNothingMatches() {
        AcceptingProvider provider = new AcceptingProvider(new ItemStack(Items.DIAMOND, 5));
        DockingStation station = stationWith(provider);

        DeliveryMockRobot robot = new DeliveryMockRobot();
        robot.setDockingStation(station);
        robot.setRegistry(new RecordingRegistry(station));
        robot.setInventoryStack(0, new ItemStack(Items.DIRT, 4));

        StackRequest request = new StackRequest(provider, 0, new ItemStack(Items.DIAMOND, 5));
        request.setStation(station);

        AIRobotDeliverRequested ai = new AIRobotDeliverRequested(robot, request);
        ai.start();

        Assertions.assertFalse(ai.success(), "offering none of the owed cargo is a failed delivery");
        Assertions.assertTrue(ItemStack.matches(new ItemStack(Items.DIRT, 4), robot.getInventoryStack(0)),
                "the refused cargo stays with the robot");
    }

    @Test
    public void deliverFailsCleanlyWhenTheProviderLostTheSlot() {
        // The provider shrank below the saved slot index while the robot flew (request grid edited).
        IRequestProvider provider = new IRequestProvider() {
            @Override
            public int getRequestsCount() {
                return 1;
            }

            @Override
            public ItemStack getRequest(int slot) {
                return ItemStack.EMPTY;
            }

            @Override
            public ItemStack offerItem(int slot, ItemStack stack) {
                throw new IndexOutOfBoundsException("slot " + slot + " no longer exists");
            }
        };
        DockingStation station = stationWith(provider);

        DeliveryMockRobot robot = new DeliveryMockRobot();
        robot.setDockingStation(station);
        robot.setRegistry(new RecordingRegistry(station));
        robot.setInventoryStack(0, new ItemStack(Items.DIAMOND, 5));

        StackRequest request = new StackRequest(provider, 7, new ItemStack(Items.DIAMOND, 5));
        request.setStation(station);

        AIRobotDeliverRequested ai = new AIRobotDeliverRequested(robot, request);
        ai.start();

        Assertions.assertFalse(ai.success(),
                "a stale slot index must fail the delivery, not throw into the AI cycle");
    }
}
