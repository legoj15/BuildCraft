/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.robotics;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.api.core.NbtApiUtil;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.IRequestProvider;
import buildcraft.api.robots.ResourceIdRequest;
import buildcraft.api.robots.RobotManager;
import buildcraft.api.statements.StatementSlot;
import buildcraft.robotics.ai.MockRobotAccess;

import java.util.List;

/** The NBT half of {@link StackRequest} — what a delivery robot carries in its save data. The
 *  world-resolving halves against a LIVE registry+pipe+requester are pinned by the
 *  {@code DeliveryRequesterTester} game tests; here the station resolution runs against a registry stub
 *  swapped into {@code RobotManager.registryProvider}, which is exactly what the by-value station link
 *  (pos + side) makes possible. */
public class StackRequestTest extends VanillaSetupBaseTester {

    /** A station double with no behaviour beyond identity — the request only ever stores and re-reads it. */
    private static DockingStation stationDouble(BlockPos pos, Direction side) {
        return new DockingStation(pos, side) {
            @Override
            public Iterable<StatementSlot> getActiveActions() {
                return List.of();
            }

            @Override
            public IRequestProvider getRequestProvider() {
                return null;
            }
        };
    }

    /** Installs a registry that resolves exactly {@code station} from {@code (pos, side)}, returning the
     *  previous provider for restoration. */
    private static buildcraft.api.robots.IRobotRegistryProvider registryResolving(DockingStation station,
            BlockPos pos, Direction side) {
        buildcraft.api.robots.IRobotRegistryProvider previous = RobotManager.registryProvider;
        RobotManager.registryProvider = world -> new MockRobotAccess.InertRobotRegistry() {
            @Override
            public DockingStation getStation(BlockPos p, Direction s) {
                return p.equals(pos) && s == side ? station : null;
            }
        };
        return previous;
    }

    @Test
    public void nbtRoundTripsSlotStackAndStation() {
        BlockPos pos = new BlockPos(11, -5, 300);
        Direction side = Direction.WEST;
        DockingStation station = stationDouble(pos, side);
        ItemStack owed = new ItemStack(Items.DIAMOND, 7);

        StackRequest original = new StackRequest(null, 13, owed);
        original.setStation(station);

        CompoundTag nbt = new CompoundTag();
        original.writeToNBT(nbt);

        StackRequest loaded = StackRequest.loadFromNBT(nbt);
        Assertions.assertNotNull(loaded, "a request with a station link must reload");
        Assertions.assertEquals(13, loaded.getSlot(), "the provider slot round-trips");
        Assertions.assertTrue(ItemStack.matches(owed, loaded.getStack()),
                "the owed stack round-trips through the ItemStack codec");

        // The station re-resolves from the saved pos+side through the registry — the whole point of
        // keeping the link by value.
        DockingStation resolved = stationDouble(pos, side);
        var previous = registryResolving(resolved, pos, side);
        try {
            Assertions.assertSame(resolved, loaded.getStation(null),
                    "a loaded request re-resolves its station from the saved pos+side");
            Assertions.assertEquals(pos, loaded.getStation(null).index(),
                    "the station position round-trips by value");
            Assertions.assertEquals(side, loaded.getStation(null).side(),
                    "the station side round-trips");
        } finally {
            RobotManager.registryProvider = previous;
        }
    }

    /** {@code ItemStack.CODEC} bounds a count to 1..99 and never checks the item's own stack limit, which a mod can
     *  raise: an order for more used to fail to encode and reload as an empty order. */
    @Test
    public void anOwedCountAboveWhatTheItemCodecCarriesSurvives() {
        StackRequest original = new StackRequest(null, 1, new ItemStack(Items.ENDER_PEARL, 128));
        original.setStation(stationDouble(new BlockPos(2, 64, 2), Direction.UP));
        CompoundTag nbt = new CompoundTag();
        original.writeToNBT(nbt);

        StackRequest loaded = StackRequest.loadFromNBT(nbt);

        Assertions.assertNotNull(loaded);
        Assertions.assertTrue(ItemStack.matches(original.getStack(), loaded.getStack()),
                "the owed stack, count above 99 included, round-trips: " + loaded.getStack());
    }

    /** A robot that reloads and saves again before anything resolves its order's station (an unload right after a
     *  load) must not lose the order: the request still knows the station by value, so it writes it back. */
    @Test
    public void loadedRequestSavesItsStationAgainBeforeResolvingIt() {
        BlockPos pos = new BlockPos(-4, 70, 9);
        Direction side = Direction.NORTH;
        StackRequest original = new StackRequest(null, 2, new ItemStack(Items.DIAMOND, 3));
        original.setStation(stationDouble(pos, side));
        CompoundTag first = new CompoundTag();
        original.writeToNBT(first);

        CompoundTag second = new CompoundTag();
        StackRequest.loadFromNBT(first).writeToNBT(second);

        Assertions.assertEquals(first, second, "a second save of an unresolved loaded request is the first save");
    }

    @Test
    public void nbtWithoutStationLoadsNull() {
        StackRequest original = new StackRequest(null, 0, new ItemStack(Items.DIRT, 1));
        CompoundTag nbt = new CompoundTag();
        original.writeToNBT(nbt);
        Assertions.assertNull(StackRequest.loadFromNBT(nbt),
                "a request that never took a station cannot be resumed — 7.1.x returned null too");
    }

    /** A request tag as a robot's board saves it, with the side byte replaced by {@code side}. */
    private static CompoundTag requestTagWithSideByte(byte side) {
        StackRequest original = new StackRequest(null, 5, new ItemStack(Items.DIAMOND, 2));
        original.setStation(stationDouble(new BlockPos(3, 70, -8), Direction.EAST));
        CompoundTag nbt = new CompoundTag();
        original.writeToNBT(nbt);
        nbt.putByte("stationSide", side);
        return nbt;
    }

    /** The side byte is the one thing in a saved order that can point outside {@code Direction.values()}: 7.1.x's
     *  {@code ForgeDirection.UNKNOWN} was 6, and a corrupt or hand-edited save can hold anything. Indexing blind threw
     *  out of the board's load, and the robot's whole AI tree with it. The order is unresumable without a station
     *  (like one saved without any station link), so it loads as no order at all - the board then looks for a new
     *  one - and, above all, loading it does not throw. */
    @ParameterizedTest
    @ValueSource(bytes = { 6, 7, 100, 127, -1, -128 })
    public void outOfRangeStationSideLoadsAsNoOrderInsteadOfThrowing(byte side) {
        CompoundTag nbt = requestTagWithSideByte(side);

        StackRequest loaded = Assertions.assertDoesNotThrow(() -> StackRequest.loadFromNBT(nbt),
                "a side byte of " + side + " must not throw out of the board's load");
        Assertions.assertNull(loaded, "an order whose station side is unreadable has no station: no order to resume");
    }

    @Test
    public void everyRealStationSideStillRoundTrips() {
        for (Direction side : Direction.values()) {
            StackRequest loaded = StackRequest.loadFromNBT(requestTagWithSideByte((byte) side.ordinal()));
            Assertions.assertNotNull(loaded, "side " + side + " is a real side and the order must reload");
            CompoundTag again = new CompoundTag();
            loaded.writeToNBT(again);
            Assertions.assertEquals((byte) side.ordinal(), NbtApiUtil.getByte(again, "stationSide", (byte) -1),
                    "side " + side + " survives a load and a second save");
        }
    }

    @Test
    public void resourceIdIsStationPosSidePlusSlot() {
        DockingStation station = stationDouble(new BlockPos(1, 2, 3), Direction.UP);
        StackRequest request = new StackRequest(null, 4, new ItemStack(Items.DIAMOND, 1));
        request.setStation(station);
        Assertions.assertEquals(new ResourceIdRequest(station, 4), request.getResourceId(null),
                "the reservation id is the station's cell plus the provider slot — what keeps two delivery"
                        + " robots off the same order");

        // The id comes from the by-value link, not the live station: a reloaded order whose station no longer
        // resolves still names the reservation the search took, so giving it up can hand that back.
        CompoundTag nbt = new CompoundTag();
        request.writeToNBT(nbt);
        StackRequest loaded = StackRequest.loadFromNBT(nbt);
        var previous = registryResolving(null, BlockPos.ZERO, Direction.DOWN);
        try {
            Assertions.assertNull(loaded.getStation(null), "precondition: the station no longer resolves");
            Assertions.assertEquals(new ResourceIdRequest(station, 4), loaded.getResourceId(null),
                    "a reloaded order whose station is gone keeps the id it was reserved under");
        } finally {
            RobotManager.registryProvider = previous;
        }
    }
}
