/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.pipe.flow;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Regression guard for the pipe-cargo data-loss bug: in-transit ItemStacks used to round-trip
 * through a lossy id+count helper, so an enchanted/named/damaged item travelling through a pipe
 * came back stripped of all components after a chunk save/reload. {@link TravellingItem} now
 * round-trips through {@code ItemStack.CODEC}; this asserts components survive.
 *
 * <p>This is a GameTest (not pure JUnit) because the codec round-trip needs a live server's
 * registry access for {@code registryAwareOps()} (the {@code stack} field is package-private, so
 * the tester lives in the same package).
 */
public class TravellingItemNbtTester {

    public static void testCargoPreservesComponentsAcrossSaveLoad(GameTestHelper helper) {
        ItemStack original = new ItemStack(Items.DIAMOND_PICKAXE);
        original.set(DataComponents.CUSTOM_NAME, Component.literal("Excalibur"));
        original.set(DataComponents.DAMAGE, 42);

        TravellingItem item = new TravellingItem(original);
        CompoundTag nbt = item.writeToNbt(0L);

        TravellingItem restored = new TravellingItem(nbt, 0L);

        if (restored.stack.isEmpty()) {
            throw new IllegalStateException("Pipe cargo lost its ItemStack entirely on save/load");
        }
        if (!ItemStack.isSameItemSameComponents(original, restored.stack)) {
            throw new IllegalStateException(
                "Pipe cargo lost item components on save/load: expected " + original
                    + " but got " + restored.stack);
        }
        if (restored.stack.getCount() != original.getCount()) {
            throw new IllegalStateException("Pipe cargo count changed on save/load");
        }
        helper.succeed();
    }
}
