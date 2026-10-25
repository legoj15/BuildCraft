/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.item;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.phys.Vec3;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.api.core.IZone;
import buildcraft.builders.snapshot.RequiredExtractor;
import buildcraft.core.BCCore;
import buildcraft.core.BCCoreItems;
import buildcraft.lib.misc.StackUtil;
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

    /** Player data is saved before level data, and a level save's write can fail (it is only logged), so a legacy map
     *  that dropped its plan before the store's file was seen holding the zone could name a zone held by neither. */
    @Test
    public void aLegacyMapKeepsItsOwnPlanUntilTheStoresFileHoldsItsZone() {
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
                "but the map keeps its own copy while the stored one is in no file");

        // The level save encodes the store; the write comes later and may fail.
        Tag encoded = ZoneMapSavedData.CODEC.encodeStart(NbtOps.INSTANCE, store).getOrThrow();
        ItemMapLocation.migrateLegacyZone(map, store);
        Assertions.assertTrue(map.get(DataComponents.CUSTOM_DATA).contains("chunkMapping"),
                "an encode alone does not drop the map's copy");

        store.confirmOnDisk((CompoundTag) encoded);
        ItemMapLocation.migrateLegacyZone(map, store);
        Assertions.assertFalse(map.get(DataComponents.CUSTOM_DATA).contains("chunkMapping"),
                "once the written file is seen holding the zone, the next read drops the map's copy");
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

    /** A zone lives in its world's store, so a blueprint carries the plan of every zone map its captured data holds
     *  (here one in a shulker box in a chest, and one loose beside it) and the build hands it to the store of the
     *  world it is built in. Built anywhere else, a bare map id names no zone and the robot idles. */
    @Test
    public void aBlueprintedZoneMapKeepsItsZoneInAnotherWorld() {
        ZoneMapSavedData source = new ZoneMapSavedData();
        ZoneMapSavedData target = new ZoneMapSavedData();
        ZonePlan plan = new ZonePlan();
        plan.set(70, -3, true);
        plan.set(-200, 41, true);
        ItemStack map = zoneMap(source, plan);
        ItemStack shulker = new ItemStack(Items.SHULKER_BOX);
        shulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(map.copy())));
        CompoundTag chest = new CompoundTag();
        ListTag items = new ListTag();
        items.add(slotted(shulker, 0));
        items.add(slotted(map, 1));
        chest.put("Items", items);

        ZoneMapPortability.carryPlans(chest, source);

        // The price: a zone map costs a blank map (the planner writes one for free), loose or inside a container. A
        // zone map's ref names an entry in one world's store only, so a container priced with it could be paid for
        // in no other world.
        ItemStack loosePrice = RequiredExtractor.parseItemStack(slot(chest, 1));
        Assertions.assertTrue(StackUtil.canMerge(loosePrice, new ItemStack(BCCoreItems.MAP_LOCATION.get())),
                "a zone map is priced as a blank map: " + loosePrice.getComponentsPatch());
        ItemStack shulkerPrice = RequiredExtractor.parseItemStack(slot(chest, 0));
        ItemStack shulkerOfBlank = new ItemStack(Items.SHULKER_BOX);
        shulkerOfBlank.set(DataComponents.CONTAINER,
                ItemContainerContents.fromItems(List.of(new ItemStack(BCCoreItems.MAP_LOCATION.get()))));
        Assertions.assertTrue(StackUtil.canMerge(shulkerPrice, shulkerOfBlank),
                "a shulker holding a zone map is priced as the shulker holding a blank map: "
                        + shulkerPrice.getComponentsPatch());
        ZonePlan elsewhere = new ZonePlan();
        elsewhere.set(-5, 600, true);
        ItemStack shulkerOfOtherWorldsMap = new ItemStack(Items.SHULKER_BOX);
        shulkerOfOtherWorldsMap.set(DataComponents.CONTAINER,
                ItemContainerContents.fromItems(List.of(zoneMap(target, elsewhere))));
        Assertions.assertTrue(RequiredExtractor.pays(shulkerPrice, shulkerOfOtherWorldsMap),
                "a shulker holding a zone map written in the building world pays for it");
        Assertions.assertTrue(RequiredExtractor.pays(shulkerPrice, shulker),
                "and so does the shulker it was captured from");
        Assertions.assertFalse(RequiredExtractor.pays(shulkerPrice, new ItemStack(Items.SHULKER_BOX)),
                "an empty shulker does not");

        CompoundTag placed = chest.copy();
        ZoneMapPortability.settlePlans(placed, target);

        // The shulker's minecraft:container list of {slot, item}, read as saved data (its accessors differ by node).
        CompoundTag shulkerComponents = (CompoundTag) slot(placed, 0).get("components");
        ListTag contents = (ListTag) shulkerComponents.get("minecraft:container");
        Tag nestedTag = ((CompoundTag) contents.get(0)).get("item");
        ItemStack nested = ItemStack.CODEC.parse(NbtOps.INSTANCE, nestedTag).getOrThrow();
        ItemStack loose = ItemStack.CODEC.parse(NbtOps.INSTANCE, slot(placed, 1)).getOrThrow();
        for (ItemStack placedMap : List.of(nested, loose)) {
            ZoneMapRef ref = placedMap.get(BCCore.ZONE_MAP_ID.get());
            ZonePlan read = ref == null ? null : target.get(ref);
            Assertions.assertTrue(read != null && read.sameContent(plan),
                    "the built map names the same zone in the world it was built in: "
                            + placedMap.getComponentsPatch());
        }
    }

    /** A built container keeps the plan on the zone map inside it until the building world's entry is durable, and
     *  nothing ticks a nested map to drop it later; captured again in that world, it is priced with a blank map. The
     *  container the world gives back when it is broken (the map still carrying its plan) must still pay for it. */
    @Test
    public void aBuiltContainerHoldingASettledPlanPaysForItself() {
        ZoneMapSavedData source = new ZoneMapSavedData();
        ZoneMapSavedData target = new ZoneMapSavedData();
        ZonePlan plan = new ZonePlan();
        plan.set(12, -9, true);
        ItemStack shulker = new ItemStack(Items.SHULKER_BOX);
        shulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(zoneMap(source, plan))));
        CompoundTag chest = new CompoundTag();
        ListTag items = new ListTag();
        items.add(slotted(shulker, 0));
        chest.put("Items", items);
        ZoneMapPortability.carryPlans(chest, source);
        CompoundTag built = chest.copy();
        ZoneMapPortability.settlePlans(built, target);
        Assertions.assertFalse(target.isDurable(target.intern(plan)), "precondition: the built entry is not durable");

        ItemStack held = ItemStack.CODEC.parse(NbtOps.INSTANCE, slot(built, 0)).getOrThrow();
        CompoundTag recaptured = built.copy();
        ZoneMapPortability.carryPlans(recaptured, target);
        ItemStack price = RequiredExtractor.parseItemStack(slot(recaptured, 0));

        Assertions.assertTrue(RequiredExtractor.pays(price, held),
                "the container the build placed pays for its own capture: " + price.getComponentsPatch()
                        + " vs " + held.getComponentsPatch());
    }

    /** A crash under a build from before ids were reserved ahead lost the entries added since the last save, and the
     *  next session handed a lost entry's id to another zone. A legacy map still carrying both that id and its own
     *  plan must keep its plan, not read the other zone. Blueprint builds rely on the same rule for a plan whose id
     *  was reused. */
    @Test
    public void aLegacyMapWhoseIdWentToAnotherZoneKeepsItsOwnPlan() {
        ZoneMapSavedData store = new ZoneMapSavedData();
        ZonePlan own = new ZonePlan();
        own.set(5, 5, true);
        ZonePlan other = new ZonePlan();
        other.set(-60, 8, true);
        ZoneMapRef reused = store.intern(other);
        CompoundTag legacy = new CompoundTag();
        own.writeToNBT(legacy);
        legacy.putString("mapType", "ZONE");
        ItemStack map = new ItemStack(BCCoreItems.MAP_LOCATION.get());
        map.set(DataComponents.CUSTOM_DATA, CustomData.of(legacy));
        map.set(BCCore.ZONE_MAP_ID.get(), reused);

        ZonePlan read = ItemMapLocation.migrateLegacyZone(map, store);

        Assertions.assertTrue(read != null && read.sameContent(own), "the map reads the plan it carries");
        ZoneMapRef now = map.get(BCCore.ZONE_MAP_ID.get());
        Assertions.assertTrue(now != null && !now.equals(reused) && store.get(now).sameContent(own),
                "and is pointed at a stored copy of it, not at the zone that took its id");
        Assertions.assertTrue(store.get(reused).sameContent(other), "the other zone is left alone");
    }

    /** A written ZONE map whose zone {@code store} holds. */
    private static ItemStack zoneMap(ZoneMapSavedData store, ZonePlan plan) {
        ItemStack map = new ItemStack(BCCoreItems.MAP_LOCATION.get());
        ItemMapLocation.setZone(map, plan);
        map.set(BCCore.ZONE_MAP_ID.get(), store.intern(plan));
        return map;
    }

    /** {@code stack} as a chest's {@code Items} list holds it. */
    private static CompoundTag slotted(ItemStack stack, int slot) {
        CompoundTag tag = (CompoundTag) ItemStack.CODEC.encodeStart(NbtOps.INSTANCE, stack).getOrThrow();
        tag.putByte("Slot", (byte) slot);
        return tag;
    }

    private static CompoundTag slot(CompoundTag chest, int index) {
        return (CompoundTag) ((ListTag) chest.get("Items")).get(index);
    }

    /** Characters in the stack's encoded component patch: what a clone copies and the network sends. */
    private static int patchSize(ItemStack stack) {
        Tag tag = DataComponentPatch.CODEC.encodeStart(NbtOps.INSTANCE, stack.getComponentsPatch()).getOrThrow();
        return tag.toString().length();
    }
}
