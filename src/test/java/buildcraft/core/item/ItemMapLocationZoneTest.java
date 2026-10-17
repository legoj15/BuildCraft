/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.item;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.world.item.ItemStack;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.core.BCCoreItems;
import buildcraft.robotics.zone.ZonePlan;

/** {@link ItemMapLocation#getZone} on a ZONE map: a map whose plan marks nothing (crafted, legacy, or a ZONE map
 *  with no {@code chunkMapping}) is no zone at all. Every caller already reads null as "no zone"; an empty plan
 *  instead reached the robot searches, whose zone sampler gets null back from it and threw on every tick. */
public class ItemMapLocationZoneTest extends VanillaSetupBaseTester {

    @Test
    public void aZoneMapThatMarksNothingIsNoZone() {
        ItemStack map = new ItemStack(BCCoreItems.MAP_LOCATION.get());
        ItemMapLocation.setZone(map, new ZonePlan());

        Assertions.assertNull(BCCoreItems.MAP_LOCATION.get().getZone(map), "an empty zone plan is no zone");
    }

    @Test
    public void aZoneMapThatMarksAColumnIsThatZone() {
        ItemStack map = new ItemStack(BCCoreItems.MAP_LOCATION.get());
        ZonePlan plan = new ZonePlan();
        plan.set(4, -9, true);
        ItemMapLocation.setZone(map, plan);

        Assertions.assertTrue(BCCoreItems.MAP_LOCATION.get().getZone(map) instanceof ZonePlan zone && zone.get(4, -9),
                "a marked plan reads back as the same zone");
    }
}
