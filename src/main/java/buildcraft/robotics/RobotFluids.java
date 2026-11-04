/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics;

import javax.annotation.Nullable;

import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.platform.Platform;
import buildcraft.platform.transfer.BCFluidStorage;

/**
 * The robot API's fluid surface (the robot's tank, a station's fluid input and output) as BuildCraft fluid storages.
 * Interim: that API still types them as the loader's fluid handler, so these read them back through the platform
 * layer, which unwraps a BuildCraft storage to itself. The fluid AIs work on the storages alone.
 */
public final class RobotFluids {
    private RobotFluids() {}

    /** The robot's tank. */
    public static BCFluidStorage tank(IRobotAccess robot) {
        return Platform.transfer().fromLoaderFluidHandler(robot.getFluidHandler());
    }

    /** The fluid a docked robot loads from at {@code station}, or null if it supplies none. */
    @Nullable
    public static BCFluidStorage input(DockingStation station) {
        return Platform.transfer().fromLoaderFluidHandler(station.getFluidInput());
    }

    /** Where a docked robot unloads fluid at {@code station}, or null if it takes none. */
    @Nullable
    public static BCFluidStorage output(DockingStation station) {
        return Platform.transfer().fromLoaderFluidHandler(station.getFluidOutput());
    }

    /** The loader's handler for a BuildCraft storage, typed as the robot API wants it. */
    @SuppressWarnings("unchecked")
    @Nullable
    public static <H> H toApi(@Nullable BCFluidStorage storage) {
        return storage == null ? null : (H) Platform.transfer().toLoaderFluidHandler(storage);
    }
}
