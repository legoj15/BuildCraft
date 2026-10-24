/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.item;

import java.util.Random;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.api.core.IZone;
import buildcraft.core.BCCore;
import buildcraft.core.BCCoreItems;
import buildcraft.robotics.zone.ZoneMapRef;
import buildcraft.robotics.zone.ZoneMapSavedData;
import buildcraft.robotics.zone.ZonePlan;

/** {@link ItemMapLocation#getZone} / {@link ItemMapLocation#setZone} on a ZONE map. A map whose plan marks nothing
 *  (crafted, legacy, or a ZONE map with no {@code chunkMapping}, which earlier planner builds wrote) must stay a zone
 *  that contains nothing, NOT become "no zone": to a robot, no work zone means unbounded work (an unzoned block
 *  search, a bomber dropping anywhere within range, load/unload falling back to any station), while an empty zone
 *  keeps it idle. The zone samplers read the empty plan's null sample as "nothing to find".
 *
 *  <p>A written map carries only a map id; the plan lives in saved data (vanilla filled-map style), so a stack's data
 *  does not grow with its zone and a creative clone of a stack of maps does not copy the zone N times. */
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

    @Test
    public void aWrittenMapsItemDataDoesNotGrowWithItsZone() {
        ZonePlan small = new ZonePlan();
        small.set(1, 1, true);
        ZonePlan large = new ZonePlan();
        large.paintRect(-800, -800, 799, 799, true);
        // Partial chunks too, so the plan is not all cheap "full" chunks.
        for (int i = -800; i < 800; i += 7) {
            large.set(i, i / 3, false);
        }

        ItemStack smallMap = new ItemStack(BCCoreItems.MAP_LOCATION.get());
        ItemMapLocation.setZone(smallMap, small);
        ItemStack largeMap = new ItemStack(BCCoreItems.MAP_LOCATION.get());
        ItemMapLocation.setZone(largeMap, large);

        int smallSize = patchSize(smallMap);
        int largeSize = patchSize(largeMap);
        Assertions.assertTrue(largeSize - smallSize < 16, "a map's item data must not scale with its zone (one"
                + " column: " + smallSize + " chars, ~10,000 chunks: " + largeSize + " chars)");
        Assertions.assertTrue(BCCoreItems.MAP_LOCATION.get().getZone(largeMap) instanceof ZonePlan zone
                && zone.sameContent(large), "and the large zone still reads back whole");
    }

    @Test
    public void aLegacyMapIsMovedOffTheItemOnFirstReadAndKeepsItsZone() {
        ZonePlan plan = new ZonePlan();
        plan.set(-37, 120, true);
        plan.set(500, 3, true);
        // The format earlier builds wrote: the whole plan inside the stack's custom data.
        CompoundTag legacy = new CompoundTag();
        plan.writeToNBT(legacy);
        legacy.putString("mapType", "ZONE");
        legacy.putString("name", "farm");
        ItemStack map = new ItemStack(BCCoreItems.MAP_LOCATION.get());
        map.set(DataComponents.CUSTOM_DATA, CustomData.of(legacy));

        IZone first = BCCoreItems.MAP_LOCATION.get().getZone(map);
        Assertions.assertTrue(first instanceof ZonePlan zone && zone.sameContent(plan),
                "a legacy map reads as the zone it carries");
        CompoundTag after = map.get(DataComponents.CUSTOM_DATA).copyTag();
        Assertions.assertFalse(after.contains("chunkMapping"), "the first read moves the plan off the item: " + after);
        Assertions.assertEquals("farm", BCCoreItems.MAP_LOCATION.get().getLocationName(map),
                "and keeps everything else the stack carries");

        Assertions.assertTrue(BCCoreItems.MAP_LOCATION.get().getZone(map) instanceof ZonePlan zone
                && zone.sameContent(plan), "the migrated map still reads as the same zone");
    }

    /** Player data is saved before level data, so a legacy map that dropped its plan before the store reached a save
     *  could, after a crash between the two, name a zone held by neither file. */
    @Test
    public void aLegacyMapKeepsItsOwnPlanUntilTheStoreHasBeenSaved() {
        ZonePlan plan = new ZonePlan();
        plan.set(12, -40, true);
        CompoundTag legacy = new CompoundTag();
        plan.writeToNBT(legacy);
        legacy.putString("mapType", "ZONE");
        ItemStack map = new ItemStack(BCCoreItems.MAP_LOCATION.get());
        map.set(DataComponents.CUSTOM_DATA, CustomData.of(legacy));
        ZoneMapSavedData store = new ZoneMapSavedData();

        ZonePlan first = ItemMapLocation.migrateLegacyZone(map, store);
        Assertions.assertTrue(first != null && first.sameContent(plan), "the first read finds the carried plan");
        ZoneMapRef ref = map.get(BCCore.ZONE_MAP_ID.get());
        Assertions.assertTrue(ref != null && store.get(ref).sameContent(plan), "and points the map at a stored copy");
        Assertions.assertTrue(map.get(DataComponents.CUSTOM_DATA).contains("chunkMapping"),
                "but the map keeps its own copy while the stored one is in no save");

        store.encodeForSave();
        ItemMapLocation.migrateLegacyZone(map, store);
        Assertions.assertFalse(map.get(DataComponents.CUSTOM_DATA).contains("chunkMapping"),
                "once the store has been saved, the next read drops the map's copy");
        Assertions.assertEquals(ref, map.get(BCCore.ZONE_MAP_ID.get()), "and the map still names the same zone");
    }

    @Test
    public void aCopiedMapSharesItsZoneAndRewritingTheCopyLeavesTheOriginal() {
        ZonePlan a = new ZonePlan();
        a.set(10, 10, true);
        ZonePlan b = new ZonePlan();
        b.set(-10, -10, true);
        ItemStack original = new ItemStack(BCCoreItems.MAP_LOCATION.get());
        ItemMapLocation.setZone(original, a);

        ItemStack copy = original.copy();
        Assertions.assertTrue(BCCoreItems.MAP_LOCATION.get().getZone(copy) instanceof ZonePlan zone
                && zone.sameContent(a), "a copy reads the same zone");

        ItemMapLocation.setZone(copy, b);
        Assertions.assertTrue(BCCoreItems.MAP_LOCATION.get().getZone(copy) instanceof ZonePlan zone
                && zone.sameContent(b), "the rewritten copy reads its new zone");
        Assertions.assertTrue(BCCoreItems.MAP_LOCATION.get().getZone(original) instanceof ZonePlan zone
                && zone.sameContent(a), "writing a copy forks it: the original keeps its zone");
    }

    @Test
    public void handingOutAZoneDoesNotLetTheCallerEditTheStoredMap() {
        ZonePlan plan = new ZonePlan();
        plan.set(2, 2, true);
        ItemStack map = new ItemStack(BCCoreItems.MAP_LOCATION.get());
        ItemMapLocation.setZone(map, plan);
        plan.set(3, 3, true); // the writer's plan, edited after the write

        ZonePlan read = Assertions.assertInstanceOf(ZonePlan.class, BCCoreItems.MAP_LOCATION.get().getZone(map));
        Assertions.assertFalse(read.get(3, 3), "an edit to the written plan after the write does not reach the map");
        read.set(4, 4, true);
        Assertions.assertTrue(BCCoreItems.MAP_LOCATION.get().getZone(map) instanceof ZonePlan zone
                && zone.get(2, 2) && !zone.get(4, 4), "an edit to a plan read from the map does not reach the map");
    }

    /** Characters in the stack's encoded component patch: what a clone copies and the network sends. */
    private static int patchSize(ItemStack stack) {
        Tag tag = DataComponentPatch.CODEC.encodeStart(NbtOps.INSTANCE, stack.getComponentsPatch()).getOrThrow();
        return tag.toString().length();
    }
}
