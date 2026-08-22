/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.statements;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.api.statements.IStatementParameter;
import buildcraft.api.statements.StatementParameterItemStack;
import buildcraft.api.statements.StatementSlot;

/** {@link ActionStationProvideItems.canExtractItem}: extraction from a station is allowed unless a
 *  filtered provide-items action is active and the stack is not what it offers. */
public class ActionStationProvideItemsTest extends VanillaSetupBaseTester {

    private static TestStation stationWithProvide(IStatementParameter... params) {
        StatementSlot slot = new StatementSlot();
        slot.statement = new ActionStationProvideItems();
        slot.parameters = params;
        return new TestStation(List.of(slot));
    }

    @Test
    public void noProvideActionAllowsExtraction() {
        Assertions.assertTrue(
                ActionStationProvideItems.canExtractItem(new TestStation(List.of()), new ItemStack(Items.STONE)),
                "a station with no provide action must allow extraction");
    }

    @Test
    public void unfilteredProvideActionAllowsExtraction() {
        Assertions.assertTrue(
                ActionStationProvideItems.canExtractItem(stationWithProvide(new StatementParameterItemStack()),
                        new ItemStack(Items.STONE)),
                "a provide action with no filter must allow extraction of anything");
    }

    @Test
    public void filteredProvideActionRefusesOthers() {
        Assertions.assertFalse(
                ActionStationProvideItems.canExtractItem(
                        stationWithProvide(new StatementParameterItemStack(new ItemStack(Items.DIAMOND))),
                        new ItemStack(Items.STONE)),
                "a provide action filtered on diamond must refuse stone");
    }

    @Test
    public void filteredProvideActionAllowsItsItem() {
        Assertions.assertTrue(
                ActionStationProvideItems.canExtractItem(
                        stationWithProvide(new StatementParameterItemStack(new ItemStack(Items.DIAMOND))),
                        new ItemStack(Items.DIAMOND)),
                "a provide action filtered on diamond must allow diamond");
    }
}
