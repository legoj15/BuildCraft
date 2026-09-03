/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.api.statements;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import buildcraft.VanillaSetupBaseTester;

/** The plain item-stack gate parameter — the widget behind Filter, Filter Tool, Provide/Accept Items,
 *  Provide/Accept Fluids and Goto Station. A click on it carries the stack the player is holding, and
 *  7.1.x's rule is the whole of the behaviour: a non-empty held stack REPLACES the parameter with a
 *  count-1 copy, an empty hand clears it. */
public class StatementParameterItemStackTest extends VanillaSetupBaseTester {

    private static final StatementMouseClick LEFT = new StatementMouseClick(0, false);
    private static final StatementMouseClick RIGHT = new StatementMouseClick(1, false);

    @Test
    public void heldStackSetsAnEmptyParameter() {
        StatementParameterItemStack result = new StatementParameterItemStack()
                .onClick(null, null, new ItemStack(Items.DIAMOND), LEFT);

        Assertions.assertEquals(Items.DIAMOND, result.getItemStack().getItem(),
                "a held stack must become the parameter — otherwise no gate filter can ever be set");
        Assertions.assertEquals(1, result.getItemStack().getCount(),
                "the parameter holds a count-1 copy");
    }

    @Test
    public void heldStackReplacesAnExistingParameter() {
        StatementParameterItemStack result = new StatementParameterItemStack(new ItemStack(Items.STONE))
                .onClick(null, null, new ItemStack(Items.DIAMOND), LEFT);

        Assertions.assertEquals(Items.DIAMOND, result.getItemStack().getItem(),
                "a held stack must replace whatever the parameter held before");
    }

    @Test
    public void aFullStackIsStoredAsOne() {
        StatementParameterItemStack result = new StatementParameterItemStack()
                .onClick(null, null, new ItemStack(Items.COBBLESTONE, 64), LEFT);

        Assertions.assertEquals(1, result.getItemStack().getCount(),
                "7.1.x stamped stackSize = 1 on the copy; a 64-stack must not leak its count into the "
                        + "parameter");
        Assertions.assertEquals(Items.COBBLESTONE, result.getItemStack().getItem(),
                "the stored item is the held one");
    }

    @Test
    public void emptyHandClearsTheParameter() {
        StatementParameterItemStack result = new StatementParameterItemStack(new ItemStack(Items.DIAMOND))
                .onClick(null, null, ItemStack.EMPTY, LEFT);

        Assertions.assertTrue(result.getItemStack().isEmpty(),
                "an empty-handed click must clear the parameter — otherwise a set filter can never be "
                        + "removed");
    }

    @Test
    public void emptyHandOnEmptyParameterStaysEmpty() {
        StatementParameterItemStack result = new StatementParameterItemStack()
                .onClick(null, null, ItemStack.EMPTY, RIGHT);

        Assertions.assertTrue(result.getItemStack().isEmpty(),
                "clicking an empty parameter with an empty hand changes nothing");
    }

    @Test
    public void theClickedStackIsNotMutated() {
        ItemStack held = new ItemStack(Items.COBBLESTONE, 64);
        new StatementParameterItemStack().onClick(null, null, held, LEFT);

        Assertions.assertEquals(64, held.getCount(),
                "the parameter must copy the held stack, never shrink the player's own cursor stack");
    }
}
