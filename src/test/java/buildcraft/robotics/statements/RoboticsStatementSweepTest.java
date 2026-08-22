/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.statements;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.api.statements.IStatement;
import buildcraft.api.statements.IStatementParameter;
import buildcraft.api.statements.StatementManager;
import buildcraft.api.statements.StatementParameterItemStack;
import buildcraft.core.BCCoreItems;
import buildcraft.core.item.ItemMapLocation;
import buildcraft.robotics.BCRoboticsStatements;
import buildcraft.robotics.item.ItemRobot;
import buildcraft.robotics.boards.BoardRobotPickerNBT;
import buildcraft.robotics.zone.ZonePlan;

/** Blanket registration sweep for the Ph6 catalog: every statement instance self-registers every one of
 *  its tags (including the {@code station.drop_in_pipe} alias) into {@code StatementManager.statements},
 *  parameter bounds hold, and every statement + widget survives a write→read NBT round-trip through the
 *  registered readers. */
public class RoboticsStatementSweepTest extends VanillaSetupBaseTester {

    /** Every tag the 18 instances must have registered, in 7.1.x spelling. */
    private static final String[] EXPECTED_TAGS = {
            "buildcraft:robot.work_filter",
            "buildcraft:robot.work_filter_tool",
            "buildcraft:robot.goto_station",
            "buildcraft:robot.wakeup",
            "buildcraft:robot.work_in_area",
            "buildcraft:robot.load_unload_area",
            "buildcraft:station.forbid_robot",
            "buildcraft:station.force_robot",
            "buildcraft:station.request_items",
            "buildcraft:station.accept_items",
            "buildcraft:station.drop_in_pipe",
            "buildcraft:station.provide_items",
            "buildcraft:station.provide_fluids",
            "buildcraft:station.accept_fluids",
            "buildcraft:station.provide_machine_request",
            "buildcraft:robot.sleep",
            "buildcraft:robot.in.station",
            "buildcraft:robot.linked",
            "buildcraft:robot.reserved",
    };

    @Test
    public void everyStatementRegistersEveryTag() {
        // Touch the catalog so its static instances self-register (the mod load already does via
        // BCRoboticsStatements.preInit — this keeps the sweep honest in any JVM).
        Assertions.assertNotNull(BCRoboticsStatements.ACTION_ROBOT_WAKE_UP);
        Assertions.assertNotNull(BCRoboticsStatements.TRIGGER_ROBOT_SLEEP);

        for (String tag : EXPECTED_TAGS) {
            IStatement stmt = StatementManager.statements.get(tag);
            Assertions.assertNotNull(stmt, "statement '" + tag + "' must be registered");
        }
    }

    @Test
    public void parameterBoundsAndCreationHold() {
        for (String tag : EXPECTED_TAGS) {
            IStatement stmt = StatementManager.statements.get(tag);
            Assertions.assertTrue(stmt.minParameters() <= stmt.maxParameters(),
                    "'" + tag + "' must have min <= max parameters");
            for (int i = 0; i < stmt.maxParameters(); i++) {
                IStatementParameter param = stmt.createParameter(i);
                Assertions.assertNotNull(param, "'" + tag + "' must create a parameter at slot " + i);
            }
            // Every statement localizes a description without throwing (missing keys return the key).
            Assertions.assertDoesNotThrow(stmt::getDescription, "'" + tag + "' must localize a description");
        }
    }

    @Test
    public void widgetReadersRoundTripRobotParams() {
        ItemStack robotStack = ItemRobot.createRobotStack(BoardRobotPickerNBT.INSTANCE.getID(), 0);
        StatementParameterRobot original = new StatementParameterRobot(robotStack);

        CompoundTag nbt = new CompoundTag();
        original.writeToNbt(nbt);

        StatementParameterRobot read = (StatementParameterRobot) StatementManager
                .getParameterReader("buildcraft:robot")
                .readFromNbt(nbt);
        Assertions.assertTrue(ItemStack.isSameItemSameComponents(original.getItemStack(), read.getItemStack()),
                "a robot parameter must survive a write→read cycle");
    }

    @Test
    public void widgetReadersRoundTripMapLocations() {
        ItemStack map = new ItemStack(BCCoreItems.MAP_LOCATION.get());
        ItemMapLocation.setZone(map, new ZonePlan());
        StatementParameterMapLocation original = new StatementParameterMapLocation(map);

        CompoundTag nbt = new CompoundTag();
        original.writeToNbt(nbt);

        StatementParameterMapLocation read = (StatementParameterMapLocation) StatementManager
                .getParameterReader("buildcraft:maplocation")
                .readFromNbt(nbt);
        Assertions.assertTrue(ItemStack.isSameItemSameComponents(original.getItemStack(), read.getItemStack()),
                "a map-location parameter must survive a write→read cycle");
    }

    @Test
    public void widgetReadersRoundTripExactStacks() {
        StatementParameterItemStackExact original = new StatementParameterItemStackExact(
                new ItemStack(Items.DIAMOND, 4), 3);

        CompoundTag nbt = new CompoundTag();
        original.writeToNbt(nbt);

        StatementParameterItemStackExact read = (StatementParameterItemStackExact) StatementManager
                .getParameterReader("buildcraft:stackExact")
                .readFromNbt(nbt);
        Assertions.assertTrue(ItemStack.isSameItemSameComponents(original.getItemStack(), read.getItemStack()),
                "an exact-stack parameter must survive a write→read cycle");
        Assertions.assertEquals(original.availableSlots, read.availableSlots,
                "the exact-stack availableSlots count must survive the round trip");
    }

    @Test
    public void everyStatementSurvivesNbtRoundTrip() {
        for (String tag : EXPECTED_TAGS) {
            IStatement stmt = StatementManager.statements.get(tag);
            Assertions.assertDoesNotThrow(() -> {
                // Statements themselves carry no NBT — the round-trip concern is the widgets they
                // create. Construct each statement's parameter set, write and re-read each one.
                List<IStatementParameter> params = new ArrayList<>();
                for (int i = 0; i < stmt.maxParameters(); i++) {
                    IStatementParameter param = stmt.createParameter(i);
                    if (param instanceof StatementParameterItemStack) {
                        CompoundTag nbt = new CompoundTag();
                        param.writeToNbt(nbt);
                        String kind = param.getUniqueTag();
                        IStatementParameter read = StatementManager.getParameterReader(kind).readFromNbt(nbt);
                        Assertions.assertNotNull(read, "'" + tag + "' param kind '" + kind + "' must re-read");
                    }
                    params.add(param);
                }
            }, "statement '" + tag + "' must tolerate an NBT round-trip of its parameters");
        }
    }
}
