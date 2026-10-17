/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.client.guide.ref;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.world.item.Item;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.lib.client.guide.entry.ItemStackValueFilter;
import buildcraft.lib.client.guide.entry.PageValue;
import buildcraft.silicon.BCSiliconItems;

/**
 * Pins the guide's "Laser Power" group: the laser is the provider, and every table the laser beams power into is a
 * receiver. The two robot tables (Programming and Integration) used to be missing or hedged behind a stale
 * "dev-only" null guard; each laser-fed table must be listed so its guide page links back to the laser.
 *
 * <p>Needs {@link VanillaSetupBaseTester}: the group population constructs ItemStacks.
 */
public class GuideLaserPowerGroupTester extends VanillaSetupBaseTester {

    @AfterAll
    public static void leaveTheGroupsAsFound() {
        GuideGroupManager.sets.clear();
    }

    @Test
    public void everyLaserFedTableIsAReceiver() {
        GuideGroupManager.populateDefaultGroups();
        GuideGroupSet group = GuideGroupManager.get("buildcraft", "laser_power_providers");
        Assertions.assertNotNull(group, "the laser_power_providers group is populated");

        Assertions.assertTrue(items(group.entries).contains(BCSiliconItems.LASER.get()),
                "precondition: the laser is the group's provider, so the population ran");

        Set<Item> receivers = items(group.sources);
        Assertions.assertTrue(receivers.contains(BCSiliconItems.ASSEMBLY_TABLE.get()), "Assembly Table");
        Assertions.assertTrue(receivers.contains(BCSiliconItems.ADVANCED_CRAFTING_TABLE.get()),
                "Advanced Crafting Table");
        Assertions.assertTrue(receivers.contains(BCSiliconItems.INTEGRATION_TABLE.get()), "Integration Table");
        Assertions.assertTrue(receivers.contains(BCSiliconItems.PROGRAMMING_TABLE.get()), "Programming Table");
    }

    private static Set<Item> items(Iterable<PageValue<?>> values) {
        Set<Item> items = new HashSet<>();
        for (PageValue<?> value : values) {
            if (value.value instanceof ItemStackValueFilter filter) {
                items.add(filter.stack.baseStack.getItem());
            }
        }
        return items;
    }
}
