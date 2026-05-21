/*
 * Copyright (c) 2026 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.statements;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import buildcraft.api.statements.StatementParameterItemStack;

/**
 * Game tests for statement-parameter {@link ItemStack} serialization.
 * <p>
 * Registered via {@link buildcraft.BuildCraftGameTests}. Runs in a loaded game environment so
 * {@code NBTUtilBC.registryAwareOps()} resolves the server registry — the same path a gate's
 * save/load and network sync take.
 * <p>
 * Regression guard: the 26.1 port originally stubbed {@code StatementParameterItemStack}'s NBT
 * constructor and {@code writeToNbt}, so the five gate triggers that use an item filter
 * (TriggerInventory, TriggerInventoryLevel, TriggerFluidContainer, TriggerFluidContainerLevel,
 * TriggerItemsTraversing) silently lost their configured item on world reload.
 */
public class StatementSerializationTester {

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) throw new IllegalStateException(msg);
    }

    /** A non-empty item filter must survive a writeToNbt → NBT-constructor round-trip. */
    public static void testItemStackParamRoundTrip(GameTestHelper helper) {
        try {
            ItemStack original = new ItemStack(Items.DIAMOND, 12);
            StatementParameterItemStack param = new StatementParameterItemStack(original);

            CompoundTag tag = new CompoundTag();
            param.writeToNbt(tag);
            StatementParameterItemStack restored = new StatementParameterItemStack(tag);

            assertTrue(ItemStack.isSameItemSameComponents(original, restored.getItemStack()),
                    "diamond filter should survive NBT round-trip, got " + restored.getItemStack());
            assertTrue(restored.getItemStack().getCount() == 12,
                    "count should survive, got " + restored.getItemStack().getCount());
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /** An empty parameter must round-trip back to empty — not crash, not resurrect a stack. */
    public static void testEmptyItemStackParamRoundTrip(GameTestHelper helper) {
        try {
            StatementParameterItemStack param = new StatementParameterItemStack();
            CompoundTag tag = new CompoundTag();
            param.writeToNbt(tag);
            StatementParameterItemStack restored = new StatementParameterItemStack(tag);

            assertTrue(restored.getItemStack().isEmpty(),
                    "empty filter should round-trip to empty, got " + restored.getItemStack());
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /** {@code StatementParameterItemStackExact} must also round-trip via its NBT helpers. */
    public static void testItemStackExactParamRoundTrip(GameTestHelper helper) {
        try {
            StatementParameterItemStackExact param = new StatementParameterItemStackExact();
            param.stack = new ItemStack(Items.IRON_INGOT, 5);

            CompoundTag tag = new CompoundTag();
            param.writeToNbt(tag);
            StatementParameterItemStackExact restored = StatementParameterItemStackExact.readFromNbt(tag);

            assertTrue(ItemStack.isSameItemSameComponents(param.stack, restored.stack),
                    "exact filter should survive NBT round-trip, got " + restored.stack);
            assertTrue(restored.stack.getCount() == 5,
                    "count should survive, got " + restored.stack.getCount());
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }
}
