/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.statements;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.api.statements.StatementMouseClick;
import buildcraft.core.BCCoreItems;
import buildcraft.core.item.ItemMapLocation;
import buildcraft.robotics.item.ItemRobot;
import buildcraft.robotics.boards.BoardRobotPickerNBT;
import buildcraft.robotics.zone.ZonePlan;

/** The two Ph6 parameter widgets and the exact-stack counter: {@link StatementParameterRobot} is a board
 *  picker (empty click cycles to the next board type, a held robot stack selects that board), {@link
 *  StatementParameterMapLocation} accepts map stacks and rejects everything else, and {@link
 *  StatementParameterItemStackExact} steps its count with each click. */
public class RoboticsParameterTest extends VanillaSetupBaseTester {

    private static final StatementMouseClick LEFT = new StatementMouseClick(0, false);
    private static final StatementMouseClick RIGHT = new StatementMouseClick(1, false);

    private static ItemStack pickerStack() {
        return ItemRobot.createRobotStack(BoardRobotPickerNBT.INSTANCE.getID(), 0);
    }

    @Test
    public void emptyClickOnRobotParamCyclesToFirstBoard() {
        StatementParameterRobot result = new StatementParameterRobot()
                .onClick(null, null, ItemStack.EMPTY, LEFT);
        Assertions.assertFalse(result.getItemStack().isEmpty(),
                "an empty click on an empty robot parameter must pick a board");
        Assertions.assertTrue(result.getItemStack().getItem() instanceof ItemRobot,
                "the picked parameter must hold a robot stack");
    }

    @Test
    public void robotParamCyclesToNextBoard() {
        ItemStack picker = pickerStack();
        StatementParameterRobot result = new StatementParameterRobot(picker)
                .onClick(null, null, ItemStack.EMPTY, LEFT);
        Assertions.assertNotEquals(ItemRobot.getBoardId(picker), ItemRobot.getBoardId(result.getItemStack()),
                "an empty click must cycle to a different board");
    }

    @Test
    public void robotParamCyclesReverseOnRightClick() {
        ItemStack picker = pickerStack();
        StatementParameterRobot result = new StatementParameterRobot(picker)
                .onClick(null, null, ItemStack.EMPTY, RIGHT);
        Assertions.assertNotEquals(ItemRobot.getBoardId(picker), ItemRobot.getBoardId(result.getItemStack()),
                "a right-click must cycle to a different board");
    }

    @Test
    public void heldRobotStackSelectsThatBoard() {
        ItemStack picker = pickerStack();
        StatementParameterRobot result = new StatementParameterRobot()
                .onClick(null, null, picker, LEFT);
        Assertions.assertEquals(ItemRobot.getBoardId(picker), ItemRobot.getBoardId(result.getItemStack()),
                "a held robot stack must select its board");
    }

    @Test
    public void nonRobotClickRefuses() {
        ItemStack picker = pickerStack();
        StatementParameterRobot result = new StatementParameterRobot(picker)
                .onClick(null, null, new ItemStack(Items.DIAMOND), LEFT);
        Assertions.assertTrue(ItemStack.isSameItemSameComponents(picker, result.getItemStack()),
                "a non-robot click must leave the parameter unchanged");
    }

    @Test
    public void mapClickAcceptsMapStack() {
        ItemStack map = new ItemStack(BCCoreItems.MAP_LOCATION.get());
        ItemMapLocation.setZone(map, new ZonePlan());

        StatementParameterMapLocation result = new StatementParameterMapLocation()
                .onClick(null, null, map, LEFT);
        Assertions.assertTrue(ItemStack.isSameItemSameComponents(map, result.getItemStack()),
                "a map-location parameter must accept a map stack");
    }

    @Test
    public void nonMapClickRejects() {
        ItemStack map = new ItemStack(BCCoreItems.MAP_LOCATION.get());
        ItemMapLocation.setZone(map, new ZonePlan());

        StatementParameterMapLocation result = new StatementParameterMapLocation(map)
                .onClick(null, null, new ItemStack(Items.DIAMOND), LEFT);
        Assertions.assertTrue(result.getItemStack().isEmpty(),
                "a non-map click must clear the map-location parameter");
    }

    @Test
    public void emptyClickClearsMapParam() {
        ItemStack map = new ItemStack(BCCoreItems.MAP_LOCATION.get());
        ItemMapLocation.setZone(map, new ZonePlan());

        StatementParameterMapLocation result = new StatementParameterMapLocation(map)
                .onClick(null, null, ItemStack.EMPTY, LEFT);
        Assertions.assertTrue(result.getItemStack().isEmpty(),
                "an empty click must clear the map-location parameter");
    }

    @Test
    public void exactParamStepsCountDown() {
        StatementParameterItemStackExact param = new StatementParameterItemStackExact(
                new ItemStack(Items.DIAMOND, 4), 3);
        StatementParameterItemStackExact result = param.onClick(null, null, ItemStack.EMPTY, RIGHT);
        Assertions.assertEquals(3, result.getItemStack().getCount(),
                "a right-click must step the exact-stack count down");
    }

    @Test
    public void exactParamStepsCountUp() {
        StatementParameterItemStackExact param = new StatementParameterItemStackExact(
                new ItemStack(Items.DIAMOND, 4), 3);
        StatementParameterItemStackExact result = param.onClick(null, null, ItemStack.EMPTY, LEFT);
        Assertions.assertEquals(5, result.getItemStack().getCount(),
                "a left-click must step the exact-stack count up");
    }

    @Test
    public void exactParamReplacesWithHeldStack() {
        StatementParameterItemStackExact result = new StatementParameterItemStackExact(3)
                .onClick(null, null, new ItemStack(Items.STONE, 2), LEFT);
        Assertions.assertTrue(ItemStack.isSameItemSameComponents(
                new ItemStack(Items.STONE, 2), result.getItemStack()),
                "a held stack must replace the exact-stack parameter");
    }
}
