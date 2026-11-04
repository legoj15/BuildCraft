/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.robotics;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import buildcraft.robotics.block.BlockRequester;
import buildcraft.robotics.block.BlockZonePlanner;
import buildcraft.platform.registry.BCRegistrar;
import buildcraft.platform.registry.BCBlockHolder;

public class BCRoboticsBlocks {
    public static final BCRegistrar.Blocks BLOCKS = BCRegistrar.createBlocks(BCRobotics.MODID);

    // The Zone Planner block. The in-GUI 3D map viewport is deferred (see todos.md), but the block,
    // its GUI/slots, and item<->layer zone transfer are live, so it ships unconditionally now.
    // 1.12.2 Material.IRON → pickaxe required (parity restored via
    // requiresCorrectToolForDrops + minecraft:mineable/pickaxe tag).
    public static final BCBlockHolder<BlockZonePlanner> ZONE_PLANNER;

    // The Requester block (Ph8): the request-network endpoint a delivery robot services.
    // 7.1.x Material.iron → same pickaxe rule as the planner.
    public static final BCBlockHolder<BlockRequester> REQUESTER;

    static {
        ZONE_PLANNER = BLOCKS.registerBlock("zone_planner", BlockZonePlanner::new,
                () -> BlockBehaviour.Properties.of().strength(5.0f, 10.0f).sound(SoundType.METAL).requiresCorrectToolForDrops());
        REQUESTER = BLOCKS.registerBlock("requester", BlockRequester::new,
                () -> BlockBehaviour.Properties.of().strength(5.0f, 10.0f).sound(SoundType.METAL).requiresCorrectToolForDrops());
    }

    public static void init() {
        BLOCKS.subscribe();
    }
}
