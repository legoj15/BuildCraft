/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
//? if <1.21.10 {
/*package buildcraft.robotics.client.model;

import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.Identifier;

import buildcraft.robotics.BCRoboticsItems;
import buildcraft.robotics.item.ItemRobot;
import buildcraft.robotics.item.RoboticsItemVariants;

// 1.21.1-only: feeds the per-board item icons to the classic model system. Item definitions
// (assets/<ns>/items/*.json, with their minecraft:component/custom_data condition chains) do not
// exist on this line, so the dispatch goes through model "overrides" instead: the property below
// reads the stack's board id — the same blob ItemRobot.getBoardId serves to the registry — and
// returns its index in RoboticsItemVariants, which models/item/robot.json and
// models/item/redstone_board.json threshold-match on. The newer nodes never load this class and
// never consult those overrides; the JSON chains are the whole story there.
//
// Registered from BCRoboticsClient during FMLClientSetupEvent, which runs before the first resource
// load, so the property exists when the models bake their override lists.
public class RoboticsBoardItemProperties {

    public static void register() {
        ItemProperties.register(BCRoboticsItems.ROBOT.get(),
                Identifier.parse(RoboticsItemVariants.BOARD_PROPERTY),
                (stack, level, entity, seed) ->
                        RoboticsItemVariants.robotProperty(ItemRobot.getBoardId(stack)));
        ItemProperties.register(BCRoboticsItems.REDSTONE_BOARD.get(),
                Identifier.parse(RoboticsItemVariants.BOARD_PROPERTY),
                (stack, level, entity, seed) ->
                        RoboticsItemVariants.boardProperty(ItemRobot.getBoardId(stack)));
    }
}*/
//?}
