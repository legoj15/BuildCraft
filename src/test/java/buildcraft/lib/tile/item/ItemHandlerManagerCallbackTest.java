/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.tile.item;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import buildcraft.api.core.EnumPipePart;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.lib.tile.item.ItemHandlerManager.EnumAccess;

/**
 * A handler built outside the manager and registered through the generic {@code addInvHandler} overload must still
 * report its changes to the tile (which is what marks the chunk dirty and updates comparators). The Filtered Buffer's
 * main inventory used to be silent: its contents changed without the tile ever calling {@code setChanged()}.
 */
public class ItemHandlerManagerCallbackTest extends VanillaSetupBaseTester {

    @Test
    void preBuiltHandlerGetsTheManagerCallback() {
        AtomicInteger changes = new AtomicInteger();
        ItemHandlerManager manager = new ItemHandlerManager((handler, slot, before, after) -> changes.incrementAndGet());
        ItemHandlerSimple filter = manager.addInvHandler("filter", 1, EnumAccess.PHANTOM);
        ItemHandlerFiltered main = manager.addInvHandler("main", new ItemHandlerFiltered(filter, false),
            EnumAccess.BOTH, EnumPipePart.VALUES);

        changes.set(0);
        main.setStackInSlot(0, new ItemStack(Items.STONE, 4));
        Assertions.assertEquals(1, changes.get(), "A change to a pre-built handler must reach the manager callback");
    }

    @Test
    void preBuiltHandlerKeepsItsOwnCallback() {
        AtomicInteger managerChanges = new AtomicInteger();
        AtomicInteger ownChanges = new AtomicInteger();
        ItemHandlerManager manager =
            new ItemHandlerManager((handler, slot, before, after) -> managerChanges.incrementAndGet());
        ItemHandlerSimple own = new ItemHandlerSimple(1, (handler, slot, before, after) -> ownChanges.incrementAndGet());
        manager.addInvHandler("own", own, EnumAccess.BOTH, EnumPipePart.VALUES);

        own.setStackInSlot(0, new ItemStack(Items.STONE));
        Assertions.assertEquals(1, ownChanges.get(), "An explicitly wired callback must not be replaced");
        Assertions.assertEquals(0, managerChanges.get());
    }
}
