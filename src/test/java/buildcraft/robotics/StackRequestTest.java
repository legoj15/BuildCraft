/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.robotics;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import buildcraft.VanillaSetupBaseTester;
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

    @Test
    public void nbtWithoutStationLoadsNull() {
        StackRequest original = new StackRequest(null, 0, new ItemStack(Items.DIRT, 1));
        CompoundTag nbt = new CompoundTag();
        original.writeToNBT(nbt);
        Assertions.assertNull(StackRequest.loadFromNBT(nbt),
                "a request that never took a station cannot be resumed — 7.1.x returned null too");
    }

    @Test
    public void resourceIdIsStationPosSidePlusSlot() {
        DockingStation station = stationDouble(new BlockPos(1, 2, 3), Direction.UP);
        StackRequest request = new StackRequest(null, 4, new ItemStack(Items.DIAMOND, 1));
        request.setStation(station);
        Assertions.assertEquals(new ResourceIdRequest(station, 4), request.getResourceId(null),
                "the reservation id is the station's cell plus the provider slot — what keeps two delivery"
                        + " robots off the same order");
    }
}
