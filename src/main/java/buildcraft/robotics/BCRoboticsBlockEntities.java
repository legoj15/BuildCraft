/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.robotics;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import buildcraft.lib.misc.BlockEntityTypeUtilBC;
import buildcraft.robotics.tile.TileZonePlanner;

public class BCRoboticsBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, BCRobotics.MODID);

    // Block entity for the Zone Planner — mirrors BCRoboticsBlocks.ZONE_PLANNER.
    public static final Supplier<BlockEntityType<TileZonePlanner>> ZONE_PLANNER;

    static {
        ZONE_PLANNER = BLOCK_ENTITIES.register("zone_planner",
                () -> BlockEntityTypeUtilBC.create(TileZonePlanner::new, BCRoboticsBlocks.ZONE_PLANNER.get()));
    }

    public static void init(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
