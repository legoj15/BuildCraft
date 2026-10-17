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
import buildcraft.api.core.IStackFilter;
import buildcraft.api.statements.IStatementParameter;
import buildcraft.api.statements.StatementParameterItemStack;
import buildcraft.api.statements.StatementSlot;

/** The {@link ActionRobotFilter} truth table: interaction with an action class is refused unless some
 *  active action of that class either has no filter set (empty params → everything passes) or its
 *  filter stacks match the robot's filter. Also pins the gate-filter extraction helpers. */
public class ActionRobotFilterTest extends VanillaSetupBaseTester {

    private static StatementSlot slot(buildcraft.api.statements.IActionInternal stmt, IStatementParameter... params) {
        StatementSlot slot = new StatementSlot();
        slot.statement = stmt;
        slot.parameters = params;
        return slot;
    }

    @Test
    public void noActionsRefusesInteraction() {
        TestStation station = new TestStation(List.of());
        Assertions.assertFalse(
                ActionRobotFilter.canInteractWithItem(station, new ItemStack(Items.DIAMOND),
                        ActionStationProvideItems.class),
                "a station with no actions must refuse interaction");
    }

    @Test
    public void matchingFilteredActionAllowsInteraction() {
        TestStation station = new TestStation(List.of(
                slot(new ActionStationProvideItems(),
                        new StatementParameterItemStack(new ItemStack(Items.DIAMOND)))));
        Assertions.assertTrue(
                ActionRobotFilter.canInteractWithItem(station, new ItemStack(Items.DIAMOND),
                        ActionStationProvideItems.class),
                "an action filtered on diamond must interact with a diamond-carrying robot");
    }

    @Test
    public void unfilteredActionAllowsEverything() {
        TestStation station = new TestStation(List.of(
                slot(new ActionStationProvideItems(), new StatementParameterItemStack())));
        Assertions.assertTrue(
                ActionRobotFilter.canInteractWithItem(station, new ItemStack(Items.STONE),
                        ActionStationProvideItems.class),
                "an action with empty filter params must interact with anything");
    }

    @Test
    public void mismatchedFilterRefusesInteraction() {
        TestStation station = new TestStation(List.of(
                slot(new ActionStationProvideItems(),
                        new StatementParameterItemStack(new ItemStack(Items.DIAMOND)))));
        Assertions.assertFalse(
                ActionRobotFilter.canInteractWithItem(station, new ItemStack(Items.STONE),
                        ActionStationProvideItems.class),
                "an action filtered on diamond must refuse a stone-carrying robot");
    }

    /** A List in the action's filter slot interacts with whatever the List matches, for Provide and Accept
     *  alike. Pre-fix the robot's stack was asked "do you match this List?" (the List as the candidate), which
     *  is always no, so a List-filtered station refused every robot. */
    @Test
    public void aListFilterInteractsWithWhatTheListMatches() {
        ItemStack list = ActionStationProvideItemsTest.listHolding(new ItemStack(Items.DIAMOND));
        for (ItemAction kind : new ItemAction[] {
                new ItemAction(new ActionStationProvideItems(), ActionStationProvideItems.class),
                new ItemAction(new ActionStationAcceptItems(), ActionStationAcceptItems.class) }) {
            TestStation station = new TestStation(List.of(
                    slot(kind.action, new StatementParameterItemStack(list))));
            Assertions.assertTrue(
                    ActionRobotFilter.canInteractWithItem(station,
                            new ItemStack(Items.DIAMOND), kind.type),
                    kind.type.getSimpleName() + " filtered on a List naming diamond must interact with diamond");
            Assertions.assertFalse(
                    ActionRobotFilter.canInteractWithItem(station,
                            new ItemStack(Items.STONE), kind.type),
                    kind.type.getSimpleName() + " filtered on a List naming diamond must refuse stone");
        }
    }

    private record ItemAction(buildcraft.api.statements.IActionInternal action, Class<?> type) {}

    @Test
    public void otherActionClassDoesNotInteract() {
        TestStation station = new TestStation(List.of(
                slot(new ActionStationProvideItems(),
                        new StatementParameterItemStack(new ItemStack(Items.DIAMOND)))));
        Assertions.assertFalse(
                ActionRobotFilter.canInteractWithItem(station, new ItemStack(Items.DIAMOND),
                        ActionStationAcceptItems.class),
                "a provide-items action must not satisfy an accept-items interaction");
    }

    @Test
    public void gateFilterExtractsFilterStacks() {
        TestStation station = new TestStation(List.of(
                slot(new ActionStationProvideItems(),
                        new StatementParameterItemStack(new ItemStack(Items.DIAMOND)),
                        new StatementParameterItemStack(new ItemStack(Items.EMERALD)))));
        // The merge helper reads the ActionRobotFilter slots specifically.
        StatementSlot filterSlot = new StatementSlot();
        filterSlot.statement = new ActionRobotFilter();
        filterSlot.parameters = new IStatementParameter[] {
                new StatementParameterItemStack(new ItemStack(Items.DIAMOND)),
                new StatementParameterItemStack(new ItemStack(Items.EMERALD))
        };
        TestStation filterStation = new TestStation(List.of(filterSlot));

        IStackFilter gateFilter = ActionRobotFilter.getGateFilter(filterStation);
        Assertions.assertTrue(gateFilter.matches(new ItemStack(Items.DIAMOND)),
                "the gate filter must match a diamond");
        Assertions.assertFalse(gateFilter.matches(new ItemStack(Items.STONE)),
                "the gate filter must refuse a stone");

        Assertions.assertEquals(2, ActionRobotFilter.getGateFilterStacks(filterStation).size(),
                "both filter stacks must be extracted");
    }

    @Test
    public void toolFilterFallbackPrimitive() {
        // DockingStationPipe.getRobotItemFilter (Ph6-green) falls back from the work filter to the tool
        // filter; this pins the tool filter's own extraction helper.
        StatementSlot toolSlot = new StatementSlot();
        toolSlot.statement = new ActionRobotFilterTool();
        toolSlot.parameters = new IStatementParameter[] {
                new StatementParameterItemStack(new ItemStack(Items.STONE_AXE))
        };
        TestStation toolStation = new TestStation(List.of(toolSlot));

        IStackFilter toolFilter = ActionRobotFilterTool.getGateFilter(toolStation);
        Assertions.assertTrue(toolFilter.matches(new ItemStack(Items.STONE_AXE)),
                "the tool gate filter must match its tool");
        Assertions.assertFalse(toolFilter.matches(new ItemStack(Items.DIAMOND)),
                "the tool gate filter must refuse other items");
    }
}
