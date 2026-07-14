/*
 * Copyright (c) 2026 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.statements;

import java.util.Locale;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import buildcraft.api.statements.IStatement;
import buildcraft.api.statements.StatementManager;
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

    /**
     * The fluid-LEVEL triggers were renamed from the plain {@code buildcraft:fluid.} prefix — now
     * owned solely by the empty/contains/space/full state family — to {@code buildcraft:fluidlevel.}
     * to avoid a future value collision. The old UID is retained as a legacy alias so gates saved in
     * existing worlds still resolve. This pins that save-compat contract (a saved gate stores the
     * statement's UID string; the alias is what lets an old {@code buildcraft:fluid.below25} save
     * find the renamed trigger on load).
     */
    public static void testFluidLevelUidAliasResolves(GameTestHelper helper) {
        try {
            for (TriggerFluidContainerLevel.TriggerType type : TriggerFluidContainerLevel.TriggerType.VALUES) {
                String suffix = type.name().toLowerCase(Locale.ROOT);
                IStatement viaNew = StatementManager.statements.get("buildcraft:fluidlevel." + suffix);
                IStatement viaLegacy = StatementManager.statements.get("buildcraft:fluid." + suffix);
                assertTrue(viaNew != null, "new UID buildcraft:fluidlevel." + suffix + " must resolve");
                assertTrue(viaLegacy != null,
                        "legacy UID buildcraft:fluid." + suffix + " must still resolve for old-world save-compat");
                assertTrue(viaNew == viaLegacy,
                        "legacy alias buildcraft:fluid." + suffix + " must resolve to the SAME statement as the new UID");
                assertTrue(("buildcraft:fluidlevel." + suffix).equals(viaNew.getUniqueTag()),
                        "primary (saved-to-disk) UID must be the fluidlevel. form, got " + viaNew.getUniqueTag());
            }
            // The plain buildcraft:fluid. prefix must still belong to the DISTINCT state family.
            IStatement stateFull = StatementManager.statements.get("buildcraft:fluid.full");
            assertTrue(stateFull != null, "state-family buildcraft:fluid.full must still resolve");
            assertTrue("buildcraft:fluid.full".equals(stateFull.getUniqueTag()),
                    "state trigger must keep the plain fluid. prefix as its primary UID");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }
}
