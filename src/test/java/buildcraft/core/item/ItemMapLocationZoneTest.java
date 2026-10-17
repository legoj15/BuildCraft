/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.item;

import java.util.Random;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.api.core.IZone;
import buildcraft.core.BCCoreItems;
import buildcraft.robotics.zone.ZonePlan;

/** {@link ItemMapLocation#getZone} on a ZONE map. A map whose plan marks nothing (crafted, legacy, or a ZONE map
 *  with no {@code chunkMapping}, which earlier planner builds wrote) must stay a zone that contains nothing, NOT
 *  become "no zone": to a robot, no work zone means unbounded work (an unzoned block search, a bomber dropping
 *  anywhere within range, load/unload falling back to any station), while an empty zone keeps it idle. The zone
 *  samplers read the empty plan's null sample as "nothing to find". */
public class ItemMapLocationZoneTest extends VanillaSetupBaseTester {

    @Test
    public void aZoneMapThatMarksNothingIsAZoneThatContainsNothing() {
        ItemStack map = new ItemStack(BCCoreItems.MAP_LOCATION.get());
        ItemMapLocation.setZone(map, new ZonePlan());

        IZone zone = BCCoreItems.MAP_LOCATION.get().getZone(map);

        Assertions.assertNotNull(zone, "an empty zone plan is still a zone: no zone would let a robot work anywhere");
        Assertions.assertFalse(zone.contains(new Vec3(30.5, 64.5, 0.5)), "the empty zone contains no block");
        Assertions.assertNull(zone.getRandomBlockPos(new Random(1), -64, 319), "the empty zone has nothing to sample");
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
