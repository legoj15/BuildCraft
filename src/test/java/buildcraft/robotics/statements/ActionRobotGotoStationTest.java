/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.statements;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.statements.StatementParameterItemStack;
import buildcraft.core.BCCoreItems;
import buildcraft.robotics.RobotRegistry;

/** The station a Go To Station gate's map-location parameter names. A spot whose side byte is not a face (a corrupt or
 *  hand-edited stack, or 7.1.x's {@code ForgeDirection.UNKNOWN} = 6) names no station: the registry's key needs a side,
 *  and looking one up with none threw out of the gate's tick. */
public class ActionRobotGotoStationTest extends VanillaSetupBaseTester {

    private static final BlockPos SPOT = BlockPos.ZERO;

    private static StatementParameterItemStack spotParam(byte side) {
        CompoundTag tag = new CompoundTag();
        tag.putString("mapType", "SPOT");
        tag.putInt("x", SPOT.getX());
        tag.putInt("y", SPOT.getY());
        tag.putInt("z", SPOT.getZ());
        tag.putByte("side", side);
        ItemStack stack = new ItemStack(BCCoreItems.MAP_LOCATION.get());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return new StatementParameterItemStack(stack);
    }

    /** A real registry holding one station on {@link #SPOT}'s up face - the lookup is the real one, which refuses a
     *  null side. */
    private static RobotRegistry registryWithStationOnUpFace(DockingStation station) {
        RobotRegistry registry = new RobotRegistry(null);
        registry.registerStation(station);
        return registry;
    }

    @Test
    public void aSpotWithARealSideNamesTheStationThere() {
        DockingStation station = new TestStation(List.of());
        RobotRegistry registry = registryWithStationOnUpFace(station);

        Assertions.assertSame(station,
                ActionRobotGotoStation.getStation(spotParam((byte) Direction.UP.ordinal()), registry),
                "the spot's point and side name the registered station");
        Assertions.assertNull(
                ActionRobotGotoStation.getStation(spotParam((byte) Direction.DOWN.ordinal()), registry),
                "another face of the same block has no station");
    }

    @Test
    public void aSpotWithAnOutOfRangeSideByteNamesNoStation() {
        RobotRegistry registry = registryWithStationOnUpFace(new TestStation(List.of()));

        for (byte bad : new byte[] { 6, 7, 127, -1, -128 }) {
            DockingStation named = Assertions.assertDoesNotThrow(
                    () -> ActionRobotGotoStation.getStation(spotParam(bad), registry),
                    "side byte " + bad + " must not throw out of the gate");

            Assertions.assertNull(named, "side byte " + bad + " is not a face, so it names no station");
        }
    }
}
