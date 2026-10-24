/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.boards;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.builders.snapshot.RequiredExtractor;
import buildcraft.core.BCCore;
import buildcraft.core.BCCoreItems;
import buildcraft.core.item.ItemMapLocation;
import buildcraft.robotics.zone.ZoneMapSavedData;
import buildcraft.robotics.zone.ZonePlan;

/** The robot builder fetches and delivers what the Builder would take as payment: a container holding a zone map is
 *  priced with a blank map in its place, so a shulker holding a written zone map must count as filling that price, for
 *  the robot exactly as for the Builder's own extraction ({@link RequiredExtractor#payer}). */
public class BuilderBoardPaymentTest extends VanillaSetupBaseTester {

    @Test
    public void aShulkerHoldingAWrittenZoneMapFillsAShulkerPrice() {
        ZoneMapSavedData store = new ZoneMapSavedData();
        ZonePlan plan = new ZonePlan();
        plan.set(3, 7, true);
        ItemStack map = new ItemStack(BCCoreItems.MAP_LOCATION.get());
        ItemMapLocation.setZone(map, plan);
        map.set(BCCore.ZONE_MAP_ID.get(), store.intern(plan));
        ItemStack shulker = new ItemStack(Items.SHULKER_BOX);
        shulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(map)));
        ItemStack price = RequiredExtractor.parseItemStack(
                (CompoundTag) ItemStack.CODEC.encodeStart(NbtOps.INSTANCE, shulker).getOrThrow());

        Assertions.assertTrue(RequiredExtractor.pays(price, shulker), "precondition: the Builder takes it");
        Assertions.assertTrue(BoardRobotBuilder.requires(List.of(price), shulker),
                "the robot delivers what the Builder takes");
        Assertions.assertTrue(BoardRobotBuilder.matchesRequired(price).matches(shulker),
                "and fetches it for that price");
        Assertions.assertFalse(BoardRobotBuilder.requires(List.of(price), new ItemStack(Items.SHULKER_BOX)),
                "an empty shulker fills nothing");
    }
}
