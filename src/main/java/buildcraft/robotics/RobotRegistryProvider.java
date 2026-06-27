/*
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics;

import net.minecraft.world.level.Level;

import buildcraft.api.robots.IRobotRegistry;
import buildcraft.api.robots.IRobotRegistryProvider;

/**
 * Hands out the per-dimension {@link RobotRegistry}. Where 7.1.x cached one registry per dimension id in a
 * static map, the modern {@code SavedData} storage ({@link RobotRegistry#get}) already caches one instance per
 * {@code ServerLevel}, so this provider is a thin delegate.
 */
public class RobotRegistryProvider implements IRobotRegistryProvider {

    @Override
    public IRobotRegistry getRegistry(Level world) {
        return RobotRegistry.get(world);
    }
}
