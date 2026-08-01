/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.robotics;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.bus.api.IEventBus;

import buildcraft.lib.misc.RegistrationUtilBC;
import buildcraft.robotics.item.ItemRobot;
import buildcraft.transport.item.ItemPluggableSimple;

public class BCRoboticsItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BCRobotics.MODID);

    // BlockItem for the Zone Planner — mirrors BCRoboticsBlocks.ZONE_PLANNER.
    public static final DeferredItem<?> ZONE_PLANNER;

    /** Docking Station Plug — the simple {@link PluggableDefinition.IPluggableCreator} path suffices
     *  (the pluggable itself writes no persistent NBT; the {@code DockingStationPipe} it registers is
     *  tracked separately by the {@code RobotRegistry}), so no bespoke {@code ItemRobotStation} class
     *  is needed — matches {@code PLUG_BLOCKER}/{@code PLUG_POWER_ADAPTOR} in transport. */
    public static final DeferredItem<ItemPluggableSimple> ROBOT_STATION;

    /** The robot itself. Never stacks: board and charge live in the stack's CUSTOM_DATA, so two robots are
     *  almost never interchangeable. */
    public static final DeferredItem<ItemRobot> ROBOT;

    static {
        ZONE_PLANNER = ITEMS.registerSimpleBlockItem(BCRoboticsBlocks.ZONE_PLANNER);
        ROBOT_STATION = RegistrationUtilBC.registerItem(ITEMS, "robot_station",
            props -> new ItemPluggableSimple(props, BCRoboticsPlugs.robotStation, null,
                buildcraft.robotics.RobotStationPluggable::boundingBoxFor));
        ROBOT = RegistrationUtilBC.registerItem(ITEMS, "robot", ItemRobot::new,
            props -> props.stacksTo(1));
    }

    public static void init(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
