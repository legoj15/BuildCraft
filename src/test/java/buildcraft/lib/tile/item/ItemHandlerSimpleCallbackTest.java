/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.tile.item;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import buildcraft.VanillaSetupBaseTester;

/**
 * When {@link ItemHandlerSimple}'s change callback fires. Tiles hang {@code setChanged()}, crafting-grid refreshes
 * and builder/filler "resources changed" resets off it, so it must fire once per REAL change and never for a
 * simulated or refused one. On 1.21.1 the classic simulate flag already guaranteed that; on 1.21.10+ every
 * operation runs inside a Transfer-API transaction, and a callback fired mid-transaction is never taken back when
 * the transaction rolls back — so the callback must wait for the root commit. Every test runs unchanged on every node.
 */
public class ItemHandlerSimpleCallbackTest extends VanillaSetupBaseTester {

    private record Change(int slot, ItemStack before, ItemStack after) {}

    private static ItemHandlerSimple recording(int size, List<Change> log) {
        return new ItemHandlerSimple(size,
                (handler, slot, before, after) -> log.add(new Change(slot, before.copy(), after.copy())));
    }

    @Test
    public void simulatedInsertFiresNoCallback() {
        List<Change> log = new ArrayList<>();
        ItemHandlerSimple inv = recording(2, log);

        ItemStack leftover = inv.insertItem(0, new ItemStack(Items.APPLE, 5), true);

        Assertions.assertTrue(leftover.isEmpty(), "the simulation must report the apples would fit");
        Assertions.assertTrue(inv.getStackInSlot(0).isEmpty(), "a simulation must not change the slot");
        Assertions.assertEquals(List.of(), log, "a simulated insert changed nothing, so nothing may be reported");
    }

    @Test
    public void simulatedExtractFiresNoCallback() {
        List<Change> log = new ArrayList<>();
        ItemHandlerSimple inv = recording(1, log);
        inv.setStackInSlot(0, new ItemStack(Items.APPLE, 5));
        log.clear();

        ItemStack extracted = inv.extractItem(0, 3, true);

        Assertions.assertEquals(3, extracted.getCount(), "the simulation must report 3 apples available");
        Assertions.assertEquals(5, inv.getStackInSlot(0).getCount(), "a simulation must not change the slot");
        Assertions.assertEquals(List.of(), log, "a simulated extract changed nothing, so nothing may be reported");
    }

    @Test
    public void realInsertFiresExactlyOnceWithBeforeAndAfter() {
        List<Change> log = new ArrayList<>();
        ItemHandlerSimple inv = recording(2, log);
        inv.setStackInSlot(1, new ItemStack(Items.APPLE, 2));
        log.clear();

        ItemStack leftover = inv.insertItem(1, new ItemStack(Items.APPLE, 3), false);

        Assertions.assertTrue(leftover.isEmpty(), "all 3 apples fit");
        Assertions.assertEquals(1, log.size(), "one real change, one callback — got " + log);
        Change change = log.get(0);
        Assertions.assertEquals(1, change.slot(), "the callback must name the changed slot");
        Assertions.assertTrue(ItemStack.matches(new ItemStack(Items.APPLE, 2), change.before()),
                "before = the slot as it was, got " + change.before());
        Assertions.assertTrue(ItemStack.matches(new ItemStack(Items.APPLE, 5), change.after()),
                "after = the slot as it is now, got " + change.after());
    }

    @Test
    public void realExtractFiresExactlyOnceWithBeforeAndAfter() {
        List<Change> log = new ArrayList<>();
        ItemHandlerSimple inv = recording(1, log);
        inv.setStackInSlot(0, new ItemStack(Items.APPLE, 5));
        log.clear();

        ItemStack extracted = inv.extractItem(0, 5, false);

        Assertions.assertEquals(5, extracted.getCount(), "all 5 apples come out");
        Assertions.assertEquals(1, log.size(), "one real change, one callback — got " + log);
        Assertions.assertTrue(ItemStack.matches(new ItemStack(Items.APPLE, 5), log.get(0).before()),
                "before = the full slot, got " + log.get(0).before());
        Assertions.assertTrue(log.get(0).after().isEmpty(), "after = the emptied slot, got " + log.get(0).after());
    }

    @Test
    public void refusedInsertFiresNoCallback() {
        List<Change> log = new ArrayList<>();
        ItemHandlerSimple inv = recording(1, log);
        inv.setStackInSlot(0, new ItemStack(Items.APPLE, 64));
        log.clear();

        ItemStack leftover = inv.insertItem(0, new ItemStack(Items.APPLE, 1), false);

        Assertions.assertEquals(1, leftover.getCount(), "a full slot refuses the apple");
        Assertions.assertEquals(List.of(), log, "a refused insert changed nothing, so nothing may be reported");
    }

    @Test
    public void setStackInSlotStillFiresImmediately() {
        List<Change> log = new ArrayList<>();
        ItemHandlerSimple inv = recording(1, log);

        inv.setStackInSlot(0, new ItemStack(Items.APPLE, 4));

        Assertions.assertEquals(1, log.size(), "a direct set is a real change — got " + log);
        Assertions.assertTrue(log.get(0).before().isEmpty(), "before = empty");
        Assertions.assertTrue(ItemStack.matches(new ItemStack(Items.APPLE, 4), log.get(0).after()), "after = apples");
    }
}
